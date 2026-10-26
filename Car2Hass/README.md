# Car2Hass

Android-приложение-сборщик телеметрии для головных устройств и телефонов.
Самостоятельно определяет доступные источники и отправляет данные в Home
Assistant через интеграцию **CARTelemetry**.

## Источники данных

- **BYD DiLink** — через опциональное приложение **DiPlus** (максимум сигналов;
  на некоторых прошивках ADB-сервисы работают и без него).
- **Voyah** — Qinggan CANBus (read-only, 170+ сигналов).
- **OBD-II** — ELM327 по Bluetooth (в т.ч. на телефоне).
- **ADB-сервисы автомобиля** и **системные датчики** Android.

Приложение не требует DiPlus: он нужен только для BYD-канала DiPlus и не
используется на других платформах.

## Возможности

- **Единый реестр** — 265 сигналов; доступный набор зависит от автомобиля
  (DiPlus до 253, Voyah до 171, плюс ADB/OBD/системные).
- **Движок правил** — локальные автоматизации: условия AND/OR/NOT, действия
  «иначе», «один раз за поездку», сохранение состояния между запусками.
  Отключённые правила не выполняются (в т.ч. после облачного синка).
- **Геозоны** — зоны на OpenStreetMap (osmdroid) с радиусом и ручным вводом
  координат; виртуальный датчик `inside`/`outside` для правил.
- **Дашборд с пресетами** — плитки, переключатели, зоны ➖/➕, выбор режимов,
  агрегированные статусы; пресеты локализованы и обновляются OTA.
- **Foreground Service** и **BootReceiver** — фоновый сбор и автозапуск.
- **Batch-чтение**, **локальный буфер** при отсутствии сети, **GPS**
  (`device_tracker`), **HTTPS**, двуязычный UI (RU/EN).
- **Обновления** — сайт проекта, а при его недоступности — GitHub Releases
  (stable/beta, проверка SHA-256).
- **Экспорт/импорт конфигурации** — настройки, правила и геозоны.

## Архитектура

```
ГУ / телефон
└─ Car2Hass
   ├─ TelemetryService (foreground)
   ├─ источники: DiPlus · Voyah CANBus · ADB · OBD-II · system
   ├─ RuleEngine / CommandQueue
   └─ MainActivity (UI)

Home Assistant
└─ custom_components/cartelemetry   (CARTelemetry)
   ├─ POST /api/cartelemetry
   ├─ sensor / binary_sensor / device_tracker
   └─ управление: lock, climate, switch, number, select, button
```

## Сборка

Нужны JDK 17 и Android SDK (platform `android-35`, build-tools `36.1.0`).

```bash
cd Car2Hass
VERSION_NAME=3.5.5-beta.15 ./build_apk.sh
# APK: build/apk/Car2Hass_v3.5.5-beta.15.apk
```

Альтернатива — Gradle: `./gradlew assembleRelease` (версия из
`build_counter.txt` и `VERSION_NAME`).

Release-подпись задаётся переменными окружения (иначе используется dev-keystore
`.keys/dev-release.jks`, который не попадает в git):

```bash
export KEYSTORE_FILE=/secure/path/release.jks
export KEYSTORE_PASS=...
export KEY_ALIAS=...
export KEY_PASS=...
VERSION_NAME=3.5.5-beta.15 ./build_apk.sh
```

## Установка

1. Скачайте APK с сайта или из
   [GitHub Releases](https://github.com/krisproger/car2hass/releases).
2. Установите на ГУ/телефон и выдайте разрешения на геолокацию (в т.ч. фоновую)
   и уведомления.
3. Для BYD DiLink при желании установите **DiPlus** и убедитесь, что он
   показывает данные автомобиля.

```bash
adb install -r Car2Hass/build/apk/Car2Hass_v3.5.5-beta.15.apk
```

## Настройка Home Assistant

1. Установите интеграцию **CARTelemetry** (HACS или вручную) и перезапустите HA.
2. Создайте Long-Lived Access Token в профиле HA.
3. В приложении: ⚙ → **Настройки Home Assistant** — адрес, токен, HTTPS.
4. Включите **Отправлять данные в HA**.
5. Имя автомобиля должно совпадать с именем в интеграции.

## Устранение неполадок

| Симптом | Решение |
|---|---|
| `diplus not responding` (только BYD) | Запустите DiPlus; на части прошивок автозапуск невозможен — запустите вручную. |
| Нет данных в HA | Проверьте адрес, токен и совпадение имени автомобиля. |
| Нет обновлений с сайта | Приложение автоматически пробует GitHub Releases. |
| Точка на карте не обновляется | Проверьте разрешения на геолокацию и GPS/сетевую геолокацию ГУ. |

## Реестр сигналов

- Стабильные ключи (`speed`, `soc`, `driver_door`, …) описаны в `SIGNALS.md`.
- Единый источник — `signals.yaml`; `const.py`, `CANDataReader.java` и
  `SIGNALS.md` генерируются через `scripts/signals_tool.py`.
- Доступность конкретных сигналов зависит от прошивки и источника.

## Безопасность

- Cleartext HTTP разрешён для локального HA (`http://192.168.x.x:8123`);
  для внешнего доступа используйте HTTPS.
- Long-Lived Access Token хранится в Android Keystore (`SecureStorage`).
- Keystore и пароли — только через переменные окружения; не коммитьте ключи.
- `android:allowBackup="false"` — токен не попадает в облачный бэкап.

## Лицензия

MIT
