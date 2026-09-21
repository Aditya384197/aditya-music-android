#!/usr/bin/env python3
"""Generate a compact offline fixed-band EQ database from AutoEq README results.

Only pre-computed fixed-band EQ settings are packaged. Raw measurement files are not copied.
The generated JSON is consumed at runtime with no network access.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from urllib.parse import unquote

SOURCE_PRIORITY = {
    "oratory1990": 0,
    "crinacle": 1,
    "Rtings": 2,
    "Innerfidelity": 3,
    "HypetheSonics": 4,
    "Super Review": 5,
    "Jaytiss": 6,
    "kr0mka": 7,
    "Fahryst": 8,
    "Kazi": 9,
    "Headphone.com Legacy": 10,
}

PREAMP_RE = re.compile(r"apply preamp of (?:\*\*)?(-?\d+(?:\.\d+)?) dB", re.I)
ROW_RE = re.compile(
    r"\|\s*\d+\s*\|\s*Peaking\s*\|\s*([0-9]+(?:\.[0-9]+)?)\s*\|\s*"
    r"[0-9]+(?:\.[0-9]+)?\s*\|\s*(-?[0-9]+(?:\.[0-9]+)?)\s*\|",
    re.I,
)


def classify(path: Path) -> str:
    parts = {p.lower() for p in path.parts}
    if "over-ear" in parts or "on-ear" in parts:
        return "headphones"
    if "in-ear" in parts or "earbud" in parts:
        return "earbuds"
    return "headphones"


def parse_readme(path: Path, root: Path) -> dict | None:
    text = path.read_text(encoding="utf-8", errors="replace")
    heading = re.search(r"^#\s+(.+?)\s*$", text, re.M)
    if not heading:
        return None
    name = heading.group(1).strip()
    section_match = re.search(r"### Fixed Band EQs\s*(.*?)(?:\n### |\Z)", text, re.S | re.I)
    if not section_match:
        return None
    section = section_match.group(1)
    rows = [(float(f), float(g)) for f, g in ROW_RE.findall(section)]
    if len(rows) != 10:
        return None
    rows.sort(key=lambda x: x[0])
    preamp_match = PREAMP_RE.search(section)
    preamp = float(preamp_match.group(1)) if preamp_match else 0.0

    rel = path.relative_to(root / "results")
    source = rel.parts[0] if rel.parts else "AutoEq"
    # The source path is retained only as attribution/provenance; raw measurement data is not bundled.
    source_path = "/".join(unquote(p) for p in rel.parts)
    return {
        "name": name,
        "type": classify(rel),
        "source": source,
        "sourcePath": source_path,
        "preampDb": preamp,
        "frequenciesHz": [int(round(f)) for f, _ in rows],
        "gainsDb": [round(g, 2) for _, g in rows],
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--autoeq-dir", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    args = ap.parse_args()

    results = args.autoeq_dir / "results"
    profiles: list[dict] = []
    for readme in results.rglob("README.md"):
        try:
            item = parse_readme(readme, args.autoeq_dir)
        except OSError:
            continue
        if item:
            profiles.append(item)

    # Prefer the highest-priority measurement source when the same model name occurs multiple times.
    by_name: dict[str, dict] = {}
    for item in profiles:
        key = re.sub(r"\s+", " ", item["name"].strip().lower())
        current = by_name.get(key)
        if current is None or SOURCE_PRIORITY.get(item["source"], 100) < SOURCE_PRIORITY.get(current["source"], 100):
            by_name[key] = item

    output = []
    for item in sorted(by_name.values(), key=lambda x: x["name"].lower()):
        slug = re.sub(r"[^a-z0-9]+", "-", item["name"].lower()).strip("-")
        item = {"id": slug, **item}
        output.append(item)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    if not output:
        print("WARNING: AutoEq result parser produced 0 profiles; keeping the bundled seed database.")
        return
    args.output.write_text(json.dumps(output, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"Generated {len(output)} offline headset profiles -> {args.output}")


if __name__ == "__main__":
    main()
