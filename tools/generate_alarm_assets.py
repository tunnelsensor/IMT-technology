"""
Hoofdstuk 126: alles wat Minecraft nodig heeft om de Alarm Speaker te tonen en te maken.

De speaker: een stalen kastje met een luidsprekerrooster aan de voorkant, op een montageplaat, met bovenop (of eronder,
aan het plafond) een rood zwaailicht en voorop een klein groen lampje. Drie vormen: aan de muur, aan het plafond en op de
vloer. Per vorm drie standen: uit (geen stroom of geen kern), online (het groene lampje brandt) en "gaat af" (het rode
licht draait: een bewegend plaatje, zie alarm_speaker_lamp_on.png.mcmeta, en het geeft zelf licht).

Wat dit script schrijft (en verder niets):
- de plaatjes (textures/block/alarm_speaker_*.png);
- de blokmodellen (9 standen + 1 voor in je inventaris) en de blockstate (48 combinaties);
- het model van het item, wat hij laat vallen (loot table: zijn naam en zijn geluid gaan mee), het recept en het recept
  in het receptenboek (advancement).

Draaien:
    python tools/generate_alarm_assets.py
"""
import json
from pathlib import Path

from PIL import Image

RESOURCES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "imttech"
DATA = RESOURCES / "data" / "imttech"
SIDES = ("north", "south", "east", "west", "up", "down")

METAL = (58, 66, 76, 255)
METAL_DARK = (28, 32, 38, 255)
METAL_LIGHT = (92, 104, 118, 255)
PLATE = (46, 52, 60, 255)
SCREW = (120, 132, 146, 255)
GRILLE = (34, 38, 46, 255)
HOLE = (12, 14, 18, 255)
RED_OFF = (86, 20, 20, 255)
RED_OFF_LIGHT = (122, 38, 36, 255)
RED_ON = (215, 32, 28, 255)
RED_HOT = (255, 120, 96, 255)
WHITE_HOT = (255, 225, 210, 255)
GREEN_ON = (90, 245, 130, 255)
GREEN_OFF = (36, 52, 40, 255)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def save_png(name, image):
    path = ASSETS / "textures" / "block" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


# ---------------------------------------------------------------- plaatjes

def body():
    """Het kastje: donker staal met een donkere rand en een lichte bovenkant."""
    im = Image.new("RGBA", (16, 16), METAL)
    px = im.load()
    for i in range(16):
        px[i, 0] = METAL_LIGHT
        px[i, 15] = METAL_DARK
        px[0, i] = METAL_DARK if i else METAL_LIGHT
        px[15, i] = METAL_DARK
    for x in range(3, 13, 4):
        px[x, 7] = METAL_DARK  # een paar naden
    return im


