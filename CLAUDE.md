# cm2android — кастомный Android-лаунчер Minecraft

Знания и контекст проекта ведём **в этом файле** (не в `~/.claude` memory).

## Цель

Форк открытого Android-лаунчера Minecraft Java (**MojoLauncher**) → собственный APK, который
запускает небольшую Fabric-сборку и автоматически заходит на приватный сервер **CounterMine**
(CS-подобный шутер в майнкрафте), с кастомной сенсорной раскладкой управления.
Аккаунты — **offline** (сервер `online-mode=false`, игрок вводит ник).

## Структура рабочей папки

```
cm2android/             # форк MojoLauncher (git-репозиторий), ветка v3_openjdk
├── app_pojavlauncher/  # Gradle-модуль приложения
├── glfw/               # сабмодуль
├── build.gradle, settings.gradle, gradlew.bat, gradle.properties, local.properties, ...
├── tmp/                 # gitignored, вспомогательные артефакты
│   ├── LTW/             # клон MojoLauncher/LTW — собирает libltw.so (рендерер)
│   ├── .gradle-iso/     # изолированный GRADLE_USER_HOME для сборки LTW
│   ├── mods/            # исходные jar-файлы модов
│   └── cm2_layout.json  # бэкап кастомной раскладки управления (вшита дефолтом)
├── docs/
└── CLAUDE.md
```

## База: MojoLauncher

- Репо: `github.com/MojoLauncher/MojoLauncher`, ветка **`v3_openjdk`**. Лицензия **LGPLv3** (artdeell).
- «MJLauncher» — это тот же проект после ребрендинга (пакет `git.artdeell.mojo` → `git.artdeell.mjlaunch`), отдельного репо нет.
- Gradle-модуль приложения: **`app_pojavlauncher`**. Флейворы: `full` (с JRE-рантаймом — наш) / `noruntime`.
- Требования: AGP 8.11.1, Gradle 8.14.3, compileSdk/targetSdk 36, minSdk 21, NDK `29.0.14206865`, CMake.
- Сабмодуль `glfw` (доинициализирован).

## Сборка (Windows)

Android SDK: `C:\Users\Admin\AppData\Local\Android\Sdk` — cmdline-tools, platform-tools,
`platforms;android-36`, `build-tools;36.0.0`, `ndk;29.0.14206865`, `cmake;3.22.1`.
Прописан в `local.properties` (`sdk.dir`).

Сборка основного APK:
```
$env:JAVA_HOME='C:\Program Files\Java\zulu-17'
gradlew.bat :app_pojavlauncher:assembleFullDebug --console=plain
```
Артефакт: `app_pojavlauncher/build/outputs/apk/full/debug/app_pojavlauncher-full-debug.apk`
(текущий ~88.7 МБ, 4 ABI, все рендереры, вшитая раскладка).

### Грабли окружения
Глобальный `C:\Users\Admin\.gradle\gradle.properties` форсит `org.gradle.java.home=zulu-21`
(class major 65) и заворачивает Gradle в SOCKS-прокси `127.0.0.1:10808`. AGP 8.11 переваривает
JDK 21 (основной APK собирается), но **старые Gradle падают**. `services.gradle.org` и
`mavenCentral` напрямую (без прокси) таймаутят; `dl.google.com` — работает напрямую.

## Рендереры

Ключ настройки `renderer` (дефолт `opengles2`). В билде присутствуют все четыре:

| id | рендерер | либа | примечание |
|---|---|---|---|
| `opengles2` | holy-gl4es | libgl4es_114.so | не тянет новые версии MC |
| `vulkan_zink` | Zink (GL→Vulkan) | mesa | на тест-девайсе даёт поворот экрана на 90° |
| `freedreno_kgsl` | freedreno/Turnip, GL 4.6 | libvulkan_freedreno.so | **рабочий на Adreno**, без поворота |
| `opengles3_ltw` | LTW (универсальный GLES-враппер) | libltw.so | нужен для устройств без Vulkan/Adreno |

## LTW — рецепт сборки (`tmp/LTW/`)

`libltw.so` не собирается в основном репо; приходит как `ltw-release.aar` в
`app_pojavlauncher/libs/` (gitignored; `build.gradle` тянет `*.aar` через fileTree).
Собираем сами из `github.com/MojoLauncher/LTW`.

