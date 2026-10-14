#!/usr/bin/env python3
"""Version retention for the CARTelemetry site.

Keeps at most KEEP newest artifacts per channel (stable/beta) for the app
(car2hass-v*.apk) and integration (cartelemetry-v*.zip), removes legacy
diplus2hass-* files, trims the release registry, and optionally prunes the
server webroot over ssh. Dry-run unless --apply is given.
"""
import re
from collections import defaultdict

FAMILIES = (("car2hass-v", ".apk"), ("cartelemetry-v", ".zip"))
LEGACY_PREFIX = "diplus2hass-"


def channel_of(name: str) -> str:
    return "beta" if "-beta." in name else "stable"


def version_key(name: str) -> list:
    m = re.match(r"^(?:car2hass|cartelemetry)-v(.+?)\.(?:apk|zip)$", name)
    v = m.group(1) if m else name
    key = []
    for part in re.split(r"[.\-]", v):
        key.append((0, int(part), "") if part.isdigit() else (1, 0, part))
    return key


def select(files, keep):
    """Return (keep, delete) across all families and channels."""
    buckets = defaultdict(list)
    for name in files:
        for prefix, _ext in FAMILIES:
            if name.startswith(prefix):
                buckets[(prefix, channel_of(name))].append(name)
    keep_list, delete_list = [], []
    for bucket in buckets.values():
        ordered = sorted(bucket, key=version_key, reverse=True)
        keep_list += ordered[:keep]
        delete_list += ordered[keep:]
    return sorted(keep_list), sorted(delete_list)


import argparse
import os
import subprocess
import sys
from pathlib import Path

REGISTRY = "api/version/index.php"


def _trim_block(text, var, keep):
    start = text.index(f"${var} = [")
    end = text.index("\n];", start)
    block, tail = text[start:end], text[end:]
    parts = re.split(r"(?m)(?=^    '[^']+' => \[)", block)
    prefix, items = parts[0], parts[1:]
    return text[:start] + prefix + "".join(items[:keep]) + tail, max(0, len(items) - keep)


def trim_registry(text, keep):
    dropped = 0
    for name in ("RELEASES", "BETA_RELEASES"):
        if f"${name} = [" in text:
            text, d = _trim_block(text, name, keep)
            dropped += d
    return text, dropped


def _git_rm(paths):
    if paths:
        subprocess.run(["git", "rm", "-q", "--cached", "--ignore-unmatch", *paths], check=False)


def remote_prune(delete, webroot, host, port, user):
    if not delete:
        return
    remote_dir = f"{webroot}/cartelemetry"
    quoted = " ".join("'" + f + "'" for f in delete)
    subprocess.run(
        ["ssh", "-p", str(port), f"{user}@{host}", f"cd {remote_dir} && rm -f {quoted}"],
        check=True,
    )


def parse_artifacts(text):
    """Artifact filenames from an `ls -1` listing."""
    return sorted({l.strip() for l in text.splitlines()
                   if l.strip().endswith((".apk", ".zip"))})


def server_files(host, port, user, webroot):
    out = subprocess.run(
        ["ssh", "-p", str(port), f"{user}@{host}", f"ls -1 {webroot}/cartelemetry"],
        check=True, capture_output=True, text=True,
    ).stdout
    return parse_artifacts(out)


def remote_plan(host, port, user, webroot, keep):
    """Compute (keep, delete) from the SERVER's own file list."""
    files = server_files(host, port, user, webroot)
    s_keep, s_delete = select(files, keep)
    s_delete = sorted(set(s_delete) | {f for f in files if f.startswith(LEGACY_PREFIX)})
    return s_keep, s_delete


def main(argv=None):
    ap = argparse.ArgumentParser(description="Keep N newest artifacts per channel.")
    ap.add_argument("--site", default=str(Path(__file__).resolve().parents[1] / "docs" / "cartelemetry"))
    ap.add_argument("--keep", type=int, default=2)
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--remote", action="store_true")
    args = ap.parse_args(argv)

    site = Path(args.site)
    files = [p.name for p in site.iterdir() if p.is_file()
             and (p.name.endswith(".apk") or p.name.endswith(".zip"))]
    keep, delete = select(files, args.keep)
    legacy = sorted(f for f in files if f.startswith(LEGACY_PREFIX))
    delete = sorted(set(delete) | set(legacy))

    print("KEEP:")
    for f in keep:
        print("  " + f)
    print("DELETE:")
    for f in delete:
        print("  " + f)

    if args.remote:
        host = os.environ.get("REMOTE_HOST", "mytechnic.ru")
        port = os.environ.get("REMOTE_PORT", "2211")
        user = os.environ.get("REMOTE_USER", "kris")
        webroot = os.environ.get("REMOTE_WEBROOT", "/var/www/mytechnic.ru/html")
        s_keep, s_delete = remote_plan(host, port, user, webroot, args.keep)
        print("REMOTE KEEP:")
        for f in s_keep:
            print("  " + f)
        print("REMOTE DELETE:")
        for f in s_delete:
            print("  " + f)
        if args.apply:
            remote_prune(s_delete, webroot, host, port, user)

    if not args.apply:
        print("(dry-run; pass --apply to delete)")
        return 0

    for f in delete:
        (site / f).unlink(missing_ok=True)
    _git_rm([f"docs/cartelemetry/{f}" for f in delete])

    reg = site / REGISTRY
    text = reg.read_text(encoding="utf-8")
    new_text, dropped = trim_registry(text, args.keep)
    reg.write_text(new_text, encoding="utf-8")
    print(f"registry: dropped {dropped} entries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
