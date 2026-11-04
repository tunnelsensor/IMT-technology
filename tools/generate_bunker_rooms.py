"""
Bouwt de Biebia's Industries-bunker ZAAL PER ZAAL (hoofdstuk 51, 4 november): losse structuurbestanden in
src/main/resources/data/imttech/structure/bunker/, die de mod bij het bouwen van de wereld achter elkaar de berg in
zet, in een willekeurige volgorde (zie BiebiaBunkerStructure en BunkerLayout in de Java-code). Zo is elke bunker
anders. Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_bunker_rooms.py

De stukken (allemaal 35 breed en 8 hoog; de ingang kijkt naar -z, de bunker loopt naar +z de berg in):
- entrance.nbt (15 diep): de uitsparing in de rots, de gevel met het embleem en de torentjes, de gang met de
                controlepost. Ligt altijd vooraan.
- <zaal>.nbt   (14 diep): een zaal met de hoofdgang in het midden en kamers opzij. De klassieke: hall (de grote hal met
                de slaapzaal) en reactor (de reactorkamer met de stroomkast: altijd één keer, want die geeft de stroom);
                nieuw: armory, lab, cells, garage, mess en comms.
- vault.nbt    (15 diep): de kluisgang met de twee laserwanden en de kluis. Ligt altijd achteraan.

Door elke zaal lopen twee kabels over de volle diepte, altijd op dezelfde plek: in het plafond van de hoofdgang (de
stroom voor de laserwanden) en in de vloer (de torentjes). De reactor voedt ze allebei, de kluis en de ingang hangen
eraan. Zo werkt alles, in welke volgorde de zalen ook liggen.

Het bouwen doet generate_bunker.py (de klassieke bunker in één stuk): de klassieke zalen bouwen we met zijn functies
en schuiven we dan op; de nieuwe zalen staan hier. De klassieke bunker blijft bestaan (biebia_bunker.nbt).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_bunker as gb  # noqa: E402

OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "imttech" / "structure" / "bunker"

MID = gb.MID
HALL_DEPTH = 14
END_DEPTH = 15
# De twee kabels door elke zaal: in het plafond van de hoofdgang (lasers) en in de vloer (torentjes).
CEILING_SPINE_X, CEILING_SPINE_Y = MID + 3, 6
FLOOR_SPINE_X = MID - 6

WALL, CONCRETE, FLOOR, TRIM, LIGHT, RED, BLACK = gb.WALL, gb.CONCRETE, gb.FLOOR, gb.TRIM, gb.LIGHT, gb.RED, gb.BLACK


# --------------------------------------------------------------------- Gereedschap

def reset(depth):
    gb.blocks.clear()
    gb.cables.clear()
    gb.SIZE_Z = depth


def shift(dz, z_min, z_max):
    """Schuift alles wat er staat {@code dz} op langs z, en houdt alleen wat tussen z_min en z_max valt."""
    moved = {}
    for (x, y, z), value in gb.blocks.items():
        if z_min <= z + dz <= z_max:
            moved[(x, y, z + dz)] = value
    gb.blocks.clear()
    gb.blocks.update(moved)
    cables = {(x, y, z + dz) for (x, y, z) in gb.cables if z_min <= z + dz <= z_max}
    gb.cables.clear()
    gb.cables.update(cables)


def write(name):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    gb.write_nbt(OUT_DIR / f"{name}.nbt", gb.to_nbt())
    soldiers = sum(1 for b in gb.blocks.values() if b[0] == "minecraft:structure_block")
    print(f"geschreven: bunker/{name}.nbt ({len(gb.blocks)} blokken, {len(gb.cables)} kabels, {soldiers} soldaten)")


def spines(depth, ceiling=True, floor=True):
    for z in range(depth):
        if ceiling:
            gb.cable(CEILING_SPINE_X, CEILING_SPINE_Y, z)
        if floor:
            gb.cable(FLOOR_SPINE_X, 0, z)


def end_walls(depth, x0=MID - 9, x1=MID + 9):
    """De muren aan beide uiteinden van een zaal, met de doorgang van de hoofdgang erin."""
    for z in (0, depth - 1):
        gb.fill(x0, 0, z, x1, 7, z, WALL)
        gb.air(MID - 2, 1, z, MID + 2, 4, z)
        gb.put(MID, 0, z, RED)


def hall_shell(depth=HALL_DEPTH, height=6):
    """De hoofdgang/hal in het midden van een zaal: 17 breed, met de rode streep, licht en de twee kabels."""
    gb.room(MID - 8, 1, MID + 8, depth - 2, height)
    end_walls(depth)
    for z in range(depth):
        gb.put(MID, 0, z, RED)
    for z in range(2, depth - 1, 4):
        gb.put(MID, height + 1, z, LIGHT)
    spines(depth)


def side_room(west, z0, z1, height=4):
    """Een kamer naast de hal (west: x 1..MID-10, oost: x MID+10..33), met een deur naar de hal."""
    if west:
        gb.room(1, z0, MID - 10, z1, height)
        gb.air(MID - 9, 1, (z0 + z1) // 2, MID - 9, 2, (z0 + z1) // 2)
    else:
        gb.room(MID + 10, z0, 33, z1, height)
        gb.air(MID + 9, 1, (z0 + z1) // 2, MID + 9, 2, (z0 + z1) // 2)


def floor_turret(x, z):
    """Een torentje op de vloer, met zijn stroom uit de vloerkabel."""
    gb.turret(x, 1, z)
    gb.route((FLOOR_SPINE_X, 0, z), (x, 0, z))


# --------------------------------------------------------------------- De klassieke stukken

def build_entrance_piece():
    reset(END_DEPTH)
    gb.build_corridor()
    gb.build_entrance()
    # De vloerkabel, en de twee torentjes naast de deur (op hun zuil).
    spines(END_DEPTH, ceiling=False)
    gb.route((FLOOR_SPINE_X, 0, 1), (MID + 3, 0, 1))
    gb.route((MID - 3, 0, 1), (MID - 3, 2, 1))
    gb.route((MID + 3, 0, 1), (MID + 3, 2, 1))
    write("entrance")


def build_hall_piece():
    """De grote hal met de slaapzaal (zoals de klassieke bunker), opgeschoven naar het begin van het stuk."""
    reset(44)
    gb.build_hall()
    gb.build_barracks()
    shift(-15, 0, HALL_DEPTH - 1)
    gb.SIZE_Z = HALL_DEPTH
    end_walls(HALL_DEPTH)
    spines(HALL_DEPTH)
    # Het torentje op de verhoging achterin, aan de vloerkabel (via de zuil).
    gb.route((FLOOR_SPINE_X, 0, 12), (MID - 6, 0, 12), (MID - 6, 2, 12))
    write("hall")


def build_reactor_piece():
    """De reactorkamer met de stroomkast (zoals de klassieke bunker), een wachtpost ertegenover, en de hal ertussen."""
    reset(44)
    gb.build_hall()
    gb.build_reactor()
    shift(-15, 0, HALL_DEPTH - 1)
    gb.SIZE_Z = HALL_DEPTH
    end_walls(HALL_DEPTH)
    spines(HALL_DEPTH)
    # De stroomkast in de hoek van de reactorkamer (zie wire_reactor in generate_bunker.py), nu met de twee kabels van
    # dit stuk: boven de lasers (de plafondkabel), onder de torentjes (de vloerkabel).
    cabinet = (33, 1, 11)
    gb.put(*cabinet, "imttech:power_cabinet", {"facing": "west"})
    gb.cables.discard((30, 5, 6))
    gb.route((31, 5, 6), (33, 5, 6), (33, 1, 6), (33, 1, 10))
    gb.route((33, 2, 11), (33, 5, 11), (28, 5, 11), (28, 5, 6), (MID + 9, 5, 6))
    gb.cable(MID + 8, 5, 6)
    gb.cable(MID + 8, 6, 6)
    gb.route((MID + 7, 6, 6), (CEILING_SPINE_X, 6, 6))
    gb.route((33, 0, 11), (FLOOR_SPINE_X, 0, 11))
    gb.turret(30, 1, 11)
    # De wachtpost aan de westkant: een bureau, een soldaat en een torentje.
    side_room(True, 4, 9)
    gb.put(3, 1, 6, TRIM)
    gb.put(3, 2, 6, "minecraft:polished_blackstone_pressure_plate", {"powered": "false"})
    gb.put(2, 5, 6, LIGHT)
    gb.soldier(4, 1, 7)
    floor_turret(6, 5)
    write("reactor")


def build_vault_piece():
    reset(44)
    gb.build_vault_corridor()
    gb.build_vault()
    shift(-29, 0, END_DEPTH - 1)
    gb.SIZE_Z = END_DEPTH
    # De deur van de kluisgang zit in de voorste muur van dit stuk; de hal ervoor opent ernaartoe.
    gb.air(MID - 1, 1, 0, MID + 1, 4, 0)
    spines(END_DEPTH)
    for z in gb.LASER_ROWS:
        gb.cable(CEILING_SPINE_X, 5, z - 29 + 1)
    gb.route((FLOOR_SPINE_X, 0, 10), (MID + 5, 0, 10))
    write("vault")


# --------------------------------------------------------------------- Nieuwe zalen

def build_armory():
    """De wapenkamer: rekken met geweren (ijzer en kettingen), kratten met munitie, en een schietbaan met doelen."""
    hall_shell()
    gb.banner(MID - 5, 4, 0, "south")
    gb.banner(MID + 5, 4, 13, "north")
    # West: de wapenkamer. Rekken: een plank van blackstone met "geweren" (ijzeren staven) erop, kettingen ervoor.
    side_room(True, 2, 11, 4)
    for z in range(3, 11, 2):
        gb.put(1, 2, z, "minecraft:polished_blackstone_slab", {"type": "top", "waterlogged": "false"})
        gb.put(1, 3, z, "minecraft:iron_bars", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
        gb.put(2, 3, z, "minecraft:chain", {"axis": "x", "waterlogged": "false"})
    for z in (3, 6, 9):
        gb.barrel(MID - 10, 1, z)
        gb.barrel(MID - 10, 2, z)
    gb.chest(4, 1, 10, "north", gb.LOOT_SUPPLIES)
    gb.put(4, 5, 6, LIGHT)
    gb.soldier(5, 1, 5)
    # Oost: de schietbaan. Drie doelen achterin, een balie vooraan, en kratten.
    side_room(False, 2, 11, 4)
    for x in (MID + 12, MID + 14, MID + 16):
        gb.put(x, 2, 11, "minecraft:target", {"power": "0"})
        gb.put(x, 1, 11, "minecraft:polished_blackstone")
    for x in range(MID + 11, MID + 17):
        gb.put(x, 1, 4, TRIM)
        gb.put(x, 0, 5, "minecraft:yellow_concrete")
    gb.put(MID + 13, 2, 4, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    gb.barrel(33, 1, 3)
    gb.barrel(33, 1, 10)
    gb.put(MID + 14, 5, 7, LIGHT)
    gb.soldier(MID + 15, 1, 5)
    floor_turret(MID - 7, 2)
    write("armory")


def build_lab():
    """Het lab van Raven: brouwstanden en ketels, specimens in glazen tanks, en een cryo-cel."""
    hall_shell()
    gb.banner(MID - 5, 4, 0, "south")
    # West: het lab met de brouwstanden.
    side_room(True, 2, 11, 4)
    for z in range(3, 11, 2):
        gb.put(2, 1, z, TRIM)
        gb.put(2, 2, z, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
        gb.put(3, 1, z, "minecraft:cauldron")
    for z in (4, 8):
        gb.put(5, 1, z, "minecraft:crafter", {"orientation": "east_up", "crafting": "false", "triggered": "false"})
        gb.put(5, 2, z, "minecraft:end_rod", {"facing": "up"})
    gb.chest(MID - 10, 1, 3, "west", gb.LOOT_SUPPLIES)
    gb.put(4, 5, 6, LIGHT)
    gb.soldier(6, 1, 9)
    # Oost: specimens in tanks (glas met water en een lamp), en de cryo-cel.
    side_room(False, 2, 11, 5)
    for z in (3, 7):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for h in (1, 2, 3):
                    if dx or dz:
                        gb.put(MID + 14 + dx, h, z + dz, "minecraft:glass")
        gb.put(MID + 14, 1, z, "minecraft:water", {"level": "0"})
        gb.put(MID + 14, 2, z, "minecraft:water", {"level": "0"})
        gb.put(MID + 14, 3, z, "minecraft:glass")
        gb.put(MID + 14, 0, z, LIGHT)
    gb.fill(31, 1, 10, 33, 3, 10, "minecraft:iron_block")
    gb.put(32, 1, 10, "minecraft:blue_ice")
    gb.put(32, 2, 10, "minecraft:blue_ice")
    gb.put(32, 1, 9, "minecraft:light_blue_stained_glass")
    gb.put(32, 2, 9, "minecraft:light_blue_stained_glass")
    gb.put(MID + 12, 6, 6, LIGHT)
    gb.soldier(MID + 11, 1, 4)
    floor_turret(MID + 7, 11)
    write("lab")


def build_cells():
    """De cellen: aan weerskanten van de hal cellen met tralies, een bed en een wasbak, en een bewaker bij elke rij."""
    hall_shell()
    for west in (True, False):
        side_room(west, 2, 11, 3)
        base = 1 if west else MID + 10
        far = MID - 10 if west else 33
        # Een gang langs de hal, en drie cellen erachter, gescheiden door muren van tralies.
        corridor_x = far if west else base
        for z in (3, 6, 9):
            cell_x0 = base if west else base + 2
            cell_x1 = far - 2 if west else far
            for x in range(cell_x0, cell_x1 + 1):
                gb.put(x, 1, z + 2, WALL)
                gb.put(x, 2, z + 2, WALL)
                gb.put(x, 3, z + 2, WALL)
            bars_x = far - 1 if west else base + 1
            for h in (1, 2, 3):
                for dz in (0, 1):
                    gb.put(bars_x, h, z + dz, "minecraft:iron_bars",
                           {"north": "true", "south": "true", "east": "false", "west": "false", "waterlogged": "false"})
            gb.put(bars_x, 1, z, "minecraft:iron_door",
                   {"facing": "east" if west else "west", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
            gb.put(bars_x, 2, z, "minecraft:iron_door",
                   {"facing": "east" if west else "west", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
            bed_x = cell_x0 if west else cell_x1
            facing = "east" if west else "west"
            gb.put(bed_x, 1, z + 1, "minecraft:gray_bed", {"facing": facing, "part": "head", "occupied": "false"})
            gb.put(bed_x + (1 if west else -1), 1, z + 1, "minecraft:gray_bed", {"facing": facing, "part": "foot", "occupied": "false"})
            gb.put(bed_x, 1, z, "minecraft:water_cauldron", {"level": "1"})
            gb.put(cell_x0 + 1, 4, z, LIGHT)
        gb.soldier(corridor_x, 1, 2)
        gb.put(corridor_x, 4, 6, LIGHT)
    gb.put(MID - 1, 1, 2, TRIM)
    gb.put(MID - 1, 2, 2, "minecraft:polished_blackstone_pressure_plate", {"powered": "false"})
    gb.soldier(MID + 1, 1, 3)
    floor_turret(MID + 7, 11)
    write("cells")


def build_garage():
    """De garage: een pantserwagen van Raven op een spoor, vaten brandstof, een takel en een gereedschapswand."""
    hall_shell(height=6)
    # West: de wagen op een spoor dat naar de hal loopt.
    side_room(True, 2, 11, 5)
    for x in range(2, MID - 9):
        gb.put(x, 1, 6, "minecraft:rail", {"shape": "east_west", "waterlogged": "false"})
    gb.fill(3, 2, 5, 8, 2, 7, BLACK)
    gb.fill(4, 3, 5, 7, 3, 7, "minecraft:gray_concrete")
    gb.fill(5, 4, 6, 6, 4, 6, BLACK)
    for x in (3, 8):
        for z in (5, 7):
            gb.put(x, 1, z, "minecraft:black_concrete")
    gb.put(5, 3, 4, "minecraft:tinted_glass")
    gb.put(6, 3, 4, "minecraft:tinted_glass")
    gb.put(8, 3, 6, "minecraft:iron_bars", {"north": "false", "south": "false", "east": "true", "west": "false", "waterlogged": "false"})
    gb.put(3, 2, 6, RED)
    for z in (3, 10):
        gb.barrel(2, 1, z)
        gb.barrel(3, 1, z)
    gb.put(5, 6, 6, LIGHT)
    gb.soldier(MID - 11, 1, 9)
    # Oost: de werkplaats met de takel (kettingen) en de gereedschapswand.
    side_room(False, 2, 11, 5)
    for h in (6, 5, 4):
        gb.put(MID + 14, h, 6, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    gb.put(MID + 14, 3, 6, "minecraft:iron_block")
    gb.put(MID + 11, 1, 3, "minecraft:anvil", {"facing": "east"})
    gb.put(MID + 11, 1, 5, "minecraft:smithing_table")
    gb.put(MID + 11, 1, 7, "minecraft:grindstone", {"face": "floor", "facing": "east"})
    gb.put(MID + 11, 1, 9, "minecraft:crafting_table")
    for z in range(3, 11, 2):
        gb.put(33, 2, z, "minecraft:iron_trapdoor", {"facing": "west", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    gb.chest(32, 1, 10, "north", gb.LOOT_SUPPLIES)
    gb.put(MID + 14, 6, 9, LIGHT)
    gb.soldier(MID + 12, 1, 10)
    floor_turret(MID - 7, 11)
    write("garage")


def build_mess():
    """De kantine: een keuken met rokers en ketels aan de ene kant, tafels met krukjes aan de andere."""
    hall_shell()
    gb.banner(MID - 5, 4, 0, "south")
    gb.banner(MID + 5, 4, 13, "north")
    # West: de keuken.
    side_room(True, 2, 11, 4)
    for z in range(3, 11):
        gb.put(1, 1, z, TRIM)
    gb.put(1, 2, 4, "minecraft:smoker", {"facing": "east", "lit": "true"})
    gb.put(1, 2, 6, "minecraft:water_cauldron", {"level": "3"})
    gb.put(1, 2, 8, "minecraft:smoker", {"facing": "east", "lit": "false"})
    gb.put(1, 2, 10, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    for z in (3, 5, 7, 9):
        gb.barrel(MID - 10, 1, z)
    gb.put(4, 1, 6, TRIM)
    gb.put(5, 1, 6, TRIM)
    gb.put(4, 2, 6, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})
    gb.put(5, 2, 6, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})
    gb.put(4, 5, 6, LIGHT)
    gb.soldier(5, 1, 9)
    # Oost: de eetzaal met drie tafels.
    side_room(False, 2, 11, 4)
    for z in (3, 6, 9):
        for x in range(MID + 12, MID + 16):
            gb.put(x, 1, z, "minecraft:dark_oak_slab", {"type": "top", "waterlogged": "false"})
            if x % 2 == 0:
                gb.put(x, 1, z - 1, "minecraft:dark_oak_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
                gb.put(x, 1, z + 1, "minecraft:dark_oak_stairs", {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"})
        gb.put(MID + 14, 5, z, LIGHT)
    gb.soldier(MID + 11, 1, 5)
    gb.soldier(MID + 16, 1, 8)
    floor_turret(MID + 7, 2)
    write("mess")


def build_comms():
    """De radiokamer: schermen en bureaus met een antennebasis, en een kaartenkamer met vaandels en een kaartentafel."""
    hall_shell()
    # West: de radiokamer.
    side_room(True, 2, 11, 4)
    for z in range(3, 10):
        for h in (2, 3):
            gb.put(1, h, z, "minecraft:cyan_stained_glass" if (z + h) % 2 == 0 else "minecraft:light_blue_stained_glass")
        gb.put(1, 1, z, BLACK)
        gb.put(1, 4, z, BLACK)
        gb.put(0, 2, z, LIGHT)
        gb.put(0, 3, z, LIGHT)
    for z in (4, 7):
        gb.put(3, 1, z, TRIM)
        gb.put(3, 2, z, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})
        gb.put(4, 1, z, "minecraft:dark_oak_stairs", {"facing": "west", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    gb.put(5, 1, 10, "minecraft:iron_block")
    gb.put(5, 2, 10, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    gb.put(5, 3, 10, "minecraft:iron_bars", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    gb.put(5, 4, 10, "minecraft:iron_bars", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    gb.put(4, 5, 6, LIGHT)
    gb.soldier(6, 1, 4)
    # Oost: de kaartenkamer.
    side_room(False, 2, 11, 4)
    for z in (3, 6, 9):
        gb.banner(33, 3, z, "west")
    gb.put(MID + 13, 1, 6, "minecraft:cartography_table")
    gb.put(MID + 14, 1, 6, "minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    gb.put(MID + 15, 1, 6, "minecraft:cartography_table")
    gb.chest(MID + 11, 1, 10, "east", gb.LOOT_SUPPLIES)
    gb.put(MID + 14, 5, 6, LIGHT)
    gb.soldier(MID + 12, 1, 3)
    floor_turret(MID - 7, 11)
    write("comms")


HALLS = {
    "hall": build_hall_piece,
    "reactor": build_reactor_piece,
    "armory": build_armory,
    "lab": build_lab,
    "cells": build_cells,
    "garage": build_garage,
    "mess": build_mess,
    "comms": build_comms,
}


def build_all():
    build_entrance_piece()
    for name, build in HALLS.items():
        if name in ("hall", "reactor"):
            build()
        else:
            reset(HALL_DEPTH)
            build()
    build_vault_piece()


if __name__ == "__main__":
    build_all()
