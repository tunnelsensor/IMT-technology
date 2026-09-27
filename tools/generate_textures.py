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


# --------------------------------------------------------------------- Schildkoepel

def dome_field():
    """
    Het patroon op de schildkoepel: een raster van zeshoeken, wit op zwart. De koepel telt het
    licht op bij wat erachter zit, dus zwart is onzichtbaar en wit is fel; de kleur (blauw,
    rood...) geeft de mod er zelf aan. Het plaatje herhaalt naadloos (links = rechts, boven =
    onder), zodat je geen naden op de koepel ziet.
    """
    import math
    w, h = 64, 112  # breedte van één zeshoek-rij en twee rijen hoog: zo sluit het rooster op zichzelf aan
    im = img(w, h, (0, 0, 0, 255))
    # De middens van de zeshoeken: een rooster met rijen die om de beurt een halve stap opschuiven.
    # Elk midden maar één keer (anders lijkt elk punt even ver van twee "verschillende" middens).
    centres = set()
    for row in range(-6, 10):
        for col in range(-4, 8):
            centres.add((col * w / 2 + (row % 2) * w / 4, row * h / 4))
    for y in range(h):
        for x in range(w):
            best = second = 1e9
            for cx, cy in centres:
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d < best:
                    best, second = d, best
                elif d < second:
                    second = d
            edge = (second - best) / 2  # afstand tot de rand van de zeshoek
            line = max(0.0, 1 - edge / 1.4)        # de felle rand
            glow = max(0.0, 1 - edge / 6.0) * 0.35  # een zachte gloed ernaast
            inside = 0.06                           # het veld zelf, heel zwak
            v = min(1.0, line + glow + inside)
            c = int(255 * v)
            im.putpixel((x, y), (c, c, c, 255))
    save(im, "misc/dome_field.png")


# --------------------------------------------------------------------- Stroomnet

