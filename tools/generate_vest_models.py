"""
Maakt het model van de Plate Carrier (hoofdstuk 109, de body armor) en de plaatjes van de vier plates.

Het vest: een plate-tas voor en een plate-tas achter, twee schouderbanden, een cummerbund om je middel (met MOLLE opzij),
drie open magazijnvakjes voor op je buik (met een elastiekje), een sleeplus bovenaan de rug, en op de rug een zwart
klittenbandvlak waar je eigen tekst op komt (die tekent de mod zelf, client/armor/VestRenderer). Net als de rugzakken
gebruikt het de stof, de MOLLE-banden, de band en de gespen van tools/generate_backpack_models.py: de stof is lichtgrijs
en wordt in het spel gekleurd (tintindex 0): antracietgrijs als hij nieuw is, of de kleur waarmee je hem verfde. Voor camo
schrijft dit script een tweede model met het camoplaatje en zonder kleuring (de override imttech:camo).

Zo staat het model: in de pixels van je lijf, zoals de schouderbanden van de rugzak (models/backpack/straps.json): je romp
is x 4 tot 12, y 4 (je middel) tot 16 (je schouders), z 6 (je rug) tot 10 (je borst). De borst (met de magazijnvakjes)
kijkt dus naar het zuiden (+z), de rug naar het noorden (-z). Zo tekent de mod het over je lijf (VestRenderer.renderOnBody).

Een plate: een echte plate heeft een "shooter's cut" (de bovenhoeken schuin weg, zodat je je geweer kunt aanleggen). Elke
soort zijn eigen kleur: polymeer gebroken wit, staal grijs, titanium blauwig, netherite donker. Van elke plate ook een
gebarsten plaatje: dat toont het item zodra de plate op is (de override imttech:cracked).

Draaien (vanuit de hoofdmap):  python tools/generate_vest_models.py
Het script is eigenaar van alles wat het schrijft; een volledige run is veilig.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import model_display as md  # noqa: E402
from generate_backpack_models import box, noisy, shade, write, ASSETS, ROOT  # noqa: E402

TIERS = {
    # naam: (basiskleur, metaal?)
    "polymer": ((218, 214, 199), False),
    "steel": ((143, 150, 158), True),
    "titanium": ((164, 188, 210), True),
    "netherite": ((76, 68, 72), True),
}


def save(im, name):
    path = ASSETS / "textures" / "item" / "vest" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ROOT))


# ---------------------------------------------------------------- De plaatjes

def loop_texture():
    """Het klittenbandvlak op de rug: fijn zwart lusjesweefsel (de tekst komt erop, wit staat er altijd goed op)."""
    im = noisy((28, 29, 31), "loop", 4)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y) % 4 == 0:
                shade(im, x, y, 7)
    # Een stiksel rondom.
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            if i % 2 == 0:
                shade(im, x, y, 22)
    save(im, "loop")


def mag_texture():
    """De bovenkant van een magazijn in een vakje: zwart polymeer met een lichtere rand bovenaan (de lippen)."""
    im = noisy((36, 37, 40), "mag", 3)
    for x in range(16):
        shade(im, x, 0, 30)
        shade(im, x, 1, 16)
    save(im, "mag")


def plate_shape():
    """De pixels van een plate (10 breed, 14 hoog) met een shooter's cut: de bovenhoeken schuin weg."""
    pixels = []
    for y in range(1, 15):
        for x in range(3, 13):
            cut = max(0, 4 - y)  # bovenaan 3 pixels schuin
            if x < 3 + cut or x > 12 - cut:
                continue
            pixels.append((x, y))
    return pixels


