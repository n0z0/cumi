"""Renders the Play Store icon and feature graphic.

Usage: python3 fastlane/assets/render.py   (needs Pillow, ImageMagick and the Inter font)
The squid artwork in squid.svg mirrors app/src/main/res/drawable/ic_launcher_foreground.xml (eyes are cut-outs there).
"""
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
META = HERE.parent / "metadata" / "android"
FONT_DIR = Path("/usr/share/fonts/opentype/inter")


def gradient(size, top_left, bottom_right):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(top_left, bottom_right))
    return img


def squid(size):
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "squid.png"
        subprocess.run(
            ["convert", "-background", "none", "-density", "600", str(HERE / "squid.svg"),
             "-resize", f"{size}x{size}", str(out)],
            check=True,
        )
        return Image.open(out).convert("RGBA").copy()


def icon():
    img = gradient((512, 512), (0x00, 0x75, 0x7A), (0x00, 0x43, 0x47)).convert("RGBA")
    # Inset the artwork so Play's rounded-corner mask never clips it.
    art = 448
    img.alpha_composite(squid(art), ((512 - art) // 2, (512 - art) // 2))
    return img.convert("RGB")


def feature_graphic():
    w, h = 1024, 500
    img = gradient((w, h), (0x00, 0x69, 0x6E), (0x00, 0x33, 0x37)).convert("RGBA")

    # Faint network motif on the right.
    net = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(net)
    nodes = {"a": (700, 80), "b": (860, 150), "c": (960, 60), "d": (900, 300),
             "e": (1000, 360), "f": (760, 400), "g": (640, 450), "h": (700, 250)}
    for p, q in ["ab", "bc", "bd", "de", "df", "fg", "fh", "hb"]:
        d.line([nodes[p], nodes[q]], fill=(127, 227, 232, 50), width=3)
    for name, (x, y) in nodes.items():
        r = 11 if name in "bd" else 7
        d.ellipse([x - r, y - r, x + r, y + r], fill=(127, 227, 232, 80))
    img.alpha_composite(net)

    # App icon tile with a soft shadow.
    tile, tx, ty = 192, 88, 154
    shadow = Image.new("RGBA", (w, h))
    ImageDraw.Draw(shadow).rounded_rectangle([tx, ty + 10, tx + tile, ty + tile + 10], 44, fill=(0, 0, 0, 90))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(14)))
    tile_img = icon().resize((tile, tile), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (tile, tile))
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, tile - 1, tile - 1], 44, fill=255)
    img.paste(tile_img, (tx, ty), mask)

    d = ImageDraw.Draw(img)
    bold = ImageFont.truetype(str(FONT_DIR / "Inter-Bold.otf"), 84)
    regular = ImageFont.truetype(str(FONT_DIR / "Inter-Regular.otf"), 34)
    semibold = ImageFont.truetype(str(FONT_DIR / "Inter-SemiBold.otf"), 22)
    d.text((330, 158), "Cumi", font=bold, fill="#FFFFFF")
    d.text((334, 262), "SOCKS5 proxy server for your phone", font=regular, fill="#B8F1F4")

    x = 334
    for label in ["Wi-Fi", "Hotspot", "USB"]:
        tw = d.textlength(label, font=semibold)
        d.rounded_rectangle([x, 330, x + tw + 44, 370], 20, fill="#7FE3E8")
        d.text((x + 22, 350), label, font=semibold, fill="#00363A", anchor="lm")
        x += tw + 44 + 12
    return img.convert("RGB")


if __name__ == "__main__":
    outputs = {"icon.png": icon(), "featureGraphic.png": feature_graphic()}
    for locale in ["en-US", "id"]:
        images = META / locale / "images"
        images.mkdir(parents=True, exist_ok=True)
        for name, image in outputs.items():
            image.save(images / name, optimize=True)
    print("Rendered", ", ".join(outputs))