LTW-проект: Gradle 7.5, AGP 7.4.1, compileSdk 34, NDK `28.2.13676358`, ndkBuild (Android.mk).
Доставлены пакеты: `ndk;28.2.13676358`, `platforms;android-34`, `build-tools;34.0.0`.

Применённые фиксы (иначе сборка падает на Windows):
1. **JDK:** junction `C:\Users\Admin\jdk17` → `zulu-17` (путь без пробелов), плюс
   `-Dorg.gradle.java.home=C:/Users/Admin/jdk17` в командной строке (перебивает глобальный java.home=21).
2. **`make (e=87)`** при линковке `libglsl_optimizer.a` (лимит длины команды Windows):
   добавлен `APP_SHORT_COMMANDS := true` в `tmp/LTW/ltw/src/main/tinywrapper/Application.mk`.
3. **Прокси/сеть:** изолированный `GRADLE_USER_HOME=tmp/.gradle-iso` (не читает глобальный конфиг),
   в него скопирован `gradle-7.5-bin` dist из `~/.gradle/wrapper/dists` (т.к. services.gradle.org
   напрямую таймаутит), зависимости тянутся с `dl.google.com`.

Команда:
```
$env:JAVA_HOME='C:\Users\Admin\jdk17'; $env:GRADLE_USER_HOME='d:\Home\Projects\cm2android\tmp\.gradle-iso'
gradlew.bat -p tmp/LTW :ltw:assembleRelease -Dorg.gradle.java.home=C:/Users/Admin/jdk17 --console=plain
```
Результат: `tmp/LTW/ltw/build/outputs/aar/ltw-release.aar` (libltw.so под все 4 ABI) →
копировать в `app_pojavlauncher/libs/` → пересобрать основной APK.

## Раскладка управления

`tmp/cm2_layout.json` вшита дефолтной: заменяет `app_pojavlauncher/src/main/assets/default.json`.
Формат — Pojav controlmap v8. Под CounterMine: джойстик движения слева, 2×SHOOT (обе левый клик
`-3`, намеренно), BUY(E)/RELOAD(F)/DROP(Q)/JUMP/SHIFT-toggle/CTRL-toggle/NEXT-PREV-слоты.

`AsyncAssetManager` копирует `default.json` из assets в `<DIR_GAME_HOME>/controlmap/default.json`
при первом запуске с `overwrite=false` (только если отсутствует). Игра грузит `controlmap/default.json`.
→ Для проверки на устройстве, где лаунчер уже запускался, нужна **чистая установка** (снести данные
приложения или удалить старый `controlmap/default.json`); новые установки подхватывают раскладку сразу.

## Версионирование и апдейтер

**Версия лаунчера** выводится из git-тега `v<major>.<minor>.<patch>` (`app_pojavlauncher/build.gradle`,
`resolveCm2Version`). Приоритет: явный override → новейший semver-тег → dev-фолбэк.
- `versionCode = major*10000 + minor*100 + patch` (v1.2.3 → 10203), `versionName = "1.2.3"`.
- Коммиты после тега → `versionName = "1.2.3-dev-<sha7>"`, versionCode остаётся релизным (иначе апдейтер
  предлагал бы обновиться на релиз, на котором билд уже основан).
- Тегов нет → `0.0.0-dev` / versionCode 1.
- Override для CI: `-Pcm2Version=v1.2.3` или env `CM2_VERSION`. Нужен, потому что `actions/checkout`
  по умолчанию не тянет теги (иначе — `fetch-depth: 0`).

**Апдейтер** (`net.kdt.pojavlaunch.cm2.updater`) при старте лаунчера дёргает
`https://api.github.com/repos/<CM2_UPDATE_REPO>/releases/latest` (репо в buildConfigField,
сейчас `SkolkovoLab/cm2android`, публичный — токен не нужен, анонимный лимит 60 req/ч на IP).
- `UpdateChecker` — парсит `tag_name` тем же semver→code правилом, сравнивает с `BuildConfig.VERSION_CODE`;
  draft/prerelease игнорируются. Ошибки (офлайн, 404, rate limit) — молча в лог, запуск не блокируют.
- `UpdatePrompt` — диалог «Обновить / Позже», один раз за процесс. «Позже» ничего не запоминает:
  при следующем старте спросит снова. Показ идёт через `ContextExecutor.executeActivity`, плюс
  `LauncherActivity.onResume` → `showPendingUpdate` (нужно для возврата с экрана «неизвестные источники»).
