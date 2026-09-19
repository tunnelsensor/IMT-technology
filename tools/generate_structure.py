"""
Bouwt de Stark Tower als Minecraft-structuurbestand (.nbt).

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_structure.py

Het resultaat komt in src/main/resources/data/starktech/structure/stark_tower.nbt.

De toren is 15 x 44 x 15 blokken: een lobby met het laadstation en kisten, een glazen
schacht met verdiepingen en een ladder, en bovenop een landingsplatform met een
"arc reactor"-kroon en antennes.

Tip: je kunt de toren ook in het spel verbouwen met een structuurblok (Structure Block),
opslaan als "starktech:stark_tower" en het .nbt-bestand uit je wereldmap (generated/)
hierheen kopiëren. Draai dit script dan niet meer, anders overschrijf je je eigen versie.
"""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "starktech" / "structure" / "stark_tower.nbt"

# Minecraft 1.21.1 "world_version" (zie version.json in de Minecraft-jar).
DATA_VERSION = 3955

# Afmetingen van de toren: breedte (x), hoogte (y), diepte (z)
SIZE_X, SIZE_Y, SIZE_Z = 15, 44, 15

# Hoogtes van de onderdelen
LOBBY_TOP = 7          # plafond van de lobby
SHAFT_TOP = 34         # bovenkant van de glazen schacht
PAD_Y = 35             # het landingsplatform
CROWN_Y = 38           # de ring met lampen


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
            data = key.encode("utf-8")
            out += struct.pack(">b", tag_type(item)) + struct.pack(">H", len(data)) + data
            write_payload(out, item)
        out += struct.pack(">b", TAG_END)


def write_nbt(path, root):
    out = bytearray()
    out += struct.pack(">b", TAG_COMPOUND) + struct.pack(">H", 0)
    write_payload(out, root)
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as file:
        file.write(bytes(out))


# --------------------------------------------------------------------- De toren bouwen

blocks = {}


def put(x, y, z, block, props=None, nbt=None):
    """Zet één blok. props zijn de blokstanden (bv. facing), nbt is extra data (bv. een kist)."""
    if 0 <= x < SIZE_X and 0 <= y < SIZE_Y and 0 <= z < SIZE_Z:
        blocks[(x, y, z)] = (block, props or {}, nbt)


def fill(x0, y0, z0, x1, y1, z1, block, props=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, block, props)


def frame(y, x0, z0, x1, z1, block, props=None):
    """Alleen de rand van een rechthoek op hoogte y."""
    for x in range(x0, x1 + 1):
        put(x, y, z0, block, props)
        put(x, y, z1, block, props)
    for z in range(z0, z1 + 1):
        put(x0, y, z, block, props)
        put(x1, y, z, block, props)


GLASS = "minecraft:light_blue_stained_glass"
FRAME_BLOCK = "minecraft:light_gray_concrete"
FLOOR = "minecraft:white_concrete"
STRIPE = "minecraft:light_blue_concrete"
TRIM = "minecraft:smooth_quartz"
LIGHT = "minecraft:sea_lantern"

# De ladder loopt in de noordwest-hoek: steun op z=2, ladders op z=3.
LADDER_X, LADDER_Z = 2, 3


