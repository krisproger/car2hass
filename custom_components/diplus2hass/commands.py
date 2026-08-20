"""Command registry for DiPlus-to-hass.

Mirrors the Android CommandRegistry and defines which commands are exposed
as controllable entities in Home Assistant (switch, number, select, button).

Entities with ``state_signal`` stay synchronized with the vehicle: every
telemetry batch updates the entity from the linked signal, so manual changes
made in the car are reflected in HA (and vice versa, HA-set values are
confirmed by telemetry). The links mirror the app's sensor_command_map.json.
"""

from dataclasses import dataclass, field
from typing import Any


@dataclass
class SwitchCommand:
    """A paired on/off command exposed as a Switch entity.

    state_signal optionally maps to a telemetry signal key so the UI toggle
    stays synchronized with the vehicle state.
    """

    key: str
    name: str
    category: str
    turn_on_cmd: str
    turn_off_cmd: str
    icon_on: str = "mdi:toggle-switch"
    icon_off: str = "mdi:toggle-switch-off"
    state_signal: str | None = None


@dataclass
class NumberCommand:
    """A command that accepts a numeric value, exposed as a Number entity.

    state_signal optionally maps to a telemetry signal key so the number
    shows the actual vehicle value, not just the last one set from HA.
    """

    key: str
    name: str
    category: str
    command_template: str
    min_value: float
    max_value: float
    step: float = 1.0
    unit: str | None = None
    icon: str = "mdi:numeric"
    state_signal: str | None = None


@dataclass
class SelectCommand:
    """A command that accepts one of predefined values, exposed as a Select entity.

    state_signal optionally maps to a telemetry signal key; state_map then
    translates the signal's English label (lowercased, as sent by the app)
    to the Android command value id. Signal values missing from state_map
    are ignored (the select keeps its current option).
    """

    key: str
    name: str
    category: str
    command_template: str
    options: dict[str, str]
    icon: str = "mdi:form-dropdown"
    state_signal: str | None = None
    state_map: dict[str, str] = field(default_factory=dict)


@dataclass
class ButtonCommand:
    """A one-shot command exposed as a Button entity."""

    key: str
    name: str
    category: str
    command: str
    icon: str = "mdi:gesture-tap-button"


# Pair on/off commands as Switch entities.
SWITCH_COMMANDS = [
    SwitchCommand(
        "ac", "A/C", "Climate", "ac_on", "ac_off",
        icon_on="mdi:air-conditioner", icon_off="mdi:air-conditioner",
        state_signal="ac_state",
    ),
    SwitchCommand(
        "front_defrost", "Front defrost", "Climate", "front_defrost_on", "front_defrost_off",
        icon_on="mdi:car-defrost-front", icon_off="mdi:car-defrost-front",
    ),
    SwitchCommand(
        "steering_heat", "Steering wheel heating", "Climate", "steering_heat_on", "steering_heat_off",
        icon_on="mdi:steering", icon_off="mdi:steering",
    ),
    SwitchCommand(
        "mirror_heat", "Mirror heating", "Climate", "mirror_heat_on", "mirror_heat_off",
        icon_on="mdi:car-side-mirror", icon_off="mdi:car-side-mirror",
    ),
    SwitchCommand(
        "hazard", "Hazard lights", "Lights", "hazard_on", "hazard_off",
        icon_on="mdi:alarm-light", icon_off="mdi:alarm-light-off",
        state_signal="hazard",
    ),
    SwitchCommand(
        "fog", "Fog lights", "Lights", "fog_on", "fog_off",
        icon_on="mdi:car-light-fog", icon_off="mdi:car-light-fog",
        state_signal="front_fog",
    ),
    SwitchCommand(
        "drl", "DRL", "Lights", "drl_on", "drl_off",
        icon_on="mdi:car-light-high", icon_off="mdi:car-light-dimmed",
        state_signal="drl",
    ),
    SwitchCommand(
        "interior_light", "Interior light", "Lights", "interior_light_on", "interior_light_off",
        icon_on="mdi:lightbulb", icon_off="mdi:lightbulb-off",
        state_signal="footwell_light",
    ),
    SwitchCommand(
        "ambilight", "Ambilight", "Lights", "ambilight_on", "ambilight_off",
        icon_on="mdi:light-strip", icon_off="mdi:light-strip",
    ),
    SwitchCommand(
        "auto_high_beam", "Auto high beam", "Lights", "auto_high_beam_on", "auto_high_beam_off",
        icon_on="mdi:car-high-beam", icon_off="mdi:car-low-beam",
    ),
    SwitchCommand(
        "dashcam", "Dashcam", "Sentry", "dashcam_on", "dashcam_off",
        icon_on="mdi:video", icon_off="mdi:video-off",
        state_signal="dashcam_state",
    ),
]

