"""
Maakt alle textures (plaatjes) van de Stark Tech-mod.

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_textures.py

Nodig: Python 3 met Pillow (pip install pillow).
De plaatjes komen in src/main/resources/assets/starktech/textures/.
Wil je een texture liever zelf tekenen (bv. in Blockbench of Paint.NET)? Teken dan over
het gegenereerde bestand heen en draai dit script daarna niet meer voor dat bestand.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "starktech" / "textures"

# Kleurenpalet (R, G, B, A)
CLEAR = (0, 0, 0, 0)
FRAME = (24, 24, 28, 255)
FRAME_HI = (62, 62, 70, 255)
LENS = (120, 34, 28, 255)
LENS_HI = (196, 84, 60, 255)
LENS_DARK = (74, 20, 18, 255)
CYAN = (63, 216, 255, 255)
CYAN_DIM = (30, 110, 140, 255)
CYAN_DARK = (16, 58, 76, 255)
METAL_1 = (46, 52, 60, 255)
METAL_2 = (58, 66, 76, 255)
METAL_3 = (74, 84, 96, 255)
METAL_DARK = (28, 32, 38, 255)
GOLD = (222, 177, 60, 255)
GOLD_DARK = (150, 110, 30, 255)
RED = (200, 40, 40, 255)

# De kleuren die je een rayshield kunt geven (zie ShieldColor in de mod).
RAYSHIELD_COLORS = {
    "blue": (60, 140, 255),
    "cyan": (60, 220, 240),
    "green": (70, 230, 120),
    "yellow": (245, 220, 70),
    "red": (235, 70, 60),
    "purple": (190, 90, 245),
}


def img(w, h, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def px(im, points, color):
    for x, y in points:
        im.putpixel((x, y), color)


def save(im, rel):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ROOT.parent.parent.parent.parent.parent.parent))


# --------------------------------------------------------------------- Items

def edith_glasses_item():
    """De bril als item-icoon: zwart montuur met rood-getinte glazen."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # Montuur (bovenrand en omtrek van twee glazen)
    d.rectangle([1, 5, 7, 10], fill=FRAME)
    d.rectangle([8, 5, 14, 10], fill=FRAME)
    # Glazen
    d.rectangle([2, 6, 6, 9], fill=LENS)
    d.rectangle([9, 6, 13, 9], fill=LENS)
    # Onderkant van de glazen iets donkerder
    d.line([2, 9, 6, 9], fill=LENS_DARK)
    d.line([9, 9, 13, 9], fill=LENS_DARK)
    # Glans
    px(im, [(3, 6), (4, 6), (3, 7), (10, 6), (11, 6), (10, 7)], LENS_HI)
    # Neusbrug en bovenlijn
    d.line([7, 6, 8, 6], fill=FRAME_HI)
    d.line([1, 5, 14, 5], fill=FRAME_HI)
    # Pootjes
    px(im, [(0, 6), (15, 6), (0, 7), (15, 7)], FRAME)
    # Klein blauw lichtje (E.D.I.T.H. staat aan)
    px(im, [(13, 5)], CYAN)
    save(im, "item/edith_glasses.png")


def stark_chip():
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # Pootjes
    for i in range(4, 12, 2):
        d.line([i, 2, i, 3], fill=GOLD)
        d.line([i, 12, i, 13], fill=GOLD)
        d.line([2, i, 3, i], fill=GOLD)
        d.line([12, i, 13, i], fill=GOLD)
    # Behuizing
    d.rectangle([4, 4, 11, 11], fill=METAL_DARK)
    d.rectangle([4, 4, 11, 4], fill=METAL_2)
    d.rectangle([4, 4, 4, 11], fill=METAL_2)
    # Blauwe kern (arc-reactor-achtig)
    d.rectangle([6, 6, 9, 9], fill=CYAN_DIM)
    px(im, [(7, 7), (8, 7), (7, 8), (8, 8)], CYAN)
    px(im, [(6, 6)], (200, 245, 255, 255))
    save(im, "item/stark_chip.png")


def battery_upgrade():
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    d.rectangle([6, 1, 9, 2], fill=METAL_3)            # pool
    d.rectangle([4, 3, 11, 14], fill=METAL_DARK)       # behuizing
    d.rectangle([4, 3, 11, 3], fill=METAL_3)
    d.rectangle([4, 3, 4, 14], fill=METAL_2)
    d.rectangle([5, 5, 10, 13], fill=CYAN_DARK)        # venster
    d.rectangle([5, 9, 10, 13], fill=CYAN_DIM)         # lading
    # Bliksem
    px(im, [(8, 5), (7, 6), (7, 7), (6, 8), (7, 8), (8, 8), (9, 8), (8, 9), (8, 10), (7, 11)], CYAN)
    save(im, "item/battery_upgrade.png")


def scanner_upgrade():
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    d.ellipse([2, 2, 13, 13], fill=METAL_DARK, outline=METAL_3)
    d.ellipse([4, 4, 11, 11], outline=CYAN_DIM)
    d.ellipse([6, 6, 9, 9], outline=CYAN_DIM)
    # Radar-streep
    d.line([8, 8, 12, 4], fill=CYAN)
    px(im, [(7, 7), (8, 7), (7, 8), (8, 8)], CYAN)
    # Stipjes (gevonden ertsen)
    px(im, [(5, 10)], (92, 225, 230, 255))
    px(im, [(10, 10)], GOLD)
    save(im, "item/scanner_upgrade.png")


# --------------------------------------------------------------------- Bril op je hoofd

def edith_equipment():
    """
    De bril zoals hij op je hoofd getekend wordt (64x32, zelfde indeling als een helm).
    Het hoofd-vlak van voren staat op x=8..15, y=8..15.
    """
    im = img(64, 32)
    fx, fy = 8, 8  # voorkant van het hoofd
    row = 3        # op welke hoogte de bril zit (0 = bovenkant hoofd)
    # Bovenrand van het montuur
    for x in range(8):
        im.putpixel((fx + x, fy + row), FRAME)
    # Glazen (rij onder de bovenrand)
    lens_row = [FRAME, LENS_HI, LENS, FRAME, FRAME, LENS_HI, LENS, FRAME]
    for x, c in enumerate(lens_row):
        im.putpixel((fx + x, fy + row + 1), c)
    # Onderrand (met ruimte voor de neus in het midden)
    bottom = [FRAME, LENS_DARK, LENS_DARK, FRAME, FRAME, LENS_DARK, LENS_DARK, FRAME]
    for x, c in enumerate(bottom):
        im.putpixel((fx + x, fy + row + 2), c)
    # Blauw lichtje op de rechterhoek van het montuur
    im.putpixel((fx + 7, fy + row), CYAN)

    # Pootjes langs de zijkanten.
    # Rechterzijde van het hoofd: x=0..7 (x=7 grenst aan de voorkant)
    for x in range(2, 8):
        im.putpixel((x, fy + row + 1), FRAME)
    # Linkerzijde van het hoofd: x=16..23 (x=16 grenst aan de voorkant)
    for x in range(16, 22):
        im.putpixel((x, fy + row + 1), FRAME)
    # In Minecraft 1.21.1 is de bril een stuk armor; de texture hoort daarom in
    # textures/models/armor/ en heet <naam>_layer_1.png (zie ModArmorMaterials).
    save(im, "models/armor/edith_layer_1.png")


