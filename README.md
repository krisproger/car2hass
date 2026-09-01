# CARTelemetry

[![Home Assistant](https://img.shields.io/badge/Home%20Assistant-2024.1%2B-41BDF5?logo=homeassistant&logoColor=white)](https://www.home-assistant.io/)
[![HACS](https://img.shields.io/badge/HACS-Custom-41BDF5.svg)](https://hacs.xyz/)
[![GitHub Release](https://img.shields.io/github/v/release/krisproger/car2hass)](https://github.com/krisproger/car2hass/releases)
[![License: MIT](https://img.shields.io/github/license/krisproger/car2hass)](LICENSE)

Universal vehicle telemetry platform for **Home Assistant**: the HA integration
(`custom_components/cartelemetry`) and the **Car2Hass** Android app.

- **Open API contract** — any app can push telemetry (`POST /api/cartelemetry`)
  and receive commands. Machine-readable spec: `https://mytechnic.ru/cartelemetry/api/spec/`.
- **Car2Hass app** — self-starting collector that probes available sources:
  BYD DiPlus (head unit), ADB, system sensors, **OBD-II over Bluetooth** (ELM327),
  **Voyah CAN** (Qinggan CanBus, 29 parameters), and more.
- **Auto-detection** — the car brand is chosen by the live channel (DiPlus → BYD,
  Voyah CANBus → Voyah); within the brand a heavy sensor pass scores the profile.
- **Works on phones too** — plug an OBD2 Bluetooth adapter and get telemetry into HA.

## Contents

| What | Where |
|---|---|
| **HA integration** | [`custom_components/cartelemetry/`](custom_components/cartelemetry/) |
| **Android app (Car2Hass)** | [`Car2Hass/`](Car2Hass/) |
| Manual (MkDocs) | [`mkdocs/`](mkdocs/) |
| Open API spec | [`docs/cartelemetry/api/spec/`](custom_components/cartelemetry/) (generated) |

APK downloads and the online manual: <https://mytechnic.ru/cartelemetry/>

## Installation

### Via HACS (recommended)

1. Install [HACS](https://hacs.xyz/) if you don't have it.
2. **HACS → menu ⋮ → Custom repositories** → add this repository:
   `https://github.com/krisproger/car2hass` (category **Integration**).
3. **HACS → Integrations** → find **CARTelemetry — Vehicle Telemetry** → **Download**.
4. **Restart Home Assistant** (Settings → System → Restart).
5. **Settings → Devices & Services → Add integration → CARTelemetry** → enter a car
   name (latin, no spaces, e.g. `bydsongpro`).

Updates arrive through HACS (the project publishes releases tagged `vX.Y.Z`).

### Manual (no HACS)

1. Download the latest `cartelemetry-vX.Y.Z.zip`:
   - from the [releases](https://github.com/krisproger/car2hass/releases), or
   - from the site: <https://mytechnic.ru/cartelemetry/download.php?file=integration>.
2. Extract the archive; copy the `cartelemetry/` folder into your HA config dir:
   ```
   <config>/custom_components/cartelemetry/
   ```
   (create `custom_components` if it doesn't exist).
3. **Restart Home Assistant**.
4. **Settings → Devices & Services → Add integration → CARTelemetry** → car name.

To update manually, replace the `custom_components/cartelemetry/` folder and restart HA.

### Car card (dashboard)

The integration copies the card to `www/community/cartelemetry-card/` at startup and
registers the resource `/local/community/cartelemetry-card/car-card.js`. If it does
not appear, add it manually (Dashboards → ⋮ → Resources → JavaScript Module →
`/local/community/cartelemetry-card/car-card.js`) and hard-refresh (Ctrl+F5). Details
in the manual: <https://mytechnic.ru/cartelemetry/manual/integration/dashboard-card/>.

## Requirements

- Home Assistant 2024.1+
- One data source: the Car2Hass app (BYD/Voyah head unit) or any app implementing
  the [open API contract](https://mytechnic.ru/cartelemetry/manual/integration/api-spec/).

## Support

- Manual: <https://mytechnic.ru/cartelemetry/manual/>
- Project: <https://mytechnic.ru/cartelemetry/>
- Issues: [GitHub Issues](https://github.com/krisproger/car2hass/issues)

## License

MIT. See [`LICENSE`](LICENSE).