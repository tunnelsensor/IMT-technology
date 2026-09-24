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

Alles testen dat op de server gebeurt: `gradlew runGameTestServer` (48 automatische tests).

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
  **warmtegenerator** neer. In het station bouw je ook upgrades in (batterij, scanner, drones).
- Zoek een **Stark Tower** in je wereld: een wolkenkrabber van 29 x 76 blokken met een lobby,
  een lab, een archief, een hangar, een penthouse en een landingsdek op het dak.
  In de gewone kisten vind je materiaal en upgrades — maar **geen bril**.
  De bril ligt in de **kluis** bovenin, en die geeft zich niet zomaar gewonnen:
  eerst door de lasergang (zes rayshields waar je niet doorheen komt), dan de omweg
  via drie deuren met verstopte schakelaars, en in de kluis zelf staan vier bewakers.
  Stap je op de plaat voor de kist, dan gaat de deur achter je dicht.

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
| **K** | Satelliet-aanval |
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

Alle toetsen kun je aanpassen: **Opties > Besturing > E.D.I.T.H.**

---

## 4. Het G-menu

| Tabblad | Wat erin zit |
|---------|--------------|
| **Gevecht** | Repulsor, rayshield, drones, één drone, escorte, satelliet + stand, satellietscherm (upgrades), stand down, markeringen wissen |
| **Drones** | Arsenaal, drone-camera, spionagedrone, meekijken, rapport |
| **Transport** | Zelf vliegen, autopiloot, vrachtdrone, iemand vervoeren |
| **Tech** | Redstone-relais, muziek (Spotify), hacken |
| **Illusie** | Valse muur, nep-leger, onzichtbaar veld, stoppen |
| **Scanner** | Ertsen-scan, nachtzicht, doelwit-analyse, dreigingsscan |
| **Navigatie** | Waypoints, ping, comms |
| **Systemen** | Eigendom overdragen, stem, spraakbesturing, commando's bewerken, spraaktekst aan/uit |

---

## 5. De grote functies

### Het drone-arsenaal
Drones komen niet meer uit het niets: je moet ze **bouwen**. Craft een **Stark-drone**
(een Stark-chip, 5 ijzer, 2 redstone en een amethist) en gebruik hem: hij schiet de lucht in
naar een baan om de planeet. Daar wacht hij tot je hem nodig hebt, waar je ook bent.

Alles wat drones gebruikt — de zwerm, de escorte, de verkenner, de spion, transport, vracht
en de illusies — haalt ze uit dat arsenaal. Hangt er niets meer, dan vertrekt er niets, en zegt
E.D.I.T.H. dat (je raakt dan geen energie kwijt).

Open het arsenaal met **G > Drones > Arsenaal**. Per drone zie je hoe heel hij nog is en waar
hij uithangt:

- **In orbit** — klaar voor gebruik. Met **Recall** komt hij uit de lucht vallen en landt hij
  naast je.
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
Ze komen **van heel hoog** aanvliegen: standaard 96 blokken boven je, in een duik met een
streep vonken achter zich aan. Zit er een plafond boven je (een grot, een kelder), dan komen ze
zo hoog als er ruimte is. Klaar met hun werk? Dan stijgen ze net zo hoog weer op en verdwijnen
in de lucht — ze lossen niet ter plekke op. Pas als ze boven zijn, staan ze weer klaar in je
arsenaal.

Hoe hoog precies staat in de config (`drones.orbitHeight`). Hoeveel drones er maximaal in een
zwerm passen staat daar ook (standaard zwerm 16 en escorte 8) — maar je arsenaal bepaalt
hoeveel het er echt worden.

- **Zwerm** (J): valt je gemarkeerde doelwitten aan, of verdedigt je.
- **Eén drone** (L): goedkoop, jaagt op één doelwit tot het dood is.
- **Escorte**: vliegt met je mee en schiet op wat jou aanvalt.
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

### Satelliet (K)
Drie standen (kies in Gevecht > Satelliet-stand):
- **Precisie** — veel schade, **geen** blokschade. Heb je vanaf het begin.
- **Bombardement** — een regen van inslagen, flinke kraters. Eerst vrijspelen.
- **Schuivende laser** — een streep die van je af over de grond kruipt. Eerst vrijspelen.
Hij volgt bewegende doelwitten (lock-on), richt tot 500 blokken, en je kunt ook op je waypoint
of je laatste ping mikken. Bij de inslag komt er een schokgolf en schudt je beeld.