# --------------------------------------------------------------------- Laadstation (blok)

def station_textures():
    # Zijkant: donker metaal met paneelnaden en een blauwe lichtstreep
    side = img(16, 16, METAL_1)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 0], fill=METAL_3)
    d.rectangle([0, 15, 15, 15], fill=METAL_DARK)
    d.line([0, 5, 15, 5], fill=METAL_DARK)
    d.line([0, 6, 15, 6], fill=METAL_2)
    d.line([2, 10, 13, 10], fill=CYAN_DIM)
    px(side, [(7, 10), (8, 10)], CYAN)
    for x in (1, 14):
        px(side, [(x, 2), (x, 13)], METAL_3)  # schroefjes
    save(side, "block/charging_station_side.png")

    # Bovenkant: plaat met een blauwe ring (zoals een arc reactor)
    top = img(16, 16, METAL_2)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=METAL_3)
    d.ellipse([2, 2, 13, 13], fill=METAL_DARK, outline=CYAN_DIM)
    d.ellipse([4, 4, 11, 11], outline=CYAN)
    d.ellipse([6, 6, 9, 9], fill=CYAN_DIM)
    px(top, [(7, 7), (8, 8)], (200, 245, 255, 255))
    save(top, "block/charging_station_top.png")

    # Zuil: donker met een verticale lichtstrook
    column = img(16, 16, METAL_DARK)
    d = ImageDraw.Draw(column)
    d.line([7, 0, 7, 15], fill=CYAN_DIM)
    d.line([8, 0, 8, 15], fill=CYAN)
    d.line([4, 0, 4, 15], fill=METAL_1)
    d.line([11, 0, 11, 15], fill=METAL_1)
    save(column, "block/charging_station_column.png")

    # Schermpje op de voorkant
    screen = img(16, 16, (10, 22, 32, 255))
    d = ImageDraw.Draw(screen)
    d.rectangle([0, 0, 15, 15], outline=METAL_3)
    d.line([2, 3, 9, 3], fill=CYAN)
    d.line([2, 5, 12, 5], fill=CYAN_DIM)
    d.line([2, 7, 7, 7], fill=CYAN_DIM)
    d.rectangle([2, 10, 13, 12], outline=CYAN_DIM)
    d.rectangle([3, 11, 9, 11], fill=CYAN)
    save(screen, "block/charging_station_screen.png")

    # Onderkant
    bottom = img(16, 16, METAL_DARK)
    d = ImageDraw.Draw(bottom)
    d.rectangle([0, 0, 15, 15], outline=METAL_1)
    save(bottom, "block/charging_station_bottom.png")


# --------------------------------------------------------------------- GUI van het laadstation

GUI_BG = (19, 28, 36, 255)
GUI_BORDER = (10, 16, 22, 255)
GUI_LINE = (30, 90, 110, 255)
SLOT_BG = (11, 17, 23, 255)
SLOT_EDGE = (42, 108, 128, 255)


def slot(d, x, y):
    """Een slot van 18x18 op de plek waar Minecraft het item tekent (x, y) - 1."""
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=SLOT_BG, outline=SLOT_EDGE)


def gui():
    im = img(256, 256)
    d = ImageDraw.Draw(im)
    w, h = 176, 166
    # Paneel met afgeronde hoeken
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=4, fill=GUI_BG, outline=GUI_BORDER)
    d.rounded_rectangle([1, 1, w - 2, h - 2], radius=3, outline=GUI_LINE)
    # Scheidingslijn boven de spelersinventaris
    d.line([7, 79, w - 8, 79], fill=GUI_LINE)

    # Energiebalk van het station (kader)
    d.rectangle([9, 16, 20, 69], fill=SLOT_BG, outline=SLOT_EDGE)
    for yy in range(22, 69, 6):  # streepjes
        d.line([21, yy, 22, yy], fill=GUI_LINE)

    # Brandstof-slot + bliksemicoontje erboven
    slot(d, 26, 53)
    px(im, [(34, 42), (33, 43), (33, 44), (32, 45), (33, 45), (34, 45), (35, 45), (34, 46), (34, 47), (33, 48)], CYAN_DIM)

    # Bril-slot met een groter "hologram"-kader
    d.rectangle([72, 25, 103, 52], outline=GUI_LINE)
    for cx, cy in ((72, 25), (103, 25), (72, 52), (103, 52)):
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=CYAN_DIM)
    slot(d, 80, 33)

    # Energiebalk van de bril (kader)
    d.rectangle([61, 55, 114, 60], fill=SLOT_BG, outline=SLOT_EDGE)

    # Upgrade-slot
    slot(d, 134, 33)

    # Pijl van upgrade (rechts) naar bril (links)
    ax, ay = 104, 37
    d.rectangle([ax + 4, ay + 2, ax + 25, ay + 5], outline=GUI_LINE)
    d.polygon([(ax, ay + 3.5), (ax + 5, ay - 1), (ax + 5, ay + 8)], outline=GUI_LINE)

    # Spelersinventaris (3 rijen) en hotbar
    for r in range(3):
        for c in range(9):
            slot(d, 8 + c * 18, 84 + r * 18)
    for c in range(9):
        slot(d, 8 + c * 18, 142)
    save(im, "gui/charging_station.png")


# --------------------------------------------------------------------- Fase 2: drones

def drone_upgrade():
    """Een drone van bovenaf gezien."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # Armen (schuin)
    d.line([3, 3, 12, 12], fill=METAL_2, width=1)
    d.line([12, 3, 3, 12], fill=METAL_2, width=1)
    # Rotors
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], outline=(170, 180, 190, 255))
        px(im, [(cx, cy)], METAL_3)
    # Romp
    d.rectangle([6, 6, 9, 9], fill=METAL_DARK, outline=METAL_3)
    px(im, [(7, 7), (8, 8)], CYAN)
    # Plusje (upgrade)
    px(im, [(13, 7), (13, 8), (13, 9), (12, 8), (14, 8)], (120, 255, 150, 255))
    save(im, "item/drone_upgrade.png")


def box_regions(u, v, w, h, d):
    """Waar elk vlak van een model-blokje op de texture staat (standaard Minecraft-indeling)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill_region(im, region, color):
    x, y, w, h = region
    for xx in range(x, x + w):
        for yy in range(y, y + h):
            im.putpixel((xx, yy), color)


