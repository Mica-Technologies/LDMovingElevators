"""
Compares two regression runs: checks that changed outcome, metrics side by side, and how much of
each screenshot changed.

    python compare.py <before-label> <after-label>

A pixel counts as changed when any channel moved by more than 24 of 255, which ignores dithering
and lighting noise. Clouds drift between runs, so views with sky in them never diff to zero; run
the same build twice to see that noise floor before reading anything into a small percentage.
Diff images (changed pixels in red over a dimmed copy of the 'after' frame) are written next to
the 'after' run's screenshots.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
THRESHOLD = 24


def load(label):
    return json.loads((HERE / "results" / label / "results.json").read_text(encoding="utf-8"))


def blockentities_ms(run):
    """The frame's block-entity pass, from the section profile -- where every renderer here lands."""
    import re
    text = run.get("metrics", {}).get("perf.client.sections") or ""
    match = re.search(r"^\s*blockentities [0-9.]+% ([0-9.]+)ms", text, re.M)
    return float(match.group(1)) if match else None


def checks(run):
    return {c["name"]: c for s in run["scenarios"].values() for c in s["checks"]}


def image_diff(before, after, out):
    a = Image.open(before).convert("RGB")
    b = Image.open(after).convert("RGB")
    if a.size != b.size:
        return None
    diff = ImageChops.difference(a, b)
    # Any channel over the threshold, not their sum.
    channels = diff.split()
    mask = Image.new("L", a.size, 0)
    for channel in channels:
        mask = ImageChops.lighter(mask, channel.point(lambda v: 255 if v > THRESHOLD else 0))
    changed = mask.histogram()[255]
    overlay = Image.blend(b, Image.new("RGB", b.size, (0, 0, 0)), 0.6)
    overlay.paste((255, 0, 0), mask=mask)
    overlay.save(out)
    return changed / (a.size[0] * a.size[1])


def main(before_label, after_label):
    before, after = load(before_label), load(after_label)
    cb, ca = checks(before), checks(after)
    print("== checks")
    changed = 0
    # Only scenarios both runs executed; a partial run should not read as everything going missing.
    shared = set(before["scenarios"]) & set(after["scenarios"])
    names = {c["name"] for run in (before, after) for s_, data in run["scenarios"].items()
             if s_ in shared for c in data["checks"]}
    for name in sorted(names):
        x, y = cb.get(name), ca.get(name)
        if x is None or y is None or x["ok"] != y["ok"]:
            changed += 1
            fmt = lambda c: "missing" if c is None else ("PASS" if c["ok"] else "FAIL " + c["detail"])
            print("  %-55s %s -> %s" % (name, fmt(x), fmt(y)))
    failing = [n for n, c in ca.items() if not c["ok"]]
    print("  %d changed outcome; %d failing in %s" % (changed, len(failing), after_label))

    print("== metrics")
    mb, ma = dict(before.get("metrics", {})), dict(after.get("metrics", {}))
    mb["perf.client.blockentities_ms"], ma["perf.client.blockentities_ms"] = blockentities_ms(before), blockentities_ms(after)
    for key in sorted(set(mb) | set(ma)):
        x, y = mb.get(key), ma.get(key)
        if isinstance(x, str) or isinstance(y, str):
            continue
        delta = ""
        if isinstance(x, (int, float)) and isinstance(y, (int, float)) and x:
            delta = "%+.0f%%" % ((y - x) / x * 100)
        print("  %-45s %10s %10s %8s" % (key, x, y, delta))

    print("== screenshots (share of pixels changed)")
    shots_b = HERE / "results" / before_label / "screenshots"
    shots_a = HERE / "results" / after_label / "screenshots"
    for shot in sorted(shots_a.glob("*.png")):
        if shot.name.startswith("diff_"):
            continue
        ref = shots_b / shot.name
        if not ref.exists():
            print("  %-35s (no reference)" % shot.name)
            continue
        share = image_diff(ref, shot, shots_a / ("diff_" + shot.name))
        print("  %-35s %s" % (shot.name, "size differs" if share is None else "%.2f%%" % (share * 100)))


if __name__ == "__main__":
    main(*sys.argv[1:3])
