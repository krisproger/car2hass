# Car2Hass / CARTelemetry — руководство

**Car2Hass / CARTelemetry** — универсальная платформа телеметрии автомобиля для Home Assistant: местоположение, состояние автомобиля, управление и локальные автоматизации прямо на головном устройстве.

Проект состоит из двух частей:

- **Android-приложение Car2Hass** — универсальный сборщик телеметрии: стартует само и само определяет доступные источники данных (ADB-сервисы автомобиля, DiPlus на BYD DiLink, OBD-II адаптер, системные датчики). Отправляет данные на сервер Home Assistant.
- **Интеграция cartelemetry** — custom-компонент Home Assistant с **открытым контрактом**: принимает телеметрию от любого приложения по [API-спецификации](integration/api-spec.md) и создаёт сенсоры, бинарные сенсоры, трекер местоположения и элементы управления (переключатели, числовые поля, списки, кнопки, замки, климат, охранный режим).

Список проверенных автомобилей см. в описании приложения; контракт сенсоров универсален — ядро (33 сигнала: скорость, SOC, диапазон, GPS, двери/окна/замки, климат) плюс расширенные источнико-зависимые сигналы.

## Загрузки

| Что | Файл | Ссылка |
|-----|------|--------|
| Android-приложение (текущая версия) | `car2hass-v3.0.20.apk` | [скачать](https://mytechnic.ru/cartelemetry/download.php?file=app) |
| Интеграция HA (текущая версия) | `cartelemetry-v3.0.20.zip` | [скачать](https://mytechnic.ru/cartelemetry/download.php?file=integration) |
| Приложение DiPlus (источник BYD DiLink, опционально) | `diplus.1.3.8-beta18.apk` | [скачать](https://mytechnic.ru/cartelemetry/download.php?file=diplus) |

??? note "Архив предыдущих версий"

    | Версия | APK | Интеграция |
    |--------|-----|------------|
    | v2.1.6 | [diplus2hass-v2.1.6.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.6.apk) | [diplus2hass-v2.1.6.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.6.zip) |
    | v2.1.5 | [diplus2hass-v2.1.5.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.5.apk) | [diplus2hass-v2.1.5.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.5.zip) |
    | v2.1.4 | [diplus2hass-v2.1.4.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.4.apk) | [diplus2hass-v2.1.4.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.4.zip) |
    | v2.1.3 | [diplus2hass-v2.1.3.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.3.apk) | [diplus2hass-v2.1.3.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.3.zip) |
    | v2.1.2 | [diplus2hass-v2.1.2.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.2.apk) | [diplus2hass-v2.1.2.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.2.zip) |
    | v2.1.1 | [diplus2hass-v2.1.1.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.1.apk) | [diplus2hass-v2.1.1.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.1.zip) |
    | v2.1.0 | [diplus2hass-v2.1.0.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.0.apk) | [diplus2hass-v2.1.0.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.1.0.zip) |
    | v2.0.0 | [diplus2hass-v2.0.0.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v2.0.0.apk) | [diplus2hass-v2.0.0.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v2.0.0.zip) |
    | v1.9.2 | — | [diplus2hass-v1.9.2.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v1.9.2.zip) |
    | v1.9.1 | [diplus2hass-v1.9.1.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v1.9.1.apk) | [diplus2hass-v1.9.1.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v1.9.1.zip) |
    | v1.9.0 | [diplus2hass-v1.9.0.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v1.9.0.apk) | [diplus2hass-v1.9.0.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v1.9.0.zip) |
    | v1.8.5 | [diplus2hass-v1.8.5.apk](https://mytechnic.ru/cartelemetry/diplus2hass-v1.8.5.apk) | [diplus2hass-v1.8.5.zip](https://mytechnic.ru/cartelemetry/diplus2hass-v1.8.5.zip) |

## Основные возможности

- **Телеметрия в реальном времени** — скорость, запас хода, SOC, температура, двери, окна, шины, свет, зарядка и ещё более 130 сигналов.
- **Геолокация** — трекер устройства в HA с координатами и скоростью.
- **Управление из HA** — климат, окна, замки, свет, режимы движения, громкость и другие команды (включается отдельной настройкой, по умолчанию выключено).
- **Дашборд в приложении** — настраиваемые плитки с пресетами, обновляемые OTA.
- **Движок правил** — локальные автоматизации на ГУ без Home Assistant: множественные условия (AND/OR/NOT), несколько действий, else-ветка, «один раз за поездку».
- **Геозоны** — зоны на карте, виртуальные датчики `inside/outside`, бинарные сенсоры в HA.
- **Фоновый режим** — работа при выключенном авто (ACC off) с помощником ADB-команд.
- **Два языка** — русский и английский интерфейс приложения.

## С чего начать

1. [Быстрый старт](quickstart.md) — полная установка за 15–20 минут.
2. [Дашборд](app/dashboard.md) — настройка главного экрана приложения.
3. [Правила](app/rules.md) — локальные автоматизации.
4. [Диагностика и FAQ](advanced/troubleshooting.md) — если что-то пошло не так.

## Ссылки

- Сайт проекта: [mytechnic.ru/cartelemetry](https://mytechnic.ru/cartelemetry/)
- Пошаговая статья со скриншотами: [article.html](https://mytechnic.ru/cartelemetry/article.html)
- Статья про правила: [article_rules.html](https://mytechnic.ru/cartelemetry/article_rules.html)
- Telegram-группа: [t.me/bydiplus2hass](https://t.me/bydiplus2hass)
- Лицензия: MIT
