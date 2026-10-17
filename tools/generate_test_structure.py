"""
Maakt de lege "arena" waarin de automatische tests draaien.

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_test_structure.py

Het resultaat komt in src/main/resources/data/imttech/structure/empty.nbt.

Waarom? Een gametest draait altijd IN een structuur. Onze drone-tests hebben gewoon een
groot, leeg stuk wereld nodig met een vloer eronder, en dat maakt dit script.
"""
from pathlib import Path

from generate_structure import DATA_VERSION, Int, write_nbt

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "imttech" / "structure" / "empty.nbt"

# Afmetingen van de arena: breed en hoog genoeg om drones in te laten vliegen.
SIZE_X, SIZE_Y, SIZE_Z = 24, 16, 24


def build():
    """Een vloer van gepolijst andesiet, met lucht erboven."""
    blocks = []
    palette = [
        {"Name": "minecraft:polished_andesite"},
        {"Name": "minecraft:air"},
    ]
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            blocks.append({"pos": [Int(x), Int(0), Int(z)], "state": Int(0)})
            for y in range(1, SIZE_Y):
                blocks.append({"pos": [Int(x), Int(y), Int(z)], "state": Int(1)})
    return {
        "size": [Int(SIZE_X), Int(SIZE_Y), Int(SIZE_Z)],
        "entities": [],
        "blocks": blocks,
        "palette": [{"Name": entry["Name"]} for entry in palette],
        "DataVersion": Int(DATA_VERSION),
    }


if __name__ == "__main__":
    structure = build()
    write_nbt(OUT, structure)
    print(f"geschreven: {OUT} ({len(structure['blocks'])} blokken)")
