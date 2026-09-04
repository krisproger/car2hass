# Карточка автомобиля для дашборда

Интеграция поставляет кастомную карточку **CARTelemetry Car Card**: автомобиль
видом сверху с сенсорами прямо на кузове и плитками управления под ним.

## Установка

1. Обновите интеграцию до v3.0.13+ — карточка копируется в
   `www/community/cartelemetry-card/` (каталог конфигурации HA), ресурс
   регистрируется автоматически (`/local/community/cartelemetry-card/car-card.js`).
2. Если карточка не появилась в списке — добавьте ресурс вручную:
   Настройки → Панели управления → ⋮ → Ресурсы → добавить
   `JavaScript Module` с URL `/local/community/cartelemetry-card/car-card.js`.
3. Обновите страницу браузера (Ctrl+F5).

## Настройка

Минимальный конфиг (YAML-редактор дашборда):

```yaml
type: custom:cartelemetry-car-card
car: my_car        # car_name как в entity_id (sensor.my_car_speed)
title: Мой автомобиль   # опционально
vehicle_type: suv  # опционально: car | hatchback | suv | pickup | van | truck | moto
```

Плитки управления по умолчанию подбираются автоматически (климат, замок,
обогрев зеркал). Свой набор:

```yaml
type: custom:cartelemetry-car-card
car: my_car
vehicle_type: suv
tiles:
  - entity: switch.my_car_ac_state
    name: Климат
    icon: mdi:air-conditioner
  - entity: lock.my_car_remote_lock_state
    name: Замок
    icon: mdi:car-key
```

## Что показывается на машине

- **Точки-индикаторы**: двери (4), капот, багажник, окна (4) — красные, когда
  открыто, зелёные — закрыто; серые, если данных нет.
- **Бейджи**: температура двигателя (капот), температура за бортом (зад),
  SOC (% заряда) — по центру.
- **Статусная строка**: едет/стоит (по скорости), SOC, диапазон.
- **Иконка типа авто** (слева сверху): base64-силуэт по `vehicle_type`.

## Vehicle Card (спидометр + binding-точки)

Вторая карточка — **`custom:cartelemetry-vehicle-card`** — универсальная: силуэт
автомобиля/грузовика/мотоцикла, сенсоры и кнопки по binding-точкам на кузове,
спидометр, панели сенсоров/управления/дверей. Иконки-изображения (PNG, CC0) лежат в
`www/community/cartelemetry-card/assets/` (копируются при установке).

```yaml
type: custom:cartelemetry-vehicle-card
vehicle: car            # car | truck | motorcycle
device: binary_sensor.my_car_online   # статус онлайн/офлайн
name: Мой автомобиль
sensors:                # маппинг на ваши сущности
  temperature: sensor.my_car_engine_coolant_temp
  fuel: sensor.my_car_soc
  battery: sensor.my_car_device_battery
  mileage: sensor.my_car_range
doors:                  # binary_sensor: on = открыто
  left: binary_sensor.my_car_driver_door
  right: binary_sensor.my_car_passenger_door
  trunk: binary_sensor.my_car_trunk
  hood: binary_sensor.my_car_bonnet
controls:               # lock/switch/button; horn может быть binary_sensor-индикатором
  lock: lock.my_car_doors_lock
  engine: switch.my_car_drl
  lights: switch.my_car_fog
  horn: binary_sensor.my_car_low_beam   # индикатор состояния (on/off) или button-кнопка
speedometer:            # опционально
  entity: sensor.my_car_speed
  max: 220
image_url: /local/community/cartelemetry-card/assets/car-silhouette.png  # своя картинка
binding_overrides:      # позиции иконок (x/y в % от картинки)
  temperature: { x: 45, y: 30 }
  fuel: { x: 45, y: 50 }
  battery: { x: 30, y: 50 }
  mileage: { x: 48, y: 66 }
  lock: { x: 60, y: 50 }
  engine: { x: 83, y: 64 }
  lights: { x: 95, y: 50 }
  horn: { x: 73, y: 43 }
  left: { x: 30, y: 35 }
  right: { x: 60, y: 35 }
  trunk: { x: 10, y: 50 }
  hood: { x: 85, y: 50 }
```