- `UpdateInstaller` — качает APK в `cacheDir/launcher_update/` (прогресс в `ProgressLayout.DOWNLOAD_UPDATE`),
  ставит через `PackageInstaller` session API. Результат приходит в `InstallResultReceiver`
  (`STATUS_PENDING_USER_ACTION` → системный диалог подтверждения).
- Манифест: `REQUEST_INSTALL_PACKAGES` + receiver. На API 26+ проверяется `canRequestPackageInstalls()`,
  иначе юзер отправляется в `ACTION_MANAGE_UNKNOWN_APP_SOURCES`.

**Требования к релизу на GitHub:** тег строго `v1.2.3`, ровно один `.apk`-ассет (апдейтер берёт
первый подходящий), не draft/prerelease. APK должен быть подписан тем же ключом и иметь тот же
applicationId, что и установленный у игроков, иначе установка поверх падает.

## Релизы через GitHub Actions

`.github/workflows/release.yml`: пуш тега `v*` (или `workflow_dispatch` с уже существующим тегом) →
валидация формата тега → checkout на тег (submodules) → JDK 17 + Gradle 8.14.3 → keystore из секрета →
`assembleFullRelease` с `CM2_VERSION=<тег>` → проверки → публикация релиза с одним APK
`CounterMine2-v1.2.3.apk`. Номер версии берётся именно из тега (env `CM2_VERSION`), поэтому `fetch-depth`
и выкачивание тегов не нужны.

Шаг «Verify the APK» страхует от тихих поломок: падает, если APK не подписан (тогда файл назывался бы
`...-unsigned.apk`) или если в нём нет `libltw.so`; печатает сертификат и badging в лог.

**Секреты репозитория:** обязательны `CM2_KEYSTORE_BASE64` и `CM2_KEYSTORE_PASSWORD` (без первого
сборка падает сразу, а не выкладывает неподписанный APK). `CM2_KEY_ALIAS` и `CM2_KEY_PASSWORD` —
опциональные: незаданные (пустые) значения фолбэчатся на `cm2` и на пароль хранилища соответственно.
Отдельный пароль ключа нужен только для старых JKS-хранилищ — PKCS12 (дефолт keytool с JDK 9+) его
игнорирует. Полностью убрать пароль нельзя: keytool требует минимум 6 символов.

**Подпись:** signingConfig `cm2Release` в `build.gradle` читает env `CM2_KEYSTORE_FILE` (по умолчанию
`app_pojavlauncher/cm2_release.jks`, gitignored), `CM2_KEYSTORE_PASSWORD`, `CM2_KEY_ALIAS` (дефолт `cm2`),
`CM2_KEY_PASSWORD`. Если файла keystore нет — `release` собирается неподписанным с warning’ом в логе.
Апстримные `debug.keystore` / `mojo_*.jks` к нашим релизам отношения не имеют (и `debug.keystore`
лежит в публичной репе с паролем `android`, так что им подписывать релизы нельзя).
**Потеря релизного keystore = невозможность обновлять установленные апки** — только переустановка
с потерей данных, бэкапь его отдельно.

**applicationId:** релиз — `dev.cherrypizza.cm2android`, локальный debug — `dev.cherrypizza.cm2android.debug`.
Это разные приложения: релиз не обновляет ранее установленные debug-билды, их надо сносить руками.

`android.yml` (debug CI) теперь игнорирует теги `v*` (иначе на тег стреляли бы два воркфлоу)
и больше не тянет LTW артефактом из чужого репо: `ltw-release.aar` теперь закоммичен в
`app_pojavlauncher/libs/` (сборка воспроизводима; пересобирать его — рецепт выше, раздел «LTW»).

## Окружение / устройство

- Тест-девайс: Sony Xperia 1 VI (`XQ-EC72`), Snapdragon 8 Gen 3 / Adreno 750, Android 16, arm64, Vulkan 1.3, OpenGL ES 3.2.
- adb: `<SDK>/platform-tools/adb.exe`. USB-соединение нестабильно (устройство периодически
  отваливается); авторизация отладки иногда слетает. Рассматривали переход на беспроводной adb (tcpip) — не сделан.
- **scrcpy** v4.0 (`winget Genymobile.scrcpy`) — зеркалирование телефона на ПК для работы с раскладкой.
  Запуск: `scrcpy.exe` с `$env:ADB` = наш platform-tools adb. Русский текст с клавы не вводится (ASCII only).
