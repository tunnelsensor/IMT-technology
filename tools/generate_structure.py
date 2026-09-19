"""
Bouwt het "Stark Industries"-buitenpost-gebouw als Minecraft-structuurbestand (.nbt).

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_structure.py

Het resultaat komt in src/main/resources/data/starktech/structure/stark_outpost.nbt.

Tip: je kunt het gebouw ook in het spel verbouwen met een structuurblok (Structure Block),
opslaan als "starktech:stark_outpost" en het .nbt-bestand uit je wereldmap (generated/)
hierheen kopiëren. Draai dit script dan niet meer, anders overschrijf je je eigen versie.
"""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "starktech" / "structure" / "stark_outpost.nbt"

# Minecraft 26.3 "world_version" (zie version.json in de Minecraft-jar).
DATA_VERSION = 5023

# Afmetingen van het gebouw: breedte (x), hoogte (y), diepte (z)
SIZE_X, SIZE_Y, SIZE_Z = 11, 8, 9


# --------------------------------------------------------------------- Heel kleine NBT-schrijver

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


class Int:
    def __init__(self, value):
        self.value = value


def tag_type(value):
    if isinstance(value, Int):
        return TAG_INT
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, list):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    raise TypeError(value)


def write_payload(out, value):
    t = tag_type(value)
    if t == TAG_INT:
        out += struct.pack(">i", value.value)
    elif t == TAG_STRING:
        data = value.encode("utf-8")
        out += struct.pack(">H", len(data)) + data
    elif t == TAG_LIST:
        element_type = tag_type(value[0]) if value else TAG_END
        out += struct.pack(">bi", element_type, len(value))
        for item in value:
            write_payload(out, item)
    elif t == TAG_COMPOUND:
        for key, item in value.items():
            out += struct.pack(">b", tag_type(item))
            write_payload(out, key)
            write_payload(out, item)
        out += struct.pack(">b", TAG_END)


def write_nbt(path, root):
    out = bytearray()
    out += struct.pack(">b", TAG_COMPOUND)
    write_payload(out, "")
    write_payload(out, root)
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(out)


# --------------------------------------------------------------------- Het gebouw

blocks = {}  # (x, y, z) -> (blok-id, eigenschappen, block-entity-nbt)


def put(x, y, z, block, props=None, nbt=None):
    blocks[(x, y, z)] = (block, props or {}, nbt)


def is_wall(x, z):
    return x in (0, SIZE_X - 1) or z in (0, SIZE_Z - 1)


def is_corner(x, z):
    return x in (0, SIZE_X - 1) and z in (0, SIZE_Z - 1)


def build():
    mid_x = SIZE_X // 2
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            # Vloer: witte tegels met een blauwe streep in het midden
            if is_wall(x, z):
                put(x, 0, z, "minecraft:polished_andesite")
            elif z == SIZE_Z // 2 or x == mid_x:
                put(x, 0, z, "minecraft:light_blue_concrete")
            else:
                put(x, 0, z, "minecraft:white_concrete")

            # Muren (y = 1 t/m 4)
            for y in range(1, 5):
                if not is_wall(x, z):
                    put(x, y, z, "minecraft:air")  # binnen leegmaken
                elif is_corner(x, z):
                    put(x, y, z, "minecraft:light_gray_concrete")
                elif y == 4:
                    put(x, y, z, "minecraft:light_blue_concrete")
                elif y in (2, 3) and is_window(x, z):
                    put(x, y, z, "minecraft:light_blue_stained_glass")
                else:
                    put(x, y, z, "minecraft:white_concrete")

            # Dak met lampen
            if (x, z) in ((mid_x, SIZE_Z // 2), (2, SIZE_Z // 2), (SIZE_X - 3, SIZE_Z // 2)):
                put(x, 5, z, "minecraft:sea_lantern")
            else:
                put(x, 5, z, "minecraft:white_concrete")

            # Dakrand
            if is_wall(x, z):
                put(x, 6, z, "minecraft:smooth_quartz_slab", {"type": "bottom", "waterlogged": "false"})

    # Deuropening in de voorkant (z = 0)
    put(mid_x, 1, 0, "minecraft:air")
    put(mid_x, 2, 0, "minecraft:air")
    put(mid_x, 3, 0, "minecraft:cyan_concrete")

    # Binnen: laadstation achteraan in het midden, met de voorkant naar de deur
    put(mid_x, 1, SIZE_Z - 2, "starktech:charging_station", {"facing": "north"})
    # De kist met Stark-buit
    put(1, 1, SIZE_Z - 2, "minecraft:chest", {"facing": "east", "type": "single", "waterlogged": "false"},
        {"id": "minecraft:chest", "LootTable": "starktech:chests/stark_outpost"})
    # Werkplaats
    put(SIZE_X - 2, 1, SIZE_Z - 2, "minecraft:smithing_table")
    put(SIZE_X - 2, 1, SIZE_Z - 3, "minecraft:crafting_table")
    put(1, 1, 2, "minecraft:blast_furnace", {"facing": "east", "lit": "false"})

    # Antennes op het dak
    put(SIZE_X - 3, 6, SIZE_Z - 3, "minecraft:iron_block")
    put(SIZE_X - 3, 7, SIZE_Z - 3, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    put(2, 6, SIZE_Z - 3, "minecraft:end_rod", {"facing": "up"})


def is_window(x, z):
    if x in (0, SIZE_X - 1):
        return 2 <= z <= SIZE_Z - 3
    if z == SIZE_Z - 1:
        return 2 <= x <= SIZE_X - 3
    # voorkant: ramen links en rechts van de deur
    return x in (2, 3, SIZE_X - 4, SIZE_X - 3)


def to_nbt():
    palette = []
    palette_index = {}
    block_list = []
    for (x, y, z), (block, props, nbt) in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        key = (block, tuple(sorted(props.items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            entry = {"id": block}
            if props:
                entry["properties"] = dict(props)
            palette.append(entry)
        entry = {"pos": [Int(x), Int(y), Int(z)], "state": Int(palette_index[key])}
        if nbt:
            entry["nbt"] = nbt
        block_list.append(entry)
    return {
        "size": [Int(SIZE_X), Int(SIZE_Y), Int(SIZE_Z)],
        "entities": [],
        "blocks": block_list,
        "palette": palette,
        "DataVersion": Int(DATA_VERSION),
    }


if __name__ == "__main__":
    build()
    write_nbt(OUT, to_nbt())
    print(f"geschreven: {OUT} ({len(blocks)} blokken)")