def drone_textures():
    """De drone (64x32) en twee lichtgevende lagen: rood (aanval) en blauw (bescherming)."""
    base = img(64, 32)
    body = box_regions(0, 0, 6, 3, 6)
    camera = box_regions(0, 10, 2, 2, 2)
    arm_a = box_regions(0, 16, 12, 1, 1)
    arm_b = box_regions(0, 19, 12, 1, 1)
    blade_x = box_regions(28, 0, 6, 1, 1)
    blade_z = box_regions(28, 3, 1, 1, 6)
    hub = box_regions(28, 11, 2, 2, 2)

    for region in body.values():
        fill_region(base, region, METAL_1)
    fill_region(base, body["top"], METAL_2)
    fill_region(base, body["bottom"], METAL_DARK)
    # Paneelrand op de bovenkant
    x, y, w, h = body["top"]
    for i in range(w):
        base.putpixel((x + i, y), METAL_3)
        base.putpixel((x + i, y + h - 1), METAL_DARK)
    # Donkere streep langs de zijkanten
    for name in ("east", "front", "west", "back"):
        x, y, w, h = body[name]
        for i in range(w):
            base.putpixel((x + i, y + h - 1), METAL_DARK)
    for region in camera.values():
        fill_region(base, region, METAL_DARK)
    fill_region(base, camera["front"], (20, 20, 24, 255))
    for arm in (arm_a, arm_b):
        for region in arm.values():
            fill_region(base, region, METAL_2)
    for blade in (blade_x, blade_z):
        for region in blade.values():
            fill_region(base, region, (175, 185, 195, 255))
    for region in hub.values():
        fill_region(base, region, METAL_3)
    save(base, "entity/drone/stark_drone.png")

    # Lichtlagen: alleen de lampjes, de rest doorzichtig.
    for name, color, dim in (("attack", (255, 70, 55, 255), (150, 30, 25, 255)),
                             ("scout", (235, 250, 255, 255), (120, 140, 150, 255)),
                             ("guard", CYAN, CYAN_DIM)):
        lights = img(64, 32)
        # Camera-lens
        fill_region(lights, camera["front"], color)
        # Lichtje midden op de bovenkant
        x, y, w, h = body["top"]
        px(lights, [(x + 2, y + 2), (x + 3, y + 2), (x + 2, y + 3), (x + 3, y + 3)], color)
        # Lichtstreep op voor- en achterkant
        for side in ("front", "back"):
            x, y, w, h = body[side]
            for i in range(1, w - 1):
                lights.putpixel((x + i, y + 1), dim)
        # Stipje op elke naaf
        x, y, w, h = hub["top"]
        px(lights, [(x, y), (x + 1, y + 1)], dim)
        save(lights, f"entity/drone/stark_drone_lights_{name}.png")

    # --- De vijandige raiddrone: dezelfde machine, maar donkerder en met oranje lampjes.
    raid = base.copy()
    for region in body.values():
        fill_region(raid, region, (32, 30, 34, 255))
    fill_region(raid, body["top"], (44, 40, 46, 255))
    fill_region(raid, body["bottom"], (20, 18, 22, 255))
    for region in camera.values():
        fill_region(raid, region, (22, 20, 24, 255))
    for arm in (arm_a, arm_b):
        for region in arm.values():
            fill_region(raid, region, (40, 38, 42, 255))
    save(raid, "entity/drone/raid_drone.png")

    raid_lights = img(64, 32)
    orange = (255, 140, 30, 255)
    orange_dim = (150, 70, 10, 255)
    fill_region(raid_lights, camera["front"], orange)
    x, y, w, h = body["top"]
    px(raid_lights, [(x + 2, y + 2), (x + 3, y + 2), (x + 2, y + 3), (x + 3, y + 3)], orange)
    for side in ("front", "back", "east", "west"):
        x, y, w, h = body[side]
        for i in range(1, w - 1):
            raid_lights.putpixel((x + i, y + 1), orange_dim)
    save(raid_lights, "entity/drone/raid_drone_lights.png")


def planet_texture():
    """
    De aarde voor het arsenaal-scherm: een wereldkaart van 64 x 32 pixels die om een bol wordt
    gevouwen (links en rechts sluiten op elkaar aan). Oceanen, continenten, woestijn, bergen
    en ijskappen, uit "ruis" gemaakt: willekeurig maar vloeiend, en elke keer hetzelfde.
    """
    import math
    import random
    rng = random.Random(42)
    w, h = 64, 32
    # Een grof rooster van willekeurige hoogtes, vloeiend opgeblazen naar 64 x 32.
    grid_w, grid_h = 8, 5
    grid = [[rng.random() for _ in range(grid_w)] for _ in range(grid_h)]
    fine = [[rng.random() for _ in range(grid_w * 2)] for _ in range(grid_h * 2)]

    def sample(g, gw, gh, x, y):
        fx, fy = x / w * gw, y / (h - 1) * (gh - 1)
        x0, y0 = int(fx) % gw, min(int(fy), gh - 2)
        tx, ty = fx - int(fx), fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        x1 = (x0 + 1) % gw  # links en rechts lopen door: het is een bol
        top = g[y0][x0] * (1 - tx) + g[y0][x1] * tx
        bottom = g[y0 + 1][x0] * (1 - tx) + g[y0 + 1][x1] * tx
        return top * (1 - ty) + bottom * ty

    im = img(w, h)
    for y in range(h):
        latitude = abs(y - (h - 1) / 2) / ((h - 1) / 2)  # 0 = evenaar, 1 = pool
        for x in range(w):
            height = sample(grid, grid_w, grid_h, x, y) * 0.75 + sample(fine, grid_w * 2, grid_h * 2, x, y) * 0.25
            if latitude > 0.85 or (latitude > 0.72 and height > 0.5):
                colour = (232, 242, 250, 255)  # ijs
            elif height < 0.47:
                deep = height < 0.36
                colour = (22, 58, 132, 255) if deep else (38, 96, 170, 255)
            elif height > 0.72:
                colour = (118, 108, 96, 255)  # bergen
            elif latitude < 0.35 and height < 0.58:
                colour = (196, 172, 104, 255)  # woestijn rond de evenaar
            else:
                colour = (64, 128, 58, 255) if (x + y) % 3 else (56, 116, 52, 255)
            im.putpixel((x, y), colour)
    save(im, "gui/arsenal/planet.png")


