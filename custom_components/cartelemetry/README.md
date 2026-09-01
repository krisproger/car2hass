# DiPlus-to-hass — Home Assistant Custom Integration

Custom integration for Home Assistant that receives telemetry from the **DiPlus-to-hass Android app** running on a BYD DiLink head unit.

The Android app reads vehicle data via the **DiPlus** app on the head unit (mandatory — it will not work without it) and forwards it to this integration via a single REST endpoint.

## Features

- **132 BYD vehicle signals** exposed as Home Assistant entities:
  - numeric `sensor` entities (speed, SOC, temperatures, tyre pressure, …)
  - `binary_sensor` entities (doors, locks, lights, seatbelts, charging, …)
  - `device_tracker` entity with GPS coordinates
- Config-flow setup — add the car directly from the HA UI.
- Multiple cars supported — each vehicle gets its own device.
- Developer-info service to inspect active cars and signal counts.

## Requirements

- Home Assistant 2024.x or newer.
- DiPlus-to-hass Android app installed and running on the BYD head unit.
- Network connectivity between the head unit and Home Assistant.
- A Long-Lived Access Token created in Home Assistant and configured in the Android app.

## Installation

### HACS (recommended, when published)

1. Open HACS → Custom repositories.
2. Add this repository with category **Integration**.
3. Install **DiPlus-to-hass — BYD Vehicle Telemetry**.
4. Restart Home Assistant.

### Manual

1. Copy the `diplus2hass` folder into your Home Assistant `custom_components/` directory:

   ```bash
   cp -r custom_components/diplus2hass /config/custom_components/
   ```

2. Restart Home Assistant.
3. Go to **Settings → Devices & services → Add integration** and search for **DiPlus-to-hass — BYD Vehicle Telemetry**.

## Configuration

During setup the integration asks only for the **car name**. This name must match the car name configured in the Android app, because incoming telemetry is routed by `car_name`.

After the config entry is created, the integration exposes the REST endpoint `/api/byd_diplus` and creates sensors, binary sensors and a device tracker automatically as soon as the first batch arrives.

## How data flows

```
BYD head unit (DiLink)
├─ DiPlus app (mandatory)
└─ DiPlus-to-hass app
    └─ POST https://<ha>/api/byd_diplus
        └─ Home Assistant
            └─ custom_components/diplus2hass
                ├─ sensor
                ├─ binary_sensor
                └─ device_tracker
```

## REST endpoint

- **URL:** `/api/byd_diplus`
- **Method:** `POST`
- **Authentication:** Home Assistant Bearer token (Long-Lived Access Token)
- **Content-Type:** `application/json`

### Request body

```json
{
  "car_name": "byd_car",
  "vvn": "VVIN1234567890",
  "firmware": "Di3.0_13.1.22.2510210.1",
  "batch": [
    {"key": "speed", "value": 87.5},
    {"key": "soc", "value": 72},
    {"key": "driver_door", "value": "open"},
    {"key": "latitude", "value": 55.7558},
    {"key": "longitude", "value": 37.6173}
  ]
}
```

`key` values must match the stable keys defined in `const.py`. Unknown keys are ignored.

## Entity overview

### Numeric sensors (excerpt)

| Key | Entity | Unit |
|---|---|---|
| `speed` | Speed | km/h |
| `soc` | Battery SOC | % |
| `battery_charge` | Battery Charge | % |
| `outside_temp` | Outside Temperature | °C |
| `cabin_temp` | Cabin Temperature | °C |
| `battery_temp_max` / `avg` / `min` | Battery Temperature | °C |
| `cell_voltage_max` / `min` | Cell Voltage | V |
| `tyre_pressure_fl` / `fr` / `rl` / `rr` | Tyre Pressure | kPa |
| `engine_rpm` | Engine RPM | rpm |
| `front_motor_rpm` / `rear_motor_rpm` | Motor RPM | rpm |
| `energy_per_100km` | Energy per 100 km | kWh |
| `total_energy` | Total Energy Consumption | kWh |
| `total_fuel` | Total Fuel Consumption | L |
| `fuel_level` | Fuel Level | % |
| `steering_angle` | Steering Wheel Angle | ° |
| `distance_to_car_ahead` | Distance to Car Ahead | m |
| `battery_voltage` | 12V Battery Voltage | V |

Full list: see `NUMERIC_SENSORS` in `const.py`.

### Binary sensors (excerpt)

| Key | Entity class |
|---|---|
| `driver_door` | door |
| `passenger_door` | door |
| `rear_left_door` / `rear_right_door` | door |
| `bonnet` | door |
| `trunk` | door |
| `driver_door_lock` | lock |
| `charging_state` | battery_charging |
| `low_beam` / `high_beam` | light |
| `left_turn` / `right_turn` / `hazard` | light |
| `driver_seatbelt` | safety |
| `auto_hold` | safety |
| `ac_state` | cold |
| `power_state` | power |

Full list: see `BINARY_SENSORS` and `BINARY_ON_MAP` in `const.py`.

### Device tracker

GPS latitude/longitude are exposed as a `device_tracker` entity named `<car_name> Location`.

## Services

### `diplus2hass.developer_info`

Creates a persistent notification with a summary of all configured vehicles:
- car name
- virtual VIN (`vvn`)
- firmware version
- last seen timestamp
- number of received signals

Useful for debugging connectivity from the Android app.

## Signal source

The Android app reads signals via the **DiPlus** app installed on the head unit (mandatory — without it the app will not work) and maps them to stable English keys used by this integration.

The single source of truth for the registry is `signals.yaml` in the parent project; `const.py`, `SIGNALS.md` and the Android `SIGNAL_REGISTRY` are generated from it.

## Security

- The endpoint requires HA authentication. Use a dedicated Long-Lived Access Token in the Android app.
- Prefer HTTPS when exposing Home Assistant to the head unit network.
- Do not commit HA tokens to this repository.

## Roadmap

- **Rule-based automatic actions:** for example, if the outside temperature is above 25 °C, automatically turn on seat ventilation to 100 % for occupied seats.

## License

MIT
