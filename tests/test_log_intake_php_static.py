"""Static checks for the log intake PHP (no PHP runtime in the dev box).

Guards the two #85 fixes at the source: millisecond-to-second timestamp
normalization and the preamble (header + diagnostics) round-trip.
"""

from pathlib import Path

PHP = (
    Path(__file__).resolve().parent.parent
    / "docs" / "cartelemetry" / "api" / "logs" / "index.php"
)


def _src():
    return PHP.read_text(encoding="utf-8")


def test_timestamp_is_normalized_from_milliseconds():
    src = _src()
    assert "function log_ts_seconds" in src
    assert "intdiv($ts, 1000)" in src
    assert "gmdate('Y-m-d\\TH:i:s', log_ts_seconds($r['ts']))" in src


def test_naive_millisecond_formatting_is_gone():
    src = _src()
    assert "gmdate('Y-m-d\\TH:i:s', (int)$r['ts'])" not in src


def test_preamble_is_prepended():
    src = _src()
    assert "is_string($rec['preamble'] ?? null)" in src
