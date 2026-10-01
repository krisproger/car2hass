import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent


def _load():
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "signal_categories", ROOT / "scripts" / "signal_categories.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_taxonomy_closed_and_ordered():
    m = _load()
    assert [k for k, _, _ in m.CATEGORIES] == [
        "drive", "battery", "climate", "access", "lights", "tyres",
        "safety", "sentry", "media", "location", "device", "phone", "other"]
    for key, en, ru in m.CATEGORIES:
        assert en and ru


def test_every_signal_has_category_in_taxonomy():
    m = _load()
    signals = yaml.safe_load((ROOT / "signals.yaml").read_text(encoding="utf-8"))["signals"]
    for s in signals:
        cat = s.get("category")
        assert cat in m.CATEGORY_KEYS, f"{s.get('key')}: bad category {cat!r}"


def test_registry_has_categories():
    reg = json.loads(
        (ROOT / "Car2Hass/app/src/main/assets/sensors_registry.json").read_text(encoding="utf-8"))
    cats = {k for k, _, _ in _load().CATEGORIES}
    for s in reg["sensors"]:
        assert s.get("category") in cats, f"{s['key']}: {s.get('category')!r}"


def test_spec_has_categories():
    spec = json.loads(
        (ROOT / "docs/cartelemetry/api/spec/spec.json").read_text(encoding="utf-8"))
    keys = [c["key"] for c in spec["categories"]]
    assert keys == [k for k, _, _ in _load().CATEGORIES]
    for s in spec["sensors_core"] + spec["sensors_extended"]:
        assert s["category"] in keys
        assert s["category_label_en"] and s["category_label_ru"]
