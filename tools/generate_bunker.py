"""
Bouwt de Hydra-bunker als Minecraft-structuurbestand (.nbt).

Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_bunker.py

Het resultaat komt in src/main/resources/data/starktech/structure/hydra_bunker.nbt.

Een geheime Hydra-basis, uitgehakt in een berg (zoals in The Winter Soldier). De mod zoekt
zelf een steile berghelling en zet hem daar zo neer dat de ingang in de rotswand zit en de
rest ín de berg (zie HydraBunkerStructure). Wat je vindt, van voor naar achter:

- de INGANG: een uitsparing in de rots, een betonnen gevel met het rode Hydra-embleem, en
  links en rechts van de deur een Hydra-torentje op een zuil;
- de GANG met een controlepost (een bureau en een soldaat);
- de GROTE HAL: zuilen, kratten met voorraden, Hydra-vaandels, een kaartentafel, soldaten en
  een torentje op een verhoging achterin;
- links de SLAAPZAAL (bedden, kisten), rechts de REACTORKAMER: twee warmtegeneratoren in een
  bak lava achter glas. Hun kabels lopen zichtbaar over het plafond van de hal en verdwijnen
  achterin in de muur, naar...
- ...de KLUISGANG, met twee rode laserwanden (drie rijen hoog). Die staan aan zolang de
  reactor stroom geeft: knip de kabels door of sloop de generatoren, en ze gaan uit;
- de KLUIS: twee kisten met Hydra-onderdelen (altijd een paar), een cryo-cel (Winter Soldier)
  en twee torentjes.

Soldaten staan er niet als blokken in, maar als "data-markeringen" (een structuurblok met de
tekst hydra_soldier). Bij het bouwen van de wereld zet de mod daar een Hydra-soldaat neer die
nooit verdwijnt (zie HydraBunkerPiece).

De voorkant (de ingang) ligt aan de kant z = 0 en kijkt naar -z (noord); de bunker loopt naar
+z de berg in. Laag y = 0 is de vloer, y = 1 de eerste laag lucht. Blokken die we hier niet
noemen, laat Minecraft zoals ze waren: dat is de berg zelf, om de kamers heen.
"""
from pathlib import Path

from generate_structure import DATA_VERSION, Int, write_nbt

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "starktech" / "structure" / "hydra_bunker.nbt"

SIZE_X, SIZE_Y, SIZE_Z = 35, 8, 44
MID = 17

LOOT_VAULT = "starktech:chests/hydra_vault"
LOOT_BARRACKS = "starktech:chests/hydra_barracks"
LOOT_SUPPLIES = "starktech:chests/hydra_supplies"

# Waar dingen staan die de gametest nakijkt.
VAULT_CHESTS = [(13, 1, 42), (21, 1, 42)]
LASER_ROWS = [32, 35]
SOLDIER = "hydra_soldier"

POWER_BLOCKS = {"starktech:cable", "starktech:laser_emitter", "starktech:heat_generator"}

# Materialen
WALL = "minecraft:deepslate_tiles"
CONCRETE = "minecraft:gray_concrete"
FLOOR = "minecraft:polished_deepslate"
TRIM = "minecraft:polished_blackstone"
LIGHT = "minecraft:sea_lantern"
RED = "minecraft:red_concrete"
BLACK = "minecraft:black_concrete"

blocks = {}


def put(x, y, z, block, props=None, nbt=None):
    if 0 <= x < SIZE_X and 0 <= y < SIZE_Y and 0 <= z < SIZE_Z:
        blocks[(x, y, z)] = (block, props or {}, nbt)


def fill(x0, y0, z0, x1, y1, z1, block, props=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, block, props)