# Numeric setters exposed as Number entities.
NUMBER_COMMANDS = [
    NumberCommand("ac_temp", "A/C temperature", "Climate", "ac_temp", 17, 30, 1, "°C", "mdi:thermometer",
                  state_signal="ac_set_temp"),
    NumberCommand("ac_fan", "A/C fan speed", "Climate", "ac_fan", 1, 7, 1, None, "mdi:fan",
                  state_signal="fan_speed"),
    NumberCommand("window_driver", "Driver window", "Windows", "window_driver", 0, 100, 1, "%", "mdi:car-door",
                  state_signal="window_fl"),
    NumberCommand("window_passenger", "Passenger window", "Windows", "window_passenger", 0, 100, 1, "%", "mdi:car-door",
                  state_signal="window_fr"),
    NumberCommand("window_rear_left", "Rear-left window", "Windows", "window_rear_left", 0, 100, 1, "%", "mdi:car-door",
                  state_signal="window_rl"),
    NumberCommand("window_rear_right", "Rear-right window", "Windows", "window_rear_right", 0, 100, 1, "%", "mdi:car-door",
                  state_signal="window_rr"),
    NumberCommand("sunroof", "Sunroof", "Windows", "sunroof", 0, 100, 1, "%", "mdi:car-convertible",
                  state_signal="sunroof"),
    NumberCommand("sunshade", "Sunshade", "Windows", "sunshade", 0, 100, 1, "%", "mdi:car-convertible",
                  state_signal="sunshade"),
    NumberCommand("charge_soc", "Target charge SOC", "Charging", "charge_soc", 15, 70, 1, "%", "mdi:battery"),
    NumberCommand("headlight_level", "Headlight level", "Comfort", "headlight_level", 0, 5, 1, None, "mdi:car-high-beam"),
    NumberCommand("volume", "Media volume", "Volume", "volume", 0, 100, 1, "%", "mdi:volume-high",
                  state_signal="media_volume"),
    NumberCommand("nav_volume", "Navigation volume", "Volume", "nav_volume", 0, 10, 1, None, "mdi:volume-medium",
                  state_signal="navigation_volume"),
    NumberCommand("ext_volume", "External volume", "Volume", "ext_volume", 0, 99, 1, "%", "mdi:volume-medium"),
]

