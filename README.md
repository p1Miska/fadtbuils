# FastBuild — порт на Fabric 1.21.11

Логика идентична NeoForge-версии (см. предыдущий архив) — тот же
восстановленный из байткода алгоритм, просто на Fabric API вместо
NeoForge-эвентов:

| NeoForge                        | Fabric                                          |
|----------------------------------|--------------------------------------------------|
| `@Mod` + `IEventBus`             | `ClientModInitializer` (entrypoint "client")     |
| `RegisterKeyMappingsEvent`       | `KeyBindingHelper.registerKeyBinding`             |
| `ClientTickEvent.Pre/Post`       | `ClientTickEvents.START_CLIENT_TICK/END_CLIENT_TICK` |
| `RegisterGuiLayersEvent`         | `HudRenderCallback.EVENT`                         |
| `neoforge.mods.toml`             | `fabric.mod.json`                                 |

Требует **Fabric API** как зависимость (для `KeyBindingHelper`,
`ClientTickEvents`, `HudRenderCallback`) — это отдельный мод, который у
игрока тоже должен быть установлен рядом с самим FastBuild.

## Важные оговорки (как и в NeoForge-версии)

- Не скомпилировано и не протестировано локально — нет интернета/тулчейна в
  среде, где это писалось. Версии в `gradle.properties`
  (`yarn_mappings`, `loader_version`, `fabric_version`) — ориентировочные на
  момент написания, **сверь их на https://fabricmc.net/develop/** перед
  первой сборкой и поправь при необходимости.
- Тот же нюанс с двумя неоднозначными полями `GameSettings` в оригинальном
  байткоде (атака/использование) — см. комментарий в начале
  `FastBuildClient.java`.
- Используются официальные маппинги Mojang (`loom.officialMojangMappings()`),
  а не Yarn, чтобы код совпадал 1:1 с NeoForge-портом.

## Сборка локально

```
./gradlew build
```
(если `gradlew` в архиве нет — один раз `gradle wrapper` с любым
установленным Gradle, либо просто используй системный `gradle build`)

Джар — в `build/libs/fastbuild-2.0.jar`.

## Сборка через GitHub Actions

Workflow уже лежит в `.github/workflows/build.yml`. Он ничего
специфичного не требует — просто:

1. Запушь этот проект в свой репозиторий на GitHub (как есть, целиком).
2. GitHub Actions включится автоматически на пуш/PR, или запусти вручную
   через вкладку **Actions → Build mod → Run workflow**.
3. После сборки джар будет доступен в **Actions → (запуск) → Artifacts**
   как `fastbuild-jar`.

Никаких секретов/токенов не требуется — Fabric/NeoForge maven-репозитории
публичные. Единственное, за чем следить: `gradle/actions/setup-gradle`
сам ставит Gradle нужной версии, поэтому коммитить `gradlew`/`gradle-wrapper.jar`
не обязательно (но можно, если хочешь запускать сборки одинаково локально
и в CI — тогда замени `run: gradle build` на `run: ./gradlew build`).