def grille():
    """Het luidsprekerrooster: rijen gaatjes, om en om verschoven, binnen een lichte rand."""
    im = Image.new("RGBA", (16, 16), GRILLE)
    px = im.load()
    for i in range(16):
        px[i, 0] = METAL_LIGHT
        px[i, 15] = METAL_DARK
        px[0, i] = METAL_LIGHT
        px[15, i] = METAL_DARK
    for y in range(2, 14, 2):
        shift = 0 if (y // 2) % 2 == 0 else 1
        for x in range(2 + shift, 14, 2):
            px[x, y] = HOLE
    return im


def plate():
    """De montageplaat: staal met vier schroefjes in de hoeken."""
    im = Image.new("RGBA", (16, 16), PLATE)
    px = im.load()
    for i in range(16):
        px[i, 15] = METAL_DARK
        px[15, i] = METAL_DARK
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = SCREW
    return im


def lamp_off():
    """Het rode licht, uit: donkerrood met een glimlichtje."""
    im = Image.new("RGBA", (16, 16), RED_OFF)
    px = im.load()
    for x in range(3, 6):
        for y in range(2, 5):
            px[x, y] = RED_OFF_LIGHT
    return im


def lamp_on():
    """
    Het rode licht dat draait: 8 beeldjes onder elkaar. Een felle strook schuift telkens twee pixels op, met een witte
    kern: zo lijkt het of er een spiegel in de kap ronddraait (zoals een echt zwaailicht).
    """
    frames = 8
    im = Image.new("RGBA", (16, 16 * frames), RED_ON)
    px = im.load()
    for frame in range(frames):
        centre = frame * 2
        for y in range(16):
            for x in range(16):
                distance = min(abs(x - centre), 16 - abs(x - centre))  # rond: wat rechts verdwijnt, komt links terug
                if distance == 0:
                    colour = WHITE_HOT
                elif distance <= 2:
                    colour = RED_HOT
                else:
                    colour = RED_ON
                px[x, frame * 16 + y] = colour
    return im


def led(colour):
    return Image.new("RGBA", (16, 16), colour)


def textures():
    save_png("alarm_speaker_body", body())
    save_png("alarm_speaker_grille", grille())
    save_png("alarm_speaker_plate", plate())
    save_png("alarm_speaker_lamp", lamp_off())
    save_png("alarm_speaker_lamp_on", lamp_on())
    write_json(ASSETS / "textures" / "block" / "alarm_speaker_lamp_on.png.mcmeta",
               {"animation": {"frametime": 2, "interpolate": False}})
    save_png("alarm_speaker_led_on", led(GREEN_ON))
    save_png("alarm_speaker_led_off", led(GREEN_OFF))


# ---------------------------------------------------------------- modellen

def box(frm, to, textures, glow=False):
    """Eén doosje (in pixels); textures is één plaatje voor alle kanten, of een dict per kant met "rest"."""
    if isinstance(textures, str):
        textures = {"rest": textures}
    faces = {}
    for side in SIDES:
        texture = textures.get(side, textures.get("rest"))
        if texture is None:
            continue
        face = {"texture": texture}
        edge = {"north": frm[2] == 0, "south": to[2] == 16, "west": frm[0] == 0,
                "east": to[0] == 16, "down": frm[1] == 0, "up": to[1] == 16}
        if edge[side]:
            face["cullface"] = side
        faces[side] = face
    element = {"from": list(frm), "to": list(to), "faces": faces}
    if glow:
        element["shade"] = False
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return element


def elements(kind, stand, dz=0.0):
    """
    De doosjes van één vorm ("wall", "ceiling", "floor"), getekend met de voorkant naar het noorden. stand is "off",
    "online" of "sounding". dz schuift alles naar voren of achteren (voor het model in je inventaris).
    """
    lamp = "#lamp_on" if stand == "sounding" else "#lamp"
    led_tex = "#led_on" if stand in ("online", "sounding") else "#led"
    glow_lamp = stand == "sounding"
    glow_led = stand in ("online", "sounding")

    def z(value):
        return value + dz

    if kind == "wall":
        parts = [
            box([2, 2, z(15)], [14, 14, z(16)], "#plate"),
            box([3, 3, z(11)], [13, 12, z(15)], {"north": "#grille", "rest": "#body"}),
            box([5.5, 12, z(11.5)], [10.5, 13, z(14.5)], "#body"),
            box([6, 13, z(12)], [10, 15, z(14)], lamp, glow=glow_lamp),
            box([11, 4, z(10.5)], [12, 5, z(11)], led_tex, glow=glow_led),
        ]
    elif kind == "ceiling":
        parts = [
            box([2, 15, 2], [14, 16, 14], "#plate"),
            box([3, 9, 4], [13, 15, 12], {"north": "#grille", "rest": "#body"}),
            box([5.5, 8, 5.5], [10.5, 9, 10.5], "#body"),
            box([6, 6, 6], [10, 8, 10], lamp, glow=glow_lamp),
            box([11, 10, 3.5], [12, 11, 4], led_tex, glow=glow_led),
        ]
    else:
        parts = [
            box([2, 0, 2], [14, 1, 14], "#plate"),
            box([3, 1, 4], [13, 8, 12], {"north": "#grille", "rest": "#body"}),
            box([5.5, 8, 5.5], [10.5, 9, 10.5], "#body"),
            box([6, 9, 6], [10, 11, 10], lamp, glow=glow_lamp),
            box([11, 2, 3.5], [12, 3, 4], led_tex, glow=glow_led),
        ]
    return parts


TEXTURES = {
    "particle": "imttech:block/alarm_speaker_body",
    "body": "imttech:block/alarm_speaker_body",
    "grille": "imttech:block/alarm_speaker_grille",
    "plate": "imttech:block/alarm_speaker_plate",
    "lamp": "imttech:block/alarm_speaker_lamp",
    "lamp_on": "imttech:block/alarm_speaker_lamp_on",
    "led": "imttech:block/alarm_speaker_led_off",
    "led_on": "imttech:block/alarm_speaker_led_on",
}


def model_name(kind, stand):
    return f"alarm_speaker_{kind}" + ("" if stand == "off" else f"_{stand}")


def models():
    for kind in ("wall", "ceiling", "floor"):
        for stand in ("off", "online", "sounding"):
            write_json(ASSETS / "models" / "block" / f"{model_name(kind, stand)}.json",
                       {"parent": "block/block", "textures": TEXTURES, "elements": elements(kind, stand)})
    # In je inventaris: de muurspeaker, naar het midden geschoven, met het groene lampje aan.
    write_json(ASSETS / "models" / "block" / "alarm_speaker_inventory.json",
               {"parent": "block/block", "textures": TEXTURES, "elements": elements("wall", "online", dz=-5.5)})
    write_json(ASSETS / "models" / "item" / "alarm_speaker.json", {"parent": "imttech:block/alarm_speaker_inventory"})

    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for kind in ("wall", "ceiling", "floor"):
        for facing, y in turn.items():
            # Op de vloer en aan het plafond is FACING de kant waar je keek toen je hem neerzette: de voorkant draait
            # dan naar jou toe (180 graden erbij).
            rotation = y if kind == "wall" else (y + 180) % 360
            for online in (False, True):
                for sounding in (False, True):
                    stand = "sounding" if sounding else ("online" if online else "off")
                    variant = {"model": f"imttech:block/{model_name(kind, stand)}"}
                    if rotation:
                        variant["y"] = rotation
                    key = f"face={kind},facing={facing},online={str(online).lower()},sounding={str(sounding).lower()}"
                    variants[key] = variant
    write_json(ASSETS / "blockstates" / "alarm_speaker.json", {"variants": variants})


# ---------------------------------------------------------------- loot table, recept

def data():
    write_json(DATA / "loot_table" / "blocks" / "alarm_speaker.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{
                "type": "minecraft:item",
                "name": "imttech:alarm_speaker",
                "functions": [{
                    "function": "minecraft:copy_components",
                    "include": ["minecraft:custom_name", "imttech:alarm_settings"],
                    "source": "block_entity",
                }],
            }],
        }],
        "random_sequence": "imttech:blocks/alarm_speaker",
    })
    write_json(DATA / "recipe" / "alarm_speaker.json", {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {
            "I": {"tag": "c:ingots/iron"},
            "L": {"item": "minecraft:redstone_lamp"},
            "N": {"item": "minecraft:note_block"},
            "R": {"tag": "c:dusts/redstone"},
            "K": {"item": "imttech:network_cable"},
        },
        "pattern": ["ILI", "RNR", "IKI"],
        "result": {"id": "imttech:alarm_speaker"},
    })
    write_json(DATA / "advancement" / "recipes" / "alarm_speaker.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": "imttech:network_cable"}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipe": "imttech:alarm_speaker"}, "trigger": "minecraft:recipe_unlocked"},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": ["imttech:alarm_speaker"]},
    })


if __name__ == "__main__":
    textures()
    models()
    data()
    print("Alarm Speaker: plaatjes, modellen, blockstate, loot table en recept geschreven.")
