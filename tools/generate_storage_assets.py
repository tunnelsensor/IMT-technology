"""
Hoofdstuk 113: alles wat Minecraft nodig heeft om de drie blokken van het opslagsysteem te tonen en te maken.

- de Storage Interface: een stalen kast met een scherm (een vol blok, draait naar je toe);
- de Chest Sensor: een plat kastje met een lensje dat je op een kist plakt (zes richtingen);
- de Charging Pad: een dunne laadplaat voor de RC Car (zijn strepen gloeien als hij laadt).

Per blok: de plaatjes, het blokmodel, de blockstate, het model van het item, wat hij laat vallen (loot table), het
recept, en het recept in het receptenboek (advancement).

Draaien (schrijft alleen de bestanden van deze drie blokken):
    python tools/generate_storage_assets.py
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

RESOURCES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "imttech"
DATA = RESOURCES / "data" / "imttech"

STEEL = (70, 80, 94, 255)
STEEL_DARK = (40, 46, 56, 255)
STEEL_LIGHT = (112, 124, 140, 255)
SCREEN = (14, 24, 34, 255)
CYAN = (63, 216, 255, 255)
CYAN_DIM = (30, 110, 140, 255)
WHITE = (207, 227, 255, 255)
GREEN = (90, 245, 130, 255)
AMBER = (245, 190, 60, 255)
AMBER_DIM = (120, 92, 30, 255)
PLASTIC = (38, 42, 50, 255)
PLASTIC_EDGE = (66, 72, 84, 255)
CLEAR = (0, 0, 0, 0)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(RESOURCES))


def save_png(im, rel):
    path = ASSETS / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("geschreven:", path.relative_to(RESOURCES))


def face(frm, to, textures, skip=(), glow=False):
    """Eén doosje van een blokmodel: van {@code frm} tot {@code to} (pixels), met een plaatje per kant."""
    faces = {}
    for side in ("north", "south", "east", "west", "up", "down"):
        if side in skip:
            continue
        texture = textures.get(side, textures.get("rest")) if isinstance(textures, dict) else textures
        if texture:
            faces[side] = {"texture": texture}
    element = {"from": list(frm), "to": list(to), "faces": faces}
    if glow:
        element["shade"] = False
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return element


def loot_table(name, keep_name=False):
    entry = {"type": "minecraft:item", "name": "imttech:" + name}
    if keep_name:
        entry["functions"] = [{"function": "minecraft:copy_components", "include": ["minecraft:custom_name"],
                               "source": "block_entity"}]
    write_json(DATA / "loot_table" / "blocks" / (name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [entry]}],
        "random_sequence": "imttech:blocks/" + name,
    })


def recipe(name, pattern, key, count=1, unlock="imttech:network_cable"):
    result = {"id": "imttech:" + name}
    if count > 1:
        result["count"] = count
    write_json(DATA / "recipe" / (name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "key": key, "pattern": pattern, "result": result,
    })
    # Het recept komt in je receptenboek zodra je het belangrijkste ingrediënt hebt.
    write_json(DATA / "advancement" / "recipes" / (name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": unlock}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipe": "imttech:" + name}, "trigger": "minecraft:recipe_unlocked"},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": ["imttech:" + name]},
    })


# ================================================================ De Storage Interface

def storage_interface():
    """
    Een stalen kast. Voorkant: een donker scherm met een raster van gekleurde vakjes (wat er in je opslag zit), een
    zoekbalkje erboven en een groen lampje. Zijkant: staal met een witte netwerkpoort. Bovenkant: staal met een rooster.
    """
    front = Image.new("RGBA", (16, 16), STEEL)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=STEEL_DARK)
    d.rectangle([2, 2, 13, 12], fill=SCREEN, outline=STEEL_LIGHT)
    d.line([3, 3, 9, 3], fill=WHITE)                      # het zoekbalkje
    colours = [CYAN, GREEN, AMBER, WHITE, CYAN_DIM, CYAN, AMBER, GREEN, CYAN]
    index = 0
    for y in (5, 7, 9):
        for x in (3, 6, 9):
            d.rectangle([x, y, x + 1, y + 1], fill=colours[index % len(colours)])
            index += 1
    d.point((12, 3), fill=GREEN)                          # het lampje
    d.line([4, 14, 11, 14], fill=STEEL_DARK)              # de gleuf waar de auto zijn spullen brengt
    save_png(front, "block/storage_interface_front.png")

    side = Image.new("RGBA", (16, 16), STEEL)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=STEEL_DARK)
    for y in (3, 12):
        d.line([2, y, 13, y], fill=STEEL_LIGHT)
    d.rectangle([6, 6, 9, 9], fill=(20, 22, 26, 255), outline=WHITE)
    save_png(side, "block/storage_interface_side.png")

    top = Image.new("RGBA", (16, 16), STEEL)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=STEEL_DARK)
    for y in (4, 6, 8, 10):
        d.line([4, y, 11, y], fill=STEEL_DARK)
    save_png(top, "block/storage_interface_top.png")

    write_json(ASSETS / "models" / "block" / "storage_interface.json", {
        "parent": "minecraft:block/orientable",
        "textures": {"front": "imttech:block/storage_interface_front", "side": "imttech:block/storage_interface_side",
                     "top": "imttech:block/storage_interface_top"},
    })
    variants = {}
    for facing, y in {"north": 0, "east": 90, "south": 180, "west": 270}.items():
        option = {"model": "imttech:block/storage_interface"}
        if y:
            option["y"] = y
        variants["facing=" + facing] = option
    write_json(ASSETS / "blockstates" / "storage_interface.json", {"variants": variants})
    write_json(ASSETS / "models" / "item" / "storage_interface.json", {"parent": "imttech:block/storage_interface"})
    loot_table("storage_interface", keep_name=True)
    recipe("storage_interface", ["IGI", "CHC", "INI"], {
        "I": {"tag": "c:ingots/iron"}, "G": {"tag": "c:glass_panes"}, "C": {"item": "imttech:computer_chip"},
        "H": {"tag": "c:chests/wooden"}, "N": {"item": "imttech:network_cable"},
    })


# ================================================================ De Chest Sensor

def chest_sensor():
    """
    Een plat zwart kastje van 6 x 6 pixels en 2 dik, met een cyaan lensje. Het model wijst naar het noorden (de kist
    staat aan de zuidkant); de blockstate draait het naar de zes richtingen. Het kastje steekt één pixel in het blok
    erachter: een kist is één pixel kleiner dan een heel blok, en zo zit de sensor er echt tegenaan.
    """
    body = Image.new("RGBA", (16, 16), PLASTIC)
    d = ImageDraw.Draw(body)
    d.rectangle([0, 0, 15, 15], outline=PLASTIC_EDGE)
    save_png(body, "block/chest_sensor.png")
    save_png(Image.new("RGBA", (16, 16), CYAN), "block/chest_sensor_lens.png")

    write_json(ASSETS / "models" / "block" / "chest_sensor.json", {
        "parent": "block/block",
        "textures": {"particle": "imttech:block/chest_sensor", "body": "imttech:block/chest_sensor",
                     "lens": "imttech:block/chest_sensor_lens"},
        "elements": [
            face([5, 5, 14], [11, 11, 17], "#body", skip=("south",)),
            face([7, 7, 13.6], [9, 9, 14], "#lens", skip=("south",), glow=True),
        ],
    })
    variants = {
        "facing=north": {"model": "imttech:block/chest_sensor"},
        "facing=east": {"model": "imttech:block/chest_sensor", "y": 90},
        "facing=south": {"model": "imttech:block/chest_sensor", "y": 180},
        "facing=west": {"model": "imttech:block/chest_sensor", "y": 270},
        "facing=up": {"model": "imttech:block/chest_sensor", "x": 270},
        "facing=down": {"model": "imttech:block/chest_sensor", "x": 90},
    }
    write_json(ASSETS / "blockstates" / "chest_sensor.json", {"variants": variants})

    # Het item: een plat plaatje (het kastje van voren), zoals de RC Controller.
    icon = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(icon)
    d.rectangle([3, 3, 12, 12], fill=PLASTIC, outline=PLASTIC_EDGE)
    d.rectangle([6, 6, 9, 9], fill=CYAN, outline=CYAN_DIM)
    d.point((11, 4), fill=GREEN)
    save_png(icon, "item/chest_sensor.png")
    write_json(ASSETS / "models" / "item" / "chest_sensor.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": "imttech:item/chest_sensor"},
    })
    loot_table("chest_sensor")
    recipe("chest_sensor", ["RGR", " N "], {
        "R": {"tag": "c:dusts/redstone"}, "G": {"tag": "c:glass_panes"}, "N": {"item": "imttech:network_cable"},
    }, count=4)


# ================================================================ De Charging Pad

def charging_pad():
    """
    Een stalen plaat van twee pixels dik met twee oranje strepen en een bliksemschicht. Als er een auto op laadt, gloeien
    de strepen en de bliksem ('charging_pad_on': een dun laagje bovenop dat zelf licht geeft).
    """
    def top(lit):
        stripe = AMBER if lit else AMBER_DIM
        im = Image.new("RGBA", (16, 16), STEEL_DARK)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=STEEL)
        d.line([2, 2, 13, 2], fill=stripe)
        d.line([2, 13, 13, 13], fill=stripe)
        # De bliksemschicht in het midden.
        for x, y in ((9, 4), (8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10), (5, 11)):
            d.point((x, y), fill=stripe)
        return im

    save_png(top(False), "block/charging_pad_top.png")
    # Het gloeiende laagje: alleen de strepen en de bliksem, de rest doorzichtig.
    glow = Image.new("RGBA", (16, 16), CLEAR)
    lit = top(True)
    for x in range(16):
        for y in range(16):
            if lit.getpixel((x, y)) == AMBER:
                glow.putpixel((x, y), AMBER)
    save_png(glow, "block/charging_pad_glow.png")
    side = Image.new("RGBA", (16, 16), STEEL_DARK)
    d = ImageDraw.Draw(side)
    d.line([0, 14, 15, 14], fill=STEEL)
    save_png(side, "block/charging_pad_side.png")

    textures = {"particle": "imttech:block/charging_pad_side", "top": "imttech:block/charging_pad_top",
                "side": "imttech:block/charging_pad_side", "glow": "imttech:block/charging_pad_glow"}
    plate = face([0, 0, 0], [16, 2, 16], {"up": "#top", "rest": "#side"})
    write_json(ASSETS / "models" / "block" / "charging_pad.json", {
        "parent": "block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": [plate],
    })
    write_json(ASSETS / "models" / "block" / "charging_pad_on.json", {
        "parent": "block/block", "render_type": "minecraft:cutout", "textures": textures,
        "elements": [plate, face([0, 2, 0], [16, 2.05, 16], {"up": "#glow"}, glow=True)],
    })
    write_json(ASSETS / "blockstates" / "charging_pad.json", {"variants": {
        "charging=false": {"model": "imttech:block/charging_pad"},
        "charging=true": {"model": "imttech:block/charging_pad_on"},
    }})
    write_json(ASSETS / "models" / "item" / "charging_pad.json", {"parent": "imttech:block/charging_pad"})
    loot_table("charging_pad")
    recipe("charging_pad", ["IPI", "WRW"], {
        "I": {"tag": "c:ingots/iron"}, "P": {"item": "minecraft:heavy_weighted_pressure_plate"},
        "W": {"item": "imttech:cable"}, "R": {"tag": "c:storage_blocks/redstone"},
    }, unlock="imttech:cable")


if __name__ == "__main__":
    storage_interface()
    chest_sensor()
    charging_pad()
