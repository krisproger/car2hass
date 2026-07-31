# DiPlus-to-hass — руководство

**DiPlus-to-hass** — мост между автомобилем BYD и Home Assistant: телеметрия (130+ сигналов), местоположение, управление автомобилем и локальные автоматизации прямо на головном устройстве.

Проект состоит из двух частей:

- **Android-приложение DiPlus-to-hass** — работает на головном устройстве BYD (DiLink). Читает данные автомобиля через приложение **DiPlus** (обязательно — без него ничего не работает) и отправляет их на сервер Home Assistant.
- **Интеграция diplus2hass** — custom-компонент Home Assistant, который принимает данные и создаёт сенсоры, бинарные сенсоры, трекер местоположения и элементы управления (переключатели, числовые поля, списки, кнопки, замки, климат, охранный режим).

## Загрузки

| Что | Файл | Ссылка |
|-----|------|--------|
| Android-приложение (текущая версия) | `diplus2hass-v2.1.5.apk` | [скачать](https://teplitzky.ru/diplus2hass/download.php?file=app) |
| Интеграция HA (текущая версия) | `diplus2hass-v2.1.5.zip` | [скачать](https://teplitzky.ru/diplus2hass/download.php?file=integration) |
| Приложение DiPlus (обязательно) | `diplus.1.3.8-beta18.apk` | [скачать](https://teplitzky.ru/diplus2hass/download.php?file=diplus) |

??? note "Архив предыдущих версий"

    | Версия | APK | Интеграция |
    |--------|-----|------------|
    | v2.1.4 | [diplus2hass-v2.1.4.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.4.apk) | [diplus2hass-v2.1.4.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.4.zip) |
    | v2.1.3 | [diplus2hass-v2.1.3.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.3.apk) | [diplus2hass-v2.1.3.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.3.zip) |
    | v2.1.2 | [diplus2hass-v2.1.2.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.2.apk) | [diplus2hass-v2.1.2.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.2.zip) |
    | v2.1.1 | [diplus2hass-v2.1.1.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.1.apk) | [diplus2hass-v2.1.1.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.1.zip) |
    | v2.1.0 | [diplus2hass-v2.1.0.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.0.apk) | [diplus2hass-v2.1.0.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v2.1.0.zip) |
    | v2.0.0 | [diplus2hass-v2.0.0.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v2.0.0.apk) | [diplus2hass-v2.0.0.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v2.0.0.zip) |
    | v1.9.2 | — | [diplus2hass-v1.9.2.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v1.9.2.zip) |
    | v1.9.1 | [diplus2hass-v1.9.1.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v1.9.1.apk) | [diplus2hass-v1.9.1.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v1.9.1.zip) |
    | v1.9.0 | [diplus2hass-v1.9.0.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v1.9.0.apk) | [diplus2hass-v1.9.0.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v1.9.0.zip) |
    | v1.8.5 | [diplus2hass-v1.8.5.apk](https://teplitzky.ru/diplus2hass/diplus2hass-v1.8.5.apk) | [diplus2hass-v1.8.5.zip](https://teplitzky.ru/diplus2hass/diplus2hass-v1.8.5.zip) |

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

- Сайт проекта: [teplitzky.ru/diplus2hass](https://teplitzky.ru/diplus2hass/)
- Пошаговая статья со скриншотами: [article.html](https://teplitzky.ru/diplus2hass/article.html)
- Статья про правила: [article_rules.html](https://teplitzky.ru/diplus2hass/article_rules.html)
- Telegram-группа: [t.me/bydiplus2hass](https://t.me/bydiplus2hass)
- Лицензия: MIT
