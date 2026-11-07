"""
Bouwt de Biebia's Industries Tower KAMER PER KAMER (hoofdstuk 51, 4 november): losse structuurbestanden in
src/main/resources/data/imttech/structure/tower/, die de mod bij het bouwen van de wereld in een willekeurige
volgorde op elkaar stapelt (zie BiebiaTowerStructure en TowerLayout in de Java-code). Zo is elke toren anders.
Gebruik (vanuit de hoofdmap van het project):
    python tools/generate_tower_rooms.py

De stukken:
- lobby.nbt   (41 x 13 x 55): het plein met het voorplein, de lobby, en bovenop het terras naar de eerste
               tussenverdieping. Ligt altijd onderaan.
- <kamer>.nbt (41 x 12 x 41): een tussenverdieping: onderaan haar vloerplaat (0), de kamer (1-10), en bovenop de
               vloerplaat van de volgende (11). Alle tussenverdiepingen hebben dezelfde gevel (inset 6), zodat ze in
               elke volgorde passen. De klassieke: lab, archive, hangar, living en security (de kern: altijd één
               keer); nieuw: gym, greenhouse, cinema, pool, museum en server_farm; en sinds hoofdstuk 54 power
               (de generatorkamer: altijd één keer, zie build_power_room).
- top.nbt     (41 x 44 x 41): de lasergang, de kluis, het penthouse, het dak en de antennes. Ligt altijd bovenaan.

Elke tussenverdieping heeft dezelfde liftkoker (ladder, glas, koperen zuil) op dezelfde plek, en in de koperen zuil
een kabel (stroom en netwerk) over de volle hoogte: de "stijgleiding". De generatorkamer (power, hoofdstuk 54) voedt
die vanuit haar stroomkast; de beveiliging (kern, torentjes), de lobby (torentjes, camera) en de top (lasergang,
kluis) hangen eraan. Zo werkt alles, in welke volgorde de verdiepingen ook liggen.

Het bouwen zelf doet generate_structure.py (de klassieke toren in één stuk): dit script zet zijn getallen per stuk
anders (de vloer op 1, de volgende op 12, de inset op 6) en laat het zijn verdiepingen bouwen. De klassieke toren
blijft bestaan (biebia_tower.nbt) voor de showcase, de tests en wie hem zo wil.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_structure as gs  # noqa: E402

OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "imttech" / "structure" / "tower"

MIDDLE_IN = 6          # de gevel van elke tussenverdieping
MIDDLE_SIZE_Y = 12     # vloerplaat (0), kamer (1-10), vloerplaat erboven (11)
LOBBY_SIZE_Y = 13      # plein (0), lobby (1-10), vloerplaat (11), de reling van het terras (12)
TOP_SIZE_Y = 44        # vloerplaat van de lasergang (0) tot de punt van de antenne (43)
TERRACE = gs.terrace   # de echte terrasfunctie (de tussenverdiepingen zetten hem uit)

MID = gs.MID
X0, Z0, X1, Z1 = gs.bounds(MIDDLE_IN)   # de binnenkant van een tussenverdieping: 6..34


# --------------------------------------------------------------------- Gereedschap

def reset(size_y, size_z=gs.TOWER_Z):
    gs.blocks.clear()
    gs.cables.clear()
    gs.net_cables.clear()
    gs.SIZE_Y = size_y
    gs.SIZE_Z = size_z


def write(name):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    gs.write_nbt(OUT_DIR / f"{name}.nbt", gs.to_nbt())
    print(f"geschreven: tower/{name}.nbt ({len(gs.blocks)} blokken, {len(gs.cables)} kabels)")


def shaft(y0, y1, openings):
    """De liftkoker: een ladder tegen een quartz achterwand, glas opzij, koper op de hoeken; open bij de openingen."""
    for y in range(y0, y1 + 1):
        gs.put(gs.SHAFT_X, y, gs.SHAFT_Z, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
        gs.put(gs.SHAFT_X, y, gs.SHAFT_Z + 1, gs.TRIM)
        gs.put(gs.SHAFT_X - 1, y, gs.SHAFT_Z, gs.GLASS)
        gs.put(gs.SHAFT_X + 1, y, gs.SHAFT_Z, gs.GLASS)
        gs.put(gs.SHAFT_X - 1, y, gs.SHAFT_Z + 1, gs.COPPER)
        gs.put(gs.SHAFT_X + 1, y, gs.SHAFT_Z + 1, gs.COPPER)
        gs.put(gs.SHAFT_X, y, gs.SHAFT_Z - 1, "minecraft:air" if y in openings else gs.GLASS)


def riser(y0, y1):
    """De stijgleiding: stroom (en netwerk) in de koperen zuil van de liftkoker."""
    for y in range(y0, y1 + 1):
        gs.cable(gs.SHAFT_X + 1, y, gs.SHAFT_Z + 1)


RISER = (gs.SHAFT_X + 1, gs.SHAFT_Z + 1)


def wire_cameras_to_riser(ceiling):
    """Elke camera op deze verdieping krijgt een verborgen kabel via het plafond naar de stijgleiding."""
    for (x, y, z), (block, props, nbt) in list(gs.blocks.items()):
        if block == "imttech:security_camera":
            gs.route((x, y, z), (x, ceiling, z), (RISER[0], ceiling, RISER[1]))


def middle_constants(this, nxt):
    """De getallen van generate_structure.py voor één tussenverdieping: de vloer op 1, de volgende op 12, inset 6."""
    setattr(gs, this + "_Y", 1)
    setattr(gs, this + "_IN", MIDDLE_IN)
    setattr(gs, nxt + "_Y", 12)
    setattr(gs, nxt + "_IN", MIDDLE_IN)


def middle(name, build):
    """Een tussenverdieping: eigen vloerplaat, de kamer, planten, de liftkoker, de stijgleiding en de camera's."""
    reset(MIDDLE_SIZE_Y)
    gs.terrace = lambda *args, **kwargs: None   # de gevel springt hier niet in: geen terras
    gs.floor_slab(0, MIDDLE_IN)
    build()
    gs.terrace = TERRACE
    shaft(0, 11, openings=(1, 2))
    riser(0, 11)
    wire_cameras_to_riser(10)
    write(name)


