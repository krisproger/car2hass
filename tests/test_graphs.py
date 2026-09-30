"""Smoke test for the project graph generator (scripts/graphs/build_graphs.py)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts" / "graphs"))
import build_graphs  # noqa: E402


def test_graphs_build():
    g = build_graphs.build()
    fn, sn = g["functional"], g["semantic"]

    assert len(fn["nodes"]) > 500, "functional graph too small"
    assert len(fn["edges"]) > 500, "functional graph has too few edges"
    assert len(sn["nodes"]) > 200, "semantic graph too small"
    assert len(sn["edges"]) > 300, "semantic graph has too few edges"

    fids = {n["id"] for n in fn["nodes"]}
    assert "df:valuestore" in fids and "df:hassclient" in fids, "data-flow landmarks missing"

    sids = {n["id"] for n in sn["nodes"]}
    for want in ("sensor:speed", "sensor:soc", "channel:diplus", "command:ac_on"):
        assert want in sids, f"missing node {want}"

    stypes = {e["type"] for e in sn["edges"]}
    for want in ("provides", "controls", "maps-to"):
        assert want in stypes, f"missing edge type {want}"
