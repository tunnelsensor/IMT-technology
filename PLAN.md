# Stark Tech — plan en voortgang

Alles wat we besproken hebben, op één plek: wat af is, wat er nog komt, en wat er nog open
staat. Bijwerken zodra er iets verandert.

Afspraken die overal gelden:

- **Eén functie tegelijk**, en committen zodra hij werkt.
- **Alles in het spel is Engels.** Commentaar in de code en documenten zoals dit mogen
  Nederlands zijn; die zie je niet in het spel.
- Blijft op **Minecraft 1.21.1 + NeoForge**, alleen **Windows**, voorlopig **privé**.
- Moet vooral **werken**, maar uitleg mag altijd.
- Je speelt zelf, maar het moet **met vrienden op een server** kunnen: iedereen heeft zijn
  eigen bril, arsenaal en spraakcommando's.
- E.D.I.T.H. mag **overpowered** zijn; de moeilijkheid zit in het verkrijgen ervan.

---

## Afgerond

### Polijsten

- [x] **Alles Engels in het spel** — `47a7d4b`
      De taalbestanden waren al Engels, maar er lekte nog Nederlands door via het
      spraakwoordenboek (6 zinnen, één met een kapot teken), de Spotify-pagina in je browser,
      foutmeldingen in de HUD en het installatiescherm. Ook de muziekzoeker verstond nog
      Nederlands ("van" als artiest-woord).
      Twee bugs onderweg: een opgeslagen `starktech-voice.json` werd eeuwig hergebruikt omdat
      de versie wel werd geschreven maar nooit gelezen, en `split("\s+")` is in Java 15+ geen
      regex maar de escape voor een spatie.

- [x] **HUD opgesplitst** — `b1f8260`
      1084 regels in één klasse → een orkestrator van 124 regels plus 16 losse widgets in
      `client/hud/`. De schermen halen hun kleuren nu uit `HudStyle` en niet meer uit de HUD.
      Er verandert niets op je scherm.

- [x] **Bril alleen nog uit de Stark Tower** — `4bcffc6`
      Geen recept meer. De enige bril in je wereld ligt in de kluis. De ingrediënten blijven
      bestaan voor al het andere.

- [x] **Redstone geeft minder energie** — `4bcffc6`
      50 per stuk in plaats van 500 (blok: 480). Een volle bril kost nu ongeveer twee stapels,
      dus redstone is je noodvoorraad en het zonnepaneel of de warmtegenerator je echte stroom.

### Fase 15

- [x] **Drone-arsenaal in een baan om de planeet** — `8fedd02`
      Je bouwt drones (Stark-chip, 5 ijzer, 2 redstone, amethist), gebruikt ze, en ze vliegen
      naar hun baan. Eén arsenaal, één soort drone die alles kan, oneindig groot, van jou
      persoonlijk en overal bereikbaar. Alles wat drones gebruikt (zwerm, escorte, verkenner,
      spion, transport, vracht, illusies) haalt ze daaruit; leeg arsenaal betekent dat er niets
      vertrekt, zonder dat het je energie kost.
      Schade blijft bewaard. Neergehaald is weg, met ~10% van de bouwkosten als schroot.
      Arsenaal-scherm op **G > Drones > Arsenaal** met de HP van elke drone.
      Onderweg gevonden: drone-entities sloegen helemaal niets op, dus een drone die een
      herstart overleefde was zijn eigenaar kwijt.

- [x] **Teruggeroepen drone landt naast je** — `379478d`
      Rechtsklik om te repareren met ijzer uit je rugzak, shift-rechtsklik om hem weer omhoog
      te sturen.

- [x] **Van heel hoog in- en uitvliegen** — `379478d`
      96 blokken in plaats van 24, in een duik met vonken. Bij vertrek stijgen ze net zo hoog
      weer op in plaats van ter plekke op te lossen.
      Onderweg gevonden: `EdithDrones.find()` keek maar 80 blokken ver, dus "stand down" miste
      een drone die nog aan het invallen was. De configsleutel heet nu `drones.orbitHeight`,
      anders hield een bestaande wereld zijn oude 24.

- [x] **Holotafel** — `47b99c1`
      Een tafel die een hologram boven zich projecteert, in een kleur die je zelf kiest
      (dezelfde zes als de rayshields, met sluipen + rechtsklik).
      Drie standen: een reliëfkaart van de omgeving met je waypoints, spelers en vijanden erop;
      de bouwtekening van het voorwerp dat je op tafel legt; en je drone-arsenaal dat om een
      bolletje cirkelt. Zonder bril doet hij niets.
      Mogelijke uitbreiding: een stand die de ertsen in de buurt laat zien.