# Enum setters exposed as Select entities.
# options: HA option label -> Android command value id (passed in params.value)
# state_map: signal English label (lowercased, as sent by the app) -> Android value id
SELECT_COMMANDS = [
    SelectCommand(
        "ac_airflow", "A/C airflow mode", "Climate", "ac_airflow",
        {
            "Face": "face",
            "Face + feet": "face_feet",
            "Feet": "feet",
            "Feet + defrost": "feet_defrost",
            "Defrost": "defrost",
            "Face + feet + defrost": "face_feet_defrost",
            "Face + defrost": "face_defrost",
        },
        "mdi:fan",
        state_signal="ac_airflow_mode",
        state_map={
            "face": "face",
            "face+feet": "face_feet",
            "face/feet": "face_feet",
            "feet": "feet",
            "feet+defrost": "feet_defrost",
            "defrost": "defrost",
            "face+feet+defrost": "face_feet_defrost",
            "face+defrost": "face_defrost",
        },
    ),
    SelectCommand(
        "ac_recirc", "A/C recirculation", "Climate", "ac_recirc",
        {"Recirculation": "recirc", "Fresh air": "fresh"},
        "mdi:air-recirculator",
        state_signal="ac_recirculation",
        state_map={"recirc": "recirc", "fresh": "fresh", "recirculation": "recirc", "external": "fresh"},
    ),
    SelectCommand(
        "driver_seat_heat", "Driver seat heating", "Climate", "driver_seat_heat",
        {"Off": "off", "Low": "low", "High": "high"},
        "mdi:car-seat-heater",
    ),
    SelectCommand(
        "passenger_seat_heat", "Passenger seat heating", "Climate", "passenger_seat_heat",
        {"Off": "off", "Low": "low", "High": "high"},
        "mdi:car-seat-heater",
    ),
    SelectCommand(
        "driver_seat_vent", "Driver seat ventilation", "Climate", "driver_seat_vent",
        {"Off": "off", "Low": "low", "High": "high"},
        "mdi:car-seat-cooler",
    ),
    SelectCommand(
        "passenger_seat_vent", "Passenger seat ventilation", "Climate", "passenger_seat_vent",
        {"Off": "off", "Low": "low", "High": "high"},
        "mdi:car-seat-cooler",
    ),
    SelectCommand(
        "powertrain_mode", "Powertrain mode", "Modes", "powertrain_mode",
        {"HEV": "hev", "EV": "ev", "Force EV": "force_ev"},
        "mdi:car-engine",
        state_signal="powertrain_mode",
        state_map={"hev": "hev", "ev": "ev", "forced ev": "force_ev"},
    ),
    SelectCommand(
        "drive_mode", "Drive mode", "Modes", "drive_mode",
        {"ECO": "eco", "SPORT": "sport", "NORMAL": "normal", "Snow": "snow"},
        "mdi:car-settings",
        state_signal="drive_mode",
        state_map={"eco": "eco", "sport": "sport", "normal": "normal"},
    ),
    SelectCommand(
        "charge_save", "Charge saving", "Modes", "charge_save",
        {"Smart": "smart", "Force": "force"},
        "mdi:battery-charging",
    ),
    SelectCommand(
        "regen", "Regeneration", "Modes", "regen",
        {"Standard": "standard", "High": "high"},
        "mdi:battery-arrow-down",
    ),
    SelectCommand(
        "steering_assist", "Steering assist", "Modes", "steering_assist",
        {"Comfort": "comfort", "Sport": "sport"},
        "mdi:steering",
    ),
    SelectCommand(
        "brake_assist", "Brake assist", "Modes", "brake_assist",
        {"Standard": "standard", "Comfort": "comfort"},
        "mdi:car-brake-alert",
    ),
    SelectCommand(
        "active_brake", "Active braking", "Modes", "active_brake",
        {"On": "on", "Off": "off"},
        "mdi:car-brake-alert",
    ),
    SelectCommand(
        "turn_signal", "Turn signal", "Lights", "turn_signal",
        {"Left": "left", "Right": "right"},
        "mdi:arrow-left-right",
        state_signal="turn_signal",
        state_map={"left": "left", "left2": "left", "right": "right", "right2": "right"},
    ),
    SelectCommand(
        "child_lock_left", "Child lock left", "Doors", "child_lock_left",
        {"On": "on", "Off": "off"},
        "mdi:account-lock",
        state_signal="rear_left_child_lock",
        state_map={"locked": "on", "unlocked": "off"},
    ),
    SelectCommand(
        "child_lock_right", "Child lock right", "Doors", "child_lock_right",
        {"On": "on", "Off": "off"},
        "mdi:account-lock",
        state_signal="rear_right_child_lock",
        state_map={"locked": "on", "unlocked": "off"},
    ),
    SelectCommand(
        "sentry", "Sentry mode", "Sentry", "sentry",
        {
            "Engine-off sentry on": "engine_off_on",
            "Time-lapse sentry on": "time_lapse_on",
            "Engine-off sentry off": "engine_off_off",
        },
        "mdi:shield-car",
    ),
    SelectCommand(
        "theme", "Theme", "System", "theme",
        {"Dark": "dark", "Light": "light"},
        "mdi:theme-light-dark",
    ),
]

# One-shot commands exposed as Button entities.
BUTTON_COMMANDS = [
    ButtonCommand("ac_auto", "A/C auto", "Climate", "ac_auto", "mdi:autorenew"),
    ButtonCommand("ac_temp_up", "Temperature +", "Climate", "ac_temp_up", "mdi:thermometer-chevron-up"),
    ButtonCommand("ac_temp_down", "Temperature -", "Climate", "ac_temp_down", "mdi:thermometer-chevron-down"),
    ButtonCommand("ac_fan_up", "Fan +", "Climate", "ac_fan_up", "mdi:fan-chevron-up"),
    ButtonCommand("ac_fan_down", "Fan -", "Climate", "ac_fan_down", "mdi:fan-chevron-down"),
    ButtonCommand("windows_close_all", "Close all windows", "Windows", "windows_close_all", "mdi:window-closed"),
    ButtonCommand("windows_vent", "Ventilate", "Windows", "windows_vent", "mdi:window-open"),
    ButtonCommand("doors_unlock", "Unlock doors", "Doors", "doors_unlock", "mdi:lock-open"),
    ButtonCommand("doors_lock", "Lock doors", "Doors", "doors_lock", "mdi:lock"),
    ButtonCommand("trunk_open", "Open trunk", "Doors", "trunk_open", "mdi:car-back"),
    ButtonCommand("trunk_close", "Close trunk", "Doors", "trunk_close", "mdi:car-back"),
    ButtonCommand("volume_up", "Volume +", "Volume", "volume_up", "mdi:volume-plus"),
    ButtonCommand("volume_down", "Volume -", "Volume", "volume_down", "mdi:volume-minus"),
]


def all_command_keys() -> set[str]:
    """Return the set of all Android command ids referenced by HA entities."""
    keys: set[str] = set()
    for sc in SWITCH_COMMANDS:
        keys.add(sc.turn_on_cmd)
        keys.add(sc.turn_off_cmd)
    for nc in NUMBER_COMMANDS:
        keys.add(nc.command_template)
    for sc in SELECT_COMMANDS:
        keys.add(sc.command_template)
    for bc in BUTTON_COMMANDS:
        keys.add(bc.command)
    return keys
