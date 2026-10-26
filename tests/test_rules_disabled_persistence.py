"""Regression guards for the disabled-rule cloud rollback bug.

Toggling or deleting a rule must bump rules.json mtime, otherwise the
newest-wins cloud sync downloads the older server copy on the next app start
and re-enables the rule (or resurrects a deleted one).
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "Car2Hass/app/src/main/java/com/car2hass/MainActivity.java"
ENGINE = ROOT / "Car2Hass/app/src/main/java/com/car2hass/rules/RuleEngine.java"


def test_toggle_marks_rules_dirty():
    text = MAIN.read_text(encoding="utf-8")
    assert re.search(
        r"RuleRegistry\.upsert\(this, rule\);\s*\n\s*markRulesChanged\(\"rule_toggle\"\)",
        text,
    ), "rule toggle must mark rules.json dirty (cloud sync)"


def test_delete_marks_rules_dirty():
    text = MAIN.read_text(encoding="utf-8")
    assert re.search(
        r"RuleRegistry\.delete\(this, rule\.id\);\s*\n\s*setupRules\(\);\s*\n\s*markRulesChanged\(\"rule_delete\"\)",
        text,
    ), "rule delete must mark rules.json dirty (cloud sync)"


def test_mark_rules_changed_touches_cloud():
    text = MAIN.read_text(encoding="utf-8")
    body = text[text.index("private void markRulesChanged"):]
    body = body[: body.index("\n    }") + 6]
    assert "touchCloudFileMtimeMs" in body and "FILE_RULES" in body
    assert "CloudSettingsSync.syncAsync" in body


def test_engine_skips_disabled_rules():
    text = ENGINE.read_text(encoding="utf-8")
    assert re.search(
        r"if \(!rule\.enabled\) \{\s*\n\s*if \(disabledLogged\.add\(rule\.id\)\)",
        text,
    ), "RuleEngine must skip disabled rules before evaluating conditions"
