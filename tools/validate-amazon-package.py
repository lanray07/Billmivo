import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
LISTINGS = ROOT / "store/listings.json"
SCENES = ("forecast", "week", "reminders", "spending", "changes", "voice", "sharing", "calendar")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inspect(path, size, alpha=False):
    with Image.open(path) as image:
        image.load()
        if image.format != "PNG" or image.size != size:
            raise ValueError(f"{path}: expected a {size[0]} × {size[1]} PNG")
        if alpha and ("A" not in image.getbands() or image.getchannel("A").getextrema() != (0, 255)):
            raise ValueError(f"{path}: app icon must include transparency")
    return {"file": path.name, "pixels": list(size), "sha256": sha(path)}


def main():
    parser = argparse.ArgumentParser(description="Validate local Fire tablet listing assets and copy.")
    parser.add_argument("--assets", type=Path, default=ROOT / "deliverables/store-assets/amazon")
    parser.add_argument("--raw", type=Path)
    options = parser.parse_args()
    definitions = json.loads(LISTINGS.read_text(encoding="utf-8"))
    manifest = {"target": "Fire tablet", "icons": [], "listings": {}}
    for size in (114, 512):
        manifest["icons"].append(inspect(options.assets / "icons" / f"billmivo-{size}.png", (size, size), True))
    manifest["promotion"] = inspect(options.assets / "promotion" / "billmivo-1024x500.png", (1024, 500))
    for locale, definition in definitions.items():
        if (len(definition["short"].encode("utf-8")) > 2000 or "\n" in definition["short"] or
                len(definition["long"]) > 4000 or not 3 <= len(definition["features"]) <= 5 or
                len(definition["headlines"]) != len(SCENES)):
            raise ValueError(f"{locale}: listing copy does not meet the package rules")
        screenshots = []
        for index, scene in enumerate(SCENES, 1):
            image = inspect(options.assets / locale / f"{index:02d}-{scene}.png", (1920, 1200))
            if options.raw:
                image["raw_sha256"] = sha(options.raw / locale / f"{scene}.png")
            screenshots.append(image)
        if options.raw and len({item["raw_sha256"] for item in screenshots}) != len(SCENES):
            raise ValueError(f"{locale}: duplicate raw scenes")
        manifest["listings"][locale] = screenshots
        print(f"{locale}: eight validated 1920 × 1200 captures and listing copy")
    (options.assets / "asset-manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