def satellite_textures():
    """
    De Stark-satelliet in het arsenaal- en satellietscherm (128 x 64): een romp met gouden
    strepen, twee blauwe zonnepanelen, een schotel, een antenne en een motor. Plus een lichtlaag
    met de reactor en de lampjes, die ook in het donker gloeien.

    De rechterhelft (x 64 en verder) is voor de onderdelen die er bij upgrades bij komen
    (zie SatelliteModel): raketkokers, het kanon, langere panelen, condensatoren, een grote
    schotel, een sensor, de lens van de schuivende laser en een grote reactor.
    """
    base = img(128, 64)
    core = box_regions(0, 0, 8, 8, 10)
    reactor = box_regions(40, 0, 4, 4, 1)
    strut = box_regions(40, 8, 4, 2, 2)
    panel = box_regions(0, 20, 18, 1, 8)
    dish = box_regions(0, 32, 7, 1, 7)
    antenna = box_regions(30, 32, 1, 5, 1)
    engine = box_regions(36, 32, 4, 4, 3)
    # De upgrade-onderdelen (zelfde plekken als de texOffs in SatelliteModel).
    pod = box_regions(64, 0, 2, 2, 6)
    cannon = box_regions(80, 0, 3, 3, 3)
    barrel = box_regions(92, 0, 2, 5, 2)
    ring = box_regions(100, 0, 3, 1, 3)
    tip = box_regions(112, 0, 1, 1, 1)
    panel_ext = box_regions(64, 8, 10, 1, 8)
    coil = box_regions(100, 8, 2, 2, 8)
    dish_big = box_regions(64, 20, 10, 1, 10)
    antenna_2 = box_regions(104, 20, 1, 6, 1)
    sensor = box_regions(108, 20, 3, 2, 3)
    lens = box_regions(64, 32, 6, 2, 3)
    fins = box_regions(84, 32, 1, 3, 4)
    reactor_big = box_regions(96, 32, 6, 6, 1)
    lens_2 = box_regions(110, 32, 4, 1, 2)

    for region in core.values():
        fill_region(base, region, METAL_2)
    for name in ("east", "west", "front", "back"):
        x, y, w, h = core[name]
        for i in range(w):
            base.putpixel((x + i, y + 2), GOLD)          # gouden streep rondom
            base.putpixel((x + i, y + h - 1), METAL_DARK)
    fill_region(base, core["top"], METAL_3)
    fill_region(base, core["bottom"], METAL_DARK)
    for region in reactor.values():
        fill_region(base, region, METAL_DARK)
    fill_region(base, reactor["front"], (20, 70, 90, 255))
    for region in strut.values():
        fill_region(base, region, METAL_3)
    # Zonnepanelen: donkerblauw met een rooster van cellen.
    for region in panel.values():
        fill_region(base, region, (24, 40, 92, 255))
    for side in ("top", "bottom"):
        x, y, w, h = panel[side]
        for i in range(w):
            for j in range(h):
                if i % 3 == 0 or j % 4 == 0:
                    base.putpixel((x + i, y + j), (70, 90, 130, 255))
    for region in dish.values():
        fill_region(base, region, (210, 214, 220, 255))
    x, y, w, h = dish["top"]
    px(base, [(x + 3, y + 3), (x + 2, y + 3), (x + 3, y + 2)], (150, 156, 164, 255))
    for region in antenna.values():
        fill_region(base, region, (180, 184, 190, 255))
    for region in engine.values():
        fill_region(base, region, METAL_DARK)

    # --- Raketkokers: donker metaal met een rode punt voorop en gouden banden.
    for region in pod.values():
        fill_region(base, region, METAL_1)
    # De lange kanten: een lichte bovenrand en twee gouden banden dwars eroverheen.
    for name in ("east", "west"):
        x, y, w, h = pod[name]
        for j in range(h):
            base.putpixel((x + 1, y + j), GOLD_DARK)
            base.putpixel((x + w - 2, y + j), GOLD_DARK)
    for name in ("top", "bottom"):
        x, y, w, h = pod[name]
        for i in range(w):
            base.putpixel((x + i, y + 1), GOLD_DARK)
            base.putpixel((x + i, y + h - 2), GOLD_DARK)
    fill_region(base, pod["front"], RED)
    fill_region(base, pod["back"], METAL_DARK)
    # --- Het kanon: een wit-gouden huis met een donkere loop en gouden ringen.
    for region in cannon.values():
        fill_region(base, region, (196, 200, 206, 255))
    for name in ("east", "west", "front", "back"):
        x, y, w, h = cannon[name]
        for i in range(w):
            base.putpixel((x + i, y + h - 1), GOLD)
    for region in barrel.values():
        fill_region(base, region, METAL_DARK)
    for name in ("east", "west", "front", "back"):
        x, y, w, h = barrel[name]
        for j in range(0, h, 2):
            for i in range(w):
                base.putpixel((x + i, y + j), METAL_1)
    for region in ring.values():
        fill_region(base, region, GOLD)
    fill_region(base, ring["top"], GOLD_DARK)
    for region in tip.values():
        fill_region(base, region, (180, 240, 255, 255))
    # --- Verlengstukken van de zonnepanelen: net als de gewone panelen.
    for region in panel_ext.values():
        fill_region(base, region, (24, 40, 92, 255))
    for side in ("top", "bottom"):
        x, y, w, h = panel_ext[side]
        for i in range(w):
            for j in range(h):
                if i % 3 == 0 or j % 4 == 0:
                    base.putpixel((x + i, y + j), (70, 90, 130, 255))
    # --- Condensatoren: koperen spoelen met donkere groeven.
    for region in coil.values():
        fill_region(base, region, (176, 104, 56, 255))
    for name in ("top", "bottom", "east", "west"):
        x, y, w, h = coil[name]
        long_side_vertical = name in ("east", "west")
        if long_side_vertical:
            for i in range(0, w, 2):
                for j in range(h):
                    base.putpixel((x + i, y + j), (120, 64, 32, 255))
        else:
            for j in range(0, h, 2):
                for i in range(w):
                    base.putpixel((x + i, y + j), (120, 64, 32, 255))
    fill_region(base, coil["front"], METAL_3)
    fill_region(base, coil["back"], METAL_3)
    # --- De grote schotel: wit met ringen.
    for region in dish_big.values():
        fill_region(base, region, (214, 218, 224, 255))
    x, y, w, h = dish_big["top"]
    for i in range(w):
        for j in range(h):
            r = max(abs(i - 4.5), abs(j - 4.5))
            if int(r) in (1, 3):
                base.putpixel((x + i, y + j), (170, 176, 184, 255))
    px(base, [(x + 4, y + 4), (x + 5, y + 5), (x + 4, y + 5), (x + 5, y + 4)], METAL_2)
    for region in antenna_2.values():
        fill_region(base, region, (180, 184, 190, 255))
    for region in sensor.values():
        fill_region(base, region, METAL_2)
    fill_region(base, sensor["front"], (20, 20, 24, 255))
    fill_region(base, sensor["bottom"], (20, 20, 24, 255))
    # --- De lens van de schuivende laser: metaal met een rode onderkant.
    for region in lens.values():
        fill_region(base, region, METAL_2)
    fill_region(base, lens["bottom"], (110, 24, 20, 255))
    for name in ("east", "west", "front", "back"):
        x, y, w, h = lens[name]
        for i in range(w):
            base.putpixel((x + i, y), GOLD_DARK)
    for region in fins.values():
        fill_region(base, region, METAL_3)
    for region in lens_2.values():
        fill_region(base, region, METAL_DARK)
    fill_region(base, lens_2["bottom"], (110, 24, 20, 255))
    # --- De grote reactor: een donkere ring met goud eromheen.
    for region in reactor_big.values():
        fill_region(base, region, GOLD)
    x, y, w, h = reactor_big["front"]
    fill_region(base, (x + 1, y + 1, w - 2, h - 2), METAL_DARK)
    fill_region(base, (x + 2, y + 2, w - 4, h - 4), (20, 70, 90, 255))
    save(base, "gui/arsenal/satellite.png")

    lights = img(128, 64)
    # De reactor voorop, en een lichtje op de antenne.
    x, y, w, h = reactor["front"]
    fill_region(lights, (x, y, w, h), CYAN)
    px(lights, [(x + 1, y + 1), (x + 2, y + 2)], (220, 250, 255, 255))
    x, y, w, h = antenna["top"]
    fill_region(lights, (x, y, w, h), (255, 70, 55, 255))
    # De motor achteraan gloeit oranje.
    x, y, w, h = engine["back"]
    fill_region(lights, (x + 1, y + 1, w - 2, h - 2), (255, 150, 40, 255))
    # Upgrades: de punt van het kanon, de rode lenzen, de sensor, de reactorkern en een lichtje
    # op elke raketkoker en condensator.
    for region in tip.values():
        fill_region(lights, region, (200, 250, 255, 255))
    x, y, w, h = barrel["bottom"]
    fill_region(lights, (x, y, w, h), CYAN)
    fill_region(lights, lens["bottom"], (255, 60, 45, 255))
    fill_region(lights, lens_2["bottom"], (255, 90, 60, 255))
    x, y, w, h = sensor["front"]
    px(lights, [(x + 1, y), (x + 1, y + 1)], (255, 70, 55, 255))
    x, y, w, h = reactor_big["front"]
    fill_region(lights, (x + 2, y + 2, w - 4, h - 4), CYAN)
    px(lights, [(x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)], CYAN_DIM)
    x, y, w, h = pod["front"]
    px(lights, [(x, y)], (255, 90, 60, 255))
    for name in ("top",):
        x, y, w, h = coil[name]
        for j in range(1, h, 3):
            px(lights, [(x, y + j), (x + 1, y + j)], CYAN_DIM)
    x, y, w, h = antenna_2["top"]
    fill_region(lights, (x, y, w, h), (255, 70, 55, 255))
    save(lights, "gui/arsenal/satellite_lights.png")


