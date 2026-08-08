package com.kapiteon.fastbuild;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric port of the original 1.12.2 FastBuild mod, reconstructed from
 * decompiled bytecode (see the NeoForge port's README for the full story
 * and the caveats about two ambiguous vanilla keybind references).
 *
 * Same disclaimer as the NeoForge version: this was written without a
 * working Fabric/Minecraft toolchain or internet access to verify exact
 * Fabric API/Loom version numbers, so expect small fixes on first build
 * (mainly: bump fabric-loader / fabric-api / yarn versions in
 * gradle.properties to whatever's current for 1.21.11).
 */
public class FastBuildClient implements ClientModInitializer {

    public static final String MODID = "fastbuild";

    private boolean enabled = false;
    private boolean showHud = false;
    private boolean enabledHorizontal = false;
    private boolean enabledFallProtect = false;
    private boolean flagReset = false;

    private KeyMapping keyShowHud;
    private KeyMapping keyOnOff;
    private KeyMapping keyHorizontal;
    private KeyMapping keyFallProtect;

    @Override
    public void onInitializeClient() {
        keyOnOff = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.fastbuild.enable", InputConstants.UNKNOWN.getValue(), "key.categories.fastbuild"));
        keyShowHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.fastbuild.showhud", InputConstants.UNKNOWN.getValue(), "key.categories.fastbuild"));
        keyHorizontal = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.fastbuild.horizontal", InputConstants.UNKNOWN.getValue(), "key.categories.fastbuild"));
        keyFallProtect = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.fastbuild.fallprotect", InputConstants.UNKNOWN.getValue(), "key.categories.fastbuild"));

        ClientTickEvents.START_CLIENT_TICK.register(this::onTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTickEnd);
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> renderHud(guiGraphics));
    }

    // ------------------------------------------------------------------
    // was: onEvent(TickEvent.ClientTickEvent) phase == START
    // ------------------------------------------------------------------
    private void onTickStart(Minecraft mc) {
        while (keyOnOff.consumeClick()) {
            enabled = !enabled;
        }
        while (keyShowHud.consumeClick()) {
            showHud = !showHud;
        }
        while (keyHorizontal.consumeClick()) {
            enabledHorizontal = !enabledHorizontal;
        }
        while (keyFallProtect.consumeClick()) {
            enabledFallProtect = !enabledFallProtect;
        }

        if (mc.options == null) return;

        if (!enabled) {
            handleFallProtect(mc);
            return;
        }

        // --- fast "remove" (attack/break) while the key is physically held ---
        InputConstants.Key attackKey = mc.options.keyAttack.getKey();
        if (mc.options.keyAttack.isDown()) {
            while (mc.options.keyAttack.consumeClick()) {
                // drain the click queue
            }
            KeyMapping.click(attackKey);
            KeyMapping.set(attackKey, true);
        }

        // --- horizontal-build handling ---
        InputConstants.Key useKey = mc.options.keyUse.getKey();
        boolean lookingUpOrDown = false;
        if (enabledHorizontal && mc.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK) {
            lookingUpOrDown = blockHit.getDirection() == Direction.UP || blockHit.getDirection() == Direction.DOWN;
        }

        if (enabledHorizontal && lookingUpOrDown) {
            if (mc.options.keyUse.isDown()) {
                KeyMapping.set(useKey, false);
                flagReset = true;
            }
            return; // matches original: early-return for this tick
        }

        // --- fast "place" (use) while the key is physically held ---
        if (mc.options.keyUse.isDown()) {
            while (mc.options.keyUse.consumeClick()) {
                // drain the click queue
            }
            KeyMapping.click(useKey);
        }

        handleFallProtect(mc);
    }

    // ------------------------------------------------------------------
    // was: onEvent(TickEvent.ClientTickEvent) phase == END
    // ------------------------------------------------------------------
    private void onTickEnd(Minecraft mc) {
        if (mc.options == null || !enabled) return;

        if (enabledHorizontal && flagReset) {
            flagReset = false;
            InputConstants.Key useKey = mc.options.keyUse.getKey();
            if (useKey.getType() == InputConstants.Type.MOUSE
                    && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), useKey.getValue()) == GLFW.GLFW_PRESS) {
                KeyMapping.set(useKey, true);
            }
        }
    }

    // ------------------------------------------------------------------
    // Fall protect
    // ------------------------------------------------------------------
    private void handleFallProtect(Minecraft mc) {
        if (!enabledFallProtect) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        BlockPos pos = new BlockPos(
                Mth.floor(player.getX()),
                Mth.floor(player.getBoundingBox().minY) - 1,
                Mth.floor(player.getZ())
        );

        boolean floorNearby = check(mc, pos, 2) || check(mc, pos, 1) || check(mc, pos, 0) || check(mc, pos, -1);
        InputConstants.Key useKey = mc.options.keyUse.getKey();
        boolean physicallyHeld = useKey.getType() == InputConstants.Type.KEYSYM
                && GLFW.glfwGetKey(mc.getWindow().getWindow(), useKey.getValue()) == GLFW.GLFW_PRESS;

        if (!floorNearby && player.onGround()) {
            if (!physicallyHeld) {
                KeyMapping.set(useKey, true);
            }
        } else {
            if (!physicallyHeld) {
                KeyMapping.set(useKey, false);
            }
        }
    }

    /** True if the block at (pos.x, pos.y + yOffset, pos.z) has a full 1x1 XZ footprint. */
    private boolean check(Minecraft mc, BlockPos pos, int yOffset) {
        if (mc.level == null) return false;
        BlockPos checkPos = pos.offset(0, yOffset, 0);
        BlockState state = mc.level.getBlockState(checkPos);
        AABB box = state.getShape(mc.level, checkPos).bounds();
        if (box.getSize() == 0) return false;
        return Math.round(box.minX) == 0 && Math.round(box.minZ) == 0
                && box.maxX == 1.0D && box.maxZ == 1.0D;
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------
    private void renderHud(GuiGraphics guiGraphics) {
        if (!showHud) return;
        Minecraft mc = Minecraft.getInstance();

        List<String> lines = new ArrayList<>();
        lines.add(ChatFormatting.RESET + "Fast place/remove: "
                + (enabled ? ChatFormatting.GREEN : ChatFormatting.RED) + enabled);
        lines.add(ChatFormatting.RESET + "Build horizontal: "
                + (enabled ? (enabledHorizontal ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.RESET)
                + (enabledHorizontal && enabled));
        lines.add(ChatFormatting.RESET + "Fall protect: "
                + (enabledFallProtect ? ChatFormatting.GREEN : ChatFormatting.RED) + enabledFallProtect);

        int lineHeight = mc.font.lineHeight;
        int screenWidth = guiGraphics.guiWidth();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) continue;
            int textWidth = mc.font.width(line);
            int x = screenWidth - 2 - textWidth;
            int y = 2 + lineHeight * i;
            guiGraphics.fill(x - 1, y - 1, x + textWidth + 1, y + lineHeight + 1, 0x90505050);
            guiGraphics.drawString(mc.font, line, x, y, 0xE0E0E0, false);
        }
    }
}
