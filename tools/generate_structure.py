"""
Bouwt de Stark Tower als Minecraft-structuurbestand (.nbt).

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_structure.py

Het resultaat komt in src/main/resources/data/starktech/structure/stark_tower.nbt.

De toren is 41 x 110 x 41 blokken en loopt trapsgewijs naar boven toe smaller: elke keer dat
de gevel een stukje naar binnen springt, blijft de vloer eronder liggen als terras met een
reling en lampjes. Van onder naar boven:

    0       het plein, met opritten, lichtmasten en plantenbakken
    1-11    de lobby: receptie met logowand, werkhoek, laadstation, zithoeken, vitrines
   12-22    het lab: watertanks, een pakstelling, een teslaspoel, werkbanken
   23-33    het archief: boekenkasten, leestafels, kaartentafel, betoveringstafel
   34-44    de hangar: landingscirkel, drone-docks voor je arsenaal, een hangardeur, generatoren
   45-55    de woonverdieping: keuken, eettafel, tv-hoek, slaapkamer
   56-66    de beveiliging: schermenwand, serverkasten, een holotafel, de verborgen krachtcentrale
   67-76    de lasergang: negen rayshields en de omweg met drie deuren
   77-87    DE KLUIS: hier ligt de bril, achter bewakers en een laserkooi-val
   88-98    het penthouse: bar, lounge, piano, slaapplek, holotafel
   99-105   het dak: landingsdek, een uitstekend landingsplatform voor het harnas (oostkant),
            zonnepanelen, schotel, de arc-reactor-kroon met een baken
  106-109   de antennes

Buiten heeft hij koperen ribben over de volle hoogte, verlichte randen per verdieping en
een verlicht STARK-logo op de zuidgevel.

De route naar boven: een glazen liftkoker (met ladder) van de lobby tot in de lasergang,
dan langs of om de lasers heen naar de ladder naar de kluis, en vanuit de kluis een ladder
langs de oostmuur door het penthouse tot op het dak.

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

# De liftkoker (een ladder in een glazen buis) van de lobby tot in de lasergang: hij komt
# boven aan de zuidkant van de lasergang uit. De ladder hangt tegen de achterwand (z + 1).
SHAFT_X, SHAFT_Z = MID, 30
# De ladder van de kluis door het penthouse naar het dak, langs de oostmuur.
EAST_LADDER_X, EAST_LADDER_Z = 29, MID


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


# --------------------------------------------------------------------- Bouwstenen

blocks = {}


def put(x, y, z, block, props=None, nbt=None):
    """
    Zet één blok. props zijn de blokstanden (bv. facing), nbt is extra data (bv. een kist).
    Standen die je niet noemt, krijgen hun gewone waarde (dat doet Minecraft bij het laden).
    """
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
# Gewaxt koper: dat blijft glanzend en wordt niet groen (het Stark-rood-goud blijft).
COPPER = "minecraft:waxed_copper_block"
RAIL = "minecraft:iron_bars"
DARK_WOOD = "minecraft:dark_oak_stairs"
QUARTZ_STAIRS = "minecraft:quartz_stairs"

LOOT = "starktech:chests/stark_tower"
VAULT_LOOT = "starktech:chests/stark_vault"

BARS = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}


def chest(x, y, z, facing, loot=LOOT):
    put(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
        {"id": "minecraft:chest", "LootTable": loot})


def security_turret(x, y, z):
    """Een beveiligingstorentje: schiet op indringers, tot iemand de toren hackt."""
    put(x, y, z, "starktech:security_turret", {"enabled": "true", "powered": "false"})


def spawner(x, y, z, mob):
    """Een bewaker die pas wakker wordt als je dichtbij komt."""
    put(x, y, z, "minecraft:spawner", None,
        {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": mob}}})


def bounds(inset):
    """De vier randen van de gevel bij deze inset."""
    return inset, inset, SIZE_X - 1 - inset, SIZE_Z - 1 - inset


def stairs(x, y, z, facing, block=QUARTZ_STAIRS):
    """
    Een trap. De hoge kant zit aan de kant van {@code facing}: als stoel is dat de rugleuning,
    dus wie erop zit, kijkt de andere kant op.
    """
    put(x, y, z, block, {"facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false"})


def top_slab(x, y, z, block="minecraft:smooth_quartz_slab"):
    """Een halve plaat in de bovenste helft: een tafelblad zonder poten."""
    put(x, y, z, block, {"type": "top", "waterlogged": "false"})


def bottom_slab(x, y, z, block="minecraft:smooth_quartz_slab"):
    put(x, y, z, block, {"type": "bottom", "waterlogged": "false"})


def bulb(x, y, z):
    """Een koperen lamp die aan staat (en aan blijft: hij schakelt alleen op een redstone-puls)."""
    put(x, y, z, "minecraft:waxed_copper_bulb", {"lit": "true", "powered": "false"})


def hanging_lamp(x, y, z, chain=1):
    """Een lantaarn aan een ketting; y is het eerste blok onder het plafond."""
    for i in range(chain):
        put(x, y - i, z, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    put(x, y - chain, z, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})


def table_lamp(x, y, z):
    put(x, y, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def plant(x, y, z, kind="potted_fern"):
    put(x, y, z, "minecraft:" + kind)


def planter(x, y, z, flowering=True):
    """Een plantenbak: mos met een azalea erop (y is de hoogte van het mos)."""
    put(x, y, z, "minecraft:moss_block")
    put(x, y + 1, z, "minecraft:flowering_azalea" if flowering else "minecraft:azalea")


def rug(x0, z0, x1, z1, y, colour):
    fill(x0, y, z0, x1, y, z1, "minecraft:%s_carpet" % colour)


def desk(x, y, z, length, facing="south", chairs=(0,)):
    """Een bureau van quartz met een blad erop; de stoelen (trappen) staan aan de zuidkant."""
    for i in range(length):
        put(x + i, y, z, TRIM)
        bottom_slab(x + i, y + 1, z)
    for i in chairs:
        stairs(x + i, y, z + 1, facing, "minecraft:oak_stairs")


def screen(x0, y0, z, width, height, backlight_z=None):
    """
    Een scherm: een zwarte rand met blauw glas erin. Hangt het tegen een muur, geef dan
    {@code backlight_z}: daar (in de muur) komen lampen achter het glas, zodat het oplicht.
    """
    for i in range(width):
        for h in range(height):
            edge = i in (0, width - 1) or h in (0, height - 1)
            if edge:
                put(x0 + i, y0 + h, z, "minecraft:black_concrete")
            else:
                glass = "minecraft:cyan_stained_glass" if (i + h) % 3 == 0 else "minecraft:light_blue_stained_glass"
                put(x0 + i, y0 + h, z, glass)
                if backlight_z is not None:
                    put(x0 + i, y0 + h, backlight_z, LIGHT)


def server_rack(x, y, z, height, facing):
    """Een serverkast: een kolom roosterblokken (crafters) met bovenop een brandend lampje."""
    for h in range(height):
        put(x, y + h, z, "minecraft:crafter", {"orientation": facing + "_up", "crafting": "false",
                                               "triggered": "false"})
    bulb(x, y + height, z)


def arc_reactor(cx, cy, z):
    """Het arc-reactor-embleem, rechtop in het vlak z: een blauwe ring om een lichtgevende kern."""
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            ring = max(abs(dx), abs(dy)) == 2
            corner = abs(dx) == 2 and abs(dy) == 2
            if corner:
                continue
            if ring:
                put(cx + dx, cy + dy, z, STRIPE)
            elif dx == 0 and dy == 0:
                put(cx + dx, cy + dy, z, LIGHT)
            else:
                put(cx + dx, cy + dy, z, "minecraft:white_concrete")


# --------------------------------------------------------------------- Gevel, terras en vloer

def curtain_wall(y0, y1, inset, glass=GLASS):
    """De glazen gevel: grote ramen tussen stalen kolommen, met koperen ribben op de hoeken."""
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
    eronder blijft liggen, met een reling en om de vier blokken een lampje in de rand (zo is
    elke verdieping 's nachts van ver te zien).
    """
    ox0, oz0, ox1, oz1 = bounds(outer_inset)
    ix0, iz0, ix1, iz1 = bounds(inner_inset)
    for x in range(ox0, ox1 + 1):
        for z in range(oz0, oz1 + 1):
            if x < ix0 or x > ix1 or z < iz0 or z > iz1:
                put(x, y, z, TRIM)
    for x in range(ox0, ox1 + 1):
        put(x, y + 1, oz0, RAIL, BARS)
        put(x, y + 1, oz1, RAIL, BARS)
        if (x - ox0) % 4 == 0:
            put(x, y, oz0, LIGHT)
            put(x, y, oz1, LIGHT)
    for z in range(oz0, oz1 + 1):
        put(ox0, y + 1, z, RAIL, BARS)
        put(ox1, y + 1, z, RAIL, BARS)
        if (z - oz0) % 4 == 0:
            put(ox0, y, z, LIGHT)
            put(ox1, y, z, LIGHT)


