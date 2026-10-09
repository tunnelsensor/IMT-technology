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
- Alle beveiliging draait op **stroom** van de verborgen krachtcentrale op de
  beveiligingsverdieping (een dicht blok met een mantel van **obsidiaan**). Daarin zit de
  **stroomkast**: een schakelaar voor de lasers en de kluis, en een voor de beveiliging (de
  kern, de torentjes en via de koperen zuil van de lift ook de lobby). Zonder stroom hangen de
  lopen van de torentjes omlaag en zakken de drones met gedoofde lampjes naar de grond. De
  kabels lopen verborgen in de vloeren: met de bril zie je ze door de vloer heen, en met een
  **schaar** knip je ze door (eerst de vloer openhakken).
- **Hacken** doe je met een **Stark Laptop** (drie zwarte glasruiten, redstone met een
  Stark-chip ertussen, drie ijzer; een bril heb je er niet voor nodig). Klik ermee op de kern:
  je zet de laptop **opengeklapt bovenop de kern** (er moet daar dus plek zijn), het alarm gaat
  af en alle drones komen op je af. Op je scherm opent de **terminal** van de laptop, waarin je
  commando's typt (Enter = uitvoeren, **Tab** = aanvullen, pijltje omhoog = vorige commando,
  scrollwiel = terug, `help` = wat je hier kunt):
  1. **Recon**: `scan` laat de poorten zien, maar ze staan allemaal op "filtered". Lees het
     firewall-logboek met `logs`: daar staan twee poorten die voor onderhoud open zijn gezet...
     en één daarvan is later weer dichtgezet. `connect <poort>` op de goede.
  2. **Breach**: een geheugendump met acht woorden tussen de tekens die op elkaar lijken. Eén is
     het wachtwoord: typ het woord (of `login <woord>`). Fout? Dan zie je welke letters op
     dezelfde plek staan als in het wachtwoord (`ROCKET -> ____ET`), streept de terminal de
     woorden door die daardoor niet meer kunnen, en zegt hij welke er nog over zijn ("Still
     possible"). Meestal heb je het in 2 of 3 keer. Na vijf fouten is hij even geblokkeerd en
     krijg je een nieuwe dump.
  3. **Search**: nu ben je binnen, en Tony heeft een briefje achtergelaten: `README.txt` (lees het
     met `cat README.txt`). Er staan geen kant-en-klare commando's in, wel alle aanwijzingen: de
     geheime naam van het beveiligingsprogramma staat in de security-config in `/etc`, een paar
     programma's in `ps` klinken alleen maar als beveiliging (nep), en je neemt het over met zijn
     nummer. `cat` vindt een bestand ook zonder het hele pad, en een foute poging zegt wat je moet
     doen (typ je de naam in plaats van het nummer, dan zegt hij dat het nummer in `ps` staat).
  4. **Override**: `override <nummer>` opent de console van het beveiligingsprogramma. `status`
     laat zijn instellingen zien. Zet eerst de waakhond uit met `disable watchdog` (anders zet hij
     de eigenaar meteen terug), en maak jezelf dan eigenaar met `set owner <jouw naam>` (`whoami`
     zegt hoe je heet; Tab vult het aan).
  Het spel loopt ondertussen door, en de beveiliging schiet op je. **Je voortgang staat op de
  laptop**: druk je op Esc, loop je weg (verder dan 6 blokken) of ga je dood, dan gaat alleen het
  scherm dicht. Rechtsklik op de laptop en je gaat verder waar je was (ook na opnieuw inloggen).
  Alleen wie de laptop neerzette, kan hem gebruiken. Is de toren van jou, dan klap je de laptop
  dicht en zit hij weer in je rugzak; breek je hem eerder af, dan krijg je hem ook terug (maar
  begin je opnieuw).

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

Alle toetsen kun je aanpassen: **Opties > Besturing > E.D.I.T.H.** Daar staan ook "Cable X-ray
on/off" (verborgen kabels door muren heen zien) en "Zone border on/off" (de rand van je zones),
allebei standaard zonder toets.

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
| **Information** | Ertsen-scan, dreigingsscan, doelwit-analyse, kabel-xray (verborgen kabels zien), zonerand |
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
Zonnepanelen en warmtegeneratoren maken stroom. Met **Stark-kabel** (koper, redstone, koper
geeft 8 stuks) breng je die naar alles wat stroom gebruikt: het laadstation, de laserzender, de
holotafel, de schildkoepel, de torentjes... Een apparaat dat direct tegen een generator staat,
heeft geen kabel nodig. Een apparaat haalt zijn stroom eerst bij de generator die het meest heeft.

Kabels zijn **geen blokken**: je **tekent** ze.
- Neem de kabel in je hand, **houd de rechtermuisknop ingedrukt** en kijk rond: waar je kijkt,
  komt een dun kabeltje te liggen, over de vloer, tegen een muur op, onder een plafond. Kijk je
  een stuk verder, dan trekt hij netjes een lijn (langs de oppervlakken). Elk stukje kost één kabel.
- Kabels die naast elkaar liggen zitten aan elkaar vast; een apparaat naast een kabel (of er
  bovenop gezet) ook.
- Omdat het geen blok is, kun je er gewoon **blokken overheen bouwen**: bouw een muur of vloer
  over een kabel en hij loopt erin door. Zo werk je kabels weg. Verborgen kabels zie je alleen
  **met de bril op**: dan lichten ze blauw op door de muur heen (tot 24 blokken ver). Dat
  zet je aan of uit met **G > Information > Cable X-ray** (of geef het een eigen toets in
  Opties > Besturing > E.D.I.T.H.: "Cable X-ray on/off").
- **Shift + rechtsklik** met kabel in je hand op een kabel: oprapen. Een **schaar** (rechtsklik)
  knipt een kabel door; er valt een stukje uit. Een kabel in een muur moet je eerst vrijhakken.
- Oude kabelblokken (uit een oudere versie, en in de Stark Tower en de Hydra-bunker) worden
  vanzelf zo'n getekende kabel.
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
- **Laserzender** (volledig blok met een lens aan de voorkant): een dun, doorzichtig energieveld
  met een zachte, wolkerige gloed waar af en toe op een willekeurige plek een klein kronkeltje
  energie oplicht, en waar niemand door komt. Het schuift uit de lens naar voren tot het eerste blok (of een tweede zender die
  terugkijkt), en bij het uitgaan weer terug de zender in, net als de ray shields uit Star Wars.
  Eén zender is genoeg. **Rechtsklik** bladert door de kleuren: blauw, cyaan, groen, geel, rood,
  paars. Aan met redstone, en hij heeft **stroom** nodig (een beetje per seconde zolang de wand
  staat; om aan te gaan moet het net stroom hebben voor minstens 5 seconden). Zonder stroom
  blijft hij gewoon uit: je hoort één zacht klikje als je de redstone aanzet, en daarna wacht
  hij stil tot er weer stroom is (dan gaat hij vanzelf aan). Wil je hem met E.D.I.T.H.
  schakelen, zet er dan een relais naast.
  **Zenders die tegen elkaar aan zitten, zijn één laserpoort**: redstone op één ervan zet ze
  allemaal aan, en ze geven de stroom van één kabel aan elkaar door. Zet er bijvoorbeeld drie
  op elkaar voor een wand van drie hoog, met één hendel.
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
laserstralen (tot 18 blokken ver; een rayshield houdt ze tegen). Ze schieten ook op je drones.
Ze bewaken de Hydra-bunker (zie hieronder).

Ze vechten als **ploeg**: ziet één soldaat je, dan weten de soldaten in de buurt het ook. Ze
verdelen zich rondom je (omsingelen), stappen tijdens het schieten opzij en lopen achteruit als
je te dichtbij komt. Verstop je je achter een muur, dan zoeken ze een plek om de muur heen van
waaruit ze je wel zien; zit je binnen, dan zoeken ze een weg naar binnen, en **deuren doen ze
gewoon open**.

- Versla je er een, dan laat hij soms een **Hydra-onderdeel** vallen. Dat heb je nodig voor
  de nieuwste tech: het raketpakket (zie Het harnas) en de torentjes (zie Torentjes).
- Zelf oproepen om te oefenen: het **Hydra Soldier Spawn Egg** in de Stark Tech-tab.

### Torentjes
Een **Stark Turret** verdedigt je basis. Craft hem met een **Hydra-onderdeel** in het midden,
twee Stark-chips ernaast, een dispenser erboven, een observer eronder en ijzer in de hoeken.
Zet hem neer: hij is van jou. De kop kijkt rustig rond; komt er een monster (ook raiddrones en
Hydra-soldaten) binnen zicht, dan draait hij erheen en blijft hij erop schieten tot het neer is.

- **Stroom**: elk torentje heeft stroom van het net nodig. Sluit hem aan met een Stark-kabel op
  een zonnepaneel of warmtegenerator (of zet die er direct tegenaan). Zonder stroom doet hij
  niets en hangt de loop omlaag (rechtsklik zegt dan "OFF (no power)").
- **Laser**: tot 16 blokken, 4 schade per seconde. Elk schot kost 60 stroom. Eén zonnepaneel
  houdt één torentje makkelijk bij.
- **Raketten**: klik rechts met **Rocket Ammo** (4 stuks uit een ijzerklompje-kruis met buskruit
  en redstone) of zet er een trechter boven. Dan gaat er om de 2,5 seconde ook een raket in een
  boogje op af, tot 24 blokken ver (20 stroom per raket). Er passen 64 in.
- **Op wie**: monsters. Spelers alleen als PvP aan staat, en nooit jij of je team. Dieren,
  dorpelingen, je huisdieren en E.D.I.T.H.-drones laat hij met rust. Wat hij neerhaalt telt
  als jouw buit.
- **Uitzetten**: een redstone-signaal (een hendel, of een E.D.I.T.H.-relais ernaast) zet hem
  uit; de loop hangt dan omlaag. E.D.I.T.H. kan hem ook zelf schakelen: hij staat in je
  redstone-lijst (toets **'**) onder **Turrets**, en werkt ook met je stem.
- **Hoe staat hij ervoor?** Klik rechts met een lege hand: aan/uit, stroom en raketten. Met
  shift haal je de raketten eruit.

In de Hydra-bunker staan **Hydra Turrets** (zwart met rood): die schieten op jou en je drones,
en ook zij draaien op stroom (van de reactor). Ook die gaan uit met redstone. Sloop je er een,
dan krijg je ijzer en soms een Hydra-onderdeel.

### De stroomkast
Een **Power Cabinet** (ijzer, twee hendels, een Stark-chip en twee Stark-kabel) is een punt waar
kabels van alle kanten samenkomen, met achter het deurtje **een schakelaar per kant** (voor,
achter, links, rechts, boven, onder). Alles wat aan de kanten hangt die aan staan, zit aan elkaar
vast; zet je een kant uit, dan krijgt wat daar aan hangt geen stroom meer uit de kast.
Bijvoorbeeld: generatoren aan de achterkant, torentjes links, lasers rechts.
- **Rechtsklik** opent het deurtje (iedereen die ervoor staat, kan schakelen). Per kant zie je
  een lampje, of er iets aan hangt ("wired"), en de schakelaar.
- **Link to E.D.I.T.H.** (alleen de eigenaar: wie hem neerzette, of in een Stark Tower wie de
  toren hackte) zet de zes schakelaars in je redstone-lijst (toets **'**), onder **Power
  cabinets**, zodat je ze op afstand of met je stem schakelt ("turn off cabinet 1 left").
- In de Stark Tower en de Hydra-bunker staat er een op een goed bewaakte plek.

### De server: je redstone programmeren
Met een **server** programmeer je de redstone van je basis: "houdt iemand een kaart van niveau 3
tegen de lezer, open dan de kluis 3 seconden; anders alarm". De server hangt aan je
**beveiligingskern** (Stark OS).
1. **Bouwen**: zet **Server Racks** binnen 30 blokken van je kern, naast of op elkaar: samen zijn
   ze één server, je **datacenter** (een los rack draait niet mee). Geef ze stroom met een kabel;
   racks geven de stroom aan elkaar door. Rechtsklik op een rack: zes vakjes, twee kolommen van
   drie. Stop er onderdelen in: een **CPU** of **GPU** (rekenkracht: de server reageert sneller,
   1 CPU = 0,5 s, 2 CPU's = 0,25 s; een GPU is half zo snel voor je redstone maar dubbel zo goed
   voor het brein van E.D.I.T.H.), **RAM** (hoeveel diagrammen tegelijk bezig kunnen zijn) en een
   **SSD** (hoeveel blokjes je kwijt kunt). Rekenkracht, RAM en een SSD: dan knipperen de lampjes.
   Elk rack met iets erin heeft ook minstens één **fan** nodig, anders wordt het te heet (zie
   hieronder, "Het datacenter").
2. **Apparaten**: zet relais (of laserzenders, torentjes, koepels) neer en geef ze met je bril een
   naam ("Vault door"): met die naam noem je ze in je diagram. Een **Keycard Reader** en een
   **Code Panel** aan de muur krijgen ook een naam ("Reader 1", of uit een aambeeld). Een andere
   naam geef je op de desktop in de app **Devices**: klik **Rename** bij de lezer of het paneel,
   typ de naam en druk Enter (Esc = laat maar). Daar zie je ook in hoeveel diagrammen hij staat:
   staat er "in no diagram" na een nieuwe naam, dan gebruikt je diagram nog de oude naam.
3. **Keycards**: craft lege kaarten en schrijf ze op de desktop in de app **Keycards** (naam en
   niveau 1-5; ze krijgen een eigen nummer). Een verloren of gestolen kaart **blokkeer** je daar.
   Geblokkeerde kaarten staan onderaan de lijst; met **Delete** (of **Delete blocked** voor allemaal
   tegelijk, twee keer klikken) gooi je ze echt weg, zodat de lijst niet vol en rommelig wordt. Het
   nummer van een weggegooide kaart komt nooit meer terug.
4. **Programmeren**: de app **Logic** op de desktop. Links je diagrammen, rechts de code; de knop
   **Blocks** toont hetzelfde als blokjes met draadjes (en **Code** weer terug). **Check** zoekt
   fouten, **Save** zet het op de server, **Help** toont alles wat je kunt gebruiken (klik erop en
   het komt in je code). **Ctrl+Spatie** in de code toont wat je op die plek kunt typen: aan de
   linkerkant de whens en `def`, in een blok de acties en je eigen defs, na `if` de gegevens die bij
   de trigger passen, en tussen aanhalingstekens de namen van je apparaten, lezers en panelen.
   Pijltjes kiezen, Enter of Tab vult in, Esc sluit; verder typen maakt de lijst korter. Na `card.`
   of `on("` gaat het lijstje vanzelf open.

De code lijkt op Python: wat ingesprongen staat (4 spaties), hoort bij de regel erboven.
```
when keycard("Lab door"):
    if card.valid and card.level >= 3:
        accept()
        pulse("Vault door", 3)
    else:
        deny()
        alarm("Wrong card at the lab")
```
- **Begin met** `when keycard("...")`, `when codepanel("...")`, `when turned_on("...")`,
  `when turned_off("...")` of `when every(10)` (elke 10 seconden).
- **Kies** met `if ...:`, `elif ...:` en `else:`, met `and`, `or`, `not` en `==`, `!=`, `<`, `>`,
  `<=`, `>=`. Je kunt kijken naar `card.level`, `card.valid`, `card.number`, `card.name`, `code`
  (tekst: `code == "4321"`), `player.trusted`, `player.owner`, `player.name`, `is_night`, `is_day`
  en `is_on("...")`.
- **Doe** `on(...)`, `off(...)`, `toggle(...)`, `pulse("...", 3)`, `wait(2)`, `accept()` en `deny()`
  (groen of rood lampje op de lezer of het paneel), `say("...")` (E.D.I.T.H. zegt het je),
  `log("...")` en `alarm("...")` (alarm, en de beveiliging jaagt op wie het deed).
- **Eigen blokken** (functies): schrijf stappen één keer met `def naam():` (aan de linkerkant, de
  stappen eronder) en gebruik ze in zoveel whens als je wil met `naam()`. Zo laten een kaart en een
  code dezelfde deur opengaan:
  ```
  def open_lab():
      accept()
      pulse("Lab door lock", 3)

  when keycard("Lab door"):
      if card.valid and card.level >= 3:
          open_lab()

  when codepanel("Lab panel"):
      if code == "4321":
          open_lab()
  ```
  Een def mag andere defs gebruiken, maar niet zichzelf. Gebruikt hij `card.level` of `accept()`,
  dan werkt hij alleen in een when die daarbij past (Check zegt het je). In de blokjes is een def
  een paars **DEF**-blok met de stappen erachter, en **use def** voert hem uit.

Neemt iemand je kern over, dan krijgt hij ook je diagrammen en kaarten. De code van een codepaneel
staat alleen in je diagram, niet in het logboek.

### Het datacenter: warmte, backups en het brein
Je racks samen zijn je **datacenter**. Het wordt warm, je kunt aflezen hoe hard het werkt, je maakt
er backups van, en het brein van E.D.I.T.H. draait erop.

- **Warmte, per rack**: je CPU's, GPU's, RAM en SSD's werken samen als één server, maar **elk rack
  heeft zijn eigen temperatuur**. Elk onderdeel maakt warmte in zijn eigen rack (een CPU 2 W, een
  GPU 3 W, meer als ze hard werken; RAM en SSD's een halve watt). Een **Server Fan** (recept: ijzer,
  ijzeren tralies, redstone = 2 fans) koelt 6 W weg, maar **alleen in zijn eigen rack**: een fan in
  het rack ernaast helpt niet. **Zonder fan raakt de warmte er niet uit**: dan wordt het rack steeds
  warmer, ook als er alleen RAM of een SSD in zit (dan wel langzaam). Dus: elk rack met iets erin
  heeft minstens één fan nodig. Met fans gaat de temperatuur in ongeveer een minuut naar 25° plus
  8° voor elke watt die de fans niet wegkoelen. Het scherm van een rack toont de temperatuur van
  dát rack (**RACK TEMP**, nu en waar hij heen gaat, of "rising" zonder fan), **HEAT/COOL**, en
  hoeveel fans er nog in moeten ("Add 2 fans here").
- **Te warm** gaat stap voor stap, per rack. Is één rack warmer dan **70°**, dan werkt de hele
  server op halve snelheid (alles werkt samen, dus het warme rack remt de rest af) en waarschuwt
  E.D.I.T.H. je, met waar dat rack staat. Vanaf **90°** slijten de onderdelen in dat rack (een
  balkje onder het icoon) en gaan ze één voor één kapot. Blijft een rack **10 seconden boven
  110°**, dan vat het vlam: je hebt 10 seconden om je onderdelen eruit te halen, dan is het weg met
  alles erin.
- **Een SSD kapot of verbrand = alle diagrammen en keycards weg** (ze staan verspreid over al je
  SSD's). Een SSD er gewoon uithalen of een rack afbreken mag wel. Je bestanden, notities en je
  wachtwoord staan in de kern zelf en blijven.
- **Aflezen**: de app **Monitor** op de desktop, zoals Taakbeheer: CPU, BRAIN, RAM, SSD en TEMP (het
  heetste rack) met een grafiekje van de laatste minuut, elk rack met zijn onderdelen (rood streepje
  = versleten), zijn eigen temperatuur en hoeveel fans er nog in moeten ("+1 fan"), wat je moet doen
  tegen de warmte ("Fans: #2 +1"), en welke diagrammen er nu bezig zijn.
- **Backups**: bouw een tweede datacenter (een andere kern met racks, ver genoeg weg). In de app
  **Backup** kies je met **<** en **>** waar de backups heen gaan, dan **Backup now**, of
  **Automatic** elke 5, 15 of 60 minuten. Een backup neemt plek op de SSD's van dat andere
  datacenter (daar blijven de laatste 3). Na een brand zet **Restore** je diagrammen en keycards
  terug (twee keer klikken). Brandt het andere datacenter af, dan zijn de backups daar ook weg.
- **Het brein van E.D.I.T.H.** draait op je sterkste datacenter, waar het ook staat. **Zonder
  datacenter werkt er geen enkel brein**, ook het woordenboek niet: dan is het gewoon een mooie
  bril (in creative hoeft het niet). Met een datacenter duurt een antwoord de tijd van het model
  plus een vertraging zolang je datacenter nog niet sterk is: 1 CPU +8 s, 1 GPU +4 s, 4 GPU's +1 s,
  vanaf 8 GPU's niets meer (warm: twee keer zo lang). Rechtsboven in **G > Systems > AI Brain** zie
  je op welk datacenter het draait en hoeveel het erbij zet. Terwijl ze nadenkt, werkt je
  datacenter hard (en wordt het warmer).

Alle getallen staan in `config/starktech-balance.toml` onder `[logic]`.

### Je basis beveiligen: zones en C4
In Minecraft kan iedereen elk blok weghakken. Met een **zone** kunnen in je basis alleen jij en je
vrienden nog bouwen en breken. Om toch te kunnen inbreken (moeilijker, niet onmogelijk) is er **C4**.

**Een zone tekenen.** Een zone hoort bij je **beveiligingskern** en bestaat uit één of meer
**kubussen**, tot op het blok precies (een L-vorm, een toren die hoger is dan de rest...). Ze moeten
binnen het gebied van je kern liggen (30 blokken opzij, 60 onder en 55 boven de kern); hooguit 16.
- Met de **Zone Marker** (recept: een redstonefakkel, een Stark-chip en een stok onder elkaar):
  **linksklik** op een blok = hoek 1, **rechtsklik** = hoek 2 (je ziet een oranje doos en hoe groot hij
  is), **sluipen + rechtsklik** = die doos wordt een kubus van je zone. **Sluipen + linksklik** in een
  kubus (twee keer) haalt hem weg. Met de marker in je hand zie je je zone altijd, met een nummer
  boven elke kubus.
- In de app **Zone** op de desktop van je kern (ook via SSH): een kaart van bovenaf, en per kubus de
  getallen van de twee hoeken (typen + Enter) en per kant een **−** en **+** om die kant één blok te
  verschuiven. **New box** zet een kubus van 7 × 5 × 7 waar je staat.

**Wie mag wat.** In een zone mogen alleen **jij, je team en je vertrouwde spelers** (de app Access)
blokken plaatsen en breken; in creative mag iedereen. Een vreemde kan er niets breken of neerzetten;
hij ziet dan "Protected zone". Wel mag iedereen **ladders, scaffolding, klimplanten, fakkels en
lantaarns** plaatsen en weer weghalen (klimmen kan in het echt ook), en natuurlijk C4. Ook mag iedereen
een **emmer leeggieten of vullen, maar alleen op de grond** (niet tegen een muur: zo klim je niet met
water omhoog), **vuur aansteken**, een **bijl of schoffel** op een blok gebruiken, en **Stark-kabels
leggen of knippen** (verstop je kabels dus in een muur of vloer). Een pad maken met een schep en
beendermeel mogen niet. Al de rest is gewoon
bruikbaar, net als in het echt: wie binnen is, kan kisten openen, deuren gebruiken en stelen. Een
houten deur is dus geen slot; een ijzeren deur met een keycard-lezer of codepaneel buiten wel.

**Wat er nog meer doorkomt.** **TNT** en de raketten van het **satellietbombardement** wel, maar zwak:
ze werken in een zone met breekpunten zoals C4 (TNT 3, een satellietraket 6, C4 10). TNT naast een
houten muur breekt er een plank uit; een stenen muur krijgt barsten en breekt pas na drie TNT op
dezelfde plek. Creepers, vuurballen en andere explosies breken geen blokken van een zone (je eigen
TNT in je eigen zone wel gewoon). Vuur brandt ze niet weg en lava steekt ze niet aan; een
zuiger van buiten duwt of trekt er niets in of uit; monsters (endermen, withers, ravagers, zombies
bij deuren) en raiddrones breken er niets.

**De rand.** Jij en je vrienden zien de rand van de zone **door de E.D.I.T.H.-bril**: doorzichtige
blauwe wanden met een raster, tot zo'n 48 blokken ver (rood als de zone geen stroom heeft). Zonder
bril zie je hem niet, en een vreemde ziet hem nooit. Aan/uit voor jezelf: **G > Information > Zone
border** (of een eigen toets). De oranje doos van de Zone Marker zie je ook zonder bril.

**Stroom.** Een zone kost stroom bovenop de kern: 1 per 2000 blokken, om de twee seconden. Zonder
stroom staat de zone uit (E.D.I.T.H. waarschuwt je). Zet je generatoren en kabels dus **in** je zone,
anders kan een inbreker ze van buitenaf kapotmaken. Wie je kern hackt, krijgt ook je zone.

**C4.** Je plakt C4 op een vloer, muur of plafond, ook in de zone van een ander.
- **C4 (Timer)** (TNT, twee klei, een slijmbal, redstone en een klok): rechtsklik in de lucht om de
  tijd te kiezen (5 seconden tot 2 minuten). Na het plakken telt hij af op zijn schermpje en piept hij
  steeds sneller.
- **C4 (Remote)** (TNT, twee klei, een slijmbal, redstone en een bliksemafleider) met de
  **Detonator** (een bliksemafleider, ijzer, een Stark-chip, ijzer en een knop): heb je de detonator
  bij je als je plakt, dan hoort de lading erbij. **Rechtsklik** met de detonator = al zijn ladingen
  binnen 128 blokken gaan af; **sluipen + rechtsklik** = hoeveel er gekoppeld zijn. Met de detonator in
  je hand zie je je ladingen als rode bordjes.
- **Sterke muren kosten meer C4**: hout, glas en aarde vliegen er meteen uit, één C4 maakt een gat
  van drie hoog in een stenen muur, obsidiaan breekt pas na drie C4 op dezelfde plek, de
  beveiligingskern na zes. Wat niet breekt krijgt **barsten** (de volgende klap telt erbij op). Op
  obsidiaan zie je die barsten bijna niet. Andere C4 in de klap gaat ook af.
- **Barsten herstellen**: barsten genezen niet vanzelf, ook niet na een herstart. Vul ze op met
  **Repair Paste** (twee klei, zand en grind = 4 pasta): rechtsklik op het blok, en de barsten zijn
  weg. Maar het blok is daarna wel 15% zwakker, elke keer opnieuw (nooit minder dan 40%). In de zone
  van een ander kan dat niet. Een nieuw blok op die plek is weer helemaal sterk.
- **Gebarsten blokken**: hak je een blok met barsten (of een geplamuurd blok) weg, dan houd je een
  **gebarsten blok** over ("Cracked Stone Bricks"). De **Crusher** (ijzer, een slijpsteen, een
  Stark-chip en een zuiger) maakt daar op Stark-stroom **poeder** van: 5 seconden per blok. De
  **Compactor** (ijzer, een aambeeld, twee zuigers en een Stark-chip) perst dat poeder tot een
  volledig nieuw blok van hetzelfde soort, weer helemaal sterk. Bij allebei doen trechters er van boven
  of opzij iets in en halen ze het er van onder uit.
- **Ontmantelen**: alleen met een **schaar** (rechtsklik): de C4 valt eruit, en jij hebt hem. Met je
  hand krijg je hem er niet af.
- **Alarm**: plakt iemand C4 op jouw zone, dan zegt E.D.I.T.H. waar (niet wie), en jij en je vrienden
  zien een knipperend rood bordje "C4!" tot hij weg is. Hoeveel er ontploft is, staat in het logboek
  van je kern. Was je offline, dan vertelt E.D.I.T.H. het als je weer inlogt.

Alle getallen staan in `config/starktech-balance.toml` onder `[c4]` en `[security]`.

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
  Hydra-onderdeel), **rechts de reactorkamer**: twee warmtegeneratoren in een bak lava, en in de
  hoek de **stroomkast**, bewaakt door een soldaat en een torentje. De kast heeft een schakelaar
  voor de laserwanden (boven) en een voor alle Hydra-torentjes (onder; hun kabels lopen door de
  vloer van de hele bunker).
- **De kluisgang** met twee rode laserwanden. Die krijgen hun stroom van de reactor, via de
  kabel onder het plafond van de hal. **Zet ze uit in de stroomkast, sloop de generatoren of
  knip de kabel door**, en ze gaan uit. (Of graaf je eromheen: het is een berg.)
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
  pakket 12 seconden herladen (links in de HUD: ROCKETS). Munitie is er niet. Meer raketten of
  sterkere raketten? Bouw een **Rocket Magazine** of **Rocket Warhead** in (zie de werkbank).

### De harnas-werkbank (Tony's lab)
Craft een **Suit Workbench** (Stark-chips, een redstoneblok, een crafting table, een aambeeld en
ijzer), of gebruik die in het lab van een Stark Tower. Hij heeft **stroom** nodig: een Stark-kabel
naar een zonnepaneel of warmtegenerator, of er direct tegenaan.

Er is **geen scherm**: boven de werkbank zweeft het harnas als een blauw **hologram** dat langzaam
ronddraait (zonder stroom flikkert het). **Kijk naar een stuk** (borstplaat, broek of laarzen): het
licht op, en naast je vizier staat een kaartje met wat een **rechtsklik** zou doen. Met **shift**
ingedrukt doet rechtsklikken weer het gewone.

- **Bouwen** (lege hand, of iets anders dan harnas of upgrades): klik op een stuk dat nog een
  hologram is. Het kaartje laat zien wat het kost en wat je bij je hebt (groen = genoeg, rood = te
  weinig). De materialen komen uit je **rugzak** (je hotbar, de rest van je inventaris en je tweede
  hand; in creative is het gratis). Het stuk bouwt zich plaatje voor plaatje op in het hologram,
  met een scanring die op en neer gaat. Klaar? Dan **vliegt het naar je toe** en gaat het meteen
  aan (heb je daar al iets, dan komt het in je rugzak). Ben je intussen verder dan 24 blokken weg,
  dan blijft het op de werkbank liggen. Het harnas kun je niet in de crafting table maken.
- **Neerleggen**: houd een stuk harnas vast en klik op het hologram: het ligt dan echt (in zijn
  eigen kleuren) op zijn plek. Lag daar al een stuk, dan wisselen ze.
- **Pakken**: klik met een lege hand op een stuk dat erop ligt: het vliegt naar je toe. Het kaartje
  laat zien welke upgrades erin zitten.
- **Upgraden**: houd een upgrade vast en klik op het stuk (dat moet erop liggen). Het kaartje zegt
  of hij past. Het item draait om het stuk en krimpt erin weg, en een ring loopt vol. De upgrades
  (te craften met Stark-chips, en soms te vinden in de kisten van een Stark Tower):
  - **Thruster Upgrade** (in de laarzen, tot 3): sneller vliegen en minder energie.
  - **Armor Plating** (in elk stuk, tot 2 per stuk): het harnas houdt meer schade tegen.
  - **Repulsor Capacitor** (in de borstplaat, tot 2): een sterkere repulsor.
  - Het **raketpakket** kan hier ook in de borstplaat (of met een rechtsklik, zie boven).
  - **Rocket Magazine** (borstplaat met raketpakket, tot 3): 2 raketten meer per salvo (tot 12).
    Craft: ijzer, buskruit, Stark-chips en een trechter.
  - **Rocket Warhead** (borstplaat met raketpakket, tot 3): 40% meer schade per raket. Craft:
    ijzer, blazepoeder, Stark-chips en TNT.
  In de tooltip van een stuk harnas zie je ook welke upgrades erin zitten.
- De werkbank doet één ding tegelijk. Breek je hem af, dan vallen alle stukken eruit, en ook de
  upgrade of de materialen van wat hij aan het maken was.
- Een werkbank uit een oudere versie (met vakjes) legt het stuk uit zijn werkvak op het hologram
  en laat de rest van zijn spullen eruit vallen.

### Landingsplatform
Een **Landing Pad** zet je neer en is van jou (het platform op het dak van een Stark Tower is van
wie de toren gehackt heeft). Tijdens het vliegen wijst de HUD je dichtstbijzijnde platform aan;
land je erop met de stuwraketten aan, dan zegt E.D.I.T.H. "Welcome home". Zolang je erop staat
(5 x 5 blokken), laadt je bril op en wordt je harnas gerepareerd.
- **Rechtsklik** opent zijn scherm: een eigen **batterij**. Die vult het platform langzaam (300 per
  seconde) met wat het net **over** heeft (net als het laadstation laat het de helft van de
  generatoren voor de rest). Sta je erop, dan laadt hij je bril eerst uit die batterij (1600 per
  seconde) en pas daarna rechtstreeks uit het net (400 per seconde).
- In het vakje **Cells** passen tot 4 **Battery Upgrades**: elke maakt de batterij 15.000
  groter (standaard 10.000). Breek je het platform af, dan vallen ze eruit.

### Holotafel
Craft een **holotafel** (getint glas, amethist, een Stark-chip en ijzer) en zet hem neer. Hij
projecteert een hologram boven zich dat langzaam ronddraait (als hij **stroom** heeft; zonder
stroom knippert er alleen een rood ringetje). Je hebt er **geen bril** voor nodig, behalve voor
wat van E.D.I.T.H. is: je **waypoints** op de kaart, en de standen **Arsenal** en **Satellite**.
Zonder bril staat daar een rood kruis met "E.D.I.T.H. glasses required" (iedereen ziet het
volgens zijn eigen bril). Bedienen:

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
  en feller het staafje. Je waypoints staan er als gekleurde paaltjes in (met de bril op), en het leeft:
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
Let op: E.D.I.T.H. kan alleen nadenken met een **datacenter** (server racks bij je
beveiligingskern, zie "Het datacenter"); zonder luistert ze niet. In creative hoeft het niet.

---

## 6. Waar staan de instellingen?

| Bestand | Wat |
|---------|-----|
| `config/starktech-balance.toml` | **Alle getallen** (ruim 140): kosten, schade, bereik, wachttijden, verbruik, per onderdeel (bril, drones, satelliet, harnas, torentjes, Hydra...) |
| `config/starktech-server.toml` | Max drones, `drones.orbitHeight` (hoe hoog ze in- en uitvliegen), raids, hacken aan/uit |
| `config/starktech-client.toml` | Stem, volume, spraaktekst, Spotify Client ID, GPU voor de AI, het zachte zoemen van de rayshields (`rayshieldHumVolume`: 1 = normaal, 2 = dubbel zo hard, 0 = uit), de rand van zones (`zoneBorder`) |
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
  `rockets`, `turrets`, `menu`, `bunker`, `missile`, `padscreen`, `cabinet`, `security`, `hack`, `os`, `cameras`, `towercams`,
  `racks`, `keycards`, `logic`, `lab`; meerdere met komma's). Op een ander
  schermformaat: `-Psize=1600x900`.
- **Geluiden**: `python tools/generate_sounds.py` maakt de eigen geluiden (repulsor,
  stuwraketten, rayshields) opnieuw. Eenmalig nodig: `pip install numpy scipy soundfile` (op deze
  pc heeft `py -3.12` ze al: `py -3.12 tools/generate_sounds.py rayshield_hum`).
