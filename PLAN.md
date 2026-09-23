# Stark Tech — plan en voortgang

Alles wat we besproken hebben, op één plek: wat af is, waar ik mee bezig ben, wat er nog komt,
en wat er nog open staat. Bijwerken zodra er iets verandert.

Afspraken die overal gelden:

- **Eén functie tegelijk**, en committen zodra hij werkt.
- **Alles in het spel is Engels.** Commentaar in de code en documenten zoals dit mogen
  Nederlands zijn; die zie je niet in het spel.
- Blijft op **Minecraft 1.21.1 + NeoForge**, alleen **Windows**, voorlopig **privé**.
- Moet vooral **werken**, maar uitleg mag altijd.
- Je speelt zelf, maar het moet **met vrienden op een server** kunnen: iedereen heeft zijn
  eigen bril, arsenaal en spraakcommando's.
- E.D.I.T.H. mag **overpowered** zijn; de moeilijkheid zit in het verkrijgen ervan.

**Stand van zaken:** 40 automatische tests, allemaal groen (`gradlew runGameTestServer`).
Wat je moet *zien*, controleer ik met `gradlew runVisualCheck`: het spel start zelf, bouwt een
testwereld, maakt screenshots van alles en sluit weer af. De plaatjes staan in
`run-visual/screenshots`. Er gaat dan even een Minecraft-venster open; laat dat met rust.

---

## Afgerond

### Polijsten

- [x] **Al het werk van vóór deze ronde vastgelegd** — `053dd5a`
      Zonnepaneel, warmtegenerator en de lader met zijn scherm; rayshields in zes kleuren met
      een schuif-animatie; de grote Stark Tower met kluis; waypoints bewerken.

- [x] **Alles Engels in het spel** — `47a7d4b`
      Er lekte nog Nederlands door via het spraakwoordenboek, de Spotify-pagina in je browser,
      foutmeldingen in de HUD en het installatiescherm, en de muziekzoeker verstond "van".
      Twee bugs onderweg: een opgeslagen `starktech-voice.json` werd eeuwig hergebruikt, en
      `split("\s+")` was geen regex maar de escape voor een spatie.

- [x] **HUD opgesplitst** — `b1f8260`
      1084 regels in één klasse → 124 regels plus 16 losse widgets in `client/hud/`.

- [x] **Bril alleen nog uit de Stark Tower** — `4bcffc6`
      Geen recept meer; de ingrediënten blijven bestaan voor al het andere.

- [x] **Redstone geeft minder energie** — `4bcffc6`
      50 per stuk in plaats van 500. Redstone is noodvoorraad; zon en warmte zijn je stroom.

### Fase 15

- [x] **Drone-arsenaal in een baan om de planeet** — `8fedd02`
      Je bouwt drones (Stark-chip, 5 ijzer, 2 redstone, amethist) en ze vliegen naar hun baan.
      Eén arsenaal, één soort drone, oneindig groot, van jou persoonlijk. Alles wat drones
      gebruikt haalt ze daaruit; leeg arsenaal = er vertrekt niets. Schade blijft bewaard,
      neergehaald is weg (~10% terug als schroot). Scherm op **G > Drones > Arsenaal**.

- [x] **Teruggeroepen drone landt naast je** — `379478d`
      Rechtsklik = repareren met ijzer, shift-rechtsklik = weer omhoog.

- [x] **Van heel hoog in- en uitvliegen** — `379478d`
      96 blokken hoog in een duik met vonken, en bij vertrek net zo hoog weer weg.
      Configsleutel heet nu `drones.orbitHeight`.

- [x] **Holotafel** — `47b99c1`, instelscherm `7565c03`, kaart hersteld `b0a2ed3`
      Hologram boven een tafel in een kleur naar keuze. Drie standen: reliëfkaart met je
      waypoints, spelers en vijanden; de bouwtekening van wat je op tafel legt; je arsenaal.
      Instelscherm (rechtsklik met een lege hand): stand, kleur, bereik (24–160 blokken),
      grootte, ronddraaien, voorwerp van tafel halen. Zonder bril doet hij niets.
      **Hersteld na jouw melding:** de kaart tekende op vlak land helemaal niets (lijnen van
      lengte nul) en is nu een draadmodel-rooster; het hologram verdween als de tafel zelf
      buiten beeld was; bij het kleinste bereik stond alles scheef; voorwerpen in de
      bouwtekening waren 's nachts zwart; twee tafels zaten elkaar in de weg.

