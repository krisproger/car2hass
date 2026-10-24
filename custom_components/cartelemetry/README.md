# CARTelemetry — Vehicle Telemetry for Home Assistant

Home Assistant custom integration (`custom_components/cartelemetry`) for the
**Car2Hass** Android app and for **any** app that implements the open telemetry
contract.

The Android app collects data from whatever sources are available on the head
unit (vehicle ADB services, optional **DiPlus** on BYD DiLink, **OBD-II**
adapter, **Voyah CANBus**, system sensors) and pushes snapshots to Home
Assistant. The integration creates sensors, binary sensors, a device tracker
and control entities.

- Open API: `POST /api/cartelemetry` — any app can send telemetry.
- Machine-readable spec: <https://mytechnic.ru/cartelemetry/api/spec/>
- Site and manual: <https://mytechnic.ru/cartelemetry/>

## Version lines

`manifest.json` is the single source of truth for the version (the `version`
field). The integration has its own version line, independent of the app.

- **Stable** — default HACS install (currently `3.5.4`).
- **Beta** — published as `prerelease: true`; enable the repository
  **"Pre-release"** switch in HACS (currently `3.5.5-beta.14`).

## Features

- **265 signals** in the shared registry; the live set depends on the car:
  DiPlus (BYD) exposes up to 253, Voyah CANBus up to 171, plus ADB, OBD-II and
  system channels.
- Numeric `sensor` entities (speed, SOC, temperatures, tyre pressure, …),
  `binary_sensor` entities (doors, locks, lights, seatbelts, charging, …),
  `device_tracker` with GPS, and control entities (lock, climate, switches,
  numbers, selects, buttons, alarm panel).
- Config-flow setup; multiple cars supported — each vehicle gets its own device.
- Event-driven rules and geofences are configured in the Android app.
- Dashboard car card is copied to `www/community/cartelemetry-card/` at startup.

## Installation

### HACS (recommended)

1. Install [HACS](https://hacs.xyz/).
2. **HACS → menu ⋮ → Custom repositories** → add
   `https://github.com/krisproger/car2hass` (category **Integration**).
3. **HACS → Integrations** → find **CARTelemetry — Vehicle Telemetry** →
   **Download**.
4. Restart Home Assistant.
5. **Settings → Devices & Services → Add integration → CARTelemetry** → enter a
   car name (latin, no spaces, e.g. `bydsongpro`).

### Manual

Download `cartelemetry-vX.Y.Z.zip` from the
[releases](https://github.com/krisproger/car2hass/releases) or the
[site](https://mytechnic.ru/cartelemetry/download.php?file=integration),
extract it, and copy the `cartelemetry/` folder to
`<config>/custom_components/cartelemetry/`. Restart Home Assistant.

## Setup

1. Create a **Long-Lived Access Token** in your HA profile.
2. In the Car2Hass app set the HA address, the token and the **same car name**
   as in the integration (the name is the registry key).
3. Open the integration page to see entities and the last-seen timestamp.

## Services

- `cartelemetry.send_command` — enqueue a vehicle command (`car_name`,
  `command`, optional `params`); the app executes it on the car.
- `cartelemetry.developer_info` — summary of configured cars, VIN, firmware,
  last seen and signal counts.

## Requirements

- Home Assistant 2024.1+
- One data source: the Car2Hass app (BYD/Voyah head unit, phone with OBD-II) or
  any app implementing the open API contract.

## Support

- Manual: <https://mytechnic.ru/cartelemetry/manual/>
- Issues: <https://github.com/krisproger/car2hass/issues>
- Telegram: <https://t.me/bydiplus2hass>

## License

MIT
