"""Phone-device routing tests for the HA integration (pure logic, no HA).

A telemetry POST may carry a top-level ``dev`` block. ``core.route_device_signals``
is the exact selection path ``_process_batch`` uses to build the maps the car and
phone entities read: car maps (``signals`` / ``signal_index``) never contain
``phone_*`` keys, and phone maps (``phone_signals`` / ``phone_signal_index``)
carry ``phone_*`` keys only while the payload is in phone mode. A ``phone_*`` key
sent by a car payload is ignored entirely (neither car nor phone).
"""

import core


def _route(payload):
    """Mirror the real ingest path: validate, aggregate, then select per device."""
    device = core.parse_device(payload.get("dev"))
    batch = core.validate_batch(payload.get("batch", []))
    latest = core.aggregate_batch(batch)["latest_signals"]
    return device, core.route_device_signals(batch, latest, device["class"])


def test_settings_enums_exist():
    assert core.DEVICE_CLASS_CAR == "car"
    assert core.DEVICE_CLASS_PHONE == "phone"
    assert core.PHONE_SIGNAL_PREFIX == "phone_"


def test_parse_device_absent_is_car():
    assert core.parse_device(None)["class"] == "car"
    assert core.parse_device({})["class"] == "car"
    assert core.parse_device("phone")["class"] == "car"


def test_parse_device_phone_descriptor():
    device = core.parse_device(
        {"class": "phone", "id": "abc123", "name": "Pixel 8"}
    )
    assert device["class"] == "phone"
    assert device["id"] == "abc123"
    assert device["name"] == "Pixel 8"


def test_parse_device_unknown_class_falls_back_to_car():
    assert core.parse_device({"class": "tablet"})["class"] == "car"


def test_is_phone_signal():
    assert core.is_phone_signal("phone_battery_level") is True
    assert core.is_phone_signal("speed") is False
    assert core.is_phone_signal(None) is False


def test_phone_payload_makes_phone_key_visible_to_phone_not_car():
    """A dev.class=phone payload exposes its phone_* keys only to the phone."""
    device, routed = _route({
        "car_name": "byd_car",
        "dev": {"class": "phone", "id": "abc123", "name": "Pixel 8"},
        "batch": [
            {"t": 1, "g": {}, "s": {"phone_battery_level": 80,
                                    "phone_is_charging": "true"}},
        ],
    })
    assert device["class"] == "phone"
    assert routed["phone_signals"] == {
        "phone_battery_level": 80, "phone_is_charging": "true",
    }
    assert "phone_battery_level" in routed["phone_signal_index"]
    assert "phone_is_charging" in routed["phone_signal_index"]
    assert routed["signals"] == {}
    assert routed["signal_index"] == {}


def test_car_payload_does_not_expose_phone_key_to_phone():
    """Without phone mode a phone_* key is ignored — not phone, not car."""
    device, routed = _route(
        {"batch": [{"t": 1, "g": {}, "s": {"phone_battery_level": 50}}]}
    )
    assert device["class"] == "car"
    assert routed["phone_signals"] == {}
    assert routed["phone_signal_index"] == {}
    assert routed["signals"] == {}
    assert routed["signal_index"] == {}


def test_normal_car_key_updates_car():
    device, routed = _route(
        {"batch": [{"t": 1, "g": {}, "s": {"speed": 80}}]}
    )
    assert device["class"] == "car"
    assert routed["signals"] == {"speed": 80}
    assert routed["signal_index"]["speed"] == [(1, 80)]
    assert routed["phone_signals"] == {}
    assert routed["phone_signal_index"] == {}


def test_mixed_phone_payload_routes_by_key():
    """In phone mode phone_* keys go to the phone, the rest to the car."""
    _, routed = _route({
        "dev": {"class": "phone", "id": "abc123", "name": "Pixel 8"},
        "batch": [{"t": 1, "g": {}, "s": {"phone_battery_level": 80, "speed": 42}}],
    })
    assert routed["phone_signals"] == {"phone_battery_level": 80}
    assert routed["signals"] == {"speed": 42}
    assert routed["phone_signal_index"]["phone_battery_level"] == [(1, 80)]
    assert routed["signal_index"]["speed"] == [(1, 42)]
