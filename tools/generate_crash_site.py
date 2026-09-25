"""
Bouwt de neergestorte Stark-satelliet als Minecraft-structuurbestand (.nbt).

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_crash_site.py

Het resultaat komt in src/main/resources/data/starktech/structure/crashed_satellite.nbt.

Een oude Stark-satelliet is uit zijn baan gevallen. Hij kwam schuin uit het oosten, trok een
voor door de grond en sloeg een krater. Wat je vindt:

- een krater van zo'n 25 blokken breed en 6 diep, met een verschroeide bodem (basalt,
  blackstone, grind, een paar gloeiende magmablokken in het midden) en opgeworpen aarde op
  de rand; aan de oostkant de voor waar hij binnenkwam;
- in het midden het wrak: de romp (half open gescheurd, met de gloeiende reactor binnenin),
  één zonnepaneel dat nog vastzit en schuin omhoog steekt, een afgebroken paneel in stukken op
  de helling, de schotel die ondersteboven op de noordhelling ligt, en een verbogen antenne;
- rook: kampvuurtjes in de romp (blauw, van de reactor) en in de voor;
- de ZWARTE DOOS in de romp: een kist met onderdelen voor de satelliet-upgrades (netherite,
  diamant, Stark-chips); en losse kratten met brokstukken.

Het wrak wordt bewaakt: kom je er met de bril op, dan duiken er raiddrones op je af (zie
EdithRaids in de mod).

Hoe hoog hij komt: de structuur ligt 8 blokken "in" de grond (zie start_height in
worldgen/structure/crashed_satellite.json). Laag y = 8 hier is dus de eerste laag lucht boven
de grond, en y = 7 het gras. Blokken die we hier niet noemen, laat Minecraft zoals ze waren: zo
blijft de grond rond de krater gewoon staan.
"""
import math
import random
from pathlib import Path

from generate_structure import DATA_VERSION, Int, write_nbt

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "starktech" / "structure" / "crashed_satellite.nbt"

SIZE_X, SIZE_Y, SIZE_Z = 33, 18, 33
CX, CZ = 16, 16
# De bovenste laag grond (het gras) en de eerste laag lucht erboven.
GROUND = 7
AIR = 8
TOP = SIZE_Y - 1

# De krater: straal aan de rand en diepte in het midden.
RADIUS = 12.5
DEPTH = 6

LOOT_BLACK_BOX = "starktech:chests/crashed_satellite"
LOOT_DEBRIS = "starktech:chests/satellite_debris"

# De zwarte doos, in de romp. De gametest kijkt of hij hier staat.
BLACK_BOX = (15, 2, 16)

rng = random.Random(12)
blocks = {}


def put(x, y, z, block, props=None, nbt=None):
    if 0 <= x < SIZE_X and 0 <= y < SIZE_Y and 0 <= z < SIZE_Z:
        blocks[(x, y, z)] = (block, props or {}, nbt)


def fill(x0, y0, z0, x1, y1, z1, block, props=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, block, props)


def mc(name):
    return "minecraft:" + name


def top_at(x, z):
    """
    De hoogte van het bovenste vaste blok dat we hier al neerzetten (de kraterbodem of de voor),
    of het gewone gras als we hier niets veranderden.
    """
    for y in range(TOP, -1, -1):
        entry = blocks.get((x, y, z))
        if entry and entry[0] != mc("air"):
            return y
    return GROUND


# --------------------------------------------------------------------- De krater

def floor_y(x, z):
    """Hoe hoog de bodem van de krater hier ligt (GROUND = gewoon het maaiveld)."""
    r = math.hypot(x - CX, z - CZ)
    if r >= RADIUS:
        return GROUND
    return GROUND - round(DEPTH * (1 - (r / RADIUS) ** 2))


def scorched(r):
    """Wat er op de bodem ligt: in het midden heet en zwart, naar de rand toe grind en tuf."""
    roll = rng.random()
    if r < 3.5:
        return mc("magma_block") if roll < 0.35 else mc("blackstone") if roll < 0.7 else mc("basalt")
    if r < 7:
        return mc("blackstone") if roll < 0.3 else mc("basalt") if roll < 0.55 else mc("gravel") if roll < 0.8 else mc("coal_block") if roll < 0.87 else mc("tuff")
    return mc("gravel") if roll < 0.45 else mc("tuff") if roll < 0.7 else mc("cobblestone") if roll < 0.85 else mc("blackstone")


