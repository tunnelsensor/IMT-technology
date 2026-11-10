"""
Maakt de 3D-modellen en plaatjes van de vuurwapens (hoofdstuk 52): de Glock 19 en zijn zes onderdelen (elk in zijn
materialen), het magazijn, de 9mm-patroon, de huls en de kogelpunt, de holster en de twee pouches, de staven en
poeders van staal en titanium en de polymeerkorrels, en de twee blokken (de Gunsmith Table en de Reloading Bench).

De holster, de pouches, de staven en de blokken zijn json-modellen: een lijstje doosjes in pixels (16 = één blok), elk
doosje krijgt een MATERIAAL uit één plaatje van 128 x 128 (textures/item/firearms.png), zoals
tools/generate_weapon_models.py (de lasergeweren).

De Glock 19, zijn magazijn en de munitie zijn sinds hoofdstuk 74 een echt 3D-model in de stijl van TACZ: OBJ-bestanden
met afgeronde randen, een ronde loop, een greep met vingergroeven en een texture met leesbare opschriften. Dat model
zelf (de vormen, de maten van een foto van een echte Glock 19 Gen 3, de kleuren) staat in tools/glock19_model.py, de
bouwdoos in tools/gunmesh.py; met tools/preview_obj.py bekijk je het zonder het spel te starten. Dit script schrijft de
json-bestandjes die Minecraft ervan inlaadt.

Een onderdeel bestaat per materiaal (polymeer, staal, titanium): models/firearm/glock_19/<onderdeel>_<materiaal>.json
(hetzelfde OBJ-bestand, een ander plaatje). In je eigen beeld en op de Gunsmith Table tekent de mod ze los (zie
client/guns/FirearmRenderer en GunsmithScreen); voor je inventaris, je hand in derde persoon en op de grond is er het
hele pistool in één bestand (models/item/glock_19.json: polymeer frame, stalen slede, vol magazijn). De losse
onderdelen als item (models/item/gun_slide.json enz.) kiezen hun materiaal met een "override" (zie
client/guns/FirearmModels.registerProperties), het magazijn kiest zo zijn lege model. Hoe zo een onderdeel in zijn
vakje staat rekent tools/model_display.py uit (hoofdstuk 59).

Draaien (vanuit de hoofdmap):  python tools/generate_gun_models.py
Het script is eigenaar van alles wat het schrijft; een volledige run is veilig.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

import glock19_model as glock
import gunmesh as gm
import model_display as md

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


def gun_display(points, grip, gui_rotation=(4, -90, 0), ground_scale=0.6):
    """
    De houdingen van het hele pistool. In je inventaris de rechterkant (de loop naar rechts, zoals op de foto), een
    tikje van boven; in je hand (derde persoon) met de greep in je hand, zoals de lasergeweren
    (generate_weapon_models.gun_display).
    """
    hand = 0.75
    turn = -17
    a = math.radians(turn)
    gx, gy, gz = (hand * (g - 8) for g in grip)
    rx = gx * math.cos(a) + gz * math.sin(a)
    rz = -gx * math.sin(a) + gz * math.cos(a)
    third = ((0, turn, 0), (round(-rx, 2), round(-gy, 2), round(-rz, 2)))
    elements = glock.points_as_elements(points)
    gui_scale, shift = md.fit(elements, gui_rotation, size=15.6, max_scale=1.2)
    disp = display(gui_scale, shift, hand, ground_scale, gui_rotation=gui_rotation, third=third)
    disp["fixed"] = {"rotation": [0, -90, 0], "translation": md.fit(elements, (0, -90, 0), scale=gui_scale)[1],
                     "scale": [gui_scale] * 3}
    return disp


def centred_display(elements, gui_rotation=md.PART_VIEW, scale=None, size=14.0, max_scale=4.5, hand_scale=0.6,
                    ground_scale=0.5):
    """
    Een houding die een los onderdeel precies in het midden van zijn vakje zet, zo groot als er past (of op {scale}).
    De verschuiving wordt NA de draaiing uitgerekend (zie model_display.fit): vroeger schoven we het onderdeel terug
    langs de assen van het model, en zo stond alles wat in het pistool niet rond z = 8 zit (de loop, de veer, de
    slagpin) naast of buiten zijn vakje (hoofdstuk 59). In je hand (derde persoon) en op de grond ligt het onderdeel
    recht, in het midden; in een itemlijst ("fixed") zoals in je inventaris.
    """
    gui_scale, shift = md.fit(elements, gui_rotation, size=size, max_scale=max_scale, scale=scale)
    _, hand_shift = md.fit(elements, (0, 0, 0), scale=hand_scale)
    _, ground_shift = md.fit(elements, (0, 0, 0), scale=ground_scale)
    disp = display(gui_scale, shift, hand_scale, ground_scale, gui_rotation=gui_rotation,
                   third=((0, 0, 0), hand_shift), first=((0, 0, 0), hand_shift))
    disp["fixed"] = dict(disp["gui"])
    disp["ground"]["translation"] = [ground_shift[0], round(2 + ground_shift[1], 3), ground_shift[2]]
    return disp


# ---------------------------------------------------------------- De Glock 19 (hoofdstuk 74: een echt 3D-model)

# Het model zelf (de vormen, de maten van de foto, de texture) staat in tools/glock19_model.py; hier schrijven we de
# json-bestandjes die Minecraft ervan inlaadt.
GLOCK_TEX = "imttech:item/glock_19/"
GLOCK_GRIP = (8, 7.8, 11.4)  # waar je hand de greep vasthoudt (derde persoon)


def obj_model(obj, textures, display=None, overrides=None, gui_light="front"):
    """
    Een json dat een OBJ-model inlaadt met NeoForge's "obj"-lader. {textures}: de namen uit glock_19.mtl ("gun",
    "polymer", "steel", "mag") en hun plaatje. Daarna gedraagt het zich als elk ander model (houdingen, overrides).
    """
    model = {"loader": "neoforge:obj", "model": f"imttech:models/firearm/glock_19/{obj}.obj",
             "automatic_culling": False, "flip_v": False, "emissive_ambient": True,
             "textures": dict(textures, particle=list(textures.values())[0])}
    if gui_light:
        model["gui_light"] = gui_light
    if display:
        model["display"] = display
    if overrides:
        model["overrides"] = overrides
    return model


def euler_xyz(m):
    """De hoeken (x, y, z) in graden waarmee Minecraft deze draaiing maakt (een houding draait Rx * Ry * Rz)."""
    b = math.degrees(math.asin(max(-1.0, min(1.0, m[0][2]))))
    a = math.degrees(math.atan2(-m[1][2], m[2][2]))
    c = math.degrees(math.atan2(-m[0][1], m[0][0]))
    return round(a, 2), round(b, 2), round(c, 2)


def magazine_display(points):
    """
    Het magazijn in je inventaris: rechtop (niet schuin zoals in de greep) en een beetje gedraaid, zodat je de getallen
    achterop ziet.
    """
    view = gm.rot_x(10) @ gm.rot_y(-72) @ gm.rot_x(glock.GRIP_ANGLE)
    rotation = euler_xyz(view)
    return centred_display(glock.points_as_elements(points), gui_rotation=rotation, size=14.5, max_scale=2.2,
                           hand_scale=0.55, ground_scale=0.45)


def glock_19():
    """
    De Glock 19: elk onderdeel als OBJ (models/firearm/glock_19/*.obj), drie plaatjes (polymeer, staal, titanium), het
    magazijn (vol en leeg) en de munitie met hun eigen plaatje, en de json-bestandjes voor elk onderdeel per materiaal,
    het hele pistool als item, het magazijn als item en de losse onderdelen als item.
    """
    parts, mags, ammo_meshes, gun_islands, mag_islands = glock.build()
    glock.write_textures(ASSETS, gun_islands, mag_islands)
    counts = glock.write_objs(ASSETS, parts, mags, ammo_meshes)
    print("vlakken per model:", counts)
    for part, materials_of in glock.PART_MATERIALS.items():
        for material in materials_of:
            write(f"models/firearm/glock_19/{part}_{material}.json",
                  obj_model(part, {"gun": GLOCK_TEX + material}, gui_light=None))
    for name in ("magazine", "magazine_empty"):
        write(f"models/firearm/glock_19/{name}.json", obj_model(name, {"mag": GLOCK_TEX + "magazine"}, gui_light=None))
    whole = [p for m in parts.values() for p in m.points()] + mags["magazine"].points()
    write("models/item/glock_19.json", obj_model(
        "whole", {"polymer": GLOCK_TEX + "polymer", "steel": GLOCK_TEX + "steel", "mag": GLOCK_TEX + "magazine"},
        display=gun_display(whole, GLOCK_GRIP)))
    mag_disp = magazine_display(mags["magazine"].points())
    write("models/item/glock_19_magazine_empty.json",
          obj_model("magazine_empty", {"mag": GLOCK_TEX + "magazine"}, display=mag_disp))
    write("models/item/glock_19_magazine.json", obj_model(
        "magazine", {"mag": GLOCK_TEX + "magazine"}, display=mag_disp,
        overrides=[{"predicate": {"imttech:empty": 1}, "model": "imttech:item/glock_19_magazine_empty"}]))
    # De losse onderdelen als item, met een override per materiaal (0 = polymeer, 1 = staal, 2 = titanium): schuin
    # van rechtsboven (model_display.PART_VIEW) en zo groot als er in het vakje past (hoofdstuk 59).
    for part, materials_of in glock.PART_MATERIALS.items():
        disp = centred_display(glock.points_as_elements(parts[part].points()))
        overrides = []
        for index, material in enumerate(("polymer", "steel", "titanium")):
            if material not in materials_of:
                continue
            write(f"models/item/gun_{part}_{material}.json", obj_model(part, {"gun": GLOCK_TEX + material}, display=disp))
            overrides.append({"predicate": {"imttech:material": index}, "model": f"imttech:item/gun_{part}_{material}"})
        write(f"models/item/gun_{part}.json", obj_model(part, {"gun": GLOCK_TEX + "steel"}, display=disp,
                                                       overrides=overrides))
    # Munitie: de patroon, de huls en de kogel als item, en de losse modellen voor in je hand en op de grond
    # (FirearmRenderer, CasingRenderer).
    small = dict(gui_rotation=(20, -35, 0), hand_scale=0.45, ground_scale=0.5)
    for item, obj, max_scale in (("round_9mm", "round", 5.5), ("casing_9mm", "casing", 6.5),
                                 ("bullet_9mm", "bullet", 8.0)):
        disp = centred_display(glock.points_as_elements(ammo_meshes[obj].points()), size=12.5, max_scale=max_scale,
                               **small)
        write(f"models/item/{item}.json", obj_model(obj, {"mag": GLOCK_TEX + "magazine"}, display=disp))
    for obj in ("casing", "round"):
        write(f"models/firearm/{obj}.json", obj_model(obj, {"mag": GLOCK_TEX + "magazine"}, gui_light=None))
    # De maten voor FirearmRig (Java): de doos om elk onderdeel.
    for name, (lo, hi) in glock.rig(parts, mags).items():
        print(f"  {name:9s} van {[round(float(v), 2) for v in lo]} tot {[round(float(v), 2) for v in hi]}")


# ---------------------------------------------------------------- Holster en pouches (hoofdstuk 52, stap 2)

def holster():
    """
    Een leren holster (hoofdstuk 54: mooier): een gevormde zak die naar beneden smaller wordt (de loop zit onderin),
    stiksels langs de rand, een band met een drukknoop over de greep, en een lus voor je riem aan de achterkant.
    """
    return [
        box((5.6, 6.2, 6.6), (10.4, 11.6, 9.4), "leather"),
        box((6.1, 3.0, 6.9), (9.9, 6.2, 9.1), "leather"),
        box((6.5, 2.4, 7.1), (9.5, 3.0, 8.9), "leather_dark"),
        *both(box((5.55, 6.5, 6.9), (5.6, 11.2, 9.1), "stitch")),
        *both(box((6.05, 3.3, 7.1), (6.1, 5.9, 8.9), "stitch")),
        # De band over de greep, met de drukknoop.
        box((5.3, 10.4, 6.3), (10.7, 11.6, 9.7), "leather_dark"),
        box((7.3, 10.7, 6.05), (8.7, 11.3, 6.3), "buckle"),
        # De riemlus achter.
        box((6.4, 7.5, 9.4), (9.6, 12.0, 10.1), "leather_dark"),
        box((6.6, 8.0, 10.1), (9.4, 11.5, 10.35), "leather"),
    ]


# Waar de granaten in de granaatpouch zitten (het midden van elk vakje, model-pixels; zie client/guns/GearLayer):
# twee naast elkaar (x) en twee achter elkaar (z), met hun bovenkant boven de rand uit.
GRENADE_POCKETS = [(6.4, 9.7, 6.7), (9.6, 9.7, 6.7), (6.4, 9.7, 9.3), (9.6, 9.7, 9.3)]
# Waar de magazijnen in de magazijnpouch staan (het midden van de voet van elk vakje; ze steken erbovenuit): sinds
# hoofdstuk 58 zes vakjes, drie naast elkaar (x) in twee rijen (z).
MAG_POCKETS = [(x, 6.6, z) for z in (6.7, 9.5) for x in (5.9, 8.0, 10.1)]


def grenade_pouch():
    """
    Een stoffen granaatpouch (hoofdstuk 54: mooier, en je ziet wat erin zit): een brede zak met vier open vakjes (twee
    naast elkaar, twee diep) met een tussenschot, een rand bovenaan en een riemlus achter. De granaten zelf tekent
    GearLayer erin, met hun bovenkant boven de rand uit.
    """
    return [
        box((4.6, 3.6, 5.3), (11.4, 9.4, 10.7), "canvas"),
        box((4.4, 9.1, 5.1), (11.6, 9.7, 10.9), "canvas_dark"),
        # De tussenschotten: één in het midden (x) en één tussen voor en achter (z), net boven de rand uit.
        box((7.85, 3.8, 5.4), (8.15, 9.8, 10.6), "canvas_dark"),
        box((4.7, 3.8, 7.85), (11.3, 9.8, 8.15), "canvas_dark"),
        # Een stiksel onderaan en de riemlus.
        box((4.55, 3.9, 5.25), (11.45, 4.3, 10.75), "canvas_dark"),
        box((6.0, 6.5, 10.7), (10.0, 10.4, 11.3), "canvas_dark"),
    ]


def mag_pouch():
    """
    Een magazijnpouch (hoofdstuk 54: mooier, en je ziet wat erin zit; hoofdstuk 58: zes vakjes): twee rijen van drie
    open vakjes met een elastische band om hun middel, zo diep als de granaatpouch; de magazijnen steken erbovenuit
    (zie GearLayer). Een riemlus achter.
    """
    el = [box((5.0, 6.0, 10.8), (11.0, 10.4, 11.4), "canvas_dark")]
    for z0 in (5.4, 8.2):
        for x0 in (4.9, 7.0, 9.1):
            el += [
                box((x0, 3.5, z0), (x0 + 2.0, 9.2, z0 + 2.6), "canvas"),
                box((x0 - 0.08, 6.4, z0 - 0.08), (x0 + 2.08, 7.4, z0 + 2.68), "canvas_dark"),
                box((x0 - 0.06, 8.7, z0 - 0.06), (x0 + 2.06, 9.3, z0 + 2.66), "canvas_dark"),
            ]
    return el


def gear():
    for name, make in (("holster", holster), ("grenade_pouch", grenade_pouch), ("mag_pouch", mag_pouch)):
        write(f"models/firearm/{name}.json", part_model(make()))
        write(f"models/item/{name}.json", item_model(make(), centred_display(make(), gui_rotation=(25, -40, 0), scale=1.0)))


# ---------------------------------------------------------------- Staven, poeders en korrels

def ingot(material):
    return [
        box((4, 6.6, 5.5), (12, 8.5, 10.5), material),
        box((4.7, 8.5, 6.2), (11.3, 9.1, 9.8), material),
    ]


def resources():
    for name, material in (("steel_ingot", "ingot_steel"), ("titanium_ingot", "ingot_titanium")):
        write(f"models/item/{name}.json", item_model(ingot(material), centred_display(ingot(material), gui_rotation=(30, 45, 0), scale=1.0)))
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
    # Hoofdstuk 55: het slot "sling" (een geweer met een sling op je rug of voor je borst): een schuine band met een gesp.
    sling = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(sling)
    d.line([3, 13, 12, 2], fill=grey, width=2)
    d.line([2, 12, 11, 1], fill=grey, width=1)
    d.rectangle([6, 6, 9, 9], outline=grey)
    path = ASSETS / "textures" / "slot" / "sling.png"
    sling.save(path)
    print("geschreven:", path.relative_to(ROOT))


# ---------------------------------------------------------------- De Laser Workbench (hoofdstuk 55)

def laser_workbench():
    """
    De Laser Workbench (hoofdstuk 55, jouw keuze: "een nieuwe soort tafel", zoals de Gunsmith Table): een donkere
    werkbank in de I.M.T-kleuren, met gouden randen, een cyaan lichtstrip, een laatje met een lampje, en op het blad het
    silhouet van een lasergeweer met de gloeiende lens en een Nether Star ernaast.
    """
    body = (34, 36, 44, 255)
    body_dark = (20, 22, 28, 255)
    gold = (214, 172, 88, 255)
    gold_dark = (150, 112, 44, 255)
    red = (176, 40, 44, 255)
    cyan = (63, 216, 255, 255)
    side = Image.new("RGBA", (16, 16), body)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=body_dark)
    d.line([1, 1, 14, 1], fill=gold)
    d.line([1, 2, 14, 2], fill=gold_dark)
    d.rectangle([2, 4, 13, 12], outline=(52, 56, 66, 255))
    d.rectangle([3, 5, 12, 11], fill=(28, 30, 36, 255))
    d.line([4, 8, 11, 8], fill=red)
    for x in range(3, 13, 3):
        side.putpixel((x, 14), cyan)
    save_block_texture(side, "laser_workbench_side")
    front = side.copy()
    d = ImageDraw.Draw(front)
    d.rectangle([3, 5, 12, 11], fill=(28, 30, 36, 255))
    # Een laatje met een gouden handvat, en een cyaan lampje rechtsboven.
    d.rectangle([3, 6, 12, 10], outline=gold_dark)
    d.line([6, 8, 9, 8], fill=gold)
    front.putpixel((12, 4), cyan)
    front.putpixel((11, 4), (20, 120, 150, 255))
    save_block_texture(front, "laser_workbench_front")
    top = Image.new("RGBA", (16, 16), (26, 28, 34, 255))
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=body_dark)
    d.rectangle([1, 1, 14, 14], outline=gold_dark)
    # Een lasergeweer van opzij in rood (romp, loop, kolf, greep), met een cyaan lens vooraan.
    for x, y in [(4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
                 (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7), (12, 7), (13, 7),
                 (12, 8), (13, 8), (7, 8), (8, 8), (7, 9), (6, 5), (7, 5)]:
        top.putpixel((x, y), red)
    for x in (2, 3):
        top.putpixel((x, 6), (150, 156, 166, 255))
    top.putpixel((2, 6), cyan)
    # Een Nether Star (wit-geel kruisje) en losse onderdelen (goud) onderaan.
    for x, y in [(12, 11), (11, 12), (12, 12), (13, 12), (12, 13)]:
        top.putpixel((x, y), (255, 240, 170, 255))
    d.line([3, 12, 6, 12], fill=gold)
    d.line([3, 13, 4, 13], fill=gold_dark)
    save_block_texture(top, "laser_workbench_top")
    orientable_block("laser_workbench")


# ---------------------------------------------------------------- Alles

def main():
    materials()
    glock_19()
    gear()
    resources()
    gunsmith_table()
    reloading_bench()
    laser_workbench()
    slot_icons()


if __name__ == "__main__":
    main()