- JDK на машине: несколько версий в `C:\Program Files\Java` (используем `zulu-17` для сборки).

## Статус

**Сделано:** тулчейн, рабочий APK из исходников, запуск на устройстве (Freedreno), LTW собран и
вшит (все рендереры/ABI), кастомная раскладка вшита дефолтом.

**Fabric-сборка + автозаход (вариант Б) — реализовано и проверено на устройстве (12.07.2026):**
дизайн `docs/superpowers/specs/2026-07-12-fabric-build-autoconnect-design.md`, план
`docs/superpowers/plans/2026-07-12-fabric-build-autoconnect.md`. Изменения:
- `assets/cm2/`: вшиты fabric profile json (`fabric-loader-0.19.3-26.2.json`), 3 мода
  (`fabric-api`, `sodium`, `zoomsensitivityfix`), `servers.dat` (CounterMine → android.cherry.pizza).
- `build.gradle`: `buildConfigField` `CM2_SERVER_ADDRESS`, `CM2_VERSION_ID`.
- `launcher_profiles.json` → профиль CounterMine (legacy-заглушка; Mojo для UI использует instances).
- `AsyncAssetManager.unpackSingleFiles`: раскатка fabric-json → versions/, модов → .minecraft/mods/,
  servers.dat → .minecraft/ (overwrite=false).
- `GameRunner.getMoJsonClientArgs`: `--quickPlayMultiplayer BuildConfig.CM2_SERVER_ADDRESS`.
- `Instances.createFirstTimeInstance`: первый instance = наша fabric-версия (было хардкод «1.12.2»),
  имя «CounterMine». **Ключевой фикс:** Mojo для профиля UI использует instance-систему
  (`instances/<name>-<uuid>/mojo_instance.json`), а НЕ `launcher_profiles.json`.
- Результат: чистая установка → offline-аккаунт → «Играть» → качается 26.2 + Fabric 0.19.3 →
  quickPlay заходит на android.cherry.pizza (в логе `Connecting to android.cherry.pizza, 25565`,
  чат сервера, cstrike-контент). apk `full/debug` ~92.6 МБ.

**Баги модов/servers.dat — ИСПРАВЛЕНО (13.07.2026):**
Симптомы: моды не грузились (`Loading 4 mods` = только базовые), сервера не было в списке.
**Root cause:** `Instance.getGameDirectory()` при `sharedData=true` возвращает `SHARED_DATA_DIRECTORY`
(`DIR_GAME_HOME/shared_dir`), а НЕ `.minecraft`. Наш instance — sharedData, значит игровой `--gameDir`
= `shared_dir`, и fabric ищет `mods/`/`servers.dat` там. А `AsyncAssetManager.unpackSingleFiles`
раскатывал их в `.minecraft/` (`DIR_GAME_NEW`) — мимо.
**Фикс:** раскатку модов + `servers.dat` перенесли из `unpackSingleFiles` в
`AsyncAssetManager.extractDefaultSettings(ctx, gamedir)` — она вызывается из `MainActivity.onCreate`
с фактическим `instance.getGameDirectory()` (для любого instance, не хардкод shared_dir). Fabric-json
остался в `unpackSingleFiles` → `versions/` (launcher-managed, не gameDir-зависим).
Проверено на устройстве: `shared_dir/mods/` = 3 наших мода, `Loading 50 mods` (sodium, zoomfix, +
модули fabric-api), сервер в списке. Вариант Б работает полностью.

**Брендинг — СДЕЛАНО (12.07.2026):** иконка `tmp/cm2icon.png` (два бойца, CS-стиль) сгенерена во все
mipmap-плотности скриптом `scripts/gen_icons.py` (legacy/round/adaptive-foreground с safe-margin 92%,
webp). Имя приложения → «CounterMine 2» (`app_short_name` во всех 48 локалях). `applicationId` →
`dev.cherrypizza.cm2android` (+`.debug`); namespace/Java-пакет `git.artdeell.mojo` НЕ трогали (только
android-пакет). Провайдер: resValue `storageProviderAuthorities`/`application_package` (build.gradle
debug+release) → `dev.cherrypizza.cm2android...` (иначе INSTALL_FAILED_CONFLICTING_PROVIDER со старым
билдом); `group_id` оставлен `git.artdeell` (логика миграции). aapt badging подтверждает.

