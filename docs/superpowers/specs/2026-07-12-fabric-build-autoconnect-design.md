# Дизайн: предустановленная Fabric-сборка + автозаход (вариант Б)

**Дата:** 2026-07-12
**Проект:** cm2android — кастомный Android-лаунчер Minecraft (форк MojoLauncher)
**Статус:** одобрено, готово к планированию реализации

## Контекст и цель

Форк MojoLauncher уже собирается, запускается на устройстве (рендерер Freedreno/LTW) и содержит
вшитую кастомную раскладку управления. Следующий шаг — превратить его в лаунчер под конкретный
сервер: предустановленная Fabric-сборка Minecraft **26.2** + набор модов + автозаход на сервер
**android.cherry.pizza** (миниигра CounterMine).

Аккаунты — **offline** (сервер `online-mode=false`, игрок вводит ник).

### Этапность
- **Сейчас (этот дизайн) — вариант Б:** сборка предустановлена, игрок открывает почти стандартный
  UI лаунчера, вводит ник, жмёт «Играть» → влетает на сервер.
- **Финал (отдельный будущий дизайн) — вариант А:** кастомный first-run экран «одна кнопка»
  (ник + Играть). Текущий дизайн не должен мешать переходу к А.

## Принятые решения (из брейншторма)

1. Стартуем с Б, целимся в А.
2. Fabric loader + моды **вшиты в apk** (не качаются с хостинга). Обновление набора модов = пересборка apk.
3. Автозаход: **`--quickPlayMultiplayer`** (мгновенный влёт) + сервер продублирован в **`servers.dat`** (fallback/список).
4. Предустановка Fabric — **подход «вшить готовую fabric-версию в assets»** (не программная установка при первом запуске).

## Вводные данные

- Версия Minecraft: **26.2** (снапшот)
- Fabric loader: **0.19.3**
- Сервер: **android.cherry.pizza**
- Моды (в `D:\Home\Projects\cm2android\mods`, ~4 МБ суммарно):
  - `fabric-api-0.154.2+26.2.jar`
  - `sodium-fabric-0.9.1+mc26.2.jar`
  - `zoomsensitivityfix-fabric-1.0.4.jar`

## Дизайн

### 1. Что вшивается в apk (`app_pojavlauncher/src/main/assets/`)

| Ассет | Назначение | Куда раскатывается |
|---|---|---|
| `cm2/fabric-loader-0.19.3-26.2.json` | fabric profile json (с `meta.fabricmc.net`) | `<DIR_HOME_VERSION>/fabric-loader-0.19.3-26.2/fabric-loader-0.19.3-26.2.json` |
| `launcher_profiles.json` (замена существующего) | профиль CounterMine, `lastVersionId` = fabric-версия | `<DIR_GAME_NEW>/launcher_profiles.json` |
| `cm2/mods/*.jar` (3 мода) | клиентские моды | `<DIR_GAME_NEW>/mods/` |
| `cm2/servers.dat` | сервер в списке | `<DIR_GAME_NEW>/servers.dat` |
| `default.json` (уже вшит) | раскладка управления | `<CTRLMAP_PATH>/default.json` |

`DIR_GAME_NEW = DIR_GAME_HOME + "/.minecraft"`; `DIR_HOME_VERSION` — каталог версий внутри `.minecraft/versions`.

### 2. Раскатка при первом запуске

Расширить `net.kdt.pojavlaunch.tasks.AsyncAssetManager` (уже копирует `default.json`,
`launcher_profiles.json`, `resolv.conf` через `Tools.copyAssetFile(..., overwrite=false)`):
- скопировать fabric-json в каталог версии;
- скопировать 3 мода в `mods/`;
- скопировать `servers.dat` в `.minecraft/`.

Все копирования с `overwrite=false` — если игрок уже что-то менял, не затираем. Клиент 26.2
(vanilla jar/assets/libraries) и fabric-библиотеки докачиваются штатным механизмом Mojo при
первом запуске игры (требуется сеть — она нужна для клиента в любом случае).

### 3. Автозаход

- **quickPlay:** в код сборки launch-команды (место, где формируется список game-аргументов
  Minecraft — предположительно `JREUtils`/`GameRunner`/`Tools.getMinecraftClientArgs` или аналог;
  точное место определить на этапе плана) добавить `--quickPlayMultiplayer android.cherry.pizza`.
  В Minecraft 26.2 quickPlay поддерживается нативно; Fabric loader прокидывает game-аргументы в
  ванильный клиент. Подстраховка (если на снапшоте будут проблемы) — вшить лёгкий Fabric-мод
  авто-коннекта; в основном дизайне не закладываем.
- **servers.dat:** вшит (сервер в списке многопользовательской игры как fallback).

### 4. UX первого запуска (Б)

Игрок ставит apk → открывает лаунчер → создаёт offline-аккаунт (вводит ник) → видит готовый
профиль CounterMine (Minecraft 26.2 + Fabric) → жмёт «Играть» → скачиваются клиент и библиотеки →
раскатанные моды на месте → quickPlay → попадает на android.cherry.pizza.

### 5. Задел на вариант А (одна кнопка — будущий этап)

- Адрес сервера, версия MC, версия loader, id профиля — вынести в **одну точку конфигурации**
  (`BuildConfig` через `resValue`/`buildConfigField` в `build.gradle`), не хардкодить по коду.
- Логику раскатки сборки и подстановки quickPlay-аргумента держать изолированно, чтобы будущий
  кастомный first-run экран (ник + одна кнопка) навешивался без переделки этих механизмов.

## Вне scope (YAGNI)

- Скачивание модов/сборки с хостинга (вшиваем).
- Кастомный UI / first-run экран «одна кнопка» (вариант А, отдельный дизайн).
- Брендинг (имя, иконка, package-id) — отдельный этап.
- authlib-injector / Microsoft-аккаунты (только offline).

## Открытые технические задачи (для этапа плана/реализации)

1. Получить fabric profile json для 26.2 / loader 0.19.3 с
   `https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.3/profile/json` и вшить в assets.
2. Сгенерировать `servers.dat` (NBT) с записью `android.cherry.pizza` (имя сервера — например «CounterMine»).
3. Найти точное место формирования game-аргументов Minecraft в коде Mojo для вставки quickPlay.
4. Определить точный путь `DIR_HOME_VERSION` и структуру каталога версий для раскатки fabric-json.
5. Проверить на устройстве: скачивание клиента 26.2 + fabric-либ, запуск, влёт на сервер.

## Проверка результата

Чистая установка apk на тест-девайс (Sony Xperia 1 VI) → создать offline-аккаунт → «Играть» →
убедиться, что скачивается 26.2, применяются моды (Sodium в видеонастройках), и клиент влетает на
android.cherry.pizza через quickPlay.
