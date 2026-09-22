#!/usr/bin/env python3
"""Export transparent paper-cut masters for the static site and offline Android UI.

Run from any directory: python tools/prepare_illustrations.py
Only Pillow is required; no image service or network access is used.
"""
from pathlib import Path
import hashlib
import json

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MASTERS = ROOT / "design/illustrations"
WEB = ROOT / "docs/images/illustrations"
ANDROID = ROOT / "app/src/main/res/drawable-nodpi"
NAMES = ("download", "security-report", "faq", "versions", "permissions", "update", "search", "empty-404")
ANDROID_NAMES = ("faq", "versions", "permissions", "search")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def describe(path, size):
    return {"path": path.relative_to(ROOT).as_posix(), "width": size, "height": size,
            "bytes": path.stat().st_size, "sha256": digest(path)}


def main():
    WEB.mkdir(parents=True, exist_ok=True)
    ANDROID.mkdir(parents=True, exist_ok=True)
    items = []
    for name in NAMES:
        source = MASTERS / f"{name}.png"
        with Image.open(source) as opened:
            master = opened.convert("RGBA")
        if master.size != (1024, 1024) or master.getchannel("A").getextrema() != (0, 255):
            raise ValueError(f"{source}: expected a transparent 1024px master")
        item = {"name": name, "source": describe(source, 1024), "web": []}
        for size in (320, 640, 960):
            image = master.resize((size, size), Image.Resampling.LANCZOS)
            path = WEB / f"{name}-{size}.webp"
            image.save(path, "WEBP", quality=86, alpha_quality=100, method=6, exact=True)
            item["web"].append(describe(path, size))
            if size == 640:
                fallback = WEB / f"{name}.png"
                image.save(fallback, "PNG", optimize=True)
                item["fallback"] = describe(fallback, size)
        if name in ANDROID_NAMES:
            # nodpi avoids implicit resource-density resampling; the view bounds
            # control the rendered dp size. Only used illustrations enter the APK.
            image = master.resize((384, 384), Image.Resampling.LANCZOS)
            path = ANDROID / f"illus_{name.replace('-', '_')}.webp"
            image.save(path, "WEBP", quality=86, alpha_quality=100, method=6, exact=True)
            item["android"] = describe(path, 384)
        items.append(item)
    manifest = {"palette": {"amber": "#FFB020", "charcoal": "#171A21", "ivory": "#ECEFF4"},
                "source_size": 1024, "items": items}
    (WEB / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    webp_bytes = sum(f["bytes"] for item in items for f in item["web"])
    android_bytes = sum(item.get("android", {}).get("bytes", 0) for item in items)
    print(f"8 illustrations: responsive WebP total {webp_bytes / 1024:.1f} KiB across ALL sizes")
    print(f"4 bundled Android WebPs: {android_bytes / 1024:.1f} KiB (not measured APK size)")


if __name__ == "__main__":
    main()