def plate_texture(name, colour, metal, cracked=False):
    rnd = random.Random(name + ("c" if cracked else ""))
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    shape = set(plate_shape())
    for (x, y) in shape:
        d = rnd.randint(-5, 5)
        c = [max(0, min(255, v + d)) for v in colour]
        im.putpixel((x, y), tuple(c) + (255,))
    # Licht van linksboven: de linkerrand en de bovenrand lichter, de rechterrand en de onderkant donkerder.
    for (x, y) in shape:
        left = (x - 1, y) not in shape
        right = (x + 1, y) not in shape
        top = (x, y - 1) not in shape
        bottom = (x, y + 1) not in shape
        if left or top:
            shade(im, x, y, 34)
        elif right or bottom:
            shade(im, x, y, -40)
    if metal:
        # Een glimstreep schuin over het metaal.
        for (x, y) in shape:
            if (x + y) in (11, 12):
                shade(im, x, y, 26)
    else:
        # Polymeer is mat, met een fijn weefsel.
        for (x, y) in shape:
            if (x + y) % 2 == 0:
                shade(im, x, y, -6)
    # Het etiket in het midden (zoals op een echte plate).
    for x in range(6, 10):
        for y in range(9, 11):
            if (x, y) in shape:
                im.putpixel((x, y), (232, 230, 224, 255) if name != "polymer" else (60, 62, 66, 255))
    if cracked:
        # Barsten: donkere lijnen die uitstralen vanuit een inslag, en het geheel wat valer.
        for (x, y) in shape:
            shade(im, x, y, -18)
        lines = Image.new("L", (16, 16), 0)
        d = ImageDraw.Draw(lines)
        impact = (8, 7)
        for end in ((5, 2), (11, 3), (13, 8), (11, 13), (7, 14), (3, 10), (3, 5)):
            d.line([impact, end], fill=255, width=1)
        for (x, y) in shape:
            if lines.getpixel((x, y)) > 0:
                im.putpixel((x, y), (22, 20, 20, 255))
        im.putpixel(impact, (10, 10, 10, 255))
    save(im, f"{name}_plate" + ("_cracked" if cracked else ""))


def textures():
    loop_texture()
    mag_texture()
    for name, (colour, metal) in TIERS.items():
        plate_texture(name, colour, metal)
        plate_texture(name, colour, metal, cracked=True)


# ---------------------------------------------------------------- Het vest

# De drie magazijnvakjes: links (x) van elk vakje en hoe breed, en de hoogte van hun bovenkant.
POUCH_X = 4.7
POUCH_STEP = 2.2
POUCH_TOP = 10.8


def plate_carrier():
    els = []
    # De plate-tas voor (met MOLLE op het borststuk boven de vakjes) en achter (met MOLLE).
    els.append(box([4.4, 8.0, 10.0], [11.6, 15.0, 11.2], "#fabric", special={"south": "#molle"}))
    els.append(box([4.4, 7.0, 4.8], [11.6, 15.2, 6.0], "#fabric", special={"north": "#molle"}))
    # Het klittenbandvlak voor de tekst, tussen je schouderbladen.
    els.append(box([5.2, 11.4, 4.6], [10.8, 13.8, 4.8], "#loop"))
    # De schouderbanden: van de achterste tas over je schouder naar de voorste.
    for x0 in (5.0, 9.4):
        els.append(box([x0, 15.0, 4.8], [x0 + 1.6, 16.4, 11.2], "#fabric"))
    # De cummerbund: opzij (met MOLLE), en een band voor je buik en een achter je onderrug. Opzij net buiten 3,5 en 12,5:
    # daar ligt de buitenkant van een broek (leggings), en twee vlakken op dezelfde plek flikkeren.
    els.append(box([3.4, 4.8, 5.0], [3.9, 9.4, 10.9], "#fabric", special={"west": "#molle"}))
    els.append(box([12.1, 4.8, 5.0], [12.6, 9.4, 10.9], "#fabric", special={"east": "#molle"}))
    els.append(box([4.0, 4.8, 10.0], [12.0, 8.0, 10.6], "#fabric"))
    els.append(box([4.0, 4.8, 5.4], [12.0, 7.0, 6.0], "#fabric"))
    # De drie magazijnvakjes op je buik, open van boven (een magazijn steekt er dan bovenuit), met een elastiekje.
    for i in range(3):
        x0 = POUCH_X + i * POUCH_STEP
        els.append(box([x0, 7.4, 10.6], [x0 + 2.1, POUCH_TOP, 12.6], "#fabric"))
        els.append(box([x0 + 0.3, POUCH_TOP - 0.7, 12.6], [x0 + 1.8, POUCH_TOP - 0.3, 12.75], "#webbing"))
    # De sleeplus bovenaan de rug en de gespen van de cummerbund.
    els.append(box([7.0, 14.6, 4.4], [9.0, 15.4, 4.8], "#webbing"))
    els.append(box([3.9, 6.0, 10.6], [4.5, 7.2, 10.95], "#buckle"))
    els.append(box([11.5, 6.0, 10.6], [12.1, 7.2, 10.95], "#buckle"))
    return els