def floor_slab(y, inset, material=FLOOR, light_step=7):
    """
    Een verdiepingsvloer (en dus het plafond van de verdieping eronder), met een blauwe streep
    en lampen in een raster. De lampen zitten ín de vloer: van onderen zijn het plafondlampen,
    van boven lichtjes in de vloer. (Eerst werden ze hier weer overschreven door de vloer zelf,
    en was het binnen overal donker.)
    """
    x0, z0, x1, z1 = bounds(inset)
    fill(x0, y, z0, x1, y, z1, material)
    for x in range(x0, x1 + 1):
        put(x, y, MID, STRIPE)
    if light_step:
        for x in range(x0 + 3, x1, light_step):
            for z in range(z0 + 3, z1, light_step):
                put(x, y, z, LIGHT)


# --------------------------------------------------------------------- Het logo

# Vijf letters van 5 hoog en 4 breed: S T A R K.
LOGO = {
    "S": ["####", "#...", "####", "...#", "####"],
    "T": ["####", ".#..", ".#..", ".#..", ".#.."],
    "A": [".##.", "#..#", "####", "#..#", "#..#"],
    "R": ["###.", "#..#", "###.", "#.#.", "#..#"],
    "K": ["#..#", "#.#.", "##..", "#.#.", "#..#"],
}


def stark_logo(y, z):
    """
    Het STARK-logo in de gevel, van koperen lampen: overdag koper, 's nachts verlicht.
    y is de onderste rij. (Het zat eerst op de lobby, waar het terras erboven de bovenste rij
    van elke letter wegvaagde.)
    """
    text = "STARK"
    width = len(text) * 5 - 1
    x0 = MID - width // 2
    for index, letter in enumerate(text):
        rows = LOGO[letter]
        for row in range(5):
            for col in range(4):
                if rows[row][col] == "#":
                    bulb(x0 + index * 5 + col, y + (4 - row), z)


# --------------------------------------------------------------------- Het plein en de lobby

def build_plaza():
    """Het plein onderaan: een brede voet met lichtmasten en plantenbakken bij de ingang."""
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
    # Plantenbakken links en rechts van de ingang.
    for x in (13, 14, 15, 25, 26, 27):
        planter(x, 0, SIZE_Z - 1, flowering=x % 2 == 1)