**Stand van zaken:** 29 automatische tests, allemaal groen (`gradlew runGameTestServer`).

---

## Nog te doen, in deze volgorde

### 1. Het harnas

- [ ] Borstplaat, broek en schoenen (geen helm — daar zit de bril)
- [ ] **Werkt alleen met de bril op.** Zonder bril doet het harnas niets: de bril levert de
      stroom, het harnas heeft geen eigen batterij
- [ ] Mag overpowered zijn, maar moeilijk te krijgen in survival

Nog niet besproken: wat het precies kan (vliegen, repulsors, pantser, val opvangen, onder water
ademen), waar je het vandaan haalt, en wat het aan energie kost.

### 2. Advancements

- [ ] Een echte boom die je door de fases leidt (nu zijn er alleen recept-advancements)

### 3. Vijanden

- [ ] Raiddrones die je aanvallen, willekeurig of als je hun basis aanvalt
- [ ] Ze mogen blokken kapotmaken, met een **gamerule** om dat uit te zetten; standaard aan

### 4. Meer in de wereld

- [ ] Meer structuren
- [ ] De Stark Tower verder uitbreiden
- [ ] Meer high tech in het algemeen

### 5. De AI-stem (als laatste)

- [ ] **Drie standen om uit te kiezen:** vaste zinnen (zoals nu), een lokaal taalmodel, of een
      taalmodel in de cloud via de Claude API
- [ ] De API-sleutel vul je **in het spel** in
- [ ] De mod kiest aan de hand van je pc-specs welk lokaal model wordt geïnstalleerd
- [ ] Ze onthoudt dingen permanent ("mijn basis heet Thuis") én leert van jouw manier van praten
- [ ] Verstaat ze je verkeerd, dan vraagt ze "bedoelde je X?" of vraagt ze je het te herhalen
- [ ] Ze mag uit zichzelf praten, aan/uit te zetten in het G-menu
- [ ] Op een server hoor je alleen je eigen bril

---

## Open vragen

- [ ] **Moeten de getallen uit `EdithBalance.java` naar een configbestand?**
      Nu moet je de mod herbouwen om iets aan te passen. In een configbestand kun je tijdens het
      spelen bijsturen — handig zodra je met vrienden speelt ("die satelliet is te sterk").
- [ ] **Wil je bij elke nieuwe functie automatische tests?**
      Ik schrijf ze nu standaard mee (29 stuks inmiddels). Ze kosten per functie 20-50 regels
      extra en vangen op dat een wijziging aan de drones per ongeluk de satelliet sloopt.
- [ ] Moeten de Nederlandse foutmeldingen in de gametests ook Engels? Die zie je alleen tijdens
      ontwikkelen, nooit in het spel.

---

## Ideeën die we nog niet gekozen hebben

Wel besproken, maar niet ingepland. Hier zodat ze niet verdwijnen.

**Gevecht**
- Repulsor-handschoen als los item
- Mini-raketten die hun doel volgen
- "Veronica": onderdelen van een harnas die naar je toe vliegen
- Drone-formaties (schild, pijl, cirkel)

**Basis en techniek**
- Een werkbank die harnasdelen maakt
- Een Stark-AI-kern die passief onderzoek doet
- Automatische torentjes die aan je relais-groepen hangen
- Energie-netwerk: kabels of een draadloos Stark-net, zodat zonnepanelen en warmtegeneratoren
  niet meer alleen zichzelf opladen

**Sfeer**
- Eigen geluiden. Alle 18 geluiden in `sounds.json` zijn nu vanilla-geluiden onder een andere
  naam (`beacon.activate`, `note_block.pling`). Echte E.D.I.T.H.-tonen zouden veel schelen.
- Betere foutmeldingen bij Spotify en de stem-installatie

**Wereld**
- Een tweede soort structuur met de onderdelen voor de volgende fase (Hydra-bunker, neergestorte
  satelliet)

---

## Besloten om niet te doen

- **Linux en Mac ondersteunen.** Alleen Windows; de stem en de AI draaien op Windows-programma's.
- **De satelliet aanpassen.** Die is goed zoals hij is.
- **De mod publiceren.** Blijft voorlopig privé.
- **Naar een nieuwere Minecraft-versie.** Voorlopig 1.21.1.
