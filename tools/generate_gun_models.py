"""
Maakt de 3D-modellen en plaatjes van de vuurwapens (hoofdstuk 52): de Glock 19 en zijn zes onderdelen (elk in zijn
materialen), het magazijn, de 9mm-patroon, de huls en de kogelpunt, de holster en de twee pouches, de staven en
poeders van staal en titanium en de polymeerkorrels, en de twee blokken (de Gunsmith Table en de Reloading Bench).

Hoe het werkt: precies zoals tools/generate_weapon_models.py (de lasergeweren). Elk model is een lijstje doosjes in
pixels (16 = één blok), elk doosje krijgt een MATERIAAL uit één plaatje van 128 x 128 (textures/item/firearms.png).
Nieuw hier: een doosje kan per kant een ander materiaal krijgen (faces=...), bijvoorbeeld de voorkant van de slede met
de twee gaten (de loop en de veerstang).

De Glock 19 (jouw keuze: de Umarex Glock 19 van airsoftdoctor.be, een zwarte Gen 3): een vierkante slede met
schuine groeven achteraan, de uitwerpopening rechts, een wit stipje op de korrel en een witte U op de keep, een
polymeerframe met een rail onder de loop, een trekkerbeugel met vingersteun, en een greep met vingergroeven. De greep
staat 22,5 graden schuin (een echte Glock 22). De loop wijst naar -z (het noorden), de greep naar +z. De loop van de
Glock ligt op x = 8, rond y = 11,7.

Een onderdeel bestaat per materiaal (polymeer, staal, titanium): models/firearm/glock_19/<onderdeel>_<materiaal>.json.
In je eigen beeld en op de Gunsmith Table tekent de mod ze los (zie client/guns/FirearmRenderer en GunsmithScreen);
voor je inventaris, je hand in derde persoon en op de grond is er het hele pistool in één bestand (stalen onderdelen,
models/item/glock_19.json). De losse onderdelen als item (models/item/gun_slide.json enz.) kiezen hun materiaal met
een "override" (zie client/guns/FirearmModels.registerProperties).

Draaien (vanuit de hoofdmap):  python tools/generate_gun_models.py
Het script is eigenaar van alles wat het schrijft; een volledige run is veilig.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "imttech"
TEXTURE = "imttech:item/firearms"
SIDES = ("north", "south", "east", "west", "up", "down")

# ---------------------------------------------------------------- De materialen (kolom, rij) op het plaatje

MATERIALS = {
    # Rij 0: het pistool zelf.
    "polymer": (0, 0), "polymer_grip": (1, 0), "slide_black": (2, 0), "slide_front": (3, 0),
    "port_dark": (4, 0), "steel": (5, 0), "titanium": (6, 0), "titanium_front": (7, 0),
    # Rij 1: munitie en kleine delen.
    "brass": (0, 1), "copper": (1, 1), "lead": (2, 1), "spring": (3, 1), "white_dot": (4, 1),
    "marking": (5, 1), "rubber": (6, 1), "frame_steel": (7, 1),
    # Rij 2: leer en stof (holster en pouches), de ingots.
    "leather": (0, 2), "leather_dark": (1, 2), "stitch": (2, 2), "canvas": (3, 2), "canvas_dark": (4, 2),
    "buckle": (5, 2), "ingot_steel": (6, 2), "ingot_titanium": (7, 2),
    # Rij 3: de vlam uit de loop (gloeit), en de spring in titanium.
    "flash": (0, 3), "flash_edge": (1, 3), "spring_titanium": (2, 3), "frame_titanium": (3, 3),
}

SIZE = 128
TILE_UV = 16 * 16 / SIZE


def clamp(v):
    return max(0, min(255, int(v)))


def tile(im, material, top, bottom, noise=6, pattern=None, seed=0):
    """Eén vakje van 16 x 16: een verloop van {top} (boven) naar {bottom} (onder), met wat ruis en een patroon."""
    col, row = MATERIALS[material]
    rnd = random.Random(seed)
    for y in range(16):
        t = y / 15
        base = [top[i] + (bottom[i] - top[i]) * t for i in range(3)]
        for x in range(16):
            n = rnd.uniform(-noise, noise)
            c = [base[i] + n for i in range(3)]
            if pattern:
                c = pattern(x, y, c)
            im.putpixel((col * 16 + x, row * 16 + y), (clamp(c[0]), clamp(c[1]), clamp(c[2]), 255))


def materials():
    """Het plaatje met alle materialen (textures/item/firearms.png)."""
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    def gloss(x, y, c):
        # Gezwart staal met een glimlijntje bovenaan.
        if y == 1:
            return [v * 1.5 + 18 for v in c]
        return c

    def stipple(x, y, c):
        # Het reliëf van een polymeergreep: een raster van puntjes.
        return [v + 16 for v in c] if (x + y) % 2 == 0 and x % 2 == 1 else c

    def brushed(x, y, c):
        return [v + (9 if (y * 7 + x // 5) % 3 == 0 else -4) for v in c]

    def front_holes(x, y, c):
        # De voorkant van de slede: een groot gat (de loop, boven) en een klein gat (de veerstang, onder).
        if (x - 7.5) ** 2 + (y - 5) ** 2 <= 3.2 ** 2:
            return [14, 14, 16] if (x - 7.5) ** 2 + (y - 5) ** 2 <= 2.2 ** 2 else [150, 152, 158]
        if (x - 7.5) ** 2 + (y - 11.5) ** 2 <= 1.6 ** 2:
            return [20, 20, 22]
        return c

    def coil(x, y, c):
        # Een spiraalveer: schuine lichte windingen.
        return [v + 40 for v in c] if (x + y) % 4 == 0 else ([v - 18 for v in c] if (x + y) % 4 == 2 else c)

    def dot(x, y, c):
        d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
        core = (255, 255, 250)
        edge = (225, 228, 220)
        return [core[i] + (edge[i] - core[i]) * d for i in range(3)]

    def engraved(x, y, c):
        # Een lichter vlak met een "tekst" van streepjes (de opdruk GLOCK 19 AUSTRIA).
        if 3 <= y <= 5 and 2 <= x <= 13 and x % 2 == 0:
            return [v + 70 for v in c]
        if 8 <= y <= 9 and 3 <= x <= 12 and x % 3 != 2:
            return [v + 50 for v in c]
        return c

    def leather_grain(x, y, c):
        return [v + 10 for v in c] if (x * 3 + y * 5) % 7 == 0 else c

    def stitches(x, y, c):
        return [235, 215, 160] if y in (2, 13) and x % 3 != 0 else c

    def weave(x, y, c):
        return [v + 8 for v in c] if (x + y) % 2 == 0 else [v - 6 for v in c]

    def flash_core(x, y, c):
        d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
        core = (255, 252, 225)
        edge = (255, 200, 90)
        return [core[i] + (edge[i] - core[i]) * d for i in range(3)]

    def flash_rim(x, y, c):
        d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
        core = (255, 170, 60)
        edge = (255, 90, 20)
        return [core[i] + (edge[i] - core[i]) * d for i in range(3)]

    def ingot(x, y, c):
        # Een ingot: een lichte rand bovenaan en links, een donkere onderaan en rechts.
        if y <= 1 or x <= 1:
            return [v + 30 for v in c]
        if y >= 14 or x >= 14:
            return [v - 30 for v in c]
        return c

    tile(im, "polymer", (52, 52, 56), (36, 36, 40), 3, None, 1)
    tile(im, "polymer_grip", (50, 50, 54), (34, 34, 38), 3, stipple, 2)
    tile(im, "slide_black", (62, 64, 70), (30, 31, 36), 4, gloss, 3)
    tile(im, "slide_front", (58, 60, 66), (32, 33, 38), 3, front_holes, 4)
    tile(im, "port_dark", (22, 22, 26), (12, 12, 15), 2, None, 5)
    tile(im, "steel", (190, 196, 206), (128, 134, 146), 4, brushed, 6)
    tile(im, "titanium", (150, 162, 178), (96, 108, 126), 4, brushed, 7)
    tile(im, "titanium_front", (146, 158, 174), (98, 110, 128), 3, front_holes, 8)
    tile(im, "brass", (214, 172, 88), (158, 116, 44), 5, gloss, 9)
    tile(im, "copper", (196, 120, 72), (140, 78, 40), 5, gloss, 10)
    tile(im, "lead", (122, 126, 134), (84, 88, 96), 4, None, 11)
    tile(im, "spring", (170, 176, 186), (110, 116, 126), 3, coil, 12)
    tile(im, "white_dot", (0, 0, 0), (0, 0, 0), 0, dot, 13)
    tile(im, "marking", (60, 62, 68), (40, 42, 48), 3, engraved, 14)
    tile(im, "rubber", (40, 40, 44), (26, 26, 30), 3, stipple, 15)
    tile(im, "frame_steel", (120, 126, 136), (78, 84, 94), 4, brushed, 16)
    tile(im, "leather", (124, 82, 48), (84, 52, 28), 5, leather_grain, 17)
    tile(im, "leather_dark", (92, 58, 32), (58, 34, 18), 5, leather_grain, 18)
    tile(im, "stitch", (118, 78, 46), (82, 50, 26), 4, stitches, 19)
    tile(im, "canvas", (98, 104, 72), (68, 72, 48), 5, weave, 20)
    tile(im, "canvas_dark", (70, 74, 52), (46, 50, 34), 4, weave, 21)
    tile(im, "buckle", (168, 172, 180), (110, 114, 122), 4, None, 22)
    tile(im, "ingot_steel", (184, 190, 200), (124, 130, 142), 4, ingot, 23)
    tile(im, "ingot_titanium", (150, 162, 178), (94, 106, 124), 4, ingot, 24)
    tile(im, "flash", (0, 0, 0), (0, 0, 0), 0, flash_core, 25)
    tile(im, "flash_edge", (0, 0, 0), (0, 0, 0), 0, flash_rim, 26)
    tile(im, "spring_titanium", (150, 160, 176), (96, 106, 124), 3, coil, 27)
    tile(im, "frame_titanium", (142, 154, 170), (92, 104, 122), 4, brushed, 28)
    path = ASSETS / "textures" / "item" / "firearms.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ROOT))


# ---------------------------------------------------------------- Doosjes

def uv(material):
    col, row = MATERIALS[material]
    inset = TILE_UV * 0.04
    return [round(col * TILE_UV + inset, 4), round(row * TILE_UV + inset, 4),
            round((col + 1) * TILE_UV - inset, 4), round((row + 1) * TILE_UV - inset, 4)]


def box(frm, to, material, glow=False, rot=None, faces=None):
    """
    Eén doosje. rot = (as, hoek, middelpunt), bijvoorbeeld ("x", 22.5, (8, 9, 11)). faces = {"north": "slide_front"}:
    die kant krijgt een ander materiaal. glow=True: altijd vol licht (de vlam uit de loop, het stipje op de korrel).
    """
    faces = faces or {}
    face_map = {side: {"uv": uv(faces.get(side, material)), "texture": "#0"} for side in SIDES}
    element = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": face_map}
    if rot:
        axis, angle, origin = rot
        element["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
    if glow:
        element["shade"] = False
        for face in face_map.values():
            face["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return element


def mirror_x(element):
    e = json.loads(json.dumps(element))
    a, b = e["from"][0], e["to"][0]
    e["from"][0], e["to"][0] = round(16 - b, 3), round(16 - a, 3)
    if "rotation" in e:
        e["rotation"]["origin"][0] = 16 - e["rotation"]["origin"][0]
        if e["rotation"]["axis"] != "x":
            e["rotation"]["angle"] = -e["rotation"]["angle"]
    return e


def both(element):
    return [element, mirror_x(element)]


def write(rel, data):
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT))


def part_model(elements):
    return {"textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements}


def item_model(elements, display, overrides=None):
    model = {"gui_light": "front", "textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements,
             "display": display}
    if overrides:
        model["overrides"] = overrides
    return model


def display(gui_scale, gui_translation, hand_scale, ground_scale, gui_rotation=(25, -135, 0),
            third=((0, 180, 0), (0, 3, 1)), first=((0, 180, 0), (1, 3, 0)), first_scale=None):
    third_rot, third_pos = third
    first_rot, first_pos = first
    first_scale = hand_scale if first_scale is None else first_scale
    return {
        "gui": {"rotation": list(gui_rotation), "translation": list(gui_translation), "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [ground_scale] * 3},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [gui_scale] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": list(third_rot), "translation": list(third_pos), "scale": [hand_scale] * 3},
        "thirdperson_lefthand": {"rotation": list(third_rot), "translation": list(third_pos), "scale": [hand_scale] * 3},
        "firstperson_righthand": {"rotation": list(first_rot), "translation": list(first_pos), "scale": [first_scale] * 3},
        "firstperson_lefthand": {"rotation": list(first_rot), "translation": list(first_pos), "scale": [first_scale] * 3},
    }


def gun_display(gui_scale, gui_translation, ground_scale, grip):
    """De houdingen van een pistool: zoals de lasergeweren (zie generate_weapon_models.gun_display)."""
    hand = 0.75
    turn = -17
    a = math.radians(turn)
    gx, gy, gz = (hand * (g - 8) for g in grip)
    rx = gx * math.cos(a) + gz * math.sin(a)
    rz = -gx * math.sin(a) + gz * math.cos(a)
    third = ((0, turn, 0), (round(-rx, 2), round(-gy, 2), round(-rz, 2)))
    return display(gui_scale, gui_translation, hand, ground_scale, gui_rotation=(0, 90, 0), third=third)


def centre_of(elements):
    """Het midden van een lijst doosjes (zonder hun draaiing)."""
    xs = [v for e in elements for v in (e["from"][0], e["to"][0])]
    ys = [v for e in elements for v in (e["from"][1], e["to"][1])]
    zs = [v for e in elements for v in (e["from"][2], e["to"][2])]
    return ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2)


def centred_display(elements, gui_scale, gui_rotation=(30, 45, 0), hand_scale=0.6, ground_scale=0.5):
    """Een houding die een los onderdeel in het midden van zijn vakje zet (het onderdeel ligt waar het in het pistool zit)."""
    cx, cy, cz = centre_of(elements)
    shift = [round(-(cx - 8) * gui_scale, 2), round(-(cy - 8) * gui_scale, 2), round(-(cz - 8) * gui_scale, 2)]
    # De verschuiving is in de ruimte NA de draaiing; voor een rechte draaiing om y van 45 graden is dat ongeveer goed
    # genoeg (de onderdelen zijn klein). Voor de zijaanzichten (0, 90, 0) klopt het precies.
    hand_shift = [round(-(cx - 8) * hand_scale, 2), round(-(cy - 8) * hand_scale, 2), round(-(cz - 8) * hand_scale, 2)]
    disp = display(gui_scale, shift, hand_scale, ground_scale, gui_rotation=gui_rotation,
                   third=((0, 0, 0), hand_shift), first=((0, 0, 0), hand_shift))
    disp["ground"]["translation"] = [0, 2 - (cy - 8) * ground_scale, 0]
    return disp


# ---------------------------------------------------------------- De Glock 19

GRIP = ("x", 22.5, (8, 9.3, 11.0))  # de greep leunt 22,5 graden naar achteren, om dit punt (bovenaan de greep)

PART_MATERIALS = {
    "frame": {"polymer": "polymer", "steel": "frame_steel", "titanium": "frame_titanium"},
    "slide": {"steel": "slide_black", "titanium": "titanium"},
    "barrel": {"steel": "steel", "titanium": "titanium"},
    "springs": {"steel": "spring", "titanium": "spring_titanium"},
    "striker": {"steel": "steel", "titanium": "titanium"},
    "trigger": {"polymer": "polymer", "steel": "steel", "titanium": "titanium"},
}


def slide(m):
    """De slede: vierkant, met de uitwerpopening rechts, groeven achteraan, korrel en keep, en de voorkant met gaten."""
    front = "titanium_front" if m == "titanium" else "slide_front"
    el = [
        box((6.85, 10.55, 0.7), (9.15, 12.95, 14.45), m, faces={"north": front}),
        box((7.1, 12.95, 0.9), (8.9, 13.1, 14.3), m),
        # Groeven achteraan, aan beide kanten.
        *[e for z in (11.3, 11.85, 12.4, 12.95, 13.5) for e in both(box((6.8, 10.9, z), (6.86, 12.6, z + 0.22), "port_dark"))],
        # De uitwerpopening (rechts, bovenaan) met daarin de glimmende loop-hood (zie barrel()).
        box((8.9, 11.9, 2.7), (9.17, 12.9, 5.3), "port_dark"),
        box((8.0, 12.9, 2.7), (9.17, 12.97, 5.3), "port_dark"),
        # De uitwerper.
        box((9.15, 11.55, 5.4), (9.24, 12.05, 6.6), m),
        # Korrel met een wit stipje.
        box((7.75, 13.1, 1.3), (8.25, 13.7, 1.75), m),
        box((7.85, 13.28, 1.24), (8.15, 13.56, 1.3), "white_dot", glow=True),
        # Keep: twee palen op een voetje, met een witte U achterop.
        box((7.0, 13.1, 13.0), (9.0, 13.32, 13.6), m),
        box((7.0, 13.3, 13.0), (7.55, 13.75, 13.6), m),
        box((8.45, 13.3, 13.0), (9.0, 13.75, 13.6), m),
        box((7.06, 13.36, 13.6), (7.5, 13.7, 13.66), "white_dot", glow=True),
        box((8.5, 13.36, 13.6), (8.94, 13.7, 13.66), "white_dot", glow=True),
        box((7.06, 13.3, 13.6), (8.94, 13.4, 13.66), "white_dot", glow=True),
        # Het afdekplaatje achteraan.
        box((7.1, 10.7, 14.45), (8.9, 12.8, 14.55), "port_dark"),
        # De opdruk links.
        box((6.8, 11.3, 6.2), (6.85, 12.2, 10.4), "marking"),
    ]
    return el


def barrel(m):
    """De loop: steekt een stukje uit de voorkant van de slede, en zijn hood glimt in de uitwerpopening."""
    return [
        box((7.55, 11.25, 0.3), (8.45, 12.2, 8.6), m),
        box((7.6, 11.3, 8.6), (8.4, 12.15, 9.6), m),
        box((9.17, 12.1, 3.0), (9.22, 12.8, 5.0), m),
    ]


def springs(m):
    """De terugslagveer op zijn stang: onder de loop, de stang steekt uit het kleine gat vooraan."""
    return [
        box((7.75, 10.8, 0.45), (8.25, 11.2, 8.0), m),
        box((7.6, 10.7, 0.9), (8.4, 11.3, 4.0), m),
    ]


def striker(m):
    """De slagpin: in de slede, achter de kamer; zijn staartje steekt uit het afdekplaatje."""
    return [
        box((7.75, 11.4, 9.7), (8.25, 11.9, 14.2), m),
        box((7.85, 11.45, 14.55), (8.15, 11.85, 14.7), "port_dark"),
    ]


def frame(m, material_name):
    """Het frame: de stofkap met de rail, het stuk boven de trekker, de trekkerbeugel en de schuine greep."""
    grip_side = "polymer_grip" if material_name == "polymer" else m
    el = [
        # Stofkap onder de slede, met de rail (drie ribbels en twee randjes).
        box((6.95, 9.55, 0.95), (9.05, 10.55, 8.3), m),
        *[box((7.2, 9.2, z), (8.8, 9.55, z + 0.5), m) for z in (2.2, 3.4, 4.6)],
        *both(box((6.95, 9.0, 1.6), (7.25, 9.55, 5.6), m)),
        # Boven de trekker, tot de achterkant.
        box((6.95, 9.3, 8.3), (9.05, 10.55, 14.2), m),
        # Trekkerbeugel: onderste balk, voorste paal, en de vingersteun vooraan.
        box((7.55, 7.1, 6.1), (8.45, 7.7, 10.4), m),
        box((7.55, 7.1, 6.1), (8.45, 9.55, 6.7), m),
        box((7.5, 7.0, 5.7), (8.5, 7.5, 6.1), m),
        # De greep, schuin naar achteren.
        box((6.95, 3.2, 10.5), (9.05, 9.3, 13.4), m, rot=GRIP),
        *[box((7.1, y, 10.35), (8.9, y + 0.8, 10.5), m, rot=GRIP) for y in (4.1, 5.5, 6.9)],
        *both(box((6.9, 3.7, 10.9), (6.95, 8.4, 13.1), grip_side, rot=GRIP)),
        box((6.95, 3.0, 10.5), (9.05, 3.2, 13.4), "port_dark", rot=GRIP),
        # Sledestop (links) en de demontagehendel (beide kanten).
        box((6.85, 10.6, 8.9), (6.9, 10.95, 10.6), "port_dark"),
        *both(box((6.9, 9.7, 6.5), (6.95, 9.95, 8.0), "port_dark")),
    ]
    return el


def trigger(m):
    """De trekker met het veiligheidslipje erin."""
    return [
        box((7.7, 7.8, 8.6), (8.3, 9.3, 9.05), m),
        box((7.9, 8.0, 8.5), (8.1, 8.9, 8.6), "steel"),
    ]


def magazine():
    """Het magazijn: een polymeer doos in de greep, de bodemplaat die eronder uitsteekt, een patroon bovenin."""
    return [
        box((7.2, 3.25, 10.85), (8.8, 9.0, 13.05), "polymer", rot=GRIP),
        box((6.85, 2.55, 10.4), (9.15, 3.25, 13.6), "polymer", rot=GRIP),
        box((7.5, 9.0, 11.2), (8.5, 9.35, 12.6), "brass", rot=GRIP),
        *[box((7.8, y, 13.05), (8.2, y + 0.35, 13.1), "brass", rot=GRIP) for y in (4.3, 5.4, 6.5, 7.6)],
    ]


PARTS = {"frame": frame, "slide": slide, "barrel": barrel, "springs": springs, "striker": striker, "trigger": trigger}


def part_elements(part, material_name):
    m = PART_MATERIALS[part][material_name]
    if part == "frame":
        return frame(m, material_name)
    return PARTS[part](m)


GLOCK_GRIP = (8, 6.2, 12.4)


def glock_19():
    """Alle onderdelen per materiaal, het magazijn, en het hele pistool als item (in staal, met magazijn)."""
    for part, materials_of in PART_MATERIALS.items():
        for material_name in materials_of:
            write(f"models/firearm/glock_19/{part}_{material_name}.json", part_model(part_elements(part, material_name)))
    write("models/firearm/glock_19/magazine.json", part_model(magazine()))
    whole = []
    for part in PARTS:
        whole += part_elements(part, "steel")
    whole += magazine()
    write("models/item/glock_19.json", item_model(whole, gun_display(1.0, (0, 0.5, 0), 0.6, GLOCK_GRIP)))
    # Het magazijn als item: op zijn kant in je inventaris.
    disp = centred_display(magazine(), 1.6, gui_rotation=(0, 90, 0), hand_scale=0.6)
    write("models/item/glock_19_magazine.json", item_model(magazine(), disp))
    # De losse onderdelen als item, met een override per materiaal (0 = polymeer, 1 = staal, 2 = titanium).
    gui_rot = {"frame": (0, 90, 0), "slide": (0, 90, 0), "barrel": (0, 90, 0), "springs": (0, 90, 0),
               "striker": (0, 90, 0), "trigger": (0, 90, 0)}
    scale = {"frame": 1.1, "slide": 1.0, "barrel": 1.5, "springs": 1.6, "striker": 2.2, "trigger": 4.0}
    for part, materials_of in PART_MATERIALS.items():
        overrides = []
        for index, material_name in enumerate(("polymer", "steel", "titanium")):
            if material_name not in materials_of:
                continue
            elements = part_elements(part, material_name)
            disp = centred_display(elements, scale[part], gui_rotation=gui_rot[part])
            write(f"models/item/gun_{part}_{material_name}.json", item_model(elements, disp))
            overrides.append({"predicate": {"imttech:material": index}, "model": f"imttech:item/gun_{part}_{material_name}"})
        base = part_elements(part, "steel")
        write(f"models/item/gun_{part}.json",
              item_model(base, centred_display(base, scale[part], gui_rotation=gui_rot[part]), overrides))


# ---------------------------------------------------------------- Munitie

def round_9mm():
    """Een 9mm-patroon: een messing huls met een koperen kogelpunt, rechtop."""
    return [
        box((7.3, 6.3, 7.3), (8.7, 6.55, 8.7), "brass"),
        box((7.4, 6.55, 7.4), (8.6, 9.0, 8.6), "brass"),
        box((7.55, 9.0, 7.55), (8.45, 9.9, 8.45), "copper"),
        box((7.75, 9.9, 7.75), (8.25, 10.3, 8.25), "copper"),
    ]


def casing_9mm():
    """Een lege huls: de messing buis, van boven open (donker)."""
    return [
        box((7.3, 6.3, 7.3), (8.7, 6.55, 8.7), "brass"),
        box((7.4, 6.55, 7.4), (8.6, 9.0, 8.6), "brass", faces={"up": "port_dark"}),
    ]


def bullet_9mm():
    """De kogelpunt alleen: koper met een loden voet."""
    return [
        box((7.55, 7.4, 7.55), (8.45, 8.5, 8.45), "copper", faces={"down": "lead"}),
        box((7.75, 8.5, 7.75), (8.25, 8.95, 8.25), "copper"),
    ]


def ammo():
    small = dict(gui_rotation=(20, -35, 0), hand_scale=0.45, ground_scale=0.5)
    write("models/item/round_9mm.json", item_model(round_9mm(), centred_display(round_9mm(), 2.6, **small)))
    write("models/item/casing_9mm.json", item_model(casing_9mm(), centred_display(casing_9mm(), 2.6, **small)))
    write("models/item/bullet_9mm.json", item_model(bullet_9mm(), centred_display(bullet_9mm(), 3.2, **small)))
    # De huls op de grond (zie client/guns/CasingRenderer) gebruikt het losse model.
    write("models/firearm/casing.json", part_model(casing_9mm()))
    write("models/firearm/round.json", part_model(round_9mm()))


# ---------------------------------------------------------------- Holster en pouches (hoofdstuk 52, stap 2)

def holster():
    """Een leren holster: een zak met een flap en stiksels, en een lus voor je riem."""
    return [
        box((5.6, 3.0, 6.6), (10.4, 11.6, 9.4), "leather"),
        *both(box((5.55, 3.4, 6.9), (5.6, 11.2, 9.1), "stitch")),
        box((5.3, 10.6, 6.3), (10.7, 12.4, 9.7), "leather_dark"),
        box((7.4, 11.9, 6.0), (8.6, 12.6, 6.3), "buckle"),
        box((6.4, 7.5, 9.4), (9.6, 12.0, 10.1), "leather_dark"),
        box((7.0, 2.5, 7.1), (9.0, 3.0, 8.9), "leather_dark"),
    ]


def grenade_pouch():
    """Een stoffen granaatpouch: een brede zak met twee flappen en twee knopen."""
    return [
        box((4.6, 3.6, 6.4), (11.4, 10.8, 9.6), "canvas"),
        box((4.4, 9.8, 6.2), (7.9, 11.4, 9.8), "canvas_dark"),
        box((8.1, 9.8, 6.2), (11.6, 11.4, 9.8), "canvas_dark"),
        box((5.8, 10.3, 5.9), (6.6, 11.0, 6.2), "buckle"),
        box((9.4, 10.3, 5.9), (10.2, 11.0, 6.2), "buckle"),
        box((5.5, 7.0, 9.6), (10.5, 11.0, 10.2), "canvas_dark"),
    ]


def mag_pouch():
    """Een magazijnpouch: drie smalle vakjes naast elkaar, elk met een klepje."""
    el = [box((5.0, 7.0, 9.4), (11.0, 11.0, 10.0), "canvas_dark")]
    for x0 in (4.9, 7.0, 9.1):
        el += [
            box((x0, 3.5, 6.6), (x0 + 2.0, 10.6, 9.4), "canvas"),
            box((x0 - 0.1, 9.9, 6.4), (x0 + 2.1, 11.2, 9.6), "canvas_dark"),
            box((x0 + 0.7, 10.2, 6.1), (x0 + 1.3, 10.8, 6.4), "buckle"),
        ]
    return el


def gear():
    for name, make in (("holster", holster), ("grenade_pouch", grenade_pouch), ("mag_pouch", mag_pouch)):
        write(f"models/firearm/{name}.json", part_model(make()))
        write(f"models/item/{name}.json", item_model(make(), centred_display(make(), 1.0, gui_rotation=(25, -40, 0))))


# ---------------------------------------------------------------- Staven, poeders en korrels

def ingot(material):
    return [
        box((4, 6.6, 5.5), (12, 8.5, 10.5), material),
        box((4.7, 8.5, 6.2), (11.3, 9.1, 9.8), material),
    ]


def resources():
    for name, material in (("steel_ingot", "ingot_steel"), ("titanium_ingot", "ingot_titanium")):
        write(f"models/item/{name}.json", item_model(ingot(material), centred_display(ingot(material), 1.0, gui_rotation=(30, 45, 0))))
    # Poeder (de blend die je smelt) en korrels (polymeer): platte plaatjes van 16 x 16.
    for name, colour, shade in (("steel_blend", (120, 118, 112), (70, 68, 64)), ("titanium_blend", (132, 140, 150), (74, 82, 94))):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        rnd = random.Random(name)
        for y in range(6, 15):
            half = min(7, 1 + (y - 6))
            for x in range(max(0, 8 - half), min(16, 8 + half)):
                c = colour if rnd.random() < 0.8 else shade
                im.putpixel((x, y), (*c, 255))
        for _ in range(10):
            x, y = rnd.randint(3, 12), rnd.randint(6, 14)
            im.putpixel((x, y), (*[min(255, v + 40) for v in colour], 255))
        save_item_texture(im, name)
        write(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"imttech:item/{name}"}})
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random("polymer")
    for cx, cy in ((4, 5), (8, 4), (12, 6), (5, 9), (9, 8), (12, 11), (6, 13), (10, 12), (3, 12)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) < 2 and 0 <= cx + dx < 16 and 0 <= cy + dy < 16:
                    v = 60 + rnd.randint(-8, 8) + (14 if dx == -1 or dy == -1 else 0)
                    im.putpixel((cx + dx, cy + dy), (v, v, v + 4, 255))
    save_item_texture(im, "polymer")
    write("models/item/polymer.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "imttech:item/polymer"}})


def save_item_texture(im, name):
    path = ASSETS / "textures" / "item" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ROOT))


# ---------------------------------------------------------------- De blokken

def save_block_texture(im, name):
    path = ASSETS / "textures" / "block" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ROOT))


def orientable_block(name):
    """Blockstate, blokmodel (vier kanten: front, side, top) en het item ervan, zoals de Robot Assembler."""
    blockstate = {"variants": {}}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        variant = {"model": f"imttech:block/{name}"}
        if y:
            variant["y"] = y
        blockstate["variants"][f"facing={facing}"] = variant
    write(f"blockstates/{name}.json", blockstate)
    write(f"models/block/{name}.json", {"parent": "minecraft:block/orientable", "textures": {
        "front": f"imttech:block/{name}_front", "side": f"imttech:block/{name}_side", "top": f"imttech:block/{name}_top"}})
    write(f"models/item/{name}.json", {"parent": f"imttech:block/{name}"})


def gunsmith_table():
    """De Gunsmith Table: een stalen werkbank met een donker werkblad met het silhouet van een pistool en gereedschap."""
    steel = (78, 86, 96, 255)
    steel_dark = (44, 50, 58, 255)
    steel_light = (112, 122, 134, 255)
    side = Image.new("RGBA", (16, 16), steel)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    d.line([1, 1, 14, 1], fill=steel_light)
    d.rectangle([2, 4, 13, 12], outline=steel_dark)
    for y in (6, 8, 10):
        d.line([3, y, 12, y], fill=(60, 66, 76, 255))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        side.putpixel((x, y), (150, 160, 172, 255))
    save_block_texture(side, "gunsmith_table_side")
    front = side.copy()
    d = ImageDraw.Draw(front)
    d.rectangle([2, 4, 13, 12], fill=(30, 34, 40, 255), outline=steel_dark)
    # Een laatje met een handvat, en een geel-zwart randje.
    d.rectangle([3, 5, 12, 8], outline=(96, 104, 116, 255))
    d.line([6, 7, 9, 7], fill=(190, 196, 206, 255))
    for x in range(3, 13):
        front.putpixel((x, 11), (230, 190, 40, 255) if x % 2 == 0 else (30, 30, 34, 255))
    save_block_texture(front, "gunsmith_table_front")
    top = Image.new("RGBA", (16, 16), (40, 44, 50, 255))
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=steel_dark)
    d.rectangle([1, 1, 14, 14], outline=(58, 64, 72, 255))
    # Een pistool van opzij in lichtgrijs: slede, greep en trekkerbeugel.
    gun = [(3, 5), (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5),
           (3, 6), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
           (9, 7), (10, 7), (11, 7), (10, 8), (11, 8), (10, 9), (11, 9), (11, 10),
           (6, 7), (6, 8), (7, 8)]
    for x, y in gun:
        top.putpixel((x, y), (168, 176, 188, 255))
    # Een schroevendraaier en een pons rechts/onder.
    d.line([2, 12, 7, 12], fill=(200, 120, 50, 255))
    d.line([8, 12, 11, 12], fill=(190, 196, 206, 255))
    d.line([13, 3, 13, 9], fill=(190, 196, 206, 255))
    save_block_texture(top, "gunsmith_table_top")
    orientable_block("gunsmith_table")


def reloading_bench():
    """De Reloading Bench: een houten werkbank met een messing pers bovenop en laatjes met patronen."""
    wood = (134, 100, 60, 255)
    wood_dark = (90, 64, 36, 255)
    wood_light = (160, 124, 80, 255)
    side = Image.new("RGBA", (16, 16), wood)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=wood_dark)
    for y in (4, 8, 12):
        d.line([1, y, 14, y], fill=wood_dark)
    d.line([1, 1, 14, 1], fill=wood_light)
    save_block_texture(side, "reloading_bench_side")
    front = side.copy()
    d = ImageDraw.Draw(front)
    # Twee laatjes met messing knopjes, en patronen op een rij in het bovenste.
    d.rectangle([2, 5, 13, 9], fill=wood_light, outline=wood_dark)
    d.rectangle([2, 10, 13, 14], fill=wood_light, outline=wood_dark)
    for x in range(4, 12, 2):
        front.putpixel((x, 7), (214, 172, 88, 255))
        front.putpixel((x, 6), (196, 120, 72, 255))
    front.putpixel((7, 12), (214, 172, 88, 255))
    front.putpixel((8, 12), (214, 172, 88, 255))
    save_block_texture(front, "reloading_bench_front")
    top = Image.new("RGBA", (16, 16), wood_light)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=wood_dark)
    for x in (5, 10):
        d.line([x, 1, x, 14], fill=wood)
    # De pers: een messing voet, een stalen kolom en de hendel; ernaast een blikje buskruit en losse hulzen.
    d.rectangle([2, 2, 7, 7], fill=(214, 172, 88, 255), outline=(158, 116, 44, 255))
    d.rectangle([4, 3, 5, 6], fill=(150, 156, 166, 255))
    d.line([6, 2, 9, 1], fill=(60, 62, 68, 255))
    d.rectangle([10, 3, 13, 6], fill=(50, 50, 54, 255), outline=(30, 30, 34, 255))
    for x, y in ((3, 11), (5, 12), (8, 10), (10, 12), (12, 11), (7, 13)):
        top.putpixel((x, y), (214, 172, 88, 255))
    save_block_texture(top, "reloading_bench_top")
    orientable_block("reloading_bench")


# ---------------------------------------------------------------- De plaatjes van de Curios-slots

def slot_icons():
    """Twee grijze silhouetten op een doorzichtige achtergrond, voor de lege Curios-slots "holster" en "pouch"."""
    grey = (139, 139, 139, 255)
    holster = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in [(3, 5), (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5), (12, 5),
                 (3, 6), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6), (12, 6),
                 (9, 7), (10, 7), (11, 7), (10, 8), (11, 8), (10, 9), (11, 9), (11, 10), (6, 7), (7, 8)]:
        holster.putpixel((x, y), grey)
    path = ASSETS / "textures" / "slot" / "holster.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    holster.save(path)
    print("geschreven:", path.relative_to(ROOT))
    pouch = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(pouch)
    d.rectangle([3, 5, 12, 13], outline=grey)
    d.rectangle([2, 3, 13, 6], outline=grey)
    d.line([7, 7, 7, 8], fill=grey)
    d.line([8, 7, 8, 8], fill=grey)
    path = ASSETS / "textures" / "slot" / "pouch.png"
    pouch.save(path)
    print("geschreven:", path.relative_to(ROOT))


# ---------------------------------------------------------------- Alles

def main():
    materials()
    glock_19()
    ammo()
    gear()
    resources()
    gunsmith_table()
    reloading_bench()
    slot_icons()


if __name__ == "__main__":
    main()