def build_crater():
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            r = math.hypot(x - CX, z - CZ)
            if r >= RADIUS:
                continue
            bottom = floor_y(x, z)
            # De lucht erboven weg (ook bomen en hoog gras die er stonden).
            fill(x, bottom + 1, z, x, TOP, z, mc("air"))
            material = scorched(r)
            props = {"axis": "y"} if material == mc("basalt") else None
            put(x, bottom, z, material, props)
            # Eronder dicht: ook als er een grot onder zit, zakt de bodem niet weg.
            put(x, bottom - 1, z, scorched(r) if r > 3.5 else mc("blackstone"))
            put(x, bottom - 2, z, mc("stone"))


def build_rim():
    """Opgeworpen aarde rond de krater: een walletje dat naar buiten toe uitdunt."""
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            r = math.hypot(x - CX, z - CZ)
            if not RADIUS <= r < RADIUS + 4:
                continue
            chance = 0.9 - (r - RADIUS) * 0.22
            if rng.random() > chance:
                continue
            put(x, GROUND, z, mc("dirt"))
            put(x, AIR, z, mc("coarse_dirt") if rng.random() < 0.6 else mc("gravel") if rng.random() < 0.5 else mc("rooted_dirt"))
            # De kam van het walletje, vlak langs de rand, is hoger.
            if r < RADIUS + 0.8 and rng.random() < 0.3:
                put(x, AIR + 1, z, mc("coarse_dirt"))


def build_furrow():
    """De voor waar hij binnenkwam: schuin uit het oosten, en hoe verder weg hoe ondieper."""
    start = CX + int(RADIUS) - 2
    for x in range(start, SIZE_X):
        t = (x - start) / (SIZE_X - start)
        half = 2.4 - 1.5 * t
        depth = 2 if t < 0.6 else 1
        for z in range(SIZE_Z):
            dz = abs(z - CZ)
            if dz > half + 1.5:
                continue
            if dz <= half:
                bottom = GROUND - depth
                fill(x, bottom + 1, z, x, TOP, z, mc("air"))
                roll = rng.random()
                put(x, bottom, z, mc("blackstone") if roll < 0.35 else mc("gravel") if roll < 0.7 else mc("basalt"),
                    {"axis": "y"} if roll >= 0.7 else None)
                put(x, bottom - 1, z, mc("stone"))
            elif rng.random() < 0.7:
                # De aarde die hij opzij schoof.
                put(x, AIR, z, mc("coarse_dirt"))


# --------------------------------------------------------------------- Het wrak

