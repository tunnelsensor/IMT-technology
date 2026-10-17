"""
Maakt alle textures (plaatjes) van de I.M.T Technology-mod.

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_textures.py

Nodig: Python 3 met Pillow (pip install pillow).
De plaatjes komen in src/main/resources/assets/imttech/textures/.
Wil je een texture liever zelf tekenen (bv. in Blockbench of Paint.NET)? Teken dan over
het gegenereerde bestand heen en draai dit script daarna niet meer voor dat bestand.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "imttech" / "textures"

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
# Hoeveel verschillende plaatjes elke rayshield-kleur krijgt (zie rayshield_walls). Zelfde
# getal als in tools/generate_block_models.py.
RAYSHIELD_VARIANTS = 4


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

def imt_glasses_item():
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
    # Klein blauw lichtje (I.M.T staat aan)
    px(im, [(13, 5)], CYAN)
    save(im, "item/imt_glasses.png")


def computer_chip():
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
    save(im, "item/computer_chip.png")


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

def imt_equipment():
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
    save(im, "models/armor/imt_layer_1.png")


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
    save(base, "entity/drone/drone.png")

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
        save(lights, f"entity/drone/drone_lights_{name}.png")

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
    De Bibie's Industries-satelliet in het arsenaal- en satellietscherm (128 x 64): een romp met gouden
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


# --------------------------------------------------------------------- Zones (hoofdstuk 10)

def zone_textures():
    """
    De rand van een zone: een fijn raster (zone_grid) en een zachte schuine glans die er langzaam
    overheen schuift (zone_sheen), zoals de rand van de wereld in Minecraft. Wit op zwart, net als de
    koepel: de mod telt het licht op bij wat erachter zit (zwart = onzichtbaar) en geeft de kleur
    (blauw, of rood als de zone geen stroom heeft). Allebei herhalen ze naadloos.

    Het raster is één blok groot: de lijn ligt op de rand van het plaatje, dus naast elkaar vormen
    ze een lijn op elke blokrand.
    """
    import math
    size = 32
    grid = img(size, size, (0, 0, 0, 255))
    for y in range(size):
        for x in range(size):
            dx = min(x, size - x)  # afstand tot de lijn op x = 0 (en die van het volgende plaatje)
            dy = min(y, size - y)
            d = min(dx, dy)
            line = max(0.0, 1 - d / 1.2)          # de dunne felle lijn
            glow = max(0.0, 1 - d / 5.0) * 0.22   # een zachte gloed ernaast
            fill = 0.05                           # het vlak zelf, heel zwak
            v = min(1.0, line + glow + fill)
            c = int(255 * v)
            grid.putpixel((x, y), (c, c, c, 255))
    save(grid, "misc/zone_grid.png")

    size = 64
    sheen = img(size, size, (0, 0, 0, 255))
    for y in range(size):
        for x in range(size):
            t = (x + y) / size                    # schuin: x + y herhaalt elke 64 pixels
            band = max(0.0, math.cos(2 * math.pi * t)) ** 6
            c = int(255 * 0.7 * band)
            sheen.putpixel((x, y), (c, c, c, 255))
    save(sheen, "misc/zone_sheen.png")


def zone_marker_item():
    """
    De Zone Marker: een metalen staafje met een greep, schuin in de hand, en bovenaan een blauw
    lampje waarmee je de hoeken van je zone "aanwijst". Twee kleine oranje haakjes ernaast zijn de
    hoeken van de doos die je ermee tekent.
    """
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    # De staaf: van linksonder naar rechtsboven, twee pixels dik.
    for i in range(9):
        x, y = 3 + i, 13 - i
        px(im, [(x, y)], METAL_3)
        px(im, [(x + 1, y)], METAL_1)
    # De greep onderaan (donker, met een gouden ringetje).
    for i in range(3):
        x, y = 2 + i, 14 - i
        px(im, [(x, y), (x + 1, y)], FRAME)
    px(im, [(5, 11), (6, 11)], GOLD)
    # Het lampje bovenaan.
    d.rectangle([11, 2, 13, 4], fill=CYAN)
    px(im, [(12, 1), (14, 3), (12, 5), (10, 3)], CYAN_DIM)
    px(im, [(12, 3)], (220, 250, 255, 255))
    # De oranje hoekjes van de doos.
    orange = (255, 169, 63, 255)
    px(im, [(1, 2), (2, 2), (1, 3)], orange)
    px(im, [(8, 9), (9, 9), (9, 8)], orange)
    save(im, "item/zone_marker.png")


def c4_textures():
    """
    C4 (hoofdstuk 10): een pakketje kneedbare springstof, licht grijsbeige zoals klei, met twee banden
    zwarte tape; erop een klein zwart kastje met een rood schermpje, en een rood en een blauw draadje.
    Ook de plaatjes van het item (C4 met timer) staan hier.
    """
    import random
    r = random.Random(4)
    clay = (196, 190, 170, 255)
    clay_dark = (172, 166, 146, 255)
    clay_light = (212, 207, 190, 255)
    tape = (38, 38, 42, 255)
    tape_hi = (60, 60, 66, 255)
    wire_red = (200, 40, 36, 255)
    wire_blue = (50, 90, 210, 255)

    putty = img(16, 16, clay)
    for _ in range(40):
        px(putty, [(r.randrange(16), r.randrange(16))], clay_dark if r.random() < 0.6 else clay_light)
    for y in (4, 5, 11, 12):
        for x in range(16):
            px(putty, [(x, y)], tape_hi if y in (4, 11) else tape)
    for x in range(1, 15):
        px(putty, [(x, 8)], wire_red)
    for x in range(3, 13):
        px(putty, [(x, 9)], wire_blue)
    save(putty, "block/c4_putty.png")

    device = img(16, 16, (34, 36, 40, 255))
    d = ImageDraw.Draw(device)
    d.rectangle([0, 0, 15, 15], outline=(58, 60, 66, 255))
    px(device, [(2, 2), (13, 2), (2, 13), (13, 13)], (110, 112, 118, 255))
    save(device, "block/c4_device.png")

    screen = img(16, 16, (34, 36, 40, 255))
    d = ImageDraw.Draw(screen)
    d.rectangle([2, 3, 13, 10], fill=(24, 8, 8, 255), outline=(58, 60, 66, 255))
    # Gedoofde cijfers ("88:88"), het echte aftellen tekent de mod er zelf voor.
    for x0 in (3, 6, 9, 12):
        d.line([x0, 5, x0 + 1, 5], fill=(70, 18, 16, 255))
        d.line([x0, 8, x0 + 1, 8], fill=(70, 18, 16, 255))
    px(screen, [(12, 12)], (230, 40, 30, 255))  # het lampje
    px(screen, [(3, 12), (4, 12), (6, 12)], (110, 112, 118, 255))  # knopjes
    save(screen, "block/c4_screen.png")

    item = img(16, 16)
    d = ImageDraw.Draw(item)
    d.rectangle([1, 6, 14, 14], fill=clay, outline=clay_dark)
    for x in range(1, 15):
        px(item, [(x, 8), (x, 12)], tape)
    d.rectangle([4, 3, 11, 8], fill=(34, 36, 40, 255), outline=(58, 60, 66, 255))
    d.rectangle([5, 4, 10, 6], fill=(30, 8, 8, 255))
    px(item, [(6, 5), (7, 5), (9, 5)], (255, 60, 40, 255))
    d.line([11, 5, 13, 3], fill=wire_red)
    d.line([4, 7, 2, 4], fill=wire_blue)
    save(item, "item/timed_c4.png")


def remote_c4_items():
    """
    De remote C4 (hetzelfde pakketje, met een antenne en een groen lampje in plaats van cijfers) en
    de detonator: een zwart kastje met een rode knop onder een klepje en een antenne.
    """
    clay = (196, 190, 170, 255)
    clay_dark = (172, 166, 146, 255)
    tape = (38, 38, 42, 255)
    metal = (34, 36, 40, 255)
    metal_edge = (58, 60, 66, 255)
    red = (220, 40, 34, 255)

    item = img(16, 16)
    d = ImageDraw.Draw(item)
    d.rectangle([1, 7, 14, 14], fill=clay, outline=clay_dark)
    for x in range(1, 15):
        px(item, [(x, 9), (x, 12)], tape)
    d.rectangle([3, 4, 9, 8], fill=metal, outline=metal_edge)
    px(item, [(5, 6), (7, 6)], (230, 40, 30, 255))
    d.line([10, 6, 13, 1], fill=(150, 152, 158, 255))
    px(item, [(13, 1)], red)
    save(item, "item/remote_c4.png")

    det = img(16, 16)
    d = ImageDraw.Draw(det)
    d.rectangle([4, 5, 11, 15], fill=metal, outline=metal_edge)
    d.rectangle([5, 7, 10, 11], fill=(60, 20, 18, 255), outline=(90, 92, 98, 255))
    d.ellipse([6, 8, 9, 10], fill=red)
    px(det, [(7, 8)], (255, 120, 100, 255))
    d.line([10, 5, 13, 0], fill=(150, 152, 158, 255))
    px(det, [(6, 13), (9, 13)], (110, 112, 118, 255))
    px(det, [(5, 13)], (90, 220, 120, 255))
    save(det, "item/detonator.png")


def repair_paste_item():
    """
    Repair Paste (hoofdstuk 11): een open blikje met een bergje grijsbeige plamuurpasta, en een
    plamuurmes met een houten steel dat erin steekt.
    """
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    tin = (150, 156, 166, 255)
    tin_dark = (92, 98, 108, 255)
    tin_light = (200, 206, 214, 255)
    paste = (204, 198, 184, 255)
    paste_dark = (170, 164, 150, 255)
    paste_light = (232, 228, 216, 255)
    # Het blikje, met een licht randje links en een etiket met een blauwe streep (de I.M.T-kleur).
    d.rectangle([2, 9, 12, 15], fill=tin, outline=tin_dark)
    d.line([3, 10, 3, 14], fill=tin_light)
    d.rectangle([5, 11, 10, 13], fill=(228, 230, 236, 255))
    d.line([5, 12, 10, 12], fill=CYAN_DIM)
    # De pasta: een bergje dat boven de rand uitkomt.
    d.ellipse([1, 5, 13, 11], fill=paste, outline=paste_dark)
    d.ellipse([4, 3, 10, 8], fill=paste, outline=paste_dark)
    d.line([5, 7, 9, 7], fill=paste)
    d.line([2, 9, 12, 9], fill=paste_dark)
    px(im, [(5, 4), (6, 4), (4, 5), (3, 7), (4, 7)], paste_light)
    # Het plamuurmes: het blad in de pasta, de steel schuin naar rechtsboven.
    d.polygon([(7, 8), (10, 5), (11, 6), (8, 9)], fill=(178, 184, 194, 255), outline=(120, 126, 136, 255))
    d.line([11, 5, 14, 2], fill=(140, 86, 48, 255))
    d.line([12, 5, 15, 2], fill=(98, 58, 32, 255))
    px(im, [(14, 1)], (140, 86, 48, 255))
    save(im, "item/repair_paste.png")


def crusher_textures():
    """
    De Crusher (hoofdstuk 11): een metalen kast met twee maalwielen achter een venster in de
    voorkant, een trechter bovenop en ventilatiesleuven opzij (de zij- en bovenkant deelt hij straks
    met de Compactor). Werkt hij, dan staan de tanden van de wielen anders (ze draaien), ligt er stof
    onderin het venster en brandt het lampje blauw.
    """
    side = img(16, 16, METAL_2)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    px(side, [(2, 2), (13, 2), (2, 13), (13, 13)], METAL_3)
    for y in (5, 8, 11):
        d.line([4, y, 11, y], fill=METAL_DARK)
        d.line([4, y + 1, 11, y + 1], fill=METAL_3)
    d.line([1, 14, 14, 14], fill=CYAN_DIM)
    save(side, "block/crack_machine_side.png")

    top = img(16, 16, METAL_1)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    px(top, [(1, 1), (14, 1), (1, 14), (14, 14)], METAL_3)
    d.rectangle([3, 3, 12, 12], fill=METAL_3)
    d.rectangle([4, 4, 11, 11], fill=METAL_DARK)
    d.rectangle([6, 6, 9, 9], fill=(16, 18, 22, 255))
    save(top, "block/crack_machine_top.png")

    rim = (136, 140, 148, 255)
    face = (98, 102, 110, 255)
    hub = (58, 62, 68, 255)
    window = (24, 26, 30, 255)
    dust = (196, 192, 182, 255)
    for name, on in (("crusher_front", False), ("crusher_front_on", True)):
        im = img(16, 16, METAL_2)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
        d.rectangle([2, 2, 13, 10], fill=window, outline=METAL_3)
        # Twee wielen van 5 bij 5 die in elkaar grijpen (x 3-7 en 8-12, y 4-8).
        for x0 in (3, 8):
            d.rectangle([x0 + 1, 4, x0 + 3, 8], fill=rim)
            d.rectangle([x0, 5, x0 + 4, 7], fill=rim)
            d.rectangle([x0 + 1, 5, x0 + 3, 7], fill=face)
            px(im, [(x0 + 2, 6)], hub)
            # De tanden: stil recht boven en onder, draaiend schuin.
            teeth = [(x0 + 1, 3), (x0 + 3, 9)] if on else [(x0 + 2, 3), (x0 + 2, 9)]
            px(im, teeth, rim)
        if on:
            px(im, [(4, 9), (6, 9), (9, 9), (11, 9), (7, 3)], dust)
        # Onderaan een waarschuwingsstreep en het lampje.
        for x in range(1, 15):
            px(im, [(x, 13), (x, 14)], (220, 170, 40, 255) if (x // 2) % 2 == 0 else METAL_DARK)
        d.rectangle([11, 11, 13, 11], fill=CYAN if on else (90, 30, 26, 255))
        save(im, "block/%s.png" % name)


def compactor_textures():
    """
    De Compactor (hoofdstuk 11): dezelfde kast als de Crusher (zij- en bovenkant), met in het venster
    een hydraulische pers: een zuigerstang met een zware kop, en daaronder een mal met poeder. Werkt
    hij, dan staat de kop lager (hij perst), is het poeder een blok geworden en brandt het lampje blauw.
    """
    rod = (176, 182, 192, 255)
    head = (136, 140, 148, 255)
    head_dark = (92, 96, 104, 255)
    window = (24, 26, 30, 255)
    mould = (70, 74, 82, 255)
    powder = (196, 192, 182, 255)
    pressed = (150, 146, 138, 255)
    for name, on in (("compactor_front", False), ("compactor_front_on", True)):
        im = img(16, 16, METAL_2)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
        d.rectangle([2, 2, 13, 10], fill=window, outline=METAL_3)
        # De mal onderin het venster.
        d.line([4, 9, 11, 9], fill=mould)
        px(im, [(4, 8), (11, 8)], mould)
        low = 6 if on else 4
        # De stang en de kop van de pers.
        d.line([7, 3, 7, low - 1], fill=rod)
        d.line([8, 3, 8, low - 1], fill=head_dark)
        d.rectangle([4, low, 11, low + 1], fill=head)
        d.line([4, low + 1, 11, low + 1], fill=head_dark)
        if on:
            # Geperst: een plat, stevig blokje onder de kop.
            d.line([5, 8, 10, 8], fill=pressed)
        else:
            # Los poeder: een hoopje.
            d.line([5, 8, 10, 8], fill=powder)
            px(im, [(6, 7), (7, 7), (8, 7), (9, 7)], powder)
        for x in range(1, 15):
            px(im, [(x, 13), (x, 14)], (220, 170, 40, 255) if (x // 2) % 2 == 0 else METAL_DARK)
        d.rectangle([11, 11, 13, 11], fill=CYAN if on else (90, 30, 26, 255))
        save(im, "block/%s.png" % name)


def arc_lamp_textures():
    """
    De Arc Lamp (9 oktober): een stalen kast met aan vier kanten een groot glas met een reflectorrooster.
    Uit is het glas donker; aan is het wit in het midden en lichtblauw naar de randen (zoals een echte
    booglamp: blauwig wit). Boven- en onderkant: een koelplaat met ribben en gouden hoekjes.
    """
    for name, on in (("arc_lamp_side", False), ("arc_lamp_side_on", True)):
        im = img(16, 16, METAL_2)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
        px(im, [(1, 1), (14, 1), (1, 14), (14, 14)], METAL_3)  # boutjes
        d.rectangle([2, 2, 13, 13], fill=(40, 46, 56, 255), outline=(120, 200, 240, 255) if on else METAL_3)
        for y in range(3, 13):
            for x in range(3, 13):
                grid = (x % 3 == 0) or (y % 3 == 0)
                if on:
                    dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                    if dist <= 1.6:
                        color = (255, 255, 255, 255)
                    elif dist <= 3.0:
                        color = (236, 248, 255, 255)
                    elif dist <= 4.4:
                        color = (205, 238, 255, 255)
                    else:
                        color = (170, 222, 255, 255)
                    if grid and dist > 1.6:
                        color = (222, 244, 255, 255)
                else:
                    color = (40, 52, 68, 255) if grid else (26, 33, 44, 255)
                im.putpixel((x, y), color)
        if not on:
            # De lamp zelf achter het glas: een grijs buisje met een glinstering.
            d.rectangle([6, 6, 9, 9], fill=(62, 68, 80, 255))
            px(im, [(6, 6), (7, 6)], (110, 120, 136, 255))
        save(im, "block/%s.png" % name)

    top = img(16, 16, METAL_1)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    for x in range(3, 13):
        d.line([x, 3, x, 12], fill=METAL_DARK if x % 2 else METAL_3)
    d.rectangle([6, 6, 9, 9], fill=METAL_2, outline=METAL_DARK)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(top, [(x, y)], GOLD)
    save(top, "block/arc_lamp_top.png")


def light_switch_textures():
    """
    De lichtschakelaar (9 oktober): een licht stalen plaatje met een wipschakelaar erop, en een lampje
    erboven. Aan: de wip is licht met een blauw streepje ("I") en het lampje brandt blauw. Uit: de wip is
    donker met een rood stipje ("O") en het lampje is donkerrood.
    De plaat zit in het model op x 5-10, y 3-12 van het plaatje en de wip op x 6-9, y 5-10; de rest van
    het plaatje zie je alleen op de smalle randjes.
    """
    plate = img(16, 16, (96, 102, 112, 255))
    d = ImageDraw.Draw(plate)
    d.rectangle([5, 3, 10, 12], fill=(178, 184, 194, 255), outline=(122, 128, 138, 255))
    px(plate, [(5, 3), (10, 3), (5, 12), (10, 12)], GOLD)
    save(plate, "block/light_switch_plate.png")

    for name, on in (("light_switch_rocker_on", True), ("light_switch_rocker_off", False)):
        base = (198, 204, 212, 255) if on else (118, 124, 134, 255)
        im = img(16, 16, base)
        d = ImageDraw.Draw(im)
        d.rectangle([6, 5, 9, 10], fill=base, outline=(150, 156, 166, 255) if on else (84, 90, 100, 255))
        if on:
            d.line([7, 6, 8, 6], fill=(40, 190, 240, 255))  # "I"
        else:
            px(im, [(7, 9), (8, 9)], (200, 40, 40, 255))  # "O"
        save(im, "block/%s.png" % name)

    save(img(16, 16, (90, 230, 255, 255)), "block/light_switch_led_on.png")
    save(img(16, 16, (110, 24, 24, 255)), "block/light_switch_led_off.png")


def block_powder_item():
    """
    Poeder (hoofdstuk 11): een hoopje fijn poeder met wat losse korrels. Lichtgrijs, want het spel
    kleurt het in met de kleur van het blok waar het van komt (zie FromBlockItem.color).
    """
    import random
    r = random.Random(11)
    light = (240, 240, 240, 255)
    mid = (204, 204, 204, 255)
    dark = (160, 160, 160, 255)
    edge = (112, 112, 112, 255)
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    d.ellipse([1, 6, 14, 22], fill=mid, outline=edge)
    d.rectangle([0, 15, 15, 15], fill=CLEAR)
    d.line([2, 14, 13, 14], fill=edge)
    # Licht van links boven, schaduw rechts.
    for (x, y) in [(5, 8), (6, 8), (4, 9), (5, 9), (3, 11), (4, 10)]:
        px(im, [(x, y)], light)
    for (x, y) in [(11, 9), (12, 10), (12, 11), (11, 12), (12, 12), (10, 13), (11, 13)]:
        px(im, [(x, y)], dark)
    for _ in range(10):
        x, y = r.randrange(3, 13), r.randrange(9, 14)
        if im.getpixel((x, y))[3]:
            px(im, [(x, y)], dark if r.random() < 0.5 else light)
    # Losse korrels ernaast.
    px(im, [(1, 13), (14, 12), (13, 13), (2, 12)], dark)
    save(im, "item/block_powder.png")


# --------------------------------------------------------------------- Stroomnet

def cable_textures():
    """
    De stroomkabel. Het verbindingsstukje ('cable') is donker rubber met koperen randjes en een
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