- [x] **Het harnas** — `710d7ab`, afgezwakt `f78aba3`, nieuw uiterlijk `e96a9df`
      Borstplaat, broek en laarzen; geen helm, want die plek is van de bril. Zonder bril doet
      het niets bijzonders. Met bril: 20% schade eraf per stuk, vliegen met het hele pak, elke
      val tot 40 blokken opgevangen, repulsor op toets **I**.
      Repulsor na jouw feedback: 7 schade (was 12), 200 energie (was 500), minder wegduwen en
      bijna geen terugslag meer.
      Uiterlijk na jouw feedback: rood en goud met een blauwe reactor. De oude texturen stonden
      op de verkeerde plekken, waardoor de laarzen onzichtbaar waren.
      Aannames die ik zelf heb ingevuld: wat het kan, dat het craftbaar is (netherite +
      Stark-chips), en de getallen in `SuitBalance.java`.

- [x] **Vijanden: raiddrones** — `66fae68`
      Een vijandige drone (donker, oranje lampjes) die uit de lucht duikt, om je heen cirkelt
      en schiet. Verstop je je, dan komt hij dichterbij en breekt hij zich naar binnen.
      Komt vanzelf als je de bril draagt (standaard elke 20–45 minuten) en meteen als je te veel
      sloopt in een Stark Tower. Gamerule `starktechRaidGriefing` zet het slopen uit (standaard
      aan); frequentie en grootte van een golf staan in de serverconfig.

- [x] **Holotafel-scherm werkt echt** — `bbdf1ab`
      Rechtsklik met een lege hand opende het instelscherm nooit: Minecraft geeft een lege hand
      ook als "voorwerp" door, en de tafel legde dan "lucht" op tafel. Het scherm zelf had
      knoppen en tekst over elkaar ("Done" over de uitleg, "Take off" eronder) en is opnieuw
      ingedeeld. De kaart was van dichtbij één dichte blauwe plaat; nu minder en lichtere lijnen,
      meer reliëf (hoogste punt per vakje, dus ook een smalle toren), randen die naar een bodem
      lopen, water zwakker en een noordpijl.

- [x] **Schermen niet meer wazig** — `ddbe519`
      In 1.21.1 tekent Minecraft een wazige achtergrond over alles wat vóór de knoppen getekend
      is. Daardoor waren de panelen van 9 schermen (arsenaal, navigatie, comms, muziek, relais,
      spionage, waypoints, holotafel) wazig en donker. Nu een lichte verduistering en een scherp
      paneel. Lange uitleg loopt niet meer buiten het paneel.

- [x] **Screenshot-rondleiding** — `6e4de3a`
      `gradlew runVisualCheck`, zie bovenaan.

- [x] **Advancements** — `1e3604d`
      16 prestaties onder "Stark Industries", van je eerste Stark-chip tot vliegen met het
      harnas. Wat de mod zelf toekent (eerste drone, satelliet, drone verloren, vliegen,
      repulsor) gebeurt op het moment dat het echt gebeurt.

---

## Nog te doen, in deze volgorde

### 1. Jouw nieuwe wensen (23 september)

- [x] **Repulsor als aan/uit-knop** — `bdaa237`
      Toets I (of G > Combat) zet hem aan of uit; staat hij aan, dan vuur je met de aanvalsknop,
      ingedrukt houden vuurt elke seconde. Een ring rond het vizier laat zien wanneer hij weer
      geladen is. Drie eigen geluiden, gemaakt door `tools/generate_sounds.py`: opladen bij
      aanzetten, wegzakken bij uitzetten, en het schot (zoem, zap, dreun, naklank).
      Onderweg gevonden: de wachttijd van één seconde werd nergens gecontroleerd; de server
      houdt hem nu echt bij (nieuwe test). De straal begint een armlengte voor je, anders zag
      je zelf alleen blauwe blokken.
- [ ] **Vliegen met natuurkunde** in plaats van creative-vliegen: snelheid opbouwen, uitlopen,
      zwaartekracht, stuwkracht.
