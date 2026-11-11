"""
Maakt de modellen en plaatjes van de armory (hoofdstuk 67): het Pegboard (een dun stalen paneel met gaatjes, om de
2 pixels een gaatje, zodat panelen naast elkaar naadloos één wand worden) en de Ammo Box (een olijfgroene stalen
munitiekist met een hengsel op het deksel, een sluiting opzij en een etiket op de voorkant; wat erin zit tekent de mod
zelf op dat etiket, zie client/armory/AmmoBoxRenderer).

Ook het haakje (models/armory/peg.json) waaraan elk wapen op het Pegboard hangt (zie client/armory/PegboardRenderer).
(Het slotje van hoofdstuk 70 is weg: in hoofdstuk 71 werd het een geverfde grijze omtrek, die de mod zelf tekent.)

Hoofdstuk 70: in een binnenhoek hangen twee panelen in een blok (de blockstate "corner": none, left of right). De
blockstate is daarom een "multipart": het eerste paneel per kant, en het tweede erbij als corner niet none is.

Draaien (vanuit de hoofdmap):  python tools/generate_armory_models.py
Het script is eigenaar van alles wat het schrijft; een volledige run is veilig.
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "imttech"


def write(rel, data):
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT))


def save(im, name):
    path = ASSETS / "textures" / "block" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(ROOT))


def noisy(colour, seed, amount=5, size=16):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            d = rnd.randint(-amount, amount)
            im.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in colour[:3]) + (255,))
    return im


def blockstate(name):
    """Vier kanten op, zoals de Reloading Bench: het model kijkt naar het noorden."""
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        variant = {"model": f"imttech:block/{name}"}
        if y:
            variant["y"] = y
        variants[f"facing={facing}"] = variant
    write(f"blockstates/{name}.json", {"variants": variants})


# ---------------------------------------------------------------- Het Pegboard

GRAPHITE = (104, 110, 120)
HOLE = (30, 32, 38)


def pegboard():
    # De voorkant: lichtgrijs staal (zwarte wapens als de Glock vallen er dan op) met 8 x 8 gaatjes. Geen rand, zodat panelen naast elkaar één wand worden.
    front = noisy(GRAPHITE, "pegboard", 4)
    for j in range(8):
        for i in range(8):
            x, y = 2 * i + 1, 2 * j + 1
            front.putpixel((x, y), HOLE + (255,))
            # Een randje licht linksboven van elk gaatje: het staal is daar gezet.
            lit = tuple(min(255, c + 16) for c in front.getpixel((x - 1, y - 1))[:3]) + (255,)
            front.putpixel((x - 1, y - 1), lit)
    save(front, "pegboard")
    side = noisy((78, 83, 92), "pegboard_side", 3)
    save(side, "pegboard_side")
    # Het haakje: licht staal.
    peg = noisy((176, 182, 192), "peg", 6)
    d = ImageDraw.Draw(peg)
    d.line([0, 0, 15, 0], fill=(214, 220, 228, 255))
    save(peg, "pegboard_peg")

    tex = {"front": "imttech:block/pegboard", "side": "imttech:block/pegboard_side", "particle": "imttech:block/pegboard"}
    faces = {
        "north": {"texture": "#front", "cullface": None},
        "south": {"texture": "#side", "cullface": "south"},
        "east": {"texture": "#side"},
        "west": {"texture": "#side"},
        "up": {"texture": "#side"},
        "down": {"texture": "#side"},
    }
    for face in faces.values():
        if face.get("cullface") is None:
            face.pop("cullface", None)
    write("models/block/pegboard.json", {
        "parent": "minecraft:block/block",
        "textures": tex,
        "elements": [{"from": [0, 0, 15], "to": [16, 16, 16], "faces": faces}],
        "display": {
            "gui": {"rotation": [20, 200, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        },
    })
    write("models/item/pegboard.json", {"parent": "imttech:block/pegboard"})
    pegboard_blockstate()

    # Het haakje: een pinnetje van 1 x 1 pixel, 16 lang langs z (de renderer schaalt het naar de juiste lengte).
    write("models/armory/peg.json", {
        "textures": {"0": "imttech:block/pegboard_peg", "particle": "imttech:block/pegboard_peg"},
        "elements": [{"from": [7.5, 7.5, 0], "to": [8.5, 8.5, 16],
                      "faces": {side: {"uv": [0, 0, 1, 16] if side in ("east", "west", "up", "down") else [0, 0, 1, 1],
                                       "texture": "#0"}
                                for side in ("north", "south", "east", "west", "up", "down")}}],
    })


def pegboard_blockstate():
    """
    Het eerste paneel kijkt naar "facing"; in een hoek (hoofdstuk 70) hangt het tweede tegen de muur links of rechts
    (zoals jij voor het eerste staat). Het tweede tegen de muur rechts kijkt naar links, dus een kwartslag met de klok
    mee gedraaid; tegen de muur links een kwartslag tegen de klok in. Hetzelfde model, alleen anders gedraaid.
    """
    order = ["north", "east", "south", "west"]
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    parts = []
    for i, facing in enumerate(order):
        main = {"model": "imttech:block/pegboard"}
        if turn[facing]:
            main["y"] = turn[facing]
        parts.append({"when": {"facing": facing}, "apply": main})
        for corner, side in (("right", order[(i + 1) % 4]), ("left", order[(i + 3) % 4])):
            second = {"model": "imttech:block/pegboard"}
            if turn[side]:
                second["y"] = turn[side]
            parts.append({"when": {"facing": facing, "corner": corner}, "apply": second})
    write("blockstates/pegboard.json", {"multipart": parts})


# ---------------------------------------------------------------- De Ammo Box

OLIVE = (82, 92, 58)
OLIVE_DARK = (58, 66, 40)
OLIVE_LIGHT = (104, 116, 74)
STEEL = (66, 70, 76)
LABEL = (198, 188, 146)
RED = (168, 36, 30)
GOLD = (214, 172, 60)


def ammo_box():
    # De zijkant (en de onderkant): olijfgroen staal met twee ribbels en een donker randje onderaan.
    side = noisy(OLIVE, "ammo_side", 4)
    d = ImageDraw.Draw(side)
    for y in (11, 13):
        d.line([0, y, 15, y], fill=OLIVE_LIGHT + (255,))
        d.line([0, y + 1, 15, y + 1], fill=OLIVE_DARK + (255,))
    d.line([0, 15, 15, 15], fill=OLIVE_DARK + (255,))
    # Een gestencilde streep in het I.M.T-rood en goud bovenaan de kist (net onder het deksel).
    for x in range(16):
        side.putpixel((x, 9), (RED if (x // 2) % 2 == 0 else GOLD) + (255,))
    save(side, "ammo_box_side")

    # De voorkant: hetzelfde, met een licht etiket (x 4 tot 12, y 2 tot 7 in de wereld; op het plaatje gespiegeld).
    front = side.copy()
    d = ImageDraw.Draw(front)
    d.rectangle([3, 9, 12, 15], fill=OLIVE + (255,))
    d.rectangle([4, 10, 11, 14], fill=LABEL + (255,), outline=(150, 140, 100, 255))
    for x in (4, 11):
        for y in (10, 14):
            front.putpixel((x, y), (120, 112, 80, 255))
    for x in range(16):
        front.putpixel((x, 9), (RED if (x // 2) % 2 == 0 else GOLD) + (255,))
    save(front, "ammo_box_front")

    # Het deksel: iets lichter, met een rand en een ingeslagen rechthoek.
    lid = noisy(OLIVE_LIGHT, "ammo_lid", 4)
    d = ImageDraw.Draw(lid)
    d.rectangle([2, 4, 13, 11], outline=OLIVE_DARK + (255,))
    d.rectangle([3, 5, 12, 10], outline=(118, 130, 86, 255))
    save(lid, "ammo_box_lid")
    lid_side = noisy(OLIVE_DARK, "ammo_lid_side", 3)
    d = ImageDraw.Draw(lid_side)
    d.line([0, 7, 15, 7], fill=(74, 84, 52, 255))
    save(lid_side, "ammo_box_lid_side")
    # Het hengsel en de sluiting: donker staal.
    steel = noisy(STEEL, "ammo_steel", 6)
    d = ImageDraw.Draw(steel)
    d.line([0, 0, 15, 0], fill=(110, 116, 124, 255))
    save(steel, "ammo_box_steel")

    tex = {"front": "imttech:block/ammo_box_front", "side": "imttech:block/ammo_box_side",
           "lid": "imttech:block/ammo_box_lid", "lid_side": "imttech:block/ammo_box_lid_side",
           "steel": "imttech:block/ammo_box_steel", "particle": "imttech:block/ammo_box_side"}

    def faces(default, **special):
        return {side: {"texture": special.get(side, default)} for side in ("north", "south", "east", "west", "up", "down")}

    elements = [
        # De kist zelf: 12 breed, 8 diep, 7,5 hoog; het etiket op de noordkant.
        {"from": [2, 0, 4], "to": [14, 7.5, 12], "faces": faces("#side", north="#front", down="#side")},
        # Het deksel steekt een kwart pixel uit.
        {"from": [1.75, 7.5, 3.75], "to": [14.25, 9, 12.25], "faces": faces("#lid_side", up="#lid", down="#lid_side")},
        # Het hengsel ligt plat op het deksel, met twee oogjes.
        {"from": [5, 9, 7.4], "to": [11, 9.6, 8.6], "faces": faces("#steel")},
        {"from": [4.4, 9, 7.6], "to": [5.2, 9.9, 8.4], "faces": faces("#steel")},
        {"from": [10.8, 9, 7.6], "to": [11.6, 9.9, 8.4], "faces": faces("#steel")},
        # De sluiting op de rechterkant (oost): een hendel die over het deksel klemt.
        {"from": [14, 3.5, 6.8], "to": [14.6, 9.4, 9.2], "faces": faces("#steel")},
        # Twee scharnieren achteraan.
        {"from": [4, 6.8, 12], "to": [6, 8.2, 12.5], "faces": faces("#steel")},
        {"from": [10, 6.8, 12], "to": [12, 8.2, 12.5], "faces": faces("#steel")},
    ]
    write("models/block/ammo_box.json", {"parent": "minecraft:block/block", "textures": tex, "elements": elements})
    write("models/item/ammo_box.json", {"parent": "imttech:block/ammo_box"})
    blockstate("ammo_box")


def main():
    pegboard()
    ammo_box()


if __name__ == "__main__":
    main()
