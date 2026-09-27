# Stark Tech — E.D.I.T.H. voor Minecraft 1.21.1

Een mod die je een E.D.I.T.H.-bril geeft: een HUD, drones, een satelliet, schilden, illusies,
redstone op afstand, en zelfs muziek. Alles werkt met **Minecraft 1.21.1** en
**NeoForge 21.1.251**.

---

## 1. Installeren

1. Bouw de mod: `gradlew build`
   De jar komt in `build/libs/starktech-2.0.0.jar`.
2. Zet die jar in de map `mods` van je Minecraft-installatie (met NeoForge 21.1.251 voor 1.21.1).
3. Wil je alleen even testen zonder te installeren? Gebruik `gradlew runClient`.

Alles testen dat op de server gebeurt: `gradlew runGameTestServer` (74 automatische tests).

---

## 2. Beginnen in het spel

- **De bril kun je niet maken.** Er bestaat geen recept voor: de enige E.D.I.T.H.-bril in je
  wereld ligt in de kluis van een Stark Tower. Zoek er dus eerst een (zie hieronder).
- Craft een **Stark-chip** (koper, goud, redstone, diamant) — die heb je nodig voor bijna al het
  andere: het laadstation, de relais, de laserzenders en de upgrades.
- Zet de bril op je hoofd (helm-slot). De eerste die hem opzet is de eigenaar; anderen krijgen
  "toegang geweigerd".
- Craft het **laadstation** om de bril op te laden. Redstone kan erin, maar geeft weinig
  (50 per stuk): dat is je noodvoorraad. Voor echte stroom zet je een **zonnepaneel** of een
  **warmtegenerator** neer en verbind je die met **Stark-kabels** (zie "Stroom en kabels").
  In het station bouw je ook upgrades in (batterij, scanner, drones).
- Zoek een **Stark Tower** in je wereld: een wolkenkrabber van 41 x 110 blokken met een
  verlicht STARK-logo en een blauwe lichtstraal uit de kroon (die zie je van ver). Binnen: een
  lobby met receptie, een lab, een archief, een hangar, een woonverdieping, de beveiliging,
  een penthouse en een landingsdek op het dak. Naar boven ga je met de glazen liftkoker
  (een ladder) in de lobby. In de gewone kisten vind je materiaal en upgrades — maar
  **geen bril**. De bril ligt in de **kluis** bovenin, en die geeft zich niet zomaar gewonnen:
  eerst door de lasergang (rayshields waar je niet doorheen komt), dan de omweg via drie
  deuren met verstopte schakelaars, en in de kluis zelf staan vier beveiligingstorentjes.
  (De lasers draaien op stroom. Wie goed kijkt, vindt misschien een andere manier...)
  En kijk uit voor de drukplaat voor de kluiskist: stap je erop, dan schieten er vier
  laserzuilen uit de vloer omhoog en sta je in een kooi.
- De toren wordt **bewaakt**. Op de beveiligingsverdieping staat de **Stark Security Core**
  (een serverkast met een rood lampje, in de rij serverkasten). Die beschermt de hele toren:
  - **beveiligingstorentjes** (wit met rood) bij de lift in de lobby, op de
    beveiligingsverdieping en in de kluis schieten op iedereen die binnenkomt (niet in creative);
  - vier **beveiligingsdrones** met rode lampjes patrouilleren rond de kern en vallen je aan.
    Schiet je er een neer, dan stuurt de kern na zo'n 45 seconden een nieuwe.
  De kern is heel sterk (slopen kan met diamant, maar duurt lang). Wie hem **hackt**, krijgt de
  toren: dan brandt het lampje groen, worden de lampjes van de drones blauw, laten torentjes,
  drones en laserwanden jou en je team met rust, en bewaken ze de toren voor jou. Andere
  spelers vallen ze dan alleen aan als PvP aan staat.
- **Hacken** doe je met een **Stark Laptop** (drie zwarte glasruiten, redstone met een
  Stark-chip ertussen, drie ijzer; een bril heb je er niet voor nodig). Klik ermee op de kern:
  het alarm gaat af en alle drones komen op je af. Op je scherm verschijnt de hack in drie lagen
  (Firewall, Encryption, Override): een cursor schuift heen en weer, en je drukt op **spatie**
  (of klikt) als hij in het groene vak staat. Mis je, dan ben je een treffer kwijt; elke laag
  gaat sneller. Het spel loopt ondertussen door: je kunt niet weglopen, en de beveiliging
  schiet op je. Loop je weg (verder dan 6 blokken), ga je dood of druk je op Esc, dan is de
  hack afgebroken.

---

## 3. Toetsen

| Toets | Wat het doet |
|-------|--------------|
| **G** | Het ronde E.D.I.T.H.-menu (scrollen = ander tabblad) |
| **V** | Inzoomen (vasthouden) |
| **N** | Nachtzicht aan/uit |
| **B** | Ertsen-scan |
| **H** | Vijanden door muren aan/uit |
| **R** | Doelwit markeren waar je naar kijkt |
| **J** | Zwerm aanvalsdrones |
| **L** | Eén drone op één doelwit |
| **K** | Satelliet: precisieraket |
| **P** | Satelliet: bombardement |
| **C** | Satelliet: schuivende laser (1x = begin, 2x = eind) |
| **,** | Stand down: alle aanvallen afbreken |
| **.** | Spionagedrone sturen / terugroepen |
| **;** | Speler hacken waar je naar kijkt |
| **'** | Redstone-scherm (al je relais) |
| **Y** | Drone-camera (verkenner) |
| **M** | Navigatie / waypoints |
| **U** | Ping plaatsen |
| **O** | Comms (berichten naar andere E.D.I.T.H.-dragers) |
| **Z** | Spraaktoets: ingedrukt houden en praten |
| **I** | Repulsor aan/uit (met de borstplaat aan); vuren met de aanvalsknop |
| **X** | Salvo mini-raketten (hele harnas + raketpakket) |