- [ ] **Schade aan drones zichtbaar**: hoe meer schade, hoe kapotter het model, met rook en
      vonken, ook als ze naast je vliegen.
- [ ] **Arsenaal in 3D**: een scherm met de satelliet en je drones als 3D-modellen, met hun
      schade eraan te zien.
- [ ] **Opstartanimatie van de bril** leuker en uitgebreider.
- [ ] **Satelliet upgraden via een scherm**: cooldown, schade, de drie schietmodi vrijspelen,
      enz. Elk tot niveau 10. Je hebt genoeg drones nodig om de onderdelen naar de baan te
      brengen.

### 2. Meer in de wereld

- [ ] Meer structuren
- [ ] De Stark Tower verder uitbreiden
- [ ] Meer high tech in het algemeen

### 3. De AI-stem (als laatste)

- [ ] **Drie standen om uit te kiezen:** vaste zinnen (zoals nu), een lokaal taalmodel, of een
      taalmodel in de cloud via de Claude API
- [ ] De API-sleutel vul je **in het spel** in
- [ ] De mod kiest aan de hand van je pc-specs welk lokaal model wordt geïnstalleerd
- [ ] Ze onthoudt dingen permanent ("mijn basis heet Thuis") én leert van jouw manier van praten
- [ ] Verstaat ze je verkeerd, dan vraagt ze "bedoelde je X?" of vraagt ze je het te herhalen
- [ ] Ze mag uit zichzelf praten, aan/uit te zetten in het G-menu
- [ ] Op een server hoor je alleen je eigen bril

---

## Nog nooit in het spel bekeken

De automatische tests draaien zonder beeld: ze controleren wat er gebeurt, niet hoe het
eruitziet. Dit is gebouwd maar door mij nooit gezien. Laat het weten als iets er raar uitziet.

Sinds de screenshot-rondleiding wél gezien en in orde: de HUD (met doelanalyse), het G-menu,
alle schermen, de holotafel in alle drie de standen, het harnas op je lijf en hoe de drones en
raiddrones eruitzien.

- [ ] Het vliegen met het harnas en de repulsorstraal (dat beweegt; een screenshot zegt weinig)
- [ ] Raiddrones: hoe ze bewegen en aanvallen
- [ ] Raiddrones die komen omdat je in een Stark Tower sloopt. Dat kan de test niet nadoen:
      een testwereld weet niet dat een geplaatst gebouw een "Stark Tower" is.

---

## Open vragen

- [ ] **Moeten de getallen uit `EdithBalance.java` naar een configbestand?**
      Nu moet je de mod herbouwen om iets aan te passen. In een configbestand kun je tijdens het
      spelen bijsturen — handig zodra je met vrienden speelt ("die satelliet is te sterk").
- [ ] **Wil je bij elke nieuwe functie automatische tests?**
      Ik schrijf ze nu standaard mee (38 stuks inmiddels). Ze kosten per functie 20-50 regels
      extra en vangen op dat een wijziging aan de drones per ongeluk de satelliet sloopt.
- [ ] Moeten de Nederlandse foutmeldingen in de gametests ook Engels? Die zie je alleen tijdens
      ontwikkelen, nooit in het spel.

---

## Ideeën die we nog niet gekozen hebben

Wel besproken, maar niet ingepland. Hier zodat ze niet verdwijnen.

**Gevecht**
- Repulsor-handschoen als los item (nu zit de repulsor in de borstplaat)
- Mini-raketten die hun doel volgen
- "Veronica": onderdelen van een harnas die naar je toe vliegen
- Drone-formaties (schild, pijl, cirkel)
- Het harnas: onder water ademen

**Basis en techniek**
- Een werkbank die harnasdelen maakt
- Een Stark-AI-kern die passief onderzoek doet
- Automatische torentjes die aan je relais-groepen hangen
- Energie-netwerk: kabels of een draadloos Stark-net, zodat zonnepanelen en warmtegeneratoren
  niet meer alleen zichzelf opladen
- Holotafel: een vierde stand die de ertsen in de buurt laat zien

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
- **De mod publiceren.** Blijft voorlopig privé.
- **Naar een nieuwere Minecraft-versie.** Voorlopig 1.21.1.
