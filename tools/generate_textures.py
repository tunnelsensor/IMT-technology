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
    relay_textures()
    shield_textures()
    rayshield_walls()
    charger_textures()
    emp_item()