def cable_textures():
    """
    De Stark-kabel. Het verbindingsstukje ('cable') is donker rubber met koperen randjes en een
    blauwe energiestreep in het midden. De streep loopt zowel horizontaal als verticaal (een
    kruis): elke kant van een kabelstuk laat maar één van de twee zien, precies in de lengte.
    De kern ('cable_core') is een metalen knooppunt met een blauw lampje.
    """
    rubber = (36, 38, 44, 255)
    rubber_hi = (54, 57, 64, 255)
    copper = (190, 110, 60, 255)
    side = img(16, 16, rubber)
    for i in range(16):
        for c in (5, 10):
            side.putpixel((i, c), copper)
            side.putpixel((c, i), copper)
        for c in (6, 9):
            side.putpixel((i, c), rubber_hi)
            side.putpixel((c, i), rubber_hi)
        for c in (7, 8):
            side.putpixel((i, c), CYAN_DIM)
            side.putpixel((c, i), CYAN_DIM)
    for i in range(0, 16, 4):
        px(side, [(i, 7), (7, i)], CYAN)
    save(side, "block/cable.png")

    core = img(16, 16, METAL_1)
    d = ImageDraw.Draw(core)
    d.rectangle([5, 5, 10, 10], fill=METAL_2, outline=METAL_DARK)
    px(core, [(5, 5), (10, 5), (5, 10), (10, 10)], copper)
    d.rectangle([7, 7, 8, 8], fill=CYAN)
    px(core, [(6, 7), (9, 8), (7, 6), (8, 9)], CYAN_DIM)
    save(core, "block/cable_core.png")

    # De rol kabel (het item waarmee je kabels tekent): een koperen spoel met donkere kabel
    # erop gewikkeld, een blauwe streep, en een los eindje dat naar beneden hangt.
    spool = img(16, 16)
    d = ImageDraw.Draw(spool)
    d.rectangle([2, 2, 4, 13], fill=copper, outline=(112, 56, 28, 255))      # linker flens
    d.rectangle([11, 2, 13, 13], fill=copper, outline=(112, 56, 28, 255))    # rechter flens
    d.rectangle([5, 4, 10, 11], fill=rubber)
    for y in (5, 7, 9):
        d.line([5, y, 10, y], fill=rubber_hi)
    d.line([5, 8, 10, 8], fill=CYAN_DIM)
    px(spool, [(7, 8), (8, 8)], CYAN)
    d.line([10, 11, 12, 14], fill=rubber)
    px(spool, [(12, 15)], copper)
    save(spool, "item/cable_spool.png")


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
    """
    De laserzender (een volledig blok met een lens in een frame) en de rode energiewand.
    Zie models/block/laser_emitter.json voor welk plaatje op welke kant zit.
    """
    # Zijkant: een donker metalen paneel met een lichte rand, koelsleuven en een rode streep.
    side = img(16, 16, METAL_1)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.line([1, 1, 14, 1], fill=METAL_3)
    d.line([1, 1, 1, 14], fill=METAL_2)
    for y in (4, 6, 8, 10):
        d.line([4, y, 11, y], fill=METAL_DARK)
    d.line([2, 13, 13, 13], fill=(150, 30, 30, 255))
    px(side, [(2, 2), (13, 2), (2, 14), (13, 14)], METAL_3)  # boutjes
    save(side, "block/emitter_side.png")

    # Achterkant: hetzelfde paneel met een aansluiting in het midden.
    back = side.copy()
    d = ImageDraw.Draw(back)
    d.rectangle([4, 3, 11, 11], fill=METAL_1)
    d.rectangle([6, 5, 9, 9], fill=METAL_DARK, outline=METAL_3)
    px(back, [(7, 7), (8, 7)], GOLD_DARK)
    save(back, "block/emitter_back.png")

    # Het frame om de lens: licht metaal met gouden hoeken.
    front = img(16, 16, METAL_2)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.rectangle([3, 3, 12, 12], outline=METAL_1)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        d.rectangle([x, y, x + 1, y + 1], fill=GOLD)
    d.line([5, 1, 10, 1], fill=(150, 30, 30, 255))
    save(front, "block/emitter_front.png")

    # De holte achter de lens: bijna zwart.
    recess = img(16, 16, (16, 18, 22, 255))
    save(recess, "block/emitter_recess.png")

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
    Het energieveld van de rayshield-laserpoort, in elke kleur: een raster van zeshoeken zoals op
    de schildkoepel, dat rustig van links naar rechts schuift.

    Subtiel, op jouw vraag (27 september): geen witte lijnen meer, alleen de eigen kleur. De
    randen van de zeshoeken zijn iets lichter, een paar vakjes iets donkerder, en het veld zelf
    is flink doorzichtig. Het plaatje herhaalt naadloos (links = rechts, boven = onder), dus
    naast elkaar vormen de blokken één doorlopend veld zonder naden.

    Elke texture is 32 breed en 32*FRAMES hoog: Minecraft speelt die frames onder elkaar af als
    animatie (zie het .mcmeta-bestand ernaast). Per frame schuift het raster 4 pixels op; na 8
    frames is het precies één rasterbreedte verder, zodat de lus niet hapert.
    """
    import math
    size = 32
    frames = 8
    shift = size // frames
    # De middens van de zeshoeken: rijen van 16 breed, om de beurt 8 opgeschoven, 16 hoog.
    centres = []
    for row in range(-2, 5):
        for col in range(-2, 5):
            centres.append((col * 16 + (row % 2) * 8 + 8, row * 16 + 8))

    def edge_distance(x, y):
        best = second = 1e9
        best_centre = None
        for cx, cy in centres:
            d = math.hypot(x - cx, y - cy)
            if d < best:
                best, second, best_centre = d, best, (cx, cy)
            elif d < second:
                second = d
        return (second - best) / 2, best_centre

    # Het raster één keer uitrekenen (per pixel: hoe ver van een rand, en in welk vakje).
    pattern = [[edge_distance(x + 0.5, y + 0.5) for x in range(size)] for y in range(size)]

    for name, (r, g, b) in RAYSHIELD_COLORS.items():
        def tint(amount, alpha):
            return (max(0, min(255, r + amount)), max(0, min(255, g + amount)), max(0, min(255, b + amount)), alpha)
        sheet = img(size, size * frames)
        for frame in range(frames):
            top = frame * size
            for y in range(size):
                for x in range(size):
                    # Van links naar rechts: pixel x toont wat eerder links stond.
                    edge, centre = pattern[y][(x - frame * shift) % size]
                    line = max(0.0, 1 - edge / 1.2)       # de rand van een zeshoek
                    glow = max(0.0, 1 - edge / 4.0) * 0.4  # zachte gloed tegen de rand
                    # Een paar vakjes net iets donkerder, verspreid (vast patroon, per vakje).
                    darker = ((int(centre[0]) * 7 + int(centre[1]) * 13) // 8) % 5 == 0
                    if line > 0.05:
                        sheet.putpixel((x, top + y), tint(45, int(70 + 70 * line)))
                    elif darker:
                        sheet.putpixel((x, top + y), tint(-35, int(58 + 40 * glow)))
                    else:
                        sheet.putpixel((x, top + y), tint(int(20 * glow), int(52 + 45 * glow)))
        save(sheet, "block/laser_wall_%s.png" % name)


def charger_textures():
    """
    Het zonnepaneel en de warmtegenerator, plus het scherm dat ze delen.

    Beide hebben een 3D-model van meerdere doosjes (zie tools/generate_block_models.py); elk
    doosje krijgt hier zijn eigen plaatje.

    - Zonnepaneel: donkerblauwe cellen in een aluminium frame, een donkere achterkant met
      ribben, en een metalen voet en paal.
    - Warmtegenerator: een metalen kast op een sokkel, met koelribben, koperen pijpen achter,
      een rooster bovenop, en voorin een rooster dat oranje gloeit als hij werkt.
    """
    alu = (150, 160, 172, 255)
    alu_dark = (104, 112, 124, 255)
    copper = (176, 98, 52, 255)
    copper_hi = (222, 140, 84, 255)
    copper_dark = (112, 56, 28, 255)
    hot = (255, 140, 40, 255)
    hot_dim = (180, 70, 20, 255)
    cold = (70, 34, 22, 255)
    cold_dim = (44, 22, 16, 255)

    # --- Zonnepaneel: de cellen (bovenkant van het schuine paneel) ---
    top = img(16, 16, (18, 26, 40, 255))
    d = ImageDraw.Draw(top)
    for cx in range(1, 15, 4):
        for cz in range(1, 15, 4):
            d.rectangle([cx, cz, cx + 2, cz + 2], fill=(40, 90, 170, 255))
            px(top, [(cx, cz)], (110, 180, 255, 255))
            px(top, [(cx + 2, cz + 2)], (28, 60, 120, 255))
    d.rectangle([0, 0, 15, 15], outline=alu)
    px(top, [(0, 0), (15, 0), (0, 15), (15, 15)], alu_dark)
    save(top, "block/solar_panel_top.png")

    # --- Zonnepaneel: de rand van het paneel (aluminium) ---
    frame = img(16, 16, alu)
    d = ImageDraw.Draw(frame)
    for y in range(16):
        if y % 4 == 3:
            d.line([0, y, 15, y], fill=alu_dark)
    save(frame, "block/solar_panel_frame.png")

    # --- Zonnepaneel: de achterkant (onderkant van het paneel), met ribben ---
    under = img(16, 16, METAL_1)
    d = ImageDraw.Draw(under)
    for x in (3, 8, 12):
        d.line([x, 0, x, 15], fill=METAL_3)
    d.rectangle([6, 6, 9, 9], fill=METAL_DARK, outline=METAL_3)   # aansluitdoos
    px(under, [(7, 7)], CYAN_DIM)
    save(under, "block/solar_panel_under.png")

    # --- Zonnepaneel: voet en paal (metaal met klinknagels) ---
    side = img(16, 16, METAL_2)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    px(side, [(2, 2), (13, 2), (2, 13), (13, 13)], METAL_3)
    px(side, [(7, 7), (8, 8)], CYAN_DIM)
    save(side, "block/solar_panel_side.png")

    # --- Warmtegenerator: zijkant van de kast (onder de koelribben) ---
    gen = img(16, 16, METAL_2)
    d = ImageDraw.Draw(gen)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.rectangle([2, 2, 13, 5], fill=METAL_1, outline=METAL_3)
    px(gen, [(4, 3), (11, 3)], hot_dim)
    for y in range(8, 15, 2):
        d.line([2, y, 13, y], fill=(120, 60, 30, 255))
    save(gen, "block/heat_generator_side.png")

    # --- Warmtegenerator: voorkant van de kast (het rooster zit ervoor, zie grill) ---
    front = img(16, 16, METAL_2)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.rectangle([1, 1, 14, 14], outline=METAL_3)
    # een meter bovenaan en een waarschuwingsstreep onderaan
    d.rectangle([5, 1, 10, 2], fill=METAL_DARK)
    px(front, [(6, 1), (7, 1)], (90, 220, 120, 255))
    for x in range(1, 15):
        px(front, [(x, 13)], (220, 170, 40, 255) if (x // 2) % 2 == 0 else METAL_DARK)
    save(front, "block/heat_generator_front.png")

    # --- Warmtegenerator: het rooster, uit en aan ---
    for name, slat, slat_dim, back in (("heat_generator_grill", cold, cold_dim, (30, 14, 10, 255)),
                                        ("heat_generator_grill_on", hot, hot_dim, (90, 30, 10, 255))):
        grill = img(16, 16, back)
        d = ImageDraw.Draw(grill)
        for y in range(1, 15, 3):
            d.line([1, y, 14, y], fill=slat)
            d.line([1, y + 1, 14, y + 1], fill=slat_dim)
        d.rectangle([0, 0, 15, 15], outline=METAL_3)
        save(grill, "block/%s.png" % name)

    # --- Warmtegenerator: sokkel, deksel en ribben (donker metaal) ---
    base = img(16, 16, METAL_1)
    d = ImageDraw.Draw(base)
    d.line([0, 0, 15, 0], fill=METAL_3)
    d.line([0, 15, 15, 15], fill=METAL_DARK)
    px(base, [(2, 1), (7, 1), (13, 1)], METAL_3)
    save(base, "block/heat_generator_base.png")

    fin = img(16, 16, METAL_3)
    d = ImageDraw.Draw(fin)
    d.line([0, 0, 0, 15], fill=(100, 110, 124, 255))
    d.line([15, 0, 15, 15], fill=METAL_1)
    save(fin, "block/heat_generator_fin.png")

    # --- Warmtegenerator: koperen pijpen achterop, met ringen ---
    pipe = img(16, 16, copper)
    d = ImageDraw.Draw(pipe)
    d.line([0, 0, 0, 15], fill=copper_hi)
    d.line([15, 0, 15, 15], fill=copper_dark)
    for y in (2, 8, 13):
        d.line([0, y, 15, y], fill=copper_dark)
    save(pipe, "block/heat_generator_pipe.png")

    # --- Warmtegenerator: achterkant van de kast (een luik) ---
    back = img(16, 16, METAL_2)
    d = ImageDraw.Draw(back)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.rectangle([5, 3, 10, 11], fill=METAL_1, outline=METAL_3)
    px(back, [(9, 7)], METAL_DARK)
    save(back, "block/heat_generator_back.png")

    # --- Warmtegenerator: bovenkant (deksel) en het luchtrooster erop ---
    gtop = img(16, 16, METAL_1)
    d = ImageDraw.Draw(gtop)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    px(gtop, [(1, 1), (14, 1), (1, 14), (14, 14)], METAL_3)
    save(gtop, "block/heat_generator_top.png")

    vent = img(16, 16, METAL_DARK)
    d = ImageDraw.Draw(vent)
    for y in range(1, 15, 2):
        d.line([1, y, 14, y], fill=(60, 30, 20, 255))
    d.rectangle([0, 0, 15, 15], outline=METAL_3)
    save(vent, "block/heat_generator_vent.png")

    # --- Het scherm dat beide apparaten delen ---
    # Alleen de achtergrond en je eigen inventaris: de vakjes en balken van het apparaat zelf
    # tekent ChargerScreen, want het zonnepaneel heeft er minder dan de warmtegenerator.
    gui = img(256, 256, CLEAR)
    d = ImageDraw.Draw(gui)
    d.rectangle([0, 0, 175, 165], fill=(28, 34, 40, 255), outline=(12, 16, 20, 255))
    # Een dun lijntje tussen het apparaat (boven) en je inventaris (onder).
    d.line([7, 80, 168, 80], fill=(44, 54, 64, 255))
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

    # ---------------------------------------------------------------- De 3D-stukken
    # Het harnas heeft een eigen model (client/suit/StarkSuitModel.java) met extra doosjes bovenop
    # het gewone harnas. Die hebben ook een plekje in het plaatje nodig, dus het plaatje wordt
    # 128x64: links boven het oude harnas, de rest voor de extra stukken. De getallen (u, v) en
    # maten (breedte, hoogte, diepte) moeten precies kloppen met StarkSuitModel.
    def part(im, u, v, w, h, d, side, top=None, bottom=None, front=None):
        f = faces(u, v, w, h, d)
        for n in ("right", "left", "back", "front"):
            fill(im, f[n], side)
        fill(im, f["top"], top or side)
        fill(im, f["bottom"], bottom or side)
        if front:
            fill(im, f["front"], front)
        return f

    big1 = img(128, 64)
    big1.paste(layer1, (0, 0))
    # reactor (3x3x1): donkere ring met een felle kern
    f = part(big1, 64, 0, 3, 3, 1, seam)
    x, y, w, h = f["front"]
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            big1.putpixel((xx, yy), glow if (xx - x, yy - y) == (1, 1) else CYAN)
    big1.putpixel((x, y), seam)
    big1.putpixel((x + 2, y), seam)
    big1.putpixel((x, y + 2), seam)
    big1.putpixel((x + 2, y + 2), seam)
    # borstplaat (3x4x1), links en rechts dezelfde (gespiegeld)
    f = part(big1, 72, 0, 3, 4, 1, red_dark, top=red_hi, front=red_hi)
    shade(big1, f["front"])
    # buikplaat (6x1x1), drie keer onder elkaar
    f = part(big1, 80, 0, 6, 1, 1, gold_dark, top=gold_hi, front=gold)
    x, y, w, h = f["front"]
    big1.putpixel((x + 2, y), gold_dark)
    big1.putpixel((x + 3, y), gold_dark)
    # rugplaat (6x7x1): rood met een naad rondom en een gouden streep
    f = part(big1, 96, 0, 6, 7, 1, red_dark, top=red_hi)
    x, y, w, h = f["back"]
    fill(big1, f["back"], red)
    for xx in range(x, x + w):
        big1.putpixel((xx, y), red_hi)
        big1.putpixel((xx, y + h - 1), seam)
    for yy in range(y + 1, y + h - 1):
        big1.putpixel((x + 2, yy), gold_dark)
        big1.putpixel((x + 3, yy), gold)
    # straalopeningen op de rug (2x2x1): donker met een blauw gloeiend puntje
    f = part(big1, 112, 0, 2, 2, 1, seam)
    x, y, w, h = f["back"]
    big1.putpixel((x, y + 1), CYAN_DIM)
    big1.putpixel((x + 1, y + 1), CYAN_DIM)
    # schouderplaat (7x3x7): rood, bovenop licht, met een gouden rand onderaan
    f = part(big1, 64, 16, 7, 3, 7, red, top=red_hi, bottom=red_dark)
    for n in ("right", "left", "back", "front"):
        fill(big1, f[n], gold, rows=(2, 3))
        fill(big1, f[n], red_hi, rows=(0, 1))
    shade(big1, f["front"])
    shade(big1, f["back"])
    # onderarmbeschermer (7x3x7): donkerrood met een gouden rand bovenaan
    f = part(big1, 92, 16, 7, 3, 7, red_dark, top=gold_hi, bottom=red_dark)
    for n in ("right", "left", "back", "front"):
        fill(big1, f[n], gold, rows=(0, 1))
    shade(big1, f["front"])
    shade(big1, f["back"])
    # repulsor in de handpalm (3x1x3): de onderkant gloeit
    f = part(big1, 64, 28, 3, 1, 3, seam, bottom=CYAN)
    x, y, w, h = f["bottom"]
    big1.putpixel((x + 1, y + 1), glow)
    # laarsschaal (7x4x7): rood met een gouden onderkant
    f = part(big1, 64, 36, 7, 4, 7, red, top=red_hi, bottom=seam)
    for n in ("right", "left", "back", "front"):
        fill(big1, f[n], gold, rows=(2, 4))
        fill(big1, f[n], gold_hi, rows=(2, 3))
    shade(big1, f["front"])
    # neus van de laars (6x3x1): goud
    f = part(big1, 92, 36, 6, 3, 1, gold_dark, top=gold_hi, front=gold)
    shade(big1, f["front"])
    # zool (7x1x7): donker, met een blauwe straalbuis in het midden van de onderkant
    f = part(big1, 64, 48, 7, 1, 7, seam, top=red_dark, bottom=seam)
    x, y, w, h = f["bottom"]
    for dy in range(2, 5):
        for dx in range(2, 5):
            big1.putpixel((x + dx, y + dy), CYAN_DIM)
    big1.putpixel((x + 3, y + 3), glow)
    # raketbuis op de rug (3x5x3), alleen met een raketpakket: rood met een gouden band, en
    # bovenop de donkere gaten met de rode neuzen van de raketten erin
    f = part(big1, 96, 48, 3, 5, 3, red_dark, top=seam, bottom=red_dark)
    for n in ("right", "left", "back", "front"):
        fill(big1, f[n], red, rows=(0, 1))
        fill(big1, f[n], gold, rows=(2, 3))
    shade(big1, f["back"])
    shade(big1, f["front"])
    x, y, w, h = f["top"]
    for dx, dy in ((0, 0), (2, 0), (0, 2), (2, 2)):
        big1.putpixel((x + dx, y + dy), RED)
    big1.putpixel((x + 1, y + 1), gold_hi)
    save(big1, "models/armor/stark_layer_1.png")

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

    # De 3D-stukken van de broek (zie hierboven bij laag 1).
    big2 = img(128, 64)
    big2.paste(layer2, (0, 0))
    # kniestuk (4x3x1): goud
    f = part(big2, 64, 0, 4, 3, 1, gold_dark, top=gold_hi, front=gold)
    x, y, w, h = f["front"]
    big2.putpixel((x + 1, y), gold_hi)
    big2.putpixel((x + 2, y), gold_hi)
    shade(big2, f["front"])
    # dijplaat aan de buitenkant (1x4x4): donkerrood met een lichte rand
    f = part(big2, 76, 0, 1, 4, 4, red_dark, top=red_hi)
    for n in ("right", "left"):
        shade(big2, f[n])
    # gesp van de riem (3x2x1): goud met een blauw lampje
    f = part(big2, 88, 0, 3, 2, 1, gold_dark, top=gold_hi, front=gold)
    x, y, w, h = f["front"]
    big2.putpixel((x + 1, y), CYAN)
    save(big2, "models/armor/stark_layer_2.png")

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


HYDRA_RED = (205, 32, 32, 255)
HYDRA_RED_DIM = (120, 20, 20, 255)
HYDRA_BLACK = (24, 25, 28, 255)
HYDRA_GREY = (48, 50, 55, 255)


def hydra_circuit():
    """Het Hydra-onderdeel: een zwart printplaatje met rode sporen en het rode embleem."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    d.rectangle([2, 3, 13, 12], fill=HYDRA_BLACK, outline=(12, 12, 14, 255))
    # Pootjes onder en boven.
    for x in range(4, 12, 2):
        px(im, [(x, 2), (x, 13)], GOLD_DARK)
    # Rode sporen.
    d.line([3, 5, 6, 5], fill=HYDRA_RED_DIM)
    d.line([9, 10, 12, 10], fill=HYDRA_RED_DIM)
    d.line([3, 10, 5, 8], fill=HYDRA_RED_DIM)
    d.line([12, 5, 10, 7], fill=HYDRA_RED_DIM)
    # Het embleem in het midden: een schedel met tentakels, heel klein.
    d.rectangle([7, 6, 8, 7], fill=HYDRA_RED)
    px(im, [(6, 8), (9, 8), (7, 9), (8, 9), (6, 9), (9, 9)], HYDRA_RED)
    px(im, [(7, 6)], (255, 110, 100, 255))
    save(im, "item/hydra_circuit.png")


