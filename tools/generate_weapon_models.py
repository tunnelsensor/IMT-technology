"""
Maakt de 3D-modellen van de wapens (hoofdstuk 35): de twee EMP-granaten, de energiecel en de vier lasergeweren,
en het plaatje met hun materialen.

Hoe het werkt
    Elk model is een lijstje doosjes ("elements"), net als een blokmodel: van een hoek tot een hoek, in pixels (16 =
    één blok). Elk doosje krijgt een MATERIAAL (rood, goud, donker metaal, gloeiend cyaan, ...). Alle materialen staan
    op één plaatje van 128 x 128 (textures/item/weapons.png): plek voor 64 vakjes van 16 x 16, met een zacht verloop
    en wat ruis.
    Elke kant van een doosje toont het hele vakje van zijn materiaal. Zo hoef je niets per kant te tekenen.

    Een doosje met glow=True gloeit: het krijgt altijd vol licht (ook in het donker) en geen schaduw.

    Een geweer bestaat uit ONDERDELEN: "body" (staat stil), "cell" (de energiecel, die eruit gaat bij herladen),
    "trigger" (de trekker) en "moving" (wat bij een schot beweegt: de slede van het pistool, de grendel van de sniper,
    de pomp van de shotgun, de spanhendel van het geweer). Elk onderdeel komt in een eigen bestand
    (models/weapon/<geweer>/<onderdeel>.json); in je eigen beeld tekent de mod ze los, zodat ze kunnen bewegen (zie
    client/weapons/GunRenderer). Voor je inventaris, je hand in derde persoon en op de grond is er ook het hele geweer
    in één bestand (models/item/<geweer>.json).

    De granaten hebben ook twee delen: "body" en "light" (het lampje, dat knippert als hij gaat afgaan).

Assen: x = links/rechts, y = omhoog, z = naar achteren. De loop wijst dus naar -z (het noorden), de kolf naar +z.
De loop van elk geweer ligt op x = 8, en rond y = 11.

Draaien (vanuit de hoofdmap):  python tools/generate_weapon_models.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "imttech"
TEXTURE = "imttech:item/weapons"
SIDES = ("north", "south", "east", "west", "up", "down")

# ---------------------------------------------------------------- De materialen

# Naam -> (kolom, rij) op het plaatje.
MATERIALS = {
    "red": (0, 0), "red_dark": (1, 0), "gold": (2, 0), "gold_dark": (3, 0),
    "graphite": (0, 1), "black": (1, 1), "steel": (2, 1), "steel_dark": (3, 1),
    "glow": (0, 2), "glow_white": (1, 2), "lens": (2, 2), "vent": (3, 2),
    "cell": (0, 3), "screen": (1, 3), "pad": (2, 3), "paint": (3, 3),
    # De frag-granaten: legergroen, oranje (gevaar) en een rood lampje.
    "olive": (4, 0), "orange": (5, 0), "glow_red": (6, 0),
}

# Het plaatje is 128 pixels breed en hoog: een vakje van 16 pixels is in de 0-16-eenheden van een model 2 breed.
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
    """Het plaatje met alle materialen (textures/item/weapons.png)."""
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    def highlight(x, y, c):
        # Een glimlijntje bovenaan, zoals gelakt metaal dat licht vangt.
        if y == 1:
            return [v * 1.25 + 12 for v in c]
        return c

    def brushed(x, y, c):
        # Geborsteld staal: lichte en donkere lijntjes in de lengte.
        return [v + (8 if (y * 7 + x // 5) % 3 == 0 else -4) for v in c]

    def rubber(x, y, c):
        # Een greep van rubber met noppen.
        return [v + 14 for v in c] if x % 3 == 1 and y % 3 == 1 else c

    def slots(x, y, c):
        # Ventilatiesleuven: donkere strepen dwars.
        return [v * 0.3 for v in c] if y % 4 in (1, 2) and 1 <= x <= 14 else c

    def glow(x, y, c):
        # Gloeiend: wit in het midden, cyaan naar de rand.
        d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
        core = (235, 252, 255)
        edge = (70, 210, 255)
        return [core[i] + (edge[i] - core[i]) * d for i in range(3)]

    def lens(x, y, c):
        # Glas: een schuine glimstreep.
        return [v + 60 for v in c] if 5 <= (x + y) <= 7 else c

    def bands(x, y, c):
        # De energiecel: staal met twee donkere ribbels.
        return [v * 0.6 for v in c] if y in (4, 11) else c

    def screen(x, y, c):
        # Een donker schermpje met een rij cyaan streepjes.
        return [40, 170, 210] if y == 8 and x % 3 != 2 and 2 <= x <= 13 else c

    def gel(x, y, c):
        # Het kleefkussen van de sticky: donker met glimmende druppels.
        return [v + 50 for v in c] if (x * 5 + y * 3) % 11 == 0 else c

    tile(im, "red", (214, 44, 44), (138, 18, 24), 5, highlight, 1)
    tile(im, "red_dark", (120, 18, 22), (84, 10, 14), 4, None, 2)
    tile(im, "gold", (252, 214, 112), (176, 124, 36), 5, highlight, 3)
    tile(im, "gold_dark", (168, 120, 38), (116, 80, 22), 4, None, 4)
    tile(im, "graphite", (70, 74, 84), (40, 43, 50), 5, None, 5)
    tile(im, "black", (34, 34, 40), (20, 20, 24), 3, rubber, 6)
    tile(im, "steel", (178, 186, 198), (118, 126, 138), 4, brushed, 7)
    tile(im, "steel_dark", (104, 110, 122), (70, 74, 84), 4, brushed, 8)
    tile(im, "glow", (0, 0, 0), (0, 0, 0), 0, glow, 9)
    tile(im, "glow_white", (240, 252, 255), (225, 245, 255), 2, None, 10)
    tile(im, "lens", (26, 48, 80), (12, 22, 40), 3, lens, 11)
    tile(im, "vent", (62, 66, 76), (44, 47, 54), 3, slots, 12)
    tile(im, "cell", (150, 158, 172), (104, 110, 122), 4, bands, 13)
    tile(im, "screen", (14, 26, 34), (8, 16, 22), 2, screen, 14)
    tile(im, "pad", (52, 78, 60), (30, 46, 36), 4, gel, 15)
    # Lichtgrijs om te kleuren: de band van de smokebom krijgt in het spel de kleur van zijn rook (tint=True).
    tile(im, "paint", (236, 236, 236), (196, 196, 196), 4, None, 16)

    def red_glow(x, y, c):
        # Een rood lampje: wit-roze in het midden, fel rood naar de rand.
        d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
        core = (255, 220, 210)
        edge = (255, 40, 30)
        return [core[i] + (edge[i] - core[i]) * d for i in range(3)]

    tile(im, "olive", (92, 104, 70), (56, 64, 42), 5, highlight, 17)
    tile(im, "orange", (255, 150, 40), (200, 96, 20), 4, highlight, 18)
    tile(im, "glow_red", (0, 0, 0), (0, 0, 0), 0, red_glow, 19)
    path = ASSETS / "textures" / "item" / "weapons.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ASSETS.parent.parent.parent.parent.parent))


# ---------------------------------------------------------------- Doosjes

def uv(material):
    """Het vakje van dit materiaal, in de 0-16-eenheden van een model (het plaatje is 128 breed: één vakje = 2)."""
    col, row = MATERIALS[material]
    inset = TILE_UV * 0.04
    return [round(col * TILE_UV + inset, 4), round(row * TILE_UV + inset, 4),
            round((col + 1) * TILE_UV - inset, 4), round((row + 1) * TILE_UV - inset, 4)]


def box(frm, to, material, glow=False, rot=None, tint=False):
    """
    Eén doosje. rot = (as, hoek, middelpunt), bijvoorbeeld ("x", 22.5, (8, 8, 11)): het doosje kantelt om dat punt
    (Minecraft kan alleen -45, -22.5, 0, 22.5 of 45 graden). tint=True: het spel kleurt dit doosje (zie
    WeaponModels.registerItemColors), zoals de band van een gekleurde smokebom.
    """
    faces = {side: {"uv": uv(material), "texture": "#0"} for side in SIDES}
    if tint:
        for face in faces.values():
            face["tintindex"] = 0
    element = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if rot:
        axis, angle, origin = rot
        element["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
    if glow:
        element["shade"] = False
        for face in faces.values():
            face["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return element


def mirror_x(element):
    """Hetzelfde doosje, gespiegeld in x = 8 (de andere kant van het geweer)."""
    e = json.loads(json.dumps(element))
    a, b = e["from"][0], e["to"][0]
    e["from"][0], e["to"][0] = round(16 - b, 3), round(16 - a, 3)
    if "rotation" in e:
        e["rotation"]["origin"][0] = 16 - e["rotation"]["origin"][0]
        if e["rotation"]["axis"] != "x":
            e["rotation"]["angle"] = -e["rotation"]["angle"]
    return e


def both(element):
    """Een doosje en zijn spiegelbeeld."""
    return [element, mirror_x(element)]


def write(rel, data):
    path = ASSETS / "models" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ASSETS.parent.parent.parent.parent.parent))


def part_model(elements):
    """Een los onderdeel: alleen doosjes, geen houding (die regelt de mod zelf)."""
    return {"textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements}


def item_model(elements, display):
    """Het hele ding als item: doosjes plus hoe het in je hand, je inventaris en op de grond ligt."""
    return {"gui_light": "front", "textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements,
            "display": display}


def gun_display(gui_scale, gui_translation, ground_scale, grip):
    """
    De houdingen van een geweer. In derde persoon (met twee handen, zie WeaponModels.registerExtensions) ligt de loop
    zonder te draaien al langs je arm naar voren; we schuiven het model zo dat de greep ({grip}) in je hand zit. In je
    eigen beeld tekent de mod het geweer zelf (zie GunRenderer), dus "firstperson" doet er niet toe.
    """
    hand = 0.75
    # De houding met twee handen draait je arm naar binnen: het geweer draait een stukje terug, zodat de loop
    # (ongeveer) recht vooruit wijst. De greep blijft in je hand (de verschuiving draait mee).
    import math
    turn = -17
    a = math.radians(turn)
    gx, gy, gz = (hand * (g - 8) for g in grip)
    rx = gx * math.cos(a) + gz * math.sin(a)
    rz = -gx * math.sin(a) + gz * math.cos(a)
    third = ((0, turn, 0), (round(-rx, 2), round(-gy, 2), round(-rz, 2)))
    return display(gui_scale, gui_translation, hand, ground_scale, gui_rotation=(0, 90, 0), third=third)


def display(gui_scale, gui_translation, hand_scale, ground_scale, gui_rotation=(25, -135, 0),
            third=((0, 180, 0), (0, 3, 1)), first=((0, 180, 0), (1, 3, 0)), first_scale=None):
    """
    De houdingen: in je inventaris, in je hand (derde en eerste persoon), op de grond en in een lijstje. In je eigen
    beeld (eerste persoon) mag iets kleiner zijn dan in je hand voor anderen: {first_scale}.
    """
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


def save_parts(name, parts, disp):
    """Elk onderdeel apart (models/weapon/<naam>/<onderdeel>.json) en alles samen als item."""
    merged = []
    for part, elements in parts.items():
        write(f"weapon/{name}/{part}.json", part_model(elements))
        merged += elements
    write(f"item/{name}.json", item_model(merged, disp))


# ---------------------------------------------------------------- De granaten

def ball(shell, band, band_tint=False):
    """
    De bol van de rollende granaten: midden (8, 8, 8), doorsnee 6 pixels. Drie kruisende doosjes en een vierde dat de
    hoeken opvult, en een band om zijn middel.
    """
    return [
        box((5, 6, 6), (11, 10, 10), shell),
        box((6, 5, 6), (10, 11, 10), shell),
        box((6, 6, 5), (10, 10, 11), shell),
        box((5.5, 5.5, 5.5), (10.5, 10.5, 10.5), shell),
        box((4.85, 7.55, 6.3), (11.15, 8.45, 9.7), band, tint=band_tint),
        box((6.3, 7.55, 4.85), (9.7, 8.45, 11.15), band, tint=band_tint),
        box((5.35, 7.55, 5.35), (10.65, 8.45, 10.65), band, tint=band_tint),
        # Onderaan een plat donker plaatje (de bol rolt, dus het mag overal).
        box((7, 4.75, 7), (9, 5.05, 9), "graphite"),
    ]


BALL_DISPLAY = dict(third=((0, 0, 0), (0, 2, 1)), first=((10, -30, 0), (0, 2, -2)), first_scale=0.42)


def emp_grenade():
    """
    De EMP Grenade (rollend): een rode bol met een gouden band om zijn middel, een knopje bovenop en vier lampjes op
    de band, zoals een thermal detonator.
    """
    body = ball("red", "gold") + [
        # Het knopje bovenop (de ontsteker), met een donkere rand.
        box((6.9, 10.9, 6.9), (9.1, 11.35, 9.1), "graphite"),
        box((7.3, 11.35, 7.3), (8.7, 11.55, 8.7), "lens"),
    ]
    light = [
        # Het lampje in het knopje, en vier lampjes op de band.
        box((7.25, 11.5, 7.25), (8.75, 11.7, 8.75), "glow", glow=True),
        box((4.75, 7.7, 7.5), (4.9, 8.3, 8.5), "glow", glow=True),
        box((11.1, 7.7, 7.5), (11.25, 8.3, 8.5), "glow", glow=True),
        box((7.5, 7.7, 4.75), (8.5, 8.3, 4.9), "glow", glow=True),
        box((7.5, 7.7, 11.1), (8.5, 8.3, 11.25), "glow", glow=True),
    ]
    save_parts("emp_grenade", {"body": body, "light": light}, display(1.6, (0, 0, 0), 0.7, 0.7, **BALL_DISPLAY))


def frag_grenade():
    """
    De Frag Grenade: een kopie van de EMP Grenade als explosief (jouw wens). Dezelfde bol, maar legergroen met een
    oranje band (gevaar), en zijn lampjes knipperen rood.
    """
    body = ball("olive", "orange") + [
        box((6.9, 10.9, 6.9), (9.1, 11.35, 9.1), "graphite"),
        box((7.3, 11.35, 7.3), (8.7, 11.55, 8.7), "red_dark"),
    ]
    light = [
        box((7.25, 11.5, 7.25), (8.75, 11.7, 8.75), "glow_red", glow=True),
        box((4.75, 7.7, 7.5), (4.9, 8.3, 8.5), "glow_red", glow=True),
        box((11.1, 7.7, 7.5), (11.25, 8.3, 8.5), "glow_red", glow=True),
        box((7.5, 7.7, 4.75), (8.5, 8.3, 4.9), "glow_red", glow=True),
        box((7.5, 7.7, 11.1), (8.5, 8.3, 11.25), "glow_red", glow=True),
    ]
    save_parts("frag_grenade", {"body": body, "light": light}, display(1.6, (0, 0, 0), 0.7, 0.7, **BALL_DISPLAY))


def smoke_grenade():
    """
    De Smoke Grenade: een donkere bol met een band in de kleur van zijn rook (grijs, of de kleurstof waarmee je hem
    maakte), en bovenop een rooster met gaatjes waar de rook uit sist.
    """
    body = ball("steel_dark", "paint", band_tint=True) + [
        box((6.6, 10.85, 6.6), (9.4, 11.4, 9.4), "vent"),
        box((7.6, 11.4, 6.9), (8.4, 11.55, 9.1), "graphite"),
        box((6.9, 11.4, 7.6), (9.1, 11.55, 8.4), "graphite"),
        # Twee stalen klemmen over de band.
        box((4.8, 7.4, 7.6), (5.1, 8.6, 8.4), "steel"),
        box((10.9, 7.4, 7.6), (11.2, 8.6, 8.4), "steel"),
    ]
    save_parts("smoke_grenade", {"body": body, "light": []}, display(1.6, (0, 0, 0), 0.7, 0.7, **BALL_DISPLAY))


def flashbang():
    """
    De Flashbang: een glanzende stalen bol met een zwarte band, een donker dopje bovenop en een gouden ring (de pin),
    en kleine witte venstertjes in de band waar de flits uit komt.
    """
    body = ball("steel", "black") + [
        box((7, 10.85, 7), (9, 11.5, 9), "graphite"),
        # De pin: een gouden ring die rechtop staat.
        box((7.85, 11.5, 6.6), (8.15, 12.6, 7.0), "gold"),
        box((7.85, 11.5, 9.0), (8.15, 12.6, 9.4), "gold"),
        box((7.85, 12.6, 6.6), (8.15, 12.9, 9.4), "gold"),
        # Witte venstertjes in de band.
        box((4.75, 7.7, 6.9), (4.85, 8.3, 7.5), "glow_white"),
        box((4.75, 7.7, 8.5), (4.85, 8.3, 9.1), "glow_white"),
        box((11.15, 7.7, 6.9), (11.25, 8.3, 7.5), "glow_white"),
        box((11.15, 7.7, 8.5), (11.25, 8.3, 9.1), "glow_white"),
    ]
    save_parts("flashbang", {"body": body, "light": []}, display(1.6, (0, 0, 0), 0.7, 0.7, **BALL_DISPLAY))


def sticky(name, shell, ring, light_material):
    """
    De halve bol die plakt: een halve bol op een groen kleefkussen. Het kussen ligt onderaan op y = 5; daar plakt hij
    met zijn platte kant tegen de muur, de grond of een robot. Een ring rond zijn middel en vier lampjes.
    """
    def layer(y0, y1, r, material, glow=False):
        """Een "ronde" laag: twee kruisende doosjes en een vierkant ertussen, met straal r rond (8, 8)."""
        return [
            box((8 - r, y0, 8 - r * 0.56), (8 + r, y1, 8 + r * 0.56), material, glow),
            box((8 - r * 0.56, y0, 8 - r), (8 + r * 0.56, y1, 8 + r), material, glow),
            box((8 - r * 0.83, y0, 8 - r * 0.83), (8 + r * 0.83, y1, 8 + r * 0.83), material, glow),
        ]
    body = (layer(5, 5.5, 3.6, "pad")
            + layer(5.5, 7.1, 3.25, shell)
            + layer(7.1, 7.6, 3.05, ring)
            + layer(7.6, 8.8, 2.55, shell)
            + layer(8.8, 9.3, 1.75, "graphite")
            + [box((7.35, 9.3, 7.35), (8.65, 9.5, 8.65), "lens")])
    light = [
        box((7.2, 9.45, 7.2), (8.8, 9.65, 8.8), light_material, glow=True),
        box((4.9, 7.2, 7.5), (5.0, 7.5, 8.5), light_material, glow=True),
        box((11.0, 7.2, 7.5), (11.1, 7.5, 8.5), light_material, glow=True),
        box((7.5, 7.2, 4.9), (8.5, 7.5, 5.0), light_material, glow=True),
        box((7.5, 7.2, 11.0), (8.5, 7.5, 11.1), light_material, glow=True),
    ]
    disp = display(1.7, (0, 1.5, 0), 0.7, 0.7, gui_rotation=(30, -135, 0), third=((0, 0, 0), (0, 3, 1)),
                   first=((10, -30, 0), (0, 3, -2)), first_scale=0.45)
    save_parts(name, {"body": body, "light": light}, disp)


def sticky_emp_grenade():
    """De Sticky EMP Grenade: rood met een gouden ring en cyaan lampjes."""
    sticky("sticky_emp_grenade", "red", "gold", "glow")


def sticky_frag_grenade():
    """De Sticky Frag Grenade: de kopie als explosief, legergroen met een oranje ring en rode lampjes."""
    sticky("sticky_frag_grenade", "olive", "orange", "glow_red")


# ---------------------------------------------------------------- De energiecel

def energy_cell():
    """De energiecel: een stalen staafje met gouden kapjes en een venster waarachter cyaan energie gloeit."""
    elements = [
        box((6, 3.5, 6.5), (10, 12.5, 9.5), "cell"),
        box((5.75, 3, 6.25), (10.25, 4, 9.75), "gold"),
        box((5.75, 12, 6.25), (10.25, 13, 9.75), "gold"),
        box((7.25, 13, 7.25), (8.75, 13.5, 8.75), "graphite"),
        box((6.75, 5, 6.4), (9.25, 11, 6.5), "glow", glow=True),
        box((6.75, 5, 9.5), (9.25, 11, 9.6), "glow", glow=True),
    ]
    disp = display(1.25, (0, 0, 0), 0.6, 0.6, gui_rotation=(20, -30, 0), third=((0, 0, 0), (0, 2, 1)),
                   first=((0, -20, 0), (0, 2, -2)), first_scale=0.4)
    write("item/energy_cell.json", item_model(elements, disp))


# ---------------------------------------------------------------- De geweren

GRIP = 22.5  # zo schuin staat elke handgreep (graden)


def grip(z0, z1, y0, y1, pivot):
    """Een schuine handgreep van rubber met rode zijplaatjes, gekanteld om {pivot}."""
    rot = ("x", GRIP, pivot)
    return [
        box((7, y0, z0), (9, y1, z1), "black", rot=rot),
        *both(box((6.85, y0 + 0.8, z0 + 0.4), (7.0, y1 - 0.6, z1 - 0.5), "red_dark", rot=rot)),
        box((7.3, y0 - 0.3, z0 + 0.2), (8.7, y0, z1 - 0.2), "graphite", rot=rot),
    ]


def trigger_guard(z0, z1, y_bottom, y_top):
    return [
        box((7.55, y_bottom, z0), (8.45, y_bottom + 0.5, z1), "graphite"),
        box((7.55, y_bottom, z0), (8.45, y_top, z0 + 0.5), "graphite"),
    ]


def emitter(z_front, y_center, size, ring_material="gold"):
    """De voorkant van een loop: een ring en een gloeiende lens (de laser komt hieruit)."""
    h = size / 2
    return [
        box((8 - h - 0.25, y_center - h - 0.25, z_front), (8 + h + 0.25, y_center + h + 0.25, z_front + 1), ring_material),
        box((8 - h + 0.3, y_center - h + 0.3, z_front - 0.1), (8 + h - 0.3, y_center + h - 0.3, z_front), "glow", glow=True),
    ]


def laser_pistol():
    """
    Het laserpistool: een rode slede met een gouden rib en cyaan strepen, een donker frame, een schuine greep, en de
    cel onderin de greep. Ongeveer 13 pixels lang.
    """
    body = [
        # Frame onder de slede, met een rail vooraan.
        box((6.75, 8, 3.5), (9.25, 9.5, 13), "graphite"),
        box((7, 7.5, 3.5), (9, 8, 7), "graphite"),
        # De loop vooraan: een gouden ring om de lens.
        *emitter(1, 11, 2.6),
        box((6.6, 9.4, 1.9), (9.4, 12.6, 2.2), "graphite"),
        # De achterkant: een gouden staartstuk.
        box((7.25, 7.6, 12.8), (8.75, 9.2, 13.4), "gold"),
        *grip(9.6, 12.8, 2.6, 8.2, (8, 8, 11)),
        *trigger_guard(6, 9.6, 5.6, 8),
    ]
    moving = [
        # De slede: schuift bij elk schot even naar achteren.
        box((6.5, 9.5, 2.2), (9.5, 12.5, 13.2), "red"),
        box((7.25, 12.5, 3), (8.75, 13, 12.4), "gold"),
        # Vizier voor en achter.
        box((7.7, 13, 3.2), (8.3, 13.6, 3.9), "graphite"),
        box((7.0, 13, 11.6), (7.6, 13.8, 12.4), "graphite"),
        box((8.4, 13, 11.6), (9.0, 13.8, 12.4), "graphite"),
        # Gleufjes achteraan om de slede vast te pakken.
        *[box((6.4, 10, z), (9.6, 12.2, z + 0.3), "red_dark") for z in (10.6, 11.3, 12.0)],
        # De cyaan strepen opzij.
        *both(box((6.38, 10.6, 3.6), (6.5, 11.0, 9.8), "glow", glow=True)),
        # Een donker raampje opzij (de lens van de laserkamer).
        *both(box((6.4, 11.3, 4.5), (6.5, 12.1, 7.5), "lens")),
    ]
    trigger = [box((7.6, 6.3, 7.9), (8.4, 8, 8.5), "steel")]
    cell = [
        box((7.15, 1.4, 9.9), (8.85, 3.2, 12.5), "cell", rot=("x", GRIP, (8, 8, 11))),
        box((7.45, 1.25, 10.3), (8.55, 1.4, 12.1), "glow", glow=True, rot=("x", GRIP, (8, 8, 11))),
    ]
    disp = gun_display(1.0, (0, 0, 0), 0.6, (8, 6.5, 11.4))
    save_parts("laser_pistol", {"body": body, "moving": moving, "trigger": trigger, "cell": cell}, disp)


def laser_rifle():
    """
    Het lasergeweer: een rode romp met een kolf, een donkere handbescherming met sleuven en cyaan strepen, een stalen
    loop met gouden ringen, een holografisch vizier bovenop, en de cel als magazijn voor de trekker.
    """
    body = [
        # Romp.
        box((6.5, 9, 2), (9.5, 12.5, 13), "red"),
        box((6.4, 10.2, 3), (9.6, 10.5, 12), "red_dark"),
        box((7, 12.5, 2.5), (9, 13, 12.5), "graphite"),
        # Spanhendel-gleuf opzij (de hendel zelf beweegt, zie moving).
        box((9.5, 11, 6.5), (9.6, 11.75, 11), "red_dark"),
        # Handbescherming met sleuven, en cyaan strepen.
        box((6.75, 9.25, -4), (9.25, 12, 2), "graphite"),
        *both(box((6.65, 9.9, -3.2), (6.75, 11.3, 1.2), "vent")),
        *both(box((6.55, 11.45, -3.6), (6.7, 11.75, 1.6), "glow", glow=True)),
        box((7.25, 8.75, -3.5), (8.75, 9.25, 1.5), "graphite"),
        # Loop met gouden ringen en de lens.
        box((7.25, 10, -8.5), (8.75, 11.75, -4), "steel"),
        box((7, 9.75, -6), (9, 12, -5.4), "gold"),
        *emitter(-9.5, 10.875, 2.2),
        # Holografisch vizier: een voetje en een gouden raampje, met een gloeiend puntje in het midden.
        box((7, 13, 6), (9, 13.5, 10), "graphite"),
        box((6.75, 13.5, 6.5), (7.25, 15.75, 7.1), "gold"),
        box((8.75, 13.5, 6.5), (9.25, 15.75, 7.1), "gold"),
        box((6.75, 15.75, 6.5), (9.25, 16.2, 7.1), "gold"),
        box((7, 13.5, 7.1), (9, 13.8, 9.5), "graphite"),
        box((7.9, 14.55, 6.75), (8.1, 14.75, 6.85), "glow", glow=True),
        # Kolf met een gouden wangsteun en een rubber kolfplaat.
        box((7, 8.5, 13), (9, 12, 19), "red"),
        box((7.25, 12, 13.5), (8.75, 12.5, 18), "gold"),
        box((6.9, 7.5, 19), (9.1, 12.25, 20), "black"),
        box((7.1, 9.2, 14.2), (8.9, 10.6, 17.6), "red_dark"),
        *grip(10, 12.6, 4, 9.2, (8, 9, 11)),
        *trigger_guard(7.5, 10, 6.5, 9),
        # De houder van de cel.
        box((6.85, 8.6, 3.6), (9.15, 9.1, 7.4), "gold_dark"),
    ]
    moving = [box((9.5, 11, 10), (10.3, 11.75, 11), "steel"), box((10.3, 10.9, 10.1), (10.6, 11.85, 10.9), "gold")]
    trigger = [box((7.6, 7, 8.6), (8.4, 9, 9.2), "steel")]
    cell = [
        box((7, 4.2, 4), (9, 8.6, 7), "cell"),
        *both(box((6.9, 5, 4.9), (7.0, 7.8, 6.1), "glow", glow=True)),
        box((6.95, 4, 3.9), (9.05, 4.5, 7.1), "gold"),
    ]
    disp = gun_display(0.55, (0, 0, -1.5), 0.5, (8, 7.2, 11.3))
    save_parts("laser_rifle", {"body": body, "moving": moving, "trigger": trigger, "cell": cell}, disp)


def laser_sniper():
    """
    De lasersniper: lang en zwaar, met een lange loop met sleuven, een grote gouden mondingsrem, een kijker met
    lenzen, een grendel opzij en een kolf met een rubber plaat.
    """
    body = [
        # Romp.
        box((6.5, 9, 4), (9.5, 12.5, 14), "red"),
        box((6.4, 10.4, 5), (9.6, 10.7, 13), "red_dark"),
        # Lange loop met sleuven, cyaan strepen en een gouden rem.
        box((6.85, 9.6, -10), (9.15, 12, 4), "graphite"),
        *both(box((6.75, 10.2, -9), (6.85, 11.4, 2.5), "vent")),
        *both(box((6.65, 11.55, -9.5), (6.8, 11.8, 3), "glow", glow=True)),
        box((7.25, 9.1, -8), (8.75, 9.6, -2), "graphite"),
        box((6.5, 9.3, -13), (9.5, 12.3, -10), "gold"),
        *both(box((6.4, 10.3, -12.4), (6.5, 11.3, -10.6), "graphite")),
        *emitter(-14, 10.8, 2.0, "gold_dark"),
        # Kijker op twee gouden pootjes.
        box((7.25, 12.5, 2), (8.75, 13.5, 3), "gold"),
        box((7.25, 12.5, 9), (8.75, 13.5, 10), "gold"),
        box((7, 13.5, 0), (9, 15.5, 12), "black"),
        box((6.5, 13, -1.5), (9.5, 16, 0.5), "graphite"),
        box((6.75, 13.25, 11), (9.25, 15.75, 13), "graphite"),
        box((7, 13.25, -1.6), (9, 15.75, -1.5), "lens"),
        box((9, 14, 4), (9.6, 15, 5.5), "gold"),
        # Kolf met een gat voor je duim, een wangsteun en een rubber plaat.
        box((7, 11, 14), (9, 12.5, 24), "red"),
        box((7, 7.5, 18), (9, 11, 24), "red"),
        box((7.25, 12.5, 15), (8.75, 13, 22), "gold"),
        box((6.9, 7, 24), (9.1, 12.75, 25), "black"),
        *grip(13, 15.6, 5, 10.2, (8, 10, 14)),
        *trigger_guard(10, 13, 7.2, 9),
    ]
    moving = [
        # De grendel: een hendel met een knop, die na elk schot omhoog en naar achteren gaat.
        box((9.5, 11, 11), (10.8, 11.6, 11.8), "steel"),
        box((10.6, 10.6, 10.9), (11.4, 11.6, 11.9), "gold"),
    ]
    trigger = [box((7.6, 7.6, 11.4), (8.4, 9, 12), "steel")]
    cell = [
        box((7, 6, 6), (9, 9, 9.5), "cell"),
        *both(box((6.9, 6.6, 6.8), (7.0, 8.4, 8.7), "glow", glow=True)),
        box((6.95, 5.8, 5.9), (9.05, 6.3, 9.6), "gold"),
    ]
    disp = gun_display(0.42, (0, 0, -1), 0.45, (8, 8.2, 14.4))
    save_parts("laser_sniper", {"body": body, "moving": moving, "trigger": trigger, "cell": cell}, disp)


def laser_shotgun():
    """
    De laser-shotgun: breed en kort, met twee lopen naast elkaar, een rubber pomp eronder (die na elk schot naar
    achteren en weer naar voren gaat), een brede romp met gouden rib, een kolf en de cel onder de romp.
    """
    body = [
        # Romp.
        box((6.25, 8.5, 2), (9.75, 12.75, 13), "red"),
        box((7.25, 12.75, 2.5), (8.75, 13.25, 12.5), "gold"),
        *both(box((6.15, 9.5, 3), (6.25, 12, 11), "red_dark")),
        *both(box((6.1, 11.2, 3.5), (6.2, 11.5, 10.5), "glow", glow=True)),
        # Twee lopen met gouden ringen en lenzen.
        box((6.5, 10, -6), (7.9, 11.6, 2), "steel"),
        box((8.1, 10, -6), (9.5, 11.6, 2), "steel"),
        box((6.3, 9.8, -6.6), (9.7, 11.8, -5.6), "gold"),
        box((6.75, 10.25, -6.7), (7.65, 11.35, -6.6), "glow", glow=True),
        box((8.35, 10.25, -6.7), (9.25, 11.35, -6.6), "glow", glow=True),
        box((6.3, 9.8, -2.2), (9.7, 11.8, -1.6), "gold"),
        # De buis onder de lopen (de pomp schuift eroverheen).
        box((7.1, 8.4, -5), (8.9, 9.8, 2), "graphite"),
        # Kolf.
        box((7, 8, 13), (9, 12, 19), "red"),
        box((6.9, 7.25, 19), (9.1, 12.25, 20), "black"),
        box((7.2, 9.3, 14), (8.8, 10.8, 18), "red_dark"),
        *grip(10, 12.6, 3.8, 9, (8, 8.5, 11)),
        *trigger_guard(7.2, 10, 6.4, 8.5),
    ]
    moving = [
        box((6.6, 7.8, -4.4), (9.4, 10.1, -0.2), "black"),
        *[box((6.5, 8.2, z), (9.5, 9.9, z + 0.4), "red_dark") for z in (-3.6, -2.6, -1.6)],
    ]
    trigger = [box((7.6, 6.9, 8.3), (8.4, 8.5, 8.9), "steel")]
    cell = [
        box((7, 6.2, 4), (9, 8.5, 7.5), "cell"),
        *both(box((6.9, 6.6, 4.8), (7.0, 8.1, 6.7), "glow", glow=True)),
        box((6.95, 6.0, 3.9), (9.05, 6.5, 7.6), "gold"),
    ]
    disp = gun_display(0.6, (0, 0, -1), 0.55, (8, 7.0, 11.3))
    save_parts("laser_shotgun", {"body": body, "moving": moving, "trigger": trigger, "cell": cell}, disp)


def main():
    materials()
    emp_grenade()
    frag_grenade()
    smoke_grenade()
    flashbang()
    sticky_emp_grenade()
    sticky_frag_grenade()
    energy_cell()
    laser_pistol()
    laser_rifle()
    laser_sniper()
    laser_shotgun()


if __name__ == "__main__":
    main()
