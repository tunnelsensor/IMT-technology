"""
Bouwt de Stark Tower als Minecraft-structuurbestand (.nbt).

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_structure.py

Het resultaat komt in src/main/resources/data/starktech/structure/stark_tower.nbt.

De toren is 41 x 110 x 41 blokken en loopt trapsgewijs naar boven toe smaller: elke keer dat
de gevel een stukje naar binnen springt, blijft de vloer eronder liggen als terras met een
reling. Van onder naar boven:

    0       het plein, met opritten en lichtmasten
    1-11    de lobby: receptie, werkbanken, het laadstation, zithoek en twee kisten
   12-22    het lab: brouwerij, ketels, een zonnepaneel op het terras
   23-33    het archief: boekenkasten, kaartentafel, betoveringstafel
   34-44    de hangar: binnenlandingsdek, gereedschapsrekken, warmtegeneratoren
   45-55    de woonverdieping: keuken, bedden, planten, uitzicht
   56-66    de beveiliging: schermenwand, donker, kisten
   67-76    de lasergang: acht rayshields en de omweg met drie deuren
   77-87    DE KLUIS: hier ligt de bril, achter bewakers en een val
   88-98    het penthouse: glas rondom, laadstation, zithoek
   99-105   het dak: landingsdek, arc-reactor-kroon
  106-109   de antennes

Buiten heeft hij koperen ribben over de volle hoogte, verlichte randen per verdieping en
een STARK-logo op de zuidgevel.

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
SIZE_X, SIZE_Y, SIZE_Z = 41, 110, 41

# De vloer van elke verdieping, en hoe ver de gevel daar naar binnen staat.
# (Grotere inset = smaller; daardoor ontstaan de terrassen.)
LOBBY_Y, LOBBY_IN = 1, 2
LAB_Y, LAB_IN = 12, 4
ARCHIVE_Y, ARCHIVE_IN = 23, 5
HANGAR_Y, HANGAR_IN = 34, 6
LIVING_Y, LIVING_IN = 45, 7
SECURITY_Y, SECURITY_IN = 56, 8
LASERS_Y, LASERS_IN = 67, 9
VAULT_Y, VAULT_IN = 77, 9
PENTHOUSE_Y, PENTHOUSE_IN = 88, 10
ROOF_Y, ROOF_IN = 99, 10
CROWN_Y = 104

# Het midden van de toren.
MID = SIZE_X // 2

# De liftschacht (ladder) loopt in de noordwesthoek.
LADDER_X, LADDER_Z = 4, 5


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
DARK_GLASS = "minecraft:gray_stained_glass"
FRAME_BLOCK = "minecraft:light_gray_concrete"
FLOOR = "minecraft:white_concrete"
DARK_FLOOR = "minecraft:polished_deepslate"
STRIPE = "minecraft:light_blue_concrete"
TRIM = "minecraft:smooth_quartz"
LIGHT = "minecraft:sea_lantern"
COPPER = "minecraft:copper_block"
RAIL = "minecraft:iron_bars"

LOOT = "starktech:chests/stark_tower"
VAULT_LOOT = "starktech:chests/stark_vault"

BARS = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}


def chest(x, y, z, facing, loot=LOOT):
    put(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
        {"id": "minecraft:chest", "LootTable": loot})


def spawner(x, y, z, mob):
    """Een bewaker die pas wakker wordt als je dichtbij komt."""
    put(x, y, z, "minecraft:spawner", None,
        {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": mob}}})


def bounds(inset):
    """De vier randen van de gevel bij deze inset."""
    return inset, inset, SIZE_X - 1 - inset, SIZE_Z - 1 - inset


def curtain_wall(y0, y1, inset, glass=GLASS):
    """
    De glazen gevel: grote ramen tussen stalen kolommen, met koperen ribben op de hoeken.
    """
    x0, z0, x1, z1 = bounds(inset)
    for y in range(y0, y1 + 1):
        frame(y, x0, z0, x1, z1, glass)
        # De hoeken zijn koperen ribben: die lopen over de hele hoogte door.
        for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
            put(x, y, z, COPPER)
        # Stalen kolommen ertussen, om de zes blokken.
        for x in range(x0 + 6, x1, 6):
            put(x, y, z0, FRAME_BLOCK)
            put(x, y, z1, FRAME_BLOCK)
        for z in range(z0 + 6, z1, 6):
            put(x0, y, z, FRAME_BLOCK)
            put(x1, y, z, FRAME_BLOCK)
        # Elke vijf blokken een quartz-band rond de toren.
        if (y - y0) % 5 == 4:
            frame(y, x0, z0, x1, z1, TRIM)


def terrace(y, outer_inset, inner_inset):
    """
    Het terras dat ontstaat waar de gevel naar binnen springt: de vloer van de verdieping
    eronder blijft liggen, met een reling en lampjes langs de rand.
    """
    ox0, oz0, ox1, oz1 = bounds(outer_inset)
    ix0, iz0, ix1, iz1 = bounds(inner_inset)
    for x in range(ox0, ox1 + 1):
        for z in range(oz0, oz1 + 1):
            if x < ix0 or x > ix1 or z < iz0 or z > iz1:
                put(x, y, z, TRIM)
    # Reling langs de buitenrand.
    for x in range(ox0, ox1 + 1):
        put(x, y + 1, oz0, RAIL, BARS)
        put(x, y + 1, oz1, RAIL, BARS)
    for z in range(oz0, oz1 + 1):
        put(ox0, y + 1, z, RAIL, BARS)
        put(ox1, y + 1, z, RAIL, BARS)
    # Lampjes op de hoeken van het terras.
    for (x, z) in ((ox0 + 1, oz0 + 1), (ox0 + 1, oz1 - 1), (ox1 - 1, oz0 + 1), (ox1 - 1, oz1 - 1)):
        put(x, y, z, LIGHT)


def floor_slab(y, inset, material=FLOOR, hole=True):
    """Een verdiepingsvloer, met een gat voor de ladder."""
    x0, z0, x1, z1 = bounds(inset)
    fill(x0, y, z0, x1, y, z1, material)
    for x in range(x0, x1 + 1):
        put(x, y, MID, STRIPE)
    if hole:
        put(LADDER_X, y, LADDER_Z, "minecraft:air")
        put(LADDER_X + 1, y, LADDER_Z, "minecraft:air")


def ceiling_lights(y, inset, step=7):
    """Lampen in het plafond, in een net raster."""
    x0, z0, x1, z1 = bounds(inset)
    for x in range(x0 + 3, x1, step):
        for z in range(z0 + 3, z1, step):
            put(x, y, z, LIGHT)


def ladder_run(y0, y1):
    for y in range(y0, y1 + 1):
        put(LADDER_X, y, LADDER_Z - 1, FRAME_BLOCK)
        put(LADDER_X, y, LADDER_Z, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})


def desk(x, y, z, length, facing="south"):
    """Een bureau van quartz-platen met een stoel ernaast."""
    for i in range(length):
        put(x + i, y, z, TRIM)
        put(x + i, y + 1, z, "minecraft:smooth_quartz_slab", {"type": "bottom", "waterlogged": "false"})
    put(x, y, z + 1, "minecraft:oak_stairs", {"facing": facing, "half": "bottom", "shape": "straight",
                                               "waterlogged": "false"})


def plant(x, y, z):
    put(x, y, z, "minecraft:flower_pot")


def screen_wall(x0, y, z, width, height):
    """Een wand van schermen (tinted glass met een lamp erachter)."""
    for i in range(width):
        for h in range(height):
            put(x0 + i, y + h, z, "minecraft:tinted_glass")
    put(x0 + width // 2, y + height // 2, z + 1, "minecraft:redstone_lamp", {"lit": "false"})


# --------------------------------------------------------------------- Het logo

# Vijf letters van 5 hoog en 4 breed: S T A R K.
LOGO = {
    "S": ["####", "#...", "####", "...#", "####"],
    "T": ["####", ".#..", ".#..", ".#..", ".#.."],
    "A": [".##.", "#..#", "####", "#..#", "#..#"],
    "R": ["###.", "#..#", "###.", "#.#.", "#..#"],
    "K": ["#..#", "#.#.", "##..", "#.#.", "#..#"],
}


def stark_logo(y, z, inset):
    """Het STARK-logo op de zuidgevel, in koper."""
    text = "STARK"
    width = len(text) * 5 - 1
    x0 = MID - width // 2
    for index, letter in enumerate(text):
        rows = LOGO[letter]
        for row in range(5):
            for col in range(4):
                if rows[row][col] == "#":
                    put(x0 + index * 5 + col, y + (4 - row), z, COPPER)


# --------------------------------------------------------------------- De verdiepingen

def build_plaza():
    """Het plein onderaan: een brede voet met opritten en lichtmasten."""
    fill(0, 0, 0, SIZE_X - 1, 0, SIZE_Z - 1, "minecraft:polished_andesite")
    fill(2, 0, 2, SIZE_X - 3, 0, SIZE_Z - 3, FLOOR)
    for x in range(2, SIZE_X - 2):
        put(x, 0, MID, STRIPE)
    for z in range(2, SIZE_Z - 2):
        put(MID, 0, z, STRIPE)
    # Lichtmasten rond het plein.
    for x in range(3, SIZE_X - 3, 8):
        for z in (1, SIZE_Z - 2):
            put(x, 0, z, LIGHT)
            put(x, 1, z, RAIL, BARS)
            put(x, 2, z, "minecraft:end_rod", {"facing": "up"})


def build_lobby():
    """De lobby: hoog en licht, met receptie, werkhoek en zithoek."""
    curtain_wall(LOBBY_Y, LAB_Y - 1, LOBBY_IN)
    x0, z0, x1, z1 = bounds(LOBBY_IN)

    # De ingang: vijf breed en vijf hoog, met een koperen luifel.
    for x in range(MID - 2, MID + 3):
        for y in range(LOBBY_Y, LOBBY_Y + 5):
            put(x, y, z1, "minecraft:air")
    for x in range(MID - 3, MID + 4):
        put(x, LOBBY_Y + 5, z1, COPPER)

    # Receptiebalie met een lessenaar.
    desk(MID - 4, LOBBY_Y, MID - 4, 8)
    put(MID, LOBBY_Y + 1, MID - 5, "minecraft:lectern",
        {"facing": "south", "has_book": "false", "powered": "false"})

    # Werkhoek links.
    put(x0 + 3, LOBBY_Y, z0 + 3, "minecraft:crafting_table")
    put(x0 + 4, LOBBY_Y, z0 + 3, "minecraft:smithing_table")
    put(x0 + 5, LOBBY_Y, z0 + 3, "minecraft:anvil", {"facing": "south"})
    put(x0 + 6, LOBBY_Y, z0 + 3, "minecraft:grindstone",
        {"face": "floor", "facing": "south"})
    # Laadstation en ovens rechts.
    put(MID, LOBBY_Y, z0 + 3, "starktech:charging_station", {"facing": "south"})
    put(x1 - 3, LOBBY_Y, z0 + 3, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    put(x1 - 4, LOBBY_Y, z0 + 3, "minecraft:furnace", {"facing": "south", "lit": "false"})

    # Zithoek bij het raam, met planten.
    for i in range(4):
        put(x0 + 4 + i, LOBBY_Y, z1 - 4, "minecraft:oak_stairs",
            {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    plant(x0 + 3, LOBBY_Y, z1 - 4)
    plant(x0 + 8, LOBBY_Y, z1 - 4)

    chest(x0 + 3, LOBBY_Y, z1 - 6, "north")
    chest(x1 - 3, LOBBY_Y, z1 - 6, "north")

    # Lichtstrip boven de werkbanken en lampen in het plafond.
    for x in range(x0 + 3, x1 - 2):
        put(x, LAB_Y - 2, z0 + 2, "minecraft:end_rod", {"facing": "down"})
    ceiling_lights(LAB_Y - 1, LOBBY_IN)
    floor_slab(LAB_Y - 1, LOBBY_IN)
    stark_logo(LOBBY_Y + 6, z1, LOBBY_IN)


def build_lab():
    """Het lab: brouwerij, ketels en een zonnepaneel op het terras."""
    terrace(LAB_Y - 1, LOBBY_IN, LAB_IN)
    curtain_wall(LAB_Y, ARCHIVE_Y - 1, LAB_IN)
    x0, z0, x1, z1 = bounds(LAB_IN)

    for x in (x0 + 2, x1 - 2):
        put(x, LAB_Y, z0 + 3, "minecraft:brewing_stand",
            {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
        put(x, LAB_Y, z0 + 4, "minecraft:cauldron")
    # Werkeiland met lichtstrips.
    fill(MID - 5, LAB_Y, MID - 1, MID + 5, LAB_Y, MID + 1, DARK_FLOOR)
    for x in range(MID - 5, MID + 6, 2):
        put(x, LAB_Y + 1, MID, "minecraft:end_rod", {"facing": "up"})
    desk(MID - 5, LAB_Y, MID + 3, 10)
    chest(x0 + 2, LAB_Y, z1 - 3, "north")
    chest(x1 - 2, LAB_Y, z1 - 3, "north")
    plant(x0 + 2, LAB_Y, z0 + 2)
    plant(x1 - 2, LAB_Y, z0 + 2)
    # Op het terras een zonnepaneel: die ziet hier de lucht.
    put(MID + 3, LAB_Y - 1 + 1, bounds(LOBBY_IN)[3] - 1, "starktech:solar_panel")
    ceiling_lights(ARCHIVE_Y - 1, LAB_IN)
    floor_slab(ARCHIVE_Y - 1, LAB_IN)


def build_archive():
    """Het archief: boekenkasten, kaartentafel en een betoveringstafel."""
    terrace(ARCHIVE_Y - 1, LAB_IN, ARCHIVE_IN)
    curtain_wall(ARCHIVE_Y, HANGAR_Y - 1, ARCHIVE_IN)
    x0, z0, x1, z1 = bounds(ARCHIVE_IN)

    for z in range(z0 + 2, z1 - 1, 3):
        fill(x0 + 1, ARCHIVE_Y, z, x0 + 1, ARCHIVE_Y + 3, z, "minecraft:bookshelf")
        fill(x1 - 1, ARCHIVE_Y, z, x1 - 1, ARCHIVE_Y + 3, z, "minecraft:bookshelf")
    put(MID, ARCHIVE_Y, MID, "minecraft:enchanting_table")
    for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        put(MID + dx, ARCHIVE_Y, MID + dz, "minecraft:bookshelf")
    # Kaartentafel.
    desk(MID - 4, ARCHIVE_Y, z1 - 3, 8)
    chest(x0 + 2, ARCHIVE_Y, z0 + 2, "south")
    chest(x1 - 2, ARCHIVE_Y, z0 + 2, "south")
    ceiling_lights(HANGAR_Y - 1, ARCHIVE_IN)
    floor_slab(HANGAR_Y - 1, ARCHIVE_IN)


def build_hangar():
    """De hangar: een open dek waar de drones landen, met warmtegeneratoren."""
    terrace(HANGAR_Y - 1, ARCHIVE_IN, HANGAR_IN)
    curtain_wall(HANGAR_Y, LIVING_Y - 1, HANGAR_IN, DARK_GLASS)
    x0, z0, x1, z1 = bounds(HANGAR_IN)

    # Landingscirkel.
    for i in range(-5, 6):
        put(MID + i, HANGAR_Y, MID, STRIPE)
        put(MID, HANGAR_Y, MID + i, STRIPE)
    for (dx, dz) in ((-5, -5), (-5, 5), (5, -5), (5, 5)):
        put(MID + dx, HANGAR_Y, MID + dz, LIGHT)
    # Gereedschapsrekken en generatoren langs de wand.
    for z in range(z0 + 2, z1 - 1, 4):
        put(x0 + 1, HANGAR_Y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        put(x1 - 1, HANGAR_Y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    put(x0 + 2, HANGAR_Y, z1 - 2, "starktech:heat_generator")
    put(x1 - 2, HANGAR_Y, z1 - 2, "starktech:heat_generator")
    chest(x0 + 2, HANGAR_Y, z0 + 2, "south")
    ceiling_lights(LIVING_Y - 1, HANGAR_IN)
    floor_slab(LIVING_Y - 1, HANGAR_IN)


def build_living():
    """De woonverdieping: keuken, bedden, planten en uitzicht."""
    terrace(LIVING_Y - 1, HANGAR_IN, LIVING_IN)
    curtain_wall(LIVING_Y, SECURITY_Y - 1, LIVING_IN)
    x0, z0, x1, z1 = bounds(LIVING_IN)

    # Keuken.
    desk(x0 + 1, LIVING_Y, z0 + 2, 6)
    put(x0 + 7, LIVING_Y, z0 + 2, "minecraft:smoker", {"facing": "south", "lit": "false"})
    put(x0 + 8, LIVING_Y, z0 + 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    # Bedden.
    for i, dx in enumerate((2, 5)):
        put(x1 - dx, LIVING_Y, z1 - 3, "minecraft:red_bed",
            {"facing": "north", "occupied": "false", "part": "foot"})
        put(x1 - dx, LIVING_Y, z1 - 4, "minecraft:red_bed",
            {"facing": "north", "occupied": "false", "part": "head"})
    # Zithoek bij het raam en groen.
    for i in range(5):
        put(MID - 2 + i, LIVING_Y, z1 - 2, "minecraft:oak_stairs",
            {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    for (x, z) in ((x0 + 1, z1 - 1), (x1 - 1, z1 - 1), (x0 + 1, z0 + 1), (x1 - 1, z0 + 1)):
        plant(x, LIVING_Y, z)
    chest(MID, LIVING_Y, z0 + 2, "south")
    ceiling_lights(SECURITY_Y - 1, LIVING_IN)
    floor_slab(SECURITY_Y - 1, LIVING_IN)


def build_security():
    """De beveiligingsverdieping: donker, dicht, vol schermen."""
    terrace(SECURITY_Y - 1, LIVING_IN, SECURITY_IN)
    curtain_wall(SECURITY_Y, LASERS_Y - 1, SECURITY_IN, DARK_GLASS)
    x0, z0, x1, z1 = bounds(SECURITY_IN)
    fill(x0 + 1, SECURITY_Y, z0 + 1, x1 - 1, SECURITY_Y, z1 - 1, DARK_FLOOR)

    screen_wall(MID - 4, SECURITY_Y + 1, z0 + 1, 9, 3)
    desk(MID - 4, SECURITY_Y, z0 + 3, 9)
    chest(x0 + 2, SECURITY_Y, z1 - 2, "north")
    chest(x1 - 2, SECURITY_Y, z1 - 2, "north")
    ceiling_lights(LASERS_Y - 1, SECURITY_IN, step=9)
    floor_slab(LASERS_Y - 1, SECURITY_IN, DARK_FLOOR)


def build_laser_gauntlet():
    """
    De lasergang: de enige weg naar de kluis, dwars doorsneden door acht rayshields.

    De velden staan permanent aan (een redstoneblok onder elke zender). Er komt niemand
    doorheen, dus je moet eromheen: drie kamers aan de zijkant, elk achter een ijzeren deur
    met de schakelaar ernaast.
    """
    terrace(LASERS_Y - 1, SECURITY_IN, LASERS_IN)
    curtain_wall(LASERS_Y, VAULT_Y - 1, LASERS_IN, DARK_GLASS)
    x0, z0, x1, z1 = bounds(LASERS_IN)
    fill(x0 + 1, LASERS_Y, z0 + 1, x1 - 1, LASERS_Y, z1 - 1, DARK_FLOOR)

    # De middengang.
    for z in range(z0 + 1, z1):
        fill(MID - 3, LASERS_Y + 1, z, MID - 3, LASERS_Y + 5, z, "minecraft:deepslate_tiles")
        fill(MID + 3, LASERS_Y + 1, z, MID + 3, LASERS_Y + 5, z, "minecraft:deepslate_tiles")

    colours = ["red", "blue", "green", "yellow", "purple", "cyan"]
    for i, z in enumerate(range(z0 + 3, z1 - 2, 2)):
        colour = colours[i % len(colours)]
        put(MID - 2, LASERS_Y + 1, z, "starktech:laser_emitter",
            {"facing": "east", "powered": "true", "switched": "false", "color": colour})
        put(MID + 2, LASERS_Y + 1, z, "starktech:laser_emitter",
            {"facing": "west", "powered": "true", "switched": "false", "color": colour})
        put(MID - 2, LASERS_Y, z, "minecraft:redstone_block")
        put(MID + 2, LASERS_Y, z, "minecraft:redstone_block")

    # De omweg: drie kamers achter een ijzeren deur, schakelaar pal ernaast.
    doors = [
        (x0 + 2, z0 + 5, "east"),
        (x0 + 2, z1 - 6, "east"),
        (x1 - 2, z1 - 6, "west"),
    ]
    for (x, z, facing) in doors:
        for y in range(LASERS_Y + 1, LASERS_Y + 5):
            for dz in range(-2, 3):
                put(x, y, z + dz, "minecraft:deepslate_bricks")
        put(x, LASERS_Y + 1, z, "minecraft:iron_door",
            {"facing": facing, "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
        put(x, LASERS_Y + 2, z, "minecraft:iron_door",
            {"facing": facing, "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
        lever_x = x + 1 if facing == "east" else x - 1
        put(lever_x, LASERS_Y + 2, z + 1, "minecraft:lever",
            {"face": "wall", "facing": facing, "powered": "false"})
        put(x, LASERS_Y + 3, z, LIGHT)

    ceiling_lights(VAULT_Y - 1, LASERS_IN, step=9)
    floor_slab(VAULT_Y - 1, LASERS_IN, DARK_FLOOR, hole=False)
    # De schacht omhoog naar de kluis.
    for x in range(MID - 1, MID + 2):
        put(x, VAULT_Y - 1, z0 + 2, "minecraft:air")
    for y in range(LASERS_Y + 1, VAULT_Y):
        put(MID - 1, y, z0 + 2, "minecraft:ladder", {"facing": "east", "waterlogged": "false"})
        put(MID - 2, y, z0 + 2, "minecraft:deepslate_tiles")


def build_vault():
    """
    DE KLUIS. Hier ligt de bril, en hier word je opgewacht.

    Dichte wanden van deepslate met versterkte hoeken, de kluiskist op een sokkel in het
    midden, vier bewakers in de hoeken en een drukplaat die de deur achter je sluit.
    """
    x0, z0, x1, z1 = bounds(VAULT_IN)
    for y in range(VAULT_Y, PENTHOUSE_Y - 1):
        frame(y, x0, z0, x1, z1, "minecraft:deepslate_bricks")
        for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
            put(x, y, z, "minecraft:reinforced_deepslate")
    fill(x0 + 1, VAULT_Y, z0 + 1, x1 - 1, VAULT_Y, z1 - 1, DARK_FLOOR)

    # De sokkel met de kluiskist.
    fill(MID - 2, VAULT_Y, MID - 2, MID + 2, VAULT_Y, MID + 2, "minecraft:chiseled_deepslate")
    fill(MID - 1, VAULT_Y + 1, MID - 1, MID + 1, VAULT_Y + 1, MID + 1, "minecraft:chiseled_deepslate")
    put(MID, VAULT_Y + 2, MID, "minecraft:chiseled_deepslate")
    chest(MID, VAULT_Y + 3, MID, "south", VAULT_LOOT)
    for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        put(MID + dx, VAULT_Y, MID + dz, LIGHT)
        put(MID + dx, VAULT_Y + 1, MID + dz, "minecraft:end_rod", {"facing": "up"})

    # De val: stap je op de plaat, dan sluit de deur achter je.
    put(MID, VAULT_Y + 1, MID + 3, "minecraft:heavy_weighted_pressure_plate", {"power": "0"})
    for x in (MID - 1, MID + 1):
        put(x, VAULT_Y + 1, z1 - 1, "starktech:laser_emitter",
            {"facing": "north", "powered": "false", "switched": "false", "color": "red"})

    # De bewakers.
    spawner(x0 + 2, VAULT_Y + 1, z0 + 2, "minecraft:iron_golem")
    spawner(x1 - 2, VAULT_Y + 1, z0 + 2, "minecraft:iron_golem")
    spawner(x0 + 2, VAULT_Y + 1, z1 - 2, "minecraft:wither_skeleton")
    spawner(x1 - 2, VAULT_Y + 1, z1 - 2, "minecraft:wither_skeleton")

    # De ingang vanuit de lasergang, en een ladder verder omhoog.
    for x in range(MID - 1, MID + 2):
        put(x, VAULT_Y, z0 + 2, "minecraft:air")
    floor_slab(PENTHOUSE_Y - 1, VAULT_IN, DARK_FLOOR)
    for y in range(VAULT_Y + 1, PENTHOUSE_Y):
        put(x1 - 1, y, MID, "minecraft:ladder", {"facing": "west", "waterlogged": "false"})


def build_penthouse():
    """Het penthouse: glas rondom, met uitzicht over de wereld."""
    terrace(PENTHOUSE_Y - 1, VAULT_IN, PENTHOUSE_IN)
    curtain_wall(PENTHOUSE_Y, ROOF_Y - 1, PENTHOUSE_IN)
    x0, z0, x1, z1 = bounds(PENTHOUSE_IN)
    fill(x0 + 1, PENTHOUSE_Y, z0 + 1, x1 - 1, PENTHOUSE_Y, z1 - 1, FLOOR)

    put(MID, PENTHOUSE_Y, MID, "starktech:charging_station", {"facing": "south"})
    desk(MID - 3, PENTHOUSE_Y, z0 + 2, 7)
    for i in range(5):
        put(MID - 2 + i, PENTHOUSE_Y, z1 - 2, "minecraft:oak_stairs",
            {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        plant(x, PENTHOUSE_Y, z)
    chest(x0 + 2, PENTHOUSE_Y, MID, "east")
    ceiling_lights(ROOF_Y - 1, PENTHOUSE_IN)
    floor_slab(ROOF_Y - 1, PENTHOUSE_IN)


def build_roof():
    """Het dak: landingsdek, de arc-reactor-kroon en de antennes."""
    x0, z0, x1, z1 = bounds(ROOF_IN)
    fill(x0, ROOF_Y, z0, x1, ROOF_Y, z1, FLOOR)
    frame(ROOF_Y, x0, z0, x1, z1, TRIM)
    for i in range(-6, 7):
        put(MID + i, ROOF_Y, MID, STRIPE)
        put(MID, ROOF_Y, MID + i, STRIPE)
    # Landingslichten en zoeklichten.
    for (x, z) in ((x0 + 2, z0 + 2), (x0 + 2, z1 - 2), (x1 - 2, z0 + 2), (x1 - 2, z1 - 2)):
        put(x, ROOF_Y, z, LIGHT)
        put(x, ROOF_Y + 1, z, "minecraft:end_rod", {"facing": "up"})
        put(x, ROOF_Y + 2, z, "minecraft:end_rod", {"facing": "up"})
    # Reling rond het dak.
    for x in range(x0, x1 + 1):
        put(x, ROOF_Y + 1, z0, RAIL, BARS)
        put(x, ROOF_Y + 1, z1, RAIL, BARS)
    for z in range(z0, z1 + 1):
        put(x0, ROOF_Y + 1, z, RAIL, BARS)
        put(x1, ROOF_Y + 1, z, RAIL, BARS)
    put(LADDER_X, ROOF_Y, LADDER_Z, "minecraft:air")
    put(LADDER_X + 1, ROOF_Y, LADDER_Z, "minecraft:air")

    # De arc-reactor-kroon.
    for y in range(ROOF_Y + 1, CROWN_Y + 1):
        frame(y, MID - 5, MID - 5, MID + 5, MID + 5, GLASS)
        for (x, z) in ((MID - 5, MID - 5), (MID - 5, MID + 5), (MID + 5, MID - 5), (MID + 5, MID + 5)):
            put(x, y, z, COPPER)
    frame(CROWN_Y, MID - 5, MID - 5, MID + 5, MID + 5, TRIM)
    fill(MID - 4, CROWN_Y - 1, MID - 4, MID + 4, CROWN_Y - 1, MID + 4, LIGHT)
    fill(MID - 3, CROWN_Y, MID - 3, MID + 3, CROWN_Y, MID + 3, "minecraft:tinted_glass")

    # Antennes.
    for y in range(CROWN_Y + 1, SIZE_Y - 1):
        put(MID, y, MID, RAIL, BARS)
    put(MID, SIZE_Y - 1, MID, "minecraft:lightning_rod",
        {"facing": "up", "powered": "false", "waterlogged": "false"})
    for (x, z) in ((MID - 5, MID - 5), (MID + 5, MID + 5)):
        for dy in (1, 2, 3, 4):
            put(x, CROWN_Y + dy, z, "minecraft:end_rod", {"facing": "up"})


def build():
    build_plaza()
    build_lobby()
    build_lab()
    build_archive()
    build_hangar()
    build_living()
    build_security()
    build_laser_gauntlet()
    build_vault()
    build_penthouse()
    build_roof()
    # De ladder loopt van de lobby tot aan de lasergang, en weer verder vanaf het penthouse.
    # Door de kluis heen gaat geen ladder: daar moet je langs de lasers.
    ladder_run(LOBBY_Y, LASERS_Y - 1)
    ladder_run(PENTHOUSE_Y, ROOF_Y - 1)


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
