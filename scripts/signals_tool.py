#!/usr/bin/env python3
"""Maintain the single source of truth for DiPlus signal registry.

Subcommands:
    extract   Parse SIGNALS.md, CANDataReader.java and const.py into signals.yaml.
    gen-java  Generate CANDataReader.java SIGNAL_REGISTRY from signals.yaml.
    gen-py    Generate const.py from signals.yaml.
    gen-md    Generate SIGNALS.md from signals.yaml.
"""

import argparse
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).parent.parent
SIGNALS_MD = ROOT / "SIGNALS.md"
JAVA_READER = ROOT / "DiPlus-to-hass" / "app" / "src" / "main" / "java" / "com" / "diplustohass" / "CANDataReader.java"
SIGNAL_TRANSLATOR = ROOT / "DiPlus-to-hass" / "app" / "src" / "main" / "java" / "com" / "diplustohass" / "SignalTranslator.java"
CONST_PY = ROOT / "custom_components" / "diplus2hass" / "const.py"
SIGNALS_YAML = ROOT / "signals.yaml"


def _parse_java_registry():
    text = JAVA_READER.read_text(encoding="utf-8")
    match = re.search(r"SIGNAL_REGISTRY\s*=\s*\{(.*?)\};", text, re.DOTALL)
    if not match:
        raise RuntimeError("Cannot find SIGNAL_REGISTRY in CANDataReader.java")
    body = match.group(1)
    rows = []
    for line in re.split(r"\},\s*\n\s*\{", body.strip("{}\n\t ")):
        line = line.strip("{}\n\t ")
        parts = [p.strip().strip('"') for p in line.split(",")]
        if len(parts) >= 4:
            rows.append({
                "name": parts[0],
                "description": parts[1],
                "key": parts[2],
                "type": parts[3],
            })
    return rows


def _parse_const():
    text = CONST_PY.read_text(encoding="utf-8")
    safe_builtins = {
        "dict": dict,
        "list": list,
        "set": set,
        "str": str,
        "int": int,
        "float": float,
        "bool": bool,
        "len": len,
        "__import__": __import__,
    }
    scope = {"__builtins__": safe_builtins, "__file__": str(CONST_PY)}
    exec(text, scope)  # noqa: S102
    numeric = scope.get("NUMERIC_SENSORS", {})
    enum = scope.get("ENUM_SENSORS", {})
    binary = scope.get("BINARY_SENSORS", {})
    on_map = scope.get("BINARY_ON_MAP", {})
    return {
        "numeric": numeric,
        "enum": enum,
        "binary": binary,
        "binary_on_map": on_map,
    }


def _parse_signals_md():
    text = SIGNALS_MD.read_text(encoding="utf-8")
    rows = []
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("|") or "---" in line or "Ключ" in line:
            continue
        parts = [p.strip() for p in line.split("|")]
        parts = [p for p in parts if p]
        if len(parts) < 4:
            continue
        id_str = parts[0]
        name_raw = parts[1]
        desc = parts[2]
        type_ = parts[3]
        labels = parts[4] if len(parts) > 4 else ""
        id_val = int(id_str) if id_str.isdigit() else None
        name = re.search(r"`([^`]+)`", name_raw)
        name = name.group(1) if name else name_raw
        label_map = {}
        if labels:
            for part in labels.split(","):
                part = part.strip()
                if "=" in part:
                    k, v = part.split("=", 1)
                    label_map[int(k.strip())] = v.strip()
        rows.append({
            "id": id_val,
            "name": name,
            "description": desc,
            "type": type_.lower(),
            "labels": label_map,
        })
    return rows