**Upgraden** doe je in het satellietscherm (G > Gevecht > Satellite). Zes onderdelen, elk tot
niveau 10: de drie standen (vrijspelen, dan sterker), opladen (kortere wachttijd), kracht (meer
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

### Rayshield
- **Persoonlijk schild** (Gevecht): vangt pijlen, vuurballen, explosies en lasers op. Elke
  treffer kost energie; batterij leeg = schild weg.
- **Laserzender** (volledig blok met een lens aan de voorkant): zet er twee tegenover elkaar
  → een doorzichtig energieveld met lopende scanlijnen waar niemand door komt. Het veld ligt
  als één rechte wand tussen de twee lenzen, en schuift dicht vanaf de uiteinden naar het
  midden (en weer open vanuit het midden), net als de ray shields uit Star Wars.
  **Rechtsklik** bladert door de kleuren: blauw, cyaan, groen, geel, rood, paars.
  Aan met redstone. Wil je hem met E.D.I.T.H. schakelen, zet er dan een relais naast.
- **Schildkoepel-generator** (blok): koepel van 20 blokken die pijlen laat verdampen en
  monsters naar buiten duwt. Spelers en dieren mogen erdoor.

### Illusies (cloakingdrones)
Een onzichtbare drone projecteert een beeld in een bol van 16 blokken. Alles is **nep**: in de
wereld verandert niets.
- **Valse muur** — van het blok dat je in je hand houdt.
- **Nep-leger** — van het spawn-ei dat je in je hand houdt.
- **Onzichtbaar veld** — alles in het veld wordt niet meer getekend.

Een muur of leger **richt je eerst**: een drone vliegt van je weg en jij kijkt door hem heen
(WASD, spatie en shift om te vliegen). Waar je naar kijkt, zie je de illusie als voorvertoning
in een blauw kader; de muur draait mee zodat hij dwars op je kijkrichting staat.
**Linkermuisknop** zet hem neer, **rechtermuisknop** annuleert (de drone gaat terug).

Zwakke plekken (net als in de film): waar twee velden overlappen gaat het beeld **flikkeren**,
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
- **Blokken slopen uitzetten**: `/gamerule starktechRaidGriefing false`. Dan kunnen ze nog wel
  schieten, maar laten ze je muren heel.
- **Instellen per wereld** in `<wereld>/serverconfig/starktech-server.toml`: `raids.enabled`,
  `raids.minMinutes`, `raids.maxMinutes` en `raids.size` (hoeveel drones per golf, standaard 4).

### Prestaties (advancements)
De mod heeft een eigen prestatieboom die je door het spel leidt, van je eerste Stark-chip tot
vliegen met het harnas. Open je prestaties (standaard **L**) en kijk bij **Stark Industries**.

Sommige krijg je vanzelf als je iets maakt; andere kent de mod zelf toe op het moment dat het
gebeurt — je eerste drone de ruimte in sturen, een satellietaanval, een drone verliezen, met het
harnas vliegen.

### Het harnas
Craft een **borstplaat**, **broek** en **laarzen** (netherite, Stark-chips, amethist). Er is geen
helm: die plek is van de bril, en dat is de bedoeling — **zonder de bril doet het harnas niets
bijzonders**. Het is dan gewoon een goed pak metaal. De bril levert de stroom; het harnas heeft
geen eigen batterij.

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
  batterij leeg, dan val je gewoon uit de lucht — hou hem dus in de gaten.
- **Landen**: de laarzen vangen elke val tot 40 blokken helemaal op.
- **Repulsor** (toets **I**, of G > Gevecht): zet de repulsor **aan of uit**. Staat hij aan,
  dan is je aanvalsknop (linkermuisknop) de repulsor: klikken vuurt, ingedrukt houden vuurt
  elke seconde. Slaan en blokken breken gaat dan niet; zet hem uit om weer te hakken. Rond je
  vizier staat een ring die na elk schot weer volloopt. Een stoot raakt alles op zijn weg en
  duwt het een stukje weg, tot 24 blokken ver. Je hebt de borstplaat nodig (daar zit de
  reactor) en het kost 200 energie per stoot.

### Holotafel
Craft een **holotafel** (getint glas, amethist, een Stark-chip en ijzer) en zet hem neer. Hij
projecteert een hologram boven zich dat langzaam ronddraait, en je bedient hem met de bril op:

| Wat je doet | Wat er gebeurt |
|---|---|
| **Rechtsklik** (lege hand) | Het instelscherm |
| **Rechtsklik met een voorwerp** | Laat het recept van dat voorwerp zien |
| **Sluipen + rechtsklik** | Meteen de volgende kleur, zonder scherm |

In het **instelscherm** kies je wat hij laat zien, de kleur (dezelfde zes als de rayshields),
hoe ver de kaart reikt (24, 48, 96 of 160 blokken), hoe groot het hologram is (klein, normaal,
groot) en of hij ronddraait. Je ziet het hologram achter het scherm meteen veranderen, dus er
is geen opslaan-knop. Met **Take off** haal je het voorwerp weer van tafel.

Drie standen:

- **Map** — een reliëfkaart van 96 bij 96 blokken rond de tafel: hoe hoger de grond, hoe hoger
  en feller het staafje. Je waypoints staan er als gekleurde paaltjes in, en het leeft:
  spelers zijn witte stipjes, vijanden rode, dieren groene.
- **Blueprint** — het recept van wat je op tafel legt, als zwevend 3x3-rooster met het
  resultaat erboven. Mag een onderdeel uit een hele groep komen (bijvoorbeeld "een ijzerstaaf"),
  dan wisselen de mogelijkheden elkaar af. Bestaat er geen recept (zoals bij de bril), dan zie
  je een rood kruis.
- **Arsenal** — je drones cirkelen om een bolletje. Groen is heel, oranje heeft een tik gehad,
  rood is bijna op; een drone die onderweg is knippert.

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
Via **G > Systemen** installeer je de stem (Piper) en de spraakbesturing. Push-to-talk is **Z**.
Praat E.D.I.T.H. terwijl jij begint te praten, dan zwijgt ze meteen. In **G > Systemen >
Spraaktekst** zet je aan of uit of je in de HUD ziet wat je zei en wat zij antwoordt.
Alle zinnen kun je zelf aanpassen in **G > Systemen > Commando's** (of in
`config/starktech-voice.json`).

