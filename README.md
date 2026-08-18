<h1 align="center">CounterMine 2</h1>

<p align="center">
  <img src="./app_pojavlauncher/src/main/res/mipmap-xxhdpi/ic_launcher.webp" width="128" height="128" alt="CounterMine 2">
</p>

<p align="center">
  <a href="https://github.com/SkolkovoLab/cm2android/releases/latest"><img src="https://img.shields.io/github/v/release/SkolkovoLab/cm2android" alt="Latest release"></a>
  <a href="https://github.com/SkolkovoLab/cm2android/actions"><img src="https://github.com/SkolkovoLab/cm2android/workflows/Android%20CI/badge.svg" alt="Android CI"></a>
</p>

Android-лаунчер для сервера **CounterMine 2**

Проект — форк [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) (ветка `v3_openjdk`),
который, в свою очередь, основан на [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher).
Лицензия — [GNU LGPLv3](./LICENSE), как у апстрима.

## Что настроено из коробки

- **Сборка игры.** Minecraft 26.2 + Fabric Loader 0.19.3, моды Fabric API, Sodium и
  ZoomSensitivityFix вшиты в APK и раскатываются при первом запуске.
- **Автоматический вход на сервер.** Игра стартует с `--quickPlayMultiplayer`, адрес сервера
  зашит в сборку (`CM2_SERVER_ADDRESS`); сервер также добавлен в список серверов.
- **Аккаунт без логина.** Сервер работает в offline-режиме, поэтому лаунчер сам создаёт
  локальный аккаунт при первом старте — экран выбора способа входа не показывается.
- **Сенсорная раскладка под шутер.** Джойстик движения, две кнопки стрельбы, покупка,
  перезарядка, смена слотов, приседание и бег — вшита как раскладка по умолчанию.
- **Предустановленные настройки игры.** `options.txt` с разумными дефолтами; язык интерфейса
  выставляется по языку устройства.
- **Урезанные звуковые ассеты.** Сервер всё равно перетирает звуки своим ресурспаком, поэтому
  индекс ассетов фильтруется перед скачиванием: ~457 МБ → ~99 МБ.
- **Встроенный апдейтер.** При старте лаунчер сверяет свою версию с последним релизом на GitHub
  и предлагает обновиться; APK скачивается и ставится изнутри приложения.

Сам клиент Minecraft в APK не входит — он скачивается с серверов Mojang при первом запуске.

## Установка