def drone_damage_textures():
    """
    Schade over een drone heen (zoals de barsten op een ijzergolem): roetvlekken, krassen en
    blank metaal waar de verf eraf is. Drie stappen, elk erger dan de vorige. De rest van het
    plaatje is doorzichtig, dus je ziet de gewone drone eronder.

    Elke stap tekent de vorige opnieuw en doet er een schepje bovenop, met vaste "toevals"
    getallen zodat het plaatje elke keer hetzelfde uitkomt.
    """
    import random
    rng = random.Random(7)
    body = box_regions(0, 0, 6, 3, 6)
    arm_a = box_regions(0, 16, 12, 1, 1)
    arm_b = box_regions(0, 19, 12, 1, 1)
    camera = box_regions(0, 10, 2, 2, 2)
    soot = [(18, 16, 14, 230), (30, 26, 22, 210), (44, 36, 30, 190)]
    scratch = (150, 158, 168, 255)
    burnt = (70, 40, 22, 235)

    surfaces = list(body.values()) + list(arm_a.values()) + list(arm_b.values()) + list(camera.values())
    pixels = [(x + i, y + j) for (x, y, w, h) in surfaces for i in range(w) for j in range(h)]

    overlay = img(64, 32)
    for name, soot_spots, scratches in (("low", 6, 3), ("medium", 12, 7), ("high", 22, 12)):
        # Roet: kleine vlekjes van een paar pixels.
        for _ in range(soot_spots):
            cx, cy = rng.choice(pixels)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (1, 1)):
                if (cx + dx, cy + dy) in pixels and rng.random() < 0.8:
                    overlay.putpixel((cx + dx, cy + dy), rng.choice(soot))
        # Krassen: korte schuine lijntjes van blank metaal.
        for _ in range(scratches):
            cx, cy = rng.choice(pixels)
            step = rng.choice([(1, 0), (1, 1), (0, 1), (1, -1)])
            for k in range(rng.randint(2, 4)):
                p = (cx + step[0] * k, cy + step[1] * k)
                if p in pixels:
                    overlay.putpixel(p, scratch)
        if name == "high":
            # Verbrande plekken op de romp: daar zat het vuur.
            for region in (body["top"], body["front"], body["east"]):
                x, y, w, h = region
                for _ in range(4):
                    overlay.putpixel((x + rng.randrange(w), y + rng.randrange(h)), burnt)
        save(overlay, f"entity/drone/drone_damage_{name}.png")


# --------------------------------------------------------------------- Fase 9: redstone-relais

def relay_textures():
    """Een plat metalen kastje met een lampje bovenop (uit = donker, aan = groen)."""
    # Zijkant: metaal met een naad en een streepje redstone-rood
    side = img(16, 16, METAL_1)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 0], fill=METAL_3)
    d.rectangle([0, 15, 15, 15], fill=METAL_DARK)
    d.line([1, 9, 14, 9], fill=METAL_DARK)
    d.line([2, 12, 6, 12], fill=(170, 40, 40, 255))
    for x in (1, 14):
        px(side, [(x, 3), (x, 6)], METAL_3)
    save(side, "block/relay_side.png")

    # Bovenkant: plaatje met vier schroeven en een lampje in het midden
    def top(lamp, glow):
        im = img(16, 16, METAL_2)
        dd = ImageDraw.Draw(im)
        dd.rectangle([0, 0, 15, 15], outline=METAL_3)
        dd.rectangle([2, 2, 13, 13], outline=METAL_DARK)
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            px(im, [(x, y)], METAL_3)
        dd.ellipse([6, 6, 9, 9], fill=lamp, outline=glow)
        return im

    save(top((40, 60, 55, 255), (70, 90, 85, 255)), "block/relay_top.png")
    save(top((92, 255, 157, 255), (200, 255, 225, 255)), "block/relay_top_on.png")

    # Onderkant: gewoon donker metaal
    bottom = img(16, 16, METAL_DARK)
    ImageDraw.Draw(bottom).rectangle([0, 0, 15, 15], outline=METAL_1)
    save(bottom, "block/relay_bottom.png")


# --------------------------------------------------------------------- Fase 11: rayshield