Alle toetsen kun je aanpassen: **Opties > Besturing > E.D.I.T.H.**

---

## 4. Het G-menu

| Tabblad | Wat erin zit |
|---------|--------------|
| **Combat** | Repulsor, mini-raketten, rayshield |
| **Satellite & Arsenal** | Precisieraket, bombardement, schuivende laser (kiezen = meteen vuren), satellietscherm (upgrades), arsenaal |
| **Drones** | Zwerm, één drone, escorte, escortevorm, stand down, markeringen wissen |
| **Recon drones** | Drone-camera, spionagedrone, meekijken, rapport |
| **Transport** | Zelf vliegen, autopiloot, vrachtdrone, iemand vervoeren |
| **Tech** | Redstone-relais, muziek (Spotify), hacken |
| **Illusion** | Valse muur, nep-leger, onzichtbaar veld, stoppen |
| **Information** | Ertsen-scan, dreigingsscan, doelwit-analyse |
| **Navigation** | Waypoints, ping, comms |
| **Systems** | Eigendom overdragen, stem, spraakbesturing, commando's bewerken, spraaktekst, nachtzicht |

Wisselen van tabblad: scrollen, de pijltjes links/rechts, of meteen met de cijfers **1** tot
**9** en **0** (het tiende tabblad).

---

## 5. De grote functies

### Het drone-arsenaal
Drones komen niet meer uit het niets: je moet ze **bouwen**. Craft een **Stark-drone**
(een Stark-chip, 5 ijzer, 2 redstone en een amethist) en gebruik hem: hij schiet de lucht in
naar een baan om de planeet. Daar wacht hij tot je hem nodig hebt, waar je ook bent.

Alles wat drones gebruikt — de zwerm, de escorte, de verkenner, de spion, transport, vracht
en de illusies — haalt ze uit dat arsenaal. Hangt er niets meer, dan vertrekt er niets, en zegt
E.D.I.T.H. dat (je raakt dan geen energie kwijt).

Open het arsenaal met **G > Satellite & Arsenal > Arsenal**. Per drone zie je hoe heel hij nog is en waar
hij uithangt:

- **In orbit** — klaar voor gebruik. Met **Recall** komt hij in een boog uit de lucht en landt
  hij voor je. Roep je er meer terug, dan landen ze naast elkaar.
- **On a job** — nu ergens bezig. Daar valt niets aan te doen tot hij terug is (of gebruik
  "stand down").
- **Grounded** — hij staat naast je te wachten. **Rechtsklik** erop om hem te repareren met
  ijzer uit je rugzak (één staaf is 3 levenspunten). Is hij weer heel, dan stijgt hij bij de
  volgende klik op. **Shift + rechtsklik** stuurt hem meteen omhoog, ook als hij nog stuk is
  (de knop **Launch** in het scherm doet hetzelfde).

Schade blijft bewaard: een drone die gehavend terugkomt, gaat gehavend weer de lucht in tot je
hem repareert. Wordt een drone **neergehaald**, dan is hij weg — je krijgt ongeveer 10% van de
bouwkosten terug als schroot op de plek waar hij viel, de rest kost je een nieuwe drone.

### Drones in het veld
Ze komen **van heel hoog** aanvliegen: standaard 96 blokken boven je, in een boog met een
streep vonken achter zich aan. Ze beginnen een stuk opzij, duiken steil naar beneden, buigen
af en remmen rustig af bij hun doel (een zwerm waaiert zo van alle kanten binnen). Zit er een
plafond boven je (een grot, een kelder), dan komen ze zo hoog als er ruimte is. Klaar met hun werk? Dan stijgen ze net zo hoog weer op en verdwijnen
in de lucht — ze lossen niet ter plekke op. Pas als ze boven zijn, staan ze weer klaar in je
arsenaal.

Hoe hoog precies staat in de config (`drones.orbitHeight`). Hoeveel drones er maximaal in een
zwerm passen staat daar ook (standaard zwerm 16 en escorte 8) — maar je arsenaal bepaalt
hoeveel het er echt worden.

- **Zwerm** (J): valt je gemarkeerde doelwitten aan, of verdedigt je.
- **Eén drone** (L): goedkoop, jaagt op één doelwit tot het dood is.
- **Escorte**: vliegt met je mee en schiet op wat jou aanvalt. Met **G > Drones > Formation**
  kies je de vorm: **Orbit** (rondcirkelen), **Shield** (een snel draaiende ring dicht om je
  heen: wat op je af komt, raakt eerst een drone), **Arrow** (een V schuin boven en achter je)
  of **Circle** (een wijde ring). In Shield en Arrow blijven ze in formatie als ze schieten.
- **Stand down** (,): alles stopt meteen.

### Spionagedrone (.)
Volgt iemand 10 minuten stiekem (hij is onzichtbaar, maar wel raakbaar):
live positie op je kompas, elke 30 seconden een rapport, een logboek van alles wat hij doet,
en je kunt live meekijken (tot ongeveer 110 blokken; verder weg stuurt Minecraft de drone niet
naar jouw spel).

