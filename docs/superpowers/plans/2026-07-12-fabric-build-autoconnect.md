# Предустановленная Fabric-сборка + автозаход — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: используйте superpowers:subagent-driven-development (рекомендуется) или superpowers:executing-plans для выполнения плана задача-за-задачей. Шаги используют чекбоксы (`- [ ]`).

**Goal:** Превратить форк MojoLauncher в лаунчер под сервер CounterMine: предустановленная Fabric-сборка Minecraft 26.2 + вшитые моды, автозаход на android.cherry.pizza (вариант Б).

**Architecture:** Вшиваем fabric profile json, моды, servers.dat и профиль в `assets/`; раскатываем их при первом запуске через существующий `AsyncAssetManager.unpackSingleFiles()` (с `overwrite=false`); добавляем `--quickPlayMultiplayer` в сборку game-аргументов в `GameRunner`. Клиент 26.2 и fabric-библиотеки докачиваются штатно при первом запуске игры. Все параметры сервера/версии — в `BuildConfig` (задел на вариант А).

**Tech Stack:** Android/Java, Gradle (AGP 8.11.1), Fabric Loader 0.19.3, Minecraft 26.2, Python 3 (генерация NBT).

## Global Constraints

- Minecraft: **26.2**; Fabric Loader: **0.19.3**; ожидаемый version id: **`fabric-loader-0.19.3-26.2`** (подтвердить по полю `id` в скачанном profile json).
- Сервер: **android.cherry.pizza**; имя в списке: **CounterMine**.
- Аккаунты: **offline** (не трогаем auth).
- Все раскатки при первом запуске — с **`overwrite=false`** (не затирать пользовательские изменения).
- Клиент Minecraft (vanilla jar/assets) **не вшивать** в apk (EULA) — только fabric-json + моды + servers.dat + профиль.
- Параметры сервера/версии — только через **`BuildConfig`** (не хардкодить по коду; задел на вариант А).
- Модуль: `app_pojavlauncher`. Пакет: `git.artdeell.mojo` (класс `BuildConfig` = `git.artdeell.mojo.BuildConfig`).
- Пути: `DIR_GAME_NEW = <storage>/games/PojavLauncher/.minecraft`; `DIR_HOME_VERSION = DIR_GAME_NEW + "/versions"`; `CTRLMAP_PATH`, `DIR_GAME_HOME` — из `Tools`.
- Сборка основного apk: `JAVA_HOME=C:\Program Files\Java\zulu-17`; `gradlew.bat :app_pojavlauncher:assembleFullDebug`.

**Примечание по «тестам»:** проект — Android-лаунчер, юнит-тестов нет. «Тест» каждой задачи = детерминированная проверка результата (наличие файла в apk через распаковку zip, успешная сборка, поведение на устройстве через adb). Коммиты — в git-репозитории cm2android (корень проекта, shallow clone); если git-flow ещё не настроен, шаги коммита можно пропускать по решению Дыни.

---

### Task 1: Вшить fabric profile json и моды в assets

**Files:**
- Create: `app_pojavlauncher/src/main/assets/cm2/fabric-loader-0.19.3-26.2.json`
- Create: `app_pojavlauncher/src/main/assets/cm2/mods/fabric-api-0.154.2+26.2.jar`
- Create: `app_pojavlauncher/src/main/assets/cm2/mods/sodium-fabric-0.9.1+mc26.2.jar`
- Create: `app_pojavlauncher/src/main/assets/cm2/mods/zoomsensitivityfix-fabric-1.0.4.jar`

**Interfaces:**
- Produces: ассет `cm2/<versionId>.json` и `cm2/mods/*.jar`, потребляются Task 5 (раскатка).

- [ ] **Step 1: Получить fabric profile json** (веб-загрузка — через research-субагента, не напрямую)
  URL: `https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.3/profile/json`
  Сохранить ответ в `app_pojavlauncher/src/main/assets/cm2/fabric-loader-0.19.3-26.2.json`.

- [ ] **Step 2: Подтвердить version id**
  Прочитать поле `"id"` из скачанного json. Ожидается `fabric-loader-0.19.3-26.2`.
  Если id отличается — переименовать файл в `<id>.json` и обновить это значение в Task 4 и Global Constraints.

- [ ] **Step 3: Скопировать моды в assets**
  ```bash
  mkdir -p app_pojavlauncher/src/main/assets/cm2/mods
  cp tmp/mods/*.jar app_pojavlauncher/src/main/assets/cm2/mods/
  ```