# --------------------------------------------------------------------- De klassieke verdiepingen

def classic(this, nxt, build, furnish=None, plants=True):
    def go():
        middle_constants(this, nxt)
        gs.LOBBY_IN = MIDDLE_IN   # het lab zet een zonnepaneel "op het terras": hier gewoon bij het raam
        gs.POWER_Z = MIDDLE_IN + 4
        gs.POWER_X = gs.SIZE_X - 1 - MIDDLE_IN - 3
        build()
        if furnish:
            furnish()
        if plants:
            gs.window_plants(1, MIDDLE_IN)
    return go


def marker(x, y, z, name):
    """Een markering (een structure block in de stand DATA): de mod zet er bij het bouwen iets neer (zie TowerPiece)."""
    gs.put(x, y, z, "minecraft:structure_block", {"mode": "data"},
           {"id": "minecraft:structure_block", "mode": "DATA", "metadata": name})


def soldier(x, y, z, route=None):
    """Hier komt bij het bouwen van de wereld een Raven-soldaat (zoals in de bunker); met een route loopt hij die (hoofdstuk 60)."""
    marker(x, y, z, route_text("raven_soldier", x, z, route))


def guard(x, y, z, route=None):
    """Hoofdstuk 54: een Raven-soldaat met een geconfigureerde keycard van de toren (groep "staff") op zak: die valt."""
    marker(x, y, z, route_text("raven_guard", x, z, route))


def route_text(kind, x, z, route=None):
    """Hoofdstuk 60: de route als verschuivingen achter het woord ("raven_soldier;2,0,0;2,0,5"), zie RavenRoutes."""
    return kind + "".join(f";{px - x},0,{pz - z}" for (px, pz) in (route or ()))


def security_floor():
    """
    De beveiligingsverdieping. Sinds hoofdstuk 54 zit de krachtcentrale niet meer hier maar in de generatorkamer
    (build_power_room): de stroom en het netwerk komen via de stijgleiding de vloer in, naar de kern en de vier
    torentjes. (Vroeger hing het torentje in de noordoosthoek nergens aan: dat was "het power probleem".)
    """
    classic("SECURITY", "LASERS", gs.build_security, plants=False)()
    floor = gs.SECURITY_Y
    guard_power = (gs.POWER_X - 4, floor + 1, MID - 3)
    guard_lift = (MID + 4, floor + 1, MID + 6)
    gs.security_turret(*guard_power)
    gs.security_turret(*guard_lift)
    # Van de stijgleiding de vloer in naar het torentje bij de lift, dan naar de lijn langs de noordgevel (de twee
    # hoektorentjes), en van die lijn naar de kern; het vierde torentje aan de lijn naar het noorden.
    gs.route((RISER[0], floor, RISER[1]), (guard_lift[0], floor, RISER[1]), (guard_lift[0], floor, guard_lift[2]))
    gs.route((guard_lift[0], floor, guard_lift[2]), (guard_lift[0], floor, Z0 + 2))
    gs.route((X0 + 2, floor, Z0 + 2), (X1 - 2, floor, Z0 + 2))
    gs.route((X0 + 2, floor, Z0 + 2), (X0 + 2, floor, MID))
    gs.route((guard_power[0], floor, guard_power[2]), (guard_lift[0], floor, guard_power[2]))
    # Hoofdstuk 54 zette hier een soldaat op wacht; sinds hoofdstuk 60 (jouw antwoord 6) bewaken Biebia's robots deze
    # verdieping, vanaf de twee Robot Docks voor de schermenwand (zie gs.build_security en TowerRobots).


# --------------------------------------------------------------------- De generatorkamer (hoofdstuk 54)

# De kluis van de generatoren: obsidiaan van (13, 8) tot (27, 20), binnenin de vier cellen, de kast en het rek.
VAULT_X0, VAULT_Z0, VAULT_X1, VAULT_Z1 = MID - 7, Z0 + 2, MID + 7, Z0 + 14
GATE_Z = VAULT_Z1                      # de zuidmuur, met de doorgang en de rayshield erin
CELLS = [(MID - 4, Z0 + 5), (MID - 4, Z0 + 10), (MID + 4, Z0 + 5), (MID + 4, Z0 + 10)]
HATCH_Z = VAULT_Z1 - 1                 # de zwakke plek: in de oostmuur, net binnen de zuidmuur