def build_lobby():
    """De lobby: hoog en licht, met receptie, logowand, werkhoek, zithoeken en vitrines."""
    curtain_wall(LOBBY_Y, LAB_Y - 1, LOBBY_IN)
    x0, z0, x1, z1 = bounds(LOBBY_IN)
    y = LOBBY_Y

    # De ingang: vijf breed en vijf hoog, met een koperen luifel.
    for x in range(MID - 2, MID + 3):
        for h in range(y, y + 5):
            put(x, h, z1, "minecraft:air")
    for x in range(MID - 3, MID + 4):
        put(x, y + 5, z1, COPPER)
    # Plantenbakken aan weerskanten van de ingang, binnen.
    for x in (MID - 5, MID - 4, MID + 4, MID + 5):
        planter(x, y, z1 - 1)

    # De logowand achter de receptie, met het arc-reactor-embleem.
    fill(MID - 6, y, MID - 7, MID + 6, y + 6, MID - 7, FRAME_BLOCK)
    fill(MID - 6, y + 7, MID - 7, MID + 6, y + 7, MID - 7, TRIM)
    arc_reactor(MID, y + 3, MID - 7)
    for x in (MID - 6, MID + 6):
        put(x, y + 8, MID - 7, "minecraft:end_rod", {"facing": "up"})
    # De receptiebalie ervoor, met twee stoelen erachter.
    for x in range(MID - 4, MID + 5):
        put(x, y, MID - 3, TRIM)
        bottom_slab(x, y + 1, MID - 3)
    # De uiteinden buigen naar voren, met een lamp erop (een lantaarn wil op een vol blok staan).
    put(MID - 4, y, MID - 2, TRIM)
    put(MID + 4, y, MID - 2, TRIM)
    table_lamp(MID - 4, y + 1, MID - 2)
    table_lamp(MID + 4, y + 1, MID - 2)
    stairs(MID - 2, y, MID - 5, "north")
    stairs(MID + 2, y, MID - 5, "north")
    hanging_lamp(MID - 2, LAB_Y - 2, MID - 3, 2)
    hanging_lamp(MID + 2, LAB_Y - 2, MID - 3, 2)

    # Werkhoek links achterin (met het laadstation in het midden: daar zoekt de test het).
    put(x0 + 3, y, z0 + 3, "minecraft:crafting_table")
    put(x0 + 4, y, z0 + 3, "minecraft:smithing_table")
    put(x0 + 5, y, z0 + 3, "minecraft:anvil", {"facing": "south"})
    put(x0 + 6, y, z0 + 3, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    put(MID, y, z0 + 3, "starktech:charging_station", {"facing": "south"})
    put(x1 - 3, y, z0 + 3, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    put(x1 - 4, y, z0 + 3, "minecraft:furnace", {"facing": "south", "lit": "false"})
    for x in range(x0 + 3, x1 - 2):
        put(x, LAB_Y - 2, z0 + 2, "minecraft:end_rod", {"facing": "down"})

    # Vitrines langs de westmuur: prototypes op een sokkel, met een lichtje in de vloer ervoor.
    for z in (MID - 6, MID - 2, MID + 2):
        put(x0 + 3, y, z, TRIM)
        put(x0 + 3, y + 1, z, "minecraft:conduit", {"waterlogged": "false"})
        put(x0 + 4, y - 1, z, LIGHT)
    # Planten langs de oostmuur.
    for z in range(MID - 8, MID + 5, 4):
        planter(x1 - 2, y, z)

    # Zithoek links voor, bij het raam.
    for i in range(4):
        stairs(x0 + 4 + i, y, z1 - 4, "south")
    rug(x0 + 4, z1 - 7, x0 + 7, z1 - 5, y, "light_gray")
    top_slab(x0 + 5, y, z1 - 6)
    top_slab(x0 + 6, y, z1 - 6)
    plant(x0 + 3, y, z1 - 4, "potted_flowering_azalea_bush")
    plant(x0 + 8, y, z1 - 4, "potted_bamboo")
    chest(x0 + 3, y, z1 - 6, "north")

    # Zithoek rechts voor: twee banken tegenover elkaar met een salontafel.
    rug(x1 - 11, MID + 4, x1 - 4, MID + 8, y, "gray")
    for x in range(x1 - 10, x1 - 4):
        stairs(x, y, MID + 3, "north")
        stairs(x, y, MID + 9, "south")
    for x in range(x1 - 9, x1 - 6):
        top_slab(x, y, MID + 6)
    chest(x1 - 3, y, z1 - 6, "north")
    # Twee beveiligingstorentjes bij de lift: de enige weg naar boven.
    security_turret(MID - 5, y, MID + 6)
    security_turret(MID + 5, y, MID + 6)

    floor_slab(LAB_Y - 1, LOBBY_IN)


# --------------------------------------------------------------------- Lab en archief

def water_tank(x, y, z):
    """Een glazen tank met water, met een lamp in de bodem."""
    for h in range(4):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    put(x + dx, y + h, z + dz, "minecraft:glass")
    put(x, y, z, LIGHT)
    put(x, y + 1, z, "minecraft:water", {"level": "0"})
    put(x, y + 2, z, "minecraft:water", {"level": "0"})
    put(x, y + 3, z, "minecraft:glass")


def build_lab():
    """Het lab: brouwerij, watertanks, werkbanken, een pakstelling en een teslaspoel."""
    terrace(LAB_Y - 1, LOBBY_IN, LAB_IN)
    curtain_wall(LAB_Y, ARCHIVE_Y - 1, LAB_IN)
    x0, z0, x1, z1 = bounds(LAB_IN)
    y = LAB_Y

    # Het STARK-logo op de zuidgevel, midden op de verdieping.
    stark_logo(y + 3, z1)

    for x in (x0 + 2, x1 - 2):
        put(x, y, z0 + 3, "minecraft:brewing_stand",
            {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
        put(x, y, z0 + 4, "minecraft:cauldron")
    water_tank(x0 + 6, y, z0 + 3)
    water_tank(x0 + 10, y, z0 + 3)
    water_tank(x1 - 10, y, z0 + 3)

    # Het werkeiland in het midden, met lampen erboven.
    fill(MID - 5, y, MID - 1, MID + 5, y, MID + 1, DARK_FLOOR)
    for x in range(MID - 5, MID + 6, 2):
        put(x, y + 1, MID, "minecraft:end_rod", {"facing": "up"})
    for x in (MID - 4, MID, MID + 4):
        hanging_lamp(x, ARCHIVE_Y - 2, MID, 2)

    # Werkbanken langs de westmuur, met lampjes ertussen.
    tools = ["crafter", "loom", "cartography_table", "fletching_table", "stonecutter", "smithing_table"]
    for i, tool in enumerate(tools):
        z = z0 + 8 + i * 2
        props = {"facing": "east"} if tool == "stonecutter" else None
        if tool == "crafter":
            props = {"orientation": "east_up", "crafting": "false", "triggered": "false"}
        put(x0 + 2, y, z, "minecraft:" + tool, props)
        put(x0 + 2, y, z + 1, TRIM)
        table_lamp(x0 + 2, y + 1, z + 1)

    # Een computerplek: bureau met schermen.
    desk(MID - 5, y, MID + 3, 10, chairs=(1, 4, 7))
    screen(MID - 5, y + 2, MID + 2, 10, 3)

    # De pakstelling: een platform met twee palen en een balk, kettingen eraan.
    fill(x1 - 6, y, MID - 3, x1 - 4, y, MID + 3, "minecraft:iron_trapdoor", {"half": "bottom", "open": "false"})
    for z in (MID - 3, MID + 3):
        for h in range(y, y + 5):
            put(x1 - 5, h, z, RAIL, BARS)
    for z in range(MID - 3, MID + 4):
        put(x1 - 5, y + 5, z, "minecraft:iron_block")
    for z in (MID - 1, MID + 1):
        for h in (y + 4, y + 3):
            put(x1 - 5, h, z, "minecraft:chain", {"axis": "y", "waterlogged": "false"})

    # De harnas-werkbank voor de pakstelling: hier bouw je het harnas en zet je er upgrades in.
    # Stroom komt van een warmtegenerator ernaast, op een gloeiend magmablok in de vloer.
    put(x1 - 8, y, MID, "starktech:suit_workbench", {"facing": "west"})
    put(x1 - 8, y, MID + 1, "starktech:heat_generator", {"facing": "west"})
    put(x1 - 8, y - 1, MID + 1, "minecraft:magma_block")

    # Een teslaspoel in de hoek.
    tx, tz = x1 - 4, z1 - 5
    put(tx, y, tz, COPPER)
    put(tx, y + 1, tz, "minecraft:waxed_copper_grate")
    put(tx, y + 2, tz, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        put(tx + dx, y, tz + dz, "minecraft:end_rod", {"facing": "up"})

    chest(x0 + 2, y, z1 - 3, "north")
    chest(x1 - 2, y, z1 - 3, "north")
    plant(x0 + 2, y, z0 + 2)
    plant(x1 - 2, y, z0 + 2, "potted_bamboo")
    # Op het terras een zonnepaneel: die ziet hier de lucht.
    put(MID + 3, y, bounds(LOBBY_IN)[3] - 1, "starktech:solar_panel")
    floor_slab(ARCHIVE_Y - 1, LAB_IN)


def build_archive():
    """Het archief: boekenkasten, leestafels, een kaartentafel en een betoveringstafel."""
    terrace(ARCHIVE_Y - 1, LAB_IN, ARCHIVE_IN)
    curtain_wall(ARCHIVE_Y, HANGAR_Y - 1, ARCHIVE_IN)
    x0, z0, x1, z1 = bounds(ARCHIVE_IN)
    y = ARCHIVE_Y

    # Boekenkasten langs de wanden, met een lampje erop.
    for z in range(z0 + 2, z1 - 1, 3):
        for x in (x0 + 1, x1 - 1):
            fill(x, y, z, x, y + 3, z, "minecraft:bookshelf")
            put(x, y + 4, z, "minecraft:end_rod", {"facing": "up"})

    # Het midden: een rood kleed, de betoveringstafel met kasten eromheen.
    rug(MID - 4, MID - 4, MID + 4, MID + 4, y, "red")
    put(MID, y, MID, "minecraft:enchanting_table")
    for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        put(MID + dx, y, MID + dz, "minecraft:bookshelf")
    hanging_lamp(MID, HANGAR_Y - 2, MID, 3)

    # Twee rijen losse kasten in de noordoosthoek (gewone en gebeitelde).
    for z in (z0 + 4, z0 + 7):
        for x in range(MID + 4, x1 - 3):
            for h in range(3):
                if (x + h) % 2 == 0:
                    put(x, y + h, z, "minecraft:chiseled_bookshelf", {"facing": "south"})
                else:
                    put(x, y + h, z, "minecraft:bookshelf")

    # Leestafels met stoelen en een lamp erboven.
    for z in (z0 + 7, MID + 4):
        for x in range(x0 + 5, x0 + 8):
            top_slab(x, y, z)
            stairs(x, y, z - 1, "north", DARK_WOOD)
            stairs(x, y, z + 1, "south", DARK_WOOD)
        hanging_lamp(x0 + 6, HANGAR_Y - 2, z, 2)
    put(x1 - 7, y, MID + 5, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    put(x1 - 7, y, MID + 8, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})

    # De kaartentafel, met een wereldbol (lodestone) en de cartografietafel.
    desk(MID - 4, y, MID + 6, 8, chairs=(2, 5))
    put(MID - 5, y, MID + 6, "minecraft:cartography_table")
    put(MID + 4, y, MID + 6, "minecraft:lodestone")

    chest(x0 + 2, y, z0 + 2, "south")
    chest(x1 - 2, y, z0 + 2, "south")
    floor_slab(HANGAR_Y - 1, ARCHIVE_IN)


# --------------------------------------------------------------------- Hangar en woonverdieping

def build_hangar():
    """
    De hangar: een landingscirkel met waarschuwingsstrepen in de vloer, zes drone-docks voor
    je arsenaal, werkbanken, generatoren met lava-ketels en een grote open hangardeur naar het
    oosten.

    De drone-docks staan langs de noordmuur. Na het hacken van de toren staan daar je eigen
    drones op, en worden ze gerepareerd: een kabel langs de muur brengt stroom van een
    warmtegenerator in de hoek (met een ketel lava ernaast).
    """
    terrace(HANGAR_Y - 1, ARCHIVE_IN, HANGAR_IN)
    curtain_wall(HANGAR_Y, LIVING_Y - 1, HANGAR_IN, DARK_GLASS)
    x0, z0, x1, z1 = bounds(HANGAR_IN)
    y = HANGAR_Y
    ground = y - 1

    # De landingscirkel: een kruis in de vloer, en een ring van gele en zwarte strepen van tapijt
    # (in de vloerplaat zelf zag je hem ook als plafond van het archief).
    for i in range(-5, 6):
        put(MID + i, ground, MID, STRIPE)
        put(MID, ground, MID + i, STRIPE)
    for dx in range(-7, 8):
        for dz in range(-7, 8):
            if 6 * 6 <= dx * dx + dz * dz <= 7 * 7 + 3:
                colour = "yellow" if (dx + dz) % 4 < 2 else "black"
                put(MID + dx, y, MID + dz, "minecraft:%s_carpet" % colour)
    for (dx, dz) in ((-5, -5), (-5, 5), (5, -5), (5, 5)):
        put(MID + dx, ground, MID + dz, LIGHT)

    # Drone-docks langs de noordmuur, met een kabel ertussen naar de generator in de hoek. Boven
    # elke dock hangt een lamp met een laadpen aan het plafond.
    docks = list(range(x0 + 4, x1 - 2, 4))
    for x in range(x0 + 2, docks[-1] + 1):
        if x in docks:
            put(x, y, z0 + 1, "starktech:drone_dock", {"facing": "south"})
            bulb(x, LIVING_Y - 2, z0 + 1)
            put(x, LIVING_Y - 3, z0 + 1, "minecraft:lightning_rod", {"facing": "down", "powered": "false", "waterlogged": "false"})
        else:
            cable(x, y, z0 + 1)
    put(x0 + 1, y, z0 + 1, "starktech:heat_generator", {"facing": "south"})
    put(x0 + 1, y, z0 + 2, "minecraft:lava_cauldron")

    # Gereedschapsrekken langs de westmuur (niet voor de hangardeur).
    for z in range(z0 + 6, z1 - 1, 4):
        put(x0 + 1, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        if abs(z - MID) > 3:
            put(x1 - 1, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    # Werkbanken aan de zuidkant.
    put(x0 + 5, y, z1 - 1, "minecraft:anvil", {"facing": "east"})
    put(x0 + 6, y, z1 - 1, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    put(x0 + 7, y, z1 - 1, "minecraft:smithing_table")
    put(x0 + 8, y, z1 - 1, "minecraft:crafting_table")
    # Generatoren met een ketel lava ernaast (die is heet, dus ze werken ook).
    put(x0 + 2, y, z1 - 2, "starktech:heat_generator", {"facing": "north"})
    put(x0 + 3, y, z1 - 2, "minecraft:lava_cauldron")
    put(x1 - 2, y, z1 - 2, "starktech:heat_generator", {"facing": "north"})
    put(x1 - 3, y, z1 - 2, "minecraft:lava_cauldron")
    chest(x0 + 2, y, z0 + 2, "south")
    # Kettingen met haken aan het plafond.
    for x in (MID - 6, MID + 6):
        for z in (MID - 6, MID + 6):
            for h in range(3):
                put(x, LIVING_Y - 2 - h, z, "minecraft:chain", {"axis": "y", "waterlogged": "false"})

    # De hangardeur: een groot gat in de oostgevel, met lampen in de rand.
    for z in range(MID - 2, MID + 3):
        for h in range(y, y + 5):
            put(x1, h, z, "minecraft:air")
    for z in range(MID - 3, MID + 4):
        put(x1, y + 5, z, LIGHT)
    for h in range(y, y + 5):
        put(x1, h, MID - 3, LIGHT)
        put(x1, h, MID + 3, LIGHT)
    floor_slab(LIVING_Y - 1, HANGAR_IN)


def build_living():
    """De woonverdieping: keuken, eettafel, tv-hoek, slaapkamer en groen."""
    terrace(LIVING_Y - 1, HANGAR_IN, LIVING_IN)
    curtain_wall(LIVING_Y, SECURITY_Y - 1, LIVING_IN)
    x0, z0, x1, z1 = bounds(LIVING_IN)
    y = LIVING_Y

    # Keuken langs de noordmuur, met een kookeiland en krukjes.
    desk(x0 + 1, y, z0 + 2, 6, chairs=())
    put(x0 + 7, y, z0 + 2, "minecraft:smoker", {"facing": "south", "lit": "false"})
    put(x0 + 8, y, z0 + 2, "minecraft:water_cauldron", {"level": "3"})
    put(x0 + 9, y, z0 + 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    put(x0 + 10, y, z0 + 2, "minecraft:iron_block")
    put(x0 + 10, y + 1, z0 + 2, "minecraft:iron_block")
    for x in range(x0 + 3, x0 + 8):
        put(x, y, z0 + 5, TRIM)
        bottom_slab(x, y + 1, z0 + 5)
        stairs(x, y, z0 + 6, "south")
    hanging_lamp(x0 + 5, SECURITY_Y - 2, z0 + 5, 2)

    # De eettafel met zes stoelen.
    for x in range(MID - 2, MID + 3):
        top_slab(x, y, MID - 5)
        top_slab(x, y, MID - 4)
    for x in (MID - 2, MID, MID + 2):
        stairs(x, y, MID - 6, "north", DARK_WOOD)
        stairs(x, y, MID - 3, "south", DARK_WOOD)
    hanging_lamp(MID - 1, SECURITY_Y - 2, MID - 5, 2)
    hanging_lamp(MID + 1, SECURITY_Y - 2, MID - 4, 2)

    # De tv-hoek: een groot scherm tegen de westmuur, een bank ervoor en een kleed.
    for z in range(MID - 3, MID + 4):
        for h in range(y + 1, y + 5):
            edge = z in (MID - 3, MID + 3) or h in (y + 1, y + 4)
            put(x0 + 1, h, z, "minecraft:black_concrete" if edge else "minecraft:tinted_glass")
        top_slab(x0 + 1, y, z, "minecraft:dark_oak_slab")
    rug(x0 + 2, MID - 3, x0 + 5, MID + 3, y, "gray")
    for z in range(MID - 2, MID + 3):
        stairs(x0 + 6, y, z, "east", DARK_WOOD)
    top_slab(x0 + 4, y, MID - 1)
    top_slab(x0 + 4, y, MID)
    top_slab(x0 + 4, y, MID + 1)

    # De slaapkamer in de zuidoosthoek, achter een wand van boekenkasten.
    for x in range(x1 - 6, x1):
        for h in range(3):
            put(x, y + h, z1 - 7, "minecraft:bookshelf")
    for dx in (2, 5):
        put(x1 - dx, y, z1 - 3, "minecraft:red_bed", {"facing": "north", "occupied": "false", "part": "foot"})
        put(x1 - dx, y, z1 - 4, "minecraft:red_bed", {"facing": "north", "occupied": "false", "part": "head"})
    for x in (x1 - 1, x1 - 3, x1 - 4):
        put(x, y, z1 - 4, "minecraft:barrel", {"facing": "up", "open": "false"})
        table_lamp(x, y + 1, z1 - 4)
    rug(x1 - 6, z1 - 2, x1 - 1, z1 - 1, y, "white")

    # Een bank bij het raam en groen in de hoeken.
    for i in range(5):
        stairs(x0 + 3 + i, y, z1 - 2, "south")
    for (x, z) in ((x0 + 1, z1 - 1), (x0 + 1, z0 + 1), (x1 - 1, z0 + 1)):
        planter(x, y, z)
    chest(MID, y, z0 + 2, "south")
    floor_slab(SECURITY_Y - 1, LIVING_IN)


# --------------------------------------------------------------------- Beveiliging en krachtcentrale

def build_security():
    """
    De beveiligingsverdieping: donker, met een grote schermenwand, bureaus, serverkasten, een
    holotafel met de kaart van de omgeving, en camera's (observers) in de hoeken. De holotafel
    krijgt stroom van een warmtegenerator onder de vloer, op een magmablok.
    """
    terrace(SECURITY_Y - 1, LIVING_IN, SECURITY_IN)
    curtain_wall(SECURITY_Y, LASERS_Y - 1, SECURITY_IN, DARK_GLASS)
    x0, z0, x1, z1 = bounds(SECURITY_IN)
    fill(x0 + 1, SECURITY_Y, z0 + 1, x1 - 1, SECURITY_Y, z1 - 1, DARK_FLOOR)
    y = SECURITY_Y + 1   # de vloer ligt hier een blok hoger (donkere tegels)

    # De schermenwand tegen de noordgevel; de lampen erachter zitten in de gevel.
    screen(MID - 6, y, z0 + 1, 13, 5, backlight_z=z0)
    desk(MID - 4, y, z0 + 3, 9, chairs=(1, 4, 7))
    for x in (MID - 4, MID + 4):
        put(x, y + 1, z0 + 3, "minecraft:tinted_glass")

    # Serverkasten in twee rijen aan de westkant.
    for z in range(MID - 6, MID + 7):
        if z == MID:
            continue
        server_rack(x0 + 2, y, z, 3, "east")
        server_rack(x0 + 5, y, z, 3, "west")

    # De holotafel in het midden, op een generator onder de vloer.
    put(MID, SECURITY_Y - 1, MID, "minecraft:magma_block")
    put(MID, SECURITY_Y, MID, "starktech:heat_generator", {"facing": "north"})
    put(MID, y, MID, "starktech:holo_table")
    rug(MID - 2, MID - 2, MID + 2, MID + 2, y, "cyan")
    put(MID, y, MID, "starktech:holo_table")

    # Camera's in de hoeken, net onder het plafond.
    for (x, z, facing) in ((x0 + 1, z0 + 1, "south"), (x1 - 1, z1 - 1, "north"), (x0 + 1, z1 - 1, "east")):
        put(x, LASERS_Y - 2, z, "minecraft:observer", {"facing": facing, "powered": "false"})

    chest(x0 + 2, y, z1 - 2, "north")
    chest(x1 - 2, y, z1 - 2, "north")

    # De beveiligingskern: de "hoofdserver" in het gat midden in de rij serverkasten. Hij
    # beschermt de hele toren en houdt de beveiligingsdrones in de lucht; met een laptop kun je
    # hem hacken, en dan werkt de beveiliging voor jou.
    put(x0 + 2, y, MID, "starktech:security_core", {"facing": "east", "claimed": "false"})
    security_turret(x0 + 2, y, z0 + 2)
    security_turret(x1 - 2, y, z0 + 2)
    floor_slab(LASERS_Y - 1, SECURITY_IN, DARK_FLOOR, light_step=9)


# Waar de kabel van de krachtcentrale het plafond in gaat (buiten de rij plafondlampen).
POWER_Z = SECURITY_IN + 4
POWER_X = SIZE_X - 1 - SECURITY_IN - 3


def build_power_plant():
    """
    De verborgen krachtcentrale voor de lasergang: een dichtgemetseld blok in de hoek van de
    beveiligingsverdieping. Binnenin twee warmtegeneratoren, elk in een ring van lava (dat is
    hitte, dus stroom). Een kabel loopt omhoog het plafond in en dan naar de twee lijnen onder de
    zenders. Knip je die kabels door, dan gaan de lasers uit.
    """
    y0, y1 = SECURITY_Y + 1, LASERS_Y - 2
    fill(POWER_X - 2, y0, POWER_Z - 2, POWER_X + 2, y1, POWER_Z + 2, "minecraft:deepslate_bricks")
    for gy in (SECURITY_Y + 2, SECURITY_Y + 5):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                put(POWER_X + dx, gy, POWER_Z + dz, "minecraft:lava", {"level": "0"})
        put(POWER_X, gy, POWER_Z, "starktech:heat_generator")
    # De kabel omhoog, tussen en boven de generatoren, tot in het plafond.
    for y in range(SECURITY_Y + 3, LASERS_Y):
        if y != SECURITY_Y + 5:
            cable(POWER_X, y, POWER_Z)
    # In het plafond naar de twee lijnen onder de zenders.
    for x in range(MID - 2, POWER_X + 1):
        cable(x, LASERS_Y - 1, POWER_Z)
    # Een paar zwarte panelen op het blok, zodat het op een serverkast lijkt.
    for y in range(y0 + 1, y1, 2):
        put(POWER_X - 3, y, POWER_Z, "minecraft:black_concrete")


# --------------------------------------------------------------------- Lasergang en kluis

def build_laser_gauntlet():
    """
    De lasergang: de enige weg naar de kluis, dwars doorsneden door rayshields.

    De velden staan aan zolang er redstone op de zenders staat (een redstoneblok in de muur
    achter elke zender) én er stroom is. Die stroom komt uit een verborgen krachtcentrale op de
    verdieping eronder (zie build_power_plant), via kabels in het plafond. Er komt niemand
    doorheen, dus je moet eromheen: drie kamers aan de zijkant, elk achter een ijzeren deur
    met de schakelaar ernaast. Of je vindt de kabels in het plafond en knipt ze door...

    Je komt binnen aan de zuidkant (de liftkoker), de ladder naar de kluis is aan de noordkant.
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
    laser_rows = list(range(z0 + 3, z1 - 2, 2))
    for i, z in enumerate(laser_rows):
        colour = colours[i % len(colours)]
        # Nog uit: ze gaan zelf aan zodra ze redstone en stroom hebben.
        put(MID - 2, LASERS_Y + 1, z, "starktech:laser_emitter",
            {"facing": "east", "powered": "false", "color": colour})
        put(MID + 2, LASERS_Y + 1, z, "starktech:laser_emitter",
            {"facing": "west", "powered": "false", "color": colour})
        # Redstone in de muur achter de zender, stroom via een kabel eronder.
        put(MID - 3, LASERS_Y + 1, z, "minecraft:redstone_block")
        put(MID + 3, LASERS_Y + 1, z, "minecraft:redstone_block")
        cable(MID - 2, LASERS_Y, z)
        cable(MID + 2, LASERS_Y, z)
    # De kabels in het plafond van de beveiligingsverdieping: een lijn onder elke rij zenders.
    for z in range(POWER_Z, laser_rows[-1] + 1):
        cable(MID - 2, LASERS_Y - 1, z)
        cable(MID + 2, LASERS_Y - 1, z)

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

    floor_slab(VAULT_Y - 1, LASERS_IN, DARK_FLOOR, light_step=9)
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
    midden, vier bewakers in de hoeken, en een val: voor de sokkel ligt een drukplaat, met in
    de vloer eromheen vier laserzenders die omhoog kijken. Stap je op de plaat, dan krijgen de
    vloerblokken eronder redstone en schieten er vier laserzuilen tot aan het plafond omhoog:
    je staat in een kooi. Eruit? Hak een zender uit de vloer, of zorg dat er geen stroom is (de
    zenders hangen via een kabel door de muur van de lasergang aan de krachtcentrale).
    """
    x0, z0, x1, z1 = bounds(VAULT_IN)
    for y in range(VAULT_Y, PENTHOUSE_Y - 1):
        frame(y, x0, z0, x1, z1, "minecraft:deepslate_bricks")
        # Van buiten hetzelfde ritme als de rest van de toren: kolommen en een lichte band.
        for x in range(x0 + 6, x1, 6):
            put(x, y, z0, "minecraft:polished_blackstone")
            put(x, y, z1, "minecraft:polished_blackstone")
        for z in range(z0 + 6, z1, 6):
            put(x0, y, z, "minecraft:polished_blackstone")
            put(x1, y, z, "minecraft:polished_blackstone")
        if (y - VAULT_Y) % 5 == 4:
            frame(y, x0, z0, x1, z1, TRIM)
        for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
            put(x, y, z, "minecraft:reinforced_deepslate")
    fill(x0 + 1, VAULT_Y, z0 + 1, x1 - 1, VAULT_Y, z1 - 1, DARK_FLOOR)
    y = VAULT_Y + 1

    # Rode lijnen in de vloer die naar de sokkel wijzen.
    for i in range(x0 + 2, x1 - 1):
        put(i, VAULT_Y, MID, "minecraft:red_concrete")
        put(MID, VAULT_Y, i, "minecraft:red_concrete")

    # De sokkel met de kluiskist.
    fill(MID - 2, VAULT_Y, MID - 2, MID + 2, VAULT_Y, MID + 2, "minecraft:chiseled_deepslate")
    fill(MID - 1, y, MID - 1, MID + 1, y, MID + 1, "minecraft:chiseled_deepslate")
    put(MID, y + 1, MID, "minecraft:chiseled_deepslate")
    chest(MID, y + 2, MID, "south", VAULT_LOOT)
    for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        put(MID + dx, VAULT_Y, MID + dz, LIGHT)
        put(MID + dx, y, MID + dz, "minecraft:end_rod", {"facing": "up"})

    # Lichtzuilen, en vitrines met prototypes langs de wanden.
    for (x, z) in ((x0 + 5, z0 + 5), (x1 - 5, z0 + 5), (x0 + 5, z1 - 5), (x1 - 5, z1 - 5)):
        fill(x, y, z, x, y + 2, z, "minecraft:polished_blackstone")
        bulb(x, y + 3, z)
    for z in (MID - 5, MID + 5):
        for x in (x0 + 1, x1 - 1):
            put(x, y, z, TRIM)
            put(x, y + 1, z, "minecraft:heavy_core" if z < MID else "minecraft:conduit",
                {"waterlogged": "false"})
            put(x, y + 2, z, "minecraft:glass")

    # Een reling om het luik waar je binnenkomt.
    for z in range(z0 + 1, z0 + 4):
        put(MID - 2, y, z, RAIL, BARS)
        put(MID + 2, y, z, RAIL, BARS)

    # De val: de drukplaat voor de sokkel, en vier zenders in de vloer eromheen die omhoog
    # kijken. Onder de vloer liggen kabels, die via de muur van de lasergang aan het net hangen.
    plate_z = MID + 3
    put(MID, y, plate_z, "minecraft:heavy_weighted_pressure_plate", {"power": "0"})
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        put(MID + dx, VAULT_Y, plate_z + dz, "starktech:laser_emitter",
            {"facing": "up", "powered": "false", "color": "red"})
        cable(MID + dx, VAULT_Y - 1, plate_z + dz)
    cable(MID, VAULT_Y - 1, plate_z)
    # Van onder de val, door het plafond van de lasergang, langs de oostmuur van de gang omlaag
    # naar de kabel onder de zenders van de lasergang.
    riser_x = MID + 3
    cable(MID + 2, VAULT_Y - 1, plate_z)
    cable(riser_x, VAULT_Y - 1, plate_z)
    for yy in range(LASERS_Y, VAULT_Y - 1):
        cable(riser_x, yy, plate_z)
    cable(MID + 2, LASERS_Y, plate_z)

    # De bewakers: vier beveiligingstorentjes in de hoeken (tot iemand de toren hackt).
    for (x, z) in ((x0 + 2, z0 + 2), (x1 - 2, z0 + 2), (x0 + 2, z1 - 2), (x1 - 2, z1 - 2)):
        security_turret(x, y, z)

    # De ingang vanuit de lasergang.
    for x in range(MID - 1, MID + 2):
        put(x, VAULT_Y, z0 + 2, "minecraft:air")
    floor_slab(PENTHOUSE_Y - 1, VAULT_IN, DARK_FLOOR, light_step=9)


# --------------------------------------------------------------------- Penthouse en dak

def build_penthouse():
    """Het penthouse: glas rondom, een bar, een lounge, een piano, een slaapplek en een holotafel."""
    terrace(PENTHOUSE_Y - 1, VAULT_IN, PENTHOUSE_IN)
    curtain_wall(PENTHOUSE_Y, ROOF_Y - 1, PENTHOUSE_IN)
    x0, z0, x1, z1 = bounds(PENTHOUSE_IN)
    fill(x0 + 1, PENTHOUSE_Y, z0 + 1, x1 - 1, PENTHOUSE_Y, z1 - 1, FLOOR)
    y = PENTHOUSE_Y + 1   # de witte vloer ligt een blok hoger

    put(MID, y, MID, "starktech:charging_station", {"facing": "south"})
    desk(MID - 7, y, z0 + 2, 6, chairs=(1, 4))

    # De bar in de noordoosthoek, met krukken en een rek erachter.
    for x in range(MID + 2, x1 - 1):
        put(x, y, z0 + 3, TRIM)
        bottom_slab(x, y + 1, z0 + 3)
        stairs(x, y, z0 + 4, "south")
        put(x, y, z0 + 1, "minecraft:barrel", {"facing": "south", "open": "false"})
        table_lamp(x, y + 1, z0 + 1)
    hanging_lamp(MID + 4, ROOF_Y - 2, z0 + 3, 2)
    hanging_lamp(MID + 7, ROOF_Y - 2, z0 + 3, 2)

    # De lounge in de zuidwesthoek: een hoekbank, een salontafel en een kleed.
    rug(x0 + 2, MID + 2, x0 + 8, z1 - 2, y, "light_gray")
    for x in range(x0 + 2, x0 + 8):
        stairs(x, y, z1 - 1, "south")
    for z in range(MID + 3, z1 - 1):
        stairs(x0 + 1, y, z, "west")
    for x in range(x0 + 4, x0 + 6):
        for z in range(MID + 5, MID + 7):
            top_slab(x, y, z)

    # Een vleugel: nootblokken met een zwarte klep, en een krukje.
    for x in range(MID + 3, MID + 6):
        put(x, y, z1 - 3, "minecraft:note_block")
        bottom_slab(x, y + 1, z1 - 3, "minecraft:blackstone_slab")
    put(MID + 4, y, z1 - 2, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})

    # Een slaapplek aan de oostkant.
    for dz in (0, 1):
        put(x1 - 3, y, MID + 4 + dz, "minecraft:white_bed", {"facing": "west", "occupied": "false", "part": "head"})
        put(x1 - 2, y, MID + 4 + dz, "minecraft:white_bed", {"facing": "west", "occupied": "false", "part": "foot"})
    for z in (MID + 3, MID + 6):
        put(x1 - 3, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        table_lamp(x1 - 3, y + 1, z)

    # Een holotafel, op een generator in de vloer met een magmablok eronder (in het plafond van
    # de kluis: een gloeiende tegel daar).
    hx, hz = x0 + 5, z0 + 6
    put(hx, PENTHOUSE_Y - 1, hz, "minecraft:magma_block")
    put(hx, PENTHOUSE_Y, hz, "starktech:heat_generator", {"facing": "north"})
    put(hx, y, hz, "starktech:holo_table")

    for (x, z) in ((x0 + 1, z0 + 1), (x0 + 1, z1 - 1)):
        planter(x, y, z)
    plant(x1 - 1, y, z1 - 1, "potted_flowering_azalea_bush")
    chest(x0 + 2, y, MID, "east")
    floor_slab(ROOF_Y - 1, PENTHOUSE_IN)


def build_roof():
    """
    Het dak: landingsdek, een rij zonnepanelen, een schotel, de arc-reactor-kroon met een
    baken erin (een blauwe lichtstraal tot in de wolken) en de antennes.
    """
    x0, z0, x1, z1 = bounds(ROOF_IN)
    fill(x0, ROOF_Y, z0, x1, ROOF_Y, z1, FLOOR)
    frame(ROOF_Y, x0, z0, x1, z1, TRIM)
    for i in range(-6, 7):
        put(MID + i, ROOF_Y, MID, STRIPE)
        put(MID, ROOF_Y, MID + i, STRIPE)
    # Een ring om de kroon heen: het landingsdek.
    for dx in range(-8, 9):
        for dz in range(-8, 9):
            if 7 * 7 <= dx * dx + dz * dz <= 8 * 8:
                put(MID + dx, ROOF_Y, MID + dz, STRIPE)
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

    # Zonnepanelen aan de zuidkant, en een schotel in de noordwesthoek.
    for x in list(range(x0 + 2, x0 + 7)) + list(range(x1 - 6, x1 - 1)):
        put(x, ROOF_Y + 1, z1 - 2, "starktech:solar_panel")
    dx, dz = x0 + 3, z0 + 4
    put(dx, ROOF_Y + 1, dz, RAIL, BARS)
    put(dx, ROOF_Y + 2, dz, RAIL, BARS)
    for ax in (-1, 0, 1):
        for az in (-1, 0, 1):
            bottom_slab(dx + ax, ROOF_Y + 3, dz + az)
    put(dx, ROOF_Y + 3, dz, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})

    # De arc-reactor-kroon: een glazen doos met koperen hoeken en een lichtplafond...
    for y in range(ROOF_Y + 1, CROWN_Y + 1):
        frame(y, MID - 5, MID - 5, MID + 5, MID + 5, GLASS)
        for (x, z) in ((MID - 5, MID - 5), (MID - 5, MID + 5), (MID + 5, MID - 5), (MID + 5, MID + 5)):
            put(x, y, z, COPPER)
    frame(CROWN_Y, MID - 5, MID - 5, MID + 5, MID + 5, TRIM)
    fill(MID - 4, CROWN_Y - 1, MID - 4, MID + 4, CROWN_Y - 1, MID + 4, LIGHT)
    fill(MID - 3, CROWN_Y, MID - 3, MID + 3, CROWN_Y, MID + 3, "minecraft:tinted_glass")
    for i in range(-3, 4):
        for (x, z) in ((MID + i, MID - 3), (MID + i, MID + 3), (MID - 3, MID + i), (MID + 3, MID + i)):
            put(x, CROWN_Y, z, STRIPE)
    # ...en in het midden een baken op ijzer. Recht erboven zit gekleurd glas (daar kleurt de
    # straal blauw van) en geen dicht blok, anders ziet hij de lucht niet.
    fill(MID - 1, ROOF_Y, MID - 1, MID + 1, ROOF_Y, MID + 1, "minecraft:iron_block")
    put(MID, ROOF_Y + 1, MID, "minecraft:beacon")
    put(MID, CROWN_Y - 1, MID, GLASS)
    put(MID, CROWN_Y, MID, GLASS)

    build_landing_platform(x1)

    # Antennes (van ijzeren staven: daar gaat de straal gewoon doorheen).
    for y in range(CROWN_Y + 1, SIZE_Y - 1):
        put(MID, y, MID, RAIL, BARS)
    put(MID, SIZE_Y - 1, MID, "minecraft:lightning_rod",
        {"facing": "up", "powered": "false", "waterlogged": "false"})
    for (x, z) in ((MID - 5, MID - 5), (MID + 5, MID + 5)):
        for dy in (1, 2, 3, 4):
            put(x, CROWN_Y + dy, z, "minecraft:end_rod", {"facing": "up"})


def build_landing_platform(roof_x1):
    """
    Het landingsplatform voor het harnas: een dek van 9 x 9 dat aan de oostkant uit het dak
    steekt, zoals in The Avengers. In het midden het Landing Pad-blok (daar land je, en daar laadt
    je bril op), met een blauwe ring om het landingsvak van 5 x 5 en geel-zwarte strepen langs
    de rand. Onder het midden hangt een warmtegenerator tussen drie magmablokken: die geeft het
    platform stroom, en van onderen zie je hem gloeien als een motor.

    Van het platform loop je zo het dak op (de reling is daar open), en de ladder naast de
    opening brengt je naar het penthouse.
    """
    y = ROOF_Y
    x0, x1 = roof_x1 + 1, roof_x1 + 9
    z0, z1 = MID - 4, MID + 4
    cx, cz = roof_x1 + 5, MID
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ring = max(abs(x - cx), abs(z - cz))
            if ring == 4:
                block = "minecraft:yellow_concrete" if (x + z) % 2 == 0 else "minecraft:black_concrete"
            elif ring == 3:
                block = STRIPE
            else:
                block = "minecraft:gray_concrete"
            put(x, y, z, block)
    put(cx, y, cz, "starktech:landing_pad")
    # Landingslichten op de vier hoeken.
    for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        put(x, y, z, LIGHT)
        put(x, y + 1, z, "minecraft:end_rod", {"facing": "up"})
    # De reling van het dak gaat open waar het platform begint.
    for z in range(z0 + 1, z1):
        put(roof_x1, y + 1, z, "minecraft:air")
    # Draagbalken eronder, en de generator met zijn magma.
    for x in range(x0, x1 + 1):
        put(x, y - 1, z0, TRIM)
        put(x, y - 1, z1, TRIM)
    for z in range(z0, z1 + 1):
        put(x1, y - 1, z, TRIM)
    put(cx, y - 1, cz, "starktech:heat_generator", {"facing": "east"})
    put(cx, y - 2, cz, "minecraft:magma_block")
    put(cx - 1, y - 1, cz, "minecraft:magma_block")
    put(cx + 1, y - 1, cz, "minecraft:magma_block")


# --------------------------------------------------------------------- De route naar boven

def build_routes():
    """
    De ladders, als allerlaatste (anders bouwt een verdieping er weer overheen):

    1. De liftkoker van de lobby tot in de lasergang: een ladder tegen een quartz achterwand, met
       glas aan de zijkanten en aan de voorkant. Op elke verdieping zit er vooraan een opening van
       twee hoog, zodat je er daar uit kunt stappen (en niet halverwege naar beneden valt).
    2. Van de kluis langs de oostmuur door het penthouse tot op het dak.
    """
    walk_levels = {LOBBY_Y, LAB_Y, ARCHIVE_Y, HANGAR_Y, LIVING_Y, SECURITY_Y + 1}
    for y in range(LOBBY_Y, LASERS_Y + 1):
        put(SHAFT_X, y, SHAFT_Z, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
        if y == LASERS_Y:
            continue   # hier komt hij in de vloer van de lasergang uit
        put(SHAFT_X, y, SHAFT_Z + 1, TRIM)
        put(SHAFT_X - 1, y, SHAFT_Z, GLASS)
        put(SHAFT_X + 1, y, SHAFT_Z, GLASS)
        put(SHAFT_X - 1, y, SHAFT_Z + 1, COPPER)
        put(SHAFT_X + 1, y, SHAFT_Z + 1, COPPER)
        opening = any(level <= y <= level + 1 for level in walk_levels)
        put(SHAFT_X, y, SHAFT_Z - 1, "minecraft:air" if opening else GLASS)

    # Van de kluis naar het dak: tegen een zuiltje in de kluis, daarboven tegen het glas.
    for y in range(VAULT_Y + 1, ROOF_Y + 1):
        put(EAST_LADDER_X, y, EAST_LADDER_Z, "minecraft:ladder", {"facing": "west", "waterlogged": "false"})
        if y < PENTHOUSE_Y - 1:
            put(EAST_LADDER_X + 1, y, EAST_LADDER_Z, "minecraft:polished_deepslate")


def build():
    build_plaza()
    build_lobby()
    build_lab()
    build_archive()
    build_hangar()
    build_living()
    build_security()
    build_power_plant()
    build_laser_gauntlet()
    build_vault()
    build_penthouse()
    build_roof()
    build_routes()
    connect_cables()


# Blokken waar een Stark-kabel op aansluit (zie PowerConnectable in de mod).
POWER_BLOCKS = {"starktech:cable", "starktech:laser_emitter", "starktech:heat_generator", "starktech:solar_panel",
                "starktech:charging_station", "starktech:dome_generator", "starktech:holo_table",
                "starktech:suit_workbench", "starktech:drone_dock"}


def cable(x, y, z):
    """Een Stark-kabel. Met welke buren hij verbonden is, rekent connect_cables() aan het eind uit."""
    put(x, y, z, "starktech:cable")


def connect_cables():
    """Zet bij elke kabel de kanten aan waar een andere kabel of een apparaat met stroom zit."""
    sides = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
             "up": (0, 1, 0), "down": (0, -1, 0)}
    for (x, y, z), (block, props, nbt) in list(blocks.items()):
        if block != "starktech:cable":
            continue
        props = {}
        for name, (dx, dy, dz) in sides.items():
            neighbour = blocks.get((x + dx, y + dy, z + dz))
            props[name] = "true" if neighbour and neighbour[0] in POWER_BLOCKS else "false"
        blocks[(x, y, z)] = (block, props, nbt)


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