### Transport
- **Zelf vliegen**: drones tillen je op. Je vliegt de kant op waar je **kijkt**, spatie geeft
  extra lift, sprinten is sneller, **sluipen (shift) = landen**.
- **Autopiloot**: naar je huidige waypoint.
- **Vrachtdrone**: leegt de kist waar je naar kijkt en brengt alles naar je waypoint.
- **Iemand vervoeren**: pakt een speler (alleen met PvP aan), dier of monster op.

### Satelliet (K, P en C)
Drie aanvallen, elk met een eigen toets die meteen vuurt (geen standen om in te stellen). Ook in
**G > Satellite & Arsenal** staan ze elk apart; wat nog op slot zit, staat er als LOCKED.
- **Precisieraket (K)** — een echte raket die uit de ruimte neerkomt: veel schade, **geen**
  blokschade. Heb je vanaf het begin.
- **Bombardement (P)** — een regen van raketten rond het doel, met flinke kraters. Eerst vrijspelen.
- **Schuivende laser (C)** — een streep die over de grond kruipt, van een begin- naar een eindpunt
  dat jij kiest: kijk naar het begin en druk **C** (daar komt een markering), kijk naar het eind
  en druk nog eens **C** (in het menu: twee keer Sweeping laser; ertussen staat er PICK END
  onder). Een beginpunt blijft een halve minuut bewaard, of tot je K of P gebruikt. Eerst vrijspelen.
Eerst verschijnt een rode richtstraal; de raket vertrekt zo dat hij inslaat als het aftellen op
nul staat (je hoort hem aankomen). Hij volgt bewegende doelwitten (lock-on), richt tot 500
blokken, en je kunt ook op je waypoint of je laatste ping mikken. Bij de inslag komt er een
schokgolf en schudt je beeld. P en C doen in gewoon Minecraft ook iets kleins (sociale
interacties, creative-werkbalk opslaan); met een werkende bril op is P van E.D.I.T.H.

**Upgraden** doe je in het satellietscherm (G > Satellite & Arsenal > Satellite). Zes onderdelen, elk tot
niveau 10: de drie aanvallen (vrijspelen, dan sterker), opladen (kortere wachttijd), kracht (meer
schade voor alles) en richten (sneller richten, verder weg). Een niveau kost spullen uit je
rugzak (ijzer, redstone en Stark-chips; later goud, diamant en netherite), en een paar drones
uit je arsenaal brengen ze naar boven — die komen daarna gewoon terug. De satelliet zelf krijgt
er zichtbaar onderdelen bij: langere zonnepanelen, raketkokers, een kanon, een grotere schotel.
In creative zet de knop **Max all** alles in één keer op 10.

### Redstone-relais (')
Craft een **E.D.I.T.H.-relais**, zet het naast een deur, lamp, zuiger of trechter en klik er
rechts op (met de bril op) om het een **naam** en een **groep** te geven. Daarna kan E.D.I.T.H.
het overal in dezelfde wereld aan- of uitzetten, omschakelen of een korte puls geven — per
relais of per hele groep. Met je stem: *"zet de voordeur aan"*, *"doe de val uit"*,
*"geef de poort een puls"*.

### Hack player (;)
Kijk een speler aan en hack hem. Daarna zie je zijn **privéberichten** (/msg, /tell, /w,
/teammsg en E.D.I.T.H.-comms) — ook de antwoorden van anderen — in een ballonnetje boven zijn
hoofd. Je ziet het alleen als je hem echt kunt zien (binnen 64 blokken, geen muur ertussen, en
hij moet in je beeld staan). Max 3 spelers tegelijk. Server-eigenaars kunnen dit uitzetten met
`hack.allowed = false` in de serverconfig.

### Stroom en kabels
Zonnepanelen en warmtegeneratoren maken stroom. Met **Stark-kabels** (koper, redstone, koper
geeft 8 kabels) breng je die naar alles wat stroom gebruikt: het laadstation, de laserzender, de
holotafel en de schildkoepel. Kabels sluiten vanzelf op elkaar en op apparaten aan; een apparaat
dat direct tegen een generator staat, heeft geen kabel nodig. Een apparaat haalt zijn stroom
eerst bij de generator die het meest heeft.
- Een **zonnepaneel** werkt alleen overdag onder open hemel. Het is een schuin paneel op een
  paaltje (je loopt er gewoon overheen).
- Een **warmtegenerator** stookt brandstof, en haalt gratis warmte uit lava, magma of vuur om
  hem heen (hoe meer, hoe beter). Bij een lavameer hoef je er nooit meer naar om te kijken.
  Zijn voorkant (het rooster) wijst naar je toe als je hem neerzet; zolang hij warmte
  opwekt, gloeit het rooster oranje en geeft hij wat licht.
- Zonder stroom doen de laserpoort, de holotafel en de koepel niets.
- Het **laadstation** laadt een bril die erin ligt uit het net, en vult daarna ook zijn eigen
  voorraad, maar alleen met wat over is: de generatoren houden altijd de helft achter voor de
  rest van het net.

### Rayshield
- **Persoonlijk schild** (G > Combat > Rayshield): vangt pijlen, vuurballen, explosies en lasers op. Elke
  treffer kost energie; batterij leeg = schild weg.