Позиции точек на кузове переопределяются через `binding_overrides` (например,
`binding_overrides: { fuel: { x: 50, y: 60 } }`). Формат значений сенсоров берётся
из `unit_of_measurement` сущности (SOC в %, диапазон в км, температура в °C — как в
интеграции).

### Редактор карточки (в интерфейсе HA)

У карточки есть визуальный редактор (кнопка «⋮» на карточке → «Редактировать»):

- **Тип транспорта, имя, устройство, маппинги сущностей** — выбор сущностей через
  нативный поиск HA (можно набирать имя/entity_id).
- **Ссылка на картинку** (`image_url`) — своё изображение (путь `/local/...` или
  `https://…`); если не указана — встроенный силуэт по `vehicle`.
- **Позиции иконок** — раздел «Позиции на изображении»: ползунки `x`/`y` (0–100 %) для
  сенсоров, дверей и кнопок; пустое значение возвращает стандартную позицию.
- **Управление** — слот «Гудок/Сигнал» автоматически подстраивается: `button` — кнопка,
  `switch`/`light` — переключатель, `binary_sensor` — индикатор состояния (on/off).

Эквивалент в YAML:
```yaml
type: custom:cartelemetry-vehicle-card
vehicle: car
image_url: /local/my-car.png
binding_overrides:
  fuel: { x: 50, y: 60 }
  lock: { x: 50, y: 20 }
```

## Вид сверху: оснастка SVG

Карточка берёт изображение из папки `www/community/cartelemetry-card/` (в каталоге
конфигурации HA) в таком порядке:

1. **Вариант состояния** `car_top_<state>.svg` — если открыты двери/окна/люк/
   багажник/капот: `doors_open`, `windows_open`, `sunroof_open`, `boot_open`,
   `bonnet_open`, при нескольких — `all_open`.
2. **База** `car_top.svg`.
3. **Вшитый fallback** — base64 (текущий вид по умолчанию).

**Картинки можно подложить самим** (например, красивее или в фирменных цветах) —
без пересборки: просто положите файл в
`<каталог конфигурации HA>/www/community/cartelemetry-card/` (например, `car_top.svg`,
`car_top_doors_open.svg`) и обновите страницу. Исходники-дефолты лежат в
`custom_components/cartelemetry/www/` интеграции — при её обновлении они копируются заново.

### Где взять картинки

- **Вид сверху** (free commercial):
  - svgrepo: https://www.svgrepo.com/vectors/car-top-view/ (напр. https://www.svgrepo.com/svg/166983/car-top-view)
  - freesvg: https://freesvg.org/top-view-car-vector
  - Vecteezy: https://www.vecteezy.com/free-svg/car-top-view (проверьте атрибуцию)
- **Иконки типов авто** (для `vehicle_type`): папка `www/community/cartelemetry-card/icons/`
  (SVG `car.svg`, `suv.svg`, …). mdi-аналоги для HA: https://pictogrammers.com/library/mdi/
  (`mdi:car`, `mdi:car-hatchback`, `mdi:car-sport`, `mdi:car-pickup`, `mdi:van-passenger`,
  `mdi:truck`, `mdi:motorbike`).

Размер SVG вида сверху — любой (карточка масштабирует по ширине, ~320px).

## Альтернатива: picture-elements (без кастомной карточки)

Если не хотите кастомные карточки — ядро HA позволяет собрать то же на
`picture-elements`. Изображение: `/local/community/cartelemetry-card/car_top.png`.

```yaml
type: picture-elements
image: /local/community/cartelemetry-card/car_top.png
elements:
  - type: state-label
    entity: sensor.my_car_soc
    style: { top: 50%, left: 50% }
  - type: state-icon
    entity: binary_sensor.my_car_driver_door
    style: { top: 38%, left: 6% }
    tap_action: { action: more-info }
```

Позиции подбираются вручную; подсветки состояний нет — только значения.
