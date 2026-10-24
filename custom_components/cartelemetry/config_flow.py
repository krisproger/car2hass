"""Config flow for CARTelemetry integration."""

import re

import voluptuous as vol

from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.helpers import config_validation as cv

from .const import DOMAIN, CONF_CAR_NAME
from .const import (
    CONF_HISTORY_REPLAY,
    CONF_REPLAY_RATE,
    DEFAULT_HISTORY_REPLAY,
    DEFAULT_REPLAY_RATE,
    CONF_ADAPTIVE_BACKLOG,
    DEFAULT_ADAPTIVE_BACKLOG,
    CONF_BACKLOG_THRESHOLD,
    DEFAULT_BACKLOG_THRESHOLD,
    CONF_BACKLOG_REPLAY,
    DEFAULT_BACKLOG_REPLAY,
)

# The car name is the shared identifier between the Android app and this
# integration (the app signs every payload with it). Keep it identifier-like:
# letters, digits, underscore and dash — no spaces, 1..64 chars.
CAR_NAME_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


def _validate_car_name(car_name: str) -> str | None:
    """Return the normalized car name, or None when invalid."""
    if car_name is None:
        return None
    name = car_name.strip()
    if not CAR_NAME_RE.match(name):
        return None
    return name


class CartelemetryConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Handle a config flow for CARTelemetry."""

    VERSION = 1

    async def async_step_user(self, user_input=None):
        """Handle the initial step."""
        errors = {}
        if user_input is not None:
            car_name = _validate_car_name(user_input.get(CONF_CAR_NAME, ""))
            if car_name is None:
                errors[CONF_CAR_NAME] = "invalid_car_name"
            else:
                await self.async_set_unique_id(car_name)
                self._abort_if_unique_id_configured()
                return self.async_create_entry(
                    title=car_name,
                    data={CONF_CAR_NAME: car_name},
                )

        return self.async_show_form(
            step_id="user",
            data_schema=self._get_schema(),
            errors=errors,
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        # The base OptionsFlow no longer accepts a config_entry argument
        # (HA >= 2024.11); self.config_entry is set by the framework.
        return OptionsFlow()

    def _get_schema(self):
        return vol.Schema(
            {
                vol.Required(CONF_CAR_NAME, default="byd_car"): cv.string,
            }
        )


class OptionsFlow(config_entries.OptionsFlow):
    """Options flow for CARTelemetry."""

    async def async_step_init(self, user_input=None):
        errors = {}
        if user_input is not None:
            car_name = _validate_car_name(user_input.get(CONF_CAR_NAME, ""))
            if car_name is None:
                errors[CONF_CAR_NAME] = "invalid_car_name"
            else:
                options = {
                    CONF_HISTORY_REPLAY: bool(
                        user_input.get(CONF_HISTORY_REPLAY, DEFAULT_HISTORY_REPLAY)
                    ),
                    CONF_REPLAY_RATE: int(
                        user_input.get(CONF_REPLAY_RATE, DEFAULT_REPLAY_RATE)
                    ),
                    CONF_ADAPTIVE_BACKLOG: bool(
                        user_input.get(
                            CONF_ADAPTIVE_BACKLOG, DEFAULT_ADAPTIVE_BACKLOG
                        )
                    ),
                    CONF_BACKLOG_THRESHOLD: int(
                        user_input.get(
                            CONF_BACKLOG_THRESHOLD, DEFAULT_BACKLOG_THRESHOLD
                        )
                    ),
                    CONF_BACKLOG_REPLAY: str(
                        user_input.get(CONF_BACKLOG_REPLAY, DEFAULT_BACKLOG_REPLAY)
                    ),
                }
                name_changed = car_name != self.config_entry.data.get(CONF_CAR_NAME)
                if name_changed:
                    self.hass.config_entries.async_update_entry(
                        self.config_entry,
                        title=car_name,
                        data={CONF_CAR_NAME: car_name},
                    )
                if name_changed or options != dict(self.config_entry.options):
                    self.hass.config_entries.async_update_entry(
                        self.config_entry, options=options
                    )
                    # Reload so the replay throttle and entity mode pick up the
                    # new options / identifier.
                    await self.hass.config_entries.async_reload(self.config_entry.entry_id)
                return self.async_create_entry(title="", data=options)

        return self.async_show_form(
            step_id="init",
            data_schema=vol.Schema(
                {
                    vol.Required(
                        CONF_CAR_NAME,
                        default=self.config_entry.data.get(CONF_CAR_NAME, "byd_car"),
                    ): cv.string,
                    vol.Optional(
                        CONF_HISTORY_REPLAY,
                        default=self.config_entry.options.get(
                            CONF_HISTORY_REPLAY, DEFAULT_HISTORY_REPLAY
                        ),
                    ): cv.boolean,
                    vol.Optional(
                        CONF_REPLAY_RATE,
                        default=self.config_entry.options.get(
                            CONF_REPLAY_RATE, DEFAULT_REPLAY_RATE
                        ),
                    ): vol.All(vol.Coerce(int), vol.Range(min=50, max=5000)),
                    vol.Optional(
                        CONF_ADAPTIVE_BACKLOG,
                        default=self.config_entry.options.get(
                            CONF_ADAPTIVE_BACKLOG, DEFAULT_ADAPTIVE_BACKLOG
                        ),
                    ): cv.boolean,
                    vol.Optional(
                        CONF_BACKLOG_THRESHOLD,
                        default=self.config_entry.options.get(
                            CONF_BACKLOG_THRESHOLD, DEFAULT_BACKLOG_THRESHOLD
                        ),
                    ): vol.All(vol.Coerce(int), vol.Range(min=1, max=1000)),
                    vol.Optional(
                        CONF_BACKLOG_REPLAY,
                        default=self.config_entry.options.get(
                            CONF_BACKLOG_REPLAY, DEFAULT_BACKLOG_REPLAY
                        ),
                    ): vol.In(["throttled", "latest"]),
                }
            ),
            errors=errors,
        )