- [ ] **Step 4: Проверка** — файлы на месте и json валиден
  ```bash
  ls app_pojavlauncher/src/main/assets/cm2/ app_pojavlauncher/src/main/assets/cm2/mods/
  python -c "import json; print(json.load(open('app_pojavlauncher/src/main/assets/cm2/fabric-loader-0.19.3-26.2.json'))['id'])"
  ```
  Expected: 3 jar в `mods/`, json печатает `fabric-loader-0.19.3-26.2`.

- [ ] **Step 5: Commit**
  ```bash
  git add app_pojavlauncher/src/main/assets/cm2/
  git commit -m "feat(cm2): bundle fabric 26.2 profile json and mods"
  ```

---

### Task 2: Сгенерировать servers.dat

**Files:**
- Create: `scripts/gen_servers_dat.py` (в корне cm2android)
- Create: `app_pojavlauncher/src/main/assets/cm2/servers.dat`

**Interfaces:**
- Produces: ассет `cm2/servers.dat` (uncompressed NBT), потребляется Task 5.

- [ ] **Step 1: Написать генератор NBT** (чистый struct, без внешних либ)
  Create `scripts/gen_servers_dat.py`:
  ```python
  import struct, sys

  def tag_string(name, value):
      nb = name.encode('utf-8'); vb = value.encode('utf-8')
      return b'\x08' + struct.pack('>H', len(nb)) + nb + struct.pack('>H', len(vb)) + vb

  # servers.dat = uncompressed NBT: root TAG_Compound{ TAG_List("servers") of TAG_Compound{name, ip} }
  name, ip = "CounterMine", "android.cherry.pizza"
  server = tag_string("name", name) + tag_string("ip", ip) + b'\x00'  # 0x00 = TAG_End of the compound
  # TAG_List "servers": tag id 0x09, name, then list-element-type (0x0A compound), count int, elements
  ln = b"servers".encode('utf-8')
  servers_list = b'\x09' + struct.pack('>H', len(ln)) + ln + b'\x0A' + struct.pack('>i', 1) + server
  root = b'\x0A' + struct.pack('>H', 0) + servers_list + b'\x00'  # root compound (empty name) + TAG_End
  open(sys.argv[1], 'wb').write(root)
  print("wrote", sys.argv[1], len(root), "bytes")
  ```

- [ ] **Step 2: Сгенерировать файл**
  ```bash
  python scripts/gen_servers_dat.py app_pojavlauncher/src/main/assets/cm2/servers.dat
  ```
  Expected: `wrote ... N bytes`.

- [ ] **Step 3: Проверка** — байты содержат адрес
  ```bash
  python -c "d=open('app_pojavlauncher/src/main/assets/cm2/servers.dat','rb').read(); assert b'android.cherry.pizza' in d and b'CounterMine' in d; print('servers.dat OK', len(d))"
  ```
  Expected: `servers.dat OK N`.
  (Полная валидация — что Minecraft прочитает список — выполняется в Task 7 на устройстве.)

- [ ] **Step 4: Commit**
  ```bash
  git add app_pojavlauncher/src/main/assets/cm2/servers.dat
  git commit -m "feat(cm2): bundle servers.dat with CounterMine server"
  ```

---

### Task 3: Конфиг сервера/версии в BuildConfig

**Files:**
- Modify: `app_pojavlauncher/build.gradle` (внутри `android { defaultConfig { ... } }`)

**Interfaces:**
- Produces: `BuildConfig.CM2_SERVER_ADDRESS` (String), `BuildConfig.CM2_VERSION_ID` (String) — потребляются Task 6 (quickPlay) и опционально будущим вариантом А. `buildFeatures { buildConfig true }` уже включён.

- [ ] **Step 1: Добавить buildConfigField**
  В `app_pojavlauncher/build.gradle`, в блок `defaultConfig { ... }` (рядом с `applicationId`), добавить:
  ```groovy
  buildConfigField "String", "CM2_SERVER_ADDRESS", '"android.cherry.pizza"'
  buildConfigField "String", "CM2_VERSION_ID", '"fabric-loader-0.19.3-26.2"'
  ```

