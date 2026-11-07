"""
Maakt de 3D-modellen van de wapens (hoofdstuk 35): de granaten, de Remote EMP en Remote Frag met hun triggers, de
energiecel en de vier lasergeweren, en het plaatje met hun materialen.

Hoe het werkt
    Elk model is een lijstje doosjes ("elements"), net als een blokmodel: van een hoek tot een hoek, in pixels (16 =
    één blok). Elk doosje krijgt een MATERIAAL (rood, goud, donker metaal, gloeiend cyaan, ...). Alle materialen staan
    op één plaatje van 128 x 128 (textures/item/weapons.png): plek voor 64 vakjes van 16 x 16, met een zacht verloop
    en wat ruis.
    Elke kant van een doosje toont het hele vakje van zijn materiaal. Zo hoef je niets per kant te tekenen.

    Een doosje met glow=True gloeit: het krijgt altijd vol licht (ook in het donker) en geen schaduw.

    Een geweer bestaat uit ONDERDELEN (hoofdstuk 55, jouw keuze: lasergeweren bouw je net als de Glock uit onderdelen
    op een tafel): het frame, de receiver, de emitter, de optics, de capacitor en de trigger, elk in twee kwaliteiten
    (titanium en netherite), plus de cel (de energiecel, die eruit gaat bij herladen), de kern (een Nether Star) en bij
    de lange geweren de sling. Elk onderdeel komt in een eigen bestand (models/weapon/<geweer>/<onderdeel>_<kwaliteit>.json);
    wat bij een schot beweegt (de slede van het pistool, de spanhendel van het geweer, de grendel van de sniper, de pomp
    van de shotgun) staat apart in ..._moving.json. In je eigen beeld en op de Laser Workbench tekent de mod ze los,
    zodat ze kunnen bewegen en ontbreken (zie client/weapons/GunRenderer en LaserWorkbenchScreen). Voor je inventaris,
    je hand in derde persoon en op de grond is er ook het hele geweer in één bestand (models/item/<geweer>.json), en
    elk los onderdeel is een item (models/item/<geweer>_<onderdeel>.json, met een override per kwaliteit).

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
    # Hoofdstuk 55: netherite (de tweede kwaliteit van de laseronderdelen), leer (de sling) en de Nether Star (de kern).
    "netherite": (4, 1), "netherite_dark": (5, 1), "leather": (6, 1), "star": (7, 1),
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

    def flecks(x, y, c):
        # Netherite: donker paarsbruin met lichtere vlekjes, zoals een netherite ingot.
        return [v + 26 for v in c] if (x * 7 + y * 11) % 13 == 0 else c

    def grain(x, y, c):
        # Leer: een fijne nerf.
        return [v + (7 if (x + y * 3) % 5 == 0 else -5 if (x * 3 + y) % 7 == 0 else 0) for v in c]

    def star(x, y, c):
        # De Nether Star: wit-geel in het midden, warm goud naar de rand.
        d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
        centre = (255, 252, 220)
        edge = (255, 196, 80)
        return [centre[i] + (edge[i] - centre[i]) * d for i in range(3)]

    tile(im, "netherite", (82, 72, 78), (54, 46, 50), 4, flecks, 20)
    tile(im, "netherite_dark", (58, 50, 56), (36, 30, 34), 3, flecks, 21)
    tile(im, "leather", (124, 84, 50), (88, 58, 32), 5, grain, 22)
    tile(im, "star", (0, 0, 0), (0, 0, 0), 0, star, 23)
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


def item_model(elements, display, overrides=None):
    """Het hele ding als item: doosjes plus hoe het in je hand, je inventaris en op de grond ligt."""
    model = {"gui_light": "front", "textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements,
             "display": display}
    if overrides:
        model["overrides"] = overrides
    return model


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
    # In je inventaris iets kleiner dan de ronde granaten: het brede kleefkussen stak anders uit zijn vakje (hoofdstuk 54).
    disp = display(1.25, (0, 0.5, 0), 0.7, 0.7, gui_rotation=(30, -135, 0), third=((0, 0, 0), (0, 3, 1)),
                   first=((10, -30, 0), (0, 3, -2)), first_scale=0.45)
    save_parts(name, {"body": body, "light": light}, disp)


def sticky_emp_grenade():
    """De Sticky EMP Grenade: rood met een gouden ring en cyaan lampjes."""
    sticky("sticky_emp_grenade", "red", "gold", "glow")


def sticky_frag_grenade():
    """De Sticky Frag Grenade: de kopie als explosief, legergroen met een oranje ring en rode lampjes."""
    sticky("sticky_frag_grenade", "olive", "orange", "glow_red")


def remote_charge(name, shell, ring, light):
    """
    Een lading met een antenne (een blok): dezelfde halve bol als de sticky, maar met een ring om zijn middel, een
    antenne met een rood lampje, en zijn lampjes gloeien altijd (hij wacht op zijn trigger). Hier ligt het kussen
    onderaan (y = 0): zo plakt hij op de vloer; de blockstate draait hem tegen een muur of plafond (net als een knop).
    """
    def layer(y0, y1, r, material, glow=False):
        return [
            box((8 - r, y0, 8 - r * 0.56), (8 + r, y1, 8 + r * 0.56), material, glow),
            box((8 - r * 0.56, y0, 8 - r), (8 + r * 0.56, y1, 8 + r), material, glow),
            box((8 - r * 0.83, y0, 8 - r * 0.83), (8 + r * 0.83, y1, 8 + r * 0.83), material, glow),
        ]
    elements = (layer(0, 0.5, 3.6, "pad")
                + layer(0.5, 2.1, 3.25, shell)
                + layer(2.1, 2.6, 3.05, ring)
                + layer(2.6, 3.8, 2.55, shell)
                + layer(3.8, 4.3, 1.75, "graphite")
                + [box((7.35, 4.3, 7.35), (8.65, 4.5, 8.65), light, glow=True),
                   # Vier lampjes in de ring.
                   box((4.9, 2.2, 7.5), (5.0, 2.5, 8.5), light, glow=True),
                   box((11.0, 2.2, 7.5), (11.1, 2.5, 8.5), light, glow=True),
                   box((7.5, 2.2, 4.9), (8.5, 2.5, 5.0), light, glow=True),
                   box((7.5, 2.2, 11.0), (8.5, 2.5, 11.1), light, glow=True),
                   # De antenne, met een rood lampje bovenop.
                   box((9.6, 3.0, 9.6), (10.2, 7.5, 10.2), "graphite"),
                   box((9.5, 7.5, 9.5), (10.3, 8.1, 10.3), "glow_red", glow=True)])
    block = {"parent": "minecraft:block/block", "ambientocclusion": False,
             "textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements}
    write(f"block/{name}.json", block)
    disp = display(1.7, (0, 3.5, 0), 0.7, 0.7, gui_rotation=(30, -135, 0), third=((0, 0, 0), (0, 3, 1)),
                   first=((10, -30, 0), (0, 3, -2)), first_scale=0.45)
    write(f"item/{name}.json", {"parent": f"imttech:block/{name}", "display": disp})
    # De blockstate: op de vloer rechtop, tegen een muur 90 graden gekanteld, aan het plafond ondersteboven.
    variants = {}
    for face, x in (("floor", 0), ("wall", 90), ("ceiling", 180)):
        for i, facing in enumerate(("north", "east", "south", "west")):
            y = (i * 90 + (180 if face == "ceiling" else 0)) % 360
            variant = {"model": f"imttech:block/{name}"}
            if x:
                variant["x"] = x
            if y:
                variant["y"] = y
            variants[f"face={face},facing={facing}"] = variant
    path = ASSETS / "blockstates" / f"{name}.json"
    path.write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ASSETS.parent.parent.parent.parent.parent))


def remote_emp():
    """De Remote EMP: rood met een stalen ring en cyaan lampjes (de sticky EMP met een antenne)."""
    remote_charge("remote_emp", "red", "steel", "glow")


def remote_frag():
    """
    De Remote Frag (26 oktober, in plaats van C4): de Sticky Frag met een antenne. Legergroen met een oranje ring en
    rode lampjes.
    """
    remote_charge("remote_frag", "olive", "orange", "glow_red")


def emp_trigger():
    """
    De EMP-trigger: een donker handzendertje met een cyaan schermpje, een grote rode knop met een gouden rand, en een
    antenne met een cyaan lampje.
    """
    elements = [
        box((6, 1.5, 7), (10, 11.5, 9.2), "graphite"),
        box((5.8, 1.3, 6.8), (10.2, 2.3, 9.4), "gold"),
        box((6.6, 7.2, 6.85), (9.4, 10.6, 7.0), "screen", glow=True),
        box((6.6, 3.0, 6.75), (9.4, 6.4, 7.0), "gold"),
        box((7.0, 3.4, 6.4), (9.0, 6.0, 6.75), "red"),
        box((8.7, 11.5, 7.8), (9.3, 15.0, 8.4), "steel"),
        box((8.6, 15.0, 7.7), (9.4, 15.6, 8.5), "glow", glow=True),
    ]
    disp = display(1.25, (0, 0, 0), 0.6, 0.6, gui_rotation=(20, 160, 0), third=((0, 180, 0), (0, 3, 1)),
                   first=((0, 160, 0), (0, 3, -2)), first_scale=0.5)
    write("item/emp_trigger.json", item_model(elements, disp))


def frag_trigger():
    """
    De Frag Trigger (jouw wens: "een nieuwe mooie 3D-model zoals de EMP trigger"): een stevige legergroene ontsteker. Een
    oranje gevarenband, rubberen grepen aan de zijkanten, een klein rood schermpje, een dikke rubberen antenne met een
    rood lampje, een tuimelschakelaar met een rood lampje (gewapend), en een grote rode knop onder een oranje klepje dat
    openstaat.
    """
    elements = [
        # Het kastje, met een donkere voet en een oranje band bovenaan.
        box((5.6, 1.5, 6.9), (10.4, 10.5, 9.5), "olive"),
        box((5.4, 0.8, 6.7), (10.6, 1.6, 9.7), "graphite"),
        box((5.5, 9.6, 6.8), (10.5, 10.2, 9.6), "orange"),
        # Rubberen grepen aan de zijkanten.
        box((5.35, 2.2, 7.2), (5.6, 8.2, 9.2), "black"),
        box((10.4, 2.2, 7.2), (10.65, 8.2, 9.2), "black"),
        # Het schermpje: een zwarte rand met een rode gloed.
        box((6.4, 7.5, 6.75), (9.6, 9.2, 6.9), "black"),
        box((6.7, 7.8, 6.68), (9.3, 8.9, 6.75), "glow_red", glow=True),
        # De knop: een stalen kader en een grote rode knop.
        box((6.3, 2.6, 6.6), (9.7, 6.6, 6.9), "steel_dark"),
        box((6.9, 3.2, 6.2), (9.1, 6.0, 6.6), "red"),
        # Het klepje over de knop, open: scharnieren bovenaan, en het klepje schuin omhoog.
        box((6.4, 6.5, 6.45), (7.0, 7.0, 6.9), "steel"),
        box((9.0, 6.5, 6.45), (9.6, 7.0, 6.9), "steel"),
        box((6.2, 6.6, 3.0), (9.8, 6.9, 6.6), "orange", rot=("x", 45, (8, 6.75, 6.6))),
        # Een dikke rubberen antenne links, met een rood lampje.
        box((6.0, 10.5, 7.8), (6.8, 14.4, 8.6), "black"),
        box((5.95, 14.4, 7.75), (6.85, 15.0, 8.65), "glow_red", glow=True),
        # Een tuimelschakelaar rechts bovenop, en ernaast een rood lampje: hij is gewapend.
        box((8.6, 10.5, 7.6), (9.6, 10.85, 8.6), "steel_dark"),
        box((8.95, 10.85, 7.95), (9.25, 12.1, 8.25), "steel", rot=("x", -22.5, (9.1, 10.85, 8.1))),
        box((7.3, 10.5, 8.3), (7.9, 10.75, 8.9), "glow_red", glow=True),
    ]
    disp = display(1.25, (0, 0, 0), 0.6, 0.6, gui_rotation=(20, 160, 0), third=((0, 180, 0), (0, 3, 1)),
                   first=((0, 160, 0), (0, 3, -2)), first_scale=0.5)
    write("item/frag_trigger.json", item_model(elements, disp))


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


# ---------------------------------------------------------------- De geweren (hoofdstuk 55: uit onderdelen)

GRIP = 22.5  # zo schuin staat elke handgreep (graden)

# De twee kwaliteiten van een laseronderdeel (hoofdstuk 55, jouw keuze: titanium en netherite). Titanium ziet eruit
# zoals de geweren altijd al waren (donker metaal en staal); bij netherite is al het metaal donker paarsbruin, zoals een
# netherite ingot. Het rood en het goud (de I.M.T-kleuren) blijven.
GRADES = ("titanium", "netherite")
# De zes onderdelen van elk lasergeweer, in deze volgorde (zie weapons/LaserPart.java): het frame (romp onderaan, kolf,
# greep), de receiver (het bovenstuk, met wat bij een schot beweegt), de emitter (loop en lens), de optics (vizier of
# kijker), de capacitor (het gloeiende blokje opzij) en de trigger.
PARTS = ("frame", "receiver", "emitter", "optics", "capacitor", "trigger")
# De "material"-override van een los onderdeel als item (zie GunMaterial: polymeer 0, staal 1, titanium 2, netherite 3).
GRADE_INDEX = {"titanium": 2, "netherite": 3}


def metal(material, grade):
    """Het metaal van een doosje in deze kwaliteit: bij netherite worden donker metaal en staal netherite."""
    if grade == "netherite":
        return {"graphite": "netherite_dark", "steel": "netherite", "steel_dark": "netherite_dark"}.get(material, material)
    return material


def grip(z0, z1, y0, y1, pivot):
    """Een schuine handgreep van rubber met rode zijplaatjes, gekanteld om {pivot}."""
    rot = ("x", GRIP, pivot)
    return [
        box((7, y0, z0), (9, y1, z1), "black", rot=rot),
        *both(box((6.85, y0 + 0.8, z0 + 0.4), (7.0, y1 - 0.6, z1 - 0.5), "red_dark", rot=rot)),
        box((7.3, y0 - 0.3, z0 + 0.2), (8.7, y0, z1 - 0.2), "graphite", rot=rot),
    ]


def trigger_guard(z0, z1, y_bottom, y_top, material="graphite"):
    return [
        box((7.55, y_bottom, z0), (8.45, y_bottom + 0.5, z1), material),
        box((7.55, y_bottom, z0), (8.45, y_top, z0 + 0.5), material),
    ]


def emitter(z_front, y_center, size, ring_material="gold"):
    """De voorkant van een loop: een ring en een gloeiende lens (de laser komt hieruit)."""
    h = size / 2
    return [
        box((8 - h - 0.25, y_center - h - 0.25, z_front), (8 + h + 0.25, y_center + h + 0.25, z_front + 1), ring_material),
        box((8 - h + 0.3, y_center - h + 0.3, z_front - 0.1), (8 + h - 0.3, y_center + h - 0.3, z_front), "glow", glow=True),
    ]


def strap(z_front, z_back, y, x=9.55):
    """
    De sling (hoofdstuk 55, jouw keuze: een los onderdeel dat je op de tafel aan de gun hangt): een leren band langs de
    rechterkant van het geweer, van de voorkant naar de kolf, met een gesp in het midden. Alleen rifle, sniper en shotgun.
    """
    mid = (z_front + z_back) / 2
    return [
        box((x, y, z_front), (x + 0.4, y + 0.4, z_back), "leather"),
        box((x, y, z_front), (x + 0.4, y + 3.4, z_front + 0.4), "leather"),
        box((x, y, z_back - 0.4), (x + 0.4, y + 2.8, z_back), "leather"),
        box((x - 0.05, y - 0.15, mid - 0.7), (x + 0.5, y + 0.6, mid + 0.7), "gold_dark"),
    ]


def core(x0, y0, z0, x1, y1, z1):
    """
    De kern: de Nether Star (hoofdstuk 55, jouw keuze: een los item dat je zichtbaar insteekt en er weer uit kunt halen).
    Een gloeiend blokje achter in de romp dat aan beide kanten net uit het metaal steekt; zonder kern schiet het geweer niet.
    """
    return [box((x0, y0, z0), (x1, y1, z1), "star", glow=True)]


def laser_pistol(grade):
    """
    Het laserpistool: een rode slede (de receiver, die bij elk schot terugschuift, met het vizier erop) op een donker
    frame met een schuine greep, de lens vooraan (de emitter), de capacitor als gloeiend blokje boven de greep, en de cel
    onderin de greep. Ongeveer 13 pixels lang.
    """
    G = metal("graphite", grade)
    S = metal("steel", grade)
    pivot = (8, 8, 11)
    parts = {
        "frame": [
            box((6.75, 8, 3.5), (9.25, 9.5, 13), G),
            box((7, 7.5, 3.5), (9, 8, 7), G),
            box((7.25, 7.6, 12.8), (8.75, 9.2, 13.4), "gold"),
            *grip(9.6, 12.8, 2.6, 8.2, pivot),
            *trigger_guard(6, 9.6, 5.6, 8, G),
        ],
        "receiver": [],
        "emitter": [*emitter(1, 11, 2.6), box((6.6, 9.4, 1.9), (9.4, 12.6, 2.2), G)],
        "optics": [],
        "capacitor": [
            box((7.0, 8.15, 10.4), (9.0, 9.45, 12.7), "gold_dark"),
            *both(box((6.63, 8.4, 10.7), (6.75, 9.2, 12.4), "glow", glow=True)),
        ],
        "trigger": [box((7.6, 6.3, 7.9), (8.4, 8, 8.5), S)],
    }
    moving = {
        # De slede: schuift bij elk schot even naar achteren; het vizier zit erop en gaat mee.
        "receiver": [
            box((6.5, 9.5, 2.2), (9.5, 12.5, 13.2), "red"),
            box((7.25, 12.5, 3), (8.75, 13, 12.4), "gold"),
            *[box((6.4, 10, z), (9.6, 12.2, z + 0.3), "red_dark") for z in (10.6, 11.3, 12.0)],
            *both(box((6.38, 10.6, 3.6), (6.5, 11.0, 9.8), "glow", glow=True)),
            *both(box((6.4, 11.3, 4.5), (6.5, 12.1, 7.5), "lens")),
        ],
        "optics": [
            box((7.7, 13, 3.2), (8.3, 13.6, 3.9), G),
            box((7.0, 13, 11.6), (7.6, 13.8, 12.4), G),
            box((8.4, 13, 11.6), (9.0, 13.8, 12.4), G),
        ],
    }
    cell = [
        box((7.15, 1.4, 9.9), (8.85, 3.2, 12.5), "cell", rot=("x", GRIP, pivot)),
        box((7.45, 1.25, 10.3), (8.55, 1.4, 12.1), "glow", glow=True, rot=("x", GRIP, pivot)),
    ]
    return parts, moving, cell, core(6.63, 8.35, 8.4, 9.37, 9.25, 9.7), None


def laser_rifle(grade):
    """
    Het lasergeweer: het frame (de onderkant van de romp, de kolf, de greep en de celhouder), de receiver (de rode romp
    met de spanhendel), de emitter (de handbescherming met sleuven, de stalen loop met gouden ringen en de lens), het
    holografische vizier, de capacitor rechts opzij, de trekker, en de cel als magazijn voor de trekker.
    """
    G = metal("graphite", grade)
    S = metal("steel", grade)
    parts = {
        "frame": [
            box((6.6, 9, 2), (9.4, 9.7, 13), "red"),
            box((7, 8.5, 13), (9, 12, 19), "red"),
            box((7.25, 12, 13.5), (8.75, 12.5, 18), "gold"),
            box((6.9, 7.5, 19), (9.1, 12.25, 20), "black"),
            box((7.1, 9.2, 14.2), (8.9, 10.6, 17.6), "red_dark"),
            *grip(10, 12.6, 4, 9.2, (8, 9, 11)),
            *trigger_guard(7.5, 10, 6.5, 9, G),
            box((6.85, 8.6, 3.6), (9.15, 9.1, 7.4), "gold_dark"),
        ],
        "receiver": [
            box((6.5, 9.7, 2), (9.5, 12.5, 13), "red"),
            box((6.4, 10.2, 3), (9.6, 10.5, 12), "red_dark"),
            box((7, 12.5, 2.5), (9, 13, 12.5), G),
            box((9.5, 11, 6.5), (9.6, 11.75, 11), "red_dark"),
        ],
        "emitter": [
            box((6.75, 9.25, -4), (9.25, 12, 2), G),
            *both(box((6.65, 9.9, -3.2), (6.75, 11.3, 1.2), "vent")),
            *both(box((6.55, 11.45, -3.6), (6.7, 11.75, 1.6), "glow", glow=True)),
            box((7.25, 8.75, -3.5), (8.75, 9.25, 1.5), G),
            box((7.25, 10, -8.5), (8.75, 11.75, -4), S),
            box((7, 9.75, -6), (9, 12, -5.4), "gold"),
            *emitter(-9.5, 10.875, 2.2),
        ],
        "optics": [
            box((7, 13, 6), (9, 13.5, 10), G),
            box((6.75, 13.5, 6.5), (7.25, 15.75, 7.1), "gold"),
            box((8.75, 13.5, 6.5), (9.25, 15.75, 7.1), "gold"),
            box((6.75, 15.75, 6.5), (9.25, 16.2, 7.1), "gold"),
            box((7, 13.5, 7.1), (9, 13.8, 9.5), G),
            box((7.9, 14.55, 6.75), (8.1, 14.75, 6.85), "glow", glow=True),
        ],
        "capacitor": [
            box((9.5, 9.9, 2.8), (9.85, 11.2, 6.2), "gold_dark"),
            box((9.85, 10.2, 3.1), (9.97, 10.9, 5.9), "glow", glow=True),
        ],
        "trigger": [box((7.6, 7, 8.6), (8.4, 9, 9.2), S)],
    }
    moving = {"receiver": [box((9.5, 11, 10), (10.3, 11.75, 11), S), box((10.3, 10.9, 10.1), (10.6, 11.85, 10.9), "gold")]}
    cell = [
        box((7, 4.2, 4), (9, 8.6, 7), "cell"),
        *both(box((6.9, 5, 4.9), (7.0, 7.8, 6.1), "glow", glow=True)),
        box((6.95, 4, 3.9), (9.05, 4.5, 7.1), "gold"),
    ]
    return parts, moving, cell, core(6.38, 10.7, 11.3, 9.62, 11.9, 12.5), strap(-3.0, 19.0, 5.6)


def laser_sniper(grade):
    """
    De lasersniper: het frame (de onderkant van de romp en de lange kolf met het duimgat), de receiver (de romp met de
    grendel), de emitter (de lange loop met sleuven en de gouden mondingsrem), de kijker, de capacitor, de trekker.
    """
    G = metal("graphite", grade)
    S = metal("steel", grade)
    parts = {
        "frame": [
            box((6.6, 9, 4), (9.4, 9.7, 14), "red"),
            box((7, 11, 14), (9, 12.5, 24), "red"),
            box((7, 7.5, 18), (9, 11, 24), "red"),
            box((7.25, 12.5, 15), (8.75, 13, 22), "gold"),
            box((6.9, 7, 24), (9.1, 12.75, 25), "black"),
            *grip(13, 15.6, 5, 10.2, (8, 10, 14)),
            *trigger_guard(10, 13, 7.2, 9, G),
        ],
        "receiver": [box((6.5, 9.7, 4), (9.5, 12.5, 14), "red"), box((6.4, 10.4, 5), (9.6, 10.7, 13), "red_dark")],
        "emitter": [
            box((6.85, 9.6, -10), (9.15, 12, 4), G),
            *both(box((6.75, 10.2, -9), (6.85, 11.4, 2.5), "vent")),
            *both(box((6.65, 11.55, -9.5), (6.8, 11.8, 3), "glow", glow=True)),
            box((7.25, 9.1, -8), (8.75, 9.6, -2), G),
            box((6.5, 9.3, -13), (9.5, 12.3, -10), "gold"),
            *both(box((6.4, 10.3, -12.4), (6.5, 11.3, -10.6), G)),
            *emitter(-14, 10.8, 2.0, "gold_dark"),
        ],
        "optics": [
            box((7.25, 12.5, 2), (8.75, 13.5, 3), "gold"),
            box((7.25, 12.5, 9), (8.75, 13.5, 10), "gold"),
            box((7, 13.5, 0), (9, 15.5, 12), "black"),
            box((6.5, 13, -1.5), (9.5, 16, 0.5), G),
            box((6.75, 13.25, 11), (9.25, 15.75, 13), G),
            box((7, 13.25, -1.6), (9, 15.75, -1.5), "lens"),
            box((9, 14, 4), (9.6, 15, 5.5), "gold"),
        ],
        "capacitor": [
            box((9.5, 9.9, 6.0), (9.85, 11.3, 9.4), "gold_dark"),
            box((9.85, 10.2, 6.3), (9.97, 11.0, 9.1), "glow", glow=True),
        ],
        "trigger": [box((7.6, 7.6, 11.4), (8.4, 9, 12), S)],
    }
    moving = {"receiver": [box((9.5, 11, 11), (10.8, 11.6, 11.8), S), box((10.6, 10.6, 10.9), (11.4, 11.6, 11.9), "gold")]}
    cell = [
        box((7, 6, 6), (9, 9, 9.5), "cell"),
        *both(box((6.9, 6.6, 6.8), (7.0, 8.4, 8.7), "glow", glow=True)),
        box((6.95, 5.8, 5.9), (9.05, 6.3, 9.6), "gold"),
    ]
    return parts, moving, cell, core(6.38, 10.9, 12.3, 9.62, 12.1, 13.5), strap(-7.0, 24.0, 5.8)


def laser_shotgun(grade):
    """
    De laser-shotgun: het frame (de onderkant van de brede romp en de kolf), de receiver (de romp met de gouden rib), de
    emitter (twee lopen naast elkaar met de buis en de rubber pomp eronder, die na elk schot heen en weer gaat), een
    korrel en een keep op de rib, de capacitor, de trekker, en de cel onder de romp.
    """
    G = metal("graphite", grade)
    S = metal("steel", grade)
    parts = {
        "frame": [
            box((6.35, 8.5, 2), (9.65, 9.3, 13), "red"),
            box((7, 8, 13), (9, 12, 19), "red"),
            box((6.9, 7.25, 19), (9.1, 12.25, 20), "black"),
            box((7.2, 9.3, 14), (8.8, 10.8, 18), "red_dark"),
            *grip(10, 12.6, 3.8, 9, (8, 8.5, 11)),
            *trigger_guard(7.2, 10, 6.4, 8.5, G),
        ],
        "receiver": [
            box((6.25, 9.3, 2), (9.75, 12.75, 13), "red"),
            box((7.25, 12.75, 2.5), (8.75, 13.25, 12.5), "gold"),
            *both(box((6.15, 9.5, 3), (6.25, 12, 11), "red_dark")),
            *both(box((6.1, 11.2, 3.5), (6.2, 11.5, 10.5), "glow", glow=True)),
        ],
        "emitter": [
            box((6.5, 10, -6), (7.9, 11.6, 2), S),
            box((8.1, 10, -6), (9.5, 11.6, 2), S),
            box((6.3, 9.8, -6.6), (9.7, 11.8, -5.6), "gold"),
            box((6.75, 10.25, -6.7), (7.65, 11.35, -6.6), "glow", glow=True),
            box((8.35, 10.25, -6.7), (9.25, 11.35, -6.6), "glow", glow=True),
            box((6.3, 9.8, -2.2), (9.7, 11.8, -1.6), "gold"),
            box((7.1, 8.4, -5), (8.9, 9.8, 2), G),
        ],
        "optics": [
            box((7.75, 13.25, 2.6), (8.25, 13.8, 3.1), "glow_white", glow=True),
            box((7.2, 13.25, 11.6), (7.7, 13.75, 12.2), G),
            box((8.3, 13.25, 11.6), (8.8, 13.75, 12.2), G),
        ],
        "capacitor": [
            box((9.75, 9.8, 3.4), (10.1, 11.1, 6.6), "gold_dark"),
            box((10.1, 10.1, 3.7), (10.22, 10.8, 6.3), "glow", glow=True),
        ],
        "trigger": [box((7.6, 6.9, 8.3), (8.4, 8.5, 8.9), S)],
    }
    moving = {"emitter": [
        box((6.6, 7.8, -4.4), (9.4, 10.1, -0.2), "black"),
        *[box((6.5, 8.2, z), (9.5, 9.9, z + 0.4), "red_dark") for z in (-3.6, -2.6, -1.6)],
    ]}
    cell = [
        box((7, 6.2, 4), (9, 8.5, 7.5), "cell"),
        *both(box((6.9, 6.6, 4.8), (7.0, 8.1, 6.7), "glow", glow=True)),
        box((6.95, 6.0, 3.9), (9.05, 6.5, 7.6), "gold"),
    ]
    return parts, moving, cell, core(6.13, 10.3, 11.2, 9.87, 11.5, 12.4), strap(-5.0, 19.0, 5.6, x=9.8)


GUNS = {
    "laser_pistol": (laser_pistol, gun_display(1.0, (0, 0, 0), 0.6, (8, 6.5, 11.4))),
    "laser_rifle": (laser_rifle, gun_display(0.55, (0, 0, -1.5), 0.5, (8, 7.2, 11.3))),
    "laser_sniper": (laser_sniper, gun_display(0.42, (0, 0, -1), 0.45, (8, 8.2, 14.4))),
    "laser_shotgun": (laser_shotgun, gun_display(0.6, (0, 0, -1), 0.55, (8, 7.0, 11.3))),
}


def centre_of(elements):
    """Het midden en de maten van een lijst doosjes (zonder hun draaiing)."""
    xs = [v for e in elements for v in (e["from"][0], e["to"][0])]
    ys = [v for e in elements for v in (e["from"][1], e["to"][1])]
    zs = [v for e in elements for v in (e["from"][2], e["to"][2])]
    return ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2), \
        (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))


def centred_display(elements):
    """
    Een houding die een los onderdeel van opzij in het midden van zijn vakje zet, zo groot als er past (een frame is
    lang, een trekker klein). Zoals generate_gun_models.centred_display voor de Glock.
    """
    (cx, cy, cz), (dx, dy, dz) = centre_of(elements)
    gui_scale = round(min(4.5, 13.0 / max(1.0, dy, dz, dx * 0.4)), 3)
    hand_scale = 0.6
    shift = [round(-(cx - 8) * gui_scale, 2), round(-(cy - 8) * gui_scale, 2), round(-(cz - 8) * gui_scale, 2)]
    hand_shift = [round(-(cx - 8) * hand_scale, 2), round(-(cy - 8) * hand_scale, 2), round(-(cz - 8) * hand_scale, 2)]
    disp = display(gui_scale, shift, hand_scale, 0.5, gui_rotation=(0, 90, 0),
                   third=((0, 0, 0), hand_shift), first=((0, 0, 0), hand_shift))
    disp["ground"]["translation"] = [0, 2 - (cy - 8) * 0.5, 0]
    return disp


def save_gun(name):
    """
    Eén lasergeweer: elk onderdeel in elke kwaliteit apart (models/weapon/<geweer>/<onderdeel>_<kwaliteit>.json, en
    ..._moving.json voor wat bij een schot beweegt), de cel, de kern en de sling, het hele geweer als item (titanium, met
    cel en kern; voor je inventaris, je hand in derde persoon en op de grond), en elk los onderdeel als item
    (models/item/<geweer>_<onderdeel>.json, met een override per kwaliteit, zoals de onderdelen van de Glock).
    """
    make, disp = GUNS[name]
    whole = []
    for grade in GRADES:
        parts, moving, cell, core_el, sling = make(grade)
        for part in PARTS:
            write(f"weapon/{name}/{part}_{grade}.json", part_model(parts[part]))
            if moving.get(part):
                write(f"weapon/{name}/{part}_{grade}_moving.json", part_model(moving[part]))
        if grade == "titanium":
            for part in PARTS:
                whole += parts[part] + moving.get(part, [])
            whole += cell + core_el
            write(f"weapon/{name}/cell.json", part_model(cell))
            write(f"weapon/{name}/core.json", part_model(core_el))
            if sling:
                write(f"weapon/{name}/sling.json", part_model(sling))
    write(f"item/{name}.json", item_model(whole, disp))
    for part in PARTS:
        overrides = []
        base = None
        for grade in GRADES:
            parts, moving, _, _, _ = make(grade)
            elements = parts[part] + moving.get(part, [])
            write(f"item/{name}_{part}_{grade}.json", item_model(elements, centred_display(elements)))
            overrides.append({"predicate": {"imttech:material": GRADE_INDEX[grade]}, "model": f"imttech:item/{name}_{part}_{grade}"})
            if grade == "titanium":
                base = elements
        write(f"item/{name}_{part}.json", item_model(base, centred_display(base), overrides))


def gun_sling():
    """De Gun Sling als los item: een opgerolde leren band met een gesp (leer en touw aan de werkbank)."""
    el = [
        box((4, 7.2, 4.5), (12, 8.0, 5.3), "leather"),
        box((4, 7.2, 10.7), (12, 8.0, 11.5), "leather"),
        box((4, 7.2, 4.5), (4.8, 8.0, 11.5), "leather"),
        box((11.2, 7.2, 4.5), (12, 8.0, 11.5), "leather"),
        box((7.3, 7.0, 4.0), (8.7, 8.3, 5.8), "gold_dark"),
    ]
    write("item/gun_sling.json", item_model(el, display(1.0, (0, 0, 0), 0.6, 0.6, gui_rotation=(30, 45, 0))))
    # De band over je lijf (zie client/guns/GearLayer): een rechte band van 13 pixels, die de mod schuin over je borst
    # of rug legt als er een geweer met een sling op je rug of voor je borst hangt.
    write("weapon/sling_strap.json", part_model([box((7.3, 0, 0), (8.7, 13, 0.5), "leather")]))


def main():
    materials()
    emp_grenade()
    frag_grenade()
    smoke_grenade()
    flashbang()
    sticky_emp_grenade()
    sticky_frag_grenade()
    remote_emp()
    emp_trigger()
    remote_frag()
    frag_trigger()
    energy_cell()
    for name in GUNS:
        save_gun(name)
    gun_sling()


if __name__ == "__main__":
    main()