def room(x0, z0, x1, z1, height, wall=WALL, floor=FLOOR, ceiling=WALL):
    """
    Een kamer: lucht van (x0, 1, z0) tot (x1, height, z1), met eromheen muren, een vloer op
    y = 0 en een plafond op height + 1. Deuren hak je er daarna in met air().
    """
    fill(x0 - 1, 0, z0 - 1, x1 + 1, height + 1, z1 + 1, wall)
    fill(x0, 0, z0, x1, 0, z1, floor)
    fill(x0, height + 1, z0, x1, height + 1, z1, ceiling)
    fill(x0, 1, z0, x1, height, z1, "minecraft:air")


def air(x0, y0, z0, x1, y1, z1):
    fill(x0, y0, z0, x1, y1, z1, "minecraft:air")


def soldier(x, y, z):
    """Hier komt bij het bouwen van de wereld een Hydra-soldaat (zie HydraBunkerPiece)."""
    put(x, y, z, "minecraft:structure_block", {"mode": "data"},
        {"id": "minecraft:structure_block", "mode": "DATA", "metadata": SOLDIER})


def turret(x, y, z):
    put(x, y, z, "starktech:hydra_turret", {"enabled": "true", "powered": "false"})


def chest(x, y, z, facing, loot):
    put(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
        {"id": "minecraft:chest", "LootTable": loot})


def barrel(x, y, z):
    put(x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"},
        {"id": "minecraft:barrel", "LootTable": LOOT_SUPPLIES})


def banner(x, y, z, facing):
    """Een rood Hydra-vaandel met een zwarte schedel en rand, aan de muur."""
    put(x, y, z, "minecraft:red_wall_banner", {"facing": facing},
        {"id": "minecraft:banner", "patterns": [
            {"color": "black", "pattern": "minecraft:skull"},
            {"color": "black", "pattern": "minecraft:curly_border"},
        ]})


def cable(x, y, z):
    """Een Stark-kabel. Met welke buren hij verbonden is, rekent connect_cables() uit."""
    put(x, y, z, "starktech:cable")


# --------------------------------------------------------------------- Ingang

def build_entrance():
    # De uitsparing in de rots voor de deur: zo breed als de gevel, 6 hoog, 3 diep.
    air(MID - 5, 1, 0, MID + 5, 6, 2)
    for x in range(MID - 5, MID + 6):
        for z in range(0, 3):
            put(x, 0, z, "minecraft:gravel" if (x + z) % 3 == 0 else "minecraft:polished_andesite")
    # De gevel: grijs beton met een donkere rand.
    fill(MID - 5, 0, 3, MID + 5, 7, 3, CONCRETE)
    for y in range(0, 8):
        put(MID - 5, y, 3, FLOOR)
        put(MID + 5, y, 3, FLOOR)
    fill(MID - 5, 7, 3, MID + 5, 7, 3, FLOOR)
    # De deuropening met een zwarte omlijsting.
    fill(MID - 2, 1, 3, MID + 2, 5, 3, TRIM)
    air(MID - 1, 1, 3, MID + 1, 4, 3)
    # Het Hydra-embleem boven de deur: een schedel met tentakels, rood op zwart.
    fill(MID - 2, 6, 3, MID + 2, 6, 3, BLACK)
    for x in (MID - 2, MID, MID + 2):
        put(x, 6, 3, RED)
    for x in (MID - 1, MID, MID + 1):
        put(x, 7, 3, RED)
    # Twee torentjes op een zuil, naast de deur.
    for x in (MID - 3, MID + 3):
        fill(x, 1, 1, x, 2, 1, FLOOR)
        turret(x, 3, 1)


# --------------------------------------------------------------------- Gang en hal

def build_corridor():
    room(MID - 2, 4, MID + 2, 13, 4, wall=CONCRETE, ceiling=CONCRETE)
    air(MID - 2, 1, 14, MID + 2, 4, 14)  # de doorgang naar de hal
    air(MID - 1, 1, 3, MID + 1, 4, 3)    # de voordeur weer open (de muur liep erover)
    for z in (5, 9, 13):
        put(MID, 5, z, LIGHT)
    # Een rode streep over de vloer.
    for z in range(4, 15):
        put(MID, 0, z, RED)
    # De controlepost: een bureau met een lessenaar, en een soldaat.
    put(MID - 2, 1, 8, TRIM)
    put(MID - 2, 1, 9, TRIM)
    put(MID - 2, 2, 8, "minecraft:polished_blackstone_pressure_plate", {"powered": "false"})
    soldier(MID + 1, 1, 9)