- **Laserzender** (volledig blok met een lens aan de voorkant): een doorzichtig energieveld
  met lopende scanlijnen waar niemand door komt. Het schuift uit de lens naar voren tot het
  eerste blok (of een tweede zender die terugkijkt), en bij het uitgaan weer terug de zender
  in, net als de ray shields uit Star Wars. Eén zender is genoeg.
  **Rechtsklik** bladert door de kleuren: blauw, cyaan, groen, geel, rood, paars.
  Aan met redstone, en hij heeft **stroom** nodig (een beetje per seconde zolang de wand
  staat). Wil je hem met E.D.I.T.H. schakelen, zet er dan een relais naast.
- **Schildkoepel-generator** (blok, op stroom): een koepel om je basis. **Rechtsklik** opent
  zijn scherm: aan/uit, kleur, bereik (8 tot 32 blokken) en sterkte (1 tot 5). Groter en
  sterker kost meer stroom. De koepel houdt alles tegen wat niet van jou of je team komt:
  pijlen en vuurballen, monsters en raiddrones (die worden naar buiten geduwd), laserschoten
  van raiddrones en andermans drones, en satellietaanvallen van anderen. Jouw eigen aanvallen
  gaan er gewoon doorheen, net als spelers en dieren. Elke treffer kost **schildkracht**; een
  sterkere koepel heeft er meer en laadt sneller bij. Is hij leeg, dan valt de koepel 10
  seconden weg. Aanzetten is een show: een straal omhoog, dan vouwt hij bovenin open en zakt
  als koepel naar beneden. De straal uit de generator blijft staan zolang de koepel aan is.

### Illusies (cloakingdrones)
Een onzichtbare drone projecteert een beeld in een bol van 16 blokken. Alles is **nep**: in de
wereld verandert niets.
- **Valse muur** — van het blok dat je in je hand houdt.
- **Nep-leger** — van het spawn-ei dat je in je hand houdt. Ze lopen op de dichtstbijzijnde
  andere speler af, gaan om hem heen staan en slaan, maar zonder schade: zo leid je iemand af.
- **Onzichtbaar veld** — alles in het veld wordt niet meer getekend.

Een muur of leger **richt je eerst**: een drone vliegt van je weg en jij kijkt door hem heen
(WASD, spatie en shift om te vliegen). Waar je naar kijkt, zie je de illusie als voorvertoning
in een blauw kader; de muur draait mee zodat hij dwars op je kijkrichting staat.
**Linkermuisknop** zet hem neer, **rechtermuisknop** annuleert (de drone gaat terug).

Zwakke plekken (net als in de film): waar de velden van twee verschillende spelers overlappen
gaat het beeld **flikkeren** (je eigen illusies storen elkaar niet),
de drone blijft raakbaar (neerhalen = beeld weg), hij verraadt zich met vonkjes en een zacht
zoempje, en met de **E.M.P.-zender** (item) leg je alle illusies binnen 24 blokken 30 seconden
plat.

### Raiddrones
Wie de E.D.I.T.H.-bril draagt, valt op. Af en toe komt er een golf **raiddrones** op je af:
donker, met oranje lampjes, en niet van jou. Ze duiken uit de lucht, cirkelen om je heen en
schieten. Verstop je je achter een muur, dan komen ze dichterbij en breken ze zich een weg naar
binnen.

- **Vanzelf**: standaard elke 20 tot 45 minuten, zolang je de bril op hebt.
- **Omdat je erom vroeg**: sloop je in een Stark Tower, dan trek je aandacht. Doe je dat te
  vaak, dan komen ze meteen (bij het eerste blok krijg je een waarschuwing).
- **Bij een wrak**: het wrak van een neergestorte satelliet zendt nog, en zij luisteren mee.
  Kom je er met de bril, dan duiken ze meteen naar beneden (hooguit eens per 10 minuten).

### Neergestorte satellieten
Hier en daar is een oude Stark-satelliet uit zijn baan gevallen: een krater van zo'n 25
blokken breed met verschroeide grond, opgeworpen aarde op de rand en aan één kant de voor
waar hij binnenkwam. Je vindt ze in vlaktes, woestijnen, savannes, badlands, bossen, taiga's
en sneeuwvlaktes; de rook van de smeulende brokstukken zie je van ver. In het midden ligt de
romp (opengescheurd, de reactor gloeit nog), een zonnepaneel dat schuin omhoog steekt en de
schotel op de helling. In de romp staat de **zwarte doos**: een kist met wat je voor de
satelliet-upgrades nodig hebt (netherite, diamanten, goud, Stark-chips, soms een upgrade of
een drone). In de kratten eromheen liggen brokstukken. Maar het wrak wordt bewaakt (zie Raiddrones).
- **Blokken slopen uitzetten**: `/gamerule starktechRaidGriefing false`. Dan kunnen ze nog wel
  schieten, maar laten ze je muren heel.
- **Instellen** in `config/starktech-server.toml`: `raids.enabled`, `raids.minMinutes`,
  `raids.maxMinutes` en `raids.size` (hoeveel drones per golf, standaard 4). Zie ook hoofdstuk 6.

### Hydra-soldaten
Hydra wil Stark-tech. Hun soldaten dragen een zwart tactisch pak, een rode bril en het rode
embleem op borst en helm. Zien ze je, dan leggen ze hun **lasergeweer** aan en schieten ze rode
laserstralen (tot 18 blokken ver; een rayshield houdt ze tegen). Ze schieten ook op je drones,
en raak je er één, dan komen de anderen ook. Ze bewaken de Hydra-bunker (zie hieronder).