def hydra_rifle():
    """Het lasergeweer van de Hydra-soldaten: schuin (zoals een zwaard), met een rode energiecel."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # Kolf linksonder, loop rechtsboven.
    d.line([2, 14, 13, 3], fill=HYDRA_GREY, width=2)
    d.line([3, 14, 13, 4], fill=HYDRA_BLACK)
    d.rectangle([1, 12, 4, 14], fill=HYDRA_BLACK)
    # De loop: dunner en lichter aan het eind, met een rood mondje.
    d.line([10, 5, 14, 1], fill=METAL_3)
    px(im, [(14, 1)], HYDRA_RED)
    # Energiecel en richtkijker.
    d.rectangle([6, 8, 7, 9], fill=HYDRA_RED)
    px(im, [(6, 8)], (255, 120, 110, 255))
    d.line([8, 5, 10, 3], fill=METAL_2)
    # Handgreep.
    d.line([5, 11, 5, 13], fill=HYDRA_BLACK)
    save(im, "item/hydra_rifle.png")


def mini_rocket():
    """
    Een mini-raket (32 x 16): zilver buisje met een rode band, rode neus en donkere vinnen.
    Het -Z-vlak van het buisje ("front" in box_regions) is de staart: daar gloeit hij oranje.
    """
    im = img(32, 16)
    silver = (200, 204, 210, 255)
    silver_dark = (140, 146, 154, 255)
    body = box_regions(0, 0, 2, 2, 8)
    for name in ("top", "bottom", "east", "west", "back"):
        fill_region(im, body[name], silver)
    # Een rode band en een gouden streepje halverwege; "top" en "bottom" lopen van boven
    # naar beneden, de zijkanten van links naar rechts.
    for name in ("top", "bottom"):
        x, y, w, h = body[name]
        for xx in range(x, x + w):
            px(im, [(xx, y + 5), (xx, y + 6)], RED)
            px(im, [(xx, y + 3)], GOLD)
    for name in ("east", "west"):
        x, y, w, h = body[name]
        for yy in range(y, y + h):
            px(im, [(x + 1, yy), (x + 2, yy)], RED)
            px(im, [(x + 4, yy)], GOLD)
        px(im, [(x, y + h - 1), (x + w - 1, y + h - 1)], silver_dark)
    fill_region(im, body["front"], (255, 150, 40, 255))  # de staart gloeit
    fins_v = box_regions(20, 0, 1, 6, 2)
    fins_h = box_regions(0, 10, 6, 1, 2)
    for fins in (fins_v, fins_h):
        for region in fins.values():
            fill_region(im, region, METAL_2)
    nose = box_regions(26, 0, 1, 1, 2)
    for region in nose.values():
        fill_region(im, region, RED)
    save(im, "entity/mini_rocket.png")


def orbital_missile():
    """
    De raket van de satelliet (64 x 64): een lange witte romp met een rode band achter de neus,
    gouden streepjes, zwarte vinnen met rode punten, een donkere straalpijp en een vlam.
    Het -Z-vlak ("front" in box_regions) is de staart, net als bij de mini-raket.
    """
    im = img(64, 64)
    white = (232, 236, 240, 255)
    white_dark = (178, 186, 196, 255)
    body = box_regions(0, 0, 4, 4, 24)
    for name in ("top", "bottom", "east", "west", "back"):
        fill_region(im, body[name], white)
    # Langs de romp (24 lang): "top" en "bottom" lopen van boven (staart) naar beneden (neus),
    # de zijkanten van links (staart) naar rechts (neus).
    for name in ("top", "bottom"):
        x, y, w, h = body[name]
        for xx in range(x, x + w):
            for yy in (y + 18, y + 19, y + 20):
                px(im, [(xx, yy)], RED)
            px(im, [(xx, y + 6), (xx, y + 12)], GOLD)
        px(im, [(x, yy) for yy in range(y, y + h)], white_dark)
    for name in ("east", "west"):
        x, y, w, h = body[name]
        for yy in range(y, y + h):
            for xx in (x + 18, x + 19, x + 20):
                px(im, [(xx, yy)], RED)
            px(im, [(x + 6, yy), (x + 12, yy)], GOLD)
        px(im, [(xx, y + h - 1) for xx in range(x, x + w)], white_dark)
    fill_region(im, body["front"], METAL_DARK)
    for name, (u, v, w, h, d) in {"nose": (0, 28, 3, 3, 3), "tip": (12, 28, 1, 1, 2)}.items():
        for region in box_regions(u, v, w, h, d).values():
            fill_region(im, region, RED)
    for u, v, w, h, d in ((20, 28, 1, 10, 5), (32, 28, 10, 1, 5)):
        regions = box_regions(u, v, w, h, d)
        for region in regions.values():
            fill_region(im, region, METAL_1)
        # De buitenste pixels van elke vin rood.
        for name in ("east", "west"):
            x, y, rw, rh = regions[name]
            if rh >= 10:
                px(im, [(xx, y) for xx in range(x, x + rw)] + [(xx, y + rh - 1) for xx in range(x, x + rw)], RED)
        for name in ("top", "bottom"):
            x, y, rw, rh = regions[name]
            if rw >= 10:
                px(im, [(x, yy) for yy in range(y, y + rh)] + [(x + rw - 1, yy) for yy in range(y, y + rh)], RED)
    for region in box_regions(0, 44, 3, 3, 2).values():
        fill_region(im, region, METAL_2)
    # De vlam: geel-wit bij de straalpijp, naar oranje aan het eind.
    flame = box_regions(12, 44, 3, 3, 8)
    for name, (x, y, w, h) in flame.items():
        for xx in range(x, x + w):
            for yy in range(y, y + h):
                along = (xx - x) / max(1, w - 1) if name in ("east", "west") else (yy - y) / max(1, h - 1)
                if name in ("front", "back"):
                    along = 0.0 if name == "back" else 1.0
                r = 255
                g = int(240 - 150 * along)
                b = int(170 - 150 * along)
                im.putpixel((xx, yy), (r, g, b, 255))
    save(im, "entity/orbital_missile.png")


def rocket_pack():
    """Het raketpakket: twee rode lanceerbuizen naast elkaar, met raketneuzen erin."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    red = (168, 28, 30, 255)
    red_dark = (106, 16, 20, 255)
    for x0 in (2, 9):
        d.rectangle([x0, 4, x0 + 4, 13], fill=red, outline=red_dark)
        d.line([x0 + 1, 10, x0 + 3, 10], fill=GOLD)
        # Twee gaten bovenin met de rode neuzen van de raketten.
        px(im, [(x0 + 1, 5), (x0 + 3, 5)], METAL_DARK)
        px(im, [(x0 + 1, 3), (x0 + 3, 3)], (220, 60, 50, 255))
        px(im, [(x0 + 1, 4), (x0 + 3, 4)], (200, 204, 210, 255))
    # Een gouden beugel die de twee buizen verbindt.
    d.line([6, 8, 9, 8], fill=GOLD_DARK)
    d.line([6, 12, 9, 12], fill=GOLD_DARK)
    save(im, "item/rocket_pack.png")