Готовый APK — в разделе [releases](https://github.com/SkolkovoLab/cm2android/releases/latest).
Дальше лаунчер обновляет себя сам.

Требуется Android 5.0 (API 21) или новее. Поддерживаемые ABI: `arm64-v8a`, `armeabi-v7a`,
`x86`, `x86_64`.

## Сборка

Требования:

- JDK 17 (сборка проверялась на Zulu 17)
- Android SDK: `platforms;android-36`, `build-tools;36.0.0`, `ndk;29.0.14206865`, `cmake;3.22.1`
- путь к SDK в `local.properties` (`sdk.dir`)

Репозиторий содержит сабмодуль `glfw`, поэтому клонировать нужно вместе с ним:

```
git clone --recurse-submodules https://github.com/SkolkovoLab/cm2android.git
```

Отладочная сборка:

```
./gradlew :app_pojavlauncher:assembleFullDebug
```

(на Windows — `.\gradlew.bat`). Артефакт:
`app_pojavlauncher/build/outputs/apk/full/debug/app_pojavlauncher-full-debug.apk`.

Флейвор `full` включает JRE-рантайм и используется для всех сборок проекта; `noruntime` остался
от апстрима.

`applicationId` у debug-сборки — `dev.cherrypizza.cm2android.debug`, у релизной —
`dev.cherrypizza.cm2android`. Это разные приложения: они уживаются на одном устройстве, но
релиз не обновляет ранее установленный debug-билд.

## Рендереры

В сборку входят все рендереры апстрима, выбираются в настройках лаунчера.

| Настройка | Рендерер | Примечание |
|---|---|---|
| `opengles2` | Holy GL4ES | значение по умолчанию у апстрима, новые версии Minecraft не тянет |
| `vulkan_zink` | Zink (GL поверх Vulkan) | на части устройств поворачивает экран |
| `freedreno_kgsl` | Freedreno/Turnip | рабочий вариант на Adreno |
| `opengles3_ltw` | LTW | универсальный GLES-враппер для устройств без Vulkan |

`libltw.so` собирается отдельным проектом [LTW](https://github.com/MojoLauncher/LTW); готовый
`ltw-release.aar` закоммичен в `app_pojavlauncher/libs/`, так что сборка воспроизводима без него.

## Версии и релизы

Версия выводится из git-тега вида `v1.2.3`: `versionName` = `1.2.3`,
`versionCode` = `major * 10000 + minor * 100 + patch`. Коммиты поверх тега дают
`1.2.3-dev-<sha>`. Версию можно задать явно через `-Pcm2Version=v1.2.3` или переменную окружения
`CM2_VERSION` — так делает CI, потому что checkout по умолчанию теги не выкачивает.

Релиз выпускается пушем тега `v*`: workflow `.github/workflows/release.yml` собирает
`assembleFullRelease`, подписывает релизным ключом и публикует APK в релиз. Нужны секреты
репозитория `CM2_KEYSTORE_BASE64` и `CM2_KEYSTORE_PASSWORD` (плюс опциональные `CM2_KEY_ALIAS`
и `CM2_KEY_PASSWORD`).

Апдейтер ищет в последнем релизе ровно один `.apk`-ассет и игнорирует draft/prerelease.
APK обязан быть подписан тем же ключом, иначе установка поверх у игроков не пройдёт.

## Отличия от апстрима

- вшитая Fabric-сборка, моды и `servers.dat`, автозаход на сервер;
- автоматический offline-аккаунт вместо экрана выбора способа входа (Microsoft и ely.by убраны);
- своя раскладка управления и `options.txt` по умолчанию;
- фильтрация звуковых ассетов при скачивании;
- собственный апдейтер по релизам GitHub;
- брендинг: иконка, имя приложения, `applicationId`.

## Лицензия и сторонние компоненты

Проект распространяется под [GNU LGPLv3](./LICENSE) — той же лицензией, что и MojoLauncher.

- [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher): [GNU LGPLv3](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/LICENSE)
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GNU LGPLv3](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE)
- [Boardwalk](https://github.com/zhuowei/Boardwalk) (JVM-лаунчер): [Apache License 2.0](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) или GNU GPLv2
- Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt)
- [Holy GL4ES](https://github.com/artdeell/gl4es_extra_extra/): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE)
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2](https://openjdk.java.net/legal/gplv2+ce.html)
- [GLFW](https://github.com/MojoLauncher/glfw): [zlib license](https://github.com/MojoLauncher/glfw/blob/glfw34/LICENSE.md)
- [LWJGL2-GLFW](https://github.com/MojoLauncher/lwjgl2-glfw): 3-Clause BSD license
- [LWJGL3](https://github.com/LWJGL/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md)
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html)
- [pro-grade](https://github.com/pro-grade/pro-grade): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt)
- [bhook](https://github.com/bytedance/bhook): [MIT license](https://github.com/bytedance/bhook/blob/main/LICENSE)
- [alsoft](https://github.com/kcat/openal-soft/): [GNU LGPL](https://github.com/kcat/openal-soft/blob/master/COPYING) и [modified PFFFT](https://github.com/kcat/openal-soft/blob/master/LICENSE-pffft)
- [oboe](https://github.com/google/oboe): [Apache License 2.0](https://github.com/google/oboe/blob/main/LICENSE)
- [Fabric Loader](https://github.com/FabricMC/fabric-loader) и [Fabric API](https://github.com/FabricMC/fabric): [Apache License 2.0](https://github.com/FabricMC/fabric-loader/blob/master/LICENSE)
- [Sodium](https://github.com/CaffeineMC/sodium): [LGPLv3](https://github.com/CaffeineMC/sodium/blob/dev/LICENSE.txt)
- [ZoomSensitivityFix](https://modrinth.com/mod/zoomsensitivityfix): MIT License

Minecraft — торговая марка Mojang Studios. Проект не связан с Mojang Studios и Microsoft.