def shield_textures():
    """Laserzender (met lens) en de rode energiewand."""
    # Zijkant van de zender: donker metaal met koelribben
    side = img(16, 16, METAL_DARK)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 0], fill=METAL_2)
    for y in (4, 7, 10):
        d.line([2, y, 13, y], fill=METAL_1)
    d.line([1, 13, 14, 13], fill=(150, 30, 30, 255))
    save(side, "block/emitter_side.png")

    # De lens: uit = donkerrood, aan = fel rood met een gloed
    def lens(dark, bright, glow):
        im = img(16, 16, METAL_1)
        dd = ImageDraw.Draw(im)
        dd.rectangle([0, 0, 15, 15], outline=METAL_DARK)
        dd.ellipse([3, 3, 12, 12], fill=dark, outline=METAL_3)
        dd.ellipse([5, 5, 10, 10], fill=bright)
        px(im, [(7, 7), (8, 8)], glow)
        return im

    save(lens((70, 20, 20, 255), (120, 35, 35, 255), (160, 60, 60, 255)), "block/emitter_lens.png")
    save(lens((180, 30, 30, 255), (255, 90, 80, 255), (255, 220, 210, 255)), "block/emitter_lens_on.png")

    # De energiewand: rode strepen die door elkaar lopen, half doorzichtig
    wall = img(16, 16, (200, 40, 40, 110))
    d = ImageDraw.Draw(wall)
    for y in range(0, 16, 3):
        d.line([0, y, 15, y], fill=(255, 120, 100, 170))
    for x in range(1, 16, 5):
        d.line([x, 0, x, 15], fill=(255, 190, 170, 130))
    save(wall, "block/laser_wall.png")


def rayshield_walls():
    """
    Het energieveld van de rayshield, in elke kleur en met een scanlijn die omhoog loopt.

    Elke texture is 16 breed en 16*FRAMES hoog: Minecraft speelt die frames onder elkaar af
    als animatie (zie het .mcmeta-bestand ernaast). Zo lopen de lijnen echt, net als de ray
    shields uit Star Wars.
    """
    frames = 8
    for name, (r, g, b) in RAYSHIELD_COLORS.items():
        sheet = img(16, 16 * frames)
        d = ImageDraw.Draw(sheet)
        for frame in range(frames):
            top = frame * 16
            # Grondtoon: flink doorzichtig, zodat je er doorheen kijkt.
            d.rectangle([0, top, 15, top + 15], fill=(r, g, b, 70))
            # Vaste, rustige lijnen in het veld.
            for y in range(0, 16, 4):
                d.line([0, top + y, 15, top + y], fill=(min(255, r + 60), min(255, g + 60), min(255, b + 60), 110))
            # De scanlijn die per frame een stukje omhoog schuift (16 / frames = 2 pixels).
            scan = 15 - (frame * 16 // frames)
            d.line([0, top + scan, 15, top + scan], fill=(255, 255, 255, 190))
            if scan > 0:
                d.line([0, top + scan - 1, 15, top + scan - 1],
                       fill=(min(255, r + 120), min(255, g + 120), min(255, b + 120), 140))
            # Randen: een helderdere omlijsting, alsof het veld ergens in gespannen staat.
            d.rectangle([0, top, 15, top + 15], outline=(min(255, r + 90), min(255, g + 90), min(255, b + 90), 150))
        save(sheet, "block/laser_wall_%s.png" % name)


def charger_textures():
    """
    Het zonnepaneel en de warmtegenerator, plus het scherm dat ze delen.

    Het paneel is een donkerblauw raster met lichtblauwe cellen; de generator is een
    metalen kast met een gloeiend rooster aan de voorkant.
    """
    # --- Zonnepaneel: bovenkant (de cellen) ---
    top = img(16, 16, (18, 26, 40, 255))
    d = ImageDraw.Draw(top)
    for cx in range(1, 15, 4):
        for cz in range(1, 15, 4):
            d.rectangle([cx, cz, cx + 2, cz + 2], fill=(40, 90, 170, 255))
            px(top, [(cx, cz)], (110, 180, 255, 255))
    d.rectangle([0, 0, 15, 15], outline=METAL_3)
    save(top, "block/solar_panel_top.png")

    # --- Zonnepaneel: zijkant (een dunne rand) ---
    side = img(16, 16, METAL_2)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 5], fill=METAL_1, outline=METAL_DARK)
    d.rectangle([0, 6, 15, 15], fill=(0, 0, 0, 0))
    px(side, [(3, 2), (8, 2), (12, 2)], CYAN_DIM)
    save(side, "block/solar_panel_side.png")

    # --- Warmtegenerator: zijkant ---
    gen = img(16, 16, METAL_2)
    d = ImageDraw.Draw(gen)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.rectangle([2, 2, 13, 6], fill=METAL_1, outline=METAL_3)
    for y in range(9, 15, 2):
        d.line([2, y, 13, y], fill=(120, 60, 30, 255))
    save(gen, "block/heat_generator_side.png")

    # --- Warmtegenerator: voorkant met gloeiend rooster ---
    front = img(16, 16, METAL_2)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.rectangle([3, 3, 12, 12], fill=(60, 20, 10, 255), outline=METAL_3)
    for y in range(4, 12, 2):
        d.line([4, y, 11, y], fill=(255, 140, 40, 255))
        d.line([4, y + 1, 11, y + 1], fill=(180, 70, 20, 255))
    save(front, "block/heat_generator_front.png")

    # --- Warmtegenerator: bovenkant ---
    gtop = img(16, 16, METAL_1)
    d = ImageDraw.Draw(gtop)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.ellipse([5, 5, 10, 10], fill=(90, 40, 20, 255), outline=METAL_3)
    px(gtop, [(7, 7), (8, 8)], (255, 160, 60, 255))
    save(gtop, "block/heat_generator_top.png")

    # --- Het scherm dat beide apparaten delen ---
    gui = img(256, 256, CLEAR)
    d = ImageDraw.Draw(gui)
    d.rectangle([0, 0, 175, 165], fill=(28, 34, 40, 255), outline=(12, 16, 20, 255))
    # De balk links (wat het apparaat zelf heeft).
    d.rectangle([9, 16, 20, 69], fill=(14, 18, 22, 255), outline=(60, 72, 82, 255))
    # Het bril-slot en het brandstof-slot.
    for (sx, sy) in ((79, 24), (79, 52)):
        d.rectangle([sx, sy, sx + 17, sy + 17], fill=(20, 26, 32, 255), outline=(70, 84, 96, 255))
    # De balk onder de bril.
    d.rectangle([61, 46, 114, 51], fill=(14, 18, 22, 255))
    # De vakjes van je eigen inventaris.
    for row in range(3):
        for col in range(9):
            x0 = 7 + col * 18
            y0 = 83 + row * 18
            d.rectangle([x0, y0, x0 + 17, y0 + 17], fill=(20, 26, 32, 255), outline=(70, 84, 96, 255))
    for col in range(9):
        x0 = 7 + col * 18
        d.rectangle([x0, 141, x0 + 17, 158], fill=(20, 26, 32, 255), outline=(70, 84, 96, 255))
    save(gui, "gui/charger.png")