def turret_textures():
    """
    De torentjes: Stark (grijs metaal, gouden streep, blauwe lens) en Hydra (zwart, rode streep,
    rode lens). Per soort een voet (zijkant en bovenkant, 16x16, voor het blokmodel) en een kop
    (64x32, voor TurretRenderer; de loop wijst naar +Z, dat is het "back"-vlak in box_regions).
    """
    looks = {
        "stark": dict(body=(150, 158, 168, 255), dark=(84, 90, 100, 255), light=(196, 202, 210, 255),
                      stripe=GOLD, lens=CYAN, lens_dim=CYAN_DIM),
        "hydra": dict(body=HYDRA_BLACK, dark=(14, 14, 16, 255), light=HYDRA_GREY,
                      stripe=HYDRA_RED_DIM, lens=HYDRA_RED, lens_dim=HYDRA_RED_DIM),
        # De beveiliging van de Stark Tower: Stark-wit met een rode streep en een rood oog.
        "security": dict(body=(196, 200, 208, 255), dark=(110, 116, 126, 255), light=(232, 235, 240, 255),
                         stripe=(170, 30, 32, 255), lens=(255, 60, 50, 255), lens_dim=(150, 30, 25, 255)),
    }
    for name, c in looks.items():
        # Voet, zijkant: metaal met een streep en boutjes; de onderste rij donker.
        side = img(16, 16, c["body"])
        d = ImageDraw.Draw(side)
        d.rectangle([0, 0, 15, 15], outline=c["dark"])
        d.line([0, 9, 15, 9], fill=c["stripe"])
        d.line([0, 10, 15, 10], fill=c["stripe"])
        for x in (2, 13):
            px(side, [(x, 3), (x, 13)], c["light"])
        d.line([4, 1, 11, 1], fill=c["light"])
        save(side, f"block/{name}_turret_side.png")
        # Voet, bovenkant: een ring waar de draaischijf op staat.
        top = img(16, 16, c["body"])
        d = ImageDraw.Draw(top)
        d.rectangle([0, 0, 15, 15], outline=c["dark"])
        d.ellipse([3, 3, 12, 12], outline=c["dark"])
        d.ellipse([5, 5, 10, 10], fill=c["light"])
        save(top, f"block/{name}_turret_top.png")

        # De kop (64 x 32).
        head = img(64, 32)
        swivel = box_regions(0, 0, 6, 1, 6)
        gun = box_regions(0, 7, 8, 6, 8)
        barrel = box_regions(32, 0, 2, 2, 7)
        muzzle = box_regions(50, 0, 3, 3, 1)
        pod = box_regions(32, 9, 3, 4, 5)
        for region in swivel.values():
            fill_region(head, region, c["dark"])
        for region in gun.values():
            fill_region(head, region, c["body"])
        # Een streep rondom de kop, lichte bovenkant, donkere naad onderaan.
        for face in ("east", "front", "west", "back"):
            x, y, w, h = gun[face]
            for xx in range(x, x + w):
                px(head, [(xx, y + 3)], c["stripe"])
                px(head, [(xx, y)], c["light"])
                px(head, [(xx, y + h - 1)], c["dark"])
        x, y, w, h = gun["top"]
        fill_region(head, (x + 1, y + 1, w - 2, h - 2), c["light"])
        for yy in range(y, y + h):
            px(head, [(x + 3, yy), (x + 4, yy)], c["stripe"])
        # De "ogen" boven de loop (voorkant = +Z = "back").
        x, y, w, h = gun["back"]
        px(head, [(x + 1, y + 1), (x + 2, y + 1), (x + 5, y + 1), (x + 6, y + 1)], c["lens"])
        px(head, [(x + 1, y + 2), (x + 6, y + 2)], c["lens_dim"])
        for region in barrel.values():
            fill_region(head, region, c["dark"])
        for face in ("top", "east", "west"):
            x, y, w, h = barrel[face]
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    if (xx + yy) % 3 == 0:
                        px(head, [(xx, yy)], c["body"])
        for region in muzzle.values():
            fill_region(head, region, METAL_DARK)
        x, y, w, h = muzzle["back"]
        px(head, [(x + 1, y + 1)], c["lens"])
        for region in pod.values():
            fill_region(head, region, c["body"])
        for face in ("east", "west", "top", "bottom"):
            x, y, w, h = pod[face]
            px(head, [(x, y), (x + w - 1, y + h - 1)], c["dark"])
        x, y, w, h = pod["back"]
        fill_region(head, pod["back"], c["dark"])
        px(head, [(x, y + 1), (x + 2, y + 1), (x + 1, y + 3)], RED)
        save(head, f"entity/{name}_turret.png")

    barrel_tex = img(16, 16, METAL_DARK)
    d = ImageDraw.Draw(barrel_tex)
    d.rectangle([0, 0, 15, 15], outline=METAL_1)
    save(barrel_tex, "block/turret_barrel.png")


