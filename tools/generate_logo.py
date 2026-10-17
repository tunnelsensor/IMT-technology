"""
Maakt het logo van I.M.T Technology: de I.M.T-bril (het item-icoon, vergroot) op een donkere
HUD-achtergrond, met "I.M.T" en "TECHNOLOGY" eronder.

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_logo.py

Het maakt twee plaatjes:
- src/main/resources/imttech_logo.png: het logo in de modlijst van Minecraft (Mods-knop op het
  titelscherm). neoforge.mods.toml verwijst ernaar met logoFile.
- publish/avatar-400.png: 400 x 400 pixels, het projectplaatje voor CurseForge (dat eist precies
  die maat, en geen effen kleur).

Nodig: Python 3 met Pillow (pip install pillow). Het bril-icoon zelf komt uit
tools/generate_textures.py (functie imt_glasses_item); draai dat eerst als je de bril verandert.
"""
from pathlib import Path

from PIL import Image, ImageDraw

PROJECT = Path(__file__).resolve().parent.parent
GLASSES = PROJECT / "src" / "main" / "resources" / "assets" / "imttech" / "textures" / "item" / "imt_glasses.png"

BACKGROUND_TOP = (10, 18, 30)
BACKGROUND_BOTTOM = (4, 8, 14)
GRID = (18, 40, 56)
RING = (30, 110, 140)
CYAN = (63, 216, 255)
CYAN_DIM = (30, 110, 140)
WHITE = (230, 244, 250)

# Een klein pixel-lettertype (5 breed, 7 hoog): alleen de letters die we nodig hebben.
FONT = {
    "I": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    ".": [".....", ".....", ".....", ".....", ".....", ".##..", ".##.."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "G": [".####", "#....", "#....", "#.###", "#...#", "#...#", ".###."],
    "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    " ": [".....", ".....", ".....", ".....", ".....", ".....", "....."],
}


def text_width(text, scale, gap):
    return len(text) * 5 * scale + (len(text) - 1) * gap


def draw_text(draw, text, center_x, top, scale, gap, color):
    """Tekent tekst met het pixel-lettertype, gecentreerd rond center_x."""
    x = center_x - text_width(text, scale, gap) // 2
    for letter in text:
        for row, line in enumerate(FONT[letter]):
            for col, cell in enumerate(line):
                if cell == "#":
                    draw.rectangle([x + col * scale, top + row * scale,
                                    x + col * scale + scale - 1, top + row * scale + scale - 1], fill=color)
        x += 5 * scale + gap


def logo(size):
    """Het logo op size x size pixels (gemaakt op 400 en daarna verkleind, zodat het overal hetzelfde is)."""
    base = 400
    im = Image.new("RGB", (base, base))
    d = ImageDraw.Draw(im)
    # Achtergrond: van donkerblauw boven naar bijna zwart onder.
    for y in range(base):
        t = y / (base - 1)
        d.line([0, y, base, y], fill=tuple(round(a + (b - a) * t) for a, b in zip(BACKGROUND_TOP, BACKGROUND_BOTTOM)))
    # Een raster zoals de HUD van de bril.
    for i in range(0, base, 25):
        d.line([i, 0, i, base], fill=GRID)
        d.line([0, i, base, i], fill=GRID)
    # Twee ringen rond de bril, en vier hoekjes zoals een doelwit-kader.
    d.ellipse([86, 66, 314, 294], outline=RING, width=3)
    d.ellipse([106, 86, 294, 274], outline=CYAN_DIM, width=1)
    for (x, y, dx, dy) in [(24, 24, 1, 1), (376, 24, -1, 1), (24, 376, 1, -1), (376, 376, -1, -1)]:
        d.line([x, y, x + 34 * dx, y], fill=CYAN, width=4)
        d.line([x, y, x, y + 34 * dy], fill=CYAN, width=4)
    # De bril: het echte item-icoon, 16 keer zo groot, met harde pixels.
    glasses = Image.open(GLASSES).convert("RGBA").resize((256, 256), Image.NEAREST)
    im.paste(glasses, (72, 52), glasses)
    # De naam.
    draw_text(d, "I.M.T", base // 2, 290, 8, 8, WHITE)
    draw_text(d, "TECHNOLOGY", base // 2, 358, 3, 4, CYAN)
    return im if size == base else im.resize((size, size), Image.LANCZOS)


def main():
    targets = [
        (PROJECT / "src" / "main" / "resources" / "imttech_logo.png", 256),
        (PROJECT / "publish" / "avatar-400.png", 400),
    ]
    for path, size in targets:
        path.parent.mkdir(parents=True, exist_ok=True)
        logo(size).save(path)
        print("geschreven:", path.relative_to(PROJECT))


if __name__ == "__main__":
    main()