def generator_cell(cx, cz):
    """
    Een reactorcel: een glazen hokje van 5 x 5 met binnenin een warmtegenerator in een ring van lava (lava is hitte,
    hitte is stroom; zie HeatGeneratorBlockEntity). De lava zit achter glas, dus je kunt erlangs lopen. De kabel
    zit in de vloerplaat onder de generator.
    """
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            edge = x in (cx - 2, cx + 2) or z in (cz - 2, cz + 2)
            if edge:
                gs.put(x, 1, z, gs.GLASS)
                gs.put(x, 2, z, gs.GLASS)
            else:
                gs.put(x, 1, z, "minecraft:lava", {"level": "0"})
                gs.put(x, 2, z, "minecraft:air")
            gs.put(x, 3, z, gs.GLASS)
    gs.put(cx, 1, cz, "imttech:heat_generator", {"facing": "south"})
    gs.cable(cx, 0, cz)


def build_power_room():
    """
    De generatorkamer (hoofdstuk 54, jouw notitie: "zorg dat er altijd 1 generator room sowieso is en die geeft power
    aan alles, deze room is beveiligd met rayshields"). Een donkere zaal met in het noorden de kluis van de
    generatoren: obsidiaan, met als enige doorgang een poort van rayshields (zes laserzenders, drie rijen hoog). In
    de kluis vier reactorcellen, de stroomkast (de hoofdschakelaar van de hele toren: hij voedt de stijgleiding) en
    een echte serverkast met CPU, RAM, SSD en fan: daarop draait het diagram "Generator room" van Biebia's kern.

    Erin komen kan op drie manieren:
    - de kern hacken: dan is de poort van jou (de app Devices, of het diagram);
    - de keycard van een bewaker (de soldaat voor de poort, of die in de lobby): die zit in de groep "staff", en het
      diagram zet de poort acht seconden uit;
    - de zwakke plek: in de oostmuur zit een luik van gebarsten deepslate-tegels (bordje "VENT 3B"), dat hak je zo
      weg, waar je bij obsidiaan minuten bezig bent. Of je sloopt elke zender van de poort.
    Zonder stroomkast (of met de kabels doorgeknipt) valt de hele toren uit: de torentjes, de lasergang, de kluis.
    """
    room_shell(dark=True)
    gs.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, gs.DARK_FLOOR)
    # De kluis: obsidiaan rondom (y 1-4) met een dak (y 5), en binnenin lucht.
    gs.fill(VAULT_X0, 1, VAULT_Z0, VAULT_X1, 5, VAULT_Z1, "minecraft:obsidian")
    gs.fill(VAULT_X0 + 1, 1, VAULT_Z0 + 1, VAULT_X1 - 1, 4, VAULT_Z1 - 1, "minecraft:air")
    for x in range(VAULT_X0 + 2, VAULT_X1 - 1, 3):
        gs.put(x, 5, MID - 1, gs.LIGHT)
    for (cx, cz) in CELLS:
        generator_cell(cx, cz)
    # De stroomkast en de serverkast tegen de noordmuur, met hun kabels in de vloerplaat.
    gs.put(MID + 1, 1, VAULT_Z0 + 1, "imttech:power_cabinet", {"facing": "south"})
    gs.put(MID - 1, 1, VAULT_Z0 + 1, "imttech:server_rack", {"facing": "south"}, {
        "id": "imttech:server_rack", "Status": "ONLINE", "Items": [
            {"Slot": gs.Int(0), "id": "imttech:server_cpu", "count": gs.Int(1)},
            {"Slot": gs.Int(1), "id": "imttech:server_ram", "count": gs.Int(1)},
            {"Slot": gs.Int(2), "id": "imttech:server_ssd", "count": gs.Int(1)},
            {"Slot": gs.Int(3), "id": "imttech:server_fan", "count": gs.Int(1)},
        ]})
    gs.route((MID - 4, 0, VAULT_Z0 + 1), (MID + 4, 0, VAULT_Z0 + 1))
    for (cx, cz) in CELLS:
        gs.route((cx, 0, cz), (cx, 0, VAULT_Z0 + 1))
    # De poort: een doorgang van drie breed en drie hoog in de zuidmuur, met aan weerskanten drie zenders die elkaar
    # aankijken (rood), en hun kabels in de muur ernaast, omlaag de vloerplaat in.
    for y in (1, 2, 3):
        for x in range(MID - 1, MID + 2):
            gs.put(x, y, GATE_Z, "minecraft:air")
        gs.put(MID - 2, y, GATE_Z, "imttech:laser_emitter",
               {"facing": "east", "powered": "false", "color": "red", "switched_on": "true"})
        gs.put(MID + 2, y, GATE_Z, "imttech:laser_emitter",
               {"facing": "west", "powered": "false", "color": "red", "switched_on": "true"})
    for side in (MID - 3, MID + 3):
        gs.route((side, 3, GATE_Z), (side, 0, GATE_Z), (side, 0, VAULT_Z0 + 1))
    # De keycardlezer "Power door" rechts van de poort (aan de buitenkant), aan de kabel in de muur erachter.
    gs.put(MID + 3, 2, GATE_Z + 1, "imttech:keycard_reader", {"facing": "south", "light": "off"},
           {"id": "imttech:keycard_reader", "Name": "Power door"})
    # Van de kast naar de stijgleiding: zo krijgt de hele toren zijn stroom en netwerk.
    gs.route((MID + 1, 0, VAULT_Z0 + 1), (MID + 1, 0, RISER[1]))
    # De zwakke plek: twee gebarsten tegels in de oostmuur, met het bordje ernaast.
    gs.put(VAULT_X1, 1, HATCH_Z, "minecraft:cracked_deepslate_tiles")
    gs.put(VAULT_X1, 2, HATCH_Z, "minecraft:cracked_deepslate_tiles")
    gs.sign(VAULT_X1 + 1, 3, HATCH_Z, "east", "VENT 3B - DO NOT BLOCK")
    # Bordjes: wat je ziet, legt zichzelf uit (jouw wens: uitleg door leuke voorbeelden).
    gs.sign(MID, 4, GATE_Z + 1, "south", "GENERATOR ROOM - STAFF CARD")
    gs.sign(MID + 1, 3, VAULT_Z0 + 1, "south", "MAIN CABINET - ALL FLOORS")
    gs.sign(MID - 1, 3, VAULT_Z0 + 1, "south", "LOGIC SERVER - RUNS THE GATE")
    gs.sign(MID - 4, 2, Z0 + 8, "south", "GEN 1 - LAVA = HEAT = POWER")
    gs.sign(MID + 4, 2, Z0 + 8, "south", "GEN 2 - DO NOT OPEN")
    gs.chest(VAULT_X0 + 1, 1, VAULT_Z1 - 1, "east")
    # Buiten de kluis: de controledesk met schermen, een bewaker voor de poort, en een camera die de poort bekijkt.
    gs.desk(MID - 4, 1, Z1 - 9, 9, chairs=(2, 6))
    gs.screen(MID - 4, 2, Z1 - 8, 9, 3)
    guard(MID + 3, 1, GATE_Z + 4)
    gs.security_camera(X0 + 1, 9, GATE_Z + 3, "ceiling", "east", "Generator room", pan=-30)
    for z in range(Z0 + 2, Z1 - 2, 4):
        gs.put(X0 + 1, 3, z, "minecraft:end_rod", {"facing": "east"})
        gs.put(X1 - 1, 3, z, "minecraft:end_rod", {"facing": "west"})
    gs.chest(X1 - 2, 1, Z1 - 2, "north")


