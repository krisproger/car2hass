"""Tests for cartelemetry command registry."""

import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT / "custom_components" / "cartelemetry"))

from commands import (
    BUTTON_COMMANDS,
    NUMBER_COMMANDS,
    SELECT_COMMANDS,
    SWITCH_COMMANDS,
    all_command_keys,
)


def test_switch_commands_have_paired_ids():
    for cmd in SWITCH_COMMANDS:
        assert cmd.turn_on_cmd, f"{cmd.key} missing turn_on_cmd"
        assert cmd.turn_off_cmd, f"{cmd.key} missing turn_off_cmd"
        assert cmd.turn_on_cmd != cmd.turn_off_cmd


def test_number_commands_have_range():
    for cmd in NUMBER_COMMANDS:
        assert cmd.min_value < cmd.max_value, f"{cmd.key} invalid range"
        assert cmd.step > 0, f"{cmd.key} invalid step"
        assert cmd.command_template, f"{cmd.key} missing command_template"


def test_select_commands_have_options():
    for cmd in SELECT_COMMANDS:
        assert cmd.options, f"{cmd.key} missing options"
        assert cmd.command_template, f"{cmd.key} missing command_template"
        # Values must be non-empty strings.
        for label, value in cmd.options.items():
            assert label and value, f"{cmd.key} empty option"


def test_button_commands_have_command():
    for cmd in BUTTON_COMMANDS:
        assert cmd.command, f"{cmd.key} missing command"


def test_all_command_keys_are_unique():
    keys = all_command_keys()
    assert len(keys) == (
        len(SWITCH_COMMANDS) * 2
        + len(NUMBER_COMMANDS)
        + len(SELECT_COMMANDS)
        + len(BUTTON_COMMANDS)
    ), "Duplicate command keys detected"


def test_state_signals_reference_known_sensors():
    """Every state_signal link must point at an existing telemetry signal."""
    from const import BINARY_SENSORS, ENUM_SENSORS, NUMERIC_SENSORS

    known = set(NUMERIC_SENSORS) | set(ENUM_SENSORS) | set(BINARY_SENSORS)
    for cmd in SWITCH_COMMANDS:
        if cmd.state_signal:
            assert cmd.state_signal in known, f"switch {cmd.key}: unknown state_signal {cmd.state_signal}"
    for cmd in NUMBER_COMMANDS:
        if cmd.state_signal:
            assert cmd.state_signal in known, f"number {cmd.key}: unknown state_signal {cmd.state_signal}"
    for cmd in SELECT_COMMANDS:
        if cmd.state_signal:
            assert cmd.state_signal in known, f"select {cmd.key}: unknown state_signal {cmd.state_signal}"


def test_number_state_signals_are_numeric_sensors():
    """Number entities can only mirror numeric telemetry signals."""
    from const import NUMERIC_SENSORS

    for cmd in NUMBER_COMMANDS:
        if cmd.state_signal:
            assert cmd.state_signal in NUMERIC_SENSORS, (
                f"number {cmd.key}: state_signal {cmd.state_signal} is not a numeric sensor"
            )


def test_select_state_map_targets_are_valid_options():
    """Every state_map value must resolve to one of the select's command values."""
    for cmd in SELECT_COMMANDS:
        valid_values = set(cmd.options.values())
        for signal_label, value_id in cmd.state_map.items():
            assert signal_label == signal_label.lower(), (
                f"select {cmd.key}: state_map key '{signal_label}' must be lowercase"
            )
            assert value_id in valid_values, (
                f"select {cmd.key}: state_map value '{value_id}' is not among options {sorted(valid_values)}"
            )