def relay_inverted_textures():
    """
    Een omgekeerd relais (13 oktober: de knop "invert"): dezelfde bovenkant, met een rode rand om het
    lampje en een rood streepje erboven. Zo teken je in een schakelschema ook "niet" (een streep erboven).
    """
    def top(lamp, glow):
        im = img(16, 16, METAL_2)
        dd = ImageDraw.Draw(im)
        dd.rectangle([0, 0, 15, 15], outline=METAL_3)
        dd.rectangle([2, 2, 13, 13], outline=METAL_DARK)
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            px(im, [(x, y)], METAL_3)
        dd.ellipse([5, 5, 10, 10], fill=(220, 50, 45, 255))
        dd.ellipse([6, 6, 9, 9], fill=lamp, outline=glow)
        dd.line([5, 3, 10, 3], fill=(220, 50, 45, 255))
        return im

    save(top((40, 60, 55, 255), (70, 90, 85, 255)), "block/relay_top_inverted.png")
    save(top((92, 255, 157, 255), (200, 255, 225, 255)), "block/relay_top_on_inverted.png")


def relay_emitter_textures():
    """
    De relay emitter (13 oktober): een plat kastje met een antennetje. Krijgt hij redstone, dan
    gloeit zijn lampje rood en schakelt hij jouw relais.
    """
    # Zijkant: zoals het relais, maar met een cyaan streepje (hij zendt, het relais ontvangt)
    side = img(16, 16, METAL_1)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 0], fill=METAL_3)
    d.rectangle([0, 15, 15, 15], fill=METAL_DARK)
    d.line([1, 9, 14, 9], fill=METAL_DARK)
    d.line([2, 12, 6, 12], fill=CYAN_DIM)
    d.line([9, 12, 13, 12], fill=(170, 40, 40, 255))
    for x in (1, 14):
        px(side, [(x, 3), (x, 6)], METAL_3)
    save(side, "block/relay_emitter_side.png")

    # Bovenkant: vier schroeven, een lampje (redstone erin = rood) en golfjes (hij zendt)
    def top(lamp, glow, wave):
        im = img(16, 16, METAL_2)
        dd = ImageDraw.Draw(im)
        dd.rectangle([0, 0, 15, 15], outline=METAL_3)
        dd.rectangle([2, 2, 13, 13], outline=METAL_DARK)
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            px(im, [(x, y)], METAL_3)
        dd.arc([4, 4, 11, 11], 200, 340, fill=wave)
        dd.arc([2, 2, 13, 13], 210, 330, fill=wave)
        dd.rectangle([6, 9, 9, 12], fill=lamp, outline=glow)
        return im

    save(top((70, 24, 22, 255), (100, 40, 36, 255), CYAN_DIM), "block/relay_emitter_top.png")
    save(top((235, 50, 40, 255), (255, 140, 120, 255), CYAN), "block/relay_emitter_top_on.png")

    # Het antennetje: dun metaal met een cyaan puntje bovenaan
    antenna = img(16, 16, METAL_3)
    da = ImageDraw.Draw(antenna)
    da.rectangle([0, 0, 15, 3], fill=CYAN)
    da.line([0, 4, 15, 4], fill=CYAN_DIM)
    da.line([0, 15, 15, 15], fill=METAL_DARK)
    save(antenna, "block/relay_emitter_antenna.png")


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
    Het energieveld van de rayshield-laserpoort, in elke kleur.

    Op jouw vraag (28 september): geen lijnen over het hele veld, maar een zachte, wolkerige
    ondergrond in de eigen kleur (redelijk subtiel, een beetje lichter en donkerder), met af en
    toe op een willekeurige plek een klein kronkeltje: een kort, fel lijntje dat zich uitrolt,
    even kronkelt en weer uitdooft.

    - De ondergrond is ruis: willekeurige getallen op een grof en een fijn rooster, zacht in
      elkaar overgevloeid. De grove vlekken drijven rustig naar rechts, de fijne korrel naar
      boven. Het plaatje herhaalt naadloos (links = rechts, boven = onder), dus naast elkaar
      vormen de blokken één doorlopend veld zonder naden.
    - Kronkeltjes: alle blokken spelen dezelfde animatie tegelijk af. Met één plaatje zou dus
      elk blok van de wand op hetzelfde moment op dezelfde plek een kronkeltje krijgen. Daarom
      zijn er per kleur RAYSHIELD_VARIANTS plaatjes met dezelfde ondergrond maar elk hun eigen
      kronkeltjes, op andere plekken en andere momenten. De blockstate laat Minecraft per blok
      een variant kiezen (zie laser_wall_models() in tools/generate_block_models.py).

    Elke texture is 32 breed en 32*FRAMES hoog: Minecraft speelt die frames onder elkaar af als
    animatie (zie het .mcmeta-bestand ernaast, frametime 1 = elke tick een nieuw frame, 20 per
    seconde). Let op: "interpolate" doet niets bij frametime 1 (Minecraft mengt alleen tussen
    frames die langer dan één tick duren), dus de vloeiendheid moet uit de frames zelf komen.
    Daarom zijn het er 120 (6 seconden) en beweegt alles maar een fractie van een pixel per
    frame. Na 120 frames staat alles weer precies op het begin, zodat de lus niet hapert.
    """
    import json
    import math
    import random
    size = 32
    frames = 120

    # --- De ondergrond ---
    def lattice(cells, seed):
        rng = random.Random(seed)
        return [[rng.uniform(-1, 1) for _ in range(cells)] for _ in range(cells)]

    def noise(grid, x, y):
        """Waarde-ruis: de rooster-getallen rond (x, y), zacht gemengd. Herhaalt om de 32 pixels."""
        cells = len(grid)
        gx, gy = x * cells / size, y * cells / size
        x0, y0 = math.floor(gx), math.floor(gy)
        fx, fy = gx - x0, gy - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)  # zachte overgang, geen knikken

        def at(i, j):
            return grid[j % cells][i % cells]
        upper = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * fx
        lower = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * fx
        return upper + (lower - upper) * fy

    coarse = lattice(4, 11)  # vlekken van ongeveer 8 pixels
    fine = lattice(8, 23)    # korrel van ongeveer 4 pixels
    ground = []  # per frame: 32x32 getallen van ongeveer -1 tot 1
    for frame in range(frames):
        shift = frame / frames * size  # in één lus precies één plaatje verder
        ground.append([[0.7 * noise(coarse, x + 0.5 - shift, y + 0.5)
                        + 0.3 * noise(fine, x + 0.5, y + 0.5 + shift)
                        for x in range(size)] for y in range(size)])

    # --- De kronkeltjes ---
    # Per variant 2 kronkeltjes per lus. De momenten worden over alle varianten verdeeld, zodat
    # er over de hele wand gezien steeds ergens eentje kan opduiken en nooit alles tegelijk.
    per_variant = 2
    rng = random.Random(7)
    slots = list(range(RAYSHIELD_VARIANTS * per_variant))
    rng.shuffle(slots)
    gap = frames / len(slots)
    events = [[] for _ in range(RAYSHIELD_VARIANTS)]
    for i, slot in enumerate(slots):
        events[i % RAYSHIELD_VARIANTS].append({
            "start": int(slot * gap + rng.uniform(0, gap * 0.6)),
            "life": rng.randint(16, 24),            # hoe lang hij zichtbaar is (frames)
            "x": rng.uniform(10, 22), "y": rng.uniform(10, 22),  # midden, ver genoeg van de rand
            "angle": rng.uniform(0, math.pi),       # welke kant hij op ligt
            "length": rng.uniform(7, 11),
            "amp": rng.uniform(0.9, 1.5),           # hoe ver hij uitslaat
            "wave": rng.uniform(4.0, 5.5),          # afstand tussen twee bochtjes
            "phase": rng.uniform(0, 2 * math.pi),
        })

    def squiggle(event, frame):
        """Hoe fel het kronkeltje is per pixel in dit frame: {(x, y): 0 tot 1}."""
        age = (frame - event["start"]) % frames  # ook als hij over het einde van de lus loopt
        if age >= event["life"]:
            return {}
        p = age / event["life"]
        grow = min(1.0, p / 0.3)                     # eerst rolt hij zich uit...
        fade = 1.0 if p < 0.5 else math.cos((p - 0.5) / 0.5 * math.pi / 2)  # ...en dooft dan uit
        phase = event["phase"] + p * 3 * math.pi     # terwijl hij kronkelt
        length = event["length"]
        dx, dy = math.cos(event["angle"]), math.sin(event["angle"])
        points = []
        s = 0.0
        while s <= grow * length:
            bend = event["amp"] * math.sin(2 * math.pi * s / event["wave"] + phase) * math.sin(math.pi * s / length)
            u = s - length / 2
            points.append((event["x"] + u * dx - bend * dy, event["y"] + u * dy + bend * dx))
            s += 0.25
        result = {}
        for y in range(size):
            for x in range(size):
                d = min((math.hypot(x + 0.5 - px_, y + 0.5 - py_) for px_, py_ in points), default=99)
                if d > 3:
                    continue
                core = max(0.0, 1 - d / 0.9)
                glow = 0.3 * math.exp(-(d * d) / (2 * 1.1 * 1.1))
                result[(x, y)] = max(core, glow) * fade
        return result

    mcmeta = json.dumps({"animation": {"frametime": 1, "interpolate": True}}, indent=2) + "\n"
    for variant in range(RAYSHIELD_VARIANTS):
        spark = []
        for frame in range(frames):
            lit = {}
            for event in events[variant]:
                for key, value in squiggle(event, frame).items():
                    lit[key] = max(lit.get(key, 0.0), value)
            spark.append(lit)

        for name, (r, g, b) in RAYSHIELD_COLORS.items():
            def tint(amount, alpha):
                return (max(0, min(255, r + round(amount))), max(0, min(255, g + round(amount))),
                        max(0, min(255, b + round(amount))), max(0, min(255, round(alpha))))
            sheet = img(size, size * frames)
            for frame in range(frames):
                top = frame * size
                for y in range(size):
                    for x in range(size):
                        n = ground[frame][y][x]
                        s = spark[frame].get((x, y), 0.0)
                        sheet.putpixel((x, top + y), tint(6 + 26 * n + 70 * s, 52 + 32 * n + 130 * s))
            file = "laser_wall_%s" % name + ("_%d" % (variant + 1) if variant else "")
            save(sheet, "block/%s.png" % file)
            (ROOT / "block" / ("%s.png.mcmeta" % file)).write_text(mcmeta, encoding="utf-8", newline="\n")


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


def tech_suit():
    """Het tech-harnas: rood en goud, met een blauwe reactor op de borst.

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
    # Het harnas heeft een eigen model (client/suit/TechSuitModel.java) met extra doosjes bovenop
    # het gewone harnas. Die hebben ook een plekje in het plaatje nodig, dus het plaatje wordt
    # 128x64: links boven het oude harnas, de rest voor de extra stukken. De getallen (u, v) en
    # maten (breedte, hoogte, diepte) moeten precies kloppen met TechSuitModel.
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
    save(big1, "models/armor/tech_layer_1.png")

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
    save(big2, "models/armor/tech_layer_2.png")

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
    save(chest, "item/tech_chestplate.png")

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
    save(legs, "item/tech_leggings.png")

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
    save(boots, "item/tech_boots.png")


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