**Упрощённый вход — СДЕЛАНО (12.07.2026):** убраны Microsoft и ely.by, offline-аккаунт создаётся
автоматически при СТАРТЕ лаунчера (перехват), логин-экран не показывается. Ник рандомный
`android_<6 цифр>` (сервер всё равно оверрайдит username), offline UUID из ника
(`nameUUIDFromBytes("OfflinePlayer:"+name)`). Изменения:
- `LauncherActivity`: метод `ensureOfflineAccount()` (создаёт local-аккаунт `android_<random>` если
  нет), вызывается в `onCreate` (перед `bindViews`) — перехватывает старт до показа AccountSpinner;
  в `mLaunchGameListener` тот же метод как fallback.
- `SelectAuthFragment`: кнопки Microsoft/ely.by скрыты (`View.GONE`), остался только Local.
Проверено: чистая установка → старт без окна выбора → аккаунт `android_802387` (authType local,
accessToken "0", валидный profileId).

**Дефолтные настройки игры (options.txt) — СДЕЛАНО (12.07.2026):** статичные значения прописаны в
`assets/options.txt` (renderDistance:8, entityDistanceScaling:5.0, darkMojangStudiosBackground:true,
enableVsync:true, chatLinksPrompt:false, skipMultiplayerWarning:true; раскатка overwrite=false —
только первый запуск). `lang` — динамически: `AsyncAssetManager.extractDefaultSettings` при первом
запуске (когда options.txt только что скопирован) вызывает `MCOptionUtils.set("lang", <язык
устройства>)` + save; код языка = `Locale.getDefault()` → `ll_cc` lowercase (метод
`deviceMinecraftLang()`). Проверено на устройстве (ru-RU → `lang:ru_ru`), все значения применяются.

**Вырезаны звуковые ассеты (18.08.2026):** сервер всё равно перетирает звуки своим ресурспаком, а
на них приходится основная часть загрузки ассетов. `tasks/SoundAssetFilter` переписывает asset index
перед планированием загрузок: выкидывает всё под `<ns>/sounds/`, кроме файлов, нужных событиям из
`KEPT_SOUND_EVENTS` (сейчас — только `minecraft:ui.button.click`), и подменяет `minecraft/sounds.json`
урезанной копией (иначе игра сыпет warning-ами «File does not exist» на каждое из ~1968 событий).
Урезанный sounds.json пишется как обычный asset object, в индексе правятся его hash/size — загрузчик
видит файл на диске как валидный и не тянет оригинал.
`MoJsonDownloader.downloadAssetsIndex`: оригинал индекса теперь качается в `DIR_CACHE/cm2_asset_index/`
(sha1-проверка), а в `assets/indexes/<id>.json` (его читает игра) пишется отфильтрованная версия;
иначе sha1 не сходился бы и индекс перекачивался каждый запуск. Оригинал sounds.json кэшируется в
`DIR_CACHE/cm2_sound_index/`.
Проверено на реальном индексе 26.2 (`assets` index `32`): 457.0 MiB → **99.4 MiB**, удалено 4870
объектов, остаётся `minecraft/sounds/random/click_stereo.ogg` (на него мапится `ui.button.click`).
Уже скачанные звуки со старых установок с диска не удаляются — только перестают быть в индексе.

**Апдейтер лаунчера — СДЕЛАНО (18.08.2026):** версионирование по git-тегу + проверка GitHub-релизов при
старте с диалогом и установкой через `PackageInstaller` (подробности — в разделе «Версионирование и
апдейтер»). Собрано и проверено на уровне сборки (`versionCode`/`versionName` во всех трёх режимах,
badging APK); сквозная проверка на устройстве требует опубликованного релиза.

**Осталось:**
1. Вариант А — причесать first-run (разрешения/instance) до «одной кнопки». Основное (offline-авто,
   автозаход, сборка, настройки) уже готово.
2. Апдейтер не проверен на устройстве — нужен первый релиз и заведённые секреты подписи.
3. Возможные хвосты: финальное ревью правок ветки, git-коммиты, релизная подпись.

## Прочее

- Сам клиент Minecraft бандлить в APK нельзя (EULA Mojang) — качается с серверов Mojang; бандлим только Fabric loader + моды + конфиги.
- Стиль общения в чате (кошкодевочка «Клодя») — **только для чата**. Код, комментарии, документация,
  этот файл — нормальный профессиональный стиль.
