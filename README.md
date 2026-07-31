# DiPlus-to-hass

Bridge for sending BYD vehicle telemetry to Home Assistant.

Мост для отправки телеметрии автомобиля BYD в Home Assistant.

- **DiPlus-to-hass** — Android app for the BYD head unit (DiLink). Reads vehicle data via the **DiPlus** app (mandatory — it will not work without it) and forwards it to a Home Assistant server.
- **custom_components/diplus2hass** — Home Assistant custom integration that receives the data and creates sensors, binary sensors, a device tracker and control entities.
- **mkdocs** — full project manual (Russian), built with MkDocs Material.

- **DiPlus-to-hass** — Android-приложение для головного устройства BYD (DiLink). Читает данные автомобиля через приложение **DiPlus** (обязательно — без него приложение не работает) и пересылает их на сервер Home Assistant.
- **custom_components/diplus2hass** — интеграция Home Assistant, которая принимает данные и создаёт сенсоры, бинарные сенсоры, трекер местоположения и элементы управления.
- **mkdocs** — полное руководство по проекту (на русском), собирается MkDocs Material.

> 📖 **Full project manual — [teplitzky.ru/diplus2hass/manual](https://teplitzky.ru/diplus2hass/manual/).**
> 📖 **Полное руководство по проекту — [teplitzky.ru/diplus2hass/manual](https://teplitzky.ru/diplus2hass/manual/).**
> 📰 **Step-by-step article — [teplitzky.ru/diplus2hass/article.html](https://teplitzky.ru/diplus2hass/article.html) · Rules guide — [article_rules.html](https://teplitzky.ru/diplus2hass/article_rules.html).**
> 💬 **Telegram community — [t.me/bydiplus2hass](https://t.me/bydiplus2hass).**

---

## Downloads / Скачать

| What / Что | Link / Ссылка |
|------------|---------------|
| Android app (latest) | [diplus2hass-v2.1.2.apk](https://teplitzky.ru/diplus2hass/download.php?file=app) |
| HA integration (latest) | [diplus2hass-v2.1.2.zip](https://teplitzky.ru/diplus2hass/download.php?file=integration) |
| DiPlus app (mandatory) | [diplus.1.3.8-beta18.apk](https://teplitzky.ru/diplus2hass/download.php?file=diplus) |

Previous releases and the version archive: [project site](https://teplitzky.ru/diplus2hass/) · Архив версий — на [сайте проекта](https://teplitzky.ru/diplus2hass/).

---

## English

### Project structure

```
.
├── custom_components/diplus2hass/  # Home Assistant custom integration
├── DiPlus-to-hass/                 # Android app
│   ├── app/src/main/               # Source code
│   ├── build_apk.sh                # Shell build script (release + test APK)
│   └── run_java_tests.sh           # Plain-JVM unit tests
├── mkdocs/                         # Project manual (MkDocs Material, Russian)
├── scripts/                        # signals_tool.py codegen + icon generators
├── tests/                          # Python tests for the integration
├── signals.yaml                    # Source of truth for signals/codegen
└── SIGNALS.md                      # Generated signal catalog
```

### Quick start

Detailed guide: [install.html](https://teplitzky.ru/diplus2hass/install.html) · full manual: [manual](https://teplitzky.ru/diplus2hass/manual/).

1. Install **DiPlus** on the head unit (mandatory).
2. Install the HA integration — one of two ways:
   - **HACS (recommended):** HACS → ⋮ → Custom repositories → add `https://github.com/krisproger/diplustohass` (Integration) → Download.
   - **Manually:** copy `custom_components/diplus2hass` to `/config/custom_components/`.
3. Restart HA and add the **DiPlus-to-hass — BYD Vehicle Telemetry** integration, set a car name.
4. Install the Android app, enter HA address, Long-Lived Access Token and the same car name.
5. Enable **Send data to HA** on the main screen and pick the signals to transmit.
6. Allow auto-start and background activity for the app in the BYD system settings.

### Features

- 130+ telemetry signals with stable units, GPS device tracker.
- Car control from HA (opt-in): climate, windows, locks, lights, drive modes, volume — with verification against real sensor state.
- Configurable dashboard with OTA-updated presets and PNG icons.
- Local rules engine on the head unit: multi-condition (AND/OR/NOT), multi-action, else branch, fire-once-per-trip, condition hold (anti-flap), geofence triggers.
- Geofence zones with an OpenStreetMap editor — each zone becomes a `binary_sensor` in HA.
- Background mode when the car is off (ACC off) with a built-in ADB helper.
- Offline queue: telemetry is buffered while HA is unreachable and flushed on reconnect.
- Bilingual UI (English/Russian).

### Building the Android app

Requirements: Android SDK (platform `android-35`, build-tools `36.1.0`) and JDK 17+.

```bash
cd DiPlus-to-hass
./build_apk.sh          # builds release + test APK into build/apk/
```

The script signs with a dev keystore. Generate it once (the script prints this command when missing), or set `KEYSTORE_FILE/KEYSTORE_PASS/KEY_ALIAS/KEY_PASS` for your own key:

```bash
mkdir -p .keys && keytool -genkey -keystore .keys/dev-release.jks -alias devkey \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass devpass -keypass devpass \
  -dname 'CN=DiPlus-to-hass Dev'
```

Gradle alternative: `./gradlew assembleRelease`.

### Tests

- Integration: `pip install -r requirements-dev.txt && pytest tests/`
- Android (plain JVM): `DiPlus-to-hass/run_java_tests.sh`

### Signal registry

`signals.yaml` is the single source of truth. After editing:

```bash
python scripts/signals_tool.py regenerate
```

Regenerates the Java registry, translator, HA `const.py` and `SIGNALS.md` (idempotent).

### Manual

Sources in `mkdocs/`. Build:

```bash
pip install mkdocs mkdocs-material==9.5.18
mkdocs/build.sh   # outputs to docs/diplus2hass/manual (site_dir in mkdocs.yml)
```

### License

MIT — see [LICENSE](LICENSE).

---

## Русский

### Структура проекта

```
.
├── custom_components/diplus2hass/  # Home Assistant Custom Integration
├── DiPlus-to-hass/                 # Android-приложение
│   ├── app/src/main/               # Исходный код
│   ├── build_apk.sh                # Shell-сборка (release + test APK)
│   └── run_java_tests.sh           # Юнит-тесты (plain JVM)
├── mkdocs/                         # Руководство по проекту (MkDocs Material)
├── scripts/                        # Кодогенерация signals_tool.py + генераторы иконок
├── tests/                          # Python-тесты интеграции
├── signals.yaml                    # Источник истины для сигналов/кодогенерации
└── SIGNALS.md                      # Сгенерированный каталог сигналов
```

### Быстрый старт

Подробная инструкция: [install.html](https://teplitzky.ru/diplus2hass/install.html) · руководство: [manual](https://teplitzky.ru/diplus2hass/manual/).

1. Установите **DiPlus** на головное устройство (обязательно).
2. Установите интеграцию HA — одним из двух способов:
   - **Через HACS (рекомендуется):** HACS → ⋮ → Custom repositories → добавьте `https://github.com/krisproger/diplustohass` (Integration) → Download.
   - **Вручную:** скопируйте `custom_components/diplus2hass` в `/config/custom_components/`.
3. Перезапустите HA и добавьте интеграцию **DiPlus-to-hass — BYD Vehicle Telemetry**, задайте имя автомобиля.
4. Установите Android-приложение, укажите адрес HA, Long-Lived Access Token и то же имя автомобиля.
5. Включите **Отправлять в HA** на главном экране и отметьте нужные сигналы.
6. Разрешите автозапуск и фоновую работу приложения в системных настройках BYD.

### Возможности

- 130+ сигналов телеметрии со стабильными единицами, GPS-трекер.
- Управление авто из HA (включается отдельно): климат, окна, замки, свет, режимы, громкость — с верификацией по реальным сенсорам.
- Настраиваемый дашборд с OTA-пресетами и PNG-иконками.
- Движок правил на ГУ: множественные условия (AND/OR/NOT), несколько действий, else-ветка, «один раз за поездку», удержание условия (анти-флап), триггеры по геозонам.
- Геозоны с редактором на карте OpenStreetMap — каждая зона становится `binary_sensor` в HA.
- Фоновый режим при выключенном авто (ACC off) со встроенным ADB-помощником.
- Очередь офлайн: телеметрия буферизуется при недоступности HA и отправляется при восстановлении связи.
- Двуязычный интерфейс (русский/английский).

### Сборка Android-приложения

Требования: Android SDK (platform `android-35`, build-tools `36.1.0`) и JDK 17+.

```bash
cd DiPlus-to-hass
./build_apk.sh          # собирает release + test APK в build/apk/
```

Скрипт подписывает dev-keystore. Сгенерируйте его один раз (команда выше в английском разделе) или задайте `KEYSTORE_FILE/KEYSTORE_PASS/KEY_ALIAS/KEY_PASS` для своего ключа.

### Тесты

- Интеграция: `pip install -r requirements-dev.txt && pytest tests/`
- Android (plain JVM): `DiPlus-to-hass/run_java_tests.sh`

### Лицензия

MIT — см. [LICENSE](LICENSE).
