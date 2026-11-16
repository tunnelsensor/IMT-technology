"""
Hoofdstuk 130: alles wat Minecraft nodig heeft om de SD-kaarten te tonen en te maken.

De kaart: een klein plat kaartje met een afgeknipte hoek (zoals een echte SD-kaart), goudkleurige contacten bovenaan en
een wit etiket. Drie maten, elk met zijn eigen kleur: 8 GB blauw, 32 GB groen en 128 GB paars.

Zit er een kaart in een Security Core of een Computer, dan steekt er vooraan een kaartje uit een gleuf (de blokstand
"card", zie SecurityCoreBlock.CARD en ComputerBlock.CARD). Dat is een klein extra model bovenop het blok.

Wat dit script schrijft (en verder niets):
- de plaatjes: textures/item/sd_card_8gb.png, sd_card_32gb.png, sd_card_128gb.png en textures/block/sd_card_slot.png;
- de modellen van de drie items, en de kaartjes in de gleuf (models/block/security_core_card.json, computer_card.json);
- de blockstate van de Security Core (blockstates/security_core.json, nu "multipart": het blok en eventueel het kaartje).
  Die van de Computer schrijft tools/generate_block_models.py (computer_and_monitor), met hetzelfde kaartje;
- de recepten en de recepten in het receptenboek (advancements).

Draaien:
    python tools/generate_sd_card_assets.py
"""
import json
from pathlib import Path

from PIL import Image

RESOURCES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "imttech"
DATA = RESOURCES / "data" / "imttech"

CLEAR = (0, 0, 0, 0)
OUTLINE = (18, 22, 30, 255)
GOLD = (222, 177, 60, 255)
GOLD_DARK = (150, 110, 30, 255)
LABEL = (232, 238, 242, 255)
LABEL_SHADE = (180, 190, 198, 255)
INK = (40, 46, 56, 255)

# Per maat: de kleur van de kaart (licht, gewoon, donker) en hoeveel streepjes er op het etiket staan.
SIZES = {
    "8gb": ((96, 150, 255, 255), (42, 91, 215, 255), (24, 52, 140, 255), 1),
    "32gb": ((110, 220, 140, 255), (47, 174, 90, 255), (24, 108, 54, 255), 2),
    "128gb": ((200, 140, 255, 255), (142, 68, 217, 255), (86, 34, 140, 255), 3),
}

TURN = {"north": 0, "east": 90, "south": 180, "west": 270}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(RESOURCES.parent.parent.parent))


def save_png(folder, name, image):
    path = ASSETS / "textures" / folder / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print("geschreven:", path.relative_to(RESOURCES.parent.parent.parent))


# ---------------------------------------------------------------- plaatjes

def card_item(light, body, dark, stripes):
    """
    Een SD-kaart van 10 bij 13 pixels in het midden: een afgeknipte hoek rechtsboven, vier goudkleurige contacten
    bovenaan, en onderaan een wit etiket met 1, 2 of 3 streepjes (de maat).
    """
    im = Image.new("RGBA", (16, 16), CLEAR)
    px = im.load()
    x0, y0, x1, y1 = 3, 1, 12, 14
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            # De afgeknipte hoek rechtsboven (3 pixels schuin).
            if y - y0 + (x1 - x) < 2:
                continue
            edge = x in (x0, x1) or y in (y0, y1) or y - y0 + (x1 - x) == 2
            px[x, y] = OUTLINE if edge else body
    # Een lichte rand links en boven (licht van linksboven), een donkere rechts en onder.
    for y in range(y0 + 1, y1):
        px[x0 + 1, y] = light
        px[x1 - 1, y] = dark
    for x in range(x0 + 1, x1 - 2):
        px[x, y0 + 1] = light
    for x in range(x0 + 1, x1):
        px[x, y1 - 1] = dark
    # De contacten.
    for x in (x0 + 2, x0 + 4, x0 + 6):
        for y in (y0 + 2, y0 + 3, y0 + 4):
            px[x, y] = GOLD if y < y0 + 4 else GOLD_DARK
    # Het etiket, met 1, 2 of 3 streepjes van 1 breed en 3 hoog (de maat: 8, 32 of 128 GB).
    for y in range(y0 + 7, y1 - 1):
        for x in range(x0 + 2, x1 - 1):
            px[x, y] = LABEL if y < y1 - 2 else LABEL_SHADE
    width = stripes * 2 - 1
    start = x0 + 2 + (x1 - 1 - (x0 + 2) - width) // 2
    for i in range(stripes):
        for dy in (0, 1, 2):
            px[start + i * 2, y0 + 8 + dy] = INK
    return im