---

## 6. Waar staan de instellingen?

| Bestand | Wat |
|---------|-----|
| `<wereld>/serverconfig/starktech-server.toml` | Max drones, `drones.orbitHeight` (hoe hoog ze in- en uitvliegen), raids, hacken aan/uit |
| `config/starktech-client.toml` | Stem, volume, spraaktekst, Spotify Client ID, GPU voor de AI |
| `config/starktech-voice.json` | Alle spraakcommando's (zelf uit te breiden) |
| `config/starktech-spotify.json` | Je Spotify-token (niet delen) |
| `src/main/java/.../edith/EdithBalance.java` | Alle getallen: kosten, schade, bereik, wachttijden |

---

## 7. Alles in één keer bekijken: de showcase

```
python tools/showcase.py
```

Het script vraagt eerst `Wil je een pauze tussen elke functie (Y/n):`. Druk gewoon op Enter
voor ja (wat in HOOFDLETTERS staat is de standaard), of typ `n`.

Daarna start het spel vanzelf in een testwereld (dat duurt een halve tot een hele minuut) en
laat het elke functie van de mod zien, van het opzetten van de bril tot de Stark Tower. In je
terminal staat bij elke functie de naam en waar je op moet letten; wat E.D.I.T.H. zegt,
verschijnt er ook.

Met pauze wacht het script na elke functie op jou:

| Wat je typt | Wat er gebeurt |
|---|---|
| Enter | volgende functie |
| `r` | deze functie nog eens |
| `q` | stoppen (het spel sluit ook) |
| een getal, bv. `12` | naar die functie springen |
| iets anders | wordt bewaard als notitie bij deze functie, in `showcase-notities.md` |

Beginnen bij een bepaalde functie: `python tools/showcase.py --vanaf 30`.

Het spel draait in de map `run-showcase`, los van je eigen werelden. Je hoeft er niets in te
doen, maar je mag wel rondkijken. Een nieuwe functie gebouwd? Zet hem in
`client/dev/ShowcaseFeatures.java`, dan zit hij in de showcase.

---

## 8. Zelf verder bouwen

- **Getallen aanpassen** (sterker/zwakker): `EdithBalance.java`, dan `gradlew build`.
- **Textures**: `python tools/generate_textures.py` maakt ze allemaal opnieuw. Teken je liever
  zelf? Teken dan over het bestand heen en draai het script niet meer.
- **De toren verbouwen**: `python tools/generate_structure.py`, of bouw hem in het spel met een
  structuurblok en sla hem op als `starktech:stark_tower`.
- **Tests**: `gradlew runGameTestServer` — 48 tests over de toren en zijn kluis, de drones en
  het arsenaal, zelf vliegen en landen, de satelliet, navigatie, spion, de bril, het
  laadstation, de rayshields, de relais, de holotafel, het harnas en de spraakcommando's.
- **Screenshots van alles**: `gradlew runVisualCheck` maakt vanzelf een testwereld, fotografeert
  alle schermen, de holotafel, het harnas, een testvlucht en de satelliet, en sluit weer af. De
  plaatjes staan in `run-visual/screenshots`. Alleen een stuk ervan: `gradlew runVisualCheck
  -Ponly=satellite` (of `boot`, `holo`, `screens`, `hud`, `suit`, `flight`, `drones`; meerdere
  met komma's).
- **Geluiden**: `python tools/generate_sounds.py` maakt de eigen geluiden (repulsor,
  stuwraketten) opnieuw. Eenmalig nodig: `pip install numpy scipy soundfile`.