def drone_item():
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
    save(im, "item/drone.png")


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
    De torentjes: Raket (grijs metaal, gouden streep, blauwe lens) en Hydra (zwart, rode streep,
    rode lens). Per soort een voet (zijkant en bovenkant, 16x16, voor het blokmodel) en een kop
    (64x32, voor TurretRenderer; de loop wijst naar +Z, dat is het "back"-vlak in box_regions).
    """
    looks = {
        "rocket": dict(body=(150, 158, 168, 255), dark=(84, 90, 100, 255), light=(196, 202, 210, 255),
                      stripe=GOLD, lens=CYAN, lens_dim=CYAN_DIM),
        "hydra": dict(body=HYDRA_BLACK, dark=(14, 14, 16, 255), light=HYDRA_GREY,
                      stripe=HYDRA_RED_DIM, lens=HYDRA_RED, lens_dim=HYDRA_RED_DIM),
        # De beveiliging van de Bibie's Industries Tower: I.M.T-wit met een rode streep en een rood oog.
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
    De beveiliging van de Bibie's Industries Tower: de kern (een serverkast met een statuslampje, rood als de
    toren van niemand is, groen als hij van jou is) en de beveiligingsdrone (I.M.T-wit met een
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
        # Het grote statuslampje onderaan, met het I.M.T-rood eromheen.
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

    # De drone: het model van de drone, maar wit met een rode streep.
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


def security_camera_textures():
    """
    De bewakingscamera: een wit huisje met een rode streep (zoals de beveiligingsdrone), een
    donker montageplaatje met boutjes, en de lens: donker glas met een ring en een lampje dat rood
    brandt als de camera aan staat (security_camera_lens_on).
    """
    white = (214, 218, 224, 255)
    shade = (176, 182, 192, 255)
    edge = (130, 136, 146, 255)
    red = (170, 30, 32, 255)
    body = img(16, 16, white)
    d = ImageDraw.Draw(body)
    d.rectangle([0, 0, 15, 15], outline=edge)
    d.rectangle([1, 11, 14, 14], fill=shade)
    d.line([1, 4, 14, 4], fill=red)
    d.line([1, 5, 14, 5], fill=red)
    px(body, [(3, 8), (6, 8), (9, 8), (12, 8)], edge)  # koelgleufjes
    save(body, "block/security_camera_body.png")

    mount = img(16, 16, METAL_2)
    d = ImageDraw.Draw(mount)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.rectangle([2, 2, 13, 13], outline=METAL_3)
    px(mount, [(3, 3), (12, 3), (3, 12), (12, 12)], (150, 156, 166, 255))  # boutjes
    save(mount, "block/security_camera_mount.png")

    for name, led in (("security_camera_lens", (90, 20, 20, 255)), ("security_camera_lens_on", (255, 50, 40, 255))):
        lens = img(16, 16, METAL_DARK)
        d = ImageDraw.Draw(lens)
        d.ellipse([2, 2, 13, 13], fill=(70, 76, 86, 255))
        d.ellipse([4, 4, 11, 11], fill=(16, 22, 34, 255))
        d.ellipse([6, 6, 9, 9], fill=(28, 46, 78, 255))
        px(lens, [(6, 6), (7, 6)], (120, 160, 220, 255))  # glinstering
        d.rectangle([12, 1, 14, 3], fill=led)  # het lampje
        save(lens, f"block/{name}.png")


def hacking_laptop():
    """De hacklaptop: open, met een blauw scherm vol code en een rood lampje."""
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
    save(im, "item/hacking_laptop.png")


def hacking_laptop_block():
    """
    De hacklaptop als blok (opengeklapt op een beveiligingskern): de kast, het toetsenbord en
    het scherm met de terminal (groene tekst op zwart, een kopregel en een knipperend blokje).
    """
    case = img(16, 16, METAL_1)
    d = ImageDraw.Draw(case)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.line([1, 1, 14, 1], fill=METAL_3)
    px(case, [(13, 13)], (255, 60, 50, 255))          # een rood lampje
    save(case, "block/hacking_laptop_case.png")

    keys = img(16, 16, METAL_2)
    d = ImageDraw.Draw(keys)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    for row in range(4):                               # vier rijen toetsen
        for col in range(7):
            x = 1 + col * 2
            y = 3 + row * 2
            px(keys, [(x, y)], METAL_3)
    d.rectangle([5, 12, 10, 14], fill=METAL_1, outline=METAL_3)   # het touchpad
    save(keys, "block/hacking_laptop_keyboard.png")

    green = (70, 240, 130, 255)
    green_dim = (30, 120, 70, 255)
    screen = img(16, 16, (6, 16, 10, 255))
    d = ImageDraw.Draw(screen)
    d.rectangle([0, 0, 15, 15], outline=METAL_DARK)
    d.line([2, 2, 13, 2], fill=CYAN)                   # de kopregel
    for y, length, colour in ((4, 9, green), (6, 6, green_dim), (8, 10, green), (10, 4, green_dim)):
        d.line([2, y, 2 + length, y], fill=colour)
    d.rectangle([2, 12, 3, 13], fill=green)            # de cursor
    save(screen, "block/hacking_laptop_screen.png")


def workbench_textures():
    """
    De harnas-werkbank (Bibie's lab) en de drie upgrades die je er inbouwt.

    - bovenkant: een blauwdruk-mat met raster, en de omtrek van een borstplaat met reactor;
    - zijkant: grijze laden met handgrepen;
    - voorkant: een gereedschapswand met een rode I.M.T-streep;
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


def suit_hologram():
    """
    Het harnas als hologram (boven de harnas-werkbank): dezelfde plaatjes als het harnas, maar
    in grijstinten, want het spel kleurt ze zelf blauw. Donkere stukken blijven een beetje
    zichtbaar (anders valt het hologram uit elkaar), en om de rij is het iets donkerder: de
    lijntjes van een hologram. Maak eerst het harnas (tech_suit), want deze leest dat plaatje.
    """
    for n in (1, 2):
        src = Image.open(ROOT / f"models/armor/tech_layer_{n}.png").convert("RGBA")
        out = img(src.width, src.height)
        for y in range(src.height):
            for x in range(src.width):
                r, g, b, a = src.getpixel((x, y))
                if a == 0:
                    continue
                light = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
                v = 0.62 + 0.38 * light
                if y % 2 == 1:
                    v *= 0.84
                c = int(255 * min(1.0, v))
                out.putpixel((x, y), (c, c, c, a))
        save(out, f"entity/suit_hologram_{n}.png")


def server_rack_textures():
    """
    Het serverrack (7 oktober): een zwarte kast met zes "bays" in twee kolommen van drie (de vakjes
    voor CPU, GPU, RAM, SSD en fans; tot 8 oktober waren het er vier onder elkaar).
    - voorkant uit: de bays met donkere lampjes;
    - voorkant aan: hetzelfde, maar de lampjes knipperen (vier plaatjes onder elkaar, met een
      .mcmeta-bestand erbij: zo maakt Minecraft er een filmpje van);
    - zijkant: geperforeerd staal (gaatjes voor de lucht);
    - boven/onder: een ventilatierooster.
    """
    import random
    shell = (30, 32, 38, 255)
    shell_dark = (18, 19, 23, 255)
    shell_light = (70, 74, 84, 255)
    bay = (40, 44, 52, 255)
    green = (80, 235, 120, 255)
    amber = (250, 180, 50, 255)
    off = (52, 60, 66, 255)

    def front(lights):
        im = img(16, 16, shell)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=shell_dark)
        px(im, [(1, y) for y in range(1, 15)] + [(14, y) for y in range(1, 15)], shell_light)
        # Twee kolommen van drie bays (zelfde volgorde als de vakjes in het spel: eerst de linker
        # kolom van boven naar beneden, dan de rechter).
        for i in range(6):
            x = 2 if i < 3 else 8
            y = 2 + (i % 3) * 4
            d.rectangle([x, y, x + 5, y + 2], fill=bay)
            d.line([x + 1, y + 1, x + 2, y + 1], fill=shell_dark)  # de greep van de bay
            for j, lx in enumerate((x + 3, x + 4)):
                px(im, [(lx, y + 1)], lights[i][j])
        # Onderaan een cyaan statusstreepje (het I.M.T-randje).
        d.line([3, 14, 12, 14], fill=CYAN_DIM if lights[0][0] != off else CYAN_DARK)
        return im

    save(front([(off, off)] * 6), "block/server_rack_front.png")
    # Aan: vier plaatjes met telkens andere lampjes (vast patroon, elke keer hetzelfde).
    pattern = random.Random(7)
    frames = img(16, 64)
    for frame in range(4):
        lights = [(green if pattern.random() < 0.7 else off, amber if pattern.random() < 0.35 else green)
                  for _ in range(6)]
        frames.paste(front(lights), (0, frame * 16))
    save(frames, "block/server_rack_front_on.png")
    (ROOT / "block/server_rack_front_on.png.mcmeta").write_text('{\n  "animation": {\n    "frametime": 4\n  }\n}\n')

    side = img(16, 16, shell)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=shell_dark)
    for y in range(2, 14, 2):
        for x in range(2 + (y // 2) % 2, 14, 2):
            px(side, [(x, y)], shell_dark)
    save(side, "block/server_rack_side.png")

    top = img(16, 16, shell)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=shell_dark)
    for y in range(3, 13, 2):
        d.line([3, y, 12, y], fill=shell_dark)
    px(top, [(1, 1), (14, 1), (1, 14), (14, 14)], shell_light)
    save(top, "block/server_rack_top.png")


def server_part_items():
    """De onderdelen voor een serverrack: CPU, GPU, RAM-latje, SSD en fan."""
    # CPU: een vierkante chip met gouden pootjes rondom en een zilveren deksel.
    cpu = img(16, 16)
    d = ImageDraw.Draw(cpu)
    for i in range(3, 13, 2):
        px(cpu, [(i, 1), (i, 14), (1, i), (14, i)], GOLD)
    d.rectangle([2, 2, 13, 13], fill=(30, 90, 50, 255))
    d.rectangle([4, 4, 11, 11], fill=(170, 176, 186, 255), outline=(120, 126, 136, 255))
    px(cpu, [(5, 5), (6, 5), (5, 6)], (225, 230, 236, 255))
    d.rectangle([7, 7, 8, 8], fill=CYAN)
    save(cpu, "item/server_cpu.png")

    # RAM: een groen latje met zwarte geheugenchips en een gouden rij contacten.
    ram = img(16, 16)
    d = ImageDraw.Draw(ram)
    d.rectangle([1, 4, 14, 11], fill=(30, 110, 60, 255), outline=(20, 70, 40, 255))
    for x in (2, 6, 10):
        d.rectangle([x, 5, x + 2, 8], fill=(24, 24, 28, 255))
    for x in range(2, 14, 2):
        px(ram, [(x, 10)], GOLD)
    px(ram, [(7, 11)], CLEAR)  # het inkepinkje
    px(ram, [(13, 5)], CYAN)
    save(ram, "item/server_ram.png")

    # SSD: een plat, donker doosje met een etiket en een blauw lampje.
    ssd = img(16, 16)
    d = ImageDraw.Draw(ssd)
    d.rectangle([2, 3, 13, 12], fill=METAL_DARK, outline=(14, 16, 20, 255))
    d.rectangle([4, 5, 11, 8], fill=(200, 205, 212, 255))
    d.line([5, 6, 9, 6], fill=(90, 96, 104, 255))
    d.line([5, 7, 7, 7], fill=(90, 96, 104, 255))
    px(ssd, [(12, 11)], CYAN)
    d.line([3, 12, 12, 12], fill=GOLD_DARK)
    save(ssd, "item/server_ssd.png")

    # GPU (8 oktober): een lange zwarte videokaart met twee ronde fans en gouden contacten.
    gpu = img(16, 16)
    d = ImageDraw.Draw(gpu)
    d.rectangle([0, 4, 15, 11], fill=(26, 28, 34, 255), outline=(12, 13, 16, 255))
    for cx in (4, 11):
        d.ellipse([cx - 3, 5, cx + 3, 10], fill=(46, 50, 58, 255), outline=(80, 86, 96, 255))
        px(gpu, [(cx, 7), (cx, 8), (cx - 1, 7), (cx + 1, 8)], (120, 126, 136, 255))
    d.line([0, 4, 15, 4], fill=(70, 230, 120, 255))  # een groen randje (het merk)
    for x in range(2, 14, 2):
        px(gpu, [(x, 12)], GOLD)
    px(gpu, [(14, 6)], CYAN)
    save(gpu, "item/server_gpu.png")

    # Fan (8 oktober): een vierkant frame met een ronde fan en vier schuine bladen.
    fan = img(16, 16)
    d = ImageDraw.Draw(fan)
    d.rectangle([1, 1, 14, 14], fill=(36, 40, 46, 255), outline=(16, 18, 22, 255))
    d.ellipse([2, 2, 13, 13], fill=(20, 22, 26, 255), outline=(90, 96, 106, 255))
    blade = (150, 156, 166, 255)
    px(fan, [(7, 3), (8, 3), (8, 4), (8, 5), (9, 5)], blade)       # boven
    px(fan, [(12, 7), (12, 8), (11, 8), (10, 8), (10, 9)], blade)  # rechts
    px(fan, [(8, 12), (7, 12), (7, 11), (7, 10), (6, 10)], blade)  # onder
    px(fan, [(3, 8), (3, 7), (4, 7), (5, 7), (5, 6)], blade)       # links
    d.rectangle([7, 7, 8, 8], fill=CYAN)
    px(fan, [(2, 2), (13, 2), (2, 13), (13, 13)], (110, 116, 126, 255))  # de schroefjes
    save(fan, "item/server_fan.png")


# De kleuren van een keycard per niveau (zelfde als KeycardsApp.levelColour in de mod).
KEYCARD_LEVELS = {
    1: (232, 238, 242, 255),
    2: (91, 227, 125, 255),
    3: (79, 168, 255, 255),
    4: (194, 123, 255, 255),
    5: (255, 201, 60, 255),
}
# Het lampje van een keycardlezer of codepaneel (zie InputLight in de mod).
INPUT_LEDS = {
    "off": (40, 46, 52, 255),
    "busy": (255, 170, 40, 255),
    "ok": (80, 240, 120, 255),
    "denied": (255, 60, 50, 255),
}


def keycard_items():
    """
    De keycard: een liggend kaartje met een gouden chip en een magneetstrip. Leeg is hij grijs;
    beschreven krijgt hij bovenaan een band in de kleur van zijn niveau (keycard_1 t/m keycard_5).
    """
    def card(band):
        im = img(16, 16)
        d = ImageDraw.Draw(im)
        d.rectangle([1, 4, 14, 12], fill=(206, 210, 216, 255), outline=(120, 126, 136, 255))
        px(im, [(1, 4), (14, 4), (1, 12), (14, 12)], CLEAR)  # ronde hoekjes
        if band:
            d.rectangle([2, 5, 13, 6], fill=band)
        d.rectangle([3, 8, 5, 10], fill=GOLD, outline=GOLD_DARK)
        d.line([7, 11, 13, 11], fill=(40, 42, 48, 255))   # magneetstrip
        px(im, [(12, 8)], CYAN)                              # het I.M.T-lampje
        return im

    save(card(None), "item/keycard.png")
    for level, colour in KEYCARD_LEVELS.items():
        save(card(colour), f"item/keycard_{level}.png")


def keycard_reader_textures():
    """
    De keycardlezer: een donker kastje van 6 bij 10 pixels (op de plek waar het in het blok zit:
    x 5-10, y 3-12) met een schermpje, een lampje en een gleuf voor de kaart. Vier voorkanten, één
    per kleur van het lampje.
    """
    body = (40, 43, 50, 255)
    edge = (22, 23, 27, 255)
    side = img(16, 16, body)
    ImageDraw.Draw(side).rectangle([0, 0, 15, 15], outline=edge)
    save(side, "block/keycard_reader_side.png")
    for name, led in INPUT_LEDS.items():
        im = img(16, 16, body)
        d = ImageDraw.Draw(im)
        d.rectangle([5, 3, 10, 12], fill=body, outline=edge)
        d.rectangle([6, 4, 9, 5], fill=CYAN_DARK)
        px(im, [(6, 4)], CYAN)
        d.line([7, 7, 8, 7], fill=led)
        d.line([6, 10, 9, 10], fill=(10, 10, 12, 255))       # de gleuf
        d.line([6, 11, 9, 11], fill=(66, 70, 80, 255))
        save(im, f"block/keycard_reader_{name}.png")


def code_panel_textures():
    """
    Het codepaneel: een donker kastje van 8 bij 12 pixels (x 4-11, y 2-13) met een schermpje, een
    lampje en twaalf toetsjes (3 bij 4; rechtsonder de groene OK, linksonder de rode C).
    """
    body = (40, 43, 50, 255)
    edge = (22, 23, 27, 255)
    key = (150, 156, 166, 255)
    side = img(16, 16, body)
    ImageDraw.Draw(side).rectangle([0, 0, 15, 15], outline=edge)
    save(side, "block/code_panel_side.png")
    for name, led in INPUT_LEDS.items():
        im = img(16, 16, body)
        d = ImageDraw.Draw(im)
        d.rectangle([4, 2, 11, 13], fill=body, outline=edge)
        d.rectangle([5, 3, 8, 4], fill=CYAN_DARK)
        px(im, [(5, 3), (6, 3)], (80, 240, 120, 255))
        d.rectangle([10, 3, 10, 4], fill=led)
        for row, y in enumerate((6, 8, 10, 12)):
            for col, x in enumerate((5, 7, 9)):
                colour = key
                if row == 3 and col == 0:
                    colour = (220, 70, 60, 255)
                elif row == 3 and col == 2:
                    colour = (80, 220, 110, 255)
                px(im, [(x, y)], colour)
        save(im, f"block/code_panel_{name}.png")


def bypass_kit_item():
    """
    De Bypass Kit (hoofdstuk 12): een open zwart etui met een schroevendraaier met een gele steel, een
    potje oplichtend poeder voor vingerafdrukken, een rood en een blauw draadje, en een computerchip.
    """
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    case = (34, 36, 42, 255)
    case_edge = (18, 19, 23, 255)
    case_light = (58, 61, 70, 255)
    # Het etui: de onderkant met een lichte rand, en de open klep erachter.
    d.rectangle([1, 4, 14, 6], fill=case_light, outline=case_edge)
    d.rectangle([1, 6, 14, 14], fill=case, outline=case_edge)
    d.line([2, 13, 13, 13], fill=case_edge)
    px(im, [(2, 7), (2, 8)], case_light)
    # De schroevendraaier: gele steel linksonder, zilveren schacht schuin omhoog.
    d.line([3, 12, 5, 10], fill=(232, 182, 40, 255))
    d.line([4, 12, 6, 10], fill=(184, 130, 24, 255))
    d.line([6, 9, 9, 6], fill=(196, 202, 212, 255))
    px(im, [(10, 5)], (236, 240, 246, 255))
    # Het potje poeder: een glazen buisje met oplichtend poeder (glowstonestof).
    d.rectangle([11, 7, 13, 12], fill=(120, 140, 150, 255), outline=(70, 80, 90, 255))
    d.rectangle([12, 9, 12, 11], fill=(255, 232, 120, 255))
    px(im, [(12, 8)], (255, 248, 200, 255))
    px(im, [(11, 6), (12, 6), (13, 6)], (140, 96, 60, 255))
    # Twee draadjes die eruit hangen, en de computerchip (blauw lampje).
    d.line([5, 13, 7, 12, 9, 13], fill=(220, 50, 40, 255))
    d.line([7, 14, 9, 13, 11, 14], fill=(60, 140, 255, 255))
    px(im, [(8, 9), (9, 9), (8, 10), (9, 10)], (40, 44, 52, 255))
    px(im, [(9, 9)], CYAN)
    save(im, "item/bypass_kit.png")


# --------------------------------------------------------------------- Guard robots (hoofdstuk 21)

# Per stijl vier verven: hoofdkleur, donker (gewrichten), lichtjes, details (ogen, sleuven). Volgorde en
# plekken zoals in RobotModel.java: Commando linksboven, Trainer rechtsboven, Battle linksonder, Super
# Battle rechtsonder; in elk vierkant van 64 x 64 de hoofdkleur bovenaan (64 x 32), donker eronder
# (64 x 16), en onderaan lichtjes (links, 32 x 16) en details (rechts, 32 x 16).
ROBOT_STYLES = [
    # naam,          hoofdkleur,       donker,           lichtjes,         details,       gloeien?
    ("commando",     (70, 74, 80),     (38, 40, 45),     (255, 90, 50),    (14, 14, 16),  True),
    ("trainer",      (226, 232, 238),  (150, 160, 172),  (110, 215, 255),  (40, 52, 64),  True),
    ("battle",       (206, 186, 138),  (138, 116, 78),   (170, 120, 60),   (52, 42, 30),  False),
    ("super_battle", (78, 88, 104),    (44, 50, 60),     (110, 122, 140),  (18, 20, 24),  False),
]


def robot_textures():
    """
    De guard robot (128 x 128) en zijn lichtjes die in het donker gloeien (zelfde indeling). Elk blokje van
    het model kiest een verf en alle blokjes met die verf delen dezelfde plek, dus elke verf is een effen
    vlak metaal met een beetje ruis en hier en daar een kras of een klinknagel.
    """
    import random
    rng = random.Random(21)
    base = img(128, 160)
    glow = img(128, 160)

    def metal(im, x0, y0, w, h, colour, spread, scratches):
        r, g, b = colour
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                n = rng.randint(-spread, spread)
                im.putpixel((x, y), (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), 255))
        # Krasjes (iets lichter) en klinknagels (iets donkerder), op vaste plekken door de vaste seed.
        for _ in range(scratches):
            x = rng.randrange(x0, x0 + w)
            y = rng.randrange(y0, y0 + h)
            light = (min(255, r + 26), min(255, g + 26), min(255, b + 26), 255)
            im.putpixel((x, y), light)
            if x + 1 < x0 + w:
                im.putpixel((x + 1, y), light)
        for _ in range(scratches // 2):
            x = rng.randrange(x0, x0 + w)
            y = rng.randrange(y0, y0 + h)
            im.putpixel((x, y), (max(0, r - 30), max(0, g - 30), max(0, b - 30), 255))

    for i, (name, main, dark, light, detail, glows) in enumerate(ROBOT_STYLES):
        qx = i % 2 * 64
        qy = i // 2 * 64
        metal(base, qx, qy, 64, 32, main, 5, 40)
        metal(base, qx, qy + 32, 64, 16, dark, 4, 12)
        metal(base, qx, qy + 48, 32, 16, light, 2, 0)
        metal(base, qx + 32, qy + 48, 32, 16, detail, 2, 0)
        if glows:
            # De lichtjes gloeien in hun eigen kleur; de rest van de gloeilaag blijft doorzichtig.
            lr, lg, lb = light
            for x in range(qx, qx + 32):
                for y in range(qy + 48, qy + 64):
                    glow.putpixel((x, y), (lr, lg, lb, 255))
    # Onderaan (hoofdstuk 22): de verven van de modules, voor alle stijlen hetzelfde (zie Paint in RobotModel).
    module_paints = [
        (0, 128, (176, 184, 194), True, 4),    # staal
        (32, 128, (52, 56, 64), True, 3),      # geweermetaal
        (64, 128, (90, 225, 255), False, 0),   # lens (gloeit)
        (96, 128, (255, 70, 55), False, 0),    # rood lampje (gloeit)
        (0, 144, (36, 62, 140), True, 3),      # zonnecellen
        (32, 144, (232, 236, 240), True, 2),   # antenne
    ]
    for x0, y0, colour, noisy, spread in module_paints:
        metal(base, x0, y0, 32, 16, colour, spread, 6 if noisy else 0)
    # De zonnecellen krijgen een raster, zoals een echt paneel.
    for x in range(0, 32, 3):
        for y in range(144, 160):
            base.putpixel((x, y), (190, 200, 215, 255))
    for (x0, y0, colour, noisy, spread) in module_paints[2:4]:
        for x in range(x0, x0 + 32):
            for y in range(y0, y0 + 16):
                glow.putpixel((x, y), colour + (255,))
    save(base, "entity/robot/guard_robot.png")
    save(glow, "entity/robot/guard_robot_glow.png")


# Elk onderdeel als icoontje (16 x 16): rechthoekjes in de verf van zijn stijl, (x1, y1, x2, y2) met de
# randen erbij, zoals ImageDraw.rectangle. "m" = hoofdkleur, "d" = donker, "l" = lichtjes, "x" = details.
ROBOT_PART_ICONS = {
    ("commando", "head"): [("m", 4, 3, 11, 11), ("m", 6, 2, 9, 2), ("d", 7, 12, 8, 13), ("x", 6, 6, 9, 9), ("l", 7, 7, 8, 8)],
    ("trainer", "head"): [("m", 3, 4, 12, 11), ("m", 4, 3, 11, 12), ("m", 5, 2, 10, 2), ("l", 4, 6, 11, 6),
                          ("l", 2, 8, 2, 8), ("l", 13, 8, 13, 8), ("d", 7, 13, 8, 14)],
    # De Battle van opzij: dunne nek, schedel, en een lange snuit naar rechts.
    ("battle", "head"): [("d", 6, 9, 6, 14), ("m", 3, 4, 8, 8), ("m", 4, 3, 7, 3), ("m", 9, 6, 11, 8),
                         ("m", 12, 7, 14, 9), ("x", 10, 7, 10, 7)],
    ("super_battle", "head"): [("m", 4, 5, 11, 11), ("d", 6, 4, 9, 4), ("x", 5, 7, 10, 7)],

    ("commando", "body"): [("m", 3, 2, 12, 8), ("d", 5, 9, 10, 11), ("m", 4, 12, 11, 14), ("d", 4, 4, 11, 4)],
    ("trainer", "body"): [("m", 2, 3, 13, 8), ("m", 3, 2, 12, 9), ("l", 7, 4, 8, 5), ("d", 4, 10, 11, 11),
                          ("m", 4, 12, 11, 14), ("l", 5, 13, 10, 13)],
    ("battle", "body"): [("m", 4, 2, 11, 7), ("m", 5, 3, 10, 6), ("d", 7, 8, 8, 11), ("m", 5, 12, 10, 14)],
    ("super_battle", "body"): [("m", 1, 3, 14, 10), ("m", 3, 1, 12, 2), ("d", 6, 11, 9, 12), ("m", 5, 13, 10, 14)],

    ("commando", "arms"): [("m", 5, 1, 10, 4), ("d", 6, 5, 9, 8), ("m", 5, 9, 10, 12), ("d", 6, 13, 9, 14)],
    ("trainer", "arms"): [("m", 5, 1, 10, 3), ("m", 6, 4, 9, 7), ("d", 7, 8, 8, 8), ("m", 6, 9, 9, 11),
                          ("l", 5, 12, 10, 12), ("d", 7, 13, 8, 14)],
    ("battle", "arms"): [("d", 7, 1, 8, 2), ("m", 7, 3, 8, 6), ("d", 7, 7, 8, 7), ("m", 7, 8, 8, 11), ("d", 6, 12, 9, 13)],
    ("super_battle", "arms"): [("m", 4, 1, 11, 3), ("d", 5, 4, 9, 5), ("m", 4, 6, 11, 12), ("x", 5, 10, 7, 11),
                               ("d", 5, 13, 9, 14)],

    ("commando", "legs"): [("m", 3, 1, 5, 6), ("m", 10, 1, 12, 6), ("d", 3, 7, 5, 7), ("d", 10, 7, 12, 7),
                           ("m", 3, 8, 5, 12), ("m", 10, 8, 12, 12), ("d", 2, 13, 5, 14), ("d", 10, 13, 13, 14)],
    ("trainer", "legs"): [("m", 2, 1, 6, 6), ("m", 9, 1, 13, 6), ("l", 4, 7, 4, 7), ("l", 11, 7, 11, 7),
                          ("d", 3, 7, 3, 7), ("d", 5, 7, 5, 7), ("d", 10, 7, 10, 7), ("d", 12, 7, 12, 7),
                          ("m", 2, 8, 6, 12), ("m", 9, 8, 13, 12), ("m", 2, 13, 6, 14), ("m", 9, 13, 13, 14)],
    ("battle", "legs"): [("m", 4, 1, 5, 6), ("m", 10, 1, 11, 6), ("d", 4, 7, 5, 7), ("d", 10, 7, 11, 7),
                         ("m", 4, 8, 5, 12), ("m", 10, 8, 11, 12), ("d", 3, 13, 6, 14), ("d", 9, 13, 12, 14)],
    ("super_battle", "legs"): [("d", 3, 1, 5, 5), ("d", 10, 1, 12, 5), ("m", 2, 6, 6, 12), ("m", 9, 6, 13, 12),
                               ("d", 2, 13, 6, 14), ("d", 9, 13, 13, 14)],
}


def robot_part_items():
    """De 16 onderdelen van een guard robot als item-icoon, met een donker randje zodat ze goed zichtbaar zijn."""
    for name, main, dark, light, detail, glows in ROBOT_STYLES:
        paint = {"m": main, "d": dark, "l": light, "x": detail}
        edge = (max(0, main[0] // 4), max(0, main[1] // 4), max(0, main[2] // 4), 255)
        for slot in ("head", "body", "arms", "legs"):
            im = img(16, 16)
            d = ImageDraw.Draw(im)
            for key, x1, y1, x2, y2 in ROBOT_PART_ICONS[(name, slot)]:
                d.rectangle([x1, y1, x2, y2], fill=paint[key] + (255,))
            # Een lichter randje bovenaan elk stuk hoofdkleur (licht van boven), dan de omtrek.
            r, g, b = main
            shine = (min(255, r + 30), min(255, g + 30), min(255, b + 30), 255)
            for x in range(16):
                for y in range(16):
                    if im.getpixel((x, y)) == main + (255,) and (y == 0 or im.getpixel((x, y - 1))[3] == 0):
                        im.putpixel((x, y), shine)
            outline = [(x, y) for x in range(16) for y in range(16) if im.getpixel((x, y))[3] == 0 and any(
                0 <= x + dx < 16 and 0 <= y + dy < 16 and im.getpixel((x + dx, y + dy))[3] == 255 and
                im.getpixel((x + dx, y + dy)) != edge for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
            for point in outline:
                im.putpixel(point, edge)
            save(im, f"item/robot/{name}_{slot}.png")


def robot_circuit_board():
    """
    De Robot Circuit Board: een groene printplaat met koperen sporen, een zwarte chip in het midden met een
    blauw lampje, en 8 gouden pinnen (vier boven, vier onder) waar de modules op komen.
    """
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    board = (34, 110, 58, 255)
    board_dark = (22, 74, 40, 255)
    trace = (190, 120, 60, 255)
    gold = (236, 196, 70, 255)
    gold_dark = (170, 128, 34, 255)
    d.rectangle([1, 2, 14, 13], fill=board, outline=board_dark)
    # Koperen sporen van de pinnen naar de chip.
    for x in (3, 6, 9, 12):
        d.line([x, 4, x, 5], fill=trace)
        d.line([x, 10, x, 11], fill=trace)
    d.line([3, 5, 12, 5], fill=trace)
    d.line([3, 10, 12, 10], fill=trace)
    # De chip.
    d.rectangle([5, 6, 10, 9], fill=(26, 28, 32, 255), outline=(60, 64, 72, 255))
    px(im, [(9, 7)], CYAN)
    # De 8 pinnen: gouden plaatjes boven en onder.
    for x in (3, 6, 9, 12):
        for y in (2, 13):
            px(im, [(x, y)], gold)
        px(im, [(x, 3)], gold_dark)
        px(im, [(x, 12)], gold_dark)
    save(im, "item/robot_circuit_board.png")


def robot_assembler_textures():
    """
    De Robot Assembler: een stalen werkbank. Voorop een scherm met het silhouet van een robot in cyaan, opzij
    stalen panelen met een geel-zwarte rand onderaan, en bovenop een werkblad met een raster en de klemmen
    waar de robot op ligt.
    """
    steel = (78, 86, 96, 255)
    steel_dark = (44, 50, 58, 255)
    steel_light = (112, 122, 134, 255)
    bolt = (150, 160, 172, 255)

    side = img(16, 16, steel)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    d.line([1, 1, 14, 1], fill=steel_light)
    d.line([7, 2, 7, 11], fill=steel_dark)
    for y in range(12, 15):
        for x in range(1, 15):
            side.putpixel((x, y), (230, 190, 40, 255) if (x + y) % 4 < 2 else (30, 30, 34, 255))
    px(side, [(2, 3), (13, 3), (2, 10), (13, 10)], bolt)
    save(side, "block/robot_assembler_side.png")

    front = side.copy()
    d = ImageDraw.Draw(front)
    d.rectangle([2, 2, 13, 11], fill=(12, 18, 24, 255), outline=steel_dark)
    # Het silhouet van een robot op het scherm.
    robot = [(7, 3), (8, 3), (7, 4), (8, 4), (6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6),
             (10, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (6, 9), (9, 9), (6, 10), (9, 10)]
    px(front, robot, CYAN)
    px(front, [(4, 3), (11, 3), (4, 10), (11, 10)], CYAN_DIM)
    save(front, "block/robot_assembler_front.png")

    top = img(16, 16, (58, 64, 72, 255))
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    for i in range(3, 16, 4):
        d.line([i, 1, i, 14], fill=(68, 76, 86, 255))
        d.line([1, i, 14, i], fill=(68, 76, 86, 255))
    # De klemmen: vier hoekjes in geel, en in het midden een lichte omtrek van een liggende robot.
    for x, y, dx, dy in ((2, 2, 1, 1), (13, 2, -1, 1), (2, 13, 1, -1), (13, 13, -1, -1)):
        px(top, [(x, y), (x + dx, y), (x, y + dy)], (230, 190, 40, 255))
    d.rectangle([6, 3, 9, 4], outline=steel_light)
    d.rectangle([5, 5, 10, 9], outline=steel_light)
    d.line([6, 10, 6, 12], fill=steel_light)
    d.line([9, 10, 9, 12], fill=steel_light)
    save(top, "block/robot_assembler_top.png")


def guard_robot_item():
    """Een samengestelde guard robot als icoon: een grijze robot van voren, met één cyaan oog."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    body = (110, 118, 128, 255)
    dark = (52, 58, 66, 255)
    edge = (24, 26, 30, 255)
    d.rectangle([6, 1, 9, 4], fill=body)          # hoofd
    px(im, [(7, 2), (8, 2)], CYAN)                # oog
    d.rectangle([5, 5, 10, 9], fill=body)         # lijf
    d.rectangle([7, 10, 8, 10], fill=dark)        # middel
    d.rectangle([3, 5, 4, 10], fill=dark)         # armen
    d.rectangle([11, 5, 12, 10], fill=dark)
    d.rectangle([5, 11, 6, 14], fill=body)        # benen
    d.rectangle([9, 11, 10, 14], fill=body)
    px(im, [(7, 6)], CYAN_DIM)
    outline = [(x, y) for x in range(16) for y in range(16) if im.getpixel((x, y))[3] == 0 and any(
        0 <= x + dx < 16 and 0 <= y + dy < 16 and im.getpixel((x + dx, y + dy))[3] == 255
        and im.getpixel((x + dx, y + dy)) != edge for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for point in outline:
        im.putpixel(point, edge)
    save(im, "item/guard_robot.png")


def robot_dock_textures():
    """
    Het Robot Dock: een donkere laadplaat met een geel-zwarte rand en een cyaan ring in het midden (daar staat
    de robot), stalen zijkanten, een paal met verticale groeven, en het cyaan lampje.
    """
    steel = (70, 78, 88, 255)
    steel_dark = (40, 46, 54, 255)
    pad = img(16, 16, (34, 38, 44, 255))
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            pad.putpixel((x, y), (230, 190, 40, 255) if (x + y) % 4 < 2 else (24, 24, 28, 255))
    d = ImageDraw.Draw(pad)
    d.ellipse([3, 3, 12, 12], outline=CYAN_DIM)
    d.ellipse([5, 5, 10, 10], outline=CYAN)
    save(pad, "block/robot_dock_pad.png")

    side = img(16, 16, steel)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    d.line([0, 13, 15, 13], fill=CYAN_DIM)
    save(side, "block/robot_dock_side.png")

    pole = img(16, 16, steel)
    d = ImageDraw.Draw(pole)
    for x in (2, 6, 10, 14):
        d.line([x, 0, x, 15], fill=steel_dark)
    save(pole, "block/robot_dock_pole.png")
    save(img(16, 16, CYAN), "block/robot_dock_light.png")


def robot_module_items():
    """
    De modules van hoofdstuk 22: elk een klein groen printplaatje met twee gouden pootjes onderaan (daarmee
    soldeer je hem op een pin), en erop een tekentje: radarboogjes (Scan), een pijl met een meetlat (Distance),
    een gezichtje met een cyaan oog (Face Recognition), een gestippeld pad met een vlag (Pathfinding), een rode
    straal (Laser), een geweerloop (Gun), blauwe zonnecellen (Solar Panel), een lens (Camera). De verbrande
    module is zwartgeblakerd, met as en een gloeiend randje.
    """
    board = (34, 110, 58, 255)
    board_dark = (20, 66, 36, 255)
    gold = (236, 196, 70, 255)

    def base(burnt=False):
        im = img(16, 16)
        d = ImageDraw.Draw(im)
        fill = (40, 34, 30, 255) if burnt else board
        edge = (16, 14, 12, 255) if burnt else board_dark
        d.rectangle([2, 2, 13, 12], fill=fill, outline=edge)
        px(im, [(5, 13), (5, 14), (10, 13), (10, 14)], (90, 80, 60, 255) if burnt else gold)
        return im, d

    symbols = {
        "scan": lambda im, d: (d.arc([4, 5, 11, 12], 200, 340, fill=CYAN), d.arc([6, 7, 9, 10], 200, 340, fill=CYAN),
                               px(im, [(7, 9), (8, 9)], (255, 255, 255, 255))),
        "distance": lambda im, d: (d.line([4, 6, 11, 6], fill=(250, 220, 80, 255)), px(im, [(10, 5), (10, 7), (9, 4), (9, 8)],
                                   (250, 220, 80, 255)), [d.line([x, 9, x, 10 if x % 2 else 9], fill=(230, 230, 230, 255)) for x in range(4, 12)]),
        "face_recognition": lambda im, d: (d.ellipse([5, 4, 10, 10], outline=(235, 240, 245, 255)),
                                           px(im, [(6, 6), (9, 6)], CYAN), d.line([6, 8, 9, 8], fill=(235, 240, 245, 255))),
        "pathfinding": lambda im, d: (px(im, [(4, 10), (6, 9), (8, 8), (9, 6)], (255, 255, 255, 255)),
                                      d.line([10, 4, 10, 8], fill=(220, 220, 220, 255)), d.rectangle([11, 4, 12, 5], fill=(230, 60, 50, 255))),
        "laser": lambda im, d: (d.rectangle([4, 6, 7, 8], fill=(60, 64, 72, 255)), d.line([8, 7, 12, 7], fill=(255, 70, 60, 255)),
                                px(im, [(12, 6), (12, 8)], (255, 160, 150, 255))),
        "gun": lambda im, d: (d.rectangle([4, 6, 12, 7], fill=(70, 74, 82, 255)), d.rectangle([5, 8, 6, 10], fill=(70, 74, 82, 255)),
                              px(im, [(12, 6)], (20, 20, 22, 255))),
        "solar_panel": lambda im, d: (d.rectangle([4, 4, 11, 10], fill=(40, 70, 150, 255), outline=(200, 210, 220, 255)),
                                      d.line([7, 4, 7, 10], fill=(200, 210, 220, 255)), d.line([4, 7, 11, 7], fill=(200, 210, 220, 255))),
        "camera": lambda im, d: (d.rectangle([4, 5, 11, 10], fill=(40, 44, 52, 255)), d.ellipse([6, 6, 9, 9], fill=(12, 14, 18, 255),
                                 outline=(140, 150, 160, 255)), px(im, [(7, 7)], CYAN)),
    }
    for name, draw in symbols.items():
        im, d = base()
        draw(im, d)
        save(im, f"item/module/{name}.png")
    # De verbrande module: zwart, met as en een gloeiend randje.
    im, d = base(burnt=True)
    px(im, [(4, 4), (7, 5), (10, 4), (5, 8), (9, 9), (11, 7)], (90, 86, 82, 255))
    px(im, [(6, 6), (8, 7), (7, 10)], (255, 120, 40, 255))
    px(im, [(9, 6)], (255, 200, 90, 255))
    save(im, "item/module/burnt.png")


def soldering_iron():
    """De Soldering Iron: schuin, met een zwart-rood handvat, een stalen staaf en een gloeiend oranje puntje."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    d.line([2, 13, 5, 10], fill=(30, 30, 34, 255), width=2)          # handvat
    px(im, [(3, 13), (4, 12)], (200, 40, 40, 255))                    # rode band
    d.line([6, 9, 11, 4], fill=(170, 176, 186, 255))                  # staaf
    d.line([6, 10, 11, 5], fill=(110, 116, 126, 255))
    px(im, [(12, 3), (12, 4)], (255, 140, 40, 255))                   # gloeiende punt
    px(im, [(13, 2)], (255, 220, 120, 255))
    save(im, "item/soldering_iron.png")


def robot_ammo():
    """Robot Ammo (hoofdstuk 24): een groen doosje met drie koperen patronen die eruit steken."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    d.rectangle([2, 7, 13, 14], fill=(62, 84, 52, 255), outline=(34, 48, 28, 255))   # doosje
    d.line([3, 9, 12, 9], fill=(84, 110, 70, 255))
    for x in (4, 7, 10):
        d.rectangle([x, 3, x + 1, 7], fill=(205, 140, 60, 255))                      # koperen huls
        px(im, [(x, 2), (x + 1, 2)], (230, 225, 210, 255))                            # punt
        px(im, [(x + 1, 4), (x + 1, 5)], (240, 180, 90, 255))                         # glans
    save(im, "item/robot_ammo.png")


def ion_blaster():
    """De Ion Blaster (hoofdstuk 24): een wit-blauw pistool met een gloeiende blauwe ionenkamer en een brede loop."""
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    white, grey, dark = (226, 232, 238, 255), (150, 160, 172, 255), (60, 66, 76, 255)
    blue, glow = (60, 150, 255, 255), (170, 225, 255, 255)
    d.polygon([(5, 10), (7, 10), (5, 13), (3, 13)], fill=dark)       # schuine handgreep, vast aan het lijf
    d.rectangle([4, 7, 11, 9], fill=white, outline=grey)             # lijf
    d.rectangle([11, 6, 14, 8], fill=grey)                           # brede loop
    px(im, [(14, 7)], blue)
    d.rectangle([6, 5, 9, 6], fill=blue)                             # ionenkamer bovenop
    px(im, [(7, 5), (8, 5)], glow)
    px(im, [(5, 8), (8, 8)], blue)                                   # blauwe streepjes op het lijf
    save(im, "item/ion_blaster.png")


def guard_drone_textures():
    """
    De security drone (hoofdstuk 25, zie GuardDroneModel): een grafietgrijze romp met een wit dakje, grijze armen en
    rotors, donkere ski's, en per module een eigen stukje (laserloop, geweerloop, zonnevleugels, radarkoepel, lens,
    sensor, antenne, camera). Plus de lichtlaag (alleen de lampjes: cyaan, en rood bij laser, sensor en camera) en het
    icoon in je rugzak.
    """
    graphite, graphite_light, graphite_dark = (58, 64, 74, 255), (78, 86, 98, 255), (34, 38, 44, 255)
    white, white_shade = (214, 220, 228, 255), (170, 178, 188, 255)
    grey, blade = (120, 128, 140, 255), (178, 188, 198, 255)
    red, red_dim = (255, 70, 55, 255), (150, 34, 26, 255)
    glass = (18, 20, 26, 255)
    solar, solar_line = (36, 64, 128, 255), (70, 110, 180, 255)
    tex = img(64, 64)
    glow = img(64, 64)

    def paint(regions, color, top=None, bottom=None):
        for region in regions.values():
            fill_region(tex, region, color)
        if top is not None:
            fill_region(tex, regions["top"], top)
        if bottom is not None:
            fill_region(tex, regions["bottom"], bottom)

    # Romp en dakje
    body = box_regions(0, 0, 8, 4, 8)
    paint(body, graphite, top=graphite_light, bottom=graphite_dark)
    for name in ("east", "front", "west", "back"):
        x, y, w, h = body[name]
        for i in range(w):
            tex.putpixel((x + i, y), graphite_light)          # lichte bovenrand
            tex.putpixel((x + i, y + h - 1), graphite_dark)   # donkere onderrand
            glow.putpixel((x + i, y + 1), CYAN if name == "front" and 0 < i < w - 1 else (0, 0, 0, 0))
        if name != "front":
            for i in range(1, w - 1):
                glow.putpixel((x + i, y + 1), CYAN_DIM)       # een zwakke lichtstreep rondom
    canopy = box_regions(0, 12, 6, 1, 6)
    paint(canopy, white, top=white, bottom=white_shade)
    x, y, w, h = canopy["top"]
    d = ImageDraw.Draw(tex)
    d.rectangle([x, y, x + w - 1, y + h - 1], outline=white_shade)
    px(glow, [(x + 2, y + 2), (x + 3, y + 2), (x + 2, y + 3), (x + 3, y + 3)], CYAN)   # lampje midden op het dak

    # Armen, naven, bladen, ski's
    for v in (20, 22):
        paint(box_regions(0, v, 16, 1, 1), grey)
    hub = box_regions(36, 20, 2, 2, 2)
    paint(hub, graphite_light)
    hx, hy, hw, hh = hub["top"]
    px(glow, [(hx, hy)], CYAN_DIM)
    paint(box_regions(36, 24, 7, 1, 1), blade)
    paint(box_regions(36, 26, 1, 1, 7), blade)
    paint(box_regions(0, 26, 1, 2, 7), graphite_dark)

    # Modules
    scan = box_regions(40, 36, 3, 2, 3)                       # radarkoepel: wit met een cyaan punt
    paint(scan, white, top=white, bottom=white_shade)
    sx, sy, sw, sh = scan["top"]
    px(glow, [(sx + 1, sy + 1)], CYAN)
    face = box_regions(0, 36, 6, 1, 1)                        # lensstrook: cyaan
    paint(face, CYAN_DARK)
    for name in ("front", "top", "bottom"):
        fill_region(glow, face[name], CYAN if name == "front" else CYAN_DIM)
    distance = box_regions(16, 36, 2, 1, 1)                   # sensor: donker met een rood oogje
    paint(distance, glass)
    fill_region(glow, distance["front"], red)
    paint(box_regions(24, 36, 1, 4, 1), white)                # antenne
    laser = box_regions(0, 40, 1, 1, 6)                       # laserloop: grijs, rode punt
    paint(laser, white_shade)
    fill_region(tex, laser["front"], red_dim)
    fill_region(glow, laser["front"], red)
    gun = box_regions(16, 40, 2, 1, 6)                        # geweerloop: donker metaal
    paint(gun, graphite_dark)
    fill_region(tex, gun["front"], glass)
    camera = box_regions(32, 40, 2, 2, 2)                     # camera: donker glas, rood opnamelampje
    paint(camera, graphite_dark)
    fill_region(tex, camera["front"], glass)
    cx, cy, cw, ch = camera["front"]
    px(glow, [(cx + 1, cy)], red)
    for u in (0, 20):                                         # zonnevleugels: blauwe cellen met lijntjes
        wing = box_regions(u, 48, 4, 1, 6)
        paint(wing, graphite_light)
        for name in ("top", "bottom"):
            x, y, w, h = wing[name]
            fill_region(tex, wing[name], solar)
            for yy in range(y + 1, y + h, 2):
                for xx in range(x, x + w):
                    tex.putpixel((xx, yy), solar_line)
    save(tex, "entity/robot/guard_drone.png")
    save(glow, "entity/robot/guard_drone_glow.png")

    # Het icoon: de drone van bovenaf, met zijn armen en rotors, een wit dakje en een cyaan lampje.
    im = img(16, 16)
    d = ImageDraw.Draw(im)
    for x0, y0, x1, y1 in [(3, 3, 6, 6), (12, 3, 9, 6), (3, 12, 6, 9), (12, 12, 9, 9)]:
        d.line([x0, y0, x1, y1], fill=grey)
    for cx, cy in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        d.ellipse([cx - 2, cy - 1, cx + 2, cy + 1], outline=blade)
    d.rectangle([5, 5, 10, 10], fill=graphite, outline=graphite_dark)
    d.rectangle([6, 6, 9, 9], fill=white)
    px(im, [(7, 7), (8, 7), (7, 8), (8, 8)], CYAN)
    d.line([6, 10, 9, 10], fill=CYAN_DIM)
    save(im, "item/guard_drone.png")


def training_block_textures():
    """
    De trainingsblokken (hoofdstuk 26): Training Start (een groen afspeel-driehoekje in een cyaan ring), Training End
    (een rood stop-vierkantje, en aan de zijkanten een lampje dat rood brandt bij een treffer) en Training Goal (een
    zwart-wit geblokte finishvlag met een gouden rand). Allemaal op een donkere metalen kast met waarschuwingsstrepen.
    """
    dark, darker, edge = (40, 45, 52, 255), (26, 29, 34, 255), (70, 78, 88, 255)
    yellow, black = (222, 186, 40, 255), (22, 22, 24, 255)
    green, green_dim = (90, 220, 90, 255), (40, 120, 45, 255)
    red, red_dim, red_off = (255, 70, 55, 255), (150, 34, 26, 255), (70, 24, 22, 255)
    white, gold, gold_dark = (230, 232, 236, 255), (232, 190, 60, 255), (160, 120, 30, 255)

    def side(stripe):
        im = img(16, 16, dark)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=edge)
        for x in range(16):
            im.putpixel((x, 14), yellow if (x // 2) % 2 == 0 else black)
            im.putpixel((x, 15), black if (x // 2) % 2 == 0 else yellow)
        if stripe:
            d.line([1, 6, 14, 6], fill=stripe)
        return im

    bottom = img(16, 16, darker)
    ImageDraw.Draw(bottom).rectangle([0, 0, 15, 15], outline=edge)
    save(bottom, "block/training_bottom.png")

    # Training Start
    save(side(green_dim), "block/training_start_side.png")
    top = img(16, 16, dark)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=edge)
    d.ellipse([2, 2, 13, 13], outline=CYAN)
    d.polygon([(6, 4), (6, 11), (11, 7)], fill=green)
    px(top, [(6, 4), (6, 11)], green_dim)
    save(top, "block/training_start_top.png")

    # Training End: een rood stop-vierkantje; het lampje op de zijkant brandt bij een treffer.
    top = img(16, 16, dark)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=edge)
    d.ellipse([2, 2, 13, 13], outline=red_dim)
    d.rectangle([5, 5, 10, 10], fill=red)
    save(top, "block/training_end_top.png")
    for lit in (False, True):
        im = side(None)
        d = ImageDraw.Draw(im)
        d.rectangle([5, 4, 10, 9], fill=red if lit else red_off, outline=darker)
        if lit:
            px(im, [(6, 5), (7, 5)], (255, 190, 170, 255))
        save(im, "block/training_end_side_on.png" if lit else "block/training_end_side.png")

    # Training Goal: een geblokte finishvlag met een gouden rand.
    top = img(16, 16, white)
    for x in range(1, 15):
        for y in range(1, 15):
            if ((x - 1) // 2 + (y - 1) // 2) % 2 == 0:
                top.putpixel((x, y), black)
    ImageDraw.Draw(top).rectangle([0, 0, 15, 15], outline=gold)
    save(top, "block/training_goal_top.png")
    im = img(16, 16, white)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 2], fill=gold)
    d.line([0, 3, 15, 3], fill=gold_dark)
    for x in range(16):
        for y in range(7, 11):
            if (x // 2 + (y - 7) // 2) % 2 == 0:
                im.putpixel((x, y), black)
    d.rectangle([0, 0, 15, 15], outline=gold_dark)
    save(im, "block/training_goal_side.png")


if __name__ == "__main__":
    imt_glasses_item()
    computer_chip()
    battery_upgrade()
    scanner_upgrade()
    imt_equipment()
    station_textures()
    gui()
    drone_upgrade()
    drone_textures()
    drone_damage_textures()
    planet_texture()
    satellite_textures()
    cable_textures()
    dome_field()
    zone_textures()
    zone_marker_item()
    c4_textures()
    remote_c4_items()
    repair_paste_item()
    crusher_textures()
    block_powder_item()
    compactor_textures()
    arc_lamp_textures()
    light_switch_textures()
    relay_textures()
    relay_inverted_textures()
    relay_emitter_textures()
    shield_textures()
    rayshield_walls()
    charger_textures()
    emp_item()
    drone_item()
    holo_table_textures()
    tech_suit()
    suit_hologram()
    hydra_circuit()
    hydra_rifle()
    hydra_soldier()
    mini_rocket()
    orbital_missile()
    rocket_pack()
    turret_textures()
    rocket_ammo()
    security_textures()
    hacking_laptop()
    hacking_laptop_block()
    workbench_textures()
    drone_dock_textures()
    landing_pad_textures()
    power_cabinet_textures()
    server_rack_textures()
    server_part_items()
    keycard_items()
    keycard_reader_textures()
    code_panel_textures()
    bypass_kit_item()
    robot_textures()
    robot_part_items()
    robot_circuit_board()
    robot_assembler_textures()
    guard_robot_item()
    robot_dock_textures()
    robot_module_items()
    soldering_iron()
    robot_ammo()
    ion_blaster()
    guard_drone_textures()
    training_block_textures()