def mag_top():
    """
    Een magazijn dat boven uit het eerste vakje steekt (de mod tekent er een per gevuld vakje, telkens POUCH_STEP verder
    naar rechts, zie VestRenderer.POUCH_STEP).
    """
    x0 = POUCH_X + 0.55
    return [box([x0, POUCH_TOP - 0.6, 11.2], [x0 + 1.0, POUCH_TOP + 1.0, 12.1], "#mag")]


# In je inventaris: van voren (de borst met de vakjes), een beetje van rechts en van boven.
GUI_ROTATION = (22, 20, 0)


def display(elements):
    gui = md.gui(elements, GUI_ROTATION, size=15.0)
    return {
        "gui": gui,
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 0, 0], "translation": [0, -4, 0], "scale": [1.0, 1.0, 1.0]},
        # Aan je hand, zoals je een vest aan een schouderband draagt.
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, -2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, -2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, -45, 0], "translation": [1.5, 1, 1], "scale": [0.35, 0.35, 0.35]},
        "firstperson_lefthand": {"rotation": [0, -45, 0], "translation": [1.5, 1, 1], "scale": [0.35, 0.35, 0.35]},
    }


def models():
    plain = {"fabric": "imttech:item/backpack/fabric", "molle": "imttech:item/backpack/molle",
             "webbing": "imttech:item/backpack/webbing", "buckle": "imttech:item/backpack/buckle",
             "loop": "imttech:item/vest/loop", "particle": "imttech:item/backpack/fabric"}
    camo = dict(plain, fabric="imttech:item/backpack/fabric_camo", molle="imttech:item/backpack/molle_camo",
                particle="imttech:item/backpack/fabric_camo")
    elements = plate_carrier()
    shown = display(elements)
    camo_elements = json.loads(json.dumps(elements))
    for element in camo_elements:
        for face in element["faces"].values():
            face.pop("tintindex", None)
    write("models/item/vest/plate_carrier_camo.json", {"textures": camo, "elements": camo_elements, "display": shown})
    write("models/item/plate_carrier.json", {
        "textures": plain, "elements": elements, "display": shown,
        "overrides": [{"predicate": {"imttech:camo": 1}, "model": "imttech:item/vest/plate_carrier_camo"}]})
    write("models/vest/mag_top.json", {"textures": {"mag": "imttech:item/vest/mag", "particle": "imttech:item/vest/mag"},
                                       "elements": mag_top()})
    for name in TIERS:
        write(f"models/item/vest/{name}_plate_cracked.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"imttech:item/vest/{name}_plate_cracked"}})
        write(f"models/item/{name}_plate.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"imttech:item/vest/{name}_plate"},
            "overrides": [{"predicate": {"imttech:cracked": 1}, "model": f"imttech:item/vest/{name}_plate_cracked"}]})


def main():
    textures()
    models()


if __name__ == "__main__":
    main()