def build_hall():
    x0, z0, x1, z1 = MID - 8, 15, MID + 8, 28
    room(x0, z0, x1, z1, 6)
    air(MID - 2, 1, 14, MID + 2, 4, 14)
    # Een rode streep in het midden van de vloer, verder donker.
    for z in range(z0, z1 + 1):
        put(MID, 0, z, RED)
    # Licht in het plafond.
    for x in (MID - 5, MID, MID + 5):
        for z in (17, 21, 25):
            put(x, 7, z, LIGHT)
    # Twee rijen zuilen.
    for x in (MID - 4, MID + 4):
        for z in (18, 22, 26):
            fill(x, 1, z, x, 6, z, FLOOR)
    # Kratten met voorraden langs de muren.
    for (x, z) in [(x0, 16), (x0, 17), (x0, 19), (x1, 16), (x1, 17), (x1, 25)]:
        barrel(x, 1, z)
    barrel(x0, 2, 16)
    barrel(x1, 2, 16)
    # De kaartentafel in het midden.
    put(MID - 1, 1, 20, "minecraft:cartography_table")
    put(MID + 1, 1, 20, "minecraft:cartography_table")
    put(MID, 1, 20, "minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    # Hydra-vaandels aan de voor- en achtermuur.
    for x in (MID - 5, MID + 5):
        banner(x, 4, z1, "north")
        banner(x, 4, z0, "south")
    # Een verhoging achterin (links) met een torentje erop, en een ladder ernaartoe.
    fill(x0 + 1, 1, 26, x0 + 3, 2, 28, "minecraft:stone_bricks")
    turret(x0 + 2, 3, 27)
    put(x0 + 4, 1, 27, "minecraft:ladder", {"facing": "east", "waterlogged": "false"})
    put(x0 + 4, 2, 27, "minecraft:ladder", {"facing": "east", "waterlogged": "false"})
    # Soldaten.
    soldier(MID - 5, 1, 20)
    soldier(MID + 5, 1, 24)
    soldier(MID, 1, 26)


def build_barracks():
    x0, z0, x1, z1 = 1, 17, MID - 10, 26
    room(x0, z0, x1, z1, 4)
    air(MID - 9, 1, 21, MID - 9, 2, 21)  # deur naar de hal
    for z in (18, 20, 22, 24, 26):
        put(1, 1, z, "minecraft:red_bed", {"facing": "west", "part": "head", "occupied": "false"})
        put(2, 1, z, "minecraft:red_bed", {"facing": "west", "part": "foot", "occupied": "false"})
    chest(x1, 1, 18, "west", LOOT_BARRACKS)
    chest(x1, 1, 25, "west", LOOT_BARRACKS)
    for z in (19, 24):
        put(4, 5, z, LIGHT)
    soldier(4, 1, 20)
    soldier(5, 1, 24)


def build_reactor():
    x0, z0, x1, z1 = MID + 10, 17, 33, 26
    room(x0, z0, x1, z1, 5)
    air(MID + 9, 1, 21, MID + 9, 2, 21)  # deur naar de hal
    # De bak lava achter glas, met twee warmtegeneratoren erin.
    fill(28, 1, 18, 32, 2, 24, "minecraft:glass")
    fill(29, 1, 19, 31, 1, 23, "minecraft:lava", {"level": "0"})
    fill(29, 2, 19, 31, 2, 23, "minecraft:air")
    for z in (20, 22):
        put(30, 1, z, "starktech:heat_generator")
        # De kabel gaat omhoog, niet onder de lava door (lava zou hem opeten).
        for y in range(2, 6):
            cable(30, y, z)
    cable(30, 5, 21)
    for z in (19, 24):
        put(30, 6, z, LIGHT)
    # Een waarschuwingsstreep op de vloer rond de bak.
    for x in range(28, 33):
        put(x, 0, 25, "minecraft:yellow_concrete")
    soldier(28, 1, 25)


# --------------------------------------------------------------------- Kluisgang en kluis

def build_power_lines():
    """
    De kabel van de reactor: door de muur de hal in, langs het plafond naar achteren, en dan de
    achtermuur in. Daarachter loopt hij door de rots naar de laserwanden in de kluisgang.
    """
    for x in range(MID + 9, 31):          # in de reactorkamer en door de muur
        cable(x, 5, 21)
    cable(MID + 8, 5, 21)                 # net in de hal, en omhoog tegen het plafond
    cable(MID + 8, 6, 21)
    for x in range(MID + 3, MID + 8):
        cable(x, 6, 21)
    for z in range(22, 37):               # naar achteren, door de achtermuur, de rots in
        cable(MID + 3, 6, z)
    for z in LASER_ROWS:                  # omlaag naar de lijnen in het plafond van de kluisgang
        cable(MID + 3, 5, z + 1)


def build_vault_corridor():
    room(MID - 1, 30, MID + 1, 37, 4)
    air(MID - 1, 1, 29, MID + 1, 4, 29)  # doorgang vanuit de hal
    for z in LASER_ROWS:
        for y in (1, 2, 3):
            # Een zender in de linkermuur die naar rechts kijkt; de wand loopt tot de rechtermuur.
            put(MID - 2, y, z, "starktech:laser_emitter", {"facing": "east", "powered": "false", "color": "red"})
            # Redstone in de rots erachter: hij staat altijd "aan"...
            put(MID - 3, y, z, "minecraft:redstone_block")
            # ...en stroom via een kabel in de muur ernaast.
            cable(MID - 2, y, z + 1)
        cable(MID - 2, 4, z + 1)
        cable(MID - 2, 5, z + 1)
        for x in range(MID - 1, MID + 4):
            cable(x, 5, z + 1)
    put(MID, 5, 31, LIGHT)
    put(MID, 5, 34, LIGHT)


def build_vault():
    x0, z0, x1, z1 = MID - 5, 39, MID + 5, 42
    room(x0, z0, x1, z1, 5)
    air(MID - 1, 1, 38, MID + 1, 4, 38)
    for (x, y, z) in VAULT_CHESTS:
        chest(x, y, z, "north", LOOT_VAULT)
    # De cryo-cel (Winter Soldier): ijs achter glas, in een ijzeren kast.
    fill(MID - 1, 1, 42, MID + 1, 3, 42, "minecraft:iron_block")
    put(MID, 1, 42, "minecraft:blue_ice")
    put(MID, 2, 42, "minecraft:blue_ice")
    put(MID, 1, 41, "minecraft:light_blue_stained_glass")
    put(MID, 2, 41, "minecraft:light_blue_stained_glass")
    put(MID, 3, 41, "minecraft:iron_block")
    # Wat Hydra nog meer weggestopt heeft.
    put(x0, 1, 42, "minecraft:gold_block")
    put(x1, 1, 42, "minecraft:gold_block")
    for x in (x0 + 2, x1 - 2):
        put(x, 6, 40, LIGHT)
    banner(MID - 3, 4, z1, "north")
    banner(MID + 3, 4, z1, "north")
    turret(x0, 1, z0)
    turret(x1, 1, z0)
    soldier(MID + 2, 1, 40)


# --------------------------------------------------------------------- Bestand

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


def build():
    # Volgorde telt: latere kamers hakken hun deuren in de muren van eerdere.
    build_corridor()
    build_hall()
    build_barracks()
    build_reactor()
    build_vault_corridor()
    build_vault()
    build_power_lines()
    build_entrance()
    connect_cables()


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
    soldiers = sum(1 for b in blocks.values() if b[0] == "minecraft:structure_block")
    print(f"geschreven: {OUT} ({len(blocks)} blokken, {soldiers} soldaten)")