def security_textures():
    """
    De beveiliging van de Stark Tower: de kern (een serverkast met een statuslampje, rood als de
    toren van niemand is, groen als hij van jou is) en de beveiligingsdrone (Stark-wit met een
    rode streep, en rode alarmlampjes).
    """
    rack = (34, 36, 42, 255)
    rack_dark = (20, 21, 25, 255)
    rack_light = (70, 74, 84, 255)
    for name, status in (("security_core_front", (255, 50, 40, 255)), ("security_core_front_claimed", (60, 230, 90, 255))):
        im = img(16, 16, rack)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=rack_light)
        # Rijen servers met knipperlichtjes.
        for row, y in enumerate(range(2, 12, 3)):
            d.rectangle([2, y, 13, y + 1], fill=rack_dark)
            for x in range(3, 13, 3):
                px(im, [(x, y)], CYAN_DIM if (x + row) % 2 else (40, 160, 60, 255))
        # Het grote statuslampje onderaan, met het Stark-rood eromheen.
        d.rectangle([5, 12, 10, 14], fill=(90, 20, 20, 255))
        d.rectangle([6, 12, 9, 13], fill=status)
        save(im, f"block/{name}.png")
    side = img(16, 16, rack)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=rack_light)
    for y in range(2, 15, 2):
        d.line([2, y, 13, y], fill=rack_dark)
    save(side, "block/security_core_side.png")
    top = img(16, 16, rack_dark)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=rack_light)
    d.rectangle([4, 4, 11, 11], outline=(170, 30, 32, 255))
    save(top, "block/security_core_top.png")

    # De drone: het model van de Stark-drone, maar wit met een rode streep.
    body = box_regions(0, 0, 6, 3, 6)
    camera = box_regions(0, 10, 2, 2, 2)
    arm_a = box_regions(0, 16, 12, 1, 1)
    arm_b = box_regions(0, 19, 12, 1, 1)
    blade_x = box_regions(28, 0, 6, 1, 1)
    blade_z = box_regions(28, 3, 1, 1, 6)
    hub = box_regions(28, 11, 2, 2, 2)
    white = (214, 218, 224, 255)
    grey = (150, 156, 166, 255)
    red = (170, 30, 32, 255)
    drone = img(64, 32)
    for region in body.values():
        fill_region(drone, region, white)
    fill_region(drone, body["bottom"], grey)
    for name in ("east", "front", "west", "back"):
        x, y, w, h = body[name]
        for i in range(w):
            drone.putpixel((x + i, y + 1), red)
            drone.putpixel((x + i, y + h - 1), grey)
    x, y, w, h = body["top"]
    for i in range(w):
        drone.putpixel((x + i, y + 2), red)
    for region in camera.values():
        fill_region(drone, region, METAL_DARK)
    for arm in (arm_a, arm_b):
        for region in arm.values():
            fill_region(drone, region, grey)
    for blade in (blade_x, blade_z):
        for region in blade.values():
            fill_region(drone, region, (175, 185, 195, 255))
    for region in hub.values():
        fill_region(drone, region, METAL_3)
    save(drone, "entity/drone/security_drone.png")

    alarm = img(64, 32)
    bright = (255, 55, 45, 255)
    dim = (160, 25, 20, 255)
    fill_region(alarm, camera["front"], bright)
    x, y, w, h = body["top"]
    px(alarm, [(x + 2, y + 3), (x + 3, y + 3), (x + 2, y + 4), (x + 3, y + 4)], bright)
    for side_name in ("front", "back"):
        x, y, w, h = body[side_name]
        for i in range(1, w - 1):
            alarm.putpixel((x + i, y + 1), dim)
    save(alarm, "entity/drone/security_drone_lights_alarm.png")