def emp_item():
    """De E.M.P.-zender: een klein kastje met een antenne en een blauwe puls."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # kastje
    d.rectangle([4, 7, 11, 14], fill=METAL_2, outline=METAL_DARK)
    d.rectangle([5, 9, 10, 10], fill=CYAN_DIM)
    px(im, [(6, 12), (9, 12)], GOLD)
    # antenne
    d.line([7, 3, 7, 7], fill=METAL_3)
    d.line([8, 2, 8, 7], fill=METAL_3)
    # pulsjes
    d.arc([2, 0, 13, 9], start=200, end=340, fill=CYAN)
    d.arc([0, -2, 15, 11], start=210, end=330, fill=CYAN_DIM)
    save(im, "item/emp.png")


def stark_suit():
    """Het Stark-harnas: rood en goud, met een blauwe reactor op de borst.

    Minecraft tekent een harnas in twee lagen van 64x32. Elk lichaamsdeel is een doosje, en
    elk doosje heeft zijn zes vlakken op een vaste plek in het plaatje:

    - borst   (laag 1): begint op (16,16), doosje 8 breed, 12 hoog, 4 diep
    - armen   (laag 1): begint op (40,16), doosje 4x12x4
    - laarzen (laag 1): het been-doosje op (0,16), maar alleen de onderste rijen
    - broek   (laag 2): het been-doosje op (0,16) helemaal, plus de onderkant van de borst
                        als riem

    Linker- en rechterarm (en -been) delen hetzelfde stukje plaatje; Minecraft spiegelt het.
    """
    red = (168, 28, 30, 255)
    red_hi = (206, 52, 44, 255)
    red_dark = (106, 16, 20, 255)
    gold = (224, 178, 62, 255)
    gold_hi = (246, 214, 120, 255)
    gold_dark = (150, 108, 30, 255)
    seam = (70, 10, 14, 255)
    glow = (150, 240, 255, 255)

    def faces(u, v, w, h, d):
        """De zes vlakken van een doosje: naam -> (x, y, breedte, hoogte)."""
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + d + w + d, v + d, w, h),
        }

    def fill(im, box, colour, rows=None):
        """Vult een vlak; met rows=(van, tot) alleen die rijen (tellend vanaf de bovenkant)."""
        x, y, w, h = box
        r0, r1 = rows if rows else (0, h)
        for yy in range(y + r0, y + r1):
            for xx in range(x, x + w):
                im.putpixel((xx, yy), colour)

    def shade(im, box, rows=None):
        """Lichtrand links en boven, schaduw rechts en onder: dan lijkt het rond."""
        x, y, w, h = box
        r0, r1 = rows if rows else (0, h)
        for yy in range(y + r0, y + r1):
            c = im.getpixel((x, yy))
            im.putpixel((x, yy), tuple(min(255, v + 30) for v in c[:3]) + (255,))
            c = im.getpixel((x + w - 1, yy))
            im.putpixel((x + w - 1, yy), tuple(max(0, v - 40) for v in c[:3]) + (255,))

    # ================================================================ Laag 1
    layer1 = img(64, 32)
    body = faces(16, 16, 8, 12, 4)
    arm = faces(40, 16, 4, 12, 4)
    leg = faces(0, 16, 4, 12, 4)

    # --- Borstplaat: rood, met een gouden buik en de reactor midden op de borst.
    for name, box in body.items():
        fill(layer1, box, red)
    fill(layer1, body["top"], red_hi)
    x, y, w, h = body["front"]
    # schouderrand
    for xx in range(x, x + w):
        layer1.putpixel((xx, y), red_hi)
    # gouden buik van vier plaatjes, met daaronder een donkere taille zodat de borstplaat
    # niet naadloos overloopt in de broek
    fill(layer1, body["front"], gold, rows=(6, 10))
    for xx in range(x + 1, x + w - 1):
        layer1.putpixel((xx, y + 8), gold_dark)
    for yy in range(y + 6, y + 10):
        layer1.putpixel((x + 3, yy), gold_dark)
        layer1.putpixel((x + 4, yy), gold_dark)
    for xx in range(x, x + w):
        layer1.putpixel((xx, y + 6), gold_hi)
    fill(layer1, body["front"], red_dark, rows=(10, 12))
    for xx in range(x, x + w):
        layer1.putpixel((xx, y + 11), seam)
    # de reactor: een donkere ring met een felle kern
    cx, cy = x + 3, y + 2
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        layer1.putpixel((cx + dx, cy + dy), glow)
    for dx, dy in ((-1, 0), (-1, 1), (2, 0), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):
        layer1.putpixel((cx + dx, cy + dy), seam)
    # borstplaten boven de buik, met een naad in het midden
    for yy in range(y + 1, y + 6):
        layer1.putpixel((x, yy), red_hi)
    shade(layer1, body["front"], rows=(0, 6))
    # rug: rood met een gouden ruggengraat
    bx, by, bw, bh = body["back"]
    for yy in range(by + 1, by + bh - 1):
        layer1.putpixel((bx + 3, yy), gold_dark)
        layer1.putpixel((bx + 4, yy), gold)
    fill(layer1, body["back"], red_dark, rows=(10, 12))
    # zijkanten iets donkerder
    fill(layer1, body["left"], red_dark)
    fill(layer1, body["right"], red_dark)

    # --- Armen: rode bovenarm, gouden elleboog, rode onderarm, blauwe repulsor in de hand.
    for name, box in arm.items():
        fill(layer1, box, red)
    fill(layer1, arm["top"], red_hi)
    for name in ("front", "back", "left", "right"):
        fill(layer1, arm[name], gold, rows=(5, 7))
        fill(layer1, arm[name], red_dark, rows=(10, 12))
    shade(layer1, arm["front"])
    shade(layer1, arm["back"])
    x, y, w, h = arm["bottom"]
    fill(layer1, arm["bottom"], seam)
    layer1.putpixel((x + 1, y + 1), glow)
    layer1.putpixel((x + 2, y + 1), glow)
    layer1.putpixel((x + 1, y + 2), glow)
    layer1.putpixel((x + 2, y + 2), glow)

    # --- Laarzen: alleen de onderste rijen van het been; rood met een gouden neus.
    boot_rows = (6, 12)
    for name in ("front", "back", "left", "right"):
        fill(layer1, leg[name], red, rows=boot_rows)
        fill(layer1, leg[name], red_hi, rows=(6, 7))
        fill(layer1, leg[name], gold, rows=(10, 12))
    shade(layer1, leg["front"], rows=boot_rows)
    x, y, w, h = leg["bottom"]
    fill(layer1, leg["bottom"], seam)
    layer1.putpixel((x + 1, y + 1), glow)
    layer1.putpixel((x + 2, y + 2), glow)
    save(layer1, "models/armor/stark_layer_1.png")

    # ================================================================ Laag 2: de broek
    layer2 = img(64, 32)
    body2 = faces(16, 16, 8, 12, 4)
    leg2 = faces(0, 16, 4, 12, 4)
    # riem: alleen de onderste rijen van de borst
    for name in ("front", "back", "left", "right"):
        fill(layer2, body2[name], red_dark, rows=(9, 12))
        fill(layer2, body2[name], gold_dark, rows=(9, 10))
    x, y, w, h = body2["front"]
    layer2.putpixel((x + 3, y + 10), gold_hi)
    layer2.putpixel((x + 4, y + 10), gold_hi)
    # benen: rode dijen met een gouden kniestuk; het scheenbeen zit onder de laars
    for name in ("front", "back", "left", "right"):
        fill(layer2, leg2[name], red, rows=(0, 12))
        fill(layer2, leg2[name], gold, rows=(4, 6))
    x, y, w, h = leg2["front"]
    layer2.putpixel((x + 1, y + 4), gold_hi)
    layer2.putpixel((x + 2, y + 4), gold_hi)
    # een naad over de dij, zodat het plaatwerk is en geen stof
    for yy in range(y, y + 4):
        layer2.putpixel((x + 3, yy), red_dark)
    fill(layer2, leg2["top"], red_dark)
    shade(layer2, leg2["front"])
    shade(layer2, leg2["back"])
    save(layer2, "models/armor/stark_layer_2.png")

    # ================================================================ Iconen in je rugzak
    # Borstplaat
    chest = img(16, 16)
    d = ImageDraw.Draw(chest)
    d.polygon([(3, 2), (12, 2), (13, 5), (12, 13), (3, 13), (2, 5)], fill=red, outline=seam)
    d.rectangle([0, 3, 2, 8], fill=red_dark, outline=seam)      # schouder links
    d.rectangle([13, 3, 15, 8], fill=red_dark, outline=seam)    # schouder rechts
    d.rectangle([4, 9, 11, 12], fill=gold, outline=gold_dark)   # buik
    d.line([4, 10, 11, 10], fill=gold_dark)
    d.line([7, 9, 7, 12], fill=gold_dark)
    d.line([8, 9, 8, 12], fill=gold_dark)
    d.line([3, 3, 12, 3], fill=red_hi)
    d.ellipse([6, 4, 9, 7], fill=seam)                          # de reactor
    px(chest, [(7, 5), (8, 5), (7, 6), (8, 6)], glow)
    save(chest, "item/stark_chestplate.png")

    # Broek
    legs = img(16, 16)
    d = ImageDraw.Draw(legs)
    d.rectangle([3, 1, 12, 4], fill=red_dark, outline=seam)     # riem
    d.line([4, 2, 11, 2], fill=gold_dark)
    d.rectangle([3, 5, 6, 14], fill=red, outline=seam)          # been links
    d.rectangle([9, 5, 12, 14], fill=red, outline=seam)         # been rechts
    d.rectangle([4, 9, 5, 10], fill=gold)                       # knie links
    d.rectangle([10, 9, 11, 10], fill=gold)                     # knie rechts
    px(legs, [(4, 9), (10, 9)], gold_hi)
    d.line([4, 5, 4, 7], fill=red_hi)                           # licht op de dijen
    d.line([10, 5, 10, 7], fill=red_hi)
    save(legs, "item/stark_leggings.png")

    # Laarzen
    boots = img(16, 16)
    d = ImageDraw.Draw(boots)
    d.rectangle([2, 4, 6, 12], fill=red, outline=seam)
    d.rectangle([9, 4, 13, 12], fill=red, outline=seam)
    d.rectangle([2, 10, 7, 12], fill=gold, outline=gold_dark)   # neus links
    d.rectangle([9, 10, 14, 12], fill=gold, outline=gold_dark)  # neus rechts
    d.line([3, 5, 5, 5], fill=red_hi)
    d.line([10, 5, 12, 5], fill=red_hi)
    px(boots, [(3, 13), (4, 13), (5, 13), (10, 13), (11, 13), (12, 13)], glow)  # repulsors
    save(boots, "item/stark_boots.png")


def holo_table_textures():
    """De holotafel: een metalen tafel met een oplichtende ring in het blad.

    De ring krijgt de kleur van het hologram, dus er is een blad per kleur (net als bij de
    rayshields). De zijkant is voor alle kleuren hetzelfde.
    """
    side = img(16, 16, METAL_1)
    d = ImageDraw.Draw(side)
    # Een paar richels, zodat het geen vlakke plaat is.
    d.line([0, 4, 15, 4], fill=METAL_DARK)
    d.line([0, 7, 15, 7], fill=METAL_3)
    d.line([0, 11, 15, 11], fill=METAL_DARK)
    for x in range(1, 15, 4):
        d.point((x, 9), fill=GOLD_DARK)
    save(side, "block/holo_table_side.png")

    for name, rgb in RAYSHIELD_COLORS.items():
        bright = rgb + (255,)
        dim = tuple(c // 3 for c in rgb) + (255,)
        top = img(16, 16, METAL_2)
        d = ImageDraw.Draw(top)
        d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
        # De projectiering in het midden.
        d.ellipse([3, 3, 12, 12], outline=dim)
        d.ellipse([5, 5, 10, 10], outline=bright)
        px(top, [(8, 8), (7, 8), (8, 7), (7, 7)], bright)
        # Vier zenders op de hoeken van de ring.
        px(top, [(3, 8), (12, 8), (8, 3), (8, 12)], bright)
        save(top, "block/holo_table_top_%s.png" % name)


def stark_drone_item():
    """Een gebouwde drone in je hand: een romp met vier rotorarmen en een cyaan oog."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # De vier armen, diagonaal naar de hoeken.
    for x0, y0, x1, y1 in [(3, 3, 6, 6), (12, 3, 9, 6), (3, 12, 6, 9), (12, 12, 9, 9)]:
        d.line([x0, y0, x1, y1], fill=METAL_3)
    # De rotors op het eind van elke arm.
    for cx, cy in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        d.ellipse([cx - 2, cy - 1, cx + 2, cy + 1], outline=METAL_2)
    # De romp.
    d.rectangle([5, 5, 10, 10], fill=METAL_2, outline=METAL_DARK)
    # Het oog: hier komt de laser uit.
    d.rectangle([7, 7, 8, 8], fill=CYAN)
    px(im, [(6, 6), (9, 9)], CYAN_DIM)
    save(im, "item/stark_drone.png")


if __name__ == "__main__":
    edith_glasses_item()
    stark_chip()
    battery_upgrade()
    scanner_upgrade()
    edith_equipment()
    station_textures()
    gui()
    drone_upgrade()
    drone_textures()
    drone_damage_textures()
    planet_texture()
    satellite_textures()
    relay_textures()
    shield_textures()
    rayshield_walls()
    charger_textures()
    emp_item()
    stark_drone_item()
    holo_table_textures()
    stark_suit()