# --------------------------------------------------------------------- Nieuwe verdiepingen

def room_shell(dark=False):
    """De gevel en de vloerplaat erboven van een nieuwe tussenverdieping."""
    gs.curtain_wall(1, 10, MIDDLE_IN, gs.DARK_GLASS if dark else gs.GLASS)
    gs.floor_slab(11, MIDDLE_IN)


def mini_tree(x, z, height=3, leaves="minecraft:azalea_leaves"):
    """Een boompje op de vloer (y = 1): een stam met een kruin."""
    gs.put(x, 1, z, "minecraft:moss_block")
    for h in range(2, height + 2):
        gs.put(x, h, z, "minecraft:oak_log", {"axis": "y"})
    top = height + 1
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (top - 1, top, top + 1):
                if abs(dx) + abs(dz) + max(0, dy - top) * 2 > 3 or (dx == 0 and dz == 0 and dy <= top):
                    continue
                gs.put(x + dx, dy, z + dz, leaves, {"distance": "1", "persistent": "true", "waterlogged": "false"})


def build_gym():
    """De sportzaal: een boksring in het midden, een looppiste eromheen, halters, bokszakken en een spiegelwand."""
    room_shell()
    y = 1
    # De looppiste: een ring van blauwe en witte strepen in de vloer.
    for x in range(X0 + 2, X1 - 1):
        for z in (Z0 + 2, Z1 - 2):
            gs.put(x, 0, z, gs.STRIPE if x % 2 == 0 else gs.FLOOR)
    for z in range(Z0 + 2, Z1 - 1):
        for x in (X0 + 2, X1 - 2):
            gs.put(x, 0, z, gs.STRIPE if z % 2 == 0 else gs.FLOOR)
    # De boksring: een verhoging van witte wol, touwen van hekjes, rode en blauwe hoeken.
    gs.fill(MID - 4, y, MID - 4, MID + 4, y, MID + 4, "minecraft:white_wool")
    for (x, z, colour) in ((MID - 4, MID - 4, "red"), (MID + 4, MID + 4, "red"), (MID - 4, MID + 4, "blue"), (MID + 4, MID - 4, "blue")):
        gs.fill(x, y + 1, z, x, y + 3, z, "minecraft:%s_wool" % colour)
    for i in range(-3, 4):
        for (x, z) in ((MID + i, MID - 4), (MID + i, MID + 4), (MID - 4, MID + i), (MID + 4, MID + i)):
            gs.put(x, y + 2, z, "minecraft:white_carpet")
            gs.put(x, y + 1, z, "minecraft:oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    gs.hanging_lamp(MID, 10, MID, 3)
    # Halters langs de westmuur: ijzeren blokken aan een ketting, met een bankje ervoor.
    for z in range(Z0 + 5, Z1 - 4, 4):
        gs.put(X0 + 2, y, z, "minecraft:iron_block")
        gs.put(X0 + 2, y + 1, z, "minecraft:chain", {"axis": "x", "waterlogged": "false"})
        gs.put(X0 + 3, y + 1, z, "minecraft:iron_block")
        gs.put(X0 + 4, y + 1, z, "minecraft:chain", {"axis": "x", "waterlogged": "false"})
        gs.put(X0 + 5, y, z, "minecraft:iron_block")
        gs.bottom_slab(X0 + 4, y, z, "minecraft:dark_oak_slab")
    # Bokszakken aan het plafond langs de oostmuur.
    for z in range(Z0 + 4, Z1 - 3, 5):
        for h in (10, 9):
            gs.put(X1 - 3, h, z, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
        gs.put(X1 - 3, 8, z, "minecraft:black_wool")
        gs.put(X1 - 3, 7, z, "minecraft:black_wool")
        gs.put(X1 - 3, 6, z, "minecraft:red_wool")
    # Een spiegelwand (tinted glass) tegen de zuidgevel, en een rek met handdoeken.
    for x in range(MID - 6, MID + 7):
        for h in range(y, y + 4):
            gs.put(x, h, Z1 - 1, "minecraft:tinted_glass")
    for x in range(MID - 3, MID + 4, 2):
        gs.put(x, y, Z1 - 2, "minecraft:white_carpet")
    # Een waterkoeler en een bankje bij de ingang van de lift.
    gs.put(X1 - 2, y, Z1 - 3, "minecraft:water_cauldron", {"level": "3"})
    for x in range(MID - 2, MID + 3):
        gs.stairs(x, y, Z0 + 1, "north", "minecraft:oak_stairs")
    gs.chest(X0 + 2, y, Z1 - 3, "north")
    gs.window_plants(1, MIDDLE_IN)


def build_greenhouse():
    """De kas: gras en bloemen, boompjes, een vijver, bijenkasten en een kweekhoek met composters."""
    room_shell()
    y = 1
    # Gras met bloemen in vier perken, paden van quartz ertussen.
    for (ax, az, bx, bz) in ((X0 + 2, Z0 + 2, MID - 2, MID - 2), (MID + 2, Z0 + 2, X1 - 2, MID - 2),
                             (X0 + 2, MID + 2, MID - 2, Z1 - 2), (MID + 2, MID + 2, X1 - 2, Z1 - 2)):
        gs.fill(ax, 0, az, bx, 0, bz, "minecraft:grass_block", {"snowy": "false"})
        flowers = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:short_grass"]
        for x in range(ax, bx + 1):
            for z in range(az, bz + 1):
                if (x * 7 + z * 3) % 5 == 0:
                    gs.put(x, y, z, flowers[(x + z) % len(flowers)])
    for x in range(X0 + 1, X1):
        gs.put(x, 0, MID - 1, gs.TRIM)
        gs.put(x, 0, MID + 1, gs.TRIM)
    for z in range(Z0 + 1, Z1):
        gs.put(MID - 1, 0, z, gs.TRIM)
        gs.put(MID + 1, 0, z, gs.TRIM)
    # Boompjes in de hoeken van de perken.
    for (x, z) in ((X0 + 5, Z0 + 5), (X1 - 5, Z0 + 5), (X0 + 5, Z1 - 5), (X1 - 5, Z1 - 5)):
        mini_tree(x, z, 3, "minecraft:flowering_azalea_leaves" if (x + z) % 2 else "minecraft:azalea_leaves")
    # De vijver in het midden: water op donkere prismarine, met een lelie en een lampje op de bodem.
    gs.fill(MID - 2, 0, MID - 2, MID + 2, 0, MID + 2, "minecraft:dark_prismarine")
    gs.put(MID, 0, MID, gs.LIGHT)
    gs.fill(MID - 2, y, MID - 2, MID + 2, y, MID + 2, "minecraft:water", {"level": "0"})
    gs.put(MID, y + 1, MID, "minecraft:lily_pad")
    # Bijenkasten op palen langs de noordgevel.
    for x in range(X0 + 4, X1 - 3, 6):
        gs.put(x, y, Z0 + 1, "minecraft:oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
        gs.put(x, y + 1, Z0 + 1, "minecraft:bee_nest", {"facing": "south", "honey_level": "3"})
    # De kweekhoek: composters, een watertank en zakken grond (tonnen) langs de zuidgevel.
    for x in range(X0 + 3, X0 + 9, 2):
        gs.put(x, y, Z1 - 1, "minecraft:composter", {"level": "4"})
    gs.water_tank(X1 - 4, y, Z1 - 3)
    for x in range(MID - 2, MID + 3):
        gs.put(x, y, Z1 - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    # Lantaarns aan het plafond, en kettingen met hangplanten.
    for x in (MID - 6, MID + 6):
        for z in (MID - 6, MID + 6):
            gs.hanging_lamp(x, 10, z, 1)
    gs.chest(X0 + 2, y, Z1 - 2, "north")


def build_cinema():
    """De bioscoop: een groot scherm op de noordgevel, oplopende rijen stoelen, een projector en een snackbar."""
    room_shell(dark=True)
    y = 1
    gs.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, gs.DARK_FLOOR)
    gs.rug(X0 + 1, Z0 + 1, X1 - 1, Z1 - 1, y, "red")
    # Het scherm: zwart kader met lichtblauw glas, verlicht vanuit de gevel.
    gs.screen(MID - 9, y + 1, Z0 + 1, 19, 8, backlight_z=Z0)
    # Drie rijen stoelen op treden, elke rij een blok hoger (de treden van donker beton).
    for row in range(3):
        z = MID + 2 + row * 3
        level = y + row
        if row > 0:
            gs.fill(X0 + 3, y, z - 1, X1 - 3, level - 1, z + 1, "minecraft:black_concrete")
            gs.rug(X0 + 3, z - 1, X1 - 3, z + 1, level, "red")
        for x in range(X0 + 4, X1 - 3):
            if abs(x - MID) == 1:
                continue   # het gangpad in het midden
            gs.stairs(x, level, z, "south", "minecraft:dark_oak_stairs")
    # De projector achterin, op een zuiltje, met een lichtstraal (end rods) naar het scherm.
    gs.fill(MID, y, Z1 - 3, MID, y + 2, Z1 - 3, "minecraft:polished_blackstone")
    gs.put(MID, y + 3, Z1 - 3, "minecraft:dispenser", {"facing": "north", "triggered": "false"})
    for z in range(Z1 - 5, Z1 - 11, -2):
        gs.put(MID, y + 4, z, "minecraft:end_rod", {"facing": "north"})
    # De snackbar in de zuidwesthoek: een toonbank met popcorn (hooi) en een frisdrankmachine.
    for x in range(X0 + 2, X0 + 8):
        gs.put(x, y, Z1 - 2, gs.TRIM)
        gs.bottom_slab(x, y + 1, Z1 - 2)
    gs.put(X0 + 3, y + 2, Z1 - 2, "minecraft:hay_block")
    gs.put(X0 + 5, y + 2, Z1 - 2, "minecraft:water_cauldron", {"level": "3"})
    gs.put(X0 + 7, y + 2, Z1 - 2, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    for x in (X0 + 2, X0 + 4, X0 + 6):
        gs.put(x, y, Z1 - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    # Zachte lampjes in de wanden.
    for z in range(Z0 + 4, Z1 - 3, 6):
        gs.put(X0 + 1, y + 3, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
        gs.put(X1 - 1, y + 3, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    gs.chest(X1 - 2, y, Z1 - 2, "north")


def build_pool():
    """Het zwembad: een verzonken bad met lampen op de bodem, een duikplank, ligstoelen, palmen en een barretje."""
    room_shell()
    y = 1
    # Het dek: lichte tegels, met een rand van quartz om het bad.
    gs.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "minecraft:light_gray_concrete")
    px0, pz0, px1, pz1 = MID - 8, MID - 4, MID + 8, MID + 4
    gs.fill(px0 - 1, 0, pz0 - 1, px1 + 1, 0, pz1 + 1, gs.TRIM)
    # Het bad zelf: de bodem (de vloerplaat) van prismarine met lampen, en water tot aan het dek.
    gs.fill(px0, 0, pz0, px1, 0, pz1, "minecraft:prismarine_bricks")
    for x in range(px0 + 2, px1, 4):
        gs.put(x, 0, MID, gs.LIGHT)
    gs.fill(px0, y, pz0, px1, y, pz1, "minecraft:water", {"level": "0"})
    # Twee banen: lijnen van lichtblauw beton op de bodem (zie je door het water).
    for x in range(px0, px1 + 1):
        gs.put(x, 0, MID - 2, gs.STRIPE)
        gs.put(x, 0, MID + 2, gs.STRIPE)
    # De duikplank aan de oostkant.
    gs.put(px1 + 2, y, MID, "minecraft:oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    gs.put(px1 + 2, y + 1, MID, "minecraft:oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    for x in range(px1 - 1, px1 + 3):
        gs.put(x, y + 2, MID, "minecraft:bamboo_slab", {"type": "bottom", "waterlogged": "false"})
    # Ligstoelen langs de noord- en zuidkant, met een handdoek en een tafeltje.
    for x in range(px0, px1 + 1, 4):
        for (z, facing) in ((pz0 - 3, "north"), (pz1 + 3, "south")):
            gs.stairs(x, y, z, facing, "minecraft:bamboo_stairs")
            gs.put(x + 1, y, z, "minecraft:white_carpet" if x % 8 == 0 else "minecraft:light_blue_carpet")
            gs.put(x + 2, y, z, "minecraft:bamboo_slab", {"type": "top", "waterlogged": "false"})
    # Palmen in de hoeken (jungle), en een barretje in de zuidwesthoek.
    for (x, z) in ((X0 + 3, Z0 + 3), (X1 - 3, Z0 + 3), (X1 - 3, Z1 - 3)):
        mini_tree(x, z, 4, "minecraft:jungle_leaves")
    for x in range(X0 + 2, X0 + 7):
        gs.put(x, y, Z1 - 3, "minecraft:bamboo_planks")
        gs.put(x, y + 1, Z1 - 3, "minecraft:bamboo_slab", {"type": "bottom", "waterlogged": "false"})
        gs.put(x, y, Z1 - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    gs.put(X0 + 4, y + 2, Z1 - 3, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    # Douches tegen de noordgevel: een paar waterkolommen achter glas.
    for x in (X1 - 5, X1 - 7):
        gs.put(x, y + 3, Z0 + 1, "minecraft:water_cauldron", {"level": "3"})
        gs.put(x, y, Z0 + 1, "minecraft:iron_trapdoor", {"facing": "south", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})
    gs.chest(X0 + 2, y, Z0 + 2, "south")
    gs.window_plants(1, MIDDLE_IN)


def build_museum():
    """Het museum: vitrines met prototypes, twee harnassen, een wereldbol, koorden en spots."""
    room_shell()
    y = 1
    gs.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, gs.FLOOR)
    gs.rug(MID - 1, Z0 + 1, MID + 1, Z1 - 1, y, "red")
    # Twee harnassen in vitrines links en rechts van de ingang.
    gs.suit_capsule(MID - 7, y, Z1 - 5, "TECH SUIT V2")
    gs.suit_capsule(MID + 7, y, Z1 - 5, "TECH SUIT V4")
    # Vitrines langs de west- en oostgevel: een sokkel met een voorwerp onder glas, een spot erboven.
    pieces = ["minecraft:heavy_core", "minecraft:conduit", "minecraft:anvil", "minecraft:lodestone",
              "minecraft:lightning_rod", "minecraft:beacon"]
    for i, z in enumerate(range(Z0 + 3, Z1 - 2, 5)):
        for (x, side) in ((X0 + 2, 0), (X1 - 2, 1)):
            item = pieces[(i * 2 + side) % len(pieces)]
            gs.put(x, y, z, gs.TRIM)
            props = {"waterlogged": "false"} if item in ("minecraft:heavy_core", "minecraft:conduit") else None
            if item == "minecraft:lightning_rod":
                props = {"facing": "up", "powered": "false", "waterlogged": "false"}
            if item == "minecraft:anvil":
                props = {"facing": "south"}
            gs.put(x, y + 1, z, item, props)
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx or dz:
                        gs.put(x + dx, y + 1, z + dz, "minecraft:glass")
            gs.put(x, y + 2, z, "minecraft:glass")
            gs.put(x, y + 4, z, "minecraft:end_rod", {"facing": "down"})
            gs.sign(x, y, z + 1, "south", "Prototype %d" % (i * 2 + side + 1))
    # De wereldbol in het midden, met een koord van hekjes en rood kleed eromheen.
    gs.globe(MID, y, MID)
    for (x, z) in ((MID - 3, MID - 3), (MID + 3, MID - 3), (MID - 3, MID + 3), (MID + 3, MID + 3)):
        gs.put(x, y, z, "minecraft:polished_blackstone_wall", {"up": "true", "north": "none", "south": "none", "east": "none", "west": "none", "waterlogged": "false"})
    for i in range(-2, 3):
        gs.put(MID + i, y, MID - 3, "minecraft:red_carpet")
        gs.put(MID + i, y, MID + 3, "minecraft:red_carpet")
        gs.put(MID - 3, y, MID + i, "minecraft:red_carpet")
        gs.put(MID + 3, y, MID + i, "minecraft:red_carpet")
    # De receptie bij de lift, en een bankje.
    gs.desk(MID - 3, y, Z0 + 2, 7, chairs=(3,))
    for x in (MID - 5, MID + 5):
        gs.stairs(x, y, Z0 + 2, "north", "minecraft:dark_oak_stairs")
    gs.chest(X0 + 2, y, Z1 - 2, "north")
    gs.chest(X1 - 2, y, Z1 - 2, "north")
    gs.window_plants(1, MIDDLE_IN)


def build_server_farm():
    """De serverzaal: rijen serverkasten op een verhoogde vloer, een koude gang met blauw licht, en een controledesk."""
    room_shell(dark=True)
    y = 1
    gs.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, gs.DARK_FLOOR)
    # Vier rijen kasten, twee aan twee met een koude gang ertussen (blauw licht in de vloer).
    for (x, facing) in ((X0 + 4, "east"), (X0 + 8, "west"), (X1 - 8, "east"), (X1 - 4, "west")):
        for z in range(Z0 + 3, Z1 - 2):
            if z == MID:
                continue
            gs.server_rack(x, y, z, 3, facing)
    for x in (X0 + 6, X1 - 6):
        for z in range(Z0 + 3, Z1 - 2, 2):
            gs.put(x, 0, z, "minecraft:sea_lantern")
    # Kabelgoten aan het plafond (kettingen), en een rij ventilatoren (ijzeren roosters) in de noordgevel.
    for x in (X0 + 6, X1 - 6):
        for z in range(Z0 + 3, Z1 - 2):
            gs.put(x, 10, z, "minecraft:chain", {"axis": "z", "waterlogged": "false"})
    for x in range(X0 + 3, X1 - 2, 3):
        gs.put(x, y + 3, Z0 + 1, "minecraft:iron_trapdoor", {"facing": "south", "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
    # De controledesk in het midden achterin, met schermen.
    gs.desk(MID - 4, y, Z1 - 3, 9, chairs=(2, 6))
    gs.screen(MID - 4, y + 2, Z1 - 4, 9, 3)
    # Een echte serverkast van de mod ertussen (zonder onderdelen), en een stroomkast.
    gs.put(MID, y, Z0 + 2, "imttech:server_rack", {"facing": "south"})
    gs.put(MID - 2, y, Z0 + 2, "imttech:power_cabinet", {"facing": "south"})
    gs.chest(X0 + 2, y, Z1 - 2, "north")
    gs.chest(X1 - 2, y, Z1 - 2, "north")


# --------------------------------------------------------------------- Lobby en top

def build_lobby_piece():
    reset(LOBBY_SIZE_Y, gs.TOWER_Z + gs.FORECOURT)
    gs.LOBBY_Y, gs.LOBBY_IN = 1, 2
    gs.LAB_Y, gs.LAB_IN = 12, MIDDLE_IN
    gs.build_plaza()
    gs.build_lobby()
    gs.build_canopy()
    gs.furnish_lobby()
    gs.window_plants(1, 2, skip={(MID + dx, 37) for dx in range(-4, 5)})
    # Een bewaker met een keycard van de toren bij de receptie (hoofdstuk 54).
    guard(MID - 8, 1, MID + 2)
    # Hoofdstuk 60: net binnen de ingang van de lobby komt de versterking binnen (zie RavenSites).
    marker(MID, 1, gs.bounds(2)[3] - 3, "raven_entry")
    # Het terras naar de eerste tussenverdieping, met zijn reling (de rij erboven).
    TERRACE(11, 2, MIDDLE_IN)
    shaft(1, 12, openings=(1, 2))
    riser(0, 12)
    # De twee torentjes bij de lift, en de camera op de logowand, vanaf de stijgleiding (in het plein).
    gs.route((RISER[0], 0, RISER[1]), (RISER[0], 0, MID + 6), (MID - 5, 0, MID + 6))
    gs.route((RISER[0], 0, MID + 6), (MID + 5, 0, MID + 6))
    gs.route((MID + 5, 0, MID + 6), (MID + 5, 0, MID - 7), (MID + 5, 1 + 6, MID - 7))
    write("lobby")


def build_top_piece():
    reset(TOP_SIZE_Y)
    gs.SECURITY_IN = MIDDLE_IN
    gs.LASERS_Y, gs.LASERS_IN = 1, 9
    gs.VAULT_Y, gs.VAULT_IN = 11, 9
    gs.PENTHOUSE_Y, gs.PENTHOUSE_IN = 22, 10
    gs.ROOF_Y, gs.ROOF_IN = 33, 10
    gs.CROWN_Y = 38
    gs.POWER_Z = MIDDLE_IN + 4
    gs.build_laser_gauntlet()
    gs.build_vault()
    gs.build_penthouse()
    gs.build_roof()
    gs.furnish_penthouse()
    gs.window_plants(gs.PENTHOUSE_Y + 1, gs.PENTHOUSE_IN)
    # De ladder van de kluis door het penthouse naar het dak (zoals build_routes).
    for y in range(gs.VAULT_Y + 1, gs.ROOF_Y + 1):
        gs.put(gs.EAST_LADDER_X, y, gs.EAST_LADDER_Z, "minecraft:ladder", {"facing": "west", "waterlogged": "false"})
        if y < gs.PENTHOUSE_Y - 1:
            gs.put(gs.EAST_LADDER_X + 1, y, gs.EAST_LADDER_Z, "minecraft:polished_deepslate")
    # De liftkoker komt hier uit: de laatste sporten.
    shaft(0, 0, openings=())
    gs.put(gs.SHAFT_X, 1, gs.SHAFT_Z, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
    riser(0, 0)
    # De lijnen onder de zenders van de lasergang hangen aan de stijgleiding.
    gs.route((MID - 2, 0, gs.POWER_Z), (RISER[0], 0, RISER[1]))
    gs.route((MID + 2, 0, gs.POWER_Z), (RISER[0], 0, RISER[1]))
    # De vier kluistorentjes aan de lasers (zoals wire_security), en de camera's (zoals wire_cameras).
    vx0, vz0, vx1, vz1 = gs.bounds(gs.VAULT_IN)
    lx0, lz0, lx1, lz1 = gs.bounds(gs.LASERS_IN)
    plate_z = MID + 3
    v = gs.VAULT_Y
    gs.route((MID + 2, v, plate_z), (vx1 - 2, v, plate_z), (vx1 - 2, v, vz0 + 2))
    gs.route((vx1 - 2, v, plate_z), (vx1 - 2, v, vz1 - 2))
    gs.route((MID - 2, v, plate_z), (vx0 + 2, v, plate_z), (vx0 + 2, v, vz0 + 2))
    gs.route((vx0 + 2, v, plate_z), (vx0 + 2, v, vz1 - 2))
    gs.route((MID + 3, v - 1, plate_z), (MID + 3, v - 1, lz1 - 2), (MID, v - 1, lz1 - 2))
    gs.route((vx0 + 2, v, vz0 + 2), (vx0 + 2, v, vz0 + 1), (MID, v, vz0 + 1), (MID, v, vz0), (MID, v + 5, vz0))
    write("top")


# --------------------------------------------------------------------- Alles

MIDDLE_ROOMS = {
    "lab": classic("LAB", "ARCHIVE", gs.build_lab, gs.furnish_lab),
    "archive": classic("ARCHIVE", "HANGAR", gs.build_archive, gs.furnish_archive),
    "hangar": classic("HANGAR", "LIVING", gs.build_hangar, plants=False),
    "living": classic("LIVING", "SECURITY", gs.build_living, gs.furnish_living),
    "security": security_floor,
    "power": build_power_room,
    "gym": build_gym,
    "greenhouse": build_greenhouse,
    "cinema": build_cinema,
    "pool": build_pool,
    "museum": build_museum,
    "server_farm": build_server_farm,
}


def build_all():
    build_lobby_piece()
    for name, build in MIDDLE_ROOMS.items():
        middle(name, build)
    build_top_piece()


if __name__ == "__main__":
    build_all()