- Versla je er een, dan laat hij soms een **Hydra-onderdeel** vallen. Dat heb je nodig voor
  de nieuwste tech: het raketpakket (zie Het harnas) en de torentjes (zie Torentjes).
- Zelf oproepen om te oefenen: het **Hydra Soldier Spawn Egg** in de Stark Tech-tab.

### Torentjes
Een **Stark Turret** verdedigt je basis. Craft hem met een **Hydra-onderdeel** in het midden,
twee Stark-chips ernaast, een dispenser erboven, een observer eronder en ijzer in de hoeken.
Zet hem neer: hij is van jou. De kop kijkt rustig rond; komt er een monster (ook raiddrones en
Hydra-soldaten) binnen zicht, dan draait hij erheen en blijft hij erop schieten tot het neer is.

- **Laser**: tot 16 blokken, 4 schade per seconde. Elk schot haalt 60 stroom van het net: sluit
  hem aan met een Stark-kabel op een zonnepaneel of warmtegenerator (of zet die er direct
  tegenaan). Eén zonnepaneel houdt één torentje makkelijk bij. Geen stroom = geen laser.
- **Raketten**: klik rechts met **Rocket Ammo** (4 stuks uit een ijzerklompje-kruis met buskruit
  en redstone) of zet er een trechter boven. Dan gaat er om de 2,5 seconde ook een raket in een
  boogje op af, tot 24 blokken ver. Er passen 64 in.
- **Op wie**: monsters. Spelers alleen als PvP aan staat, en nooit jij of je team. Dieren,
  dorpelingen, je huisdieren en E.D.I.T.H.-drones laat hij met rust. Wat hij neerhaalt telt
  als jouw buit.
