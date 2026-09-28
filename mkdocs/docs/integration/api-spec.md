# Открытый API-контракт CARTelemetry

Интеграция CARTelemetry принимает телеметрию от **любого** приложения, которое
умеет отправлять данные по контракту ниже, и возвращает этому приложению команды.
Машиночитаемая версия: **GET `https://mytechnic.ru/cartelemetry/api/spec/`**
(генерируется из реестров проекта).

## Аутентификация

Long-lived access token Home Assistant, заголовок `Authorization: Bearer <token>`.

## Отправка телеметрии

```
POST /api/cartelemetry
```

```json
{
  "car_name": "my_car",
  "vvn": "VIN (опционально)",
  "firmware": "прошивка головы (опционально)",
  "app_version": "1.0.0",
  "ts": 1730000000,
  "batch": [
    {
      "t": 1730000000,
      "g": {"lat": 56.02, "lon": 92.89, "a": 4.0},
      "s": {"speed": "0", "soc": "68", "power_state": "on"}
    }
  ]
}
```

- `car_name` должен совпадать с именем, заданным при настройке интеграции.
- `batch` — окно снапшотов (рекомендуется 1 Гц, отправка раз в ~4 с).
- `g` — GPS-минимум (широта/долгота/точность), присутствует всегда.

## Получение команд

```
GET /api/cartelemetry/commands?car_name=my_car
→ {"commands": [{"id": "ac_on", "value": "1", ...}]}
```

Приложение забирает очередь, выполняет команды и подтверждает:

```
POST /api/cartelemetry/commands
{"car_name": "my_car", "acked": [{"id": "ac_on", "success": true}]}
```

## Сенсоры

- **Ядро** (`sensors_core`, 33 сигнала) — универсальный минимум: скорость, SOC,
  диапазон, power_state, GPS-блок, батарея устройства, двери/окна/люк, замки,
  климат, температура за бортом.
- **Расширенные** (`sensors_extended`, 167) — специфичные для источников
  (BYD/DiPlus, OBD, Voyah): RADAR-сигналы, зарядная система, подогревы и т.д.

Неизвестные ключи интеграция игнорирует — приложение может отправлять только то,
что умеет. Минимально жизнеспособный клиент — только `car_name` + `batch[].s`
с несколькими ключами ядра.

## Команды

`commands` в спеке: id, связанный state_sensor (для подтверждения изменения),
параметр и каналы, которыми команда доступна. Приложение выполняет команду тем же
каналом, которым читает соответствующий сенсор, либо любым доступным.

## Служебные API

**Интеграция HA** (обрабатывает ваш Home Assistant):

| Ручка | Назначение | Доступ |
|---|---|---|
| `POST /api/cartelemetry` | приём телеметрии | Bearer-токен HA |
| `GET/POST /api/cartelemetry/commands` | очередь команд | Bearer-токен HA |

**Сервисы mytechnic.ru** (префикс `https://mytechnic.ru/cartelemetry/api`):

| Ручка | Назначение | Доступ |
|---|---|---|
| `POST /probe-report/index.php` | анонимные отчёты исследования | `X-Cartelemetry-Token` |
| `POST /logs/index.php` | журнал приложения разработчику (+описание авто; поддерживает gzip и чанки) | `X-Cartelemetry-Token` |
| `GET /version/index.php` | последний релиз канала (`channel=stable` или `channel=beta`): `version`, `apk_url`, `sha256`, `size`, `whats_new` | открыто |
| `GET /spec/index.php` | эта спецификация (JSON) | открыто |
| `POST /mcp/index.php` | анализ исследований: `list_car_profiles`, `signal_availability`, `channel_availability`, `raw_reports` | открыто |
| `POST /admin/index.php` | управление хранилищем исследований/журналов: `list` (по дате/статусу), `get`, `grep` (по строкам), `mark` (обработан), `delete`, `summary` | `X-Cartelemetry-Admin-Token` |
| `POST /telegram/webhook.php` | приём updates Telegram-бота | `X-Telegram-Bot-Api-Secret-Token` |

**Облако аккаунта** (`https://mytechnic.ru`, Bearer-токен, scope `car:telemetry`):
`POST /api/account/token` — вход по email+TOTP, `POST /api/cars` — привязка авто
(до 3), `POST /api/telemetry` — отправка батчей; веб-кабинет — `/account`.

Токены intake хранятся на сервере (`.data/api_token.txt`, `.data/admin_token.txt`);
в APK вшит intake-токен (защита от случайного использования сторонними ресурсами).
Обработанные отчёты-исследования старше 7 дней автоматически удаляются при приёме новых.
