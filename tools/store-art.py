import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store"
W, H = 1920, 1200
FOREST = "#143c34"
PAPER = "#f7f6ef"
LIME = "#d6edb7"
SCENES = ("forecast", "week", "reminders", "spending", "changes", "voice", "sharing", "calendar")


def mark(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size * 4, size * 4))
    draw = ImageDraw.Draw(canvas)
    scale = size * 4 / 108
    unit = lambda v: round(v * scale)
    box = lambda points: tuple(map(unit, points))
    draw.rounded_rectangle(box((1, 1, 107, 107)), radius=unit(22), fill=FOREST)
    for points in (
        ((34, 33), (74, 33), (74, 77), (64, 71), (54, 77), (44, 71), (34, 77), (34, 33)),
        ((34, 44), (74, 44)),
        ((44, 28), (44, 37)),
        ((64, 28), (64, 37)),
        ((46, 58), (52, 64), (64, 52)),
    ):
        draw.line([box(p) for p in points], fill=LIME, width=unit(4.3), joint="curve")
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def font(file: str, size: int):
    windir = Path(__import__("os").environ.get("WINDIR", "C:/Windows"))
    return ImageFont.truetype(windir / "Fonts" / file, size)


def compose(raw: Path, title: str, output: Path):
    with Image.open(raw) as source:
        if source.size != (W, H):
            raise ValueError(f"{raw}: expected native Fire HD 10 landscape dimensions")
        screen = source.convert("RGB").crop((0, 36, 1920, 1128))
    panel = Image.new("RGB", (W, H), PAPER)
    draw = ImageDraw.Draw(panel)
    panel.paste(mark(42), (78, 45), mark(42))
    draw.text((134, 47), "Billmivo", font=font("seguisb.ttf", 31), fill=FOREST)
    title_size = 64
    while draw.textlength(title, font=font("georgia.ttf", title_size)) > 1740:
        title_size -= 2
    draw.text((78, 111), title, font=font("georgia.ttf", title_size), fill=FOREST)
    screen.thumbnail((1764, 972), Image.Resampling.LANCZOS)
    x = (W - screen.width) // 2
    y = 214
    draw.rounded_rectangle((x - 2, y - 2, x + screen.width + 1, y + screen.height + 1), radius=18, fill=FOREST)
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen.width - 1, screen.height - 1), radius=16, fill=255)
    panel.paste(screen, (x, y), mask)
    output.parent.mkdir(parents=True, exist_ok=True)
    panel.save(output, optimize=True)


def promotional_image(output: Path):
    panel = Image.new("RGB", (1024, 500), FOREST)
    draw = ImageDraw.Draw(panel)
    draw.rounded_rectangle((55, 54, 969, 445), radius=34, outline="#426458", width=2)
    draw.text((101, 190), "Billmivo", font=font("georgia.ttf", 105), fill=PAPER)
    draw.rounded_rectangle((101, 331, 289, 340), radius=4, fill=LIME)
    draw.rounded_rectangle((648, 105, 898, 395), radius=66, fill="#264c41")
    icon = mark(212)
    panel.paste(icon, (667, 144), icon)
    output.parent.mkdir(parents=True, exist_ok=True)
    panel.save(output, optimize=True)


def main():
    parser = argparse.ArgumentParser(description="Render Billmivo's Fire tablet assets from real app captures.")
    parser.add_argument("--raw", type=Path)
    parser.add_argument("--locale", choices=("en-GB", "fr-FR", "es-ES", "de-DE", "it-IT"))
    parser.add_argument("--out", type=Path, default=ROOT / "deliverables/store-assets/amazon")
    options = parser.parse_args()
    if (options.raw is None) != (options.locale is None):
        parser.error("--raw and --locale must be supplied together")
    icon_dir = options.out / "icons"
    icon_dir.mkdir(parents=True, exist_ok=True)
    for size in (114, 512):
        mark(size).save(icon_dir / f"billmivo-{size}.png")
    promotional_image(options.out / "promotion" / "billmivo-1024x500.png")
    if options.locale:
        listing = json.loads((STORE / "listings.json").read_text(encoding="utf-8"))[options.locale]
        for index, (scene, title) in enumerate(zip(SCENES, listing["headlines"]), start=1):
            compose(options.raw / f"{scene}.png", title, options.out / options.locale / f"{index:02d}-{scene}.png")


if __name__ == "__main__":
    main()