- **Uitzetten**: een redstone-signaal (een hendel, of een E.D.I.T.H.-relais ernaast) zet hem
  uit; de loop hangt dan omlaag. E.D.I.T.H. kan hem ook zelf schakelen: hij staat in je
  redstone-lijst (toets **'**) onder **Turrets**, en werkt ook met je stem.
- **Hoe staat hij ervoor?** Klik rechts met een lege hand: aan/uit, stroom en raketten. Met
  shift haal je de raketten eruit.

In de Hydra-bunker staan **Hydra Turrets** (zwart met rood): die hebben geen stroom nodig en
schieten op jou en je drones. Ook die gaan uit met redstone. Sloop je er een, dan krijg je
ijzer en soms een Hydra-onderdeel.

### De Hydra-bunker
Ergens in de bergen heeft Hydra een geheime basis uitgehakt, zoals in The Winter Soldier. Je
vindt hem in bergachtige streken (pieken, sneeuwhellingen, bosjes op de berg, winderige heuvels):
een uitsparing in een rotswand met een betonnen gevel en het rode Hydra-embleem. Er is geen
kaart naartoe: je moet hem zelf vinden.

Wat je binnen tegenkomt:
- **Bij de deur** twee Hydra-torentjes; daarna een gang met een controlepost.
- **De grote hal**: zuilen, kratten met voorraden (buskruit, ijzer, redstone, soms raketten),
  Hydra-vaandels, soldaten en een torentje op een verhoging.
- **Links de slaapzaal** (bedden, kisten met wat soldaten bij zich hebben, soms een
  Hydra-onderdeel), **rechts de reactorkamer**: twee warmtegeneratoren in een bak lava.
- **De kluisgang** met twee rode laserwanden. Die krijgen hun stroom van de reactor, via de
  kabel over het plafond van de hal. **Sloop de generatoren of knip de kabel door**, en ze gaan
  uit. (Of graaf je eromheen: het is een berg.)
- **De kluis**: twee kisten met altijd 2 à 3 **Hydra-onderdelen**, en verder diamanten, goud,
  raketmunitie, Stark-chips en soms een raketpakket. Plus twee torentjes en een cryo-cel.

Er staan acht soldaten en vijf torentjes; zonder harnas is dat pittig. Soldaten komen niet
terug als je ze verslagen hebt, dus de onderdelen zijn niet eindeloos te halen.

### Prestaties (advancements)
De mod heeft een eigen prestatieboom die je door het spel leidt, van je eerste Stark-chip tot
vliegen met het harnas. Open je prestaties (standaard **L**) en kijk bij **Stark Industries**.

Sommige krijg je vanzelf als je iets maakt; andere kent de mod zelf toe op het moment dat het
gebeurt — je eerste drone de ruimte in sturen, een satellietaanval, een drone verliezen, met het
harnas vliegen.

### Het harnas
Het harnas maak je aan de **harnas-werkbank** (zie hieronder): een **borstplaat**, **broek** en
**laarzen** (netherite, Stark-chips, amethist, ijzer, redstone). Er is geen
helm: die plek is van de bril, en dat is de bedoeling — **zonder de bril doet het harnas niets
bijzonders**. Het is dan gewoon een goed pak metaal. De bril levert de stroom; het harnas heeft
geen eigen batterij.

Het harnas heeft een eigen 3D-vorm: een reactor op je borst die blauw gloeit (ook 's nachts),
borst- en buikplaten, een rugplaat, schouderplaten, onderarmbeschermers, kniestukken en
laarzen met een gouden neus.

Met de bril op en energie erin:

- **Schade**: elk gedragen stuk haalt 20% van elke klap weg, dus met het hele pak 60% — bovenop
  de gewone bescherming. Dat betaalt je bril per treffer.
- **Vliegen**: met alle drie de stukken heb je stuwraketten. **Dubbel op spatie** zet ze aan
  (nog eens dubbel spatie zet ze uit; landen doen ze vanzelf). Het is geen creative-vliegen:
  je hebt vaart. **W/S** stuwt je de kant op waar je **kijkt** (ook omhoog of omlaag),
  **A/D** opzij, **spatie/shift** stijgen en dalen, en **sprint** is de naverbrander. Snelheid
  bouwt langzaam op en loopt uit als je loslaat, het beeld helt over in de bochten en vlak
  boven de grond remt het harnas zelf af. Links van je vizier staan je snelheid en hoogte.
  Stilhangen kost 12 energie per seconde, vooruit vliegen 26, de naverbrander 45. Raakt je
  batterij leeg, dan val je gewoon uit de lucht — hou hem dus in de gaten. Onder je laarzen
  branden blauwe straalvlammen (langer en feller als je harder gaat); iedereen ziet ze. Van
  buitenaf zie je geen loopbeweging: wie vooruit vliegt, helt voorover tot bijna liggend, met
  de benen recht, en de vlammen wijzen dan langs je lichaam naar achteren.
- **Landen**: de laarzen vangen elke val tot 40 blokken helemaal op.
- **Repulsor** (toets **I**, of G > Combat): zet de repulsor **aan of uit**. Staat hij aan,
  dan is je aanvalsknop (linkermuisknop) de repulsor: klikken vuurt, ingedrukt houden vuurt
  elke seconde. Slaan en blokken breken gaat dan niet; zet hem uit om weer te hakken. Rond je
  vizier staat een ring die na elk schot weer volloopt. Een stoot raakt alles op zijn weg en
  duwt het een stukje weg, tot 24 blokken ver. Je hebt de borstplaat nodig (daar zit de
  reactor) en het kost 200 energie per stoot. Met een lege hand zie je je arm omhoog gaan,
  de hand geknikt met een gloeiende handpalm; bij een schot flitst hij op en slaat je arm
  terug. Anderen zien je arm vooruit wijzen en je handpalm oplichten als je schiet.
- **Mini-raketten** (toets **X**, G > Combat, of zeg "fire rockets"): een salvo van zes
  raketten uit twee buizen op je rug. Daarvoor heb je een **raketpakket** nodig: craft het met
  een **Hydra-onderdeel** in het midden, een Stark-chip eronder, buskruit en ijzer. Trek de
  borstplaat aan en klik rechts met het pakket in je hand; dan zit het vast op DIE borstplaat
  (de tooltip zegt "Rocket pack installed") en zie je de buizen op je rug. De raketten gaan
  om de beurt links en rechts weg, waaieren uit en draaien dan naar hun doel: je
  **gemarkeerde doelwitten** (R; ze worden verdeeld), anders **wat je aankijkt**, anders de
  **plek waar je kijkt**. Ze ontploffen zonder blokken te slopen en raken jou, je team en je
  drones nooit. Je hebt het hele harnas nodig; een salvo kost 900 energie en daarna moet het
  pakket 12 seconden herladen (links in de HUD: ROCKETS). Munitie is er niet.

### De harnas-werkbank (Tony's lab)
Craft een **Suit Workbench** (Stark-chips, een redstoneblok, een crafting table, een aambeeld en
ijzer), of gebruik die in het lab van een Stark Tower. Hij heeft **stroom** nodig: een Stark-kabel
naar een zonnepaneel of warmtegenerator, of er direct tegenaan. Klik erop voor zijn scherm:

- **Bouwen**: leg de materialen in de negen vakken links en druk op **Chestplate**, **Leggings** of
  **Boots** (houd de muis op een knop en je ziet wat het kost; een grijze knop betekent dat er iets
  ontbreekt). De materialen gaan eruit, er loopt een balkje vol, en het stuk verschijnt in het vak
  **Suit**. Het harnas kun je niet meer in de crafting table maken.
- **Upgraden**: leg een stuk harnas in het vak **Suit** en een upgrade in het vak **Upgrade**; het
  inbouwen begint vanzelf. De upgrades (te craften met Stark-chips, en soms te vinden in de kisten
  van een Stark Tower):
  - **Thruster Upgrade** (in de laarzen, tot 3): sneller vliegen en minder energie.
  - **Armor Plating** (in elk stuk, tot 2 per stuk): het harnas houdt meer schade tegen.
  - **Repulsor Capacitor** (in de borstplaat, tot 2): een sterkere repulsor.
  - Het **raketpakket** kan hier ook in de borstplaat (of met een rechtsklik, zie boven).
  In de tooltip van een stuk harnas zie je welke upgrades erin zitten.

### Holotafel
Craft een **holotafel** (getint glas, amethist, een Stark-chip en ijzer) en zet hem neer. Hij
projecteert een hologram boven zich dat langzaam ronddraait (als hij **stroom** heeft; zonder
stroom knippert er alleen een rood ringetje), en je bedient hem met de bril op:

| Wat je doet | Wat er gebeurt |
|---|---|
| **Rechtsklik** (lege hand) | Het instelscherm |
| **Rechtsklik met een voorwerp** | Laat het recept van dat voorwerp zien |
| **Sluipen + rechtsklik** | Meteen de volgende kleur, zonder scherm |

In het **instelscherm** kies je wat hij laat zien, de kleur (dezelfde zes als de rayshields),
hoe ver de kaart reikt (24, 48, 96 of 160 blokken), hoe groot het hologram is (klein, normaal,
groot) en of hij ronddraait. Je ziet het hologram achter het scherm meteen veranderen, dus er
is geen opslaan-knop. Met **Take off** haal je het voorwerp weer van tafel.

Vijf standen:

- **Map** — een reliëfkaart van 96 bij 96 blokken rond de tafel: hoe hoger de grond, hoe hoger
  en feller het staafje. Je waypoints staan er als gekleurde paaltjes in, en het leeft:
  spelers zijn witte stipjes, vijanden rode, dieren groene.
- **Blueprint** — het recept van wat je op tafel legt, als zwevend 3x3-rooster met het
  resultaat erboven. Mag een onderdeel uit een hele groep komen (bijvoorbeeld "een ijzerstaaf"),
  dan wisselen de mogelijkheden elkaar af. Bestaat er geen recept (zoals bij de bril), dan zie
  je een rood kruis.
- **Arsenal** — je drones cirkelen om een bolletje, als kleine hologrammen van het echte
  drone-model. Groen is heel, oranje heeft een tik gehad, rood is bijna op; een drone die
  onderweg is knippert.
- **Satellite** — je satelliet groot als hologram, met alle onderdelen van zijn upgrades
  (langere zonnepanelen, kanon, raketkokers, grotere schotel...). De ring eronder heeft zes
  stukken, één per upgrade, met tien streepjes: fel wat je al hebt.
- **Ores** — de ertsen in de grond onder de tafel (tot 16 blokken opzij en 48 diep) als
  gekleurde blokjes in een doorzichtig blok aarde, in de kleuren van de ertsscanner. Diamanten
  en smaragden knipperen, kolen en koper zijn zwakker. Het witte puntje is de tafel.

Zonder bril doet de tafel niets.

### Muziek (Spotify)
Zeg *"E.D.I.T.H., speel Thunderstruck van AC/DC"*. Ook: pauze, verder, volgende, vorige en
*"wat speelt er"*.

Eenmalig instellen (G > Tech > Muziek legt het ook stap voor stap uit):
1. Ga naar `developer.spotify.com/dashboard` en maak een gratis app ("Create app").
2. Vul als **Redirect URI** exact in: `http://127.0.0.1:8899/starktech`
   (in het muziekscherm staat er een knop **Kopieer** naast, dan kun je hem plakken)
3. Kopieer het **Client ID** van die app in het muziekscherm.
4. Klik **Koppelen**: je browser opent, je logt in bij Spotify, klaar.

Je wachtwoord komt nooit in de mod: je logt in op de site van Spotify zelf. De mod bewaart
alleen een token in `config/starktech-spotify.json`. Spotify moet ergens open staan (pc,
telefoon of speaker), want daar speelt de muziek. Starten van muziek werkt alleen met Premium.

### Stem en spraakbesturing
Via **G > Systems** installeer je de stem (Piper) en de spraakbesturing. Push-to-talk is **Z**.
Praat E.D.I.T.H. terwijl jij begint te praten, dan zwijgt ze meteen. In **G > Systems >
Voice text** zet je aan of uit of je in de HUD ziet wat je zei en wat zij antwoordt.
Alle zinnen kun je zelf aanpassen in **G > Systems > Commands** (of in
`config/starktech-voice.json`).

---

## 6. Waar staan de instellingen?

| Bestand | Wat |
|---------|-----|
| `config/starktech-balance.toml` | **Alle getallen** (120 stuks): kosten, schade, bereik, wachttijden, verbruik, per onderdeel (bril, drones, satelliet, harnas, torentjes, Hydra...) |
| `config/starktech-server.toml` | Max drones, `drones.orbitHeight` (hoe hoog ze in- en uitvliegen), raids, hacken aan/uit |
| `config/starktech-client.toml` | Stem, volume, spraaktekst, Spotify Client ID, GPU voor de AI |
| `config/starktech-voice.json` | Alle spraakcommando's (zelf uit te breiden) |
| `config/starktech-spotify.json` | Je Spotify-token (niet delen) |
| `src/main/java/.../edith/EdithBalance.java` (en `SuitBalance`, `TurretBalance`...) | De standaardwaarden van die getallen |

De twee `starktech-...toml`-bestanden gelden voor **al je werelden** (op een server: de map
`config` van de server; spelers krijgen de getallen van de server vanzelf mee). Moet **één
wereld** anders zijn, bijvoorbeeld "in deze wereld is de satelliet te sterk"? Kopieer het bestand
naar `<wereld>/serverconfig/` en pas het daar aan: die kopie gaat voor. Het spel leest het bestand
opnieuw als je het opslaat terwijl je speelt; je hoeft niets te herstarten.

Een nieuw getal instelbaar maken (voor jezelf als modder): zet `@Tunable(section = "...",
comment = "...")` erboven in het balance-bestand en haal `final` weg.

---

## 7. Alles in één keer bekijken: de showcase

```
python tools/showcase.py
```

Het spel start meteen op in een testwereld (dat duurt een halve tot een hele minuut). Intussen
zie je de **inhoud**: alle functies per deel (De bril, Drones, Transport, ...), met hun nummer.
Bovenaan staat **NIEUW**: wat je nog niet gezien hebt (ook in de delen gemarkeerd met een `*`).
Dan vraagt het script `Wat wil je zien?`:

| Wat je typt | Wat je ziet |
|---|---|
| Enter | alles |
| `n` | alleen de nieuwe functies |
| `3 7 12` | die functies |
| `10-15` | functie 10 tot en met 15 |
| `drones` | een heel deel (een stukje van de naam is genoeg: `bril`, `harnas`, `vijanden`...) |

Je mag ze mengen, bijvoorbeeld `n 3-5 harnas`. Een functie gaat uit NIEUW zodra je hem bekeken
hebt: hij speelde tot het eind, of je stopte hem pas na 8 seconden. Wat je meteen overslaat,
blijft nieuw. Wat je gezien hebt staat in `showcase-gezien.json`; gooi dat weg en alles is
weer nieuw.

Daarna vraagt het `Wil je een pauze tussen elke functie (Y/n):`. Druk gewoon op Enter voor ja
(wat in HOOFDLETTERS staat is de standaard), of typ `n`. In je terminal staat bij elke functie
de naam en waar je op moet letten; wat E.D.I.T.H. zegt, verschijnt er ook.

Met pauze wacht het script na elke functie op jou. Je typt iets en drukt op Enter:

| Wat je typt | Wat er gebeurt |
|---|---|
| Enter of `s` | volgende functie (van wat je koos) |
| `v` | vorige functie |
| `r` | deze functie nog eens |
| `i` | de inhoud weer zien en iets anders kiezen |
| `q` | stoppen (het spel sluit ook) |
| een getal, bv. `12` | naar die functie springen |
| iets anders | wordt bewaard als notitie bij deze functie, in `showcase-notities.md` |

Dat typen kan ook al terwijl een functie speelt. Een notitie wordt dan meteen bewaard en de
functie speelt gewoon door. Typ je `s` en Enter (of `v`, `r`, `i`, `q` of een getal), dan stopt
de functie meteen en gaat het script daarheen. Alleen wat je in zijn geheel typt telt: een S
midden in een notitie slaat dus niets over. Klik wel eerst op de terminal, anders krijgt het
spel je toetsen.

Zonder inhoud meteen beginnen bij een bepaalde functie: `python tools/showcase.py --vanaf 30`.
Alleen een stukje: `python tools/showcase.py --vanaf 30 --tot 34`.

Het spel draait in de map `run-showcase`, los van je eigen werelden. Je hoeft er niets in te
doen, maar je mag wel rondkijken. Een nieuwe functie gebouwd? Zet hem in
`client/dev/ShowcaseFeatures.java` (onder het juiste kopje `// ----- Naam`), dan zit hij in de
showcase en staat hij bij NIEUW.

---

## 8. Zelf verder bouwen

- **Getallen aanpassen** (sterker/zwakker): `EdithBalance.java`, dan `gradlew build`.
- **Textures**: `python tools/generate_textures.py` maakt ze allemaal opnieuw. Teken je liever
  zelf? Teken dan over het bestand heen en draai het script niet meer.
- **Blokmodellen**: `python tools/generate_block_models.py` maakt de 3D-modellen van het
  zonnepaneel en de warmtegenerator (een lijstje doosjes per model).
- **De toren verbouwen**: `python tools/generate_structure.py`, of bouw hem in het spel met een
  structuurblok en sla hem op als `starktech:stark_tower`.
- **Het satellietwrak verbouwen**: `python tools/generate_crash_site.py` (of in het spel opslaan
  als `starktech:crashed_satellite`).
- **Komen de structuren echt in de wereld?** `gradlew runStructureCheck` start een server met
  een gewone wereld (in `run-structures`), zoekt de dichtstbijzijnde Stark Tower, het
  dichtstbijzijnde satellietwrak en de dichtstbijzijnde Hydra-bunker, en schrijft in het log
  (`[structures]`) waar ze liggen, waar de kisten staan,
  hoeveel soldaten en torentjes er in de bunker staan en hoe je er met `/tp` komt. Daarna stopt
  hij vanzelf. Wil je een andere wereld, gooi dan `run-structures/world` weg.
- **De bunker verbouwen**: `python tools/generate_bunker.py`.
- **Tests**: `gradlew runGameTestServer` — 74 tests over de toren en zijn kluis, het
  satellietwrak, de Hydra-bunker, de drones en het arsenaal, zelf vliegen en landen, de satelliet, navigatie,
  spion, de bril, het laadstation, de rayshields, de relais, de holotafel, het harnas en de
  mini-raketten, de Hydra-soldaten, de torentjes en de spraakcommando's.
- **Screenshots van alles**: `gradlew runVisualCheck` maakt vanzelf een testwereld, fotografeert
  alle schermen, de holotafel, het harnas, een testvlucht en de satelliet, en sluit weer af. De
  plaatjes staan in `run-visual/screenshots`. Alleen een stuk ervan: `gradlew runVisualCheck
  -Ponly=satellite` (of `boot`, `holo`, `screens`, `hud`, `suit`, `flight`, `drones`,
  `laser`, `commands`, `illusion`, `power`, `dome`, `models`, `tower`, `crash`, `hydra`,
  `rockets`, `turrets`, `menu`, `bunker`, `missile`; meerdere met komma's). Op een ander
  schermformaat: `-Psize=1600x900`.
- **Geluiden**: `python tools/generate_sounds.py` maakt de eigen geluiden (repulsor,
  stuwraketten) opnieuw. Eenmalig nodig: `pip install numpy scipy soundfile`.