def build_base():
    """Het platform en de lobby."""
    # Platform van gepolijst andesiet, met een afgeschuinde rand van quartz-halfblokken.
    fill(0, 0, 0, SIZE_X - 1, 0, SIZE_Z - 1, "minecraft:polished_andesite")
    fill(1, 0, 1, SIZE_X - 2, 0, SIZE_Z - 2, FLOOR)
    for x in range(1, SIZE_X - 1):
        put(x, 0, SIZE_Z // 2, STRIPE)
    for z in range(1, SIZE_Z - 1):
        put(SIZE_X // 2, 0, z, STRIPE)

    # Lobbymuren: hoeken van beton, daartussen grote ramen.
    for y in range(1, LOBBY_TOP):
        frame(y, 1, 1, SIZE_X - 2, SIZE_Z - 2, GLASS)
        for (x, z) in ((1, 1), (1, SIZE_Z - 2), (SIZE_X - 2, 1), (SIZE_X - 2, SIZE_Z - 2)):
            put(x, y, z, FRAME_BLOCK)
        # Kolommen in het midden van elke kant
        for x in (4, SIZE_X - 5):
            put(x, y, 1, FRAME_BLOCK)
            put(x, y, SIZE_Z - 2, FRAME_BLOCK)
        for z in (4, SIZE_Z - 5):
            put(1, y, z, FRAME_BLOCK)
            put(SIZE_X - 2, y, z, FRAME_BLOCK)

    # Koperen band net onder het plafond: het "STARK"-lint.
    frame(LOBBY_TOP - 1, 1, 1, SIZE_X - 2, SIZE_Z - 2, "minecraft:copper_block")

    # Ingang aan de zuidkant (z = SIZE_Z - 2), drie breed en drie hoog.
    for x in range(SIZE_X // 2 - 1, SIZE_X // 2 + 2):
        for y in range(1, 4):
            put(x, y, SIZE_Z - 2, "minecraft:air")
    put(SIZE_X // 2, 4, SIZE_Z - 2, "minecraft:light_blue_stained_glass")

    # Plafond van de lobby, met een gat voor de ladder.
    fill(1, LOBBY_TOP, 1, SIZE_X - 2, LOBBY_TOP, SIZE_Z - 2, FLOOR)
    put(LADDER_X, LOBBY_TOP, LADDER_Z, "minecraft:air")
    put(LADDER_X, LOBBY_TOP, LADDER_Z + 1, "minecraft:air")

    # Lampen aan het plafond
    for (x, z) in ((4, 4), (SIZE_X - 5, 4), (4, SIZE_Z - 5), (SIZE_X - 5, SIZE_Z - 5), (SIZE_X // 2, SIZE_Z // 2)):
        put(x, LOBBY_TOP - 1, z, LIGHT)

    # Inrichting van de lobby
    put(7, 1, 12, "starktech:charging_station", {"facing": "south"})     # laadstation
    put(3, 1, 12, "minecraft:chest", {"facing": "east", "type": "single", "waterlogged": "false"},
        {"id": "minecraft:chest", "LootTable": "starktech:chests/stark_tower"})
    put(11, 1, 12, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"},
        {"id": "minecraft:chest", "LootTable": "starktech:chests/stark_tower"})
    put(3, 1, 2, "minecraft:crafting_table")
    put(4, 1, 2, "minecraft:smithing_table")
    put(11, 1, 2, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    put(10, 1, 2, "minecraft:anvil", {"facing": "south"})
    # Werkbanken met een lichtstrip erboven
    for x in range(3, 12):
        put(x, 5, 2, "minecraft:end_rod", {"facing": "down"})


def build_ladder():
    """Eén ladder van de lobby tot het dak, met steunblokken erachter."""
    for y in range(1, PAD_Y):
        put(LADDER_X, y, LADDER_Z - 1, FRAME_BLOCK)                       # steun
        put(LADDER_X, y, LADDER_Z, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})


def build_shaft():
    """De glazen schacht met verdiepingen."""
    for y in range(LOBBY_TOP + 1, SHAFT_TOP + 1):
        frame(y, 2, 2, SIZE_X - 3, SIZE_Z - 3, GLASS)
        # Hoeken en kolommen van beton
        for (x, z) in ((2, 2), (2, SIZE_Z - 3), (SIZE_X - 3, 2), (SIZE_X - 3, SIZE_Z - 3)):
            put(x, y, z, FRAME_BLOCK)
        if y % 7 == 0:
            # Elke zeven blokken een quartz-band rond de toren
            frame(y, 2, 2, SIZE_X - 3, SIZE_Z - 3, TRIM)

    # Verdiepingsvloeren met een gat voor de ladder
    for floor_y in range(LOBBY_TOP + 7, SHAFT_TOP - 2, 7):
        fill(3, floor_y, 3, SIZE_X - 4, floor_y, SIZE_Z - 4, FLOOR)
        for x in range(3, SIZE_X - 3):
            put(x, floor_y, SIZE_Z // 2, STRIPE)
        put(LADDER_X, floor_y, LADDER_Z, "minecraft:air")
        put(LADDER_X + 1, floor_y, LADDER_Z, "minecraft:air")
        # Wat licht en een kist op de bovenste verdieping
        put(SIZE_X // 2, floor_y + 4, SIZE_Z // 2, LIGHT)
        put(4, floor_y + 1, SIZE_Z - 5, "minecraft:end_rod", {"facing": "up"})
        put(SIZE_X - 5, floor_y + 1, SIZE_Z - 5, "minecraft:end_rod", {"facing": "up"})
    top_floor = LOBBY_TOP + 7 * ((SHAFT_TOP - 2 - LOBBY_TOP) // 7)
    put(SIZE_X - 5, top_floor + 1, 4, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
        {"id": "minecraft:chest", "LootTable": "starktech:chests/stark_tower"})


def build_pad_and_crown():
    """Het landingsplatform, de lampenring en de antennes."""
    # Landingsplatform (iets breder dan de schacht)
    fill(1, PAD_Y, 1, SIZE_X - 2, PAD_Y, SIZE_Z - 2, FLOOR)
    frame(PAD_Y, 1, 1, SIZE_X - 2, SIZE_Z - 2, TRIM)
    # Landingskruis
    for i in range(4, SIZE_X - 4):
        put(i, PAD_Y, SIZE_Z // 2, STRIPE)
        put(SIZE_X // 2, PAD_Y, i, STRIPE)
    # Landingslichten op de hoeken
    for (x, z) in ((3, 3), (3, SIZE_Z - 4), (SIZE_X - 4, 3), (SIZE_X - 4, SIZE_Z - 4)):
        put(x, PAD_Y, z, LIGHT)
        put(x, PAD_Y + 1, z, "minecraft:end_rod", {"facing": "up"})
    # Gat + ladder-uitgang naar het dak
    put(LADDER_X, PAD_Y, LADDER_Z, "minecraft:air")
    put(LADDER_X, PAD_Y, LADDER_Z + 1, "minecraft:air")
    put(LADDER_X, PAD_Y + 1, LADDER_Z, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    put(LADDER_X, PAD_Y + 1, LADDER_Z - 1, FRAME_BLOCK)

    # De "arc reactor"-kroon: een ring van glas met lampen erin
    center = SIZE_X // 2
    for y in range(PAD_Y + 1, CROWN_Y + 1):
        frame(y, center - 3, center - 3, center + 3, center + 3, GLASS)
    frame(CROWN_Y, center - 3, center - 3, center + 3, center + 3, TRIM)
    fill(center - 2, CROWN_Y - 1, center - 2, center + 2, CROWN_Y - 1, center + 2, LIGHT)
    fill(center - 1, CROWN_Y, center - 1, center + 1, CROWN_Y, center + 1, "minecraft:tinted_glass")

    # Antennes: een hoge in het midden, twee kleinere ernaast
    for y in range(CROWN_Y + 1, SIZE_Y - 1):
        put(center, y, center, "minecraft:iron_bars", {"north": "false", "south": "false", "east": "false",
                                                       "west": "false", "waterlogged": "false"})
    put(center, SIZE_Y - 1, center, "minecraft:lightning_rod", {"facing": "up", "powered": "false",
                                                                "waterlogged": "false"})
    for (x, z) in ((center - 3, center - 3), (center + 3, center + 3)):
        put(x, CROWN_Y + 1, z, "minecraft:end_rod", {"facing": "up"})
        put(x, CROWN_Y + 2, z, "minecraft:end_rod", {"facing": "up"})


def build():
    build_base()
    build_shaft()
    build_ladder()
    build_pad_and_crown()


def to_nbt():
    palette = []
    palette_index = {}
    block_list = []
    for (x, y, z), (block, props, nbt) in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        key = (block, tuple(sorted(props.items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            entry = {"Name": block}
            if props:
                entry["Properties"] = dict(props)
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
