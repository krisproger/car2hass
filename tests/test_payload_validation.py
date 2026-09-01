"""Tests for REST payload validation logic (HA-independent)."""

import voluptuous as vol

# Replicate the validation schema used by the integration so these tests
# do not depend on the full Home Assistant runtime.
MAX_BATCH_SNAPSHOTS = 1000

_BATCH_SCHEMA = vol.Schema(
    {
        vol.Required("car_name"): str,
        vol.Optional("vvn", default=""): str,
        vol.Optional("firmware", default=""): str,
        vol.Optional("batch", default=[]): vol.All(
            list,
            vol.Length(max=MAX_BATCH_SNAPSHOTS),
        ),
    },
    extra=vol.ALLOW_EXTRA,
)


def test_valid_minimal_payload():
    data = _BATCH_SCHEMA({"car_name": "byd_car"})
    assert data["car_name"] == "byd_car"
    assert data["vvn"] == ""
    assert data["firmware"] == ""
    assert data["batch"] == []


def test_valid_full_payload():
    data = _BATCH_SCHEMA({
        "car_name": "byd_car",
        "vvn": "VVIN123",
        "firmware": "FW1",
        "batch": [
            {"t": 123, "g": {"lat": 55.7, "lon": 37.6, "a": 10}, "s": {"speed": 80}},
        ],
    })
    assert len(data["batch"]) == 1


def test_missing_car_name_raises():
    try:
        _BATCH_SCHEMA({"batch": []})
        assert False, "Expected Invalid"
    except vol.Invalid:
        pass


def test_batch_too_large_raises():
    payload = {"car_name": "byd_car", "batch": [{"t": i} for i in range(1001)]}
    try:
        _BATCH_SCHEMA(payload)
        assert False, "Expected Invalid"
    except vol.Invalid:
        pass