def build_body():
    """
    De romp: een blok van 5 x 5 x 5, half in de bodem, met de oostkant opengescheurd. Binnen
    gloeit de reactor nog, liggen de draden bloot en staat de zwarte doos.
    """
    x0, x1, z0, z1 = 13, 17, 14, 18
    y0, y1 = 1, 5
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                edge = (x in (x0, x1)) + (y in (y0, y1)) + (z in (z0, z1))
                if edge == 0:
                    put(x, y, z, mc("air"))
                elif edge >= 2:
                    put(x, y, z, mc("polished_deepslate"))
                else:
                    put(x, y, z, mc("gray_concrete") if (x + y + z) % 3 else mc("light_gray_concrete"))
    # Opengescheurd aan de oostkant (waar hij binnenkwam) en een stuk van het dak weg.
    for y in range(2, 5):
        for z in range(15, 18):
            put(x1, y, z, mc("air"))
    for x, z in [(16, 15), (16, 16), (15, 17), (17, 14)]:
        put(x, y1, z, mc("air"))
    # De Stark-band op de zuidkant: rood en goud.
    for x in range(x0, x1 + 1):
        put(x, 3, z1, mc("gold_block") if x % 2 else mc("red_concrete"))
    # Binnen: de reactor, blote draden, de zwarte doos en een blauw vuurtje van de reactor.
    put(14, 3, 16, mc("sea_lantern"))
    put(14, 2, 16, mc("redstone_block"))
    put(14, 4, 15, mc("redstone_block"))
    put(14, 4, 17, mc("chain"), {"axis": "y", "waterlogged": "false"})
    bx, by, bz = BLACK_BOX
    put(bx, by, bz, mc("chest"), {"facing": "east", "type": "single", "waterlogged": "false"},
        {"id": mc("chest"), "LootTable": LOOT_BLACK_BOX})
    put(15, 2, 15, mc("soul_campfire"), {"facing": "east", "lit": "true", "signal_fire": "true", "waterlogged": "false"})
    put(15, 2, 17, mc("soul_campfire"), {"facing": "east", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    # Stuwmondjes aan de achterkant.
    put(x0 - 1, 2, 15, mc("dispenser"), {"facing": "west", "triggered": "false"})
    put(x0 - 1, 2, 17, mc("dispenser"), {"facing": "west", "triggered": "false"})


def build_wing():
    """
    Het zonnepaneel dat nog vastzit: het steekt vanaf de westkant schuin omhoog, een trap van
    zonnecellen (omgekeerde daglichtsensoren: blauwe paneeltjes) op een ijzeren ligger.
    """
    for i in range(9):
        x = 12 - i
        y = 4 + i // 2
        for z in range(14, 19):
            if (i, z) in [(7, 14), (8, 14), (8, 18), (6, 18)]:
                continue  # afgebroken hoekjes
            put(x, y, z, mc("daylight_detector"), {"inverted": "true", "power": "0"})
        put(x, y - 1, 16, mc("iron_block") if i % 2 == 0 else mc("light_gray_concrete"))
    # Losgeslingerde kabels die eronder bungelen (bij x = 8 hangt het paneel op y = 6).
    for z in (14, 18):
        for y in (5, 4):
            put(8, y, z, mc("chain"), {"axis": "y", "waterlogged": "false"})


def build_broken_wing():
    """Het andere paneel: in stukken op de zuidhelling, en één stuk dat rechtop in de grond steekt."""
    for x in range(16, 24):
        for z in range(21, 25):
            if rng.random() < 0.55:
                put(x, floor_y(x, z) + 1, z, mc("daylight_detector"), {"inverted": "true", "power": "0"})
    # Rechtop in de grond geboord.
    for y in range(3):
        for z in range(19, 22):
            base = floor_y(25, z)
            put(25, base + 1 + y, z, mc("blue_stained_glass") if 0 < z - 19 < 2 and y < 2 else mc("light_gray_concrete"))


def build_dish():
    """De schotel: van de romp losgeslagen, ondersteboven op de noordhelling."""
    dx, dz = 15, 8
    for x in range(dx - 3, dx + 4):
        for z in range(dz - 3, dz + 4):
            r = math.hypot(x - dx, z - dz)
            if r > 3.2 or rng.random() < 0.12:
                continue
            y = floor_y(x, z) + 1
            if r > 2.4:
                put(x, y, z, mc("white_concrete"))
            else:
                put(x, y, z, mc("smooth_quartz_slab"), {"type": "bottom", "waterlogged": "false"})
    # De ontvanger in het midden, en de arm die hem vasthield.
    y = floor_y(dx, dz) + 2
    put(dx, y, dz, mc("end_rod"), {"facing": "up"})
    put(dx + 1, y - 1, dz, mc("lightning_rod"), {"facing": "east", "powered": "false", "waterlogged": "false"})


def build_antenna():
    """Een verbogen antenne op het dak van de romp."""
    for y in range(6, 9):
        put(14, y, 15, mc("lightning_rod"), {"facing": "up", "powered": "false", "waterlogged": "false"})
    put(15, 9, 15, mc("end_rod"), {"facing": "east"})
    put(16, 9, 15, mc("end_rod"), {"facing": "east"})


def build_debris():
    """Brokstukken in en rond de krater, twee kratten met onderdelen, en rook in de voor."""
    pieces = [mc("iron_trapdoor"), mc("gray_concrete"), mc("light_gray_concrete"), mc("iron_bars"),
              mc("chain"), mc("damaged_anvil")]
    for _ in range(26):
        angle = rng.random() * math.tau
        r = 4 + rng.random() * 12
        x = round(CX + math.cos(angle) * r)
        z = round(CZ + math.sin(angle) * r)
        if not (0 <= x < SIZE_X and 0 <= z < SIZE_Z):
            continue
        y = top_at(x, z) + 1
        if y > TOP or blocks.get((x, y, z), (None,))[0] not in (None, mc("air")):
            continue
        piece = rng.choice(pieces)
        if piece == mc("iron_trapdoor"):
            put(x, y, z, piece, {"facing": "north", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})
        elif piece == mc("chain"):
            put(x, y, z, piece, {"axis": "x", "waterlogged": "false"})
        elif piece == mc("iron_bars"):
            put(x, y, z, piece, {"north": "false", "south": "false", "east": "true", "west": "true", "waterlogged": "false"})
        elif piece == mc("damaged_anvil"):
            put(x, y, z, piece, {"facing": "west"})
        else:
            put(x, y, z, piece)
    for x, z in [(20, 23), (27, 17)]:
        put(x, top_at(x, z) + 1, z, mc("barrel"), {"facing": "up", "open": "false"},
            {"id": mc("barrel"), "LootTable": LOOT_DEBRIS})
    # Het spoor van de inslag smeult nog.
    for x, z in [(29, 16), (24, 15)]:
        put(x, top_at(x, z) + 1, z, mc("campfire"),
            {"facing": "west", "lit": "true", "signal_fire": "false", "waterlogged": "false"})


def build():
    build_crater()
    build_rim()
    build_furrow()
    build_body()
    build_wing()
    build_broken_wing()
    build_dish()
    build_antenna()
    build_debris()


def to_nbt():
    palette, palette_index, block_list = [], {}, []
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
