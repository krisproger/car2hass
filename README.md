# CARTelemetry

Universal vehicle telemetry platform for **Home Assistant**: the HA integration
(`custom_components/cartelemetry`) and the Car2Hass Android app.

- **Open API contract** — any app can push telemetry (`POST /api/cartelemetry`)
  and receive commands. See `docs` / the manual on the site.
- **Car2Hass app** — self-starting collector that probes available sources:
  BYD DiPlus (head unit), ADB, system sensors, **OBD-II over Bluetooth** (ELM327),
  **Voyah CAN** (Qinggan CanBus, 29 parameters), and more.
- **Auto-detection** — the car brand is chosen by the live channel (DiPlus → BYD,
  Voyah CANBus → Voyah), within the brand a heavy sensor pass scores the profile.
- **Works on phones too** — plug an OBD2 Bluetooth adapter and get telemetry into HA.

## Install

- **HACS**: add this repository as a Custom repository (category **Integration**) →
  Download → restart HA → add the CARTelemetry integration.
- **Manual**: download the latest `cartelemetry-vX.Y.Z.zip` from
  <https://mytechnic.ru/cartelemetry/download.php?file=integration>, extract
  `custom_components/cartelemetry/` into your HA `<config>/custom_components/`,
  restart HA and add the integration.

See `INSTALL.md` for details.

## Requirements

- Home Assistant 2024.1+
- One data source: the Car2Hass app (BYD/Voyah head unit) or any app implementing
  the [open API contract].

## Support

- Manual: <https://mytechnic.ru/cartelemetry/manual/>
- Project: <https://mytechnic.ru/cartelemetry/>

## License

MIT. See `LICENSE`.