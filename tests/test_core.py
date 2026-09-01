"""Unit tests for the integration's pure business logic (core.py).

These tests run without Home Assistant installed.
"""

from datetime import datetime, timedelta, timezone

import pytest

import core


NOW = datetime(2026, 7, 23, 12, 0, 0, tzinfo=timezone.utc)


# --- validate_batch / aggregate_batch ---

def test_validate_batch_sorts_chronologically():
    batch = [
        {"t": 3, "s": {"speed": 30}, "g": {}},
        {"t": 1, "s": {"speed": 10}, "g": {}},
        {"t": 2, "s": {"speed": 20}, "g": {}},
    ]
    out = core.validate_batch(batch)
    assert [s["t"] for s in out] == [1, 2, 3]


def test_validate_batch_rejects_non_dict_item():
    with pytest.raises(core.BatchValidationError):
        core.validate_batch([{"t": 1, "s": {}, "g": {}}, "not-a-dict"])


def test_validate_batch_rejects_non_dict_s_or_g():
    with pytest.raises(core.BatchValidationError):
        core.validate_batch([{"t": 1, "s": "x", "g": {}}])
    with pytest.raises(core.BatchValidationError):
        core.validate_batch([{"t": 1, "s": {}, "g": 42}])


def test_validate_batch_allows_missing_s_and_g():
    out = core.validate_batch([{"t": 5}])
    assert out == [{"t": 5}]


def test_validate_batch_coerces_string_t_and_sorts():
    out = core.validate_batch([{"t": "3", "s": {}, "g": {}}, {"t": 1, "s": {}, "g": {}}])
    assert [s["t"] for s in out] == [1, 3.0]


def test_validate_batch_rejects_non_numeric_t():
    with pytest.raises(core.BatchValidationError):
        core.validate_batch([{"t": "abc", "s": {}, "g": {}}])
    with pytest.raises(core.BatchValidationError):
        core.validate_batch([{"t": None, "s": {}, "g": {}}])
    with pytest.raises(core.BatchValidationError):
        core.validate_batch([{"t": True, "s": {}, "g": {}}])


def test_aggregate_latest_value_wins_and_gps():
    batch = core.validate_batch([
        {"t": 1, "s": {"speed": 10}, "g": {"lat": 55.0, "lon": 37.0, "a": 5}},
        {"t": 2, "s": {"speed": 20, "soc": 80}, "g": {}},
    ])
    agg = core.aggregate_batch(batch)
    assert agg["latest_signals"] == {"speed": 20, "soc": 80}
    assert agg["latitude"] == 55.0
    assert agg["longitude"] == 37.0
    assert agg["accuracy"] == 5
    assert agg["timestamp"] == 2


def test_aggregate_skips_invalid_gps():
    batch = core.validate_batch([
        {"t": 1, "s": {}, "g": {"lat": "bad", "lon": 37.0}},
        {"t": 2, "s": {}, "g": {"lat": None, "lon": None}},
    ])
    agg = core.aggregate_batch(batch)
    assert agg["latitude"] is None
    assert agg["longitude"] is None


def test_aggregate_empty_batch():
    agg = core.aggregate_batch([])
    assert agg["latest_signals"] == {}
    assert agg["latitude"] is None
    assert agg["timestamp"] == 0


def test_aggregate_uses_fix_time_for_last_location():
    batch = core.validate_batch([
        {"t": 10, "s": {}, "g": {"lat": 55.0, "lon": 37.0, "a": 5, "t": 9}},
        {"t": 11, "s": {}, "g": {}},
    ])
    agg = core.aggregate_batch(batch)
    # GPS carried by the first snapshot, but its fix time (9) is what the
    # location belongs to, not the later collection time (10/11).
    assert agg["latitude"] == 55.0
    assert agg["fix_timestamp"] == 9


def test_aggregate_falls_back_to_snapshot_time_without_fix_time():
    batch = core.validate_batch([
        {"t": 10, "s": {}, "g": {"lat": 55.0, "lon": 37.0, "a": 5}},
    ])
    agg = core.aggregate_batch(batch)
    assert agg["fix_timestamp"] == 10


def test_aggregate_fix_time_tolerates_bad_value():
    batch = core.validate_batch([
        {"t": 10, "s": {}, "g": {"lat": 55.0, "lon": 37.0, "t": "bad"}},
    ])
    agg = core.aggregate_batch(batch)
    assert agg["fix_timestamp"] == 10


# --- is_command_expired ---

def test_command_expired_by_created():
    cmd = {"created": (NOW - timedelta(minutes=2)).isoformat()}
    assert core.is_command_expired(cmd, NOW) is True


def test_command_not_expired_when_fresh():
    cmd = {"created": NOW.isoformat()}
    assert core.is_command_expired(cmd, NOW) is False


def test_command_expired_by_delivered_at():
    cmd = {
        "created": (NOW - timedelta(minutes=3)).isoformat(),
        "delivered_at": (NOW - timedelta(minutes=2)).isoformat(),
    }
    assert core.is_command_expired(cmd, NOW) is True


def test_command_expired_when_created_old_even_if_delivered_recently():
    # Existing production semantics: `created` and `delivered_at` are checked
    # independently, so an old `created` alone marks the command expired.
    cmd = {
        "created": (NOW - timedelta(minutes=3)).isoformat(),
        "delivered_at": NOW.isoformat(),
    }
    assert core.is_command_expired(cmd, NOW) is True


def test_command_expired_handles_garbage_timestamps():
    assert core.is_command_expired({"created": "not-a-date"}, NOW) is False
    assert core.is_command_expired({}, NOW) is False


# --- enqueue_command ---

def test_enqueue_adds_entry():
    commands = []
    entry, created = core.enqueue_command(commands, "ac_on", None, NOW)
    assert created is True
    assert commands[0]["command"] == "ac_on"
    assert commands[0]["status"] == "pending"
    assert commands[0]["delivered"] is False
    assert entry["id"]


def test_enqueue_deduplicates_pending():
    commands = []
    e1, _ = core.enqueue_command(commands, "ac_on", None, NOW)
    e2, created = core.enqueue_command(commands, "ac_on", None, NOW)
    assert created is False
    assert e1["id"] == e2["id"]
    assert len(commands) == 1


def test_enqueue_dedup_considers_params():
    commands = []
    core.enqueue_command(commands, "window_driver", {"value": 50}, NOW)
    _, created = core.enqueue_command(commands, "window_driver", {"value": 80}, NOW)
    assert created is True
    assert len(commands) == 2


def test_enqueue_no_dedup_after_delivery():
    commands = []
    e1, _ = core.enqueue_command(commands, "ac_on", None, NOW)
    commands[0]["delivered"] = True
    e2, created = core.enqueue_command(commands, "ac_on", None, NOW)
    assert created is True
    assert e1["id"] != e2["id"]


def test_enqueue_prunes_expired_before_length_check():
    old = {"id": "x", "command": "a", "params": None, "delivered": False,
           "created": (NOW - timedelta(minutes=5)).isoformat()}
    commands = [dict(old) for _ in range(50)]
    _, created = core.enqueue_command(commands, "b", None, NOW)
    assert created is True
    assert len(commands) == 1
    assert commands[0]["command"] == "b"


def test_enqueue_raises_when_full():
    commands = [
        {"id": str(i), "command": f"c{i}", "params": None, "delivered": False,
         "created": NOW.isoformat()}
        for i in range(50)
    ]
    with pytest.raises(core.QueueFullError):
        core.enqueue_command(commands, "d", None, NOW)


# --- check_rate_limit ---

def test_rate_limit_allows_under_limit():
    buckets = {}
    assert core.check_rate_limit(buckets, "car", 1000.0, 60.0, 3) is True
    assert core.check_rate_limit(buckets, "car", 1001.0, 60.0, 3) is True


def test_rate_limit_blocks_at_limit():
    buckets = {}
    for i in range(3):
        assert core.check_rate_limit(buckets, "car", 1000.0 + i, 60.0, 3) is True
    assert core.check_rate_limit(buckets, "car", 1003.0, 60.0, 3) is False


def test_rate_limit_window_prunes_old():
    buckets = {"car": [900.0, 901.0, 902.0]}
    assert core.check_rate_limit(buckets, "car", 1000.0, 60.0, 3) is True
    assert buckets["car"] == [1000.0]


def test_rate_limit_keys_are_independent():
    buckets = {}
    for i in range(3):
        core.check_rate_limit(buckets, "car_a", 1000.0 + i, 60.0, 3)
    assert core.check_rate_limit(buckets, "car_b", 1003.0, 60.0, 3) is True


# --- build_signal_index ---

def test_signal_index_groups_values_chronologically():
    batch = core.validate_batch([
        {"t": 2, "s": {"speed": 20, "soc": 80}, "g": {}},
        {"t": 1, "s": {"speed": 10}, "g": {}},
        {"t": 3, "s": {"soc": 79}, "g": {}},
    ])
    index = core.build_signal_index(batch)
    assert index["speed"] == [(1, 10), (2, 20)]
    assert index["soc"] == [(2, 80), (3, 79)]
    assert "other" not in index


def test_signal_index_skips_none_values():
    batch = core.validate_batch([
        {"t": 1, "s": {"speed": None}, "g": {}},
        {"t": 2, "s": {"speed": 5}, "g": {}},
    ])
    index = core.build_signal_index(batch)
    assert index["speed"] == [(2, 5)]


def test_signal_index_empty_batch():
    assert core.build_signal_index([]) == {}


# --- build_gps_track ---

def test_gps_track_collects_valid_points():
    batch = core.validate_batch([
        {"t": 1, "s": {}, "g": {"lat": 55.0, "lon": 37.0, "a": 5}},
        {"t": 2, "s": {}, "g": {}},
        {"t": 3, "s": {}, "g": {"lat": "55.5", "lon": "37.5"}},
    ])
    track = core.build_gps_track(batch)
    assert track == [(1, 55.0, 37.0, 5.0), (3, 55.5, 37.5, 0.0)]


def test_gps_track_skips_invalid_and_none():
    batch = core.validate_batch([
        {"t": 1, "s": {}, "g": {"lat": "bad", "lon": 37.0}},
        {"t": 2, "s": {}, "g": {"lat": None, "lon": 37.0}},
    ])
    assert core.build_gps_track(batch) == []


def test_gps_track_uses_fix_time_when_present():
    batch = core.validate_batch([
        {"t": 10, "s": {}, "g": {"lat": 55.0, "lon": 37.0, "a": 5, "t": 9}},
        {"t": 11, "s": {}, "g": {"lat": 55.5, "lon": 37.5, "a": 8}},
    ])
    track = core.build_gps_track(batch)
    # First point attributed to its fix time (9), second falls back to the
    # snapshot collection time (11) because it carries no g.t.
    assert track == [(9, 55.0, 37.0, 5.0), (11, 55.5, 37.5, 8.0)]


# --- geofence dynamic keys ---

def test_find_geofence_keys_detects_geo_keys():
    signals = {
        "speed": 12,
        "geo_ab12cd34": "inside",
        "geo_xy98": "outside",
        "geo_ab12cd34_name": "home",
    }
    assert core.find_geofence_keys(signals) == ["geo_ab12cd34", "geo_xy98"]


def test_find_geofence_keys_empty_when_no_geo():
    assert core.find_geofence_keys({"speed": 1, "soc": 80}) == []
    assert core.find_geofence_keys({}) == []


def test_geofence_zone_name_prefers_companion_name():
    signals = {"geo_ab12cd34": "inside", "geo_ab12cd34_name": "дом"}
    assert core.geofence_zone_name(signals, "geo_ab12cd34") == "дом"


def test_geofence_zone_name_falls_back_to_zone_id():
    assert core.geofence_zone_name({}, "geo_ab12cd34") == "ab12cd34"
    assert core.geofence_zone_name({"geo_ab12cd34_name": "  "}, "geo_ab12cd34") == "ab12cd34"


def test_geofence_keys_flow_through_batch_aggregation():
    """geo_* signals from the app must survive validation and aggregation."""
    batch = core.validate_batch([
        {"t": 1, "s": {"geo_ab12cd34": "outside"}, "g": {}},
        {"t": 2, "s": {"geo_ab12cd34": "inside", "geo_ab12cd34_name": "home"}, "g": {}},
    ])
    agg = core.aggregate_batch(batch)
    assert agg["latest_signals"]["geo_ab12cd34"] == "inside"
    assert core.find_geofence_keys(agg["latest_signals"]) == ["geo_ab12cd34"]
    index = core.build_signal_index(batch)
    assert index["geo_ab12cd34"] == [(1, "outside"), (2, "inside")]


# --- supports_async_set_timestamp ---

def test_supports_timestamp_for_2024_6_and_newer():
    assert core.supports_async_set_timestamp(2024, 6) is True
    assert core.supports_async_set_timestamp(2025, 1) is True


def test_supports_timestamp_false_before_2024_6():
    assert core.supports_async_set_timestamp(2024, 5) is False
    assert core.supports_async_set_timestamp(2023, 12) is False