def cmd_extract(args):
    java_rows = _parse_java_registry()
    const = _parse_const()
    md_rows = _parse_signals_md()

    java_by_name = {r["name"]: r for r in java_rows}
    md_by_name = {r["name"]: r for r in md_rows}

    all_names = sorted(set(java_by_name) | set(md_by_name), key=lambda n: md_by_name.get(n, {}).get("id") or 9999)

    merged = []
    key_to_type = {}
    for key, cfg in {**const["numeric"], **const["enum"], **const["binary"]}.items():
        key_to_type[key] = cfg

    binary_keys = set(const["binary"].keys())
    binary_on_map = const["binary_on_map"]

    for name in all_names:
        md = md_by_name.get(name, {})
        java = java_by_name.get(name, {})
        key = java.get("key", "")
        signal_type = java.get("type", md.get("type", "num"))

        entry = {
            "id": md.get("id"),
            "name": name,
            "description": md.get("description", java.get("description", "")),
            "type": signal_type,
            "key": key,
            "labels": md.get("labels", {}),
        }
        if key in binary_keys:
            entry["ha_type"] = "binary_sensor"
            entry["device_class"] = const["binary"][key].get("device_class")
            entry["truthy"] = binary_on_map.get(key, binary_on_map.get("_default_", []))
        elif key in const["enum"]:
            entry["ha_type"] = "sensor"
            entry["icon"] = const["enum"][key].get("icon", "mdi:car")
        elif key in const["numeric"]:
            entry["ha_type"] = "sensor"
            entry.update({k: v for k, v in const["numeric"][key].items() if k != "name"})
        else:
            entry["ha_type"] = "sensor"
        merged.append(entry)

    SIGNALS_YAML.write_text(
        yaml.safe_dump({"signals": merged}, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    print(f"Extracted {len(merged)} signals to {SIGNALS_YAML}")


def _load_yaml():
    return yaml.safe_load(SIGNALS_YAML.read_text(encoding="utf-8"))["signals"]


def _build_enum_labels_snippet(signals):
    lines = []
    for s in signals:
        if s.get("type") != "enum":
            continue
        labels = s.get("labels", {})
        if not labels:
            continue
        parts = [f"{k}:{v}" for k, v in sorted(labels.items())]
        lines.append(f'        ENUM_LABELS.put("{s["key"]}", "{",".join(parts)}");')
    return "\n".join(lines)


def _build_value_trans_snippet(value_trans):
    lines = []
    for chinese, english in value_trans.items():
        lines.append(f'        VALUE_TRANS.put("{chinese}", "{english}");')
    return "\n".join(lines)


def cmd_gen_java(args):
    signals = _load_yaml()
    yaml_data = yaml.safe_load(SIGNALS_YAML.read_text(encoding="utf-8"))
    value_trans = yaml_data.get("value_trans", {})

    # Synthetic sensors are supplied by the app itself (e.g. app_version, WiFi);
    # they have no DiPlus CAN mapping and must not be queried from the head unit.
    can_signals = [s for s in signals if not s.get("synthetic")]

    snippet_lines = []
    snippet_lines.append("    public static final String[][] SIGNAL_REGISTRY = {")
    for s in can_signals:
        desc = s["description"].replace('"', '\\"')
        name = s["name"].replace('"', '\\"')
        snippet_lines.append(
            f'        {{"{name}", "{desc}", "{s["key"]}", "{s["type"]}"}},'
        )
    snippet_lines[-1] = snippet_lines[-1].rstrip(",")
    snippet_lines.append("    };")
    snippet = "\n".join(snippet_lines)

    if args.stdout:
        print(snippet)
        return

    text = JAVA_READER.read_text(encoding="utf-8")
    new_text = re.sub(
        r"(// ─── Full signal registry from SIGNALS\.md \(\d+ signals\) ───\n(?:(?!\s*// \d+ CAN-mapped signals).)*?)(?:\s*// \d+ CAN-mapped signals[^\n]*)*\s*public static final String\[\]\[\] SIGNAL_REGISTRY = \{.*?\};",
        lambda m: m.group(1) + f"\n\n        // {len(can_signals)} CAN-mapped signals (synthetic app-supplied signals excluded)\n" + snippet,
        text,
        flags=re.DOTALL,
    )
    JAVA_READER.write_text(new_text, encoding="utf-8")
    print(f"Updated {JAVA_READER} with {len(can_signals)} CAN signals ({len(signals)} total)")

    # Update SignalTranslator generated sections
    enum_labels = _build_enum_labels_snippet(signals)
    value_trans_snippet = _build_value_trans_snippet(value_trans)
    st_text = SIGNAL_TRANSLATOR.read_text(encoding="utf-8")
    st_new = re.sub(
        r"(// AUTO-GENERATED VALUE TRANS START)\r?\n.*?(// AUTO-GENERATED VALUE TRANS END)",
        lambda m: m.group(1) + "\n" + value_trans_snippet + "\n        " + m.group(2),
        st_text,
        flags=re.DOTALL,
    )
    st_new = re.sub(
        r"(// AUTO-GENERATED ENUM LABELS START)\r?\n.*?(// AUTO-GENERATED ENUM LABELS END)",
        lambda m: m.group(1) + "\n" + enum_labels + "\n        " + m.group(2),
        st_new,
        flags=re.DOTALL,
    )
    SIGNAL_TRANSLATOR.write_text(st_new, encoding="utf-8")
    print(f"Updated {SIGNAL_TRANSLATOR} value trans and enum labels")


def _build_const_snippets(signals):
    numeric = {}
    enum = {}
    binary = {}
    binary_on_map = {}
    for s in signals:
        key = s["key"]
        if not key:
            continue
        ha_type = s.get("ha_type", "sensor")
        if ha_type == "binary_sensor":
            binary[key] = {"name": s["description"], "device_class": s.get("device_class")}
            if "truthy" in s:
                binary_on_map[key] = s["truthy"]
        elif s["type"] == "enum":
            enum[key] = {"name": s["description"], "icon": s.get("icon", "mdi:car")}
        else:
            numeric[key] = {"name": s["description"]}
            for k in ("unit", "icon", "device_class", "state_class"):
                if k in s and s[k] is not None:
                    numeric[key][k] = s[k]

    def _dict_repr(d, indent=4):
        inner = " " * indent
        parts = []
        for k, v in d.items():
            if isinstance(v, dict):
                sub = "\n".join(_dict_repr(v, indent + 4).splitlines())
                parts.append(f'{inner}"{k}": {sub},')
            elif isinstance(v, list):
                parts.append(f'{inner}"{k}": {v!r},')
            elif isinstance(v, str):
                parts.append(f'{inner}"{k}": "{v}",')
            else:
                parts.append(f'{inner}"{k}": {v!r},')
        return "{\n" + "\n".join(parts) + "\n" + " " * (indent - 4) + "}"

    lines = []
    lines.append('DOMAIN = "diplus2hass"')
    lines.append("")
    lines.append('CONF_CAR_NAME = "car_name"')
    lines.append("")
    lines.append(f'INTEGRATION_VERSION = "{_integration_version()}"')
    lines.append("")
    lines.append("# Numeric signals and their configurations")
    lines.append("# Keys must match the stable keys generated by the Android app.")
    lines.append("NUMERIC_SENSORS = " + _dict_repr(numeric))
    lines.append("")
    lines.append("# Enum/text signals — string-valued signals from the vehicle")
    lines.append("ENUM_SENSORS = " + _dict_repr(enum))
    lines.append("")
    lines.append("# Binary sensor signals — mapped from enum values")
    lines.append("# Keys must match the stable keys generated by the Android app.")
    lines.append("BINARY_SENSORS = " + _dict_repr(binary))
    lines.append("")
    lines.append('# Map from binary sensor key → list of "truthy" string values')
    lines.append('# Used by binary_sensor.py _value_to_bool()')
    lines.append("BINARY_ON_MAP: dict[str, list[str]] = " + repr(binary_on_map))
    lines.append("")
    lines.append("# Dynamic geofence virtual sensors sent by the Android app.")
    lines.append('# Keys look like "geo_<zoneId>" with values "inside"/"outside"; the optional')
    lines.append('# companion key "geo_<zoneId>_name" carries the user-defined zone name.')
    lines.append('GEOFENCE_KEY_PREFIX = "geo_"')
    lines.append('GEOFENCE_NAME_SUFFIX = "_name"')
    lines.append('GEOFENCE_ON_VALUES = ["inside"]')
    lines.append("")
    return "\n".join(lines)


def _integration_version():
    """Keep INTEGRATION_VERSION stable across regenerations: prefer the value
    already present in const.py, fall back to manifest.json."""
    import json as _json
    if CONST_PY.exists():
        m = re.search(r'INTEGRATION_VERSION\s*=\s*"([^"]+)"', CONST_PY.read_text(encoding="utf-8"))
        if m:
            return m.group(1)
    manifest = CONST_PY.parent / "manifest.json"
    if manifest.exists():
        try:
            return _json.loads(manifest.read_text(encoding="utf-8")).get("version", "0.0.0")
        except Exception:
            pass
    return "0.0.0"


def cmd_gen_py(args):
    signals = _load_yaml()
    snippet = _build_const_snippets(signals)
    if args.stdout:
        print(snippet)
        return
    CONST_PY.write_text(snippet, encoding="utf-8")
    print(f"Updated {CONST_PY} with {len(signals)} signals")


def cmd_gen_md(args):
    signals = _load_yaml()
    sections = {}
    for s in signals:
        sid = s.get("id")
        if sid is None:
            section = "Unknown"
        elif sid < 200:
            section = "Ходовые / силовая установка / батарея (1–199)"
        elif sid < 1000:
            section = "Системные / мультимедиа (200–999)"
        elif sid < 1200:
            section = "Мультимедиа / регистратор / система (1000–1199)"
        elif sid < 2100:
            section = "ИИ-распознавание / записи (2000–2099)"
        else:
            section = "Прочие"
        sections.setdefault(section, []).append(s)

    out = ["# diplus — полный каталог сигналов (`/api/getVal?name=`)", ""]
    out.append("Источник: `com.van.diplus.cmd.s` (конструктор), декомпиляция diplus 1.3.8-beta18.")
    out.append("Всего **" + str(len(signals)) + "** сигналов. Ключ `name` — китайская строка (единственное латинское имя — `SOC`).")
    out.append("")
    out.append("- **ID** — внутренний индекс в реестре (`SparseArray`), для справки; в HTTP-запросе не используется.")
    out.append("- **Тип**: *num* — числовое значение; *enum* — при `status=true` возвращает текстовую метку, при `status=false` — числовой индекс.")
    out.append("- **Метки enum** — соответствие `индекс=значение` (перевод; `—` = null/зарезервировано).")
    out.append("")
    out.append("```")
    out.append("GET http://127.0.0.1:8988/api/getVal?name=车速&status=true   →  {\"success\":true,\"val\":\"…\"}")
    out.append("```")
    out.append("")

    for section, rows in sections.items():
        out.append(f"## {section}")
        out.append("")
        out.append("| ID | Ключ (`name`) | Значение | Тип | Метки enum |")
        out.append("|---:|---|---|---|---|")
        for s in rows:
            labels = ", ".join(f"{k}={v}" for k, v in sorted(s.get("labels", {}).items()))
            out.append(f'| {s.get("id", "")} | `{s["name"]}` | {s["description"]} | {s["type"]} | {labels} |')
        out.append("")

    SIGNALS_MD.write_text("\n".join(out), encoding="utf-8")
    print(f"Generated {SIGNALS_MD} with {len(signals)} signals")


def main():
    parser = argparse.ArgumentParser(description="DiPlus signal registry tool")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("extract", help="Build signals.yaml from existing sources")
    p_java = sub.add_parser("gen-java", help="Generate CANDataReader.java SIGNAL_REGISTRY")
    p_java.add_argument("--stdout", action="store_true", help="Print snippet instead of writing file")
    p_py = sub.add_parser("gen-py", help="Generate const.py")
    p_py.add_argument("--stdout", action="store_true", help="Print snippet instead of writing file")
    sub.add_parser("gen-md", help="Regenerate SIGNALS.md")
    sub.add_parser("regenerate", help="Regenerate Java, Python and Markdown from signals.yaml")
    args = parser.parse_args()

    if args.cmd == "extract":
        cmd_extract(args)
    elif args.cmd == "gen-java":
        cmd_gen_java(args)
    elif args.cmd == "gen-py":
        cmd_gen_py(args)
    elif args.cmd == "gen-md":
        cmd_gen_md(args)
    elif args.cmd == "regenerate":
        class A:
            stdout = False
        cmd_gen_java(A())
        cmd_gen_py(A())
        cmd_gen_md(A())


if __name__ == "__main__":
    main()