def slot_card():
    """
    Het kaartje dat uit de gleuf steekt (4 bij 3 pixels linksboven, plus de randjes die je van opzij ziet): blauw met een
    wit etiketje, een lichte bovenrand en een donkere onderrand.
    """
    im = Image.new("RGBA", (16, 16), CLEAR)
    px = im.load()
    light, body, dark, _ = SIZES["8gb"]
    for x in range(4):
        px[x, 0] = light
        px[x, 1] = body
        px[x, 2] = dark
    px[1, 1] = LABEL
    px[2, 1] = LABEL
    return im


# ---------------------------------------------------------------- modellen

def card_element(u0, v0, u1, v1, out=1.0):
    """
    Een kaartje dat {@code out} pixels uit de voorkant (noord) van een blok steekt, op de plek u0..u1, v0..v1 van het
    plaatje van die voorkant (u van links, v van boven). Zo zit het precies op de gleuf die je op dat plaatje ziet.
    """
    w = u1 - u0
    h = v1 - v0
    return {
        "from": [16 - u1, 16 - v1, -out],
        "to": [16 - u0, 16 - v0, 0],
        "faces": {
            "north": {"uv": [0, 0, w, h], "texture": "#card"},
            "up": {"uv": [0, 0, w, 1], "texture": "#card"},
            "down": {"uv": [0, h - 1, w, h], "texture": "#card"},
            "east": {"uv": [0, 0, 1, h], "texture": "#card"},
            "west": {"uv": [w - 1, 0, w, h], "texture": "#card"},
        },
    }


def card_model(name, element):
    write_json(ASSETS / "models" / "block" / f"{name}.json", {
        "parent": "block/block",
        "textures": {"particle": "imttech:block/sd_card_slot", "card": "imttech:block/sd_card_slot"},
        "elements": [element],
    })


def core_blockstate():
    """
    De Security Core als "multipart": per richting het blok (rood of groen lampje), en als er een kaart in zit het kaartje
    (rechtsonder op de voorkant, naast het lampje). Een blokstand zonder "card" (een oude wereld) heeft geen kaartje.
    Let op: bij "multipart" nemen de brokjes bij het breken altijd het plaatje van het EERSTE model (de rode kern). Daarom
    hebben security_core.json en security_core_claimed.json een eigen "particle": de zijkant, zonder lampje (gevonden door
    de nalezing: een groene kern liet anders rode brokjes vallen).
    """
    parts = []
    for facing, y in TURN.items():
        for claimed in (False, True):
            apply = {"model": "imttech:block/security_core" + ("_claimed" if claimed else "")}
            if y:
                apply["y"] = y
            parts.append({"when": {"facing": facing, "claimed": "true" if claimed else "false"}, "apply": apply})
    for facing, y in TURN.items():
        apply = {"model": "imttech:block/security_core_card"}
        if y:
            apply["y"] = y
        parts.append({"when": {"facing": facing, "card": "true"}, "apply": apply})
    write_json(ASSETS / "blockstates" / "security_core.json", {"multipart": parts})


# ---------------------------------------------------------------- items, recepten

def item_model(name):
    write_json(ASSETS / "models" / "item" / f"{name}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"imttech:item/{name}"}})


def recipe(name, ingredients):
    """Een recept zonder vorm (de volgorde in het raster maakt niet uit), en het recept in het receptenboek."""
    write_json(DATA / "recipe" / f"{name}.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ingredients,
        "result": {"id": f"imttech:{name}", "count": 1},
    })
    write_json(DATA / "advancement" / "recipes" / f"{name}.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {
                "conditions": {"items": [{"items": "imttech:computer_chip"}]},
                "trigger": "minecraft:inventory_changed",
            },
            "has_the_recipe": {
                "conditions": {"recipe": f"imttech:{name}"},
                "trigger": "minecraft:recipe_unlocked",
            },
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": [f"imttech:{name}"]},
    })


CHIP = {"item": "imttech:computer_chip"}
PLASTIC = {"item": "imttech:plastic"}


def main():
    for size, (light, body, dark, stripes) in SIZES.items():
        save_png("item", f"sd_card_{size}", card_item(light, body, dark, stripes))
        item_model(f"sd_card_{size}")
    save_png("block", "sd_card_slot", slot_card())
    # De kern: rechtsonder op de voorkant (naast het lampje, onder de rijen van de server).
    card_model("security_core_card", card_element(11, 12, 15, 15))
    # De computer: in de bovenste "drive bay" van de kast.
    card_model("computer_card", card_element(9, 2, 13, 5))
    core_blockstate()
    # Hoe groter de kaart, hoe duurder (jouw keuze: drie maten).
    recipe("sd_card_8gb", [CHIP, PLASTIC, {"tag": "c:wires/copper"}])
    recipe("sd_card_32gb", [CHIP, CHIP, PLASTIC, {"tag": "c:ingots/gold"}])
    recipe("sd_card_128gb", [CHIP, CHIP, CHIP, PLASTIC, {"tag": "c:gems/diamond"}])


if __name__ == "__main__":
    main()
