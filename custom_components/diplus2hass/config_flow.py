"""Config flow for diplus2hass integration."""

import voluptuous as vol

from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.helpers import config_validation as cv

from .const import DOMAIN, CONF_CAR_NAME


class Diplus2HassConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Handle a config flow for diplus2hass."""

    VERSION = 1

    async def async_step_user(self, user_input=None):
        """Handle the initial step."""
        if user_input is not None:
            car_name = user_input.get(CONF_CAR_NAME, "byd_car")
            await self.async_set_unique_id(car_name)
            self._abort_if_unique_id_configured()
            return self.async_create_entry(
                title=car_name,
                data=user_input,
            )

        return self.async_show_form(
            step_id="user",
            data_schema=self._get_schema(),
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        return OptionsFlow(config_entry)

    def _get_schema(self):
        return vol.Schema(
            {
                vol.Required(CONF_CAR_NAME, default="byd_car"): cv.string,
            }
        )


class OptionsFlow(config_entries.OptionsFlow):
    """Options flow for diplus2hass."""

    async def async_step_init(self, user_input=None):
        if user_input is not None:
            car_name = user_input.get(CONF_CAR_NAME)
            if car_name:
                self.hass.config_entries.async_update_entry(
                    self.config_entry,
                    title=car_name,
                )
            return self.async_create_entry(title="", data=user_input)

        return self.async_show_form(
            step_id="init",
            data_schema=vol.Schema(
                {
                    vol.Optional(
                        CONF_CAR_NAME,
                        default=self.config_entry.options.get(
                            CONF_CAR_NAME, self.config_entry.data.get(CONF_CAR_NAME, "byd_car")
                        ),
                    ): cv.string,
                }
            ),
        )