- [ ] **Step 2: Проверка** — сборка генерирует поля
  Run: `JAVA_HOME='C:\Program Files\Java\zulu-17'; gradlew.bat :app_pojavlauncher:compileFullDebugJavaWithJavac --console=plain`
  Expected: BUILD SUCCESSFUL; файл `app_pojavlauncher/build/generated/source/buildConfig/full/debug/git/artdeell/mojo/BuildConfig.java` содержит `CM2_SERVER_ADDRESS`.

- [ ] **Step 3: Commit**
  ```bash
  git add app_pojavlauncher/build.gradle
  git commit -m "feat(cm2): expose server/version config via BuildConfig"
  ```

---

### Task 4: Профиль CounterMine в launcher_profiles.json

**Files:**
- Modify: `app_pojavlauncher/src/main/assets/launcher_profiles.json`

**Interfaces:**
- Consumes: version id из Task 1 (`fabric-loader-0.19.3-26.2`).
- Produces: дефолтный профиль, указывающий на fabric-версию; раскатывается существующим кодом (`unpackSingleFiles` уже копирует `launcher_profiles.json`).

- [ ] **Step 1: Заменить содержимое** `launcher_profiles.json` на:
  ```json
  {
    "profiles": {
      "CounterMine": {
        "name": "CounterMine",
        "lastVersionId": "fabric-loader-0.19.3-26.2"
      }
    },
    "selectedProfile": "CounterMine"
  }
  ```

- [ ] **Step 2: Проверка**
  ```bash
  python -c "import json; d=json.load(open('app_pojavlauncher/src/main/assets/launcher_profiles.json')); assert d['profiles']['CounterMine']['lastVersionId']=='fabric-loader-0.19.3-26.2'; print('profile OK')"
  ```
  Expected: `profile OK`.

- [ ] **Step 3: Commit**
  ```bash
  git add app_pojavlauncher/src/main/assets/launcher_profiles.json
  git commit -m "feat(cm2): default profile points to fabric 26.2"
  ```

---

### Task 5: Раскатка fabric-json, модов и servers.dat при первом запуске

**Files:**
- Modify: `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/AsyncAssetManager.java` (метод `unpackSingleFiles`, строки 64-76)

**Interfaces:**
- Consumes: ассеты из Task 1 и Task 2; `BuildConfig.CM2_VERSION_ID` (Task 3); `Tools.copyAssetFile(Context, String assetPath, String outDir, boolean overwrite)`, `Tools.DIR_HOME_VERSION`, `Tools.DIR_GAME_NEW`, `AssetManager.list(String)`.
- Produces: раскатанные `versions/<id>/<id>.json`, `.minecraft/mods/*.jar`, `.minecraft/servers.dat`.

- [ ] **Step 1: Добавить импорт BuildConfig**
  В начало `AsyncAssetManager.java` (к остальным import) добавить:
  ```java
  import git.artdeell.mojo.BuildConfig;
  ```

- [ ] **Step 2: Добавить раскатку в `unpackSingleFiles`**
  В `AsyncAssetManager.java`, внутри `try` в `unpackSingleFiles` (после строки `Tools.copyAssetFile(ctx,"resolv.conf",Tools.DIR_DATA, false);`), добавить:
  ```java
  // cm2android: seed the preconfigured Fabric build (version json, mods, server list)
  String versionId = BuildConfig.CM2_VERSION_ID;
  Tools.copyAssetFile(ctx, "cm2/" + versionId + ".json",
          Tools.DIR_HOME_VERSION + "/" + versionId, false);
  Tools.copyAssetFile(ctx, "cm2/servers.dat", Tools.DIR_GAME_NEW, false);
  String[] cm2Mods = ctx.getAssets().list("cm2/mods");
  if (cm2Mods != null) {
      for (String mod : cm2Mods) {
          Tools.copyAssetFile(ctx, "cm2/mods/" + mod, Tools.DIR_GAME_NEW + "/mods", false);
      }
  }
  ```

