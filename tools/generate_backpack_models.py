"""
Maakt de modellen en plaatjes van de militaire rugzakken (hoofdstuk 72): de Assault Pack (klein, 18 vakjes), de
Patrol Pack (27) en de Rucksack (36, met een titanium frame en een slaapmat). Alle drie hebben aan de voorkant
MOLLE-banden (rijen stevige band waar je pouches aan hangt), een klep bovenop, een handvat, schouderbanden aan de
achterkant en zwarte gespen.

Kleur: de stof is lichtgrijs en wordt in het spel gekleurd (tintindex 0, zoals leren harnas): olijfgroen als je hem net
maakt, of de kleur waarmee je hem verfde (zie client/backpack/BackpackClient). De band, de gespen en het frame hebben
hun eigen kleur en worden niet gekleurd. Voor camo (jouw wens: bruin + licht- en donkergroen, "een kleur op zich")
schrijft dit script van elk model een tweede versie met een camoplaatje en zonder kleuring; het item kiest die via de
override imttech:camo.

Zo staat het model: de voorkant (met de MOLLE-banden) kijkt naar het noorden (-z), de schouderbanden zitten aan de
zuidkant (+z), de onderkant ligt op y = 0. Zo zet de mod hem neer als blok (client/backpack/BackpackRenderer) en zo hangt
hij op je rug (client/backpack/BackpackLayer).

Draaien (vanuit de hoofdmap):  python tools/generate_backpack_models.py
Het script is eigenaar van alles wat het schrijft; een volledige run is veilig.
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

import model_display as md

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "imttech"

SIZES = ("assault_pack", "patrol_pack", "rucksack")


def write(rel, data):
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT))


def save(im, name):
    path = ASSETS / "textures" / "item" / "backpack" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ROOT))


def noisy(colour, seed, amount=6, size=16):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            d = rnd.randint(-amount, amount)
            im.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in colour[:3]) + (255,))
    return im


def shade(im, x, y, amount):
    c = im.getpixel((x, y))
    im.putpixel((x, y), tuple(max(0, min(255, v + amount)) for v in c[:3]) + (255,))


# ---------------------------------------------------------------- De plaatjes

# De stof: lichtgrijs, zodat de kleur in het spel er goed op komt (grijs x kleur = die kleur, iets donkerder).
FABRIC = (214, 214, 210)
# De zwarte band (schouderbanden, gespen, handvat).
WEBBING = (34, 35, 37)
BUCKLE = (58, 60, 64)
FRAME = (150, 156, 164)
# Camo (jouw wens): bruin, lichtgroen en donkergroen.
CAMO_BROWN = (104, 80, 52)
CAMO_LIGHT = (118, 142, 70)
CAMO_DARK = (50, 74, 40)


def weave(base, seed):
    """Cordura: een fijn weefsel, om de pixel een tikje lichter of donkerder."""
    im = noisy(base, seed, 5)
    for y in range(16):
        for x in range(16):
            if (x + y) % 2 == 0:
                shade(im, x, y, 4)
    return im


def molle_rows(im):
    """MOLLE: horizontale banden om de 3 pixels, met om de 2 pixels een stiksel (waar je een pouch doorheen vlecht)."""
    for top in (1, 5, 9, 13):
        for x in range(16):
            shade(im, x, top, -62)
            shade(im, x, top + 1, -44)
        for x in range(0, 16, 3):
            shade(im, x, top, -22)
            shade(im, x, top + 1, -22)
        for x in range(16):
            shade(im, x, top + 2, 18)
            shade(im, x, (top - 1) % 16, 8)


def camo(seed):
    """Woodland-camo: vlekken bruin en donkergroen op lichtgroen, met hier en daar een zwarte streep."""
    rnd = random.Random(seed)
    im = noisy(CAMO_LIGHT, seed, 6)
    d = ImageDraw.Draw(im)
    for colour, count, size in ((CAMO_BROWN, 5, 4), (CAMO_DARK, 6, 4), (CAMO_BROWN, 4, 2)):
        for _ in range(count):
            x, y = rnd.randint(-2, 15), rnd.randint(-2, 15)
            w, h = rnd.randint(2, size + 2), rnd.randint(1, size)
            d.ellipse([x, y, x + w, y + h], fill=colour + (255,))
            # Naadloos: dezelfde vlek ook aan de andere kant van het plaatje.
            d.ellipse([x - 16, y, x - 16 + w, y + h], fill=colour + (255,))
            d.ellipse([x, y - 16, x + w, y - 16 + h], fill=colour + (255,))
    for _ in range(3):
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        d.line([x, y, x + rnd.randint(1, 3), y + rnd.randint(-1, 1)], fill=(30, 32, 26, 255))
    out = noisy((0, 0, 0), seed + "n", 0)
    for y in range(16):
        for x in range(16):
            out.putpixel((x, y), im.getpixel((x, y)))
            if (x + y) % 2 == 0:
                shade(out, x, y, 5)
    return out


def textures():
    fabric = weave(FABRIC, "fabric")
    save(fabric, "fabric")
    molle = weave(FABRIC, "molle")
    molle_rows(molle)
    save(molle, "molle")
    fabric_camo = camo("camo")
    save(fabric_camo, "fabric_camo")
    molle_camo = camo("camo_molle")
    molle_rows(molle_camo)
    save(molle_camo, "molle_camo")
    # De band: zwart, met een stiksel langs beide randen.
    webbing = noisy(WEBBING, "webbing", 3)
    for y in range(16):
        for x in (1, 14):
            if y % 2 == 0:
                shade(webbing, x, y, 26)
    save(webbing, "webbing")
    buckle = noisy(BUCKLE, "buckle", 4)
    d = ImageDraw.Draw(buckle)
    d.rectangle([2, 2, 13, 13], outline=(84, 88, 94, 255))
    save(buckle, "buckle")
    frame = noisy(FRAME, "frame", 5)
    for y in range(16):
        shade(frame, 0, y, 24)
        shade(frame, 15, y, -24)
    save(frame, "frame")
    # De slaapmat bovenop de Rucksack: donker schuim met ribbels.
    mat = noisy((60, 72, 58), "mat", 4)
    for x in range(0, 16, 2):
        for y in range(16):
            shade(mat, x, y, -12)
    save(mat, "bedroll")
    # Het rode kruis van de IFAK (de medkit-pouch): wit vlak met een rood kruis.
    cross = noisy((230, 226, 218), "cross", 3)
    d = ImageDraw.Draw(cross)
    d.rectangle([6, 2, 9, 13], fill=(196, 34, 34, 255))
    d.rectangle([2, 6, 13, 9], fill=(196, 34, 34, 255))
    save(cross, "cross")
    # Een stukje olijfgroene stof voor de brokjes als je een neergezette rugzak breekt.
    particle = weave((92, 99, 58), "particle")
    save(particle, "particle")


# ---------------------------------------------------------------- De doosjes

DIRS = ("north", "south", "east", "west", "up", "down")


def face_uv(direction, fr, to):
    """
    Een uv binnen het plaatje (0 tot 16), zo groot als de kant echt is. Zonder uv rekent Minecraft hem uit de plek van
    het doosje, en een doosje boven y = 16 (de slaapmat) zou dan buiten zijn plaatje lezen.
    """
    if direction in ("north", "south"):
        w, h, u, v = to[0] - fr[0], to[1] - fr[1], fr[0], 16 - to[1]
    elif direction in ("east", "west"):
        w, h, u, v = to[2] - fr[2], to[1] - fr[1], fr[2], 16 - to[1]
    else:
        w, h, u, v = to[0] - fr[0], to[2] - fr[2], fr[0], fr[2]
    w, h = min(w, 16), min(h, 16)
    u = max(0.0, min(u % 16, 16 - w))
    v = max(0.0, min(v % 16, 16 - h))
    return [round(u, 3), round(v, 3), round(u + w, 3), round(v + h, 3)]


def box(fr, to, tex, tint=None, only=None, special=None):
    """Een doosje: elke kant met plaatje {tex} (of {special}[kant]); stof is gekleurd (tintindex 0), band en frame niet."""
    tint = tex in ("#fabric", "#molle") if tint is None else tint
    faces = {}
    for direction in DIRS:
        if only and direction not in only:
            continue
        texture = (special or {}).get(direction, tex)
        face = {"uv": face_uv(direction, fr, to), "texture": texture}
        if texture in ("#fabric", "#molle"):
            face["tintindex"] = 0
        faces[direction] = face
    return {"from": [round(c, 3) for c in fr], "to": [round(c, 3) for c in to], "faces": faces}


def straps(x0, x1, top, bottom, back_z, thickness=0.6, width=1.3):
    """Twee schouderbanden aan de achterkant (zuid), van boven naar beneden, met een gesp halverwege."""
    out = []
    for x in (x0, x1 - width):
        out.append(box([x, bottom, back_z], [x + width, top, back_z + thickness], "#webbing"))
        mid = bottom + (top - bottom) * 0.35
        out.append(box([x - 0.15, mid, back_z + thickness], [x + width + 0.15, mid + 0.9, back_z + thickness + 0.3], "#buckle"))
    return out


def handle(cx, top, z):
    """Het handvat bovenop: een lusje band."""
    return [box([cx - 1.2, top, z - 0.3], [cx + 1.2, top + 0.6, z + 0.3], "#webbing")]


def compression(x_side, bottom, top, z0, z1, east):
    """Een compressieband opzij (rond de zijkant), met een gesp."""
    t = 0.3
    x0, x1 = (x_side, x_side + t) if east else (x_side - t, x_side)
    out = []
    for y in (bottom + (top - bottom) * 0.3, bottom + (top - bottom) * 0.72):
        out.append(box([x0, y, z0], [x1, y + 0.8, z1], "#webbing"))
        bx0, bx1 = (x1, x1 + 0.25) if east else (x0 - 0.25, x0)
        zc = (z0 + z1) / 2
        out.append(box([bx0, y - 0.1, zc - 0.5], [bx1, y + 0.9, zc + 0.5], "#buckle"))
    return out


def front_pocket(x0, x1, y0, y1, z_front, depth, buckles=2):
    """Een zak op de voorkant met MOLLE erop en gespen aan de bovenrand."""
    out = [box([x0, y0, z_front - depth], [x1, y1, z_front], "#fabric", special={"north": "#molle"})]
    span = x1 - x0
    for i in range(buckles):
        cx = x0 + span * (i + 1) / (buckles + 1)
        out.append(box([cx - 0.5, y1 - 1.6, z_front - depth - 0.25], [cx + 0.5, y1 + 0.2, z_front - depth], "#webbing"))
        out.append(box([cx - 0.6, y1 - 2.2, z_front - depth - 0.35], [cx + 0.6, y1 - 1.4, z_front - depth - 0.1], "#buckle"))
    return out


def assault_pack():
    # Het lijf: 8 breed, 10 hoog, 4 diep (een speler is 8 x 12 x 4).
    x0, x1, z0, z1, top = 4.0, 12.0, 6.0, 10.0, 10.0
    els = [box([x0, 0, z0], [x1, top, z1], "#fabric", special={"north": "#molle"})]
    # De klep bovenop, iets breder en ronder.
    els.append(box([x0 + 0.25, top - 0.5, z0 - 0.25], [x1 - 0.25, top + 0.75, z1 + 0.1], "#fabric"))
    els += front_pocket(5.0, 11.0, 1.2, 6.2, z0, 1.3)
    els += handle(8.0, top + 0.75, 8.6)
    els += straps(x0 + 0.8, x1 - 0.8, top - 0.6, 0.8, z1)
    els += compression(x1, 0, top, z0 + 0.6, z1 - 0.6, True)
    els += compression(x0, 0, top, z0 + 0.6, z1 - 0.6, False)
    return els


def patrol_pack():
    # 9 breed, 12 hoog, 5 diep, met twee fleszakken opzij.
    x0, x1, z0, z1, top = 3.5, 12.5, 5.5, 10.5, 12.0
    els = [box([x0, 0, z0], [x1, top, z1], "#fabric", special={"north": "#molle"})]
    els.append(box([x0 + 0.25, top - 0.5, z0 - 0.3], [x1 - 0.25, top + 0.9, z1 + 0.1], "#fabric"))
    els += front_pocket(4.5, 11.5, 1.2, 7.6, z0, 1.4, buckles=2)
    els += handle(8.0, top + 0.9, 8.3)
    els += straps(x0 + 1.0, x1 - 1.0, top - 0.6, 1.0, z1)
    # De zijzakken (een fles of een radio), met MOLLE op de buitenkant.
    els.append(box([x1, 0.8, z0 + 1.0], [x1 + 1.4, 6.0, z1 - 1.0], "#fabric", special={"east": "#molle"}))
    els.append(box([x0 - 1.4, 0.8, z0 + 1.0], [x0, 6.0, z1 - 1.0], "#fabric", special={"west": "#molle"}))
    els += compression(x1, 6.2, top, z0 + 0.6, z1 - 0.6, True)[2:]
    els += compression(x0, 6.2, top, z0 + 0.6, z1 - 0.6, False)[2:]
    return els


def rucksack():
    # 10 breed, 14 hoog, 6 diep, op een titanium frame, met een slaapmat bovenop.
    x0, x1, z0, z1, top = 3.0, 13.0, 4.5, 10.5, 14.0
    els = [box([x0, 0, z0], [x1, top, z1], "#fabric", special={"north": "#molle"})]
    els.append(box([x0 + 0.25, top - 0.5, z0 - 0.3], [x1 - 0.25, top + 1.0, z1 + 0.1], "#fabric"))
    els += front_pocket(4.0, 12.0, 1.2, 6.8, z0, 1.3, buckles=2)
    els += front_pocket(4.6, 11.4, 7.6, 12.4, z0, 1.0, buckles=1)
    els += handle(8.0, top + 1.0, 9.6)
    # Het frame: twee buizen achter het lijf, van onder tot boven de klep, met twee dwarsbuizen.
    fz0, fz1 = z1, z1 + 0.8
    for x in (x0 + 0.4, x1 - 1.2):
        els.append(box([x, -0.6, fz0], [x + 0.8, top + 1.6, fz1], "#frame"))
    for y in (0.4, top - 1.2):
        els.append(box([x0 + 0.4, y, fz0], [x1 - 0.4, y + 0.8, fz1], "#frame"))
    els += straps(x0 + 1.4, x1 - 1.4, top - 0.4, 1.2, fz1)
    # Zijzakken.
    els.append(box([x1, 0.8, z0 + 1.2], [x1 + 1.6, 7.0, z1 - 1.2], "#fabric", special={"east": "#molle"}))
    els.append(box([x0 - 1.6, 0.8, z0 + 1.2], [x0, 7.0, z1 - 1.2], "#fabric", special={"west": "#molle"}))
    els += compression(x1, 7.2, top, z0 + 0.6, z1 - 0.6, True)[2:]
    els += compression(x0, 7.2, top, z0 + 0.6, z1 - 0.6, False)[2:]
    # De slaapmat: een rol bovenop, met twee banden.
    els.append(box([2.2, top + 1.0, 6.2], [13.8, top + 3.2, 8.8], "#bedroll"))
    for x in (4.4, 10.8):
        els.append(box([x, top + 0.9, 6.0], [x + 0.8, top + 3.4, 9.0], "#webbing"))
    return els


BUILDERS = {"assault_pack": assault_pack, "patrol_pack": patrol_pack, "rucksack": rucksack}

# In je inventaris: van voren, een beetje van links en van boven, zodat je de MOLLE-banden en de klep ziet.
GUI_ROTATION = (22, 200, 0)


def display(elements):
    gui = md.gui(elements, GUI_ROTATION, size=15.0)
    return {
        "gui": gui,
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 6, 0], "scale": [0.9, 0.9, 0.9]},
        # Aan je hand, zoals je een tas aan zijn handvat draagt.
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, -1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, -1.5, 1.5], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [1.5, 1, 1], "scale": [0.3, 0.3, 0.3]},
        "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [1.5, 1, 1], "scale": [0.3, 0.3, 0.3]},
    }


def chest_straps():
    """
    De schouderbanden zoals je ze van voren ziet (de mod tekent ze over je lijf, zie BackpackLayer): over je schouders en
    voor je borst naar beneden, met een borstband en twee gespen. Een eigen model (models/backpack/straps.json), in de
    pixels van je lijf: je romp is x 4 tot 12, y 4 (je middel) tot 16 (je schouders), z 6 (je rug) tot 10 (je borst).
    Voor de Rucksack ook een heupband (models/backpack/hip_belt.json).
    """
    tex = {"webbing": "imttech:item/backpack/webbing", "buckle": "imttech:item/backpack/buckle",
           "particle": "imttech:item/backpack/webbing"}
    els = []
    for x0 in (5.2, 9.4):
        x1 = x0 + 1.4
        els.append(box([x0, 5.5, 10.0], [x1, 16.25, 10.45], "#webbing"))
        els.append(box([x0, 16.0, 6.0], [x1, 16.45, 10.45], "#webbing"))
        els.append(box([x0 - 0.15, 6.4, 10.3], [x1 + 0.15, 7.2, 10.65], "#buckle"))
    els.append(box([6.6, 11.0, 10.15], [9.4, 11.6, 10.5], "#webbing"))
    els.append(box([7.4, 10.8, 10.35], [8.6, 11.8, 10.7], "#buckle"))
    write("models/backpack/straps.json", {"textures": tex, "elements": els})
    belt = [box([4.0, 4.2, 10.0], [12.0, 5.4, 10.5], "#webbing"),
            box([3.6, 4.2, 6.2], [4.0, 5.4, 10.5], "#webbing"),
            box([12.0, 4.2, 6.2], [12.4, 5.4, 10.5], "#webbing"),
            box([7.2, 4.0, 10.4], [8.8, 5.6, 10.8], "#buckle")]
    write("models/backpack/hip_belt.json", {"textures": tex, "elements": belt})


def models():
    textures_plain = {"fabric": "imttech:item/backpack/fabric", "molle": "imttech:item/backpack/molle",
                      "webbing": "imttech:item/backpack/webbing", "buckle": "imttech:item/backpack/buckle",
                      "frame": "imttech:item/backpack/frame", "bedroll": "imttech:item/backpack/bedroll",
                      "particle": "imttech:item/backpack/particle"}
    textures_camo = dict(textures_plain, fabric="imttech:item/backpack/fabric_camo", molle="imttech:item/backpack/molle_camo")
    for name in SIZES:
        elements = BUILDERS[name]()
        shown = display(elements)
        # De camo-versie: dezelfde doosjes met het camoplaatje, en geen kleuring.
        camo_elements = json.loads(json.dumps(elements))
        for element in camo_elements:
            for face in element["faces"].values():
                face.pop("tintindex", None)
        write(f"models/item/backpack/{name}_camo.json",
              {"textures": textures_camo, "elements": camo_elements, "display": shown})
        write(f"models/item/{name}.json", {
            "textures": textures_plain, "elements": elements, "display": shown,
            "overrides": [{"predicate": {"imttech:camo": 1}, "model": f"imttech:item/backpack/{name}_camo"}]})
    # Het neergezette blok: de mod tekent de rugzak zelf (BackpackRenderer); het blokmodel heeft alleen de brokjes.
    write("models/block/backpack.json", {"textures": {"particle": "imttech:item/backpack/particle"}})
    write("blockstates/backpack.json", {"variants": {"": {"model": "imttech:block/backpack"}}})


# ---------------------------------------------------------------- De MOLLE-pouches (stap 2)

POUCHES = ("molle_mag_pouch", "molle_grenade_pouch", "molle_dump_pouch", "ifak", "utility_pouch")


def flap(x0, x1, top, z_front, depth=0.5, drop=1.3):
    """Een klep over de bovenkant van een pouch, met een stukje over de voorkant."""
    return [box([x0, top - 0.1, z_front - 0.15], [x1, top + 0.45, 8.0], "#fabric"),
            box([x0, top - drop, z_front - 0.15 - depth * 0.4], [x1, top + 0.35, z_front - 0.15], "#fabric")]


def molle_mag_pouch():
    """Drie magazijnvakjes naast elkaar (6 breed, 4,5 hoog, 1,7 diep), elk met een eigen klep en een trekbandje."""
    z = 8 - 1.7
    els = [box([5.0, 5.5, z], [11.0, 10.0, 8.0], "#fabric", special={"north": "#molle"})]
    for i in range(3):
        x0 = 5.05 + i * 2.0
        els += flap(x0, x0 + 1.9, 10.0, z)
        els.append(box([x0 + 0.65, 8.2, z - 0.55], [x0 + 1.25, 9.4, z - 0.25], "#webbing"))
    return els


def molle_grenade_pouch():
    """Twee granaatvakjes (elk 2,6 breed, 3,4 hoog, 2,4 diep) met een klep en een gesp."""
    z = 8 - 2.4
    els = []
    for x0 in (5.2, 8.2):
        els.append(box([x0, 5.8, z], [x0 + 2.6, 9.2, 8.0], "#fabric", special={"north": "#molle"}))
        els += flap(x0 + 0.05, x0 + 2.55, 9.2, z, drop=1.1)
        els.append(box([x0 + 0.9, 7.3, z - 0.45], [x0 + 1.7, 8.3, z - 0.2], "#buckle"))
    return els


def molle_dump_pouch():
    """Een opgevouwen dump pouch (5 breed, 5,5 hoog, 1,2 diep) met een trekkoord en een stopper bovenaan."""
    z = 8 - 1.2
    els = [box([5.5, 5.0, z], [10.5, 10.5, 8.0], "#fabric", special={"north": "#molle"}),
           box([5.4, 9.6, z - 0.2], [10.6, 10.4, 8.0], "#webbing"),
           box([7.6, 10.4, z + 0.2], [8.4, 11.4, z + 0.8], "#webbing"),
           box([7.4, 11.2, z + 0.1], [8.6, 11.9, z + 0.9], "#buckle")]
    return els


def ifak():
    """De IFAK (Individual First Aid Kit): 4,6 breed, 3,6 hoog, 2 diep, met een rood kruis en een treklus."""
    z = 8 - 2.0
    els = [box([5.7, 5.8, z], [10.3, 9.4, 8.0], "#fabric", special={"north": "#molle"}),
           box([6.9, 6.6, z - 0.12], [9.1, 8.6, z], "#cross"),
           box([5.6, 9.2, z - 0.1], [10.4, 9.6, 8.0], "#webbing"),
           box([7.5, 9.6, z + 0.6], [8.5, 10.6, z + 1.0], "#webbing")]
    return els


def utility_pouch():
    """Een utility pouch (5 breed, 4,6 hoog, 2,2 diep) met een rits rondom en een treklipje."""
    z = 8 - 2.2
    els = [box([5.5, 5.4, z], [10.5, 10.0, 8.0], "#fabric", special={"north": "#molle"}),
           box([5.4, 9.6, z - 0.1], [10.6, 10.1, 8.0], "#webbing"),
           box([9.2, 9.0, z - 0.4], [9.8, 9.8, z - 0.1], "#buckle")]
    return els


POUCH_BUILDERS = {"molle_mag_pouch": molle_mag_pouch, "molle_grenade_pouch": molle_grenade_pouch,
                  "molle_dump_pouch": molle_dump_pouch, "ifak": ifak, "utility_pouch": utility_pouch}

# Een pouch in je inventaris: van voren en een beetje schuin, zoals de rugzakken.
POUCH_ROTATION = (22, 200, 0)


def pouch_display(elements):
    gui = md.gui(elements, POUCH_ROTATION, size=13.0)
    return {
        "gui": gui,
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1, 1], "scale": [0.7, 0.7, 0.7]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1, 1], "scale": [0.7, 0.7, 0.7]},
        "firstperson_righthand": {"rotation": [0, 160, 0], "translation": [1, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "firstperson_lefthand": {"rotation": [0, 160, 0], "translation": [1, 2, 0], "scale": [0.8, 0.8, 0.8]},
    }


def pouch_models():
    """
    Elke pouch kijkt naar het noorden (-z) en zit met zijn achterkant op z = 8, in het midden x = 8, y = 7,5 tot 8:
    zo zet de mod hem op een MOLLE-plek van de rugzak (de achterkant tegen de rugzak, zie BackpackLayer.MOLLE_SPOTS).
    """
    textures_plain = {"fabric": "imttech:item/backpack/fabric", "molle": "imttech:item/backpack/molle",
                      "webbing": "imttech:item/backpack/webbing", "buckle": "imttech:item/backpack/buckle",
                      "cross": "imttech:item/backpack/cross", "particle": "imttech:item/backpack/particle"}
    textures_camo = dict(textures_plain, fabric="imttech:item/backpack/fabric_camo", molle="imttech:item/backpack/molle_camo")
    for name in POUCHES:
        elements = POUCH_BUILDERS[name]()
        shown = pouch_display(elements)
        camo_elements = json.loads(json.dumps(elements))
        for element in camo_elements:
            for face in element["faces"].values():
                face.pop("tintindex", None)
        write(f"models/item/backpack/{name}_camo.json",
              {"textures": textures_camo, "elements": camo_elements, "display": shown})
        write(f"models/item/{name}.json", {
            "textures": textures_plain, "elements": elements, "display": shown,
            "overrides": [{"predicate": {"imttech:camo": 1}, "model": f"imttech:item/backpack/{name}_camo"}]})


def main():
    textures()
    models()
    chest_straps()
    pouch_models()


if __name__ == "__main__":
    main()