def stark_laptop():
    """De Stark-laptop: open, met een blauw scherm vol code en een rood lampje."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # Het scherm (achterkant van het deksel en het beeld).
    d.rectangle([2, 2, 13, 9], fill=METAL_DARK, outline=METAL_3)
    d.rectangle([3, 3, 12, 8], fill=(8, 30, 40, 255))
    for y, length in ((4, 6), (5, 4), (6, 7), (7, 3)):
        d.line([4, y, 4 + length, y], fill=CYAN_DIM)
    px(im, [(11, 4)], (255, 60, 50, 255))
    # Het toetsenbord, schuin eronder.
    d.polygon([(1, 11), (14, 11), (15, 14), (0, 14)], fill=METAL_2, outline=METAL_1)
    for x in range(2, 14, 2):
        px(im, [(x, 12), (x + 1, 13)], METAL_3)
    save(im, "item/stark_laptop.png")


def workbench_textures():
    """
    De harnas-werkbank (Tony's lab) en de drie upgrades die je er inbouwt.

    - bovenkant: een blauwdruk-mat met raster, en de omtrek van een borstplaat met reactor;
    - zijkant: grijze laden met handgrepen;
    - voorkant: een gereedschapswand met een rode Stark-streep;
    - upgrades: een straalbuis met een blauwe vlam, een rood-gouden pantserplaat en een
      gloeiende condensator.
    """
    red = (168, 28, 30, 255)
    red_dark = (106, 16, 20, 255)
    gold = (224, 178, 62, 255)
    steel = (92, 98, 108, 255)
    steel_dark = (58, 62, 70, 255)
    steel_light = (140, 146, 156, 255)
    blueprint = (18, 52, 88, 255)
    grid = (40, 90, 140, 255)

    top = img(16, 16, blueprint)
    d = ImageDraw.Draw(top)
    for i in range(0, 16, 4):
        d.line([i, 0, i, 15], fill=grid)
        d.line([0, i, 15, i], fill=grid)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    # De omtrek van een borstplaat, met in het midden de reactor.
    d.polygon([(4, 3), (11, 3), (12, 6), (11, 12), (4, 12), (3, 6)], outline=(200, 230, 255, 255))
    px(top, [(7, 7), (8, 7), (7, 8), (8, 8)], CYAN)
    save(top, "block/suit_workbench_top.png")

    side = img(16, 16, steel)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    for y0 in (2, 7, 12):
        d.rectangle([2, y0, 13, y0 + 3], fill=steel_dark, outline=steel_light)
        d.line([6, y0 + 1, 9, y0 + 1], fill=steel_light)
    save(side, "block/suit_workbench_side.png")

    front = img(16, 16, steel_dark)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=steel)
    d.line([0, 2, 15, 2], fill=red)
    d.line([0, 3, 15, 3], fill=gold)
    # Gereedschap aan de wand: een sleutel, een schroevendraaier, een hamer.
    d.line([3, 6, 3, 12], fill=steel_light)
    px(front, [(2, 6), (4, 6)], steel_light)
    d.line([7, 6, 7, 11], fill=steel_light)
    px(front, [(7, 12)], red)
    d.line([11, 7, 11, 12], fill=(120, 80, 40, 255))
    d.rectangle([9, 5, 13, 6], fill=steel_light)
    save(front, "block/suit_workbench_front.png")

    thrusters = img(16, 16)
    d = ImageDraw.Draw(thrusters)
    d.polygon([(5, 2), (10, 2), (12, 8), (3, 8)], fill=steel, outline=steel_dark)
    d.line([4, 5, 11, 5], fill=gold)
    d.polygon([(4, 9), (11, 9), (9, 14), (6, 14)], fill=CYAN_DIM)
    d.polygon([(6, 9), (9, 9), (8, 13), (7, 13)], fill=CYAN)
    save(thrusters, "item/thruster_upgrade.png")

    plating = img(16, 16)
    d = ImageDraw.Draw(plating)
    d.polygon([(3, 2), (12, 2), (13, 8), (8, 14), (2, 8)], fill=red, outline=red_dark)
    d.line([4, 5, 11, 5], fill=gold)
    d.line([4, 6, 11, 6], fill=(246, 214, 120, 255))
    px(plating, [(4, 3), (11, 3), (4, 9), (11, 9)], steel_light)
    save(plating, "item/armor_plating.png")

    capacitor = img(16, 16)
    d = ImageDraw.Draw(capacitor)
    d.ellipse([2, 2, 13, 13], fill=steel_dark, outline=gold)
    d.ellipse([4, 4, 11, 11], fill=CYAN_DIM)
    d.ellipse([6, 6, 9, 9], fill=CYAN)
    px(capacitor, [(7, 7)], (235, 250, 255, 255))
    save(capacitor, "item/repulsor_capacitor.png")

    # Raketkop: een rode neuskegel met een gouden band en een geel-oranje ontploffingsteken.
    warhead = img(16, 16)
    d = ImageDraw.Draw(warhead)
    d.polygon([(8, 1), (12, 7), (12, 14), (4, 14), (4, 7)], fill=red, outline=red_dark)
    d.line([4, 10, 12, 10], fill=gold)
    d.line([4, 11, 12, 11], fill=(246, 214, 120, 255))
    px(warhead, [(7, 5), (8, 5), (7, 6), (8, 6), (6, 7), (9, 7)], (255, 200, 60, 255))
    px(warhead, [(8, 3), (8, 4)], (255, 236, 150, 255))
    save(warhead, "item/rocket_warhead.png")

    # Magazijn: een stalen houder met vier kleine raketten met rode neuzen.
    magazine = img(16, 16)
    d = ImageDraw.Draw(magazine)
    d.rectangle([2, 6, 13, 14], fill=steel_dark, outline=steel)
    d.line([2, 11, 13, 11], fill=gold)
    for x0 in (3, 6, 9, 12):
        d.line([x0, 3, x0, 9], fill=steel_light)
        px(magazine, [(x0, 2), (x0, 3)], red)
    save(magazine, "item/rocket_magazine.png")


def power_cabinet_textures():
    """
    De stroomkast: een grijze stalen kast.
    - voorkant: een deurtje met een geel-zwart hoogspanningsbordje, zes kleine schakelaars in twee
      rijen (groen lampje erboven) en een handgreep;
    - zijkant: staal met een ronde doorvoer in het midden (daar komt een kabel binnen);
    - bovenkant: staal met een ventilatierooster.
    """
    steel = (104, 110, 120, 255)
    steel_dark = (66, 70, 78, 255)
    steel_light = (150, 156, 166, 255)
    yellow = (240, 196, 40, 255)
    black = (24, 24, 28, 255)
    green = (80, 230, 120, 255)

    front = img(16, 16, steel)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    d.rectangle([1, 1, 14, 14], outline=steel_light)
    # Het bordje: een geel driehoekje met een zwarte bliksem.
    d.polygon([(8, 2), (11, 6), (5, 6)], fill=yellow, outline=black)
    px(front, [(8, 3), (7, 4), (8, 4), (8, 5)], black)
    # Zes schakelaars in twee rijen, elk met een lampje erboven.
    for row, y in enumerate((8, 12)):
        for x in (4, 7, 10):
            px(front, [(x, y - 1)], green)
            d.rectangle([x, y, x + 1, y + 1], fill=black)
            px(front, [(x, y)], steel_light)
    # De handgreep.
    d.line([13, 8, 13, 12], fill=steel_dark)
    save(front, "block/power_cabinet_front.png")

    side = img(16, 16, steel)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    for y in (3, 12):
        d.line([2, y, 13, y], fill=steel_light)
    d.ellipse([5, 5, 10, 10], fill=black, outline=steel_dark)
    d.ellipse([6, 6, 9, 9], fill=(40, 44, 50, 255))
    px(side, [(7, 7), (8, 8)], (190, 110, 60, 255))
    save(side, "block/power_cabinet_side.png")

    top = img(16, 16, steel)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    for y in range(4, 12, 2):
        d.line([4, y, 11, y], fill=steel_dark)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(top, [(x, y)], steel_light)
    save(top, "block/power_cabinet_top.png")


def drone_dock_textures():
    """
    De drone-dock voor de hangar:

    - bovenkant: donker staal met gele en zwarte waarschuwingsstrepen langs de rand en blauwe
      lampjes in de hoeken (het midden valt onder de landingsplaat);
    - landingsplaat: een blauwe ring met een kruis, zoals de landingscirkel in de hangar;
    - zijkant: staal, met onderaan dezelfde strepen (het platform is maar 3 pixels hoog).
    """
    steel = (70, 76, 86, 255)
    steel_dark = (44, 48, 56, 255)
    steel_light = (128, 134, 146, 255)
    yellow = (230, 190, 40, 255)
    black = (26, 26, 30, 255)

    top = img(16, 16, steel)
    d = ImageDraw.Draw(top)
    for i in range(16):
        stripe = yellow if (i // 2) % 2 == 0 else black
        for (x, y) in ((i, 0), (i, 1), (i, 14), (i, 15), (0, i), (1, i), (14, i), (15, i)):
            top.putpixel((x, y), stripe)
    d.rectangle([2, 2, 13, 13], outline=steel_dark)
    px(top, [(2, 2), (13, 2), (2, 13), (13, 13)], CYAN)
    save(top, "block/drone_dock_top.png")

    pad = img(16, 16, steel_dark)
    d = ImageDraw.Draw(pad)
    d.ellipse([4, 4, 11, 11], outline=CYAN)
    d.line([7, 5, 7, 10], fill=CYAN_DIM)
    d.line([8, 5, 8, 10], fill=CYAN_DIM)
    d.line([5, 7, 10, 7], fill=CYAN_DIM)
    d.line([5, 8, 10, 8], fill=CYAN_DIM)
    d.rectangle([3, 3, 12, 12], outline=steel_light)
    save(pad, "block/drone_dock_pad.png")

    side = img(16, 16, steel)
    for x in range(16):
        side.putpixel((x, 12), steel_light)
        side.putpixel((x, 13), steel_light)
        side.putpixel((x, 14), yellow if (x // 2) % 2 == 0 else black)
        side.putpixel((x, 15), steel_dark)
    save(side, "block/drone_dock_side.png")


def landing_pad_textures():
    """
    Het midden van een landingsplatform: een gloeiende blauwe ring met een stip in het midden
    op donker staal, en gele hoekjes. De zijkant heeft een blauwe lichtstrip.
    """
    steel = (58, 62, 70, 255)
    steel_dark = (36, 40, 46, 255)
    yellow = (230, 190, 40, 255)

    top = img(16, 16, steel_dark)
    d = ImageDraw.Draw(top)
    d.ellipse([1, 1, 14, 14], outline=CYAN_DIM)
    d.ellipse([2, 2, 13, 13], outline=CYAN)
    d.ellipse([6, 6, 9, 9], fill=CYAN_DIM)
    px(top, [(7, 7), (8, 7), (7, 8), (8, 8)], (235, 250, 255, 255))
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        px(top, [(x, y)], yellow)
    px(top, [(1, 0), (0, 1), (14, 0), (15, 1), (0, 14), (1, 15), (15, 14), (14, 15)], yellow)
    save(top, "block/landing_pad_top.png")

    side = img(16, 16, steel)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    d.line([1, 3, 14, 3], fill=CYAN)
    d.line([1, 4, 14, 4], fill=CYAN_DIM)
    for x in range(1, 15, 4):
        d.line([x, 9, x + 1, 9], fill=yellow)
    save(side, "block/landing_pad_side.png")


def rocket_ammo():
    """Munitie voor het torentje: drie kleine raketten met rode neuzen, samengebonden."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    silver = (200, 204, 210, 255)
    for x0 in (3, 7, 11):
        d.rectangle([x0, 4, x0 + 2, 12], fill=silver, outline=(140, 146, 154, 255))
        px(im, [(x0 + 1, 2), (x0 + 1, 3), (x0, 3), (x0 + 2, 3)], RED)
        px(im, [(x0 - 1, 12), (x0 + 3, 12), (x0 - 1, 13), (x0 + 3, 13)], METAL_2)
        px(im, [(x0 + 1, 13)], (255, 150, 40, 255))
    d.line([2, 8, 14, 8], fill=GOLD_DARK)
    save(im, "item/rocket_ammo.png")


def hydra_soldier():
    """
    Het pak van een Hydra-soldaat (64 x 64, de indeling van mensachtige wezens; de linker arm
    en het linker been gebruiken gespiegeld dezelfde plek als de rechter).

    Een zwart tactisch pak met een grijs vest, een bivakmuts met een rode bril, een helm, het
    rode Hydra-embleem op de borst en op de helm, rode banden om de armen, handschoenen,
    kniestukken en laarzen.
    """
    im = img(64, 64)
    d = ImageDraw.Draw(im)
    suit = HYDRA_BLACK
    dark = (16, 16, 18, 255)

    # Hoofd (0,0; 8x8x8): een bivakmuts, met een rode bril over de ogen.
    d.rectangle([0, 0, 31, 15], fill=(34, 34, 38, 255))
    d.rectangle([8, 8, 15, 15], fill=(34, 34, 38, 255))
    d.rectangle([8, 11, 15, 12], fill=dark)                      # brilband
    d.rectangle([9, 11, 10, 12], fill=HYDRA_RED)                  # glazen
    d.rectangle([13, 11, 14, 12], fill=HYDRA_RED)
    px(im, [(9, 11), (13, 11)], (255, 110, 100, 255))
    d.line([0, 11, 7, 11], fill=dark)                             # band om de zijkant
    d.line([16, 11, 31, 11], fill=dark)
    d.rectangle([10, 14, 13, 15], fill=(24, 24, 27, 255))          # mondstuk

    # Helm (de hoedlaag, 32,0): alleen bovenop en de bovenste rijen van de kanten.
    d.rectangle([40, 0, 55, 7], fill=(40, 42, 38, 255))           # boven en onder
    for x0 in (32, 40, 48, 56):
        d.rectangle([x0, 8, x0 + 7, 10], fill=(40, 42, 38, 255))
    px(im, [(43, 9), (44, 9)], HYDRA_RED)                          # embleempje voorop

    # Lijf (16,16; 8x12x4).
    d.rectangle([16, 16, 39, 31], fill=suit)
    d.rectangle([20, 20, 27, 28], fill=HYDRA_GREY)                  # vest voor
    d.rectangle([32, 20, 39, 28], fill=HYDRA_GREY)                  # vest achter
    d.rectangle([20, 29, 27, 30], fill=(40, 30, 24, 255))           # riem
    px(im, [(23, 29), (24, 29)], METAL_3)                           # gesp
    # Het embleem op de borst: een schedel met tentakels.
    d.rectangle([23, 21, 24, 22], fill=HYDRA_RED)
    px(im, [(22, 22), (25, 22)], HYDRA_RED)
    px(im, [(21, 23), (22, 24), (23, 23), (24, 23), (25, 24), (26, 23), (22, 23), (25, 23)], HYDRA_RED)
    px(im, [(23, 21)], (255, 110, 100, 255))

    # Arm (40,16; 4x12x4): zwarte mouw, rode band, grijze handschoen.
    d.rectangle([40, 16, 55, 31], fill=suit)
    d.line([40, 23, 55, 23], fill=HYDRA_RED)
    d.rectangle([40, 28, 55, 31], fill=HYDRA_GREY)
    d.rectangle([44, 16, 51, 19], fill=HYDRA_GREY)                  # schouderstuk boven

    # Been (0,16; 4x12x4): broek, kniestuk, laars.
    d.rectangle([0, 16, 15, 31], fill=(22, 23, 26, 255))
    d.rectangle([4, 23, 7, 24], fill=HYDRA_GREY)                    # kniestuk voor
    d.rectangle([0, 27, 15, 31], fill=dark)                          # laars
    d.line([0, 31, 15, 31], fill=(58, 50, 44, 255))                  # zool
    save(im, "entity/hydra_soldier.png")


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
    cable_textures()
    dome_field()
    relay_textures()
    shield_textures()
    rayshield_walls()
    charger_textures()
    emp_item()
    stark_drone_item()
    holo_table_textures()
    stark_suit()
    hydra_circuit()
    hydra_rifle()
    hydra_soldier()
    mini_rocket()
    orbital_missile()
    rocket_pack()
    turret_textures()
    rocket_ammo()
    security_textures()
    stark_laptop()
    workbench_textures()
    drone_dock_textures()
    landing_pad_textures()
    power_cabinet_textures()