- [ ] **Step 3: Проверка компиляции**
  Run: `JAVA_HOME='C:\Program Files\Java\zulu-17'; gradlew.bat :app_pojavlauncher:compileFullDebugJavaWithJavac --console=plain`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Проверка на устройстве (чистая раскатка)**
  Собрать и поставить на чистую (Task 7 соберёт финально; здесь — промежуточно):
  ```bash
  gradlew.bat :app_pojavlauncher:assembleFullDebug --console=plain
  adb uninstall git.artdeell.mjlaunch.debug   # чистая установка, чтобы overwrite=false раскатал
  adb install app_pojavlauncher/build/outputs/apk/full/debug/app_pojavlauncher-full-debug.apk
  adb shell monkey -p git.artdeell.mjlaunch.debug -c android.intent.category.LAUNCHER 1
  # дать лаунчеру раскатать ассеты, затем проверить:
  adb shell "ls /storage/emulated/0/games/PojavLauncher/.minecraft/mods/ /storage/emulated/0/games/PojavLauncher/.minecraft/versions/fabric-loader-0.19.3-26.2/ /storage/emulated/0/games/PojavLauncher/.minecraft/servers.dat"
  ```
  Expected: 3 мода в `mods/`, `fabric-loader-0.19.3-26.2.json` в версии, `servers.dat` на месте.

- [ ] **Step 5: Commit**
  ```bash
  git add app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/AsyncAssetManager.java
  git commit -m "feat(cm2): seed fabric build (version, mods, servers.dat) on first run"
  ```

---

### Task 6: Автозаход через quickPlay

**Files:**
- Modify: `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java` (метод `getMoJsonClientArgs`, перед `return` на строке 391)

**Interfaces:**
- Consumes: `BuildConfig.CM2_SERVER_ADDRESS` (Task 3); локальный `List<String> clientArgs`.
- Produces: game-аргумент `--quickPlayMultiplayer <адрес>` в команде запуска.

- [ ] **Step 1: Добавить импорт BuildConfig**
  В начало `GameRunner.java` (к остальным import) добавить:
  ```java
  import git.artdeell.mojo.BuildConfig;
  ```

- [ ] **Step 2: Добавить quickPlay-аргумент**
  В `getMoJsonClientArgs`, непосредственно перед `return JSONUtils.insertJSONValueList(clientArgs, varArgMap);` (строка 391), вставить:
  ```java
  // cm2android: auto-join the target server on launch (Quick Play, MC 1.20+/26.2)
  clientArgs.add("--quickPlayMultiplayer");
  clientArgs.add(BuildConfig.CM2_SERVER_ADDRESS);
  ```

- [ ] **Step 3: Проверка компиляции**
  Run: `JAVA_HOME='C:\Program Files\Java\zulu-17'; gradlew.bat :app_pojavlauncher:compileFullDebugJavaWithJavac --console=plain`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
  ```bash
  git add app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java
  git commit -m "feat(cm2): auto-join server via --quickPlayMultiplayer"
  ```

---

### Task 7: Финальная сборка и проверка на устройстве

**Files:** —

**Interfaces:**
- Consumes: всё из Task 1-6.

- [ ] **Step 1: Собрать финальный apk**
  Run: `JAVA_HOME='C:\Program Files\Java\zulu-17'; gradlew.bat :app_pojavlauncher:assembleFullDebug --console=plain`
  Expected: BUILD SUCCESSFUL; apk в `app_pojavlauncher/build/outputs/apk/full/debug/`.

- [ ] **Step 2: Проверить содержимое apk**
  Через распаковку zip убедиться, что в apk есть `assets/cm2/fabric-loader-0.19.3-26.2.json`, `assets/cm2/servers.dat`, 3 мода в `assets/cm2/mods/`, и `assets/launcher_profiles.json` с профилем CounterMine.

- [ ] **Step 3: Чистая установка на телефон**
  ```bash
  adb uninstall git.artdeell.mjlaunch.debug
  adb install app_pojavlauncher/build/outputs/apk/full/debug/app_pojavlauncher-full-debug.apk
  ```

- [ ] **Step 4: Ручная проверка (через scrcpy)**
  Открыть лаунчер → создать offline-аккаунт (ник) → убедиться, что выбран профиль CounterMine (Minecraft 26.2 + Fabric) → «Играть».
  Ожидается: скачивается клиент 26.2 + fabric-библиотеки; при старте игры клиент влетает на android.cherry.pizza (quickPlay); в видеонастройках присутствует Sodium; сервер CounterMine есть в списке многопользовательской игры.
  При проблемах с quickPlay на снапшоте 26.2 — зафиксировать симптом; запасной план (вшить Fabric-мод авто-коннекта) не входит в этот план.

- [ ] **Step 5: Commit (тег/пометка готовности, опционально)**
  ```bash
  git commit --allow-empty -m "chore(cm2): fabric build + auto-connect verified on device"
  ```
