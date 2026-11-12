# I.M.T Technology — plan en voortgang

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
- I.M.T mag **overpowered** zijn; de moeilijkheid zit in het verkrijgen ervan.
- **Eigen namen** (17 oktober): de mod heet **I.M.T Technology** (mod-id `imttech`), de bril
  **I.M.T** ("I Am Tunnelsensor"); de toren, de bunker en de satelliet zijn van **Biebia's
  Industries**, en spullen heten naar wat ze zijn (Computer Chip, Power Cable, Drone, Hacking
  Laptop, Rocket Turret, Tech Chestplate, Security OS). Niets meer van Stark of E.D.I.T.H.
  Ronde 2 (17 oktober): de vijanden heten **Raven** (was Hydra), de repulsor is de **laser
  cannon**, de AI van de toren heet **BiTech** (Biebia Technologie), en de training heet
  **Rookie squad** met als voorbeeldnaam **Bootcamp**. Rayshield, de robotstijlen en de Ion
  Blaster blijven zo (jouw keuze).

**Stand van zaken:** 512 automatische tests (`gradlew runGameTestServer`). Met de standaardwaarden uit
de code allemaal groen; met jouw tragere waarden in `run/config/imttech.toml` (zonnepaneel 0.3, warmte 0.7
per tick) 3 rood (zie hoofdstuk 14, stap 2). Voor de release blijven de standaardwaarden uit de code
(jouw keuze, 17 oktober).
**Publiceren:** klaar voor CurseForge (17 oktober), onder **All Rights Reserved** (sinds 11 november, daarvoor
MIT): de beschrijving, het logo, de changelog en een stappenplan staan in `publish/` (begin bij
`publish/HOE-PUBLICEREN.md`).
Alles zelf bekijken: `python tools/showcase.py` laat alle 128 functies één voor één zien (of een
stukje: `--vanaf 30 --tot 34`), met een
pauze tussen elke functie om notities te maken (zie LEESMIJ.md, hoofdstuk 7).
Wat je moet *zien*, controleer ik met `gradlew runVisualCheck`: het spel start zelf, bouwt een
testwereld, maakt screenshots van alles en sluit weer af. De plaatjes staan in
`run-visual/screenshots`. Er gaat dan even een Minecraft-venster open; laat dat met rust.
Alleen een stuk fotograferen kan ook: `gradlew runVisualCheck -Ponly=satellite` (of `laser`,
`commands`, `holo`, `screens`, ...).

---

## Afgerond

### Polijsten

- [x] **Al het werk van vóór deze ronde vastgelegd** — `053dd5a`
      Zonnepaneel, warmtegenerator en de lader met zijn scherm; rayshields in zes kleuren met
      een schuif-animatie; de grote Biebia's Industries Tower met kluis; waypoints bewerken.

- [x] **Alles Engels in het spel** — `47a7d4b`
      Er lekte nog Nederlands door via het spraakwoordenboek, de Spotify-pagina in je browser,
      foutmeldingen in de HUD en het installatiescherm, en de muziekzoeker verstond "van".
      Twee bugs onderweg: een opgeslagen `imttech-voice.json` werd eeuwig hergebruikt, en
      `split("\s+")` was geen regex maar de escape voor een spatie.

- [x] **HUD opgesplitst** — `b1f8260`
      1084 regels in één klasse → 124 regels plus 16 losse widgets in `client/hud/`.

- [x] **Bril alleen nog uit de Biebia's Industries Tower** — `4bcffc6`
      Geen recept meer; de ingrediënten blijven bestaan voor al het andere.

- [x] **Redstone geeft minder energie** — `4bcffc6`
      50 per stuk in plaats van 500. Redstone is noodvoorraad; zon en warmte zijn je stroom.

### Fase 15

- [x] **Drone-arsenaal in een baan om de planeet** — `8fedd02`
      Je bouwt drones (computerchip, 5 ijzer, 2 redstone, amethist) en ze vliegen naar hun baan.
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
      val tot 40 blokken opgevangen, laser cannon op toets **I**.
      Laser cannon na jouw feedback: 7 schade (was 12), 200 energie (was 500), minder wegduwen en
      bijna geen terugslag meer.
      Uiterlijk na jouw feedback: rood en goud met een blauwe reactor. De oude texturen stonden
      op de verkeerde plekken, waardoor de laarzen onzichtbaar waren.
      Aannames die ik zelf heb ingevuld: wat het kan, dat het craftbaar is (netherite +
      computerchips), en de getallen in `SuitBalance.java`.

- [x] **Vijanden: raiddrones** — `66fae68`
      Een vijandige drone (donker, oranje lampjes) die uit de lucht duikt, om je heen cirkelt
      en schiet. Verstop je je, dan komt hij dichterbij en breekt hij zich naar binnen.
      Komt vanzelf als je de bril draagt (standaard elke 20–45 minuten) en meteen als je te veel
      sloopt in een Biebia's Industries Tower. Gamerule `imttechRaidGriefing` zet het slopen uit (standaard
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
      16 prestaties onder "Biebia's Industries", van je eerste computerchip tot vliegen met het
      harnas. Wat de mod zelf toekent (eerste drone, satelliet, drone verloren, vliegen,
      laser cannon) gebeurt op het moment dat het echt gebeurt.

---

## Nog te doen, in deze volgorde

### 1. Jouw nieuwe wensen (23 september)

- [x] **Laser cannon als aan/uit-knop** — `bdaa237`
      Toets I (of G > Combat) zet hem aan of uit; staat hij aan, dan vuur je met de aanvalsknop,
      ingedrukt houden vuurt elke seconde. Een ring rond het vizier laat zien wanneer hij weer
      geladen is. Drie eigen geluiden, gemaakt door `tools/generate_sounds.py`: opladen bij
      aanzetten, wegzakken bij uitzetten, en het schot (zoem, zap, dreun, naklank).
      Onderweg gevonden: de wachttijd van één seconde werd nergens gecontroleerd; de server
      houdt hem nu echt bij (nieuwe test). De straal begint een armlengte voor je, anders zag
      je zelf alleen blauwe blokken.
- [x] **Vliegen met natuurkunde** — `917c83c`
      Dubbel spatie zet de stuwraketten aan. Stuwkracht in je kijkrichting, vaart die opbouwt
      en uitloopt, zwaartekracht, naverbrander op sprint, overhellen in bochten, breder beeld
      bij hoge snelheid, zelf afremmen boven de grond, een stuwraketgeluid (lus) en blauwe
      straalvlammen die iedereen ziet. Nagemeten in het spel: stilhangen blijft op dezelfde
      hoogte, naverbrander 21 → 48 m/s in twee seconden, stijgen 12 m/s, dalen 15 m/s.
      Getallen staan in `SuitBalance.java`.
- [x] **Showcase** — `c4caae7`
      `python tools/showcase.py`: vraagt of je pauzes wilt (Y/n), start het spel en laat alle
      52 functies zien. Na elke functie: Enter = volgende, r = opnieuw, q = stoppen, een getal =
      springen, iets anders = notitie in `showcase-notities.md`.
- [x] **Schade aan drones zichtbaar** — `1935f14`
      Roet en krassen in drie stappen, een kromme rotor, een ontbrekende rotor en een hangende
      camera, op het laatst twee rotors weg en een wiebelende romp. Rook, vonken, brandende
      brokjes en haperende lampjes. Ook bij raiddrones.
- [x] **Arsenaal in 3D** — `1935f14`
      De aarde met de Biebia's Industries-satelliet en al je drones in hun baan, als echte modellen met hun
      schade. Slepen = rondkijken, scrollen = zoomen, klikken = kiezen; de gekozen drone draait
      groot in een vakje.
- [x] **Opstartanimatie van de bril** — `3c5dcec`
      Ruim vier seconden: scherm gaat aan, logo met reactorringen, systeemcheck met piepjes
      (met je echte aantal drones en je batterij), irisscan met je naam, ONLINE. Eigen geluiden.
- [x] **Satelliet upgraden via een scherm** — `85f1dd3`
      Zes onderdelen, elk tot niveau 10: precisie-straal, bombardement en schuivende laser
      (die laatste twee eerst vrijspelen), opladen, kracht en richten. Een niveau kost spullen
      uit je rugzak, en 2 tot 6 drones uit je arsenaal vliegen ze voor je ogen omhoog (ze komen
      daarna terug). De getallen per niveau staan in `SatelliteStats.java`, de kosten in
      `SatelliteUpgrade.java`. Drie nieuwe tests.

### 2. Uit de showcase (23 september, 21:13)

Jouw notities uit de showcase, per onderwerp gegroepeerd. Alles uit het notitiebestand staat
hier, dus dat bestand is weggegooid. In deze volgorde: eerst wat de satelliet afmaakt, dan de
kleine dingen, dan de grote.

**Satelliet** (hoort bij het upgradescherm hierboven)
- [x] Satelliet los van het arsenaal: geen knop meer in het arsenaalscherm, een eigen plek
      onder G > Combat. In de 3D-baan van het arsenaal blijft hij wel hangen. — `85f1dd3`
- [x] De satelliet ziet er beter uit naarmate je hem upgradet — `85f1dd3`
      Per onderdeel komen er stukken bij: een kanon met focusringen en een gloeiende punt,
      raketkokers (tot acht), een rode lens met vinnen, langere zonnepanelen, condensatoren en
      een grote reactor, een tweede antenne, een grotere schotel en een sensor. Ook in het
      arsenaal zie je je eigen satelliet zo. Slepen draait hem, scrollen zoomt in.
- [x] Scherm netter: geen tekst over elkaar, duidelijkere secties, en *alle* stats onder de
      3D-satelliet (ook die van het bombardement en de laser) — `85f1dd3`
      Links de satelliet met daaronder alles per stand; rechts "Firing modes" en "Systems".
      Nagekeken op screenshots bij een klein scherm (1280x720, GUI-schaal 3).
      Onderweg: de uitleg onderaan het arsenaalscherm hield halverwege op ("click a").
- [x] In creative: een knop om alle upgrades in één keer vrij te spelen ("Max all") — `85f1dd3`
- [x] Showcase: de drie schietstanden vrijspelen, anders werken bombardement en laser niet —
      `85f1dd3`. Nieuw in de showcase: "Satellite upgrades", waarin de satelliet groeit.

**Kleine dingen**
- [x] Geen tekst meer boven de relais — `a6ef7d4`
- [x] Laserpoort: geen relais-functies meer, een volledig blok, en het glas in de goede
      richting (één lijn `---`, niet naast elkaar `| | |`) — `a6ef7d4`
      Een metalen blok met de lens in een frame; aan met redstone, rechtsklik = kleur. Wil je
      hem toch met I.M.T schakelen: zet er een relais naast. Oude laserpoorten verdwijnen
      vanzelf uit je relaislijst. Nagekeken op screenshots (`-Ponly=laser`), ook van boven.
- [x] De illusie "wereld kopiëren" weghalen ("cloak" blijft) — `ab4ab2d`
- [x] Laser cannon: niet bij elk schot "hit 1" zeggen of tonen; alleen geluid — `c7c7e7a`
- [x] Raiddrones iets minder sterk: minder schade, minder leven en trager schieten — `bc46a61`
      3 schade (was 5), 10 leven (was 16), om de 2 seconden (was 1,25).
- [x] G > Systems > Commands: bij een nieuw commando staan de acties over elkaar of buiten
      beeld; een echte scrollbalk erbij — `a8b14b5`
      Past het gekozen commando niet, dan scrolt de rechterkant (wieltje of balk slepen); de
      lijst links heeft ook een balk. Nagekeken met vier acties op een klein scherm
      (`-Ponly=commands`).

**Geluid en opstarten**
- [x] De bevestigingsgeluiden (zoals "aangekomen op bestemming") klinken lelijk: eigen
      I.M.T-tonen, en meteen ook de andere geleende Minecraft-geluiden vervangen — `270ae6b`
      Alle 17 geleende geluiden zijn nu eigen geluiden (`tools/generate_sounds.py`). Door jou
      beluisterd: "voorlopig goed".
- [x] Opstartanimatie: een laadbalk, duidelijk "I.M.T" in beeld, de bril zegt iets, en
      je identiteit wordt gecontroleerd — `6761fd5`, geluid `668fabb`
      Groot logo in reactorringen, een laadbalk met wat er geladen wordt (je echte drones en
      batterij), dan scant hij je eigen gezicht terwijl de balk op 90% wacht. Bevestigd: groen,
      je naam, en I.M.T zegt haar welkomstzin (die werd vroeger ongezien meteen gezegd).
      Geluid: stijl "D, arc reactor", door jou gekozen uit vier voorbeelden.
- [x] Showcase: geluid standaard op 100% (ook de stem) — `49f1931`

**Illusies richten**
- [x] Valse muur: vóór het plaatsen de drone besturen en zo richten waar de muur komt — `1f47de1`
      Je kijkt door de drone; de muur staat als voorvertoning waar je naar kijkt, in een blauw
      kader met een richtlijn, en draait mee met je kijkrichting. Linkermuisknop = neerzetten,
      rechtermuisknop = annuleren. Nagekeken op screenshots (`-Ponly=illusion`).
- [x] Nep-leger: de zombies zakten in de grond; ook richten met de drone, zoals de muur — `1f47de1`
      Ze stonden precies één blok te laag. Nu staat elke zombie op de grond onder zijn eigen
      plek, dus ook netjes op de treden van een heuvel.
      Onderweg gevonden: de spionagedrone en de cloakingdrone waren nooit echt onzichtbaar
      (Minecraft zette dat meteen weer uit). Nu wel, en hun lampjes verraden ze ook niet meer.
      Een beschadigde drone waar je doorheen kijkt, vult je beeld niet meer met rook.

**Stroomkabels en de schildkoepel**
- [x] Een kabelblok: zonnepanelen en warmtegeneratoren sturen stroom naar alles wat stroom
      gebruikt (koepel, laadstation, laserpoort, holotafel). Laserpoort en holotafel doen
      zonder stroom niets meer. — kabels `4ed89b7`, lasers/holotafel/toren `413c0c3`
      De stroomkabel (koper, redstone, koper = 8 kabels) sluit vanzelf aan op kabels en
      apparaten; een apparaat dat direct tegen een generator staat, doet ook mee. Het
      laadstation haalt alleen stroom voor een bril die nog niet vol is (anders zoog het het
      net leeg). Een holotafel zonder stroom toont alleen een knipperend rood ringetje.
      De **Biebia's Industries Tower** heeft nu een verborgen krachtcentrale: een dichtgemetseld blok op de
      beveiligingsverdieping met twee warmtegeneratoren in lava, en kabels door het plafond naar
      alle laserzenders. Knip ze door en de lasers gaan uit. Onderweg gevonden: de lasers in de
      toren stonden als "aan" opgeslagen zonder wand, dus de gang had waarschijnlijk nooit echt
      laserwanden. Nu gaan ze zelf aan zodra ze redstone en stroom hebben (test erbij).
      Ook gevonden: het zonnepaneel en de warmtegenerator lieten bij afbreken niets vallen.
- [x] Koepel begint als één straal omhoog, wordt vanaf een bepaalde hoogte mooi breder en zakt
      dan als koepel naar beneden — `e0d2b64`
      Een felle lichtbundel omhoog, bovenin vouwt hij open als een paraplu en zakt met een
      oplichtende onderrand naar de grond. Daarna een doorzichtig zeshoekjesveld in zijn kleur
      dat langzaam over de koepel glijdt, ook van binnen te zien, en dat flakkert als de
      schildkracht bijna op is. Nagekeken op screenshots (`-Ponly=dome`).
- [x] Een scherm om kleur, bereik en sterkte in te stellen (sterker = meer energie) — `e0d2b64`
      Rechtsklik (alleen de eigenaar): aan/uit, zes kleuren, bereik 8 tot 32 blokken,
      sterkte 1 tot 5, en wat dat kost en hoeveel stroombronnen er aan hangen.
- [x] Houdt alles tegen wat niet van jou of je team is: projectielen, monsters, raiddrones,
      satellietaanvallen van anderen — `e0d2b64`
      Ook de laserschoten van raiddrones en van andermans drones; een satellietstraal eindigt
      zichtbaar óp de koepel. Elke treffer kost schildkracht; is die op, dan valt hij 10
      seconden weg. De getallen staan in `DomeSettings.java`. Vier nieuwe tests.

**Harnas**
- [x] Laser cannon: de hand beweegt en gloeit bij een schot, en het schot is een eigen, gedetailleerd
      effect in plaats van Minecraft-deeltjes — `da56f6c`
      In je eigen beeld gaat je arm omhoog (onderarm vooruit, hand omhoog geknikt, palm naar
      voren) met een zacht kloppende blauwe gloed; bij een schot flitst de palm en slaat je arm
      terug. Anderen (en jij met F5) zien de arm vooruit wijzen en de palm oplichten: daarvoor
      een nieuwe armhouding via NeoForge (`META-INF/enumextensions.json`). Het schot is zelf
      getekend licht: witte kern, blauwe gloed, drukgolfringen bij de hand, een lichtbol naar het
      doel en een flits met een uitdijende ring bij de inslag.
- [x] Vliegen: de vlammen beter onder de voeten, geen echt spoor erachter, en eigen deeltjes
      — `da56f6c`
      Zelf getekende straalvlammen (blauw met een witte kern en een gloed tegen de zool) die
      vast aan de laarzen zitten, langer en feller naarmate je harder gaat. De server vertelt
      spelers in de buurt wie er vliegt en hoe hard, dus iedereen ziet ze. Screenshots:
      `-Ponly=suit,flight`.
- [x] Het harnas meer 3D-vorm geven — `76900f5`
      Een eigen model bovenop het gewone harnas (`client/suit/TechSuitModel.java`), met de
      plaatjes nu 128x64 zodat de extra stukken een plek hebben: een uitstekende reactor die
      blauw gloeit (ook in het donker), borstplaten, drie gouden buikplaten, een rugplaat met
      twee straalopeningen, schouderplaten die iets naar buiten kantelen, onderarmbeschermers
      met een laser cannon in de handpalm, laarzen met een schaal, gouden neus en zool, en aan de
      broek kniestukken, dijplaten en een gesp. Je eigen arm (laser cannon) toont de harnasmouw
      ook. Close-ups: `-Ponly=suit` (`41b` t/m `41d`).

**Modellen en gebouwen**
- [x] Holotafel: een vierde stand "Satellite" (groot en gedetailleerd, met zijn upgrades), en
      in de arsenaalstand echte drone-modellen in plaats van stipjes — `0bd0964`
      Beide gebruiken de echte 3D-modellen uit het arsenaalscherm, doorschijnend en getint:
      de satelliet in de kleur van het hologram (met al zijn upgrade-onderdelen, iets schuin
      zodat je het kanon en de schotel ziet, en een ring met zes stukken van tien streepjes
      voor de niveaus), de drones in hun schadekleur met de neus in de vliegrichting. Nieuwe
      showcase-functie "Holo table: satellite"; close-ups met `-Ponly=holo` (`05a` t/m `05d`).
- [x] Zonnepaneel en warmtegenerator een mooier 3D-model — `7c527b8`
      Zonnepaneel: voetplaat, paaltje met scharnier en een schuin paneel (naar het zuiden) met
      cellen, aluminium rand en geribde achterkant. Warmtegenerator: sokkel, kast, deksel met
      luchtrooster, vier koelribben per kant, twee koperen pijpen achter en voorin een rooster.
      Hij heeft nu een voorkant (naar je toe bij plaatsen) en gloeit alleen als hij warmte
      opwekt (dan ook licht); de test van de generator controleert dat. De modellen maakt
      `tools/generate_block_models.py`; screenshots met `-Ponly=models`.
- [x] Biebia's Industries Tower mooier, overal meer detail — `23d7f84`
      Onderweg gevonden en gerepareerd: het BIEBIA-logo miste de bovenste rij van elke letter
      (het terras erboven overschreef hem), en er hing nergens een plafondlamp (de vloer erboven
      overschreef ze), dus binnen was het donker. De ladder liep buiten langs de gevel en vanaf
      het archief kwam je nergens meer binnen; de ladder van de kluis eindigde tegen het glas.
      Nu: een glazen liftkoker van de lobby tot in de lasergang, en een ladder van de kluis
      door het penthouse naar het dak. De valstrik in de kluis deed niets (geen redstone, geen
      stroom): nu schieten er vier laserzuilen omhoog als je op de drukplaat stapt (nieuwe
      test). Het logo is van koperen lampen (gloeit 's nachts), elk terras heeft lampjes, de
      kluis heeft van buiten banden en kolommen, en uit de kroon schiet een blauwe bakenstraal.
      Elke verdieping is ingericht (receptie met logowand, lab met watertanks en pakstelling,
      archief, hangar met landingscirkel en hangardeur, woonverdieping, serverkasten en een
      holotafel in de beveiliging, bar en vleugel in het penthouse, schotel en zonnepanelen
      op het dak). Rondleiding met screenshots: `-Ponly=tower`.

### 3. Uit de tweede showcase (25 september, 17:07)

Jouw notities, per onderwerp. Alles uit het notitiebestand staat hier, dus dat bestand is
weggegooid. Waar ik zelf iets heb ingevuld staat "(mijn invulling)": zeg het als je het anders
wilt. Eerst de kleine dingen, dan de grote.

**Showcase**
- [x] Overslaan, vorige en typen tijdens een functie — `6c9a69f`
      Typ tijdens een functie en druk op Enter: `s` = overslaan, `v` = vorige, `r` = opnieuw,
      `q` = stoppen, een getal = springen, al het andere = notitie (de functie speelt door). Een
      S in een notitie telt dus niet als overslaan.
- [x] De showcase uitgebreid met wat er nog niet in zat — `cb04ecb`
      Negen nieuwe functies (nu 66): "Transfer glasses" (bril doorgeven), "Edit a waypoint",
      "Relay: name and group", "Dome under attack" (raiddrones tegen de koepel), "Repair a
      drone", "Glasses upgrades" (in het laadstation), "Raid drones: breaking in" (ze schieten
      zich een hut in), "I.M.T Technology items" (het creative-tabblad) en het persoonlijke schild
      vangt nu echt pijlen op. De sweep kiest een begin en eind, het leger valt Seppe aan.
      Onderweg gevonden: Seppe ging dood door raiddrones (en was daarna niet meer te hacken of af
      te leiden); hij is nu onkwetsbaar in de showcase.
- [x] Eerst een inhoudstafel, en een kopje NIEUW — `09ed998`
      Terwijl het spel opstart zie je alle functies per deel, met bovenaan NIEUW (wat je nog
      niet zag). Kies: Enter = alles, `n` = de nieuwe, nummers, `10-15` of een deel (`drones`),
      ook gemengd. Tijdens het kijken brengt `i` de inhoud terug. Een functie gaat uit NIEUW
      als hij tot het eind speelde of je hem pas na 8 seconden stopte; dat staat in
      `showcase-gezien.json` (niet in git). De 16 functies die er sinds je laatste keer bij
      kwamen, staan nu bij NIEUW.

**Rayshields**
- [x] Laserpoort: de wand komt altijd van één kant: hij schuift uit de zender naar het eind (en
      schuift er ook weer in terug), in plaats van vanaf twee kanten naar het midden — `fb89766`
      Een tweede zender aan de overkant is niet nodig. Staan er twee tegenover elkaar die
      allebei aan zijn, dan bouwt alleen de eerste de wand, en die blijft staan zolang één van
      de twee aan is (nieuwe test).
- [x] Koepel: de kleine hapering bij het opstarten weg — `4a561f8`
      Het openvouwende kapje was een platte schijf die in één keer verdween als de koepel begon
      te zakken, waarna die weer als klein dopje begon. Nu is het één oppervlak dat vanaf de top
      groeit. Nagekeken op screenshots (`-Ponly=dome`).
- [x] Koepel: de lichtstraal uit de generator blijft staan zolang de koepel aan is — `4a561f8`
      Rustiger dan bij het opstarten, en hij klopt zachtjes.

**Satelliet**
- [x] Satellietscherm: de getallen onder de 3D-satelliet in een echte tabel — `82fde3a`
      Een gekleurde kopregel per groep, gestreepte regels en een kolomlijn tussen naam en waarde.
- [x] Satellietscherm: de upgradekosten netter, niet meer opgepropt tegen de upgradeknop — `82fde3a`
      Elk onderdeel heeft een eigen kadertje met de kosten (twee rijen van vier), los van de
      knop, en de aantallen lopen niet meer tegen het volgende plaatje aan.
- [x] Schuivende laser: zelf een begin- en eindpunt kiezen — `9c448a0`, `8d4db10`
      Kijk naar het begin en druk op K (een markering "Sweep start", nog geen kosten), kijk naar
      het eind en druk nog eens op K. Na het opladen trekt de laser in de hele schuiftijd van het
      begin naar het eind. Een beginpunt blijft een halve minuut bewaard, of tot je van stand
      wisselt (nieuwe test: hij komt bij het eind en schiet er niet voorbij).

**Stroom**
- [x] Zonnepaneel en warmtegenerator: een logischer scherm, zonder vakjes die niets doen — `14087e2`
      Het zonnepaneel heeft nu alleen een vakje voor de bril; de warmtegenerator ook een voor
      brandstof. Lege vakjes tonen een vaag voorbeeld, er staan namen bij, en rechts staan de
      voorraad en het totaal. Onderweg gevonden: jouw spel dacht altijd dat het een
      warmtegenerator was (daarom het brandstofvakje, een verkeerde balk en 's nachts "no fuel
      and no heat" bij een zonnepaneel).
- [x] Laadstation: de voorraad vult ook via de kabels, alleen met wat het net over heeft —
      `5f49839`
      De generatoren houden altijd de helft achter voor de holotafel, laserpoorten en de koepel;
      alleen wat daarboven zit gaat, langzaam, het station in (nieuwe test).

**Illusies**
- [x] Valse muur: er kunnen er meer tegelijk staan — `811ec9f`
      De "rare glitch" was het flikkeren waar twee velden overlappen (de zwakke plek uit de
      film). Dat gebeurde ook bij twee muren van jezelf naast elkaar. Nu storen alleen velden van
      verschillende spelers elkaar.
- [x] Nep-leger: de zombies vallen andere spelers aan, zonder schade — `811ec9f`
      Ze lopen naar de dichtstbijzijnde speler die niet de eigenaar is (tot 24 blokken van het
      beeld), gaan in een kring om hem heen staan met hun armen vooruit en slaan, met een tik
      zonder schade. Is er niemand, dan lopen ze terug naar hun plek.

**Harnas**
- [x] Vliegen: een echte vlieghouding in plaats van de loopanimatie — `5d6564a`
      Wie met het harnas vooruit vliegt, helt voorover tot bijna liggend (70 graden bij volle
      vaart), met de benen recht; stilhangen en stijgen doe je rechtop, achteruit een beetje
      achterover. De straalvlammen kantelen mee en wijzen langs je lichaam naar achteren. Geldt
      voor jezelf in derde persoon en voor andere spelers.

**Drones**
- [x] Drones die uit de lucht komen vliegen in een boog naar beneden en remmen rustig af — `a30b194`
      Een drone begint een stuk opzij (hoe hoger, hoe verder, tot 24 blokken), duikt steil,
      buigt af en komt schuin binnen, met zijn neus in de vliegrichting. Past de boog nergens
      (grot, gebouw), dan komt hij recht naar beneden. Ook raiddrones komen zo binnen.
- [x] Teruggeroepen drones landen naast elkaar, niet in elkaar — `a30b194`
      Elke drone kiest een vrij plekje voor je; ook een plek waar een drone nog naartoe onderweg
      is, telt als bezet (nieuwe test).

**Onderweg gevonden**
- [x] Een bril verwisselen (rechtsklik met een bril terwijl je er al een op hebt) telde niet als
      opzetten: een vrijgegeven bril kreeg nooit zijn nieuwe eigenaar, en de bril van een ander
      zei geen "Access denied" — `e135989` (nieuwe test)
- [x] De standaardgroep van een relais heette "algemeen" (Nederlands in het spel); nu "general",
      en oude relais gaan vanzelf mee — `b08c4d3`

**Het G-menu opnieuw ingedeeld** (26 september)
- [x] Tien tabbladen — `1bca747`
      COMBAT (laser cannon, raketten, rayshield) · SATELLITE & ARSENAL (precisie-straal,
      bombardement en schuivende laser elk apart: kiezen = die stand en meteen vuren, LOCKED
      als hij nog op slot zit, PICK END als de laser op zijn eindpunt wacht; plus het
      satellietscherm en het arsenaal) · DRONES (zwerm, één drone, escorte, vorm, stand down,
      markeringen wissen) · RECON DRONES (camera, spion, meekijken, rapport) · TRANSPORT ·
      TECH · ILLUSION · INFORMATION (ertsen, dreigingen, doelanalyse) · NAVIGATION · SYSTEMS
      (nu ook nachtzicht). Cijfers 1-9 en 0 springen naar een tabblad. Eén nieuwe test.
      (mijn invulling) Het tweede, lege puntje onder Satellite & Arsenal: het satellietscherm
      en het arsenaal.

### 4. Meer in de wereld

- [x] Meer structuren (het satellietwrak en de Biebia's Industries-bunker)
  - [x] **Neergestorte Biebia's Industries-satelliet** — `2510e03`, controle `4629359`
        Een oude Biebia's Industries-satelliet uit zijn baan gevallen: een krater van zo'n 25 blokken met
        verschroeide grond, opgeworpen aarde en de voor waar hij binnenkwam; in het midden de
        opengescheurde romp (de reactor gloeit nog), een zonnepaneel dat schuin omhoog steekt,
        een afgebroken paneel, de schotel op de helling en rookende brokstukken. In de romp de
        **zwarte doos** met wat de satelliet-upgrades nodig hebben (netherite, diamanten, goud,
        computerchips, soms een upgrade of drone), eromheen kratten met schroot. In vlaktes,
        woestijnen, savannes, badlands, bossen, taiga's en sneeuwvlaktes; hij ligt 8 blokken in
        de grond, zodat de krater echt is uitgegraven. **Bewaakt**: kom je er met de bril, dan
        duiken er raiddrones op je af (hooguit eens per 10 minuten). Bouwscript
        `tools/generate_crash_site.py`, screenshots `-Ponly=crash`, showcase "Crashed satellite".
        Nieuw: `gradlew runStructureCheck` kijkt in een echte wereld of de structuren er komen
        (beide gevonden, met al hun kisten, in een savanne en een berkenbos).
        Aannames (zeg het als je het anders wilt): de buit, hoe vaak hij voorkomt (ongeveer even
        vaak als de toren) en de raiddrones als bewaking.
  - [x] **Biebia's Industries-bunker, in een berg** (Winter Soldier-stijl) — `163771b`
        Een eigen structuursoort (BiebiaBunkerStructure) zoekt in bergbiomen een rotswand: voor de
        deur vlak en open (16 blokken uitzicht, dus geen kuil of grot), achter de deur over de
        hele bunker minstens 9 blokken berg. De bunker draait mee zodat de ingang naar buiten
        kijkt. Binnen (tools/generate_bunker.py): gevel met embleem en twee torentjes, gang met
        controlepost, grote hal, slaapzaal, reactorkamer (generatoren in lava, kabel over het
        plafond), kluisgang met twee laserwanden van 3 x 3 die uitgaan zonder reactor, en de
        kluis (altijd 2-3 Raven-onderdelen, cryo-cel). Acht soldaten (geen spawner, dus niet te
        farmen) en vijf Raven-torentjes. Vinden: eerst lag er een kaart "Raven Intel" in de zwarte
        doos van een satellietwrak; die heb jij er op 26 september uitgehaald (`29a674c`). In drie
        testwerelden lag de dichtstbijzijnde bunker op 0,7 tot 3 km. Drie nieuwe tests
        (kluis en bewakers, lasers en reactor, alle buittabellen laden).
        (mijn invulling) De indeling, en dat soldaten niet terugkomen.
- [x] **De Biebia's Industries Tower verder uitbreiden** (jouw keuzes op 26 september), in deze volgorde:
  - [x] **Beveiliging die leeft** — `af0d24f`
        Een Security Core op de beveiligingsverdieping (in de rij serverkasten) beschermt
        de hele toren. Beveiligingstorentjes (wit met rood) bij de lift, op de
        beveiligingsverdieping en in de kluis (in plaats van de vier spawners), en vier
        beveiligingsdrones die rond de kern patrouilleren; een neergeschoten drone vervangt de
        kern na 45 s. Zolang niemand de kern hackt, is iedereen een indringer (behalve in
        creative); daarna laten torentjes, drones én laserwanden de eigenaar en zijn team met
        rust. De drones zijn geen "monsters", zodat je eigen torentjes en escorte ze na de hack
        niet neerschieten. Drie nieuwe tests.
  - [x] **De toren overnemen met een laptop** (jouw idee) — `9cbf5ab`
        Hacking Laptop (zwart glas, redstone, computerchip, ijzer; geen bril nodig): klik op de kern,
        het alarm gaat af en alle drones komen op je af. De minigame heeft drie lagen (Firewall,
        Encryption, Override; 3, 4 en 5 treffers): spatie of klik als de heen-en-weer schuivende
        cursor in het groene vak staat, elke laag sneller met een kleiner vak; mis = een treffer
        kwijt. Het spel loopt door, dus je wordt beschoten. Weglopen (> 6 blokken), doodgaan of
        Esc breekt de hack af; de server keurt een onmogelijk snelle hack af. Daarna is de toren
        van jou. Showcase: "Biebia's Industries Tower: security and hack". Drie nieuwe tests.
        (mijn invulling) De vorm van de minigame (timing met een balk).
  - [x] **Biebia's lab** met een harnas-werkbank — `61fde87`
        De Suit Workbench (op stroom van het net): negen vakken voor materialen, knoppen om een
        borstplaat, broek of laarzen te bouwen (zelfde materialen als het oude recept; de muis op
        de knop laat zien wat het kost), en een vak voor een stuk harnas plus een upgrade. Upgrades:
        Thruster Upgrade (laarzen, tot 3: sneller en zuiniger vliegen), Armor Plating (elk stuk, tot
        2: meer schade weg), Laser Cannon Capacitor (borstplaat, tot 2: sterkere laser cannon), en het
        raketpakket. Het harnas staat niet meer in de crafting table. In het lab van de toren staat
        een werkbank met stroom, en in de kisten zit soms een upgrade. Vier nieuwe tests.
        (mijn invulling) Welke upgrades, hoeveel ze doen, en de recepten.
  - [x] **Landingsplatform en hangar** — `ae1af9d`, `47d5e7d`
        *Hangar:* een nieuw blok, de Drone Dock. Een dock pakt zelf een drone uit het arsenaal van
        zijn eigenaar, en die drone staat dan echt op het platform (het 3D-model, met zijn schade).
        Met stroom van het net repareert de dock hem (0,25 leven per seconde). Gaat de drone op pad,
        dan stijgt hij op met een wolkje en blijft er een blauw hologram op zijn plek; komt hij
        terug, dan landt hij weer. Docks naast elkaar geven stroom door, net als een kabel. In het
        arsenaalscherm staat zo'n drone als "In hangar". De hangar van de toren heeft zes docks
        langs de noordmuur, met een kabel naar een warmtegenerator in de hoek; ze zijn van wie de
        toren gehackt heeft. Een dock die je zelf neerzet, is van jou.
        *Dak:* een nieuw blok, de Landing Pad. De toren heeft een platform van 9 x 9 dat aan de
        oostkant uit het dak steekt (zoals in The Avengers), met eronder een gloeiende
        warmtegenerator als stroombron. Tijdens het vliegen wijst de HUD je dichtstbijzijnde
        platform aan (groen ruitje met afstand, dichtbij ook het landingsvak van 5 x 5). Land je
        erop met de stuwraketten aan, dan zegt I.M.T "Welcome home"; zolang je erop staat,
        laadt je bril op en wordt je harnas gerepareerd. Je platforms worden per speler onthouden,
        dus de weg naar huis werkt ook van ver. Showcase: "Biebia's Industries Tower: hangar and landing pad".
        Vijf nieuwe tests.
        Onderweg gevonden: de server kon crashen als een rayshield aanging naast een wand die nog
        aan het schuiven was — opgelost (`3fa38d1`).
        (mijn invulling) Alles hierboven: hoe de docks en het platform werken en eruitzien, de
        getallen, en dat drones niet echt uit de hangar naar buiten vliegen (ze stijgen op en zijn
        weg, zoals uit hun baan).
  - [x] **Inrichting en buitenkant** mooier afgewerkt, met meer detail — `6a99853`, `9fefc87`
        *Buiten:* een voorplein van 14 blokken diep aan de zuidkant (de toren zelf staat nog op
        dezelfde plek): tegels, een ronde fontein met het arc-reactor-embleem op een sokkel,
        bankjes, vier bomen, lantaarnpalen, twee vlaggenmasten en een lage haag. Een luifel met
        lampen boven de ingang, en in de koperen hoekribben om de drie blokken een lamp, zodat de
        toren 's nachts opvalt.
        *Binnen:* lobby: een glazen vergaderzaal en een koffiebar; lab: de Hall of Armor (vijf
        harnassen in vitrines met naambordjes: Mark I, II, III, VII en XLII) en twee werkplekken
        met schermen; archief: een wereldbol en een leeshoek; woonverdieping: een aquariumwand en
        een pooltafel; penthouse: een zithoek en nog een harnas (Mark L). Overal plantjes langs de
        ramen, waar niets in de weg staat.
        Onderweg: de rand van de fontein was eerst van halve platen, en die maakt Minecraft bij
        het neerzetten "nat", waarna het water het plein over stroomde. Nu volle blokken.
        (mijn invulling) Alles hierboven.
- [x] **Meer high tech**, in deze volgorde (eerst wat meteen kan, dan de Raven-tech):
  - [x] **Holotafel: ertsen** — een vijfde stand die de ertsen onder de tafel laat zien — `6cd20ed`
        Tot 16 blokken opzij en 48 diep, als gekleurde blokjes in een doorzichtig blok aarde,
        in de kleuren van de ertsscanner (diamant en smaragd knipperen, kolen en koper zwakker).
        Het holotafelscherm zet de vijf standen nu in twee rijen, anders pasten de namen niet.
  - [x] **Drone-formaties** — je escorte in een vorm: schild om je heen, pijl, cirkel — `065664e`
        G > Drones > Formation wisselt tussen Orbit (zoals altijd), Shield (snel draaiende ring
        dicht om je heen), Arrow (een V schuin achter je die meedraait) en Circle (wijde ring).
        In Shield en Arrow blijven ze in formatie als ze schieten; de bril onthoudt de keuze.
  - [x] **Raven-onderdeel** (het item uit de bunker) en **Raven-soldaten** — `f4888be`
        Een nieuw vijandig wezen: zwart tactisch pak, rode bril en het rode embleem. Ziet hij
        je, dan legt hij zijn lasergeweer aan en schiet hij rode laserstralen (4 schade, om de
        ~2 seconden, tot 18 blokken; een rayshield houdt ze tegen). Hij schiet ook op je drones
        en roept zijn maten als je hem raakt. Het **Raven-onderdeel** valt soms (7%) als jij er
        een verslaat; in de bunker ligt het zeker. Er is een spawn-ei. Eén nieuwe test.
  - [x] **Mini-raketten** uit het harnas — `c030a4a`
        Toets X (of G > Gevecht, of "fire rockets"): zes raketten uit twee buizen op je rug,
        verdeeld over je gemarkeerde doelwitten (anders wat je aankijkt, anders de plek waar je
        kijkt). Ze vliegen eerst omhoog, sturen dan bij (dichtbij steeds scherper) en ontploffen
        zonder blokken te slopen; jou en je team raken ze nooit. Hele harnas nodig, 900 energie,
        12 s herladen. Het **raketpakket** (Raven-onderdeel, computerchip, buskruit, ijzer) klik je
        met de borstplaat aan op die borstplaat; dan zie je de buizen op je rug. Twee nieuwe tests.
  - [x] **Automatische torentjes** — `53ff0b9`
        Rocket Turret (Raven-onderdeel, 2 computerchips, dispenser, observer, ijzer): een draaiende
        kop die op monsters, raiddrones en Raven-soldaten richt (spelers alleen met PvP, nooit
        jij of je team) en op hetzelfde doelwit blijft tot het neer is. Laser: 16 blokken, 4
        schade per seconde, 60 stroom per schot van het net. Raketten: Rocket Ammo (rechtsklik of
        trechter), om de 2,5 s, tot 24 blokken. Redstone zet hem uit (de loop hangt omlaag), en
        hij staat in de redstone-lijst van I.M.T onder Turrets. Rechtsklik = status, shift =
        raketten eruit. Raven Turret: zwart/rood, geen stroom nodig, schiet op spelers en drones;
        gesloopt geeft hij ijzer en soms een Raven-onderdeel. Vier nieuwe tests.
  - [x] Daarna de bunker (zie hierboven), met de soldaten en torentjes erin

### 5. De AI-stem (als laatste)

Alles hieronder zit in `1e91264` (showcase: "AI Brain", `52bc4eb`). Twee nieuwe tests, en een
visuele check (`runVisualCheck -Ponly=brain`) die zinnen naar het echte lokale model stuurt.

- [x] **Drie standen om uit te kiezen** in **G > Systems > AI Brain**: Phrase book (het
      woordenboek), Local model, of Claude
- [x] Ze **praat én doet dingen**: gewone taal wordt een antwoord en/of acties. Beide breinen
      krijgen dezelfde instructies (assets/imttech/imt/brain_prompt.txt), je toestand (leven,
      batterij, tijd, weer, plek, vijanden, waar je naar kijkt, route, waypoints, relais) en wat ze
      onthouden heeft, en moeten één vast JSON-antwoord geven (het model kán niets anders
      schrijven). Lukt het brein niet (geen internet, geen sleutel, model start niet), dan zegt ze
      dat en gebruikt ze het woordenboek.
- [x] De API-sleutel vul je **in het spel** in (sterretjes; bewaard in
      .minecraft/imttech/ai/claude-key.txt, gaat alleen naar Anthropic). Claude loopt via de
      officiële Anthropic-SDK, model claude-opus-5 (te veranderen in config/imttech-client.toml),
      met "effort low" (snel), prompt caching en een automatische fallback als Claude een vraag
      weigert. Getest tot en met het inloggen (een verkeerde sleutel geeft netjes "Claude did not
      accept the API key"); met een echte sleutel nog niet, want dat kost geld: probeer jij het.
- [x] De mod kiest aan de hand van je pc welk lokaal model hij downloadt: Qwen3.5 2B, 4B of 9B,
      naar het geheugen van je videokaart (jouw pc: 4B, 2,7 GB; ± 1 seconde per zin). De vorige
      keer (19 september) vond je 2B "nog niet zo slim"; 4B deed het in de test goed.
- [x] Ze **onthoudt dingen permanent** ("onthoud dat mijn basis Biebia Hoeve heet" → later "hoe
      heet mijn basis?" → "Je basis heet Biebia Hoeve"), in .minecraft/imttech/ai/memory.json;
      "Forget everything" in het scherm wist het. Ze **leert van jouw manier van praten**: begrijpt
      het brein een nieuwe zin, dan zet ze die in het woordenboek (commando's "Learned: ...").
- [x] Weet ze niet wat je bedoelt, dan vraagt ze het ("Wil je een drone sturen of ...?"); zeg je
      daarna "ja", dan doet ze wat ze voorstelde.
- [x] Ze mag **uit zichzelf praten** ("Speaks on her own" in het scherm AI Brain): bij weinig
      leven, een bijna lege batterij, een groep vijanden, de nacht, en als je op je bestemming
      bent; hooguit één keer per minuut. Getest: "You're low on health. Stay alert, that Husk is
      close."
- [x] Op een server hoor je alleen je eigen bril: het brein draait in jouw eigen spel, met jouw
      toestand, en haar meldingen gaan alleen naar jou.
- Ze antwoordt in de taal waarin jij praat (Nederlands of Engels); dat raden we aan de woordjes.
- Nadeel: de Claude-SDK moet mee in de mod-jar (met Kotlin, Jackson en OkHttp): de jar is nu
  ± 28 MB in plaats van 1,6 MB. Getest dat een echte NeoForge-server ermee opstart.
- (mijn invulling) De modelkeuze per pc, de instructies, wanneer ze uit zichzelf praat, wat ze
  wel en niet leert.

### 6. Uit de derde showcase (26 en 27 september)

Jouw notities, per onderwerp. Alles uit het notitiebestand staat hier, dus dat bestand is
weggegooid. Jouw antwoorden op mijn vragen staan erbij. Eerst de kleine dingen, dan de grote.

**Kleine dingen**
- [x] De welkomstzin ("Welcome back, Dev. All systems online.") klinkt de eerste keer twee keer,
      nog voor je in de wereld bent — `3ff3c0e`
      Bij het inloggen kwam de zin van de server binnen vóórdat jouw spel doorhad dat je de bril
      droeg (het laadscherm stond er nog). Dan zei ze hem meteen, en daarna nog eens in de
      opstartanimatie. Nu wacht de animatie tot het laadscherm weg is, en bewaart ze de zin tot
      het goede moment.
- [x] Staat "Voice text" uit, dan toont I.M.T **geen enkele** tekst meer (ook niet "Target 1
      marked", drones, escorte, formaties...): ze zegt het alleen — `7ab75f1`
      Ook de welkomstzin in de opstartanimatie niet (alleen het golfje dat laat zien dat ze
      praat). (mijn invulling) Is haar stem niet geïnstalleerd, dan komt de tekst er toch:
      anders hoor én zie je niets.
- [x] AI-brein alleen Engels: verstaat en spreekt geen Nederlands, en er zit nergens nog
      Nederlands in — `1560aa7`
      Het oor zegt zelf welke taal het hoorde; is dat geen Engels, dan zegt ze "Sorry, I only
      understand English." en doet ze niets (Engels met een accent, dat het oor voor Nederlands
      aanzag, telt wel). Het brein antwoordt altijd Engels, de Nederlandse voorbeelden zijn uit
      haar instructies, en het woordenboek verloor 178 Nederlandse woorden en zinnen
      ("droontjes", "aanzetten", "weerbericht"...). Je opgeslagen woordenboek wordt vanzelf zo
      opgeschoond (ook Nederlandse zinnen die ze geleerd had); je eigen Engelse zinnen blijven.
      Haar stem was al Engels.
- [x] Het harnas is niet stapelbaar — `092f392`
      Het had geen duurzaamheid, en dan laat Minecraft het tot 64 stapelen.
- [x] Satellietscherm: het vak met de 3D-satelliet iets kleiner, zodat de rest beter past — `a7e6748`
      Op jouw schermformaat van 144 x 104 naar 128 x 72; de tabel eronder krijgt ruimere regels en
      de onderdelen rechts meer breedte. Nagekeken op screenshots (`-Ponly=satellite -Psize=1600x900`).
- [x] Satellietwrak: de kaart naar de bunker blijft weg (door jou weggehaald in `29a674c`) — `8d9c732`
      Ook de rest opgeruimd: de naam van de kaart, de structuur-tag, de controle in
      `runStructureCheck`, en de uitleg in de showcase en LEESMIJ.md.

**HUD en satelliet**
- [x] Doelwit-analyse bij het wezen zelf in plaats van in een paneel rechts: beugels om het
      wezen en een lijntje naar een kaartje ernaast (naam, leven, pantser, afstand, dreiging,
      wat hij vasthoudt). Jouw keuze. — `f2ccb17`
      Het lijntje groeit uit de hoek van de beugels als je een nieuw doelwit aankijkt, daarna
      verschijnt het kaartje (de naam letter voor letter). Het kaartje loopt mee met het wezen
      en springt naar links als het rechts niet past. Screenshot: `-Ponly=hud`.
- [x] Satelliet zonder standen: elke aanval een eigen toets die meteen vuurt: **K** = precisie,
      **P** = bombardement, **C** = schuivende laser (1x = begin, 2x = eind). Jouw keuze. — `8d89f30`
      Ook het G-menu en je stem vuren meteen ("precision strike", "missile barrage"...). Met een
      werkende bril op opent P niet meer het scherm "sociale interacties" van Minecraft. Een
      half gekozen laserlijn vervalt als je K of P gebruikt. Nieuwe test.
- [x] Satelliet: een echte raket die uit de ruimte neerkomt in plaats van een bliksemschicht.
      Precisie = één raket (volgt een bewegend doel), bombardement = een regen raketten. De
      schuivende laser blijft een laser; de rode richtstraal tijdens het opladen blijft. Jouw keuze.
      — `8d89f30`
      Een witte raket met rode neus en vinnen en een gloeiende vlam, 150 blokken boven het doel
      losgelaten. Hij komt schuin binnen en duikt het laatste stuk bijna loodrecht (zo raakt hij
      geen muur naast het doelwit), met een dik rookspoor en een fluittoon die je bij het doel
      hoort aankomen. Hij vertrekt zo dat hij inslaat als het aftellen op nul staat. Schade,
      kraters en schokgolf komen bij de inslag; een dak of andermans koepel vangt hem op. Twee
      nieuwe geluiden (`satellite_missile`, `satellite_impact`). Screenshots: `-Ponly=missile`.

**Harnas**
- [x] Mini-raketten upgraden: sterkere raketten en meer raketten per salvo — `c6fbe64`
      Twee nieuwe upgrades voor de borstplaat (alleen met een raketpakket erop), elk tot 3 keer:
      **Rocket Magazine** (2 raketten meer per salvo, tot 12) en **Rocket Warhead** (40% meer
      schade, ook de splash). Inbouwen aan de werkbank; recepten met computerchips, ijzer en buskruit
      of TNT. Een ouder harnas leest gewoon 0 upgrades. Nieuwe test.
      (mijn invulling) Welke upgrades, hoeveel ze doen, de recepten, en dat de energie per salvo
      gelijk blijft.
- [x] Landingsplatform: een scherm met een batterij, zodat het platform stroom opslaat en je
      sneller oplaadt — `39d34aa`
      Rechtsklik op je eigen platform opent zijn scherm. De batterij (10.000) vult zich langzaam
      met wat het net over heeft (300 per seconde; de generatoren houden de helft achter, zoals
      bij het laadstation) en laadt je bril op met 1600 per seconde, vier keer zo snel als het net
      alleen (400). Een vakje voor tot 4 **Battery Upgrades**, elk +15.000. Het scherm toont de
      batterij, het net, hoe snel hij vult en hoe snel hij jou oplaadt. Screenshot:
      `-Ponly=padscreen`. Nieuwe test.
      (mijn invulling) Dat de batterij met Battery Upgrades groter wordt, en alle getallen.
      Onderweg: de test die de getallen in het configbestand telde, telt nu zelf (hij moest bij
      elk nieuw getal met de hand omhoog).

**Rayshields**
- [x] Laserpoorten die aan elkaar hangen worden één geheel: één blok met redstone zet ze allemaal aan
      — `b31e7d9`
      Zenders die tegen elkaar aan zitten (in welke richting ook) zijn één poort. Ze geven ook de
      stroom van één kabel aan elkaar door. Nieuwe test (drie op elkaar: redstone onder, stroom
      boven, alle drie gaan aan en weer uit).
      (mijn invulling) Dat ze ook stroom doorgeven.
- [x] De laserwand dunner, in de stijl van de koepel, met een animatie van links naar rechts in
      plaats van omhoog, en veel subtieler: geen witte lijnen maar de eigen kleur, iets lichter of
      donkerder — `b31e7d9`
      Half zo dik (1 pixel), een honingraat van zeshoeken die naadloos over de blokken doorloopt
      (randen iets lichter, een paar vakjes iets donkerder, de rest doorzichtig) en rustig opzij
      schuift. Screenshots: `-Ponly=laser`.

**Vijanden**
- [x] Raven-soldaten slimmer: samen aanvallen (niet allemaal recht op je af), en zit je achter een
      muur, dan zoeken ze een weg naar je toe in plaats van te blijven staan — `7ac5399`
      Een eigen gevechts-AI (`RavenCombatGoal`): soldaten met hetzelfde doelwit vormen een ploeg.
      Ziet één je, dan weten de anderen in de buurt het ook. Ze verdelen zich rondom je; zie je
      ze niet (muur), dan zoekt elk aan zijn eigen kant een plek waar hij kan komen en van waaruit
      hij je ziet; zit je binnen, dan zoeken ze een weg naar binnen en doen ze deuren open. Tijdens
      het schieten stappen ze opzij, en ze lopen achteruit als je te dichtbij komt. Twee nieuwe
      tests (om een muur heen, en een maat in een hokje waarschuwen).
      (mijn invulling) Hoe ze precies samenwerken; geen granaten of zelf muren slopen.

**Stroom** (in deze volgorde: eerst de kabels, want de rest bouwt erop)
- [x] Kabels tekenen in plaats van blokken: met een kabelrol klik je een begin- en eindpunt en ligt
      er een platte kabel over de grond of muur. Geen blok, dus je kunt er blokken op zetten (dan
      zit de kabel in de muur). Met de bril zie je verborgen kabels, een schaar knipt ze door.
      Jouw keuze. — `1beaca7`
      Tekenen gaat met de rechtermuisknop ingedrukt: waar je kijkt komt de kabel; kijk je verder
      weg, dan trekt hij een lijn langs de oppervlakken. Shift + rechtsklik raapt op. De kabel is
      data bij de chunk (geen entity: honderden entities zouden laggen). Getekend als een dun
      kabeltje tegen vloer en muur, met nette hoeken; verborgen kabels lichten met de bril blauw
      op door de muur. Oude kabelblokken (oude werelden, de toren en de bunker) worden vanzelf
      getekende kabels, en een kabel die in een muur zat laat de muur heel. Twee nieuwe tests;
      screenshots `-Ponly=power`.
      (mijn invulling) Ingedrukt houden in plaats van twee klikken (dat voelt meer als tekenen).
- [x] Alle torentjes en beveiliging hebben stroom nodig; zonder stroom doen ze niets — `744a668`
      Raket-, Raven- en beveiligingstorentjes: zonder stroom hangt de loop omlaag en valt er geen
      schot; elk laserschot (60) en elke raket (20) kost stroom. De beveiligingskern van de toren
      ook: zonder stroom geen drones, en de drones zakken met gedoofde lampjes naar de grond.
- [x] Een elektriciteitskast waar kabels samenkomen, met schakelaars om stroomgroepen aan en uit te
      zetten; ook te bedienen via het redstone-tabblad (op afstand alleen door de eigenaar). Wie
      bij de kast staat kan hem bedienen, dus in de toren en de bunker staat hij in een zwaar
      bewaakte, dichte kamer. Een kabel doorknippen zet een deel van de stroom uit. Jouw keuze.
      — `744a668`
      De **Power Cabinet**: een schakelaar per kant (voor, achter, links, rechts, boven, onder);
      het net loopt alleen door kanten die aan staan. Rechtsklik = het scherm; "Link to I.M.T."
      (alleen de eigenaar) zet de zes schakelaars in het redstone-tabblad, ook met je stem.
      *Toren:* de krachtcentrale kreeg een mantel van obsidiaan met de kast erin: één kant voor
      de lasers en de kluis (met de vier kluistorentjes), één voor de kern en alle andere
      torentjes (de lobby via de koperen zuil van de lift). Twee torentjes erbij op de
      beveiligingsverdieping (één bij de krachtcentrale, één bij de lift; er stond er een ín
      de krachtcentrale, die was al die tijd weg). *Bunker:* de kast in de hoek van de
      reactorkamer, met een torentje en een soldaat ernaast; de lasers aan de ene kant, alle
      zes Raven-torentjes (door de vloer) aan de andere.
      Kabels in de gebouwen zijn nu onzichtbare markers die bij het bouwen een getekende kabel
      worden, zodat het blok op die plek (koperen zuil, vloer) blijft staan. Zes nieuwe tests;
      screenshots `-Ponly=cabinet`.
      (mijn invulling) De indeling van de kasten en waar de kabels lopen, de extra torentjes, de
      obsidiaan mantel, en de recepten.

**Groot**
- [x] De toren hacken als een echte terminal: commando's typen, zoeken welke poort openstaat,
      daarlangs inbreken, het beveiligingsprogramma vinden en overnemen, met verschillende
      puzzels (niet alleen op het goede moment op spatie drukken) — `0f1fe57`
      Vier stappen. *Recon:* `scan` toont alleen "filtered" poorten; in het firewall-logboek
      (`logs`) staan twee poorten die voor onderhoud open zijn gezet, waarvan er één later weer
      dicht is. *Breach:* een geheugendump met acht woorden; een fout wachtwoord zegt hoeveel
      letters op de goede plek staan, na vier fouten even geblokkeerd. *Search:* met `ls`, `cd`
      en `cat` de codenaam van het beveiligingsprogramma vinden (er draaien er een paar met zo'n
      naam), met `ps` zijn nummer. *Override:* `override <nummer>` en dan vier keer de
      timingbalk. Tab vult aan, pijltje omhoog herhaalt. Nieuwe test die de terminal uitspeelt;
      screenshots `-Ponly=hack`.
      (mijn invulling) De puzzels zelf, de commando's en de teksten.
- [x] De harnas-werkbank als hologram zonder scherm: boven de werkbank zweeft het harnas; kijk
      naar een stuk en rechtsklik = bouwen (materiaal uit je rugzak, het stuk bouwt zich op in het
      hologram en vliegt naar je toe), upgrade in je hand + klik op een stuk = inbouwen. Jouw keuze.
      **Gedaan** (3fbd94e): het echte harnasmodel als blauw hologram dat ronddraait; waar je naar
      kijkt licht op, met een kaartje naast je vizier (kosten groen/rood, of wat een klik doet).
      Het scherm is weg. Tests: bouwen uit de rugzak, laarzen neerleggen + upgrade, een stuk
      pakken, een oude werkbank leegmaken. Screenshots `-Ponly=workbench`.
      (mijn invulling) Een klaar stuk gaat meteen aan als die plek vrij is (anders in je rugzak);
      met een lege hand pak je een stuk dat erop ligt; met shift doet rechtsklikken het gewone;
      één ding tegelijk; in creative gratis; verder dan 24 blokken weg = het blijft liggen.
- [x] Het wachtwoord raden bij de hack is niet leuk en te moeilijk: maak de aanwijzingen
      duidelijker (27 september). **Gedaan**: de woorden in een dump lijken nu op elkaar
      (ROCKET, POCKET, PLANET, ...), een fout woord laat zien welke letters goed staan
      (`ROCKET -> ____ET`), de terminal streept zelf door wat niet meer kan en noemt wat er nog
      over is; Tab vult alleen die aan. Vijf pogingen in plaats van vier. Nieuwe test speelt het
      300 keer: gemiddeld minder dan 3 pogingen.
      (mijn invulling) Het automatisch doorstrepen, de woordenlijst en de vijf pogingen.
- [x] De stap Search is ook te moeilijk en niet duidelijk: maak het simpeler, bv. met een README
      waar je de instructies uit haalt (27 september). **Gedaan**: na het inloggen zegt de
      terminal "type cat README.txt"; de README staat bovenaan en legt de drie stappen uit
      (`cat security.conf`, `ps`, `override <PID>`). `cat` vindt een bestand ook op alleen zijn
      naam (en Tab vult dat aan), en foute pogingen zeggen wat je moet doen. Test uitgebreid;
      screenshot `k035_hack_readme`.
      (mijn invulling) De tekst van de README en de hulpzinnen bij fouten.
- [x] De override-minigame moet meer iets zijn als een commando: override, dan iets met disable,
      set owner en je naam (27 september). **Gedaan**: de timingbalk is weg. `override <PID>` opent
      de console van het beveiligingsprogramma (`status` toont owner, watchdog, turrets, drones);
      `disable watchdog` en dan `set owner <jouw naam>`. Met de waakhond nog aan zet hij de
      eigenaar terug; een andere naam dan de jouwe kent hij niet. Test speelt de console uit;
      screenshot `k05_hack_takeover`.
      (mijn invulling) Dat de waakhond eerst uit moet, en de teksten van de console.
- [x] De README iets minder "straight to the point"; je laptop moet je neerzetten op het
      beveiligingsblok; en je voortgang niet kwijt als je Esc doet (27 september). **Gedaan**: de
      README is een briefje van Biebia met aanwijzingen (config in /etc, nep-namen in `ps`, override
      op nummer, eerst de waakhond stil, dan jezelf eigenaar) in plaats van letterlijke commando's.
      De laptop klik je op de kern: hij staat dan opengeklapt bovenop (nieuw blok met model), en
      rechtsklik opent de terminal. De laptop onthoudt het getal van de puzzel en elk commando;
      openen speelt dat na, dus Esc/weglopen/doodgaan sluit alleen het scherm. De server speelt ook
      zelf na of je klaar bent (vroeger meldde het spel alleen "klaar"). Tests: laptop op de kern
      + uitspelen, te snel, voortgang bewaard; screenshots `k025_hack_laptop_on_core`,
      `k026_hack_restored`.
      (mijn invulling) Alleen wie hem neerzette kan hem gebruiken; na de overname krijg je de laptop
      terug; afbreken = opnieuw beginnen; de tekst van het briefje.
- [x] Laserdeuren met redstone maar zonder stroom maken heel veel lawaai: ze moeten dan gewoon
      niet aangaan (27 september). **Gedaan**: zonder stroom wachten ze stil (vroeger klikte elke
      zender om de 2 seconden); alleen bij het aanzetten van de redstone één zacht klikje, bij de
      zender waar de redstone op zit. Aangaan kan pas met stroom voor 5 seconden, zodat hij niet
      aan-uit-aan flikkert bij een beetje stroom. Test: bijna lege generator = blijft uit, vol =
      gaat vanzelf aan.
      (mijn invulling) De 5 seconden en het ene klikje.
- [x] De holotafel moet werken zonder de bril; wat echt van de bril is (zoals waypoints) wel met
      bril, de kaart enzo niet (27 september). **Gedaan**: bedienen (scherm, recept, kleur) kan
      zonder bril. De kaart, het recept en de ertsen laat de tafel zelf zien; je waypoints op de
      kaart en de standen Arsenal en Satellite alleen met je eigen bril op (anders een rood kruis
      met een bordje, en een rode uitleg in het scherm). Test: zonder bril kleur wisselen en een
      recept op tafel leggen; screenshots `11_holo_map_no_glasses`, `11a_holo_arsenal_no_glasses`.
      (mijn invulling) Welke dingen "van de bril" zijn: waypoints, drones en satelliet (spelers en
      mobs op de kaart en de ertsen niet).

### 7. De beveiliging upgraden (28 september)

Jouw wensen: de hack ongeveer zoals nu maar logischer; een desktop op de beveiligingskern waar je
camera's, drones en torentjes bekijkt en bedient (alleen als de beveiliging van jou is); zelf een
wachtwoord en je eigen mappen; anderen kunnen jouw beveiliging opnieuw hacken; elk systeem een
IP-adres, en verbinden met SSH. Jouw antwoorden: een echte desktop met vensters; een
bewakingsmuur met live beeldjes zoals de mod Vista (zonder lag, ook van ver); status, aan/uit en
wie ze aanvallen; de kern en camera's ook zelf bouwen; SSH-inloggen overal maar hacken alleen ter
plekke; bij een wachtwoord geen dump maar raden, social engineering (in het echt), woordenlijst of
bruteforce; alleen a-z en 0-9, hoogstens 8 tekens; mappen beheren, notities, security.conf
verstoppen, valstrikken; bij een overname alarm, je bestanden blijven, nieuw wachtwoord.
Het volledige plan: `C:\Users\senne\.claude\plans\ik-wil-de-starktower-zesty-eich.md`.

- [x] **1. Fundament**: elke kern is een klein systeem — `0f43f59`
      IP (10.x.y.z, uniek in de wereld; een nieuw bestand `imttech_network.dat` onthoudt welk
      IP bij welke kern hoort), SSH-poort, naam (`biebia-sec-core` in de toren), het wachtwoord
      (alleen als hash, nooit leesbaar), mappen en bestanden met systeembestanden en
      valstrikken, en een logboek. Een kern uit een oude wereld krijgt het bij de eerste tick.
      Drie nieuwe tests.
- [~] **2. De hack op de server, de laptop in je hand** — opzijgezet, niet herbouwd.
      De overname met de laptop uit hoofdstuk 6 blijft gewoon werken en staat in git; die gebruik
      je nog steeds. De herschreven server-terminal hiervoor (netscan/ssh/dump) werd telkens door
      een geautomatiseerd veiligheidsfilter tegengehouden, dus die staat buiten de build in
      `chapter7-wip/` (SystemShell.java, DumpPuzzle.java, TermLine.java).
- [~] **3. Een wachtwoord kraken** (raden, woordenlijst, bruteforce) — om dezelfde reden niet
      gebouwd. Een wachtwoord *zetten* en met het goede wachtwoord *inloggen* kan wél (stap 4 en 9).
- [x] **4. De Security OS-desktop** — `78916d6` (met de toegang/gegevens eronder: `20d2b34`)
      Rechtsklik met lege hand op de kern opent voor de eigenaar een desktop met sleepbare vensters,
      een taakbalk en icoontjes. Drie apps nu: **Status** (naam, IP, SSH-poort, stroom, wachtwoord
      ja/nee), **Logs** (het logboek van de kern) en **Settings** (een a-z0-9-wachtwoord van 4-8
      tekens zetten of wijzigen; dat gooit andere sessies eruit). Alles is een momentopname die de
      server maakt en alleen naar wie ingelogd is stuurt, dus het wachtwoord en de mappen komen
      nooit in het spel van een ander. De **Terminal**-app hoort bij de geparkeerde server-terminal
      (stap 2) en zit er dus nog niet bij. Twee nieuwe tests.
- [x] **5. Bestanden, notities en valstrikken**
      Twee nieuwe apps op de desktop. **Files** werkt als de Verkenner: knoppen voor een map
      omhoog, nieuwe map, nieuw bestand, hernoemen, verwijderen (met "Sure?") en valstrik;
      dubbelklik opent, slepen naar een map verplaatst, rechtsklik geeft een menu met "Mark as
      trap". `security.conf` (SYS) kun je verplaatsen en hernoemen, niet weggooien. **Notes** is
      een teksteditor (het tekstvak van Minecraft zelf: selecteren, Ctrl+C/V, Ctrl+S, woordomslag;
      hoogstens 4096 tekens); sluiten met wijzigingen bewaart vanzelf. Een **valstrik** gaat af als
      iemand anders dan de eigenaar hem opent (straks via SSH met het wachtwoord, of de terminal):
      een regel in het logboek, een I.M.T-alarm voor de eigenaar, de lezer vliegt eruit en is
      30 s buitengesloten (`TRAP_LOCKOUT` in de config), en de beveiliging jaagt dan op hem, ook
      als "attack players" uit staat. Alleen de eigenaar ziet welk bestand een valstrik is. Fouten
      en meldingen staan nu rechts in de taakbalk (het actiebalk-bericht zag je niet achter de
      desktop). Screenshots: `-Ponly=os`. Eén nieuwe test.
      De terminal-commando's (`mkdir`, `mv`, `rm`, `nano`) horen bij de geparkeerde terminal (stap 2). Commit: `eb241c4`.
- [x] **6. Apparaten en toegang, en de kern zelf bouwen** (de gegevens eronder: `20d2b34`)
      Twee nieuwe apps. **Devices** toont wat bij de kern hoort: de beveiligingstorentjes in het
      gebied en de Biebia's Industries-torentjes van de eigenaar (aan/uit per torentje, stroom, raketten, doelwit),
      de drones (levens, wat ze doen) en de laserpoorten; bovenaan "Drones" (uit = ze landen naast
      de kern en er komen er geen nieuwe) en "Lasers" (uit = alle laserpoorten in het gebied gaan
      open, ook al staat er redstone op). **Access**: "Attack monsters", "Attack other players" en
      de vertrouwde spelers (toevoegen op naam, weghalen). Een Biebia's Industries-torentje van de eigenaar in zijn
      eigen gebied volgt nu ook die regels. **Zelf bouwen**: een recept voor de Security Core
      (ijzerblokken, computerchip, observers, Raven-circuit, redstoneblok); neerzetten maakt hem van
      jou met een IP, en de desktop opent dan meteen Settings om een wachtwoord te zetten. Een kern
      waarvan het gebied een ander netwerk overlapt (binnen 60 blokken) lukt niet: "too close to
      another security network", en je houdt het item. Afbreken geeft de kern terug.
      Screenshots: `-Ponly=os`. Twee nieuwe tests. Commit: `0cdbeca`.
- [x] **7. Security Camera en de bewakingsmuur** (in de buurt)
      Een nieuw blok: de **Security Camera** (recept: ijzer, glas, observer, computerchip; 2 stuks), aan
      de muur, het plafond of de vloer. Hij heeft een beetje stroom nodig (`CAMERA_UPKEEP`), hoort bij
      de kern in wiens gebied hij hangt, en een naam uit een aambeeld komt bij zijn beeld te staan.
      Rechtsklik zegt hoe hij ervoor staat. Nieuwe app **Cameras**: de bewakingsmuur met een live
      beeldje per camera (CRT-randje, naam, knipperend REC; "NO SIGNAL" zonder stroom of als de
      camera's uit staan, "OUT OF RANGE" buiten wat je spel klaar heeft staan). Klik = groot beeld,
      draaien en kantelen met de pijltjes, de pijltjestoetsen of door het beeld te slepen (hooguit
      60 graden). Het beeld tekent de mod zelf (`client/os/camera/FeedRenderer`): blokken, wezens
      (ook jij) en blokken met een model (kisten, torentjes), met een EIGEN lijst van wat in beeld
      is, dus een camera in een dichte kamer op een andere verdieping ziet zijn kamer wel. Tegen lag
      (zoals Vista, `FeedPlanner`): alleen beelden die op het scherm staan, 5 per seconde (groot 10),
      minder onder de 60 fps (nooit minder dan 2), om de beurt, samen hooguit 3 ms per frame, en na
      5 s weggegooid; instelbaar in `config/imttech-client.toml`. Devices kreeg de schakelaar
      "Cameras". Nieuw: een access transformer (`META-INF/accesstransformer.cfg`). Met
      "Fabulous!"-graphics zie je geen water/glas in het beeld (dat tekent Minecraft dan apart).
      Screenshots: `-Ponly=cameras`. Twee nieuwe tests. Commit: `33883fe`.
- [x] **8. Echte camera's in de toren**
      De drie nep-camera's (observers) op de beveiligingsverdieping zijn echte camera's aan het
      plafond (45 graden naar de kamer gedraaid), en er hangen er drie bij: "Lobby" (op de
      logowand, met zicht op de receptie en de ingang), "Laser hall" (aan het plafond van de
      lasergang) en "Vault" (boven het luik, met zicht op de sokkel). Elk hangt met een verborgen
      kabel aan het net van de toren (de lobby aan de beveiliging, de rest aan de lasers: knip je
      die door, dan geven ook de camera's NO SIGNAL). Biebia's kern heeft nog steeds geen wachtwoord.
      Het NBT-bestand is opnieuw gemaakt (gecontroleerd: verder niets veranderd). Screenshots:
      `-Ponly=towercams`. Eén nieuwe test. Commit: `38c744e`.
- [~] **9. SSH van overal** — opzijgezet, net als stap 2 en 3.
      Het inlogvenster hiervoor werd door hetzelfde geautomatiseerde veiligheidsfilter
      tegengehouden. Wat er al was (de serverkant), staat buiten de build in
      `chapter7-wip/step9/` (een patch en twee netwerkberichten).
- [~] **10. Camerabeeld van ver** (mixins, achter een schakelaar) — opzijgezet: alleen nuttig
      met een sessie op afstand (stap 9). Bij de kern zelf hangen alle camera's binnen 30 blokken,
      dus daar is het beeld altijd "in de buurt" en werkt het al (stap 7).
- [x] **Camera's een andere naam geven** (jouw vraag, 3 oktober)
      In het grote camerabeeld staat onder de pijltjes de knop **Rename**: de naam bovenaan wordt
      een invoervak (de oude naam geselecteerd, dus typen vervangt hem). Enter of Save bewaart, Esc
      of ergens anders klikken laat het zoals het was, en een lege naam maakt hem weer "Camera 3".
      De server maakt de naam netjes (geen kleurcodes, hooguit 32 tekens) en zet het in het
      logboek. Breek je een camera met een naam af, dan houdt het item die naam (ook een naam uit
      een aambeeld). Screenshots: `-Ponly=cameras` (`c03b`, `c03c`). Eén nieuwe test.
- [x] **Showcase**: twee nieuwe functies na "Biebia's Industries Tower: security and hack": "Security OS desktop"
      (Files met security.conf, het rechtsklik-menu, Notes, Devices, Access, Logs) en "Security
      cameras" (de bewakingsmuur van de toren, het grote beeld, draaien, NO SIGNAL, een camera van
      dichtbij). Ze staan in de showcase als NIEUW.

### 8. Redstone programmeren op een server (7 oktober)

Jouw wensen: je redstone programmeren in een diagram (een relais gaat aan, dan gaat een ander
relais aan; met if/else); een keycardlezer of codepaneel start iets, en een centraal systeem
kijkt wat er moet gebeuren. Dat systeem draait op een **server**: een multiblok van
**serverracks** die je naast of op elkaar zet. In de racks steek je **CPU, RAM en opslag**, zodat
je meer kunt programmeren en je redstone sneller reageert. De server hangt aan je eigen
beveiliging (Security OS). En de rayshields krijgen **Star Wars-geluiden**.

Jouw antwoorden: het diagram kan **allebei**: blokjes met draadjes, én regels code (sneller, maar
moeilijker), en je wisselt heen en weer (blokjes worden code, code wordt blokjes); keycards met
een **niveau (1-5) en een eigen nummer** (een gestolen kaart blokkeer je); losse **CPU, RAM en
opslag** in de racks; het Star Wars-geluid op **alle drie** de rayshields (laserpoorten,
persoonlijk schild, koepel).

Mijn invulling (de tweede vragenronde klikte je weg; zeg het als je iets anders wil):

- **De code lijkt op Python**: inspringen, `if`/`else`, dubbele punten.
  ```
  when keycard("Lab door"):
      if card.valid and card.level >= 3:
          accept()
          pulse("Vault door", 3)
      else:
          deny()
          alarm("Wrong card at the lab")
  ```
- **Wat een diagram start**: een relais dat aan of uit gaat, een keycardlezer, een codepaneel, en
  een timer (`every(10)`: elke 10 seconden). Meer kan later (gewone redstone, een camera die
  iemand ziet, een alarm).
- **Bereik**: de server stuurt alles in het gebied van zijn beveiligingskern (relais,
  laserzenders, koepels, torentjes, schakelaars van de stroomkast die je in je redstone-lijst hebt).
- **Een hacker** die je kern overneemt, krijgt ook je diagrammen: hij kan ze zien en veranderen
  (net als je bestanden, die blijven ook staan).
- **Echte Star Wars-geluiden** mogen niet in de mod (auteursrecht). Ik maak ze zelf na met
  `tools/generate_sounds.py`: een brommend zoemen, een "vwoem" bij aan/uit, een knetter bij een
  treffer.

De stappen, één per commit:

- [x] **1. Rayshield-geluiden**
      Vier nieuwe geluiden (`rayshield_hum`, `_on`, `_off`, `_hit`): twee zaagtanden van 92 en 93 Hz
      die tegen elkaar zweven ("wom-wom"), met af en toe een knetter. **Zoemen**: een lus zolang een
      laserzender of koepelgenerator binnen 24 blokken aan staat (`client/shield/RayshieldHum`,
      hooguit 6 tegelijk, een stapel zenders deelt er één; de koepel dieper en harder). **"Vwoem"**
      bij aan en een wegzakkende toon bij uit: laserpoort, koepel en je persoonlijke schild (G-menu
      of stem). **Knetter**: tegen een laserwand lopen, een treffer op je schild of op de koepel.
      (Later: het zoemen heel stil gemaakt, zie "Nog nooit in het spel bekeken".)
      Ik kan zelf niet luisteren: zeg het als ze te hard, te zacht of te raar klinken (opnieuw maken:
      `py -3.12 tools/generate_sounds.py rayshield_hum`). Commit: `e565efe`.
- [x] **2. Serverracks**
      Een nieuw blok, de **Server Rack** (recept: ijzer, ijzeren tralies, computerchip, redstone), met
      4 vakjes. Racks die elkaar raken (naast of op elkaar) zijn samen de server van de kern in wiens
      gebied ze staan; staan er losse groepjes, dan telt de grootste ("NOT CONNECTED" voor de rest).
      Elk rack heeft stroom nodig (`RACK_UPKEEP`) en geeft die door aan het rack ernaast, dus een
      kast vol racks heeft genoeg aan één kabel. Drie nieuwe onderdelen: **CPU** (1 CPU reageert in
      0,5 s, elke extra 0,15 s sneller, nooit sneller dan 1 tick), **RAM** (2 diagrammen tegelijk per
      latje; je krijgt er 2 per recept) en **SSD** (24 blokjes per stuk). Zonder één van elk draait
      de server niet. Lampjes op de voorkant knipperen als het rack meedraait. Rechtsklik: de vier
      vakjes, en wat de hele server kan (of waarom niet). De app Status op de desktop zegt het ook.
      Alle getallen staan in de config onder `[logic]`. Screenshots: `-Ponly=racks`. Twee nieuwe tests.
      Commit: `926edbd`.
- [x] **3. Keycards en de keycardlezer**
      **Keycard** (recept: computerchip, 2 papier, ijzer = 4 lege kaarten). Nieuwe app **Keycards** op de
      desktop: naam en niveau (1-5) invullen, "Write card" schrijft de eerste lege kaart in je
      rugzak; hij krijgt het volgende nummer (#1001, #1002...) en de kleur van zijn niveau (wit,
      groen, blauw, paars, goud). Daaronder alle kaarten van dit netwerk met **Block/Unblock**. Een
      kaart is alleen geldig in het netwerk dat hem schreef, als hij niet geblokkeerd is. De kaarten
      staan in de kern (dus wie je kern overneemt, krijgt de lijst ook). De **Keycard Reader**
      (recept: ijzer, glas, redstone, computerchip = 2 stuks) hangt aan de muur, krijgt een naam
      ("Reader 1", of uit een aambeeld) en heeft een lampje: oranje = gelezen, groen = goed, rood =
      geweigerd of de server draait niet. Elke gelezen kaart komt in het logboek. Screenshots:
      `-Ponly=keycards`. Eén nieuwe test. Commit: `68de29b`.
- [x] **4. Het codepaneel**
      De **Code Panel** (recept: ijzer, stenen knoppen, redstone, computerchip = 2 stuks) hangt aan de
      muur, met een naam ("Panel 1") en hetzelfde lampje als de lezer. Rechtsklik opent de toetsen:
      0-9, C (wissen) en OK, met de muis of je toetsenbord (Backspace, Enter); op het schermpje zie je
      sterretjes. OK stuurt de code naar de server en sluit het scherm. Welke code goed is, staat in
      je diagram (`if code == "4321":`), niet in het paneel; de server neemt alleen cijfers aan (1-8),
      alleen van dichtbij (6 blokken), en zet NIET de code in het logboek (een hacker die je
      logboek leest, ziet hem dus niet). Screenshots: `-Ponly=keycards`. Eén nieuwe test.
      Commit: `9b0f847`.
- [x] **5. Het brein van de server** (nog zonder app: die komt in stap 6 en 7)
      **De taal** (`logic/program/`): triggers `keycard("...")`, `codepanel("...")`,
      `turned_on("...")`, `turned_off("...")`, `every(10)`; `if`/`elif`/`else` met `and`, `or`,
      `not`, `==`, `!=`, `<`, `>`, `<=`, `>=`; gegevens `card.level`, `card.number`, `card.valid`,
      `card.name`, `code`, `player.name`, `player.trusted`, `player.owner`, `is_night`, `is_day`,
      `is_on("...")`; acties `on`, `off`, `toggle`, `pulse("...", 3)`, `wait(2)`, `accept()`,
      `deny()`, `say("...")`, `alarm("...")`, `log("...")`. Fouten krijgen een regelnummer en uitleg
      voor beginners ("Line 2: I don't know \"opn\". Did you mean on?", "code is text: put the code
      between quotes"). **Code en blokjes** zijn hetzelfde diagram (`GraphConverter`): elke when,
      if en actie is één blokje; een if heeft de uitgangen yes, no en after. Code naar blokjes zet
      ze netjes op een rij; een when-blok dat je niet veranderde, houdt zijn plek. **Uitvoeren**
      (`LogicRunner`): een run begint na de reactietijd van je CPU's, zoveel tegelijk als je RAM
      toelaat (de rest wacht in een rij van hooguit 32, dan "Out of memory"), alleen diagrammen die
      op je SSD's passen, hooguit 40 triggers per seconde (tegen eindeloze kettingen). De namen van
      apparaten komen uit de redstone-lijst van de eigenaar (alleen in het gebied van de kern) en de
      kern onthoudt ze, dus het werkt ook als jij offline bent. `alarm()` zet een rode regel in het
      logboek, waarschuwt de eigenaar, luidt een bel en laat de beveiliging jagen op wie het deed.
      Alles staat in de kern: wie hem overneemt, krijgt ook de diagrammen. Zes nieuwe tests (in een
      eigen bestand, `gametest/LogicGameTests.java`). Commit: `c825b4c`.
- [x] **6. De Logic-app: code**
      Nieuwe app **Logic** (icoon `{}`) op de desktop; zijn venster vult het hele scherm. Links je
      diagrammen (groen bolletje = aan, * = niet bewaard), **+** voor een nieuw diagram, en hoe vol
      je SSD's zijn en hoeveel er bezig is. Rechts een eigen **code-editor** (`client/os/CodeEditor`):
      regelnummers, kleuren zoals in VS Code (when/if/else roze, triggers cyaan, acties lichtblauw,
      gegevens geel, tekst groen, getallen oranje, commentaar grijs), Enter springt na een `:`
      vanzelf 4 spaties in, Tab/Shift+Tab, Backspace haalt een hele inspringing weg, selecteren,
      Ctrl+A/C/X/V/Z/Y/S. Knoppen: **Check** (de foutregel wordt rood en de editor springt erheen),
      **Save** (de server controleert alles nog eens: kloppen de blokjes, past het op de SSD's, is
      de naam vrij), **On/Off**, **Delete** (twee keer klikken), **Help**: alles wat je kunt
      gebruiken, met de namen van jouw apparaten, lezers en panelen; klik erop en het komt in je
      code; met R geef je een lezer of paneel een andere naam. Je commentaar (#) blijft bewaard
      zolang de code bij de blokjes past. Sluit je de desktop met code die klopt, dan bewaart hij
      vanzelf. Screenshots: `-Ponly=logic`. Commit: `bcf9cf3`.
- [x] **7. De Logic-app: blokjes**
      De knop **Blocks** (en terug: **Code**) toont hetzelfde diagram als blokjes met draadjes
      (`client/os/BlockCanvas`): triggers cyaan, IF roze, acties lichtblauw; een IF heeft drie
      uitgangen (groen = yes, rood = no, onderaan = after). Blokjes slepen; het vlak verschuiven door
      op een lege plek te slepen (of met rechts, of het scrollwiel; Shift = opzij); zoomen met − en +
      of Ctrl+scrollwiel (50-100%, standaard 75%). Een draadje trek je van een uitgang naar een
      blokje; loslaten op een lege plek haalt het weg; een rondje of een draadje naar een when mag
      niet. Rechts het **palet** (klik = een nieuw blokje midden in beeld), of, als je een blokje kiest,
      zijn **instellingen**: welk apparaat/welke lezer, hoeveel seconden, de voorwaarde, met jouw
      apparaten (of ideeën voor een if) als suggestie en meteen "OK" of wat er mis is; Done, Delete
      (of de Delete-toets). Een blokje met een fout heeft een rode rand, een los blokje is grijs. In de
      statusregel staat het blokje onder de muis zoals in de code. Wisselen houdt alles: blokjes
      die je niet veranderde blijven waar ze stonden (ook losse), en code met een fout kan pas naar
      de blokjes als de fout weg is. Screenshots: `-Ponly=logic` (`l05`-`l07`). Commit: `d799e04`.
- [x] **8. Showcase en screenshots**
      Nieuwe functie **72, "Server racks and Logic"** (staat als NIEUW): een klein lab op (-50, -50)
      met een eigen kern, drie racks, een muur met een ijzeren deur, een lezer, een paneel en een
      lamp. Een kaart van niveau 3 opent de deur 3 seconden (groen), een geblokkeerde kaart geeft
      rood en I.M.T zegt "Someone tried the lab door", de code 4321 zet de lamp aan; daarna de
      app Logic met de code en als blokjes. Screenshots: `-Ponly=racks`, `keycards`, `logic` en `lab`
      (het lab stap voor stap). LEESMIJ.md heeft een nieuw stuk "De server: je redstone
      programmeren". Commit: `8b35100`.
- [x] **9. Eigen blokken: def** (jouw wens: "functions, zodat je meerdere triggers hetzelfde kunt
      laten doen")
      In de code `def naam():` aan de linkerkant met de stappen eronder, en `naam()` om hem te
      gebruiken, in elke when of in een andere def (ook als de def verderop in de code staat). In de
      blokjes een paars **DEF**-blok (een begin, net als een when) en **use def** (in het palet
      "call"); een DEF een andere naam geven neemt de use-def-blokjes mee. De server springt bij een
      use def naar de stappen van de def en gaat daarna verder met wat erna komt. Controles
      (`ScriptChecker`, dezelfde voor code en blokjes): twee defs met dezelfde naam, een def die niet
      bestaat ("Did you mean open_lab?"), een def die (via andere) zichzelf gebruikt ("a() uses
      itself (a > b > a): that would never stop"), en een def die niet past bij de when die hem
      gebruikt ("greet() uses accept(), which only works inside when keycard(...) or when
      codepanel(...)"); verder namen zoals in Python (letters, cijfers, _), geen woorden van de taal,
      niets tussen de haakjes. Een def hoort bij één diagram (zoals een functie bij één bestand). Help
      heeft een stuk "YOUR OWN BLOCKS". Het lab in de showcase en de screenshots (`-Ponly=logic`,
      `l08`) gebruiken een def. Drie nieuwe tests. Commit: `fe01c29`.
- [x] **10. Ctrl+Spatie in de code** (jouw wens: "zorg dat Ctrl + spatie alle opties toont die je
      kunt typen")
      Een lijstje bij de cursor met wat daar past (`client/os/CodeCompletion`): aan de linkerkant de
      whens (met je eerste lezer, paneel of apparaat al ingevuld) en `def`; na `when ` de triggers; in
      een blok de acties, if/elif/else/pass en je eigen defs; na `if`, `and`, `or`, `not` of een
      vergelijking alleen de gegevens die bij de trigger van dat blok passen (`card.level` alleen
      onder `when keycard`); tussen aanhalingstekens de namen van je lezers, panelen of apparaten,
      afhankelijk van wat ervoor staat. Wat je al typte maakt de lijst korter (wat ermee begint
      bovenaan); onderaan staat wat de gekozen regel doet. Pijltjes, Enter/Tab, Esc, scrollen en
      klikken werken. Kies je iets met aanhalingstekens (`pulse("...", 3)`), dan staat de cursor
      ertussen en gaat meteen de lijst met namen open; `def name():` selecteert de naam zodat je hem
      meteen overtypt. Na `card.`, `player.` of `on("` gaat het lijstje vanzelf open. Screenshots:
      `-Ponly=logic` (`l09`-`l11`). Eén nieuwe test. Commit: `b5519ce`.
- [x] **11. Geblokkeerde keycards echt weggooien** (jouw wens: "zodat je keycard tab niet overvol en
      rommelig wordt")
      In de app Keycards staan de geblokkeerde kaarten nu onderaan, met per kaart **Delete**, en
      rechtsboven **Delete blocked (n)** voor allemaal tegelijk; allebei vragen eerst "Sure?" (nog een
      keer klikken binnen 4 seconden). Alleen geblokkeerde kaarten kun je weggooien (een actieve eerst
      blokkeren). Het nummer van een weggegooide kaart komt nooit meer terug, ook niet na opslaan en
      laden, dus een oude kaart die nog ergens rondzwerft, wordt nooit meer geldig. Bovenaan staat
      hoeveel kaarten actief en geblokkeerd zijn. Daarbij twee kleine verbeteringen die een test liet
      zien: bij twee even grote groepjes racks is de server nu het groepje het dichtst bij de kern, en
      liggen er ooit twee gebieden over een plek, dan hoort die bij de dichtstbijzijnde kern (niet bij
      een willekeurige). Screenshots: `-Ponly=keycards` (`y06`-`y08`). Eén nieuwe test.
- [x] **12. Lezers en panelen een andere naam geven in Devices** (jouw wens: "zorg dat je bij de
      desktop in devices alle keycard en code panel readers kunt renamen")
      De app Devices heeft nu de kopjes **KEYCARD READERS** en **CODE PANELS**: per lezer of paneel
      zijn naam, waar hij hangt, of de server hem hoort (**ready** of **no server**) en in hoeveel
      diagrammen hij staat. De knop **Rename** maakt van de naam een typvak: Enter (of **Save**)
      bewaart, Esc of ergens anders klikken laat het zoals het was; een lege naam mag niet. Een
      diagram verandert niet mee: staat er na een nieuwe naam "in no diagram", dan gebruikt je
      diagram nog de oude naam. De lezers en panelen staan in een vaste volgorde (van boven naar
      beneden), zodat er niets verspringt als je een naam verandert. Screenshots: `-Ponly=devrename`
      (`dr1`-`dr3`). Eén nieuwe test.

### 9. Het datacenter (8 oktober)

Jouw wensen: het datacenter (je serverracks) moet meer gebruikt worden. Je moet kunnen **aflezen
hoeveel CPU, RAM en SSD** er gebruikt wordt. Er komt een **fan**: heb je er te weinig, dan
**brandt je datacenter af of gaat je hardware kapot**. Daarom kun je **backups** maken naar een
ander datacenter, anders ben je bij een brand je hele config kwijt. En het **brein van I.M.T**
draait op de server: een **GPU** in plaats van een CPU is dubbel zo snel voor het brein, maar half
zo snel voor je redstone (Logic). Zo moet je goed nadenken over wat je in je racks steekt, of een
heel groot datacenter bouwen. Het brein zelf werkt zoals nu; het datacenter zet er alleen een
vertraging bij zolang het nog niet sterk is.

Jouw antwoorden:

- **Fans** gaan in de gewone vakjes, maar een rack krijgt er **6 in plaats van 4**: twee kolommen
  van drie.
- **Te warm** gaat stap voor stap: vanaf **70°** trager en I.M.T waarschuwt; vanaf **90°**
  slijten onderdelen en gaan ze één voor één kapot; vanaf **110°** vat een rack vlam en brandt
  het af met alles erin.
- **Backups**: automatisch (om de zoveel minuten) én met de hand, naar een ander datacenter van
  jezelf.
- **Zonder datacenter werkt er géén enkel brein**, ook het woordenboek niet: dan is het gewoon een
  mooie bril. Met een datacenter kies je zelf: woordenboek, lokaal of Claude. Hoe lang ze erover
  doet = de tijd van het model + de vertraging van je datacenter. **In creative** heb je geen
  datacenter nodig (makkelijker om te proberen en om de mod te bekijken).

Mijn invulling (zeg het als je iets anders wil):

- **Rekenkracht**: een CPU telt 1 voor Logic en 1 voor het brein; een GPU ½ voor Logic en 2 voor
  het brein. Reageren duurt 0,5 s gedeeld door je Logic-kracht (1 CPU 0,5 s, 2 CPU's 0,25 s, 5
  CPU's 0,1 s; alleen één GPU: 1 s). Het brein krijgt 8 s gedeeld door zijn kracht erbij (1 CPU
  +8 s, 1 GPU +4 s, 4 GPU's +1 s); bij een halve seconde of minder valt het weg (vanaf 16
  kracht, dus 8 GPU's).
  Een server heeft nu een CPU **of** een GPU nodig (en nog steeds RAM en een SSD).
- ~~Eén temperatuur voor het hele datacenter~~ (zo was het tot stap 8): elk onderdeel maakt warmte:
  CPU 2 W (4 W als hij hard werkt), GPU 3 W (7 W als hij hard werkt), RAM en SSD ½ W. Een fan
  koelt 6 W weg. Elke watt die niet weggekoeld wordt = 8° warmer dan 25°. De temperatuur gaat er
  langzaam naartoe (ongeveer een minuut), dus je hebt tijd om fans bij te steken. Sinds stap 8 heeft
  **elk rack zijn eigen temperatuur** en heeft elk rack met iets erin een fan nodig.
- **Wat er verbrandt**: je diagrammen en je keycards staan verspreid over al je SSD's (zoals RAID
  0). Gaat er één SSD kapot door hitte of brand, dan is alles weg (alle keycards werken niet
  meer). Een SSD er gewoon uithalen mag wel (dan draaien alleen de diagrammen die niet meer passen
  niet). Je bestanden, notities, wachtwoord en vrienden staan in de kern zelf en blijven.
  Fans slijten niet.
- **Het brein draait op het sterkste datacenter van jezelf**, waar het ook staat (ook in een
  andere dimensie), als de server draait.
- **Aflezen**: een nieuwe app **Monitor** op de desktop (zoals Taakbeheer in Windows) en kort op
  het scherm van een rack.

De stappen, één per commit:

- [x] **1. Zes vakjes, de GPU en de fan**
      Een rack heeft nu 6 vakjes: twee kolommen van drie, op het scherm en op de voorkant van het
      blok (je oude racks houden wat erin zat). Twee nieuwe onderdelen: de **Server GPU** (recept:
      goud, redstone, 2 computerchips en een diamant) en de **Server Fan** (ijzer, ijzeren tralies en
      redstone = 2 fans; koelen doet hij vanaf stap 2). De tooltip zegt hoeveel een CPU of GPU telt
      voor Logic en voor het brein. Een server draait nu met een CPU **of** een GPU. Het rackscherm
      is wat hoger en toont ook GPU's, fans en "I.M.T +2.0 s" (hoe lang het brein op dit
      datacenter extra zou nadenken; dat gebruiken we pas in stap 6). Let op: reageren gaat nu
      anders dan eerst (eerst 0,15 s sneller per extra CPU, nu 0,5 s gedeeld door de kracht), dus
      4 CPU's reageren in 0,15 s in plaats van meteen. In de config (`[logic]`): `gpuLogicPower`,
      `gpuBrainPower`, `brainDelayTicks`, `brainDelayGoneTicks`; `cpuSpeedupTicks` is weg.
      Screenshots: `-Ponly=racks`. Eén nieuwe test (in een eigen bestand,
      `gametest/DatacenterGameTests.java`). Commit: `0e4bf5a`.
- [x] **2. Warmte**
      Het datacenter heeft nu één temperatuur (`logic/Datacenter`, de kern onthoudt hem ook na
      opslaan). Om de twee seconden telt hij de warmte van de onderdelen in racks die meedraaien,
      trekt de koeling eraf (fans 6 W, elk rack zelf 2 W) en schuift de temperatuur 8% op naar waar
      hij heen gaat (25° + 8° per watt te veel). Vanaf **70°** werkt alles op halve snelheid: je
      diagrammen reageren twee keer zo traag (en straks denkt het brein twee keer zo lang).
      I.M.T waarschuwt de eigenaar één keer (bij 70°, bij 90°, en als het weer koel is,
      onder 65°), en het staat in het logboek (een nieuwe oranje soort regel, HEAT). Het rackscherm
      toont **TEMP** (nu, en waar hij naartoe gaat), **HEAT/COOL**, en zegt hoeveel fans er nog bij
      moeten zodat het ook koel blijft als alles zo hard mogelijk werkt ("Add 3 fans"). De tooltip
      van elk onderdeel zegt hoeveel warmte het maakt (een fan: hoeveel hij wegkoelt). Alle
      getallen staan in de config onder `[logic]`. Screenshots: `-Ponly=racks` (`r05_rack_hot`). Eén
      nieuwe test. Commit: `1be6c89`.
- [x] **3. Slijten en brand**
      Vanaf **90°** slijten de onderdelen (niet de fans): om de twee seconden krijgt de helft ervan
      wat slijtage (1, vanaf 100° 2, vanaf 110° 3...). Je ziet een balkje onder het icoon, zoals bij
      gereedschap, en de tooltip zegt "Worn out: 45%". Bij 20 is een onderdeel kapot: rook, een knak,
      weg, en I.M.T zegt welk. Blijft het **10 seconden boven 110°**, dan vat het heetste rack
      vlam (het rack dat de meeste warmte maakt): vuur erop en ernaast, vlammen en rook, en het scherm
      van het rack knippert "ON FIRE! Take your parts out: gone in 18 s." Na **10 seconden** is het
      weg, met alles wat er nog in zit (er valt niets uit; op zijn plek brandt nog wat vuur). Is het
      daarna nog steeds te heet, dan is het volgende rack aan de beurt. Gaat er een **SSD** kapot of
      verbrandt hij, dan zijn alle diagrammen en keycards van de server weg (alarm in het logboek en
      van I.M.T); kaartnummers komen ook dan nooit terug. Een rack afbreken of een SSD eruit
      halen kan altijd (dan ben je niets kwijt). Een warm datacenter vat geen vlam als je op tijd
      fans bijsteekt. In de config: `maxWear`, `fireTemperature`, `fireDelayTicks`, `burnTicks`.
      Screenshots: `-Ponly=racks` (`r06_rack_fire`, `r07_rack_fire_screen`). Twee nieuwe tests.
      Commit: `5d6104a`.
- [x] **4. Gebruik meten en de app Monitor**
      De kern meet elke seconde hoe hard alles werkt en onthoudt de laatste minuut: **CPU** (hoeveel
      blokjes en triggers je diagrammen afliepen, tegen wat je CPU's en GPU's aankunnen: 100 per
      seconde per Logic-kracht, `logicStepsPerSecond`), **brein** (komt in stap 6), **RAM**
      (diagrammen die bezig zijn of wachten, tegen wat er tegelijk kan) en **SSD** (blokjes in je
      diagrammen tegen wat erop past). Werken de CPU's en GPU's hard, dan maken ze meer warmte (tot
      de "busy"-warmte uit stap 2). Nieuwe app **Monitor** (icoon M) op de desktop, zoals Taakbeheer:
      vijf tegels (CPU, BRAIN, RAM, SSD, TEMP) met een groot getal, een grafiekje van de laatste
      minuut en een regel eronder (kracht, vertraging van het brein, hoeveel bezig, blokjes, waar
      de temperatuur heen gaat); links elk rack (een groen, oranje of rood bolletje, waar het staat,
      en zijn onderdelen als gekleurde blokjes met een rood streepje als ze slijten, of "ON FIRE
      15 s"); rechts de warmte en koeling met wat je moet doen ("Add 3 fans for full load", "Too hot:
      half speed"), en welke diagrammen er nu bezig zijn ("Busy x3", "2 waiting for RAM"). Het werkt
      ook via SSH. Screenshots: `-Ponly=racks` (`r08_os_monitor`). Eén nieuwe test. Commit: `436ca99`.
- [x] **5. Backups**
      Nieuwe app **Backup** (icoon B) op de desktop. Bovenaan kies je met **<** en **>** naar welk
      ander datacenter van jezelf de backups gaan (met zijn IP en hoeveel plek er nog vrij is), dan
      **Backup now**, en **Automatic: Off / 5 min / 15 min / 60 min** (speltijd; de eerste meteen).
      Een backup is een kopie van je diagrammen en keycards; hij neemt op het andere datacenter
      zoveel blokjes in als je diagrammen, plus één per 10 kaarten, en daar blijven van elk netwerk
      de laatste 3 staan. Past hij niet, draait het andere datacenter niet, of draait je eigen server
      niet, dan zegt hij waarom (automatisch: één keer, ook door I.M.T, en over een minuut
      opnieuw proberen). Onderaan **YOUR BACKUPS**: alle backups van de eigenaar, met **Restore**
      en **Delete** (allebei eerst "Sure?"). Restore vervangt je diagrammen; de keycards alleen als
      de backup van dit netwerk is (een kaart hoort bij het IP dat hem schreef), en kaartnummers
      gaan nooit terug. Het andere datacenter mag ver weg staan (ook in een andere dimensie, ook als
      zijn chunk niet geladen is): elke kern meldt om de twee seconden hoe hij ervoor staat
      (`logic/Datacenters`, bewaard in de wereld). Brandt er een SSD op het andere datacenter, dan
      zijn de backups daar ook weg; breek je een kern af, dan ook. Daarbij een verbetering die de test
      liet zien: een rack telt nu alleen mee voor de kern die het dichtst bij staat (ook als twee
      gebieden over elkaar liggen). Screenshots: `-Ponly=racks` (`r09_os_backup`). Eén nieuwe test.
      Commit: `f44a964`.
- [x] **6. Het brein op het datacenter**
      Het brein draait op het sterkste datacenter van jezelf dat draait (de kortste vertraging),
      waar het ook staat (`logic/DatacenterBrain`). De server stuurt twee keer per seconde mee met
      de status van je bril hoeveel vertraging dat is, en waar. **Zonder datacenter** (en niet in
      creative) werkt er geen enkel brein: druk je op de spraaktoets, dan zegt I.M.T "I can't
      think without a datacenter..." en luistert ze niet; ze zegt ook uit zichzelf niets meer. Met
      een datacenter: de tijd van het model + de vertraging van je datacenter (1 CPU +8 s, 1 GPU
      +4 s, warm twee keer zo lang; vanaf 8 GPU's niets), ook bij het woordenboek; in de HUD staat
      zolang "THINKING...". **In creative** heb je geen datacenter nodig en is er geen vertraging.
      Terwijl ze nadenkt, zegt je spel dat tegen de server: dan werken de CPU's en GPU's van dat
      datacenter op vol (BRAIN 100% in de Monitor, met "I.M.T thinking (naam)", en het wordt
      warmer). Het scherm **AI Brain** zegt rechtsboven "Datacenter: dev-lab (+2.0 s)", "Creative: no
      datacenter needed" of "No datacenter: no brain". Let op: in de screenshot-rondleiding zegt ze
      bij `b08b_brain_speaks_up` (survival, geen datacenter) nu terecht niets meer. Screenshots:
      `-Ponly=racks` (`r10_brain_datacenter`, `r11_brain_thinking`, `r12_brain_answered`). Eén nieuwe
      test. Commit: `bb19ce6`.
- [x] **7. Showcase, screenshots en LEESMIJ**
      Nieuwe functie **73, "The datacenter"** (staat als NIEUW; de functies erna schuiven een
      nummer op, nu 81 in totaal), in survival: het lab van functie 72, met in het rack rechts RAM,
      een SSD en vier GPU's en geen fan. Je ziet het scherm van dat rack ("Add 5 fans"), dan te heet
      (96°, halve snelheid, slijten), dan vlam en afbranden; omdat er een SSD in zat zijn de
      diagrammen en keycards weg. Daarna de Monitor, de app Backup met een backup op een tweede
      datacenter ver weg (dev-vault, op -50 -60 -130), en Restore zet alles terug. Tot slot AI Brain
      ("Datacenter: dev-sec (+4.0 s)") en "night vision on" met eerst THINKING... Ik heb hem
      afgespeeld met `--test` en elke stap gefotografeerd. De Monitor zegt nu "off" bij de SSD als er
      geen SSD draait (in plaats van 999%). LEESMIJ.md heeft een nieuw stuk "Het datacenter: warmte,
      backups en het brein", en de stukken over de server en de stem zeggen dat je GPU's, fans en een
      datacenter nodig hebt.
- [x] **8. Elk rack zijn eigen temperatuur** (jouw wens, 8 oktober 's avonds)
      "Elke server rack maakt zijn eigen temperatuur en heeft dus minstens 1 fan nodig; de CPU, GPU,
      RAM en SSD zijn wel gemeenschappelijk." Zo werkt het nu: de onderdelen werken nog steeds samen
      als één server, maar elk onderdeel maakt warmte in **zijn eigen rack**, en een fan koelt alleen
      **zijn eigen rack** (6 W). De 2 W "lucht" per rack is weg: **zonder fan raakt de warmte er niet
      uit** en wordt het rack steeds warmer, ¼ graad per watt om de twee seconden (een rack met één
      RAM-latje: na ongeveer 6 minuten 70°; een rack met CPU, RAM en SSD: na 2 minuten). Met fans
      werkt het zoals eerst (25° + 8° per watt te veel), maar per rack. Elk rack onthoudt zijn eigen
      temperatuur (ook na opslaan) en vat zelf vlam als **het** te heet is; slijten gebeurt alleen in
      dat rack. **Mijn keuze**: is één rack warm (70°), dan werkt de **hele** server op halve
      snelheid (alles werkt samen, dus het warme rack remt de rest af); zeg het als je liever wil dat
      alleen de CPU's en GPU's in dat rack trager worden. I.M.T en het logboek zeggen nu ook
      **waar** het warme rack staat. Het rackscherm toont **RACK TEMP** van dat rack ("rising" zonder
      fan), HEAT/COOL van dat rack, en "No fan: it keeps getting warmer. Add 1 fan!" of "Another rack
      is too hot: half speed." De Monitor toont bij elk rack zijn temperatuur en "+1 fan", rechts
      "Fans: #2 +1, #3 +3", en TEMP is het heetste rack. In de config (`[logic]`): `noFanHeating`
      nieuw, `rackCooling` weg. In de showcase heeft elk rack van het lab nu een fan (behalve het
      GPU-rack van functie 73, dat expres geen fan heeft). De warmtetest is vervangen door een test
      met jouw voorbeeld: een rack met alleen RAM en zonder fan wordt warm, het rack ernaast met een
      fan blijft koel; een fan erin en het koelt weer af. Screenshots: `-Ponly=racks` (`r05_rack_hot`,
      nieuw `r05b_rack_other_hot`, `r08_os_monitor`).

### 10. Je basis beveiligen: zones en C4 (8 oktober)

> **Sinds 26 oktober bestaat C4 niet meer** (jouw wens: "haal de C4 weg, ik wil dat er nog enkel granaten
> bestaan"). De remote C4 werd de **Remote Frag** met de **Frag Trigger** (hoofdstuk 35, stap 2f); de klap met
> breekpunten en barsten bleef (nu `breach/Blast`, config `[breach]`). Hieronder staat hoe het toen gebouwd werd.

Jouw wensen: in Minecraft kan iedereen zomaar elk blok weghakken; je basis moet veiliger. Je stelt
een **zone** in, tot op het blok precies (hoeken, rechte lijnen, x, y en z), je kunt zien waar hij
wel en niet is, en je kunt die rand aan- en uitzetten. In de zone kunnen alleen spelers **met
toestemming** plaatsen en breken. Om het in balans te houden (inbreken moet moeilijker worden, niet
onmogelijk) komt er **C4** dat alles kan opblazen, in of buiten een zone: één met een **timer** die
je instelt, en één met een **afstandsbediening**. En om het realistisch te maken mag iedereen
**ladders, scaffolding** en zo wel plaatsen in een zone, want dat kan in het echt ook.

Jouw antwoorden:

- De zone hangt aan je **beveiligingskern** en heeft **stroom** nodig: een nieuwe app **Zone** op de
  desktop. Bouwen mogen jij, je team en je vertrouwde spelers (Access). De zone ligt binnen het
  gebied van je kern (61 × 61, 60 onder en 55 boven de kern; sinds 9 oktober tot 200 blokken van de
  kern, zie stap 13) en werkt alleen als de kern stroom heeft. Wie je kern hackt, krijgt ook je zone.
- De vorm: **meerdere kubussen**. Per kubus twee hoeken: aanklikken met een **Zone Marker** in de
  wereld, of in de app per blok bijstellen (x, y, z). Zo maak je een L-vorm, of een toren die hoger
  is dan de rest.
- **Sterke muren kosten meer C4**: hout, glas en aarde vliegen er meteen uit, steen en baksteen
  breken met 1 C4, obsidiaan en andere sterke blokken krijgen barsten en breken pas na 3 C4.
- **Ontmantelen kan**: iedereen knipt met een **schaar** de draad door. Plakt iemand C4 op of in je
  zone, dan zegt **I.M.T** waar (aan jou en je vertrouwde spelers). De timer piept steeds
  sneller.
- **Alleen plaatsen en breken** is beveiligd: "zo realistisch mogelijk: in het echt kun je moeilijk
  inbreken, maar als je binnen bent kun je alles opendoen en stelen." Kisten, deuren, knoppen, item
  frames en dieren blijven dus voor iedereen bruikbaar. (Tip: een houten deur is geen slot; een
  ijzeren deur met een keycard-lezer of codepaneel aan de buitenkant wel.)
- **Iedereen mag plaatsen en weer weghalen**: ladders, scaffolding, klimplanten (vines, twisting en
  weeping vines) en fakkels en lantaarns. C4 kan altijd.
- **De rand zien alleen de eigenaar en zijn vrienden** (team en vertrouwde spelers). Anderen zien
  niets; ze merken het pas als een blok niet breekt.
- **C4 werkt altijd**, ook als de eigenaar offline is. Logt hij weer in, dan vertelt I.M.T wat
  er gebeurd is.

Mijn invulling (zeg het als je iets anders wil):

- **Vrienden** zijn dezelfde als bij de torentjes: de eigenaar, zijn team en zijn vertrouwde
  spelers. Wie in **creative** speelt, mag altijd (anders kan ik de showcase niet bouwen). Alleen de
  eigenaar verandert de zone (op de desktop, of via SSH met het wachtwoord).
- **Hoe groot**: hooguit 16 kubussen; wat buiten het gebied van je kern steekt, valt eraf. Hoe groter
  de zone, hoe meer stroom: bovenop de 20 van de kern 1 per 2000 blokken (om de twee seconden). Een
  basis van 30 × 20 × 30 kost zo 9 extra, het hele gebied 216 (ongeveer één zonnepaneel in de volle
  zon). Valt de stroom uit, dan staat de zone uit en waarschuwt I.M.T je één keer. Zet je
  generatoren en kabels dus **in** je zone!
- **De kern zelf** hoort altijd bij zijn zone (anders hak je de kern weg en is de zone weg).
- **Wat ook als plaatsen of breken telt** voor wie geen toestemming heeft: een emmer leeggieten
  (water, lava, poedersneeuw), iets aansteken, met een bijl, schep of schoffel een blok veranderen,
  beendermeel, en stroomkabels leggen of doorknippen (anders knip je de stroom van de zone eruit).
- **Alleen C4 komt erdoor**: andere explosies (TNT, creepers, vuurballen, de satelliet) breken geen
  blokken in een zone (ze doen wel pijn), behalve als iemand met toestemming ze aanstak; vuur brandt
  geen blokken van een zone weg en lava steekt ze niet aan; een zuiger buiten de zone duwt of trekt
  geen blokken in of uit de zone; endermen, withers, ravagers en zombies (deuren) breken geen
  blokken in een zone. Anders kon je met een aansteker of TNT om de zone heen.
- **De Biebia's Industries Tower en de Biebia's Industries-bunker** hebben geen zone (je moet er nog steeds in kunnen hakken);
  na het overnemen van de toren kun je er zelf een tekenen.
- **De rand**: doorzichtige blauwe wanden met een fijn raster aan de buitenkant van de zone, van
  binnen en van buiten te zien tot ongeveer 48 blokken ver. Aan/uit voor jezelf: **G > Information >
  Zone border**, of een eigen toets (standaard geen, zoals de kabel-xray). Met de Zone Marker in je
  hand zie je je zone altijd, met nummers op de kubussen. Wie geen toestemming heeft en iets
  probeert, ziet onderaan "Protected zone" en hoort een kort piepje.
- **Zone Marker**: linksklik op een blok = hoek 1, rechtsklik = hoek 2 (je ziet een oranje doos en
  "12 × 6 × 20"); sluipen + rechtsklik = deze kubus aan je zone toevoegen; sluipen + linksklik in een
  kubus = die kubus weghalen (twee keer klikken). Recept: een redstonefakkel, een computerchip en een
  stok onder elkaar.
- **App Zone** (icoon Z): links een kaart van bovenaf van het gebied van je kern met je kubussen;
  rechts de gekozen kubus met zijn hoeken (x, y, z, ook te typen) en per kant een − en + (west/oost,
  onder/boven, noord/zuid) om die kant één blok te verschuiven; **New box** en **Delete**. Bovenaan:
  ACTIVE of NO POWER, hoeveel blokken en hoeveel stroom.
- **C4 (Timer)**: rechtsklik in de lucht = een klein scherm om de tijd te kiezen (5 s tot 2 min,
  standaard 10 s; het item onthoudt het). Je plakt hem op een vloer, muur of plafond en hij loopt
  meteen: een rood schermpje telt af ("0:07"), een lampje knippert en hij piept steeds sneller.
  Recept: TNT, twee klei, een slijmbal, redstone en een klok.
- **C4 (Remote)** en de **Detonator**: plak je een remote C4 terwijl je een detonator bij je hebt,
  dan hoort hij bij die detonator (of rechtsklik met de detonator op een C4). Rechtsklik met de
  detonator = al zijn C4 binnen 128 blokken gaan af, kort na elkaar; sluipen + rechtsklik = hoeveel
  er gekoppeld en in bereik zijn. Met de detonator in je hand zie je je C4 als rode markering.
  Recepten: remote C4 = TNT, twee klei, een slijmbal, redstone en een bliksemafleider (de antenne);
  detonator = een bliksemafleider, een computerchip, ijzer en een knop.
- **Hoe hard**: één C4 doet 10 "breekpunten" vlak bij de lading, aflopend tot 0 op 3,5 blokken. Een
  zwak blok (hout, glas, aarde, wol, zand...) kan er 2 hebben, een gewoon blok (steen, baksteen,
  deepslate, ijzer...) 6, een sterk blok (obsidiaan, netheriet, de laserzender...) 20, en de
  **beveiligingskern 48** (ongeveer 6 C4: hacken is de andere weg). Bedrock en zo nooit. Zo maakt
  één C4 een gat in een stenen muur van één dik waar je door kunt, en heb je voor obsidiaan er drie
  nodig. Wat niet breekt, krijgt **barsten** die je ziet; de volgende C4 telt erbij op. Barsten
  genezen na 10 minuten (en als de server opnieuw start). Opgeblazen blokken vallen eruit zoals bij
  TNT (kisten ook, met hun inhoud). De klap doet pijn zoals TNT. Ander C4 in de klap gaat ook af, en
  TNT wordt aangestoken.
- **Ontmantelen** kan alleen met een schaar (met je hand gaat hij er niet af, ook niet voor wie hem
  plakte); de C4 valt eruit als item, dus de verdediger krijgt hem. Wie hem plakte, hoort het van
  I.M.T
- **Het alarm** gaat af als er C4 in een zone geplakt wordt of binnen 3 blokken ervan: I.M.T
  zegt waar (zonder naam: in het echt weet je ook niet wie het was), en je ziet een rode markering
  tot hij weg is. Ontploft hij, dan staat in het logboek hoeveel blokken er weg zijn. Was je
  offline, dan hoor je bij het inloggen: "While you were away: 3 C4 blasts at your zone (12
  blocks)".

De stappen, één per commit:

- [x] **1. De zone op de server**
      Een zone is een lijst kubussen bij een kern. Ze staan in een overzicht van de hele wereld
      (`security/zone/ZoneData`, bewaard in de wereld), samen met van wie de kern is, wie hij vertrouwt
      en of er stroom is; zo werkt de zone ook als de chunk van de kern niet geladen is. De kern
      vertelt dat meteen als er iets verandert (eigenaar, Access, overname) en om de twee seconden.
      Wie geen toestemming heeft, kan in een actieve zone niets breken (hakken begint niet eens) en
      niets plaatsen, ook geen deur of bed die half in de zone zou komen; ook vuur, een bijl of
      schoffel op een blok, beendermeel, een emmer leeggieten of vullen, en stroomkabels leggen,
      oprapen of knippen niet. Onderaan staat dan "Protected zone: you can't build or break here. Only
      C4 gets through." met een kort piepje. Ladders, scaffolding, klimplanten, fakkels en lantaarns
      mag iedereen plaatsen en weghalen (de tag `imttech:zone_free`, ook zelf aan te vullen). De kern
      zelf hoort altijd bij zijn zone. Stroom: de zone kost bovenop de kern 1 per 2000 blokken (om de
      twee seconden, `zoneBlocksPerPower` in de config onder `[security]`); is er te weinig, dan staat alleen de zone uit,
      met een regel in het logboek (nieuwe blauwe soort, ZONE) en een waarschuwing van I.M.T aan
      de eigenaar, en weer een bericht als hij terug is. Kubussen die buiten het gebied van de kern
      steken worden afgeknipt; hooguit 16; kubussen die over elkaar liggen tellen één keer. Zes nieuwe
      tests (in een eigen bestand, `gametest/ZoneGameTests.java`): wie mag bouwen (eigenaar,
      vertrouwd, team, na een overname de nieuwe eigenaar), vrije blokken, stroom, de kubussen
      passend maken en bewaren, emmers, en kabels.
- [x] **2. De grens zien**
      Twee keer per seconde stuurt de server elke speler de zones in de buurt (256 blokken) waar hij
      mag bouwen: zijn eigen zones en die van wie hem vertrouwt of in zijn team zit. Een vreemde krijgt
      niets (`security/zone/ZoneSync`, alleen als er iets veranderde). Je spel tekent de buitenkant:
      doorzichtige blauwe wanden met een raster op elke blokrand en een zachte glans die er langzaam
      overheen schuift (zoals de rand van de wereld), net buiten je muren, van binnen en van buiten te
      zien tot 48 blokken ver; dichtbij is hij het felst. Waar twee kubussen tegen of over elkaar
      liggen, komt geen wand (`security/zone/ZoneShape` rekent de echte buitenkant uit). Zonder stroom
      is hij rood en klopt hij langzaam. Aan/uit voor jezelf: **G > Information > Zone border**, of een
      eigen toets (Opties > Besturing, standaard geen). ~~Dat werkt ook zonder bril~~ (sinds stap 9 alleen
      met je bril). De plaatjes van het raster en de glans maakt `tools/generate_textures.py`
      (`zone_textures`). Screenshots: `-Ponly=zones` (`z01` tot `z05`: een L-vormige basis met een
      torentje, van buiten en binnen, zonder stroom, en met de rand uit). Twee nieuwe tests: de
      buitenkant van een L en van kubussen over elkaar, en wie de zone krijgt.
- [x] **3. De Zone Marker en de app Zone**
      Nieuw item **Zone Marker** (recept: een redstonefakkel, een computerchip en een stok onder
      elkaar). Linksklik op een blok = hoek 1, rechtsklik = hoek 2 (ook op een kist: de marker gaat
      voor); onderaan staat hoe groot de doos is ("12 × 6 × 20 (1440 blocks)"). **Sluipen + rechtsklik**
      voegt hem toe aan de zone van de kern in wiens gebied hij ligt (die moet van jou zijn; wat buiten
      het gebied steekt valt eraf, en dat zegt hij), daarna is de keuze weer leeg. **Sluipen +
      linksklik** in een kubus vraagt "remove it?", een tweede klik binnen drie seconden haalt hem weg.
      Met de marker breek of sla je niets: je spel vangt de linksklik af en stuurt alleen "klik op dit
      blok" naar de server; houd je de knop ingedrukt, dan telt dat als één klik. Met de marker in je
      hand zie je je zone altijd (ook met Zone border uit), de gekozen doos in oranje met de twee
      hoekblokken, en boven elke kubus een bordje "Box 1  10 × 8 × 8". Nieuwe app **Zone** (icoon Z):
      bovenaan PROTECTED / NO POWER / NO ZONE YET, hoeveel blokken en hoeveel stroom; links een kaart
      van bovenaf van het gebied van je kern (je kubussen, de kern wit, jij geel; klik op een kubus om
      hem te kiezen); rechts de gekozen kubus met **<** en **>**, de getallen van beide hoeken (typen +
      Enter, Esc = niet), de grootte, per kant een **−** en **+** (die kant een blok naar binnen of
      buiten, nooit kleiner dan één blok), **New box** (7 × 5 × 7 waar je staat, via SSH bij de kern)
      en **Delete box** (eerst "Sure?"). Toevoegen en weghalen staat in het logboek. Werkt ook via SSH.
      Screenshots: `-Ponly=zones` (`z03b_zone_marker`, `z03c_zone_app`). Twee nieuwe tests: de marker
      (hoeken, toevoegen, een vreemde kan het niet, weghalen in twee klikken) en de knoppen van de app.
- [x] **4. Alleen C4 komt erdoor**
      **Explosies** (TNT, creepers, vuurballen, de satelliet, end crystals...) breken geen blokken van
      een actieve zone meer; wezens krijgen de klap wel. Stak iemand die er mag bouwen hem zelf aan
      (je eigen TNT in je eigen basis), dan wel. **Vuur** brandt geen blokken van een zone weg: daar
      heeft NeoForge geen event voor, dus is dit de eerste **mixin** van de mod (`mixin/FireBlockMixin`,
      aangemeld in `neoforge.mods.toml` en `imttech.mixins.json`): vlak voordat vuur een buurblok
      wegbrandt, kijkt hij of dat in een zone ligt. **Lava** steekt in een zone niets aan. Een
      **zuiger** duwt of trekt geen blokken in, uit of door een zone, behalve een zuiger die zelf in
      diezelfde zone staat (je eigen machines). **Monsters** (endermen, withers, ravagers, zombies bij
      deuren, zilvervisjes) breken in en tot 3 blokken rond een zone niets; dieren en dorpelingen wel
      gewoon. En de **raiddrones** schieten geen blokken van een zone meer weg. Vier nieuwe tests
      (explosies, vuur en lava, zuigers, monsters), telkens met een controle buiten de zone.
- [x] **5. C4 met timer**
      Nieuw: **C4 (Timer)** (recept: TNT, twee klei, een slijmbal, redstone en een klok, zonder vaste
      vorm). Rechtsklik in de lucht opent **C4 TIMER**: de tijd groot in rood, −5 s / +5 s, snelknoppen
      0:05, 0:10, 0:30, 1:00, 2:00 (ook scrollen of de pijltjes), Done; het item onthoudt het (de
      tooltip zegt "Timer: 0:30"). Je plakt hem op een vloer, muur of plafond (ook in de zone van een
      ander) en hij loopt meteen: een klikje en twee piepjes, een rood schermpje dat aftelt en altijd
      naar je toe kijkt, een lampje dat knippert en piepjes die steeds sneller gaan (elke seconde, de
      laatste vijf twee keer zo vaak, de laatste twee snel). Hij blijft zitten, ook als het blok achter
      hem weg is; met je hand krijg je hem er niet af (alleen met een schaar, stap 7). **De klap**
      (`c4/C4Blast`): het midden ligt op het blok waar hij tegenaan plakt; elk blok binnen 3,5 krijgt
      breekpunten (10 vlakbij, aflopend). Zwak (explosieweerstand onder 4: hout, glas, aarde...) 2,
      gewoon (steen, baksteen...) 6, sterk (100 of meer: obsidiaan, netheriet, de laserzender...) 20, de
      beveiligingskern 48. Eén C4 tegen een stenen muur maakt een gat van drie hoog (het blok erachter en
      de vier ernaast); obsidiaan pas na de derde; de kern na de zesde (en dan is zijn zone weg). Wat
      niet breekt, krijgt **barsten** (zichtbaar, ze tellen op bij de volgende klap, en genezen na 10
      minuten). Op obsidiaan zie je de barsten bijna niet: Minecraft tekent ze donker, net als bij
      hakken. Wat breekt valt eruit zoals bij TNT (kisten met inhoud); TNT in de buurt gaat ook af; ander
      C4 gaat drie ticks later af (een kettingreactie). Wezens krijgen een klap zoals van TNT. Een zone
      houdt C4 niet tegen. Nieuwe geluiden (piepje, aanzetten, en alvast doorknippen en de detonator) uit
      `tools/generate_sounds.py`. Alle getallen in de config onder `[c4]`. Screenshots: `-Ponly=c4`
      (`x01` tot `x05`). Zes nieuwe tests (in een eigen bestand, `gametest/C4GameTests.java`): een
      stenen muur, obsidiaan, de kern, de timer in een zone, een kettingreactie, en de grenzen van de
      timer.
- [x] **6. C4 met afstandsbediening**
      Nieuw: **C4 (Remote)** (TNT, twee klei, een slijmbal, redstone en een bliksemafleider als antenne)
      en de **Detonator** (een bliksemafleider boven, ijzer, een computerchip, ijzer, en een knop eronder).
      Plak je een remote C4 terwijl je een detonator bij je hebt (in je hand of rugzak), dan hoort hij
      meteen bij die detonator ("Remote C4 linked to your detonator (3 charges)"); een losse remote C4
      koppel je met een rechtsklik met de detonator, maar een lading van een andere detonator neem je
      niet over. Een detonator onthoudt hooguit 32 ladingen (`c4/DetonatorData`, op het item zelf).
      **Rechtsklik** = een klik en een radiotoontje, en al zijn ladingen binnen 128 blokken gaan af, de
      dichtstbijzijnde eerst en dan om de drie ticks de volgende ("3 charges fired. 1 out of range");
      ladingen die er niet meer zijn, vergeet hij. **Sluipen + rechtsklik** zegt hoeveel er gekoppeld en
      in bereik zijn. Met de detonator in je hand zie je boven elke lading een rood bordje "C4  12 m"
      (ook door muren; grijs als hij te ver is). Een remote C4 heeft een antenne en knippert rustig
      "RF". Het bereik staat in de config (`detonatorRange` onder `[c4]`). Screenshot: `-Ponly=c4`
      (`x06_remote_c4_detonator`). Twee nieuwe tests: koppelen en afgaan, en bereik en overnemen.
- [x] **7. Ontmantelen en alarm**
      **Ontmantelen**: rechtsklik met een **schaar** op C4 (iedereen, ook wie hem plakte): een knip en
      een dalend toontje, "You cut the wire: the C4 is harmless now", en de C4 valt eruit als item (de
      verdediger krijgt hem). De schaar slijt een beetje. Met je hand gaat het niet: dan zegt hij "cut
      the wire with shears". Wie hem plakte, hoort van I.M.T "Your C4 at ... was defused".
      **Alarm** (`c4/C4Alarm`): plakt iemand die er niet mag bouwen C4 in een zone of tot 3 blokken
      ervan, dan zegt I.M.T tegen de eigenaar en zijn vrienden die online zijn "C4 planted at your
      zone: x y z (timer 0:40)" of "(remote)", zonder naam, en ze zien boven de lading een knipperend
      rood bordje "C4!  8 m" (door muren heen) tot hij ontploft of onschadelijk is. Je eigen C4 in je
      eigen zone geeft geen alarm. Ontploft er C4 op een zone, dan zegt ze hoeveel blokken er weg zijn;
      is de eigenaar offline, dan onthoudt de wereld het en hoort hij een paar seconden na het inloggen
      "While you were away: 3 C4 blasts at your zone (12 blocks)". Alles staat ook in het logboek van de
      kern (rode regels: geplakt, klap met het aantal blokken, onschadelijk gemaakt). Screenshot:
      `-Ponly=c4` (`x07_c4_alarm`). Twee nieuwe tests: alarm en schaar (en geen alarm voor je eigen
      C4), en het verslag voor een eigenaar die offline was.
- [x] **8. Showcase, screenshots en LEESMIJ**
      Twee nieuwe functies na "The datacenter" (staan als NIEUW; de functies erna schuiven op, nu 83
      in totaal), allebei in survival, bij een L-vormige basis met een torentje en een stuk obsidiaan
      (op -130 -60 60). **74, "Protected zones"**: de blauwe rand, de Zone Marker (oranje doos, nummers,
      een derde kubus erbij), de app Zone, zonder stroom rood; dan is de basis van "Lotte" en ben jij
      een vreemde: slaan geeft "Protected zone", een steen neerzetten lukt niet, een ladder wel. **75,
      "C4"**: eerst het alarm ("C4 planted at your zone" en het rode bordje) en ontmantelen met een
      schaar; dan ben jij de inbreker: de timer op 0:05, plakken, piepen en een gat in de stenen muur;
      daarna drie keer een remote C4 met de detonator tegen het obsidiaan, en na de derde een gat. Ik
      heb ze afgespeeld met `--test` (en de stappen apart gefotografeerd). Onderweg gevonden: een
      nep-speler die voor de tweede keer inlogt laat NeoForge struikelen; de showcase geeft de basis
      daarom gewoon op naam aan Lotte (`setOwner(id, naam)` op de kern). LEESMIJ.md heeft een nieuw
      stuk "Je basis beveiligen: zones en C4", en de toetsen, het G-menu en de instellingen noemen de
      zonerand.
- [x] **9. De rand alleen met de bril** (jouw wens, 9 oktober: "zorg dat je de border enkel met bril
      kunt zien")
      De blauwe rand en de bordjes "Box 1" boven je kubussen zie je nu alleen met je eigen
      I.M.T-bril op (met energie), net als de kabel-xray. Ook het aan/uit-knopje (G > Information >
      Zone border, of de eigen toets) werkt alleen met de bril. **Mijn keuze**: de oranje doos van de
      Zone Marker (wat je aanklikt) zie je ook zonder bril, anders kon je zonder bril geen zone tekenen;
      zeg het als die ook alleen met de bril moet. Screenshots: `-Ponly=zones` (`z05b_zone_with_glasses`,
      `z06_zone_no_glasses`).
- [x] **10. TNT en de satelliet komen er ook door, maar zwak** (jouw wens, 9 oktober: "zorg dat de
      satelliet en TNT er ook door kan, TNT doet gewoon heel weinig damage aan de muur")
      In een zone gaan TNT en de raketten van het satellietbombardement niet meer langs de gewone
      explosie, maar werken ze met **breekpunten**, net als C4, alleen zwakker: TNT 3 en een
      satellietraket 6 (C4 doet 10), aflopend tot 0 op 3 blokken. Zo breekt TNT naast een houten muur
      alleen een plank vlakbij, en heeft een stenen muur pas na drie TNT op dezelfde plek een gat (de
      barsten tellen op, ook met die van C4). Na twee satellietraketten is een stenen blok weg. Wat het
      doet, staat in het logboek ("TNT blast at ...", "Satellite strike blast at ...") en I.M.T zegt
      het als er iets kapot is. Creepers, vuurballen en andere explosies blijven niets breken in een
      zone, en je eigen TNT in je eigen zone werkt gewoon. De satelliet zegt zelf dat hij het is
      (`Zones.satelliteExplosion`). In de config onder `[c4]`: `tntBreach`, `satelliteBreach`. Eén
      nieuwe test.
- [x] **11. Wat iedereen in een zone wel mag** (jouw wens, 9 oktober: "dit moet allemaal kunnen binnen
      een zone: emmers leeggieten en vullen maar enkel op de grond niet tegen muren, zo kun je niet met
      water klimmen, vuur aansteken, een bijl of schoffel op een blok, en kabels leggen of knippen")
      Een vreemde mag in een zone nu: een **emmer leeggieten of vullen** als hij naar de bovenkant van
      een blok kijkt (op de grond), niet tegen de zijkant of onderkant (geen waterlift tegen je muur op);
      **vuur aansteken** (vuur, of een kampvuur of kaars die aangaat); een **bijl** (schillen, koper
      schrapen) of **schoffel** op een blok; en **stroomkabels leggen, oprapen en knippen**. We kijken
      naar wat er echt verandert, niet naar wat je vasthoudt: met een aansteker in je andere hand zet je
      dus nog steeds geen steen neer. **Mijn keuze**: een pad met een schep en beendermeel noemde je niet,
      die mogen nog steeds niet; zeg het als die er ook bij moeten. Kabels die gewoon op de vloer van je
      basis liggen kan een inbreker nu doorknippen: verstop ze in een muur of vloer. De tests van de
      emmers en de kabels zijn vervangen door "emmer alleen op de grond" en "vuur, bijl, schoffel en
      kabels mogen".
- [x] **12. De kern houdt alles als je hem weghakt** (jouw wens, 9 oktober: "kun je zorgen dat al de
      config bewaard wordt in de security block zodat als je die hakt niet alles weg is")
      Hak je een beveiligingskern weg (of blaast C4 hem op), dan valt hij eruit met **alles** erin: de
      eigenaar, het IP, de SSH-poort, de naam, het wachtwoord, de bestanden en notities, het logboek, de
      keycards en diagrammen, de instellingen van het datacenter (zoals waar de backups heen gaan), de
      vertrouwde spelers en de knoppen van Access. En ook wat niet in de kern zelf stond maar in de
      wereld: de kubussen van zijn **zone** en de **backups** die op dit datacenter stonden. Zet je hem
      weer neer, dan is alles terug (`SecurityCoreBlockEntity.pack` en `unpack`): het IP ook, als het nog
      vrij is (keycards horen bij het IP), en de zone voor zover die in het gebied van de nieuwe plek
      past (op dezelfde plek: dezelfde zone). Hij zegt "Security core vault is back online at 10.x.y.z,
      with everything it had...". In de tooltip zie je welke kern het is ("vault (10.x.y.z)", "Owner:
      ..."). **Waarom niet alles op het item zelf?** Wat op een item staat, gaat ook naar het spel van
      wie het vasthoudt, en het wachtwoord mag nooit naar iemands spel (zie `SystemPassword`). Het item
      krijgt dus alleen een etiket met een nummer (`security/PackedCore`, de data component
      `imttech:packed_core`); de server bewaart de rest (`security/PackedCores`, in
      `data/imttech_packed_cores.dat`). Eén kern, één keer neerzetten: daarna is het etiket eraf.
      **Mijn keuzes** (zeg het als je iets anders wil): (1) **de eigenaar blijft de eigenaar**, ook als een
      ander de kern wegneemt en neerzet. Met de kern gaan zijn wachtwoord en bestanden mee, dus een
      gestolen kern moet je nog steeds hacken; anders was weghakken en neerzetten een makkelijke manier
      om het hacken over te slaan. Wie hem neerzet krijgt "This security core still belongs to ...", en
      in het logboek staat wie hem waar neerzette (de eigenaar kan dat via SSH lezen). (2) In
      **creative** valt een kern met instellingen er toch uit (zoals een volle shulkerdoos), anders was
      alles met één klik weg. (3) Een kern **van niemand** (de Biebia's Industries Tower, nog niet gehackt) valt als
      een nieuwe kern, zoals altijd. (4) Niet mee: de drones (die komen vanzelf terug) en wie de
      beveiliging even opjaagt na een valstrik. Vier nieuwe tests (`gametest/CoreConfigGameTests.java`):
      weghakken en terugzetten (alles is er weer, en zolang hij als item rondgaat zijn zone, IP en
      backup uit de wereld), een dief, opblazen en een kern van niemand, en creative.
- [x] **13. Een zone tot 200 blokken van de kern** (jouw wens, 9 oktober: "maak dat je een zone tot 200
      block van de security core kunt doen")
      Een zone mag nu tot **200 blokken** van zijn kern liggen, in elke richting (opzij, omhoog en
      omlaag, maar niet buiten de wereld): `zoneReach` in de config onder `[security]`. Het **gebied**
      van de kern (61 × 61, waar torentjes en drones verdedigen) blijft even groot; alleen de zone mag
      verder. De Zone Marker vindt je kern nu ook als je 200 blokken verderop staat (ook als dat stuk
      wereld niet geladen is), de app Zone knipt af op 200 blokken ("Your zone can't reach further"),
      en de kaart in de app zoomt zelf uit tot al je kubussen erop passen (met een rode lijn op 200
      blokken). Je ziet de rand van een verre zone ook als je ver van de kern staat. **Mijn keuzes**
      (zeg het als je iets anders wil): (1) een zone mag **niet over de zone van een ander** liggen en
      **niet in het gebied van een andere kern** ("Too close to another security network or its zone"),
      anders trok je je zone over iemands basis heen; (2) omgekeerd mag een **nieuwe kern niet zo
      staan dat zijn gebied over een zone valt**, anders zette iemand naast je zone een kern en schoten
      zijn torentjes in jouw zone; (3) kleiner maken mag altijd. Stroom blijft 1 per 2000 blokken: een
      zone van 100 × 40 × 100 kost 200 extra om de twee seconden, dus een heel grote zone vraagt een
      flink stroomnet. Eén nieuwe test (150 blokken ver met de app, 120 met de marker, stoppen op 200,
      niet in het gebied of de zone van een ander, en een kern niet naast je zone).
- [x] **14. Alle apparaten even ver als de zone** (jouw wens, 9 oktober: "zorg dat al de security items
      zoals keycard readers enzo even grote range van de core hebben als dat de zone groot kan, en laat
      dit aanpassen aan de config")
      Het **gebied** van een kern is niet meer 61 × 61 (60 onder, 55 boven), maar even groot als zijn
      zone: `zoneReach` (200) in elke richting. Eén getal voor alles: de zone, en alle apparaten die bij
      de kern horen (beveiligingstorentjes, camera's, keycard-lezers, codepanelen, serverkasten,
      laserwanden, relais van de bril, het landingsplatform van de toren), en zijn drones jagen ook zo
      ver. Het getal wordt overal gelezen op het moment zelf, dus verander je het in de config (ook
      terwijl het spel draait), dan past alles zich meteen aan; de servertekst bij een losse
      serverkast noemt het ook. Een apparaat hoort bij de kern als het in zijn gebied ligt en geen
      andere kern dichterbij staat (`SecurityCores.ownedBy`). **Gevolg** (zeg het als je dit anders
      wil): twee kernen mogen nog steeds niet in elkaars gebied staan, dus met 200 moet een nieuwe kern
      meer dan 400 blokken van een andere kern staan (ook van een Biebia's Industries Tower); met een kleiner getal
      mogen ze weer dichter bij elkaar. **Mijn keuze**: maak je het getal kleiner, dan beschermt een
      zone meteen alleen nog tot daar, maar de getekende kubussen blijven bewaard (weer groter: alles
      terug). De zone-test is vervangen door "zone en apparaten volgen de config", en de tests die een
      kern neerzetten zoals een speler doen dat met het bereik even op 30 (`withReach`): in de
      testwereld staan overal oude kernen.

### 11. Barsten herstellen (9 oktober)

Jouw wens: een blok met barsten kun je **vervangen** door een nieuw blok, of **opvullen met pasta**
("een beetje plamuren"); dan is het blok wel altijd net iets minder sterk. Vervang je het blok, dan
houd je het **gebarsten blok** over. Dat steek je in een **crushing block** die er **poeder** van
maakt, en dat poeder kun je **compacten** tot een volledig nieuw, sterk blok.

Jouw antwoorden:

- Barsten **genezen niet meer vanzelf**: ze blijven (ook na een herstart, ze staan in de wereld) tot
  je ze herstelt.
- **Plamuren** maakt het blok elke keer **15% zwakker** (85%, 70%, ...), nooit minder dan 40%.
- **Twee machines op stroom**: een **Crusher** (gebarsten blok naar poeder) en een **Compactor**
  (poeder naar een nieuw blok), elk met een vakje erin en eruit en een balkje hoe ver ze zijn.
- Het gebarsten blok en het poeder **onthouden welk blok het was**: stenen bakstenen blijven stenen
  bakstenen, obsidiaan blijft obsidiaan.

Mijn invulling (zeg het als je iets anders wil):

- **Repair Paste** (twee klei, zand en grind = 4 pasta): rechtsklik op een blok met barsten. De
  barsten zijn weg, het blok is 15% zwakker (dat zie je niet, maar het staat in de wereld). In een
  beveiligde zone alleen wie er mag bouwen (zie stap 1). Geen barsten: dan gebeurt er niets.
- **Een gebarsten (of geplamuurd) blok weghakken** geeft een **Cracked ...** (bijvoorbeeld "Cracked
  Stone Bricks"), niet het gewone blok, ook bij blokken die normaal iets anders geven (steen geeft dan
  gebarsten steen, geen keien). Wat C4 of TNT kapotmaakt, valt eruit zoals altijd.
- **1 gebarsten blok = 1 poeder = 1 nieuw blok**. Elke machine doet er 5 seconden over en gebruikt
  dan 4 stroom per tick (via kabels, zoals de lader). Trechters kunnen erin doen en eruit halen.
- Recepten: **Crusher** = ijzer, een slijpsteen, een computerchip en een zuiger; **Compactor** = ijzer,
  twee zuigers, een computerchip en een aambeeld.

De stappen, één per commit:

- [x] **1. Barsten blijven, en plamuren**
      Barsten genezen niet meer vanzelf: ze staan in de wereld (`c4/CrackData`: per blok de
      breekpunten, welk blok het was en hoe vaak het geplamuurd is) en blijven ook na een herstart. Je
      ziet ze zoals altijd (zoals bij hakken), om de tien seconden opnieuw gestuurd, zodat je ze ook
      ziet als je later aankomt. Komt er op die plek een ander blok (gehakt, opgeblazen, weggeduwd,
      `/setblock`), dan vergeet de wereld het meteen: daarvoor is er een tweede mixin
      (`mixin/LevelChunkMixin`), want NeoForge heeft geen event dat bij elke blokverandering komt. Het
      rekenwerk staat nu in `c4/Cracks`. **Repair Paste** (twee klei, zand en grind = 4): rechtsklik op
      een blok met barsten. De barsten zijn weg (een smeergeluid, wat pasta die eraf spat, en onderaan
      "Cracks filled. This block now has 85% of its strength."), maar het blok is 15% zwakker: obsidiaan
      20, 17, 14... nooit minder dan 40% (`patchWeaker` en `patchWeakest` in de config onder `[c4]`).
      Zonder barsten zegt hij "No cracks to fill here." en gaat er niets op. **Mijn keuze, anders dan
      hierboven eerst gepland**: in een beveiligde zone mag alleen wie er mag bouwen plamuren. Anders
      kon een inbreker een muur met wat TNT en pasta steeds zwakker maken (TNT geeft kleine barsten,
      plamuren haalt er telkens 15% af), tot één C4 genoeg is. Vijf nieuwe tests
      (`gametest/CrackGameTests.java`): barsten blijven en worden bewaard, plamuren en hoeveel zwakker,
      zonder barsten gebeurt er niets, een nieuw blok is weer sterk, en plamuren in een zone.
- [x] **2. Gebarsten blokken en de Crusher**
      Hak je een blok met barsten (of een geplamuurd blok) weg, dan valt er een **Cracked ...** uit
      (`repair/CrackDrops`): valt het blok normaal zelf, dan evenveel gebarsten blokken (een dubbele
      plaat geeft er twee); valt er iets anders (steen geeft keien), dan één gebarsten blok van het blok
      zelf; valt er niets (verkeerd gereedschap, glas zonder zijdezacht), dan ook niets. Wat C4 of TNT
      kapotmaakt valt eruit zoals altijd, en blokken met iets erin (kisten, shulkerdozen...) en de vrije
      blokken van een zone (ladders, fakkels...) ook. Het gebarsten blok en het poeder onthouden van welk
      blok ze komen (de data component `imttech:from_block`) en heten daarnaar ("Cracked Stone Bricks",
      "Obsidian Powder"). Een gebarsten blok ziet eruit als het blok zelf met de barsten erover die je
      ook ziet als je aan een blok hakt (`client/repair/CrackedBlockRenderer`), in je hand, op de grond
      en in je inventaris; het poeder is een hoopje in de kleur van het blok (zoals op een kaart; heel
      donker wordt wat lichter, anders zie je het niet). **De Crusher** (ijzer, een slijpsteen, een
      computerchip en een zuiger): een metalen kast met twee maalwielen achter een venster, die op stroom
      (via kabels of direct tegen een generator) van een gebarsten blok poeder maakt: 5 seconden per
      blok, 4 stroom per tick (`machineTicks` en `machinePower` in de config onder `[repair]`). Rechtsklik
      opent zijn scherm: het vakje erin (leeg: een vaag voorbeeld van wat erin hoort), een blauwe pijl die
      vol loopt, het vakje eruit, en onderaan wat hij doet ("Crushing...", "No power: connect it with power
      cables.", "Output full: take it out.", "Put a cracked block in."). Valt de stroom weg of zit het
      vakje eruit vol, dan wacht hij (hij vergeet niet hoe ver hij was). **Mijn keuzes**: trechters doen
      er van boven en opzij iets in en halen het er van onder uit (zoals bij een oven, ook voor buizen van
      andere mods); er gaat alleen in wat hij kan verwerken. Terwijl hij werkt draaien zijn wielen, brandt
      zijn lampje blauw, komt er wat stof van het blok bovenuit en hoor je heel zacht een slijpsteen (elke
      seconde), en een korte knars als er een poeder klaar is. Een comparator leest hoe vol hij zit. Vijf
      nieuwe tests: wat er uit een gebarsten, gewoon en geplamuurd blok valt, wat er uit een opgeblazen
      blok valt, poeder maken (en de stroom die het kost), niets zonder stroom, en trechters. Screenshots:
      `-Ponly=repair` (`r01_crusher_working`, `r02_crusher_screen`, `r03_cracked_in_hand`). Op obsidiaan
      zie je de barsten ook op het voorwerp bijna niet (net als in de wereld).
- [x] **3. De Compactor**
      **De Compactor** (ijzer, een aambeeld, twee zuigers en een computerchip): dezelfde kast als de Crusher,
      met in zijn venster een hydraulische pers. Hij perst poeder tot een **volledig nieuw blok** van
      hetzelfde soort: een gewoon blok, zonder barsten en nooit geplamuurd, dus weer helemaal sterk.
      Ook 5 seconden en 4 stroom per tick, met hetzelfde soort scherm ("Pressing...", "Put powder in."),
      dezelfde trechters en een comparator. Hij neemt alleen poeder aan, de Crusher alleen gebarsten
      blokken. Terwijl hij werkt staat zijn perskop omlaag en hoor je heel zacht een zuiger; is een blok
      klaar, dan hoor je het geluid van dat blok als je het neerzet (glas klinkt anders dan steen; de
      Crusher laat nu ook het breekgeluid van het blok horen). Twee nieuwe tests: de Compactor zelf (en
      wat beide machines wel en niet aannemen), en **de hele weg rond**: obsidiaan met barsten, twee
      keer geplamuurd (nog 14 van de 20), weghakken, Crusher, Compactor, terugzetten: weer 20.
      Screenshots: `-Ponly=repair` (beide machines in `r01`, en `r04_compactor_screen`).
- [x] **4. Showcase, screenshots en LEESMIJ**
      Showcase-functie 76 **Repairing cracks** (in survival, in de basis van de zones): vijf stenen
      bakstenen in de zuidmuur hebben barsten; je plamuurt er een ("Cracks filled. This block now has 85%
      of its strength."), hakt er een weg (het gebarsten blok komt in je hotbar), en daarachter staan de
      Crusher en de Compactor op een generator: hun schermen lopen vol, en het nieuwe blok zet je terug in
      het gat. De showcase heeft nu 84 functies (de toren, het wrak en de rest schuiven één op). Ik heb
      hem in testmodus afgespeeld en op zes momenten bekeken. Screenshots van de rondleiding:
      `-Ponly=repair` (`r01` beide machines aan het werk met een gebarsten blok op de grond, `r02` en
      `r04` hun schermen, `r03` gebarsten obsidiaan in je hand en de hotbar met gebarsten blokken en
      poeder in vier kleuren). LEESMIJ.md heeft een eigen stuk "Barsten herstellen".

### 12. Inbreken: lezers en panelen hacken, en zakkenrollen (9 oktober)

Jouw wens: keycardlezers en codepanelen **hacken met een leuke minigame**, en een **keycard pikken**
uit de zakken van een andere speler.

Jouw antwoorden:

- **Een keycardlezer: een stroomcircuit.** Je schroeft de lezer open en ziet een raster met stukjes
  draad. Klik om ze te draaien en leid het signaal van CARD naar ACCEPT, zonder ALARM te raken. Tegen
  de klok.
- **Jij kiest het niveau** van de kaart die je namaakt (1 tot 5), voordat je begint: hoe hoger, hoe
  moeilijker (meer stukjes, minder tijd). Lukt het, dan leest de lezer een geldige kaart van dat
  niveau, en je diagram beslist zoals altijd wat er gebeurt.
- **Een codepaneel: vingerafdrukken en de volgorde.** Eerst bestuif je de toetsen: vegen laten zien
  welke cijfers de code gebruikt. Daarna raad je de volgorde; je hoort alleen hoeveel cijfers op de
  goede plek staan.
- **Zakkenrollen: een zak-minigame.** Achter iemand opent rechtsklik een klein scherm van zijn zak:
  de kaarten liggen omgekeerd tussen rommel, en je pakt er één terwijl een "hij merkt het"-meter
  volloopt bij elke beweging.

Mijn invulling (zeg het als je iets anders wil):

- **Het gereedschap: de Bypass Kit** (een inbraakset; recept zonder vaste vorm: computerchip, schaar,
  glowstonestof als poeder voor vingerafdrukken, redstone en ijzer). Rechtsklik ermee op een lezer of
  paneel en de minigame begint. Hij gaat 16 keer mee: elke hack die je begint kost er één, ook als je
  stopt. Zakkenrollen doe je met je blote hand.
- **Alleen echt aangesloten apparaten**: hangt de lezer of het paneel niet aan een kern met een
  draaiende server, dan valt er niets te bedotten ("not connected to a running server") en kost het
  niets. Je eigen lezers hacken mag ook, om je beveiliging te testen.
- **Tijdens de hack loopt het spel door** (torentjes schieten gewoon op je). Esc, weglopen (verder dan
  6 blokken) of doodgaan stopt de hack zonder alarm. Op elk scherm staat in één zin waarom het werkt.
- **Mislukt** (de tijd is om, ALARM langer dan 1 seconde geraakt, of geen pogingen meer): rood lampje,
  een alarm in het logboek ("Someone is tampering with Lab door", met je naam erachter zoals bij elk
  alarm), I.M.T waarschuwt de eigenaar, de beveiliging jaagt op je, en dat apparaat laat jou 60
  seconden niet meer proberen.
- **Het stroomcircuit**: linksklik draait een stukje rechtsom, rechtsklik linksom. Stukjes die aan CARD
  vastzitten lichten blauw op. Raakt het signaal ALARM, dan knippert die rood en piept hij: draai het
  stukje binnen 1 seconde weg, anders gaat het alarm af. Niveau 1: 5 bij 4 stukjes en 60 seconden;
  niveau 2: 6×4 en 55 s; niveau 3: 6×5 en 50 s; niveau 4: 7×5 en 45 s; niveau 5: 8×6 en 40 s; met 1,
  1, 2, 2 en 3 ALARM-punten. Elk raster heeft een oplossing (zijn er meer, dan telt elke goede).
- **De nagemaakte kaart** krijgt een verzonnen nummer (#2000 tot #9999, nooit een nummer van jouw lijst)
  en geen naam. In het logboek staat hij als een gewone kaart ("Bob used card #4071 (level 3) at Lab
  door"), maar dat nummer staat niet in je app Keycards: zo zie je dat er gehackt is. Wie heel
  voorzichtig is, laat zijn diagram ook op `card.number` letten.
- **De code van een paneel** staat in je diagram (`if code == "4321":` onder `when codepanel("...")`,
  ook in een def die daar gebruikt wordt). Staan er meer codes voor één paneel, dan kraakt de kit de
  eerste; staat er geen, dan zegt hij "No diagram checks a code on this panel."
- **Vingerafdrukken**: houd de muisknop ingedrukt en veeg over alle cijfertoetsen; de cijfers van de
  code krijgen afdrukken, een cijfer dat twee keer in de code zit krijgt er meer. Bovenaan staat hoe
  lang de code is. **De volgorde**: je typt alleen cijfers met afdrukken; elke poging zegt hoeveel
  cijfers op de goede plek staan, en hoeveel volgordes er nog kunnen (zoals de terminal van de laptop
  woorden doorstreept). Je krijgt de lengte van de code + 2 pogingen (4 cijfers: 6 pogingen). Ik heb
  het nagerekend: wie logisch blijft raden, heeft bij 4 cijfers gemiddeld 3,6 pogingen nodig en
  bijna nooit meer dan 5. Een lange code met 7 of 8 verschillende cijfers is echt moeilijk, en dus
  veiliger. Je pogingen gaan niet langs je diagram (geen "wrong code" in het logboek); alleen de goede
  code typt de kit echt in ("Bob entered a code at Panel 1"), en dan beslist je diagram. De code
  blijft op je scherm staan: voortaan typ je hem gewoon in.
- **Zakkenrollen**: sluip met een lege hand achter iemand (meer dan ongeveer 100 graden naast waar hij
  heen kijkt, binnen 2,5 blokken) en rechtsklik op hem. In zijn zak liggen zijn keycards (alles in zijn
  inventaris behalve wat hij in zijn handen houdt; hooguit 6) omgekeerd tussen 5 tot 8 stukjes rommel
  (een touwtje, een bonnetje, een muntje, een vuursteen: alleen plaatjes). Sleep met de muis: elke
  beweging vult de meter (snel slepen meer dan rustig), stilhouden laat hem langzaam zakken. Sleep een
  kaart boven uit de zak en hij is van jou: hij draait om en je ziet welke het is (uit een stapel krijg
  je er één). **Betrapt** ben je als de meter vol is, of als hij zich omdraait en je ziet; loopt hij weg
  (verder dan 3,5 blokken), dan houdt het gewoon op. Betrapt: hij krijgt "... tried to pick your
  pocket!" en jij licht 10 seconden op, zodat hij je terugvindt. Lukt het, dan merkt hij niets (tot hij
  in zijn rugzak kijkt). Bij dezelfde speler kun je het pas na 30 seconden weer proberen, wat er ook
  gebeurde. Een server kan het uitzetten met `pickpocket.allowed` in `imttech-server.toml`, net als
  "hack player".
- Alle getallen staan in de config onder `[bypass]`: de tijden, hoe lang ALARM mag, de wachttijd na
  een mislukte hack, de extra pogingen, en het bereik en de wachttijd van zakkenrollen.

De stappen, één per commit:

- [x] **1. De Bypass Kit en een keycardlezer hacken**
      De **Bypass Kit** (zonder vaste vorm: computerchip, schaar, glowstonestof, redstone en ijzer; gaat 16
      keer mee). Rechtsklik ermee op een keycardlezer: eerst kies je welke kaart je namaakt (niveau 1 tot
      5, met per niveau de grootte, de tijd en het aantal ALARM-punten), dan het **stroomcircuit**:
      linksklik draait een stukje rechtsom, rechtsklik linksom, en wat aan CARD vastzit licht blauw op.
      Komt het signaal bij ACCEPT, dan wordt het pad groen en leest de lezer een geldige kaart van dat
      niveau met een verzonnen nummer (#2000 tot #9999, nooit een nummer van je lijst); je diagram beslist
      zoals altijd. Raakt het signaal ALARM, dan knippert het rood, piept het en loopt een balkje leeg: 1
      seconde om dat stukje weg te draaien. Mislukt (ALARM of de tijd is om): rood lampje, een alarm
      "Someone is tampering with Lab door" (met je naam erachter), de beveiliging jaagt op je, en die lezer
      laat je 60 seconden niet meer proberen. Esc, weglopen (verder dan 6 blokken) of doodgaan stopt
      zonder alarm. **Hoe de server het controleert** (`bypass/Bypass`): hij stuurt alleen een getal; je
      spel en de server maken daaruit precies hetzelfde raster (`bypass/CircuitPuzzle`), en als je klaar
      bent rekent de server zelf na of het signaal echt bij ACCEPT komt en of je op tijd was. **Het
      raster**: eerst een pad van CARD naar ACCEPT (kolom voor kolom, soms een stukje op of neer), de rest
      willekeurige stukjes (recht, bocht, T, kruis), en de ALARM-punten het liefst naast een bocht van het
      pad die je met één verkeerde draai naar buiten draait: zo is ALARM echt gevaarlijk. Zonder draaiende
      server of van te ver gebeurt er niets (en kost het niets). De tijden staan in de config onder
      `[bypass]`. Screenshots: `-Ponly=bypass` (`bp01`-`bp09`). Vier nieuwe tests: honderden rasters per
      niveau (altijd op te lossen, nooit al klaar, hetzelfde getal geeft hetzelfde raster, en meestal raak
      je ALARM met één verkeerde draai), een nagemaakte kaart van niveau 3 opent de kluis en een van
      niveau 2 niet (en een raster dat niet klopt telt niet), mislukken geeft alarm en de wachttijd, en
      zonder server gebeurt er niets.
- [x] **2. Een codepaneel hacken**
      Rechtsklik met de Bypass Kit op een codepaneel. De kit zoekt de code in je diagram
      (`logic/PanelCodes`): elke `code == "..."` (ook `"..." == code` en `!=`) onder
      `when codepanel("...")` van dat paneel, ook in een def die daar gebruikt wordt, alleen in diagrammen
      die aan staan; bij meer codes de eerste. Staat er geen, dan "No diagram checks a code on this panel"
      (en kost het niets). Het scherm: eerst **stoffen**: houd de muisknop ingedrukt en veeg over de
      toetsen (stofjes vallen van je kwast, je hoort een kwast). Op de cijfers van de code verschijnen
      lichtgele afdrukken (glowstonestof), dikker bij een cijfer dat vaker in de code zit; C en OK hebben
      altijd afdrukken. Bovenaan staat hoe lang de code is, en als alle cijfers gestoft zijn staat er
      "Prints on 1, 4 and 7. Extra dirty: 1 is in the code more than once." Dan **de volgorde**: je typt
      (of klikt) alleen cijfers met afdrukken, Enter probeert. Je hoort één klik per cijfer op de goede
      plek, en rechts staan je pogingen ("4 1 1 7 ■■□□ 2 right"), hoeveel volgordes er nog kunnen
      (`bypass/PanelOrders`) en hoeveel pogingen je nog hebt (de lengte + 2, `orderExtraTries` onder
      `[bypass]`). Elke poging gaat naar de server, die de code kent en alleen "zoveel goed" terugzegt:
      de code zelf komt pas op je scherm als je hem hebt. Je pogingen komen niet in het logboek en gaan
      niet langs je diagram. Goed: "CODE CRACKED: 4711. The kit typed it in." en de kit typt hem echt in
      ("... entered a code at Front door"), je diagram beslist. Geen pogingen meer: het paneel slaat
      alarm, net als bij de lezer, en laat je een minuut niet meer proberen. Screenshots: `-Ponly=bypass`
      (`bp10`-`bp15`). Drie nieuwe tests: de codes uit een diagram (ook uit een def, niet van een ander
      paneel of een diagram dat uit staat) en het rekenwerk, kraken met pogingen die niet tellen en die
      wel tellen en dan de kluis open, en te veel pogingen geeft alarm (en een paneel zonder code valt
      niet te kraken).
- [x] **3. Zakkenrollen**
      Sluip met een lege hand achter iemand (meer dan ongeveer 100 graden naast waar hij heen kijkt,
      binnen 2,5 blokken) en rechtsklik op hem. Sta je ervoor, dan zegt hij "Get behind ... first: they
      would see you." Het scherm (`client/pickpocket/PocketScreen`): bovenaan een vak "pull a card up
      here", daaronder zijn zak (bruine stof met een stiksel) met zijn keycards omgekeerd (een blauwe
      achterkant, voor iedereen hetzelfde) tussen 7 tot 10 stukjes rommel (touwtje, papiertje, muntje,
      vuursteen, stokje, veer, ijzerklompje, klei: alleen plaatjes), voor een groot deel bovenop de
      kaarten. Rechts de meter **NOTICED**: elke pixel slepen telt (rustig 0,25% per pixel, sneller dan 8
      pixels per tick 0,6%), iets oppakken 2%; hou je even stil, dan zakt hij (6% per seconde). Vanaf
      60% hoor je een hartslag, vanaf 80% knippert hij rood. Sleep een kaart boven uit de zak: de server
      zegt welke het is, hij draait om en ligt groot in je hand ("Keycard: Lab access", LEVEL 3), en hij
      zit in je rugzak (uit een stapel één). **Welke kaart achter welke achterkant zit, weet alleen de
      server** (`pickpocket/Pickpocket`): je spel kent alleen het aantal. In de zak zit alles uit zijn
      inventaris behalve wat hij in zijn hand houdt (hooguit 6 kaarten). Heeft hij hem net verplaatst,
      dan "That card isn't there any more." **Betrapt**: de meter vol, of hij draait zich om en ziet je
      (binnen ongeveer 60 graden van waar hij kijkt, geen muur ertussen): hij krijgt in de chat "... tried
      to pick your pocket!" met een piep, en jij licht 10 seconden op. Loopt hij weg (verder dan 3,5
      blokken), dan houdt het op, zonder betrapt. Bij dezelfde speler mag je het pas na 30 seconden weer
      proberen, wat er ook gebeurde. Spelers in creative of spectator kun je niet zakkenrollen. Een
      server zet het uit met `pickpocket.allowed = false` in `imttech-server.toml`; het bereik, de
      wachttijd en hoe lang je oplicht staan onder `[bypass]`. Screenshots: `-Ponly=bypass`
      (`bp16`-`bp20`, alleen het scherm: in de testwereld is er geen tweede speler). Drie nieuwe tests:
      van voren mag niet en van achteren pik je een kaart (hij is hem kwijt, jij hebt hem, de kaart in zijn
      hand blijft, en daarna moet je wachten), betrapt als hij zich omdraait of als de meter vol is (en
      dan licht je op), en weglopen (en iemand in creative).
- [x] **4. Showcase, screenshots en LEESMIJ**
      Showcase-functie **Hacking and pickpocketing** (na "Repairing cracks", in survival), in het lab
      van "Server racks and Logic", met de Bypass Kit in je hand. De keycardlezer: het stroomcircuit speelt
      zichzelf (een kaart van niveau 3, stukje voor stukje vanaf CARD; soms raakt het even een ALARM en
      knippert alles rood), het pad wordt groen, en de deur gaat 3 seconden open. Het codepaneel: stoffen
      (afdrukken op 1, 2, 3 en 4), raden ("1 2 3 4: 0 right", "2 1 4 3: 0 right", "3 4 1 2: 0 right") en bij
      de vierde poging CODE CRACKED 4321: de lamp gaat aan. Dan zakkenrollen bij Seppe (die in de
      showcase-wereld echt een speler is): je sluipt achter hem, schuift de rommel opzij en trekt een
      kaart uit zijn zak, en die zit daarna echt in je hotbar. De tweede keer draait Seppe zich om:
      CAUGHT!, en jij licht op. Ik heb hem in testmodus afgespeeld en op zes momenten bekeken. De showcase
      heeft nu 85 functies. Screenshots: `-Ponly=bypass` (`bp01`-`bp20`). LEESMIJ.md heeft een eigen stuk
      "Inbreken: lezers en panelen hacken, en zakkenrollen" (na "Barsten herstellen"), en de lijsten van de
      config en de screenshots noemen zakkenrollen en `bypass`.

### 13. Licht: de Arc Lamp en de lichtschakelaar (9 oktober)

Jouw wens: een **lamp op stroom** die **veel helderder** kan dan lichtniveau 15, "ik wil er een van
**100**". Hij werkt **alleen op stroom**: stroom = aan, geen stroom = uit. En een **lichtschakelaar** die
je **tussen een kabel** zet, om zo de lichten te besturen.

Hoe lichtniveau 100 kan: Minecraft bewaart licht in 4 bits per blok, dus meer dan 15 bestaat niet. De
lamp meet daarom zelf hoe ver licht van 100 zou komen (stap voor stap, net als het spel: door lucht,
glas en water, niet door muren), en laat het spel op al die plekken (tot 85 stappen ver) doen alsof er
een onzichtbare lichtbron van 15 staat. Daarna dooft het licht zoals gewoon licht uit tot 0 op 100
blokken. Een helderder blok dan 15 kan het scherm niet tekenen: "100" betekent hier dus vooral **veel
verder** (vol licht tot 85 blokken in plaats van een paar), plus een gloed om de lamp heen.

Mijn invulling (zeg het als je iets anders wil):

- **Arc Lamp** (ijzer, vier glazen ruiten, gloeisteen en een computerchip): een stalen blok met aan vier
  kanten glas. **2 stroom per tick** zolang hij brandt (een volle warmtegenerator: ruim acht minuten).
  Een lamp die uit is, gaat pas aan als het net stroom heeft voor 5 seconden, zodat hij aan een te
  zwakke generator niet elke tick knippert. Alle drie de getallen staan onder `[lights]` in de config
  (`arcLampMaxLevel`, het hoogste lichtniveau, tussen 15 en 128).
- **Lichtschakelaar** (een hendel, een ijzeren klompje en een stroomkabel = 2): een plaatje met een
  wipschakelaar, aan de muur, op de vloer of aan het plafond. Rechtsklik zet hem om. Aan: blauw lampje,
  de stroom gaat erdoor alsof het kabel is. Uit: rood lampje, daar houdt de stroom op (ook als er een
  getekende kabel door de schakelaar loopt). Hij geldt voor álles achter hem, niet alleen lampen.
- **Ook via I.M.T**: wie een schakelaar neerzet, krijgt hem in de redstone-lijst ("Light switch 1"),
  net als een torentje of de koepel.

De stappen:

- [x] **1. De Arc Lamp, lichtniveau 100**
      `light/ArcLampBlock` en `ArcLampBlockEntity`: stroom halen en aan/uit, het blok zelf geeft 15 als
      hij brandt, rechtsklik zegt hoe hij ervoor staat. Het licht zelf: `light/LampSurvey` meet op een
      achtergrond-thread (de server loopt gewoon door) welke plekken binnen 85 stappen liggen;
      `light/ArcLampLight` houdt dat bij en geeft het aan het licht-rekenwerk van het spel. Daarvoor zijn
      er twee nieuwe mixins: `BlockLightEngineMixin` (het spel vraagt een blok "hoeveel licht geef jij?",
      wij zeggen 15 op die plekken) en `LightEngineMixin` (de honderdduizenden plekken die opnieuw moeten,
      in één keer in plaats van één klusje per plek). Gaat hij aan of uit, dan rekent het spel ruim
      300.000 plekken na: in de test duurde dat minder dan een seconde. De eerste keer meet hij (een
      halve seconde "opwarmen"); daarna onthoudt hij zijn licht, dus met de schakelaar is het meteen aan
      en uit. Bouw je in zijn licht (de `LevelChunkMixin` ziet elk blok dat verandert), dan meet hij
      opnieuw zodra het even stil is: een dichte ruimte wordt donker. Gaat hij uit terwijl een deel van
      zijn licht in een niet-geladen chunk ligt, dan ruimt `light/LightDebt` dat op zodra die chunk weer
      laadt (ook als het spel stopt voordat het licht klaar was). **Gevonden onderweg**: sinds Minecraft
      1.20 stuurt de server lichtveranderingen niet meer naar je spel (je spel rekent bij een
      blokverandering zelf); ons licht verandert zonder blokverandering, dus in je spel bleef het eerst
      donker terwijl de server het licht wel had. Nu stuurt de lamp het licht zelf naar iedereen die het
      gebied ziet, zodra het klaar is. Een stralenkrans om de brandende lamp (`client/light/ArcLampRenderer`)
      laat zien dat hij feller is dan een gewone lamp; je ziet hem tot 256 blokken ver.
- [x] **2. De lichtschakelaar**
      `light/LightSwitchBlock`, met een uitzondering in `energy/PowerGrid`: een schakelaar die aan staat
      geleidt zoals een kabel, een die uit staat houdt alles tegen.
- [x] **3. Tests, screenshots, showcase en LEESMIJ**
      Drie nieuwe tests (`gametest/LightGameTests.java`): de schakelaar knipt de stroom (ook met een kabel
      eronder, en tussen twee kabeleinden); de lamp brandt alleen met stroom; en het licht zelf: 15 op 60
      blokken, 5 op 95, donker op 105, licht in een glazen doos, donker in een stenen doos, donker in een
      doos die je later in het licht dichtbouwt, en alles weer donker zonder stroom. Screenshots:
      `-Ponly=light` (`lt01`-`lt08`: 's nachts uit, aan van dichtbij, het veld van ver weg en van bovenaf
      een ruit van 85 blokken licht, sinds stap 4 het instelscherm en de lamp op 40, de schakelaar op de
      kabel, en weer uit). Showcase-functie 61 **Arc
      Lamp and light switch** (na "Power grid"), met een echte rechtsklik op de schakelaar; in testmodus
      afgespeeld en op drie momenten bekeken. De showcase heeft nu 86 functies. LEESMIJ.md heeft een
      eigen stuk "Licht: de Arc Lamp en de lichtschakelaar" (na "De stroomkast").
- [x] **4. De helderheid instellen** (10 oktober: "zorg dat je de brightness kunt instellen")
      Rechtsklik op de lamp opent nu zijn instelscherm (`client/light/ArcLampScreen`, de serverkant in
      `light/ArcLamps`, twee nieuwe netwerkberichten). **Brightness**: het lichtniveau van deze lamp, van
      1 tot 100 (het maximum is de config, nu `arcLampMaxLevel`). Onder het schuifje staat wat het
      betekent ("Full light up to 25 blocks, dark after 40", of tot 15 "Like a normal lamp") en hoeveel
      stroom hij dan gebruikt. **Mijn invulling**: zachter is zuiniger (op 50 de helft van op 100); tot 15
      is het een gewone lamp (het blok geeft zelf dat licht, zonder meting); de lamp meet opnieuw zodra je
      even stopt met schuiven, en tot dan blijft zijn oude licht staan. Omdat het niet zeker was of je met
      "brightness" het licht of de gloed bedoelde, kan allebei: **Glow** zet de stralenkrans van 0% (geen)
      tot 100%, en een zachtere lamp krijgt vanzelf een kleinere gloed. Elk schuifje heeft een **-** en een
      **+** voor één stapje. Iedereen die bij de lamp staat, kan hem instellen (zoals een hendel); de lamp
      onthoudt het, en wie de lamp ziet, krijgt het mee. Een nieuwe test: een lamp begint op 100; het scherm
      werkt alleen van dichtbij; op niveau 40 is het 15 op 20 blokken, 10 op 30 en donker op 40; op niveau
      10 geeft het blok zelf 10 en zijn er geen onzichtbare lichtbronnen; de gloed blijft tussen 0 en 100%;
      en op 50 gebruikt hij de helft. Screenshots: `-Ponly=light` (`lt05_lamp_screen`, `lt06_dimmed_from_above`:
      een veel kleinere ruit). De showcase-functie zet de lamp met het schuifje van 100 naar 40.
- [x] **5. Licht per kamer, en geen gloed meer** (13 oktober: "zorg dat het light van de arc lamp niet door
      muren kan, dus dat je echt light per kamer moet hebben en niet gewoon een block die overal werkt", en
      "arc lamp glow mag volledig weg"). Het licht ging eigenlijk niet *door* muren, maar stroomde 85 stappen
      ver om alles heen: door elke deuropening en gang, om elke hoek, en dwars door dichte deuren (in
      Minecraft houdt een deur geen licht tegen). Zo verlichtte één lamp je hele huis. Nu meet
      `light/LampSurvey` wat de lamp in **rechte lijnen** kan zien: schil voor schil krijgt elke plek een
      getal van 0 tot 1 (hoe goed de lamp hem ziet, het gewogen gemiddelde van zijn buren aan de lamp-kant),
      en vanaf 0.5 is het vol licht. Achter een muur, om een hoek of in de kamer ernaast is het donker; door
      een open deur valt een bundel. Een **dichte deur of dicht luik** telt als muur (`stopsBeam`), en een
      deur open- of dichtdoen laat de lamp opnieuw meten. **Mijn invulling**: de lamp kijkt vanuit elk vrij
      plekje naast zich (anders zag een lamp in het plafond schuin langs het plafond niets, en bleef een
      grote hal aan de randen donker); een lamp die ín een muur zit, schijnt dus naar beide kanten. Onder
      een dichte deur door komt nog wat gewoon licht, zoals bij een fakkel (dat is het spel zelf, niet de
      lamp). De **gloed** is helemaal weg: `ArcLampRenderer` is verwijderd, het schuifje Glow uit het scherm
      (daar staat nu "Straight lines only, not through walls or closed doors: one lamp per room."), en een
      oude lamp vergeet zijn opgeslagen gloed. Een nieuwe test (`arc_lamp_light_stays_in_its_room`): twee
      kamers met een deur ertussen, de lamp in het plafond; dicht: de eigen kamer vol licht tot in de
      hoeken, de andere kamer geen enkele plek van de lamp; open: een bundel door de deur, maar niet naast
      de deur en niet in de verre hoek; weer dicht: de bundel weg. 212 tests: 209 groen, de 3 rode van
      hoofdstuk 14 stap 2.

### 14. Eén centraal configbestand (11 oktober)

Jouw wens: "een centraal config bestand waar **echt alles** in staat: **powerproduction per item**,
**reload time voor de satelliet per level**...", makkelijk te lezen, met duidelijke uitleg, en de
oude configbestanden erven ervan **of** de code haalt de waarde **meteen uit het hoofdbestand**. Ik
koos het tweede: er is nu één plek, en geen tussenbestanden meer.

- **`config/ImtConfig.java`**: alle getallen van de mod, in 27 hoofdstukken (Rules, Glasses,
  Scanner, **Power**, Drones, **Satellite**, ... Raids), met een inhoudsopgave en uitleg over de
  eenheden bovenaan, en bij elk getal een regel Nederlands. De code leest overal rechtstreeks hieruit,
  bijvoorbeeld `ImtConfig.Satellite.COOLDOWN`.
- **`config/imttech.toml`**: wordt daar vanzelf uit gemaakt (`config/ImtConfigLoader`), met
  dezelfde hoofdstukken en Engelse uitleg. **344 instelbare getallen** (eerst 204 in het ene bestand en 14 in het andere). Het vervangt
  `imttech-balance.toml` en `imttech-server.toml`; de oude namen `drones.orbitHeight`,
  `drones.swarmMax` enzovoort zijn gebleven. Weg: `ImtBalance`, `ImtConfig`, `BalanceConfig` en
  de 13 andere `...Balance`-klassen.
- **`[power]`**: alles van het stroomnet bij elkaar, per blok: wat het opwekt (zonnepaneel, nu ook bij
  regen en onweer; warmtegenerator; redstone), wat het opslaat, en wat het gebruikt (laserwand, koepel
  per maat, holotafel, Arc Lamp, torentje, werkbank, Crusher/Compactor, serverkast, kern, camera,
  zone, landingsplatform, drone-dock).
- **Per niveau** als rijtje: de satelliet (wachttijd, schade, bereik, richttijd, inslagen, explosie,
  schuiftijd, drones om een upgrade te brengen: 11 getallen voor Recharge/Power/Targeting, 10 voor de
  schietstanden), de koepel per sterkte (schildkracht, bijladen) en per maat (stroom), de scanner per
  niveau, en het stroomcircuit per kaartniveau (nu ook de seconden). De formules die eerst in de code
  zaten (`SatelliteStats`, `DomeSettings`) zoeken nu alleen het getal op.
- **Er nieuw in** (stond eerst vast in de code): alle getallen die in de balance-bestanden `final`
  waren (vliegen met het harnas, raketten, de duik van drones, raiddrones, ...), en losse getallen van
  elders: wachtwoordpogingen bij inbreken, hoeveel backups een datacenter bewaart, de afstanden van
  de holotafel, hoe lang de satelliet-levering duurt, de seconden stroom voor een laserwand.
- **Vast gebleven** (staan wél in `ImtConfig`, met `final` en "VAST" erbij): wat het spel bij het
  opstarten al nodig heeft (levens van drones en vijanden, het pantser van het harnas, hoe vaak een
  Bypass Kit meegaat, hoeveel kubussen een zone mag hebben) en hoe schermen eruitzien (vakjes in een
  serverkast of vrachtdrone).
- **Mijn invulling**: je persoonlijke instellingen (stem, volume, Spotify, zone-rand) blijven in
  `imttech-client.toml`: die kiest elke speler zelf, en het wereldbestand komt op een server van de
  server. Eén piepklein verschil: een bombardement op niveau 0 (dat op slot zit) rekent nu met de
  explosie van niveau 1.

De stappen:

- [x] **1. ImtConfig, de lader en alles omzetten**
      118 bestanden lezen nu uit `ImtConfig`; `logic/LogicStats` en `CircuitPuzzle` kregen de kleine
      rekenhulpjes uit de oude bestanden. De bestaande configtest kijkt nu ook of een rijtje per niveau
      telt (de satelliet wacht dan echt korter), of een aan/uit-regel telt, en of een rijtje met het
      verkeerde aantal getallen terugvalt op de standaard. Alle 203 tests groen. LEESMIJ.md hoofdstuk 6
      en de stukken die naar de oude bestanden verwezen, zijn bijgewerkt.
- [x] **2. Kommagetallen voor stroom** (11 oktober: "ik heb bij power ints naar floats veranderd ... dit
      werkt niet ... zorg dat alles in floats staat zodat ik makkelijk heel kleine getallen kan gebruiken")
      Er gingen twee dingen mis. Het net rekende in hele getallen, dus 0.3 per tick werd 0. En een
      nieuwe standaardwaarde in `ImtConfig` verandert een bestaand `imttech.toml` niet: daar stond
      nog 6. Nu zijn **alle energiegetallen in `[power]` kommagetallen** (float; aantallen zoals "hete
      blokken" en tijden in ticks blijven hele getallen). Generatoren bewaren hun stroom als kommagetal;
      apparaten betalen met `PowerGrid.pay`, dat per apparaat onthoudt wat nog geen hele stroom was
      (0.25 per tick = elke vierde tick 1); snelheden zoals "zoveel per tick in de bril" gaan via
      `energy/EnergyCarry`. Het scherm van een generator toont nu ook "0.3 per tick". Jouw vijf nieuwe
      waarden staan nu ook in `run/config/imttech.toml`, en `ImtConfig` waarschuwt bovenaan voor die
      valkuil. Een nieuwe test (`power_works_with_fractions`). Met de oude waarden (6 en 14 per tick)
      zijn alle 204 tests groen. **Met jouw nieuwe waarden** (0.3 en 0.7) zijn er 3 rood:
      `biebia_bunker_lasers_need_the_reactor`, `biebia_tower_vault_trap` en `biebia_tower_lasers_have_power`.
      De warmtegenerator naast de lava in de bunker en de toren maakt nu te weinig voor hun laserwanden
      (elke zender gebruikt nog 1 per tick). Jouw keuze: zo laten, je kijkt zelf naar de getallen.
- [x] **3. Alle getallen kommagetallen** (11 oktober: "zorg dat alle getallen in de mod met komma getallen
      kunnen werken"). De 169 getallen die nog heel waren, zijn nu ook float. **Energie** telt precies:
      de batterij van de bril bewaart nu een kommagetal (een bril uit een oude wereld leest hij gewoon), dus
      kosten en verbruik zoals 0.5 per seconde werken. **Tijden in ticks en aantallen** (drones, tekens,
      niveaus, vakjes) worden afgerond waar ze gebruikt worden (`Math.round`), zodat alles met hele getallen
      precies werkt zoals vroeger. **Seconden en minuten** (inbreken, zakkenrollen, raids) gaan eerst naar
      ticks en worden dan pas afgerond. Schermen en berichten tonen getallen zonder ".0"
      (`config/Numbers`). Alleen de VAST-getallen (opstarten en schermen) zijn heel gebleven. De configtest
      zet nu 123.5 als kosten, en de kommagetallentest kijkt ook of 4 x 0.25 uit de bril precies 1 kost.
      Met de oude stroomwaarden alle 204 tests groen.
- [x] **4. Een knop /tick, /sec, /min, /hour** (11 oktober: "maak een knop die laat toggelen tussen per
      tick per sec per min per uur"). `client/PowerUnit`: een knopje in het scherm van de generator
      (rechtsboven), de koepel (boven Done) en de Arc Lamp (naast Done). Elke klik: tick, seconde, minuut,
      uur, en weer tick. De keuze geldt meteen voor alle schermen, ook het verbruik in het G-menu en wat een
      zone kost in de app Zones, en blijft bewaard in `imttech-client.toml` (`powerUnit`). **Mijn
      invulling**: één keuze voor alles in plaats van per scherm. Screenshots: `-Ponly=power` (`pw01`-`pw05`:
      het generatorscherm, vier keer echt op de knop geklikt), en de knop staat ook op `99_dome_screen` en
      `lt05_lamp_screen`.
- [x] **5. Is er genoeg stroom?** (11 oktober: "een output per instelling van tick/sec/min/hour zodat je
      weet of je powerproduction voldoende is"). Naast het scherm van een generator staat nu een overzicht
      van **het hele net**: hoeveel generatoren, wat ze samen **maken**, wat alle apparaten **gebruiken**,
      wat erin zit, en een oordeel: groen "Enough power: X to spare", oranje "Not enough: running on stored
      power, losing X. Empty in 8m 10s", of rood "Not enough power! Devices are missing X" (een apparaat
      kreeg zijn stroom niet). Alles in de tijd van de knop /tick. Hoe: elke generator meet steeds 6
      seconden (`ChargerBlockEntity.GRID_WINDOW`, zodat ook apparaten die om de 2 seconden betalen
      eerlijk meetellen) wat hij kon maken, wat het net eruit haalde en wat apparaten tekortkwamen
      (`PowerGrid.missed`: ook een laserwand of Arc Lamp die niet aan kan, een kern zonder stroom, een
      torentje zonder stroom voor een raket); elke seconde telt hij alle generatoren op zijn net op.
      **Mijn invulling**: een generator die vol zit, maakt even niets; dan zegt het overzicht "the generators
      are full" in plaats van "te weinig". Het overzicht staat waar het meeste plek is (rechts of links) en
      wordt kleiner als er weinig plek is. Een nieuwe test (`power_grid_overview_measures`): 0.25 gemaakt en
      0.5 gebruikt wordt zo gemeten, en een lege generator met een apparaat dat 2 per tick wil, telt als 2
      tekort. Screenshots `-Ponly=power` (met drie holotafels op één generator: oranje).

### 15. Relais: tags, omkeren en de relay emitter (13 oktober)

(De tags van stap 1 zijn op 14 oktober vervangen door groepen in een boom: zie hoofdstuk 19.)

Jouw wensen: "maak een block die **relay emitter** is zodat je ook relays kunt triggeren door redstone",
"zorg dat je relays kunt **renamen zonder bril** en dat je een **button om het off signaal te inverten**
hebt", en "zorg dat de relaygroups **tags** worden zodat je **groepen in groepen** hebt, vb: deur1 en deur2
zitten in security room en die zit in base". Jouw antwoorden: de emitter kiest in zijn scherm Follow,
Toggle of Pulse; hij werkt altijd, gratis, ook als je offline bent; een relais mag meerdere tags hebben;
tags in tags zet je in een apart tag-scherm.

- [x] **1. Tags in plaats van groepen.** Een relais heeft nu een lijst tags (`Relay.tags`), en je lijst
      onthoudt welke tag in welke tag zit (`ImtRelays.tagParents`). Een tag mag in meerdere tags zitten,
      nooit (via een omweg) in zichzelf (`wouldLoop`). Schakelen op een tag pakt alles eronder mee
      (`inTag`): je stem, de knoppen in het redstone-scherm, en ook de diagrammen op een server
      (`on("base")`, `LogicDevices`). In het scherm van een relais typ je de tags met komma's
      (`security room, doors`); hooguit 8 per relais (`relays.maxTags` in de config). Het redstone-scherm
      toont alles als **boom** (base > security room > deur1), met onderaan de knop **Tags**: het
      **tag-scherm** (`RelayTagsScreen`): links je tags (als boom, met een vakje om een nieuwe tag te
      maken), rechts klik je in welke tags de gekozen tag zit (vinkje; wat een rondje zou maken, staat
      grijs). Daar gooi je ook een tag weg (de relais blijven). Oude werelden: een groep wordt vanzelf een
      tag, de oude standaardgroep "general" wordt "geen tag".
- [x] **2. Zonder bril, en Invert.** Rechtsklik op een relais opent zijn scherm nu ook zonder bril: naam,
      tags, testen (PULSE) en weghalen. Zonder bril hoor je het antwoord boven je hotbar. De nieuwe knop
      **Invert** zet het relais om: het geeft dan redstone als het **uit** staat (`RelayBlock.INVERTED`, je
      ziet een rode rand om het lampje). **Mijn invulling**: invert staat in het blok zelf (iedereen merkt
      het), dus mag het met je bril van overal, zonder bril alleen binnen 6 blokken. Hetzelfde voor testen
      zonder bril. Schakelen op afstand blijft met bril. (Eerst mocht het ook niet in de basis van een
      ander; dat is vervangen door stap 4: alleen de eigenaar.)
- [x] **3. De relay emitter** (shapeless: een relais, een comparator en redstone). Het omgekeerde van een
      relais: krijgt hij redstone, dan schakelt hij jouw relais. In zijn scherm (`RelayEmitterScreen`) typ
      je de naam van een relais of een tag (hij zegt meteen hoeveel apparaten dat zijn) en kies je **Follow**
      (aan = aan, uit = uit, standaard), **Toggle** of **Pulse**. Gratis, geen bril nodig, en hij werkt ook
      als je offline bent: hij onthoudt zelf waar de relais staan (`RelayEmitterBlockEntity.targets`), en
      werkt dat bij zolang jij online bent (bij opslaan, elke 5 seconden en vlak voor het schakelen). Alleen
      wie hem neerzette, kan hem instellen. **Mijn invulling**: hij reageert één tick na de redstone (zoals
      een repeater), zodat een relais naast zijn eigen emitter het spel nooit in een eindeloze kring laat
      vastlopen; en hij zoekt precies (een naam, anders een tag), niet half zoals je stem.
- [x] **Tests en beelden.** Vijf nieuwe tests (`RelayGameTests`): tags in tags en schakelen (ook een rondje
      weigeren en een tag weggooien), oude groepen worden tags (en opslaan/doorsturen klopt), naam en invert
      zonder bril (ook: van ver weg niet, met bril wel, en een vreemde in jouw basis niet), de emitter met
      echte redstone (Follow), en Toggle, Pulse en offline. 210 tests: 207 groen, de 3 rode van hoofdstuk 14
      stap 2 (jouw stroomwaarden). Screenshots: `-Ponly=relays` (`rl01`-`rl07`). In de showcase: "Relay tags,
      invert and emitter" (en "Relay: name and group" heet nu "Relay: name and tags").
- [x] **4. Alleen de eigenaar** (13 oktober: "zorg dat de relay die je zelf plaatst, jij de enige bent die die
      kan bewerken"). Er zat een lek in: vroeger kon iedereen met een bril op jouw relais rechtsklikken, het
      zo in zijn eigen lijst zetten en het daarna van overal schakelen. Nu onthoudt een relais wie het neerzette
      (`RelayBlockEntity`), en alleen die kan het openen, in zijn lijst zetten, hernoemen, omkeren en schakelen
      (`ImtRedstone.otherOwner`). Een ander hoort "This relay belongs to ...". Stond jouw relais van vroeger
      nog in de lijst van een ander, dan gaat het eruit zodra hij het probeert te gebruiken, of bij het
      opruimen (elke 5 seconden, `pruneGhosts`). Een emitter schakelt nooit het relais van een ander, ook niet
      uit zijn oude kopie. **Mijn invulling**: een relais van vóór vandaag heeft nog geen eigenaar; dat wordt
      van wie het als eerste in zijn lijst heeft (bij het opruimen) of opent, dus in je eigen wereld van jou.
      Ook vrienden en creative kunnen andermans relais niet bewerken ("jij de enige"). Slopen kan nog wel
      (daarvoor zijn de zones). Een relais heeft nu een blok-entiteit, dus een zuiger duwt het niet meer weg
      (zoals een kist); zo kan niemand het verschuiven en zo de eigenaar kwijtraken. Een nieuwe test
      (`relay_only_owner_can_edit`), en de emitter-test zet de eigenaar nu echt offline. 211 tests: 208
      groen, de 3 rode van hoofdstuk 14 stap 2.

### 16. De laserzender instellen (13 oktober)

Jouw wens: "zorg dat je gewoon op ze kunt klikken zonder dat er iets gebeurt, dit zorgt ervoor dat het
makkelijker is om **cables te leggen**. Enkel de persoon die het heeft **geplaatst (of trusted players in
zone)** kunnen hier op **sneak klikken** en dan een **GUI** openen. In deze GUI kun je de **kleur** kiezen,
**signaal inverteren** zodat redstone aan laser uit is en kiezen of de laser **horizontaal of verticaal** is."

- [x] **1. Klikken doet niets, sneak + klik opent het scherm.** Gewoon rechtsklikken op een zender doet
      niets meer (vroeger wisselde de kleur): de klik gaat door naar wat je vasthoudt, dus met een kabelrol
      leg je gewoon een kabel tegen de zender aan. **Sluipen + rechtsklik met een lege hand** opent het
      scherm (`LaserEmitterBlock.useWithoutItem`). Met iets in je hand en sluipen gebruikt Minecraft zelf
      dat ding (zo werkt sluipen bij elk blok), dus het scherm opent alleen met een lege hand.
- [x] **2. Wie mag het openen** (`LaserEmitters.mayEdit`): wie de zender neerzette (de zender heeft nu een
      blok-entiteit die dat onthoudt, `LaserEmitterBlockEntity`), en wie vertrouwd is in de basis waar hij
      staat: de eigenaar van de beveiligingskern, zijn team en zijn vertrouwde spelers (Access). Een ander
      hoort "This laser emitter belongs to ...". **Mijn invulling**: "in de basis" is de zone van de kern
      én de rest van het gebied van de kern, net als bij de laserwand zelf (daar lopen die spelers al
      doorheen). Een zender van vóór vandaag heeft nog geen eigenaar: die wordt van wie hem als eerste
      opent, net als een oud relais. Behalve in iemands basis (dan alleen de vertrouwde spelers daar) en
      in een gebouw van deze mod (de Biebia's Industries Tower, de Biebia's Industries-bunker): anders kon een indringer de lasers van
      de bunker overnemen en uitzetten. Geen uitzondering voor creative. Een zuiger duwt een zender nu
      niet meer weg (zoals een kist), omdat hij een blok-entiteit heeft.
- [x] **3. Het scherm** (`client/shield/LaserEmitterScreen`, Engels in het spel): **Color** (zes vakjes,
      de gekozen kleur met een witte rand), **Signal** (*Normal*: redstone aan = laser aan; *Inverted*:
      redstone aan = laser uit, en zonder redstone staat hij aan) en **Wall** (*Vertical*: staand zoals
      altijd; *Horizontal*: plat als een vloertje). Kijkt de zender omhoog of omlaag, dan staat de wand
      altijd rechtop en kies je *east-west* of *north-south*. Alles gaat meteen naar de zender (geen
      opslaan), en de wand die er al staat kleurt en draait mee (`LaserWallBlock.TURNED`, de
      blockstate maakt `tools/generate_block_models.py`). Bovenaan staat van wie hij is en of hij aan
      staat, onderaan voor hoeveel zenders het geldt. **Mijn invulling**: wat je kiest geldt voor de
      **hele laserpoort** (alle zenders die eraan vastzitten, ze gaan ook samen aan en uit), anders ging
      een omgekeerde poort half aan en half uit. Daarom neemt een zender die je tegen een poort zet ook
      haar kleur, omkeren en liggend/staand over. Een omgekeerde zender staat aan zonder redstone, dus
      kost hij dan ook gewoon stroom.
- [x] **Tests en beelden.** Vier nieuwe tests (`LaserGameTests`): klikken doet niets (ook met een kabelrol),
      een vreemde mag niets en de eigenaar kleurt de hele poort, een nieuwe zender neemt de instellingen
      over; omgekeerd (aan zonder redstone, uit met) en liggend (een plat vloertje); vertrouwde spelers in
      een zone wel, een vreemde niet (ook niet een zender zonder eigenaar overnemen); en een oude zender
      wordt van wie hem als eerste opent. 216 tests: 213 groen, de 3 rode van hoofdstuk 14 stap 2.
      Onderweg werd `biebia_tower_has_powered_cameras` rood: met de nieuwe batches lopen de tests in een andere
      volgorde, en de toren van de kluistest stond er nu vlak naast. Zijn kern stond dichter bij de lobbycamera
      en pikte die in. De test ruimt zo'n kern van een andere test nu eerst op. Screenshots: `-Ponly=laser`
      (`73_laser_flat`, `74_laser_flat_angle`, `75_laser_screen`: een cyaan poort van drie hoog, omgekeerd
      en liggend). In de showcase: "Rayshield: laser settings".

### 17. Een keycardlezer of codepaneel hernoemen (13 oktober)

Jouw wens: "rename keycard reader door **sneak rechts te klikken zonder item in hand**, dit kan **enkel de
owner doen of trusted players in zone**".

- [x] **1. Sneak + klik opent het naamscherm.** Gewoon rechtsklikken op een lezer zegt nog steeds "Hold a
      keycard and right-click the reader." **Sluipen + rechtsklik met een lege hand** opent nu een klein
      scherm (`client/InputNameScreen`, Engels in het spel): bovenaan van wie hij is ("Placed by ...", of
      "Part of this base"), het vakje met de naam (helemaal geselecteerd: typen vervangt hem), en eronder
      hoe je hem in een diagram noemt: `when keycard("Lab door"):`, in de kleuren van de app Logic. Typ je
      een andere naam, dan zegt het scherm in het oranje "Change it in your diagrams too.": een diagram
      zoekt de lezer op zijn naam. **Save** of Enter bewaart, **Cancel** of Esc laat het zoals het was.
      Dezelfde regels als Rename in de app Devices: hooguit 24 tekens, geen aanhalingstekens, leeg mag
      niet. Boven je hotbar staat dan "Renamed to ...", en het logboek van de kern zegt wie welke lezer
      hernoemde (`InputDevices.handleRename`). Met een kaart of de Bypass Kit in je hand werkt sluipen
      zoals altijd: dan gebruik je dat ding (de kit begint gewoon zijn hack).
- [x] **2. Wie mag het** (`InputDevices.mayRename`): wie de lezer neerzette (een lezer onthoudt dat nu,
      `InputDeviceBlockEntity.owner`) en wie vertrouwd is in de basis waar hij hangt: de eigenaar van de
      beveiligingskern, zijn team en zijn vertrouwde spelers (Access). Een ander hoort "This keycard reader
      belongs to ...". Je moet binnen 6 blokken staan. **Mijn invulling**: "de owner" lees ik als wie hem
      neerzette; de eigenaar van de kern is in zijn eigen basis altijd vertrouwd, dus die mag het ook (zo
      klopt het allebei). "In zone" is, net als bij de laserzender, de zone én de rest van het gebied van
      de kern. Het is precies de regel van de laserzender, die nu op één plek staat (`security/BaseTrust`,
      de laserzender gebruikt hem ook). Een lezer van vóór vandaag heeft nog geen eigenaar: die wordt van
      wie er als eerste op sneak-klikt, behalve in iemands basis (dan alleen de vertrouwde spelers daar).
      Eerst alleen de keycardlezer, zoals je vroeg; het codepaneel kwam in stap 3. De netwerkversie is nu 19
      (twee nieuwe berichten).
- [x] **Tests en beelden.** Twee nieuwe tests (`LogicGameTests`): de eigenaar hernoemt (de naam wordt netjes,
      leeg en van ver weg niet), een vreemde niet, en een lezer zonder eigenaar wordt van wie er als eerste
      op sneak-klikt (gewoon klikken niet); en in een zone: een vertrouwde speler wel (met een regel in het
      logboek), een vreemde niet, ook niet een lezer zonder eigenaar overnemen. 218 tests: 215 groen, de 3
      rode van hoofdstuk 14 stap 2. Screenshots: `-Ponly=devrename` (`dr4_reader_name_screen`,
      `dr5_reader_name_typed`, `dr6_reader_renamed`). In de showcase: in "Server racks and Logic", na het
      codepaneel (het scherm met "Lab entrance" getypt, dan Esc, zodat het diagram blijft werken).
- [x] **3. Ook het codepaneel** (13 oktober: "zorg ook dat dit bij code panels werkt"). Sluipen + rechtsklik
      met een lege hand op een codepaneel opent hetzelfde naamscherm, met de titel "Code Panel" en
      `when codepanel("Front door"):` onder het vakje. Dezelfde regel voor wie mag (wie het neerzette, of
      vertrouwd in de basis), en een paneel onthoudt nu ook wie het neerzette. Gewoon klikken opent de
      toetsen, zoals altijd. **Mijn invulling**: mag je het paneel niet hernoemen, dan krijg je bij sneak +
      klik gewoon de toetsen (geen "belongs to ..." zoals bij de lezer): iedereen moet een code kunnen typen,
      ook als hij sluipt. De meldingen "Renamed to ..." en "The name cannot be empty." gelden nu voor allebei.
      Een nieuwe test (`panel_rename_by_sneak_click`): de eigenaar krijgt het naamscherm en hernoemt, een
      vreemde niet (en neemt niets over met sneak + klik), een paneel zonder eigenaar wordt van wie er als
      eerste op sneak-klikt (gewoon klikken niet). 219 tests: 216 groen, de 3 rode van hoofdstuk 14 stap 2.
      Screenshot: `dr7_panel_name_screen` (`-Ponly=devrename`).

### 18. Geen doorkijkje langs de warmtegenerator (14 oktober)

Jouw melding: "de heatgens geven **xray langs de blokken**".

- [x] **De oorzaak.** Sinds zijn 3D-model (`7c527b8`) is de warmtegenerator geen vol blok meer: tussen de
      sokkel en het deksel staat de kast een pixel naar binnen, en boven het deksel zit alleen het
      luchtrooster. Voor het spel was hij nog wel een dicht, vol blok, dus lieten de blokken ernaast (een
      muur, een plafond) hun kant naar de generator weg: die zou je toch niet zien. Door de kieren keek je
      zo dwars door de wereld. Ik heb de andere blokken van de mod nagelopen: alleen de generator had dit.
- [x] **De oplossing.** `.noOcclusion()` bij de generator in `ModBlocks`: de blokken ernaast tekenen hun kant
      nu gewoon, en in de kieren zie je steen (in de schaduw) in plaats van lucht. **Mijn invulling**: licht
      houdt hij tegen zoals vroeger, ook de bundel van een Arc Lamp (`HeatGeneratorBlock.getLightBlock`,
      net als getint glas). Zonder dat ging het licht er na deze fix ineens doorheen, en kon een generator
      in een muur licht naar de kamer ernaast laten lekken.
- [x] **Tests en beelden.** Een nieuwe test (`heat_generator_hides_no_neighbour_faces`): tekent de steen aan
      alle vier de kanten en erboven zijn kant naar de generator, en houdt hij nog licht tegen? Zonder de
      oplossing was hij rood. 220 tests: 217 groen, de 3 rode van hoofdstuk 14 stap 2. Screenshot:
      `98a_models_in_wall` (`-Ponly=models`): een generator ingemetseld in steen, van dichtbij. Vóór de
      oplossing zag je door de kier boven het deksel en langs de kast de lucht en het gras, nu een donkere
      naad.

### 19. Relaisgroepen: een boom in plaats van tags (14 oktober)

Jouw wens: "de tags mogen aangepast worden naar **groepen**, deze groepen configureer je **in de desktop
en/of in de relay**, dan kies je een groep waar die bij hoort, een groep is ook **optioneel**, hierdoor kun
je **groepen in groepen** doen", met als voorbeeld base > visitors (cafe, storage) en base > restricted
(security room, power room). En erachteraan: "zorg ook dat je een **tree van de groups** kunt zien, voor de
duidelijkheid **tags zijn volledig weg**, je kunt enkel relay namen in groepen steken en groepen in groepen
(zorg dat **base niet in temp** kan en temp in base waardoor je een **loop** krijgt)".

- [x] **1. Groepen in plaats van tags.** Een relais zit in hooguit één groep (`Relay.group`, leeg = geen
      groep), en een groep in hooguit één andere groep (`ImtRelays.groupParents`, leeg = bovenaan). Zo is
      het een echte boom. Een groep kan nooit (via een omweg) in zichzelf (`wouldLoop`): zit `temp` in
      `base`, dan weigert de server `base` in `temp` ("that would make a loop"), en in de schermen staat die
      keuze grijs (of rood bij slepen). Schakelen op een groep pakt alles eronder mee: je stem, de knoppen in
      het redstone-scherm, de relay emitter en de diagrammen op een server (`on("base")`). Tags bestaan
      niet meer (`RelayTagsScreen` is weg). Oude werelden: van een relais met tags wordt de eerste tag zijn
      groep, en van "tag in tags" de eerste tag erboven (een rondje daarin wordt gebroken).
- [x] **2. In het relais.** De knop **Group** in het scherm van een relais toont waar het in zit (`base ›
      restricted › security room`, of `No group`). Klik erop: het groepen-scherm opent als kiezer (bovenaan
      "Pick a group for 'Door 1'", links eerst **(No group)** en dan de boom). Kies een groep en klik
      **Choose**; **Save** bewaart naam en groep samen. In die kiezer kun je ook meteen groepen maken en
      verhuizen.
- [x] **3. Het groepen-scherm** (`RelayGroupsScreen`, de knop **Groups** onderaan het redstone-scherm, of via
      een relais). Links de boom van groepen. Een nieuwe groep (links onderaan, **+**) komt **in de groep die
      je gekozen hebt**, zodat je jouw voorbeeld zo intikt: kies `base`, typ `visitors`; kies `visitors`, typ
      `cafe`. Rechts: in welke groep de gekozen groep zit (een vinkje; **(top level)** = nergens in; wat een
      rondje zou maken staat grijs), **Rename** en **Delete group**.
- [x] **4. Op de desktop: de app Groups** (`GroupsApp`, icoon **G** op de Security OS-desktop). Dezelfde boom,
      met je relais erin (lampje groen = aan, rechts waar het staat) en per groep hoeveel apparaten hij
      schakelt. **Sleep** een relais of groep op een groep om hem erin te zetten (groen = mag, rood = rondje),
      of op de bovenste regel "Your relays (top level)" om hem uit elke groep te halen. Bovenaan **New group**
      (in de gekozen groep), **Rename** (een groep of een relais) en **Delete**.
- [x] **Mijn invulling** (niet gevraagd):
      - De app Groups toont altijd **jouw eigen** relais, ook aan de desktop van een vriend (een relais is van
        wie het neerzette, hoofdstuk 15 stap 4). Een indringer aan jouw desktop ziet dus zijn eigen relais,
        niet die van jou.
      - **Weggooien** van een groep gooit niets anders weg: wat erin zat (relais en groepen) schuift één laag
        omhoog. Gooi je `restricted` weg, dan zitten `security room` en `power room` direct in `base`, en
        blijft `base` alles schakelen.
      - Een groep kan een **andere naam** krijgen; de relais en groepen erin gaan mee. Een naam die al bestaat,
        mag niet. Let op: een emitter of diagram met de oude naam moet je zelf bijwerken (zoals bij een relais).
      - In de config: `relays.maxTags` is weg, `relays.maxGroups` (standaard 32 groepen per speler) is nieuw.
      - De groepen blijven van jou (bij je speler opgeslagen), niet van de desktop of de kern.
- [x] **Tests en beelden.** Twee tests vervangen de tag-tests (`RelayGameTests`): `relay_groups_nest_and_switch`
      (jouw boom, schakelen op `base`, één groep per relais en per groep, `base` in `temp` geweigerd, ook in
      zichzelf en via een omweg, hernoemen, weggooien schuift omhoog, het maximum) en
      `relay_old_tags_become_groups` (oude groep, oude tags, oude tags-in-tags met een rondje, opslaan en
      doorsturen). 220 tests: 217 groen, de 3 rode van hoofdstuk 14 stap 2. Screenshots `-Ponly=relays`: `rl03` (de boom), `rl04` en `rl041` (het
      groepen-scherm, met `base` gekozen: alles eronder grijs), `rl042` (de kiezer van een relais), `rl043`
      (het scherm van een relais met de knop Group) en `rl061` (de app Groups op de desktop). In de showcase:
      "Relay groups, invert and emitter" en "Relay: name and group" (eerst "... tags").

### 20. De taal groeit (14 oktober)

Hoofdstukken 20 tot en met 27 komen uit één verzoek van 14 oktober: guard robots, programmeerbare
security drones, training zoals Rookie squad, en een inbraak- of uitbraakoefening. Ze bouwen op elkaar:
eerst de taal, dan de robot, dan de upgrades, en pas daarna de training.

Jouw wens: een pin die je een naam geeft "zoals bij C++" (`scanPin = pinNumber(1)`), en een robot die
steeds opnieuw patrouilleert. Dat vraagt om variabelen, rekenen en lussen. Alle bestaande diagrammen
blijven werken.

**Mijn invulling**: `pin(1)` in de code (korter dan `pinNumber`); `=` maakt een naam (vergelijken blijft
`==`); lussen `forever:`, `repeat(n):`, `while ...:` en `break`; aanroepen met meerdere waarden, zoals
`goto(x, y - 1, -20)`. Een naam bovenaan het programma geldt voor alle whens; in een blok alleen voor die
ene run.

- [x] **1. Eén bron voor alle lijsten** — `Op.example()` zegt hoe je een blokje schrijft en waar de cursor
      dan komt (`on("|")`, `pulse("|", 3)`, `wait(2)`). Ctrl+Spatie (de whens, de triggers en de acties),
      het palet van de blokjes en de kleur van de gegevens in de editor lezen nu uit `Op` en
      `Expr.Variable`: een nieuw blokje staat er vanzelf in. **Mijn invulling**: de Help blijft met de hand
      geschreven, want die heeft echte voorbeelden (een hele `when keycard` met `accept()` en `deny()`); de
      controles per soort programma komen in stap 7, samen met die soorten. Geen gedragsverandering: 220
      tests, 217 groen (de 3 rode van hoofdstuk 14 stap 2).
- [x] **2. Rekenen** — in een `if` kun je nu rekenen: `if card.level * 2 - 1 >= 5:`. Keer en delen gaan voor
      (zoals op school: `2 + 3 * 4` is 14), haakjes, een minteken (`-20`, `-card.level`), en met `+` plak je
      tekst aan elkaar (`"Level " + card.level` wordt "Level 3"; een getal zonder overbodige nullen).
      Terugschrijven zet alleen de haakjes die nodig zijn (`((2 + 3) * 4)` wordt `(2 + 3) * 4`). Fouten voor
      een beginner: "You can only subtract numbers", "A - in front only works with a number, like -20". In
      de editor hebben `+ - * /` de kleur van de vergelijkingstekens, Ctrl+Spatie na een rekenteken toont de
      gegevens, en de Help heeft een regel over rekenen. **Mijn invulling**: delen door 0 geeft 0 (geen
      foutmelding midden in een diagram), en `true`/`false` mag je ook aan tekst plakken. Eén nieuwe test
      (`logic_arithmetic`).
- [x] **3. Variabelen** — `naam = waarde`. Helemaal links (`count = 0`) is het een naam voor het hele
      programma: hij houdt zijn waarde tussen de runs (zolang de server draait; opnieuw bewaren zet hem
      terug). In een when of def bestaat een naam vanaf de regel waar hij zijn waarde krijgt, en een def
      heeft zijn eigen namen (de namen van de when die hem gebruikt, ziet hij niet, zoals een functie in
      Python). Een naam houdt zijn soort: een getal kan geen tekst worden. Een nieuwe controle-ronde
      (`ScriptChecker`) loopt het hele diagram af en kijkt elke `if` en elke `naam = waarde` streng na,
      voor code én blokjes; de parser en de server lezen soepel. In de blokjes: een geel **SET**-blokje
      (in een rij) en **AT START** (een naam helemaal links, zonder draadjes), met een vak voor de naam en
      een vak voor de waarde. Ctrl+Spatie stelt `count = 0` voor en kent je eigen namen; de Help heeft een
      stuk NAMES. Fouten: "count has no value yet: give it one first, like count = 0", "count holds a
      number, so you cannot give it text", "card.level is information from I.M.T: you can read it,
      but not give it a value", "Did you mean is_night?". **Mijn invulling**: helemaal links mag je alleen
      getallen, tekst en andere namen van links gebruiken (geen `card.level`: er is dan nog geen kaart).
      Twee nieuwe tests (`logic_variables`, `logic_variables_run`: een timer telt tot 3, dan pas gaat de
      lamp aan). Commit: `eff9511`.
- [x] **4. Lussen en break** — `forever:`, `repeat(n):` (het aantal mag een naam of een berekening zijn),
      `while ...:` en `break` (meteen uit de binnenste lus, ook vanuit een if erin). In de blokjes een roze
      **FOREVER**-, **REPEAT**- of **WHILE**-blokje met de uitgang "do" rechtsboven (de stappen erin) en
      "after" onderaan, en een **BREAK**-blokje; code wordt blokjes en terug. De tolk onthoudt een lus op
      zijn stapel (met de rondjes die nog over zijn). Een lus zonder wachten laat de server niet hangen:
      elke run doet hooguit 64 blokjes per tick, de rest de volgende tick (een `forever` die tot 1000 telt,
      doet daar een paar seconden over). Ctrl+Spatie stelt `repeat(3):`, `while ...:`, `forever:` en `break`
      voor; de Help heeft een stuk LOOPS; de kleuren in de editor kennen de nieuwe woorden. Fouten: "break
      only works inside forever, repeat or while", "repeat needs a number, like repeat(3):", "while needs a
      check". **Mijn invulling**: een `forever` zonder `wait` houdt een stukje RAM bezet en laat je CPU's
      hard werken (warm datacenter): dat zegt de Help ook. Twee nieuwe tests (`logic_loops`,
      `logic_loops_run`). Commit: `e2621ff`.
- [x] **5. De tolk apart** (vóór stap 3 gedaan: variabelen hebben hem nodig) — het aflopen van de blokjes
      (if met yes, no en after, eigen blokken, `wait`, hooguit 64 blokjes per tick) zit nu in
      `logic/program/Interpreter`, zonder Minecraft erin. Wat een blokje echt doet, vraagt hij aan een
      `Host`: op de server is dat `LogicRunner.ServerHost` (relais, lampjes, I.M.T, het logboek), straks
      ook een robot, een drone en het Training End-blok. Geen gedragsverandering: 221 tests, 218 groen.
      Commit: `9685b84`.
- [x] **6. Meerdere waarden en berekeningen als argument** — wat tussen de haakjes staat, is nu een
      uitdrukking. Een bericht (`say`, `log`, `alarm`) en het aantal seconden (`wait`, `pulse`) mogen een
      berekening zijn: `say("Count: " + count)`, `wait(delay * 2)`, `pulse("Lamp", delay + 0.5)`. Een gewone
      tekst of een gewoon getal blijft bewaard zoals vroeger (oude diagrammen blijven precies hetzelfde);
      alleen een echte berekening komt in het nieuwe veld `expr` van het blokje. In de blokjes staat zo'n
      berekening in een code-vak ("Message (code)" of "Seconds (code)"). De Help heeft er een regel over.
      **Mijn invulling**: een apparaat blijft gewone tekst tussen aanhalingstekens (zo kan de editor je
      apparaten blijven voorstellen en nakijken), een trigger neemt alleen gewone tekst en getallen
      (`every(10)`), berekende seconden houden we binnen de grenzen (`wait` 0,05 s tot 10 minuten, `pulse`
      tot 60 s), en een berekend bericht is hooguit 200 tekens. `say(Hello)` zonder aanhalingstekens zegt
      "Hello has no value yet... If you meant text, put it between quotes: say("Hello")". De robot-acties met
      meerdere waarden (`goto(x, y, z)`, `write(pin, waarde)`) gebruiken straks hetzelfde. Twee nieuwe tests
      (`logic_values_in_actions`, `logic_values_run`). Commit: `8bdfe7b`.
- [x] **7. Soorten programma's** — *verschoven naar hoofdstuk 23*: een soort "Robot" of "Drone" doet pas iets
      als er robots zijn. Gedaan als stap 1 van hoofdstuk 23 (een "Drone" komt in hoofdstuk 25).
- [x] **8. Oude programma's** — een def met een naam die nu een woord van de taal is (`repeat`, `while`,
      `forever`, `break`, `set` of `var`), krijgt bij het laden een andere naam (`repeat_1`, of `_2` als die
      al bestaat); de blokjes die hem gebruiken gaan mee, en de bewaarde code wordt opnieuw uit de blokjes
      geschreven (je commentaar in dat ene diagram valt dan weg). Ook een backup die je terugzet, gaat zo.
      Eén nieuwe test (`logic_old_programs`). Commit: `dfa8b87`.

Tests: taaltests zonder wereld (rekenen, haakjes, typefouten, `break` buiten een lus, een variabele zonder
waarde, code en blokjes met lussen), en runner-tests (`repeat(3)` pulst drie keer, een oneindige lus legt
de server niet plat, een oud diagram laadt nog).

- [x] **Afronding** — 228 tests: 225 groen, de 3 rode van hoofdstuk 14 stap 2 (jouw stroomwaarden). Screenshots
      `-Ponly=logic` (`l12_logic_names_loops`: de code met namen, rekenen en een lus; `l13`: dezelfde code als
      blokjes met AT START en SET; `l14`: het REPEAT-blokje gekozen, met "do" en "after"; `l15`: het SET-blokje
      gekozen, met de vakken Name en Value). Op de screenshots zag ik dat het palet rechts te lang was geworden
      (`break` viel eraf): het palet **scrolt** nu met het muiswiel (met ▲/▼ als er meer is), en het
      AT START-blokje heet in het palet "at start" in plaats van "var". In de showcase de nieuwe functie
      **"Logic: names, sums and loops"** na "Server racks and Logic" (het lab met een tweede diagram "Blink":
      code 4321, I.M.T telt mee, de deur gaat drie keer open, en dan de code en de blokjes). LEESMIJ.md,
      "De server": rekenen, namen en lussen, met voorbeelden.

### 21. De guard robot bouwen (14 oktober)

Jouw wens: "robots die ik via de security core programmeer, met een laadstation en een batterij". Als ze uit
staan, staan ze in hun laadblok. Je kunt er upgrades op zetten, en met een camera kijk je vanaf de core door
hun ogen. Dit hoofdstuk bouwt de robot zelf; de upgrades komen in 22 en 24.

Jouw antwoorden: de robot heeft **zijn eigen code** en praat met de server. Het uiterlijk stel je zelf samen
uit **hoofd, body, armen en benen**, elk in een van vier stijlen die op Star Wars lijken: commando droid,
trainingsdroid van Kamino, battle droid en super battle droid.

**Mijn invulling**: de namen in het spel zijn Commando, Trainer, Battle en Super Battle (zonder "droid", dat
is een merk). Het ontwerp is van ons, in die sfeer. Samenstellen gebeurt in een nieuw blok, de **Robot
Assembler**, met een draaiend 3D-voorbeeld. De onderdelen verschillen een beetje: benen = snelheid, body =
leven en batterij, armen = schieten, hoofd = kijkhoek. De robot hoort bij de core in wiens gebied hij staat.
Een robot opent een deur door een relais aan te sturen (jouw wens). Aan = hij stapt uit zijn laadblok; uit =
hij loopt terug en laadt.

- [x] **1. Config en de robot** — `[robots]` in `ImtConfig` (7 lijstjes met elk 4 getallen, één per stijl:
      leven, pantser en batterij van het lijf, snelheid van de benen, schiettempo van de armen, kijkafstand en
      kijkhoek van het hoofd), `RobotStyle`, `RobotSlot` en `RobotParts` (de vier onderdelen in één getal),
      `GuardRobotEntity` (geen Monster, zodat je eigen torentjes hem met rust laten; verdwijnt nooit vanzelf;
      stapt een blok op zonder te springen; verdrinkt niet; geen touw). Zijn onderdelen zet de server in zijn
      leven, pantser en snelheid, ook na het laden (dan geldt de config van nu). Wie hem neerzet is eigenaar
      (`BaseTrust.Placed`, dezelfde regel als de laserzender). Een spawn-ei (creative) geeft willekeurige
      onderdelen. Zonder programma staat hij stil en kijkt hij rond. Toestand en batterij komen in stap 6.
      Nieuwe testklasse `RobotGameTests` (4 tests).
- [x] **2. Onderdelen** — 16 onderdelen als item (`commando_head` tot `super_battle_legs`, "Commando Head" tot
      "Super Battle Legs"), elk met een icoontje (`robot_part_items()` in `tools/generate_textures.py`) en een
      recept: ijzer plus de stof van de stijl (Commando grijze kleurstof, Trainer witte, Battle koper, Super
      Battle een ijzerblok: het zwaarst); een hoofd heeft een Computer Chip en een glasruit, een lijf een
      redstoneblok, armen en benen zuigers. Als je erover beweegt zegt het item wat het doet, met de getallen
      uit de config ("Body: 44 health, 10 armor, battery 8000"). Gaat een robot echt kapot, dan blijft elk
      onderdeel met een kans heel (`partSurviveChance`, standaard 0,5) en valt het op de grond. Twee nieuwe
      tests (alle items met recept; kapot = onderdelen bij kans 1, niets bij kans 0). Vorige stap: `7aa87b6`.
- [x] **3. Het model** — *samen met stap 1 gedaan* (anders kon je de robot niet zien). `client/robot/RobotModel`:
      de botten van een mens (dus lopen en armzwaaien gratis), en aan elk bot vier versies, één per stijl;
      per robot staat alleen de gekozen stijl aan. De armen hangen aan de schouders van het gekozen lijf
      (een Super Battle-lijf is veel breder), het hoofd op zijn nek (bij de Super Battle half in de borst),
      de benen bepalen zelf hun breedte. *Battle*: mager en zandkleurig, smalle schedel met een lange snuit,
      rugzak, dunne ruggengraat. *Super Battle*: donker blauwgrijs, enorme borst met een bult, klein hoofd met
      kijksleuf, dikke onderarmen met een blaster in de pols. *Commando*: donkergrijs gepantserd, rond hoofd
      met één oranjerood oog. *Trainer*: wit en rond, lichtblauw vizier, borstlampje, polsringen en
      knielichtjes. Eén textuur van 128 x 128 (`robot_textures()` in `tools/generate_textures.py`): per stijl
      vier "verven" (hoofdkleur, donker, lichtjes, details) die de blokjes delen, plus een gloeilaag.
      Screenshots `-Ponly=robots` (`rb01`-`rb06`: de vier stijlen, van dichtbij, opzij, twee gemengde van
      achter en voor, en 's nachts met de lichtjes).
- [x] **4. Printplaat** — de **Robot Circuit Board** (`CircuitBoardItem`, recept: koper, redstone, goudklompjes
      en een Computer Chip; icoon: een groene printplaat met 8 gouden pinnen). Wat erop zit staat in het item
      (`BotBoard` in de nieuwe map `bot/`, die robots en later drones delen): 8 pinnen met elk de naam van
      een module of leeg, en de soldeerbruggen tussen naburige pinnen (voor hoofdstuk 22). Een nieuwe
      printplaat is leeg. Eén nieuwe test (8 pinnen, bewaren en laden, recept). Vorige stap: `ca743d5`.
- [x] **5. Robot Assembler** — een stalen werkbank (recept: ijzer, Computer Chip, zuigers en een werkbank; voorop
      een scherm met een cyaan robot, bovenop een werkblad met klemmen). Zijn scherm: links vier vakjes
      (hoofd, lijf, armen, benen; elk neemt alleen zijn eigen soort), in het midden een **draaiend
      3D-voorbeeld** dat meegroeit (wat nog niet ligt, zie je niet), rechts de printplaat en het robotvakje,
      de knoppen **Assemble** en **Take apart**, en eronder wat er nog ontbreekt ("Missing: arms, legs,
      board."). Assemble maakt een **Guard Robot**-item (`GuardRobotItem`, met `RobotData`: onderdelen,
      printplaat, naam); Take apart kan als de andere vakjes leeg zijn en geeft alles terug, ook de
      printplaat met wat erop zit. Een Guard Robot-item op een blok zet de robot erop, met zijn gezicht naar
      jou; jij bent zijn eigenaar. De knoppen gaan als "knop van een menu" naar de server (zoals de
      betafel), geen nieuw netwerkbericht. Twee nieuwe tests (bouwen en uit elkaar halen; neerzetten).
      Screenshots `rb07`-`rb10`. Vorige stap: `4b85ae0`.
- [x] **6. Robot Dock en batterij** — het **Robot Dock** (`RobotDockBlock`): een lage laadplaat met een geel-zwarte
      rand en een cyaan ring, achteraan een paal met een stekker en een cyaan lampje (recept: een kabel, ijzer
      en een redstoneblok). Zet je een Guard Robot-item op een vrij dock, dan is het zijn thuis (het dock
      onthoudt zijn robot; is het dock weg, dan vergeet de robot het). De robot heeft een **batterij** (zo
      groot als zijn lijf zegt); op zijn dock laadt hij via het stroomnet (`dockChargeRate`, 5 per tick),
      aan gebruikt hij `drainOn` (0,25 per tick) en lopend `drainWalking` (0,5 extra). Toestanden
      (`RobotState`): **Active**, **Standby** (uit) en **No power** (leeg: hij staat stil); je spel krijgt ze
      mee en zijn lichtjes gloeien alleen als hij aan staat. **Aan** in zijn dock: hij stapt `stepOut` (2)
      blokken naar voren. **Uit**: hij loopt naar het blok vóór zijn dock, het laatste stukje rechtdoor erin,
      gaat precies in het midden staan met zijn gezicht naar voren, en daar duw je hem niet weg. Een nieuwe
      robot staat uit; uit een spawn-ei staat hij aan. De batterij gaat mee in het item als je hem oppakt.
      *Wat ik vond*: de gewone padzoeker van Minecraft vindt geen weg van of naar een plek op de lage plaat
      (het pad bleef bij zijn eerste stap); daarom de laatste meter rechtdoor. Drie nieuwe tests (laden met
      en zonder stroom; aan = eruit, uit = terug in het midden; batterij leeg = stil). Screenshot `rb11`.
      Vorige stap: `ed39e6e`.
- [x] **7. Robotscherm en opslag** — **`BotNetwork`** (in `bot/`, bewaard in de wereld als
      `imttech_bots`): elke robot met naam, eigenaar, laatste plek, dock, onderdelen, toestand, batterij en
      de kern in wiens gebied hij staat. Een robot meldt zich om de seconde; kapot of opgepakt gaat hij eruit
      (een stuk wereld dat niet geladen is, telt niet). Elke robot krijgt een **naam** die bij zijn eigenaar
      nog niet bestaat ("Guard 1", "Guard 2"...): in je code noem je hem straks zo, dus een naam mag maar
      één keer per eigenaar (hoofdletters tellen niet). Het **robotscherm** (sneak + rechtsklik met een lege
      hand; wie hem neerzette of vertrouwd is in de basis, zoals bij de laserzender, anders "This robot
      belongs to ..."): naam en toestand, eigenaar, de vier onderdelen, een batterijbalk, zijn dock,
      **Rename**, **Switch on/off**, **Pick up** (hij wordt weer een item met alles erop, zijn dock is weer
      vrij). Nieuwe berichten `RobotOpenPayload` en `RobotActionPayload`; netwerkversie 19 → 20. Twee nieuwe
      tests (namen, opslaan en laden, oppakken, kapot; een vreemde kan niets, een dubbele naam mag niet).
      Screenshot `rb12`. Vorige stap: `9b7896e`.
- [x] **8. App Robots** — een nieuwe app op de Security OS-desktop (icoon "R", na Groups): de robots in het gebied
      van de kern (uit `BotNetwork`), elk met naam, plek, toestand, batterij en of hij een dock heeft, en de
      knoppen **Rename** (Enter bewaart, Esc niet) en **Switch on/off**. Een robot in een stuk wereld dat
      niet geladen is, staat er grijs bij ("far away") zonder knoppen. Werkt ook op afstand (SSH), zoals
      Devices. De desktop bedient alleen robots in het gebied van zijn eigen kern. "Verbinding" komt in
      hoofdstuk 23 (dan pas praat een robot met de server). Eén nieuwe test (de lijst; aanzetten en
      hernoemen van de desktop; een rare id doet niets). Screenshot `rb13`. Vorige stap: `1f9c408`.
- [x] **9. Geluid, showcase, LEESMIJ** — vijf nieuwe geluiden uit `tools/generate_sounds.py`: een **stap** (een
      servo-ritje en een metalen tikje, in plaats van de zware ijzergolem), **opstarten** (de servo's lopen
      op, twee piepjes), **uitschakelen** (de zoem zakt weg, een doffe klonk; ook als de batterij leeg is,
      en als hij kapot gaat), **geraakt** (een metalen klang met vonkjes) en het **laadzoemen** op het dock:
      een rustige lus, heel stil (0,08), alleen in je spel en alleen als hij echt laadt, met een eigen
      volumeknop `robotHumVolume` in `imttech-client.toml` (zoals de rayshields). In de showcase de nieuwe
      functie **"Guard robots"** (nummer 82, na "Hacking and pickpocketing"): de assembler, een gemengde
      robot op een dock, een halflege Commando die laadt, aanzetten, het robotscherm, uitzetten, de app
      Robots. LEESMIJ.md: een nieuw stuk "Guard robots: bouwen, laden, aan en uit". Vorige stap: `0f75652`.

Tests: samenstellen en uit elkaar halen; een robot op een dock laadt via de kabel en niet zonder stroom;
aan = eruit, uit = terug; een vreemde kan hem niet bewerken; opslaan en laden klopt.

- [x] **Afronding** — 243 tests: 240 groen, de 3 rode van hoofdstuk 14 stap 2 (jouw stroomwaarden); 15 nieuwe in
      `RobotGameTests`. Screenshots `-Ponly=robots` (`rb01`-`rb13`: de vier stijlen, gemengd, 's nachts, de
      assembler half en vol, het blok, de docks, het robotscherm, de app Robots), de showcase-functie 82
      afgespeeld (screenshot: het robotscherm van Guard 1). Het jar gebouwd.

### 22. De printplaat: solderen (14 oktober)

Jouw antwoorden: je soldeert de upgrades op de **pinnen** van de printplaat, met een **soldeerbout** en
vasthouden. Het is een **klein spelletje**: je kunt per ongeluk een **kortsluiting** maken (een soldeerbrug
naar de pin ernaast). Zet je de robot niet aan, dan is dat geen probleem: je kunt lossolderen en opnieuw
proberen. Zag je het niet en zet je hem wel aan, dan kunnen de robot of de onderdelen afbranden.

**Mijn invulling**: acht modules (Scan, Distance, Face Recognition, Pathfinding, Laser, Gun, Solar Panel,
Camera). Het spelletje: een hittebalk; loslaten in het groene vak = goede verbinding; te vroeg = niets
(opnieuw); te laat = een brug. Een brug zie je alleen als je goed kijkt, of met de knop Inspect. De kans op
brand staat in de config.

- [x] **1. Modules** — `BotModule` (in `bot/`) en negen items: **Scan**, **Distance**, **Face Recognition**,
      **Pathfinding**, **Laser**, **Gun**, **Solar Panel**, **Camera** en de **Burnt Module** (wat overblijft na
      een kortsluiting; geen recept). Elk recept: een Computer Chip, twee redstone en iets dat bij de module past
      (een glasruit, een observer, een verrekijker, een kompas, een diamant met een blazestaaf, een dispenser
      met ijzer, een daglichtsensor, getint glas). Icoontjes: een groen printplaatje met twee gouden pootjes
      en een tekentje (`robot_module_items()`). Tooltip: wat hij doet en "Solder it onto a pin...". Eén
      nieuwe test. Vorige commit: `bff8e91` (einde hoofdstuk 21).
- [x] **2. Het bord** — `BotBoard` kan nu solderen (`with`), bruggen leggen (`withBridge`), lossolderen (de module
      eraf en de bruggen aan die pin weg, ook aan een lege pin) en zegt welke bruggen kortsluiting geven
      (`shorts()`: een brug tussen twee lege pinnen doet niets). De robot stuurt zijn bord mee naar je spel
      (voor het bordscherm en het model). **`Soldering`** beslist op de server hoe het afloopt, uit hoe lang je
      vasthield: onder `solderCold` (0,45 van de hittebalk) pakt het niet (de module blijft bij jou), tot
      `solderHot` (0,75) is het goed (kans op een brug `bridgeChanceGood` 0,03), langer geeft meestal een brug
      naar een buurpin (`bridgeChanceHot` 0,7) — en dat zeggen we je niet. De **Soldering Iron** (recept: ijzer,
      blazepoeder, een stok; slijt, 128 keer) hoeft alleen in je rugzak. Alleen als je de robot mag instellen
      en hij uit staat. Nieuw bericht `SolderPayload`; netwerkversie 20 → 21. Twee nieuwe tests (solderen,
      te kort, te lang, lossolderen, aan = geweigerd, geen bout, een vreemde; bruggen op het bord). Vorige
      stap: `52594c8`.
- [x] **3. Het spelletje** — het **bordscherm** (`client/robot/BoardScreen`, knop **Board** in het robotscherm):
      een groen bord met 8 genummerde gouden pinnen (met het icoon van de module die erop zit), onderaan je
      modules uit je rugzak, en hoeveel je soldeerbout nog kan. Sleep een module op een lege pin (of klik hem
      aan en druk op een pin) en houd vast: boven de pin loopt een **hittebalk** op, met het groene vak
      (oranje = te koud, groen = goed, rood = te heet). Loslaten stuurt hoe lang je vasthield naar de server.
      Een brug is zonder Inspect maar een dun groenig-zilver streepje tussen twee pinnen; met **Inspect**
      een roze balk, en bovenaan "switching on now would short!" (of dat hij tussen lege pinnen niets doet).
      Rechtsklik op een pin = lossolderen. Staat de robot aan of heb je geen bout, dan zegt het scherm dat.
      Screenshots `rb14` (hittebalk) en `rb15` (Inspect). Vorige stap: `a37fe66`.
- [x] **4. Aanzetten met een brug** — `GuardRobotEntity.shortCircuit`: zet je een robot aan met een soldeerbrug aan
      een module, dan blijft hij uit; vonken, rook, een knal; de modules op die pinnen worden **Burnt Modules**
      (lossolderen geeft je de verbrande module) en de brug is weggebrand. Met de kans `burnChance` (0,25)
      brandt de hele robot af (echt kapot: zijn onderdelen vallen met hun kans). De eigenaar krijgt een rode
      I.M.T-melding ("Short circuit in Guard 1: pin 3 burnt out"), het logboek van de kern een regel, en wie
      hem aanzette (robotscherm of app Robots) hoort "Short circuit!". Een brug tussen twee lege pinnen doet
      niets; daarna gaat hij gewoon aan. Eén nieuwe test. Vorige stap: `4f1b9ec`.
- [x] **5. Zichtbaar op de robot** — wat er op het bord zit, zie je op de robot (in elke stijl op een eigen
      plek): een paaltje met een schoteltje op zijn hoofd (Scan), een camerakastje rechts opzij met een lens
      (Camera), een cyaan lensstrook bovenaan zijn gezicht (Face Recognition), een sensortje met een rood lampje
      op zijn borst (Distance), een sprietantenne met een rood lampje (Pathfinding) en een zonnepaneel op zijn
      rug, een laser met een rode punt aan zijn rechterarm en een geweerloop aan zijn linker. De lenzen en rode
      lampjes gloeien als hij aan staat. De textuur van de robot is daarvoor 128 x 160 geworden (onderaan de
      verven van de modules). Screenshots `rb16` (van voren) en `rb17` (van achteren). Vorige stap: `f27e90c`.

Tests: goed solderen geeft een module; te laat geeft een brug; aanzetten met een brug verbrandt de modules;
zonder brug gebeurt er niets; lossolderen geeft de module terug; solderen aan een robot die aan staat wordt
geweigerd.

- [x] **Afronding** — 247 tests: 244 groen, de 3 rode van hoofdstuk 14 stap 2. Vier nieuwe in hoofdstuk 22
      (modules, solderen en lossolderen, bruggen op het bord, kortsluiting). In de showcase de nieuwe functie
      **"Soldering modules"** (83, na "Guard robots"): het bordscherm met de hittebalk, Inspect met de
      verstopte brug, aanzetten = kortsluiting, het bord daarna, en de vier stijlen met alle modules.
      LEESMIJ.md: een nieuw stuk "Modules solderen". Het jar gebouwd.

### 23. Robots programmeren en praten met de server (14 oktober)

Jouw antwoord: de robot heeft **zijn eigen code**, maar **praat met de server**. Een scan laat hij door de
server nakijken ("is dit oké?"), hij stuurt de server opdrachten (zoals een alarm), en de server geeft hem
opdrachten (zoals "val punt x aan").

**Mijn invulling**: een robotprogramma maak je in de app Logic (soort Robot), in code of in blokjes. De core
stuurt het via de verbinding naar de robot, die zijn eigen kopie bewaart.
- Bewegen: `forward(n)`, `back(n)`, `turn_left()`, `turn_right()`, `wait(s)`, `stop()`; `robot.battery`;
  `charge()` loopt zelf naar het laadstation; `goto(x, y, z)` alleen met de Pathfinding-module.
- Robot naar server: `alarm`, `say`, `log`, `on/off/toggle/pulse("Door 1")`, `send("intruder")`.
- Server naar robot: `order("Guard 1", "attack", x, y, z)`, `activate` en `deactivate`. In de robot:
  `when started:` en `when ordered("attack"):`.

- [x] **1. Soorten programma's** (stap 7 van hoofdstuk 20) — elk programma heeft nu een soort
      (`ProgramKind`): een **Server diagram** (zoals altijd) of een **Robot program**. Bij **+** in de app
      Logic kies je welke (screenshot `l16`); een robotprogramma heet "Robot 1", "Robot 2"... en krijgt in de
      lijst een geel **R**. Een robot kent geen `keycard`, `codepanel`, `turned_on/off`, `accept`, `deny` en
      geen `card.*`, `code` of `player.*` (die horen bij een lezer of paneel van je server): de code zegt dan
      "Line 1: keycard(...) only works in a server diagram, not in a robot program", en het palet van de
      blokjes, Help en Ctrl+Spatie laten ze bij een robotprogramma gewoon weg (screenshot `l17`). De soort
      wordt bewaard (NBT `Kind`; een oud diagram is een diagram op de server). Een robotprogramma staat op je
      SSD's (het telt mee), maar de server voert het nooit uit, en de knop On/Off heeft het niet. Twee nieuwe
      tests (`logic_program_kinds`, `logic_robot_programs_do_not_run_on_the_server`).
- [x] **2. De woorden van een robot** — de taal kent ze nu (de robot voert ze uit vanaf stap 3). In een
      robotprogramma: `when started:` (zonder haakjes) en `when ordered("attack"):` (met -ed, want `order`
      is het woord van de server); `forward(3)` en `back(1)` (1 tot 64 blokken), `turn_left()`,
      `turn_right()`, `goto(x, y, z)`, `charge()` of `charge(80)`, `stop()`, `write(laserPin, true)` en
      `send("intruder")`; als waarde `pin(1)` (gewoon het getal, zodat je een pin een naam kunt geven:
      `scanPin = pin(1)`, 1 tot 8) en `read(scanPin)`; de gegevens `robot.battery`, `robot.x/y/z`,
      `robot.facing`, `robot.name`, `robot.blocked`, `robot.connected`, en na `when ordered` ook
      `order.name` en `order.x/y/z`. In een serverdiagram: `order("Guard 1", "attack")` of met een plek erbij
      (`order("Guard 1", "attack", x, y, z)`), `activate("Guard 1")`, `deactivate("Guard 1")`, en
      `when message("intruder"):` met `sender.name` en `sender.x/y/z`. Een aanroep met meerdere waarden
      (`goto`, `write`, `order`) bewaart ze als een rijtje code; in de blokjes is dat één vak "x, y, z"
      (screenshot `l19`). Elk woord werkt alleen in zijn eigen soort ("forward() only works in a robot
      program, not in a server diagram"), een tikfout raadt ("Did you mean robot.battery?"), Ctrl+Spatie en
      Help kennen alles per soort (screenshot `l18`), en een oude def die nu `forward`, `stop`, `send`,
      `read`... heet, krijgt bij het laden een andere naam. Twee nieuwe tests (`logic_robot_words`,
      `logic_robot_values`).
- [x] **3. Het brein van de robot** — een robot voert zijn programma nu zelf uit (`bot/BotBrain`, met
      dezelfde tolk als de server). Aan, met stroom en een programma: elke `when started:` begint (stapt hij net
      uit zijn dock, dan pas als hij eruit is), en `every(...)` gaat af, ook terwijl een andere when nog bezig
      is. Elke when doet hooguit 32 blokjes per tick (`STEPS_PER_TICK`), dus `forever:` zonder `wait` laat de
      wereld niet hangen; hooguit 8 whens tegelijk (`MAX_RUNS`). Uit, een lege batterij of een nieuw programma:
      alles stopt, en de volgende keer begint hij opnieuw bij `when started:` met verse namen. Zijn lijf
      (`bot/BotMove`) doet één beweging tegelijk (wie het eerst vroeg, is eerst): `forward(n)`/`back(n)` blok
      voor blok, precies van midden naar midden, één blok op of af; tegen een muur, boven een gat, of als iets
      hem 2 seconden tegenhoudt (`STUCK_SECONDS`) is `robot.blocked` waar en gaat hij terug naar het midden van
      zijn blok. Achteruit blijft hij vooruit kijken; `turn_left()`/`turn_right()` is een kwartslag in 0,25 s.
      `goto(x, y, z)` loopt met de padzoeker (alleen met de Pathfinding-module, anders `robot.blocked` en een
      regel in het logboek), `charge(60)` loopt naar zijn dock, laadt tot 60%, stapt eruit en loopt terug naar
      waar hij was (geen stroom op het dock: na 10 s geeft hij het op). `stop()` zet hem uit. Met een programma
      kijkt hij recht voor zich uit (niet meer rond). Zijn programma en zijn windrichting worden bewaard. Nieuwe
      config: `STEPS_PER_TICK`, `MAX_RUNS`, `WALK_FACTOR` (0,45 x de snelheid van zijn benen = 2,25 blokken
      per seconde), `STUCK_SECONDS`, `TURN_SECONDS`, `GOTO_SECONDS`. Onderweg gevonden: de padzoeker van
      Minecraft vindt "een blok ernaast" al aangekomen, waardoor een robot die precies een blok vóór het
      blok voor zijn dock stond, niet meer thuiskwam (nu loopt hij dat stukje zelf). Zeven nieuwe tests
      (`BotProgramGameTests`: vierkantje, muur, goto met en zonder module, eindeloze lus, laden en terug,
      stop, opnieuw opstarten).
- [x] **4. Programma's naar de robot** — in de app Robots heeft elke robot nu een derde regel: welk programma
      hij draait, en de knop **Program** met de robotprogramma's van deze server en "(no program)" (screenshots
      `rb13`, `rb18`). De kern stuurt een kopie via de verbinding (`bot/BotLink`: de robot staat in het gebied van
      de kern, zijn eigenaar is daar de eigenaar of vertrouwd, de kern heeft stroom en de server draait); de robot
      begint dan opnieuw bij `when started:`. Een serverdiagram kan niet ("... is a server diagram, not a robot
      program"), zonder verbinding ook niet ("... has no connection"). De robot houdt zijn eigen kopie: verander
      je het programma op de server, dan zegt de app "(older than on the server)" tot je hem opnieuw stuurt, en
      opgepakt zit het programma in zijn item. Een robot die ver weg is (niet geladen), krijgt het als hij terug is
      en verbinding heeft (het wacht in `BotNetwork`; de app zegt "waiting: Patrol"). `robot.connected` werkt nu
      ook. Onderweg gevonden: sinds hoofdstuk 22 stond de camera van de screenshots te ver van de kern, waardoor
      `rb13` de wereld liet zien in plaats van de app (de desktop opent alleen vlakbij); hersteld. Eén nieuwe
      test (`robot_program_upload_from_the_desktop`).
- [x] **5. Robot naar server** — met verbinding gaat wat een robot zegt via zijn server: `say(...)` (I.M.T
      vertelt het de eigenaar), `log(...)` en `alarm(...)` komen in het logboek met zijn naam ervoor ("Scout:
      Intruder!"), `on`/`off`/`toggle`/`pulse("Door")` schakelen een apparaat van de basis, `is_on("Door")` vraagt
      het aan de server, en `send("intruder")` laat elke `when message("intruder"):` op de server beginnen, met
      `sender.name` en `sender.x/y/z` (waar de robot stond). Zonder verbinding gebeurt dat gewoon niet, maar loopt
      zijn programma door. Eén nieuwe test (`robot_talks_to_the_server`).
- [x] **6. Server naar robot** — in een serverdiagram: `order("Guard 1", "attack")` of met een plek erbij
      (`order("Guard 1", "attack", x, y, z)`), `activate("Guard 1")` en `deactivate("Guard 1")` (de naam van een
      robot in het gebied van de kern; hoofdletters tellen niet). Een opdracht onderbreekt wat hij doet: elke
      `when ordered("attack"):` begint (met `order.name` en `order.x/y/z`), zijn andere whens wachten zolang, en
      daarna gaan ze verder waar ze waren (een half gelopen `forward(5)` loopt de rest nog; een draai is meteen
      af). Een nieuwe opdracht vervangt de vorige. Heeft hij geen `when ordered` met die naam, staat hij uit of
      heeft hij geen programma, dan negeert hij hem en staat dat in het logboek ("Scout: it has no when
      ordered("dance"), so it ignored that order"); een robot die niet bestaat ook ("there is no robot called
      ..."). Een robot die niet geladen is, krijgt zijn aan/uit en zijn laatste opdracht als hij terug is en
      verbinding heeft (ze wachten in `BotNetwork`). Ook: `charge(80)` als hij al vol genoeg is, loopt niet meer
      naar zijn dock. Twee nieuwe tests (`robot_obeys_orders_from_the_server`, `robot_orders_wait_for_a_far_robot`).
- [x] **7. De app Logic** — de Help van een robotprogramma heeft nu **YOUR ROBOTS**: elke robot in het gebied
      van de kern met wat er op zijn pinnen zit ("Guard 7: 1 scan, 3 laser, 6 camera", of "8 modules" als het
      niet past); klik erop en de regels `scanPin = pin(1)`, `laserPin = pin(3)`... staan in je code (een
      robot ver weg: "far away"). Daaronder **EXAMPLES**: "Patrol a square" en "Come when called"; in een
      serverdiagram de namen van je robots en het voorbeeld "Call a robot". Ctrl+Spatie stelt de namen van je
      robots voor tussen de aanhalingstekens van `order(`, `activate(` en `deactivate(`. Screenshot `rb19`. Eén
      nieuwe test (`logic_robot_completion`). In de showcase de nieuwe functie **"Robot programs"** (84, na
      "Soldering modules"); LEESMIJ.md: een nieuw stuk "Robots programmeren".

Tests: vierkantje lopen (vier keer `turn_right()` na `forward(2)` komt op de plek waar hij begon); batterij
laden en terug; een opdracht onderbreekt het programma en het hervat; zonder verbinding geen alarm maar wel
lopen; een relais via `pulse`; een wachtende opdracht bij laden.

- [x] **Afronding** — 263 tests: 260 groen, de 3 rode van hoofdstuk 14 stap 2. Zestien nieuwe in hoofdstuk 23
      (soorten programma's, de woorden van een robot, ctrl+spatie, `BotProgramGameTests` met lopen, laden,
      uploaden, praten met de server en opdrachten). De showcase-functie **"Robot programs"** (84) heb ik
      helemaal laten lopen: het programma kwam via de app Robots op de robot, hij liep zijn vierkantje, en de
      server zei drie keer "Guard 1 walked a full round" (gecontroleerd in het logboek van de kern). Het jar
      gebouwd.

### 24. Zien en schieten (14 oktober)

Jouw wens: scan geeft true of false ("zoek naar player"); gezichtsherkenning verbetert de scan door op
spelers te filteren; een laser of een geweer; een zonnepaneel; een camera waarmee je door zijn ogen kijkt.

**Mijn invulling**: modules geven gegevens met `read(pin)` en krijgen opdrachten met `write(pin, ...)`:

| Module | `read(pin)` | `write(pin, ...)` |
|---|---|---|
| Scan | true als hij een speler ziet (zicht, niet door muren) | — |
| Distance | blokken tot wat voor hem staat | — |
| Face Recognition | de naam van wie de scan ziet | filter: everyone, strangers, trusted, trainee of een naam |
| Pathfinding | true zolang hij onderweg is | — |
| Laser | true als hij klaar is | true = één schot |
| Gun | kogels over | true = één schot (Robot Ammo) |
| Solar Panel | zon die hij vangt (0-100) | — |
| Camera | true als iemand meekijkt | false = uit |

"strangers" en "trusted" vraagt de robot aan de server. Robots met een camera staan in de app Cameras, met
"ROBOT" erbij. De **Ion Blaster** laat robots en drones neervallen zonder ze kapot te maken; op mensen en
dieren werkt hij niet.

- [x] **1. Scan en Distance** — `read(scanPin)` is true als hij een speler ziet: binnen het bereik en de
      kijkhoek van zijn hoofd (Commando 14 blokken en 120 graden, Trainer 12 en rondom, Battle 10 en 90, Super
      Battle 12 en 70; config `headScanRange`, `headScanAngle`), en niet door muren. Spelers in creative of
      spectator ziet hij niet, net als monsters en torentjes. `read(distancePin)` is hoeveel blokken het is tot
      wat recht voor hem staat (een blok, een speler of dier; een muur vlak voor hem = 1; niets binnen 16 = 99).
      `read(pathPin)` is true zolang hij met `goto` onderweg is; een lege pin zegt false; `write` naar een
      module die daar niets mee doet, zegt dat in het logboek. Nieuw: `bot/BotSense` (wat hij ziet) en
      `bot/BotPins` (wat elke pin doet). Eén nieuwe test (`robot_scan_sees_players_in_sight`).
- [x] **2. Face Recognition** — `read(facePin)` is de naam van wie hij ziet ("" = niemand, de dichtste
      eerst), en `write(facePin, "strangers")` zet het filter waar zijn Scan (en straks zijn wapens) zich aan
      houden: "everyone" (zo begint hij, ook na opnieuw opstarten), "strangers" (wie niet bij de basis hoort),
      "trusted" (de eigenaar, zijn team en vertrouwde spelers), "trainee" (hoofdstuk 26; tot dan niemand) of de
      naam van een speler (hoofdletters tellen niet). Wie een vriend is, vraagt hij aan de server: zonder
      verbinding ziet hij met "strangers" en "trusted" niemand, een naam herkent hij wel. Eén nieuwe test
      (`robot_face_recognition_filters`).
- [x] **3. Laser en Gun** — `write(laserPin, true)` is één laserschot (5 schade, kost 60 batterij),
      `write(gunPin, true)` één kogel (6 schade, kost één **Robot Ammo**). Hij richt op wie zijn Scan ziet (door
      het filter van zijn Face Recognition), en zonder Scan-module of als hij niemand ziet schiet hij recht
      vooruit en raakt hij wat daar staat (tot 24 blokken). Tussen twee schoten zit de tijd van zijn armen
      (`armsFireInterval`: Commando 24 ticks, Trainer 30, Battle 36, Super Battle 18); `read(laserPin)` zegt of de
      laser klaar is, `read(gunPin)` hoeveel kogels hij nog heeft. Een muur en een schildkoepel houden een schot
      tegen (zijn eigen koepels niet); spelers raakt hij alleen met PvP aan en niet in creative. De laser komt uit
      zijn rechterarm (oranjerode straal, vonken), het geweer uit zijn linker (een dun geel spoortje en rook);
      twee nieuwe geluiden (`robot_laser`, `robot_gun`). **Robot Ammo**: buskruit, een koperstaaf en twee
      ijzerklompjes geven 16 kogels; rechtsklik een robot ermee (als je hem mag instellen) en hij laadt ze, tot
      64 (`maxAmmo`); zijn scherm zegt "Ammo 12/64" als hij een Gun-module heeft, en opgepakt gaan ze mee in zijn
      item. Sterfberichten: "... was lasered by Guard 1" en "... was shot by Guard 1". Netwerkversie 22.
      Screenshots `rb20` (laser) en `rb21` (geweer). Twee nieuwe tests (`robot_laser_and_gun_shoot`,
      `robot_shots_stop_at_walls_domes_and_pvp`; in de testwereld staat PvP uit, dus daar test ik op koeien).
- [x] **4. Zonnepaneel** — de Solar Panel-module laadt zijn batterij in de zon, ook als hij uit staat: in de
      volle middagzon 0,5 per tick (`solarCharge`; zo houdt een robot die stilstaat het overdag vol), minder als
      de zon laag staat of het regent, niets 's nachts of onder een dak. `read(solarPin)` is hoeveel zon hij
      vangt (0-100). De zonberekening staat nu op één plek (`station/SolarMath`), voor het zonnepaneel van het
      net en de module. Eén nieuwe test (`robot_solar_panel_charges_in_the_sun`).
- [x] **5. Camera** — een robot met een Camera-module en verbinding staat in de app **Cameras**, tussen je
      camera's, met "ROBOT" linksonder (screenshot `rb22`). Het beeld kijkt live mee met zijn hoofd (een stukje
      voor zijn ogen; de robot zelf tekenen we niet, anders keek je in zijn hoofd), alleen als je spel hem ziet
      (anders OUT OF RANGE). Zijn grote beeld heeft alleen Back: draaien doet zijn programma (screenshot `rb23`).
      Zolang je naar een robot kijkt, zegt de app dat om de seconde tegen de server, en dan is `read(cameraPin)`
      true; `write(cameraPin, false)` zet de camera uit (NO SIGNAL), `true` weer aan. Binnenin herkent de app
      een camera nu aan een sleutel (plek van een camerablok, of nummer van een robot), want een robot loopt.
      Eén nieuwe test (`robot_camera_in_the_cameras_app`).
- [x] **6. Ion Blaster** — een nieuw wapen (lapis, twee ijzer, een Computer Chip en redstone; slijt na 200
      schoten, 1,5 s tussen twee schoten). Rechtsklik: een dikke blauwe ionenstraal tot 24 blokken (screenshot
      `rb24`; muren en schildkoepels houden hem tegen). Een guard robot die hij raakt, valt neer: de nieuwe
      toestand **DOWN**, hij ligt op zijn rug (screenshot `rb25`), af en toe een vonkje, zijn programma staat
      stil, hij loopt niet en gebruikt geen stroom, en zijn leven blijft vol. Na 60 seconden (`ionDownSeconds`)
      staat hij weer op (het opstartgeluid) en begint zijn programma opnieuw bij `when started:`. Op spelers en
      dieren doet de straal niets. Een nieuw geluid (`ion_blaster`). In een training (hoofdstuk 26) blijft hij
      liggen tot de training voorbij is; drones (hoofdstuk 25) komen er dan ook bij. Eén nieuwe test
      (`ion_blaster_downs_robots_only`).

Tests: de scan ziet een speler in zicht en niet door een muur; het filter "strangers" ziet een vreemde en
niet de eigenaar; zonder verbinding ziet hij niemand; een koepel houdt de laser tegen; zon laadt overdag; de
Ion Blaster zet een robot DOWN en een speler blijft ongeschonden.

- [x] **Afronding** — 270 tests: 267 groen, de 3 rode van hoofdstuk 14 stap 2. Zeven nieuwe in hoofdstuk 24
      (`BotSenseGameTests`: scan en afstand, gezichtsherkenning, laser en geweer, muren/koepels/PvP, zonnepaneel,
      camera, Ion Blaster). De Help van een robotprogramma heeft nu **MODULES (read / write)** met een voorbeeld
      per module. In de showcase de nieuwe functie **"Robot senses and weapons"** (85, na "Robot programs"): twee
      Commando's schieten op husks (laser en geweer), jij legt er een neer met de Ion Blaster, en de app Cameras
      kijkt door de ogen van de andere (een neergeschoten robot geeft NO SIGNAL). LEESMIJ.md: een nieuw stuk "Zien
      en schieten". Het jar gebouwd.

### 25. Programmeerbare security drones (14 oktober)

Jouw antwoorden: de security drones krijgen dezelfde programmeerbaarheid, plus **omhoog en omlaag**. Hun
laadstation wordt de **Drone Dock**. De **arsenal-drones** laden daar niet meer, die blijven in hun baan.
Drones bouw je zelf, met een eigen printplaat. De Biebia's Industries Tower krijgt docks met drones die Biebia al
geprogrammeerd heeft.

**Mijn invulling**: de core maakt geen gratis drones meer; de schakelaar "Drones" in de app Devices zet al je
drones aan of uit. Arsenal-drones herstellen zich in hun baan, even snel als vroeger op een dock, zodat je
niets kwijtraakt. Oude drones (zonder printplaat) blijven werken tot ze neergeschoten worden.

- [x] **1. Vliegende beweging** — de nieuwe **Security Drone** is een vliegende guard robot: dezelfde printplaat,
      modules, batterij, aan/uit en Ion Blaster. Hij vliegt blok voor blok: `forward(n)` en `back(n)` op dezelfde
      hoogte, `up(n)` en `down(n)` recht omhoog en omlaag (niet door een plafond of de grond: dan is
      `drone.blocked` waar), `turn_left()`/`turn_right()`, `goto(x, y, z)` met de padzoeker voor vliegers (om muren
      heen; Pathfinding-module), en `charge()` (terugvliegen, landen op zijn Drone Dock, laden, opstijgen en terug
      naar waar hij was). In de app Logic kies je na **+** nu ook **Drone program** (een lichtblauwe D in de lijst;
      een nieuw heet "Flight 1"). Daarin mogen `up`/`down` en `drone.battery`, `drone.x/y/z`, `drone.height`
      (hoeveel blokken lucht onder hem), `drone.facing`, `drone.name`, `drone.blocked` en `drone.connected`; de Help
      toont je drones en het voorbeeld "Patrol a square". Een fout zegt nu ook waar iets wel werkt ("forward() only
      works in a robot program or a drone program, not in a server diagram"). Zonder stroom, of geraakt door de Ion
      Blaster, valt hij naar de grond (zonder schade). Zijn Scan kijkt rondom (16 blokken), zijn laser en geweer
      hangen onder zijn buik. Nieuw in de config `[robots]`: `droneHealth` (16), `droneBattery` (3000),
      `droneDockChargeRate`, `droneDrainOn`, `droneDrainFlying`, `droneSpeed` (3 blokken per seconde),
      `droneScanRange`, `droneScanAngle` (360), `droneFireInterval` en `droneTakeOff`. Binnenin: wat robots en
      drones delen staat nu in `bot/BotEntity`, vliegen in `bot/DroneMove`. Vijf nieuwe tests (`DroneGameTests`).
- [x] **2. De drone bouwen** — recept: ijzer, een amethist, redstone, een Robot Circuit Board en een Computer Chip.
      Rechtsklik op een Drone Dock en hij staat erop (zijn thuis en zijn lader); op een ander blok hangt hij erboven,
      zonder thuis. Hij heet "Drone 1", "Drone 2"... Sneak + rechtsklik met een lege hand: hetzelfde scherm als een
      robot (zonder onderdelen), met Board (solderen) en Pick up (alles gaat mee in het item). Zijn model: een
      grafietgrijze romp met een wit dakje, vier rotors en twee ski's, en per module een stukje (radarkoepel,
      lensstrook, sensoroogje, antenne, laserloop, geweerloop, zonnevleugels, camera); zijn lichtjes branden alleen
      als hij aan staat. In de app Robots staat "drone" achter zijn naam (en Program toont je droneprogramma's), in
      de app Cameras "DRONE" (zijn camera kijkt een beetje naar beneden). Screenshots `sd01`-`sd11`
      (`gradlew runVisualCheck -Ponly=secdrones`).
- [x] **3. De Drone Dock opnieuw** — een Drone Dock is nu het thuis en de lader van één security drone. Je zet
      je drone erop met het item, alleen op je eigen dock of op een dock in een basis waar je vertrouwd bent.
      Rechtsklik op het dock zegt hoe het met zijn drone gaat ("Drone 1 is charging (56%)", "is out (battery
      80%)", "is far away", of "empty (right-click it with a Security Drone)"). Drones uit je arsenaal komen er niet
      meer op: ze herstellen zich in hun baan, even snel als vroeger op een dock en zonder stroom (nieuw in
      `[drones]`: `orbitRepairPerSecond`, 0,25 leven per seconde). De oude vlag "In hangar" gaat bij het inloggen
      weg. Weg: het hologram op het dock, `dockRepairPerSecond` en `dockEnergyPerHealth` in `[power]`, en de
      screenshots `d01`-`d05`. De twee oude dock-tests zijn één nieuwe geworden (`arsenal_drones_repair_in_orbit`).
- [x] **4. De core** — de kern maakt geen gratis drones meer (weg uit `[security]`: `maxDrones` en
      `droneRespawn`). De schakelaar **Drones** (apps Access en Devices) zet nu ook al je security drones in zijn
      gebied aan of uit: uit vliegen ze naar hun dock en landen erop, en de taakbalk zegt bijvoorbeeld "Drones
      landing (2 security drones)". Alleen drones met verbinding horen het; een drone die nu niet geladen is, krijgt
      het als hij terug is. In de app Devices staan je security drones bij DRONES, met hun toestand en batterij. De
      oude drones van vroeger heten daar nu "Patrol drone 1", "Patrol drone 2"... en blijven werken tot ze
      neergeschoten worden. Tests: `security_core_launches_no_drones` (in plaats van `security_core_launches_drones`:
      de kern maakt er geen meer, en een oude drone patrouilleert nog) en `core_drones_switch_sends_drones_home`.
- [x] **5. De Biebia's Industries Tower** — de hangar van de toren had al zes Drone Docks met stroom. De kern van een toren van
      niemand zet nu één keer op de eerste vier een **drone van Biebia** ("Tower drone 1" tot "Tower drone 4"; hoeveel:
      `towerDrones` in `[security]`): aan, met Scan, Face Recognition, Laser en Pathfinding, en het programma
      **Tower patrol** (filter "strangers", omhoog, rondjes van 3 blokken door de hangar, elke seconde schieten op wie
      hij ziet, laden onder 25%). Ze zijn van niemand, dus voor hen is iedereen een vreemde, en ze schieten ook zonder
      PvP (het is de toren die schiet, geen speler). Gaat het alarm (iemand begint de kern te hacken), dan krijgen ze
      de opdracht "alarm" met de plek van de indringer en vliegen ze erheen, als ze er kunnen komen. Wie de toren
      hackt, krijgt ze: dan zijn ze van hem (heeft hij al een drone met die naam, dan krijgt die een nieuwe). Een
      drone die kapot gaat, komt niet terug. Een toren uit een bestaande wereld die nog van niemand is, krijgt ze ook
      (één keer). Het structuurbestand zelf veranderde niet. Twee nieuwe tests (`biebia_tower_has_biebia_drones`,
      `biebia_drone_shoots_strangers_until_hacked`).

Tests: bouwen en laden op een dock; `up(3)` stijgt; de core spawnt zelf geen drones meer; een dock pakt geen
arsenal-drone meer; de toren heeft vier gewapende drones; een oude drone patrouilleert nog.

- [x] **Afronding** — 277 tests: 274 groen, de 3 rode van hoofdstuk 14 stap 2. Acht nieuwe in `DroneGameTests`
      (vliegen met up/back/turn, plafond en grond, laden op het dock, neer en zonder stroom, de taal, de
      schakelaar Drones, Biebia's drones in de toren en hoe ze schieten, het alarm en de hack), en twee herschreven
      (`arsenal_drones_repair_in_orbit`, `security_core_launches_no_drones`). Screenshots `sd01`-`sd11`
      (`-Ponly=secdrones`) en `t25_hangar_biebia_drones` (`-Ponly=tower`). In de showcase de nieuwe functie
      **"Security drones"** (86, na "Robot senses and weapons"): twee drones op hun dock, hun scherm en bord, de app
      Logic met Drone program, opstijgen en vierkantjes vliegen, een drone die gaat laden, de camera van een drone,
      de Ion Blaster en de schakelaar Drones. "Biebia's Industries Tower: hangar and landing pad" (nu 87) toont Biebia's drones in de
      hangar in plaats van arsenaal-drones op de docks. LEESMIJ.md: een nieuw stuk "Security drones", en het stuk
      over de Biebia's Industries Tower en het arsenaal bijgewerkt. Het jar gebouwd.

### 26. Training zoals Rookie squad (14 oktober)

Jouw wens: programma's die gecoördineerde golven op de speler sturen, die non-lethal of lethal schieten (jouw
keuze). Een **Training End**-blok geeft redstone als de trainee geraakt wordt, en daarin programmeer je wat
er dan gebeurt (robots aan of uit).

Jouw antwoorden: een **Training Start**-blok; wie erop klikt, is de trainee; daar kies je de training en
lethal of non-lethal. Het Training End-blok heeft een **mini-code-editor** in het blok, dezelfde taal. Je
diagram hoort `when training_started("Bootcamp"):`.

**Mijn invulling**: standaard 1 leven (één treffer en je ligt eruit). Non-lethal = geen schade, een blauwe
flits en 1 seconde trager. Alleen de eigenaar, zijn team en vertrouwde spelers starten een training. Golven
via squads: `activate("Wave 1")`, `when squad_down("Wave 1"):`. Een **Training Goal**-blok: erop komen is
geslaagd. Na afloop een rapport van I.M.T en de app Training.

- [x] **1. Centraal schot** — elk schot van je beveiliging komt nu op één plek uit (`training/Shots`): torentjes,
      laserwanden, de oude patrouilledrones, robots en security drones. Is wie geraakt wordt de trainee van een
      training die bezig is, dan beslist de training: met **trainingsschoten** geen schade, maar een blauwe flits
      (met een "zap"), een seconde trager en een leven eraf; **lethal** doet echte schade en telt ook. Eén schot is
      één leven: na een treffer telt een volgende even niet mee (`hitCooldown` in `[training]`, 20 ticks; anders
      kostte een laserwand elke tick een leven). Met trainingsschoten schieten torentjes alleen hun laser, geen
      raketten.
- [x] **2. Training-sessie** — één training tegelijk per kern (alleen in het geheugen van de server), met een naam,
      trainingsschoten of lethal, 1-10 levens en eventueel een tijdslimiet. I.M.T telt af (`countdownSeconds`,
      3) en zegt "go!". Hij stopt als de levens op zijn, de tijd om is (zonder Training Goal-blok in de basis ben je
      dan geslaagd, met zo'n blok niet: je moest het doel halen), de trainee doodgaat, uitlogt of het gebied van de
      kern verlaat, of als hij gestopt wordt. Dan zegt I.M.T het rapport tegen de trainee en de eigenaar
      ("Training Bootcamp: Senne ran out of lives after 1:12 (3 hits: Guard 2 x2, Guard 1 x1; 2 robots down)"), de
      kern bewaart het (de laatste 10, `maxReports`) en het logboek krijgt een regel (TRAINING). Je diagrammen
      horen `when training_started("Bootcamp"):`, `when training_hit("Bootcamp"):` en `when training_ended("Bootcamp"):`,
      met `trainee`, `lives`, `hits` en (na het einde) `passed`; in de Help onder TRAINING. Een robot of drone die
      tijdens een training neergaat (de Ion Blaster), blijft liggen tot hij voorbij is; dan staat hij op en gaat hij
      naar zijn dock. Nieuw in de config: `[training]`. Drie nieuwe tests (`TrainingGameTests`).
- [x] **3. Training Start-blok** — een klik begint de training, met jou als trainee: alleen de eigenaar van de
      basis, zijn team, vertrouwde spelers of wie het blok neerzette (anders kon een inbreker jouw verdediging op
      trainingsschoten zetten). Loopt hij al, dan stopt een klik van de trainee of een vertrouwde speler hem. Sneak +
      klik met een lege hand: de instellingen (Name, Training shots of Lethal, Lives 1-10, Time: geen limiet tot een
      uur). Het moet in het gebied van een beveiligingskern staan. In creative zegt hij erbij dat niemand op je
      schiet. Recept: ijzer, een limoengroene kleurstof, redstone en een Computer Chip. Netwerkversie 23.
- [x] **4. Squads** — in de app Robots heeft elke robot en drone een knop **Squad**: typ een naam ("Wave 1"; leeg =
      geen squad); de rij zegt dan "squad Wave 1". In een diagram mag waar een robotnaam staat ook een squad staan:
      `activate("Wave 1")`, `deactivate("Wave 1")` en `order("Wave 1", "attack", x, y, z)` gaan naar ze allemaal
      tegelijk (een robot met die naam gaat voor). `when squad_down("Wave 1"):` begint als elk lid neer ligt (de Ion
      Blaster), geen stroom meer heeft of kapot is, nadat er eentje aan stond (uitgezet telt niet als neer), en
      `active("Wave 1")` zegt hoeveel er nu aan staan en rechtop. De Help toont je squads en wie erin zit. Een
      onbekende naam zegt nu "there is no robot, drone or squad called ...". Eén nieuwe test
      (`squads_switch_together_and_report_down`).
- [x] **5. Training End-blok** — bij elke treffer in een training van zijn basis geeft hij een redstone-puls (een
      halve seconde, `endPulse`; het lampje op de zijkanten brandt dan rood) en loopt zijn eigen programmaatje op je
      server (met de grenzen van zijn RAM). Sneak + klik met een lege hand (wie hem neerzette of vertrouwd is): de
      **mini-editor**, dezelfde code-editor als in de app Logic (kleuren, Ctrl+Spatie met je apparaten, robots en
      squads, Check). Zo'n "Training End program" kent alleen `when trainee_hit:` (niet gewoon "hit": dat woord
      gebruik je misschien al als naam), met `trainee`, `lives` en `hits`, en mag robots en squads aansturen:
      `if lives == 0: deactivate("Wave 1")`. Hij bewaart een programma alleen als het klopt. Recept: ijzer, een rode
      kleurstof, redstone en een Computer Chip. Een oud diagram dat zelf al een naam `lives` of `hits` had, houdt die
      buiten een training gewoon. Eén nieuwe test (`training_end_block_pulses_and_runs_its_program`).
- [x] **6. Training Goal-blok** — een geblokte finishvlag met een gouden rand (recept: witte en zwarte wol en een
      gouden staaf). Stapt de trainee erop (in het gebied van zijn training), dan is hij geslaagd. Staat er een in de
      basis, dan is "de tijd is om" mislukt: hij moest het doel halen. Twee nieuwe tests
      (`training_start_block_only_for_trusted`, `training_goal_ends_with_success`).
- [x] **7. Einde en rapport** — met het filter **"trainee"** (`write(facePin, "trainee")`) ziet een robot of drone
      alleen de trainee van de training bij zijn kern, en alleen zolang die bezig is (dat vraagt hij aan de server).
      Gaat er een neer tijdens de training (de Ion Blaster), dan blijft hij liggen tot de training voorbij is; dan
      staat hij op en gaat hij uit (naar zijn dock), en telt het rapport hem mee. Nieuwe app **Training** op de
      desktop: de training die nu bezig is (wie, trainingsschoten of lethal, levens, treffers, hoe lang al en de
      limiet), met een knop **Stop**, en daaronder de rapporten van de laatste trainingen (PASSED, FAILED of
      STOPPED, hoe, hoe lang, en wie raakte). De trainee kan altijd geraakt worden door de robots en drones van de
      training, ook als PvP uit staat (hij koos er zelf voor). Twee nieuwe tests (`robots_in_a_training`, en van
      begin tot eind `robot_shoots_the_trainee_with_training_shots`).

Tests: een non-lethal treffer doet geen schade maar telt; lethal doet schade; levens op beëindigt de training;
het End-blok geeft een puls en deactiveert een squad; een inbreker kan geen training starten; het Goal-blok
beëindigt met succes; down-robots staan na afloop weer op.

- [x] **Afronding** — 286 tests: 283 groen, de 3 rode van hoofdstuk 14 stap 2. Negen nieuwe in `TrainingGameTests`
      (trainingsschoten tegen lethal, alleen vertrouwden starten, het doel, de tijdslimiet, squads, het End-blok,
      robots in een training, en van begin tot eind een robot die de trainee raakt). Screenshots `tr01`-`tr07`
      (`gradlew runVisualCheck -Ponly=training`): de drie blokken, het instelscherm van Start, de mini-editor van End,
      het aftellen, en de apps Training, Robots (met squads) en Logic (Help onder TRAINING). Wat ik op de screenshots
      zag en verbeterde: in de app Robots liep de regel "squad Wave 1" onder de knoppen door (nu zegt de knop zelf
      **Squad: Wave 1**), op het instelscherm van Start raakte de onderste regel de knoppen en stak een uitleg buiten het
      venster, en de blauwe flits van een
      treffer zat in de eerste persoon vlak voor je ogen, zodat je een seconde bijna niets zag (nu kleiner en rond je
      lijf). In de showcase de nieuwe functie **"Training"** (87, na "Security drones", in survival): het
      instelscherm, de mini-editor, aftellen, twee Trainers (Wave 1) die op je schieten, de lamp op het End-blok, jij
      met de Ion Blaster, Wave 2 die aangaat, het doel, het rapport en de app Training. "Biebia's Industries Tower: hangar and
      landing pad" is nu 88. LEESMIJ.md: een nieuw stuk "Training zoals Rookie squad". Het jar gebouwd.

### 27. Inbraak- en uitbraakoefening (14 oktober)

Jouw wens: "test hoe moeilijk het is om tot bij de security core te geraken en hem te hacken, of hoe moeilijk
het is om uit je prison uit te breken." Jouw antwoord op de vraag wat er daarna gebeurt: **niets wordt
teruggezet**. Wat kapot gaat, blijft kapot; robots staan weer op, tenzij ze echt vernietigd zijn.

**Mijn invulling**: de soorten **Break-in** en **Break-out**, alleen in survival. Tijdens zo'n oefening
behandelt je hele basis de trainee als indringer, ook de eigenaar zelf: torentjes, drones, robots (filter
"strangers"), laserwanden, de zone (niets bouwen of slopen, behalve vrije blokken en C4), de desktop (niet te
openen) en de bril (niet op afstand schakelen). Kaarten die hij bij het begin had, werken niet. Een geslaagde
hack (de Hacking Laptop-minigame) telt als rapport; de core blijft van jou. Break-out is geslaagd als hij het
Goal-blok buiten de cel haalt. Het rapport zegt hoe dicht hij bij de core kwam en wie hem raakte.

- [x] **1. Soorten en sessie** — in het instelscherm van het Training Start-blok een knop **Kind**: *Training*,
      *Break-in drill* of *Break-out drill* (met een uitleg ernaast: "you are the intruder", "escape to the goal"). Het
      is dezelfde sessie als een training (één per kern, aftellen, levens, trainingsschoten of lethal, tijdslimiet), met
      een soort erbij (`Training.Kind`). Een oefening begint alleen in survival ("A drill only works in survival"), en
      wie tijdens de oefening naar creative gaat, stopt ermee. I.M.T zegt bij het begin wat je moet doen ("Hack the core
      with a Hacking Laptop" of "Reach the goal outside your cell"). De app Training zegt welke soort bezig is.
- [x] **2. Indringer in je eigen basis** — één plek (`Training.isDrillTrainee`), pas na het aftellen (anders schoten
      je torentjes al tijdens het aftellen echt). Ernaar vragen: `SecurityCores.isFriend` en `isIntruder` (torentjes,
      oude patrouilledrones, laserwanden, robots en drones met "strangers"/"trusted", de laptop; een indringer ook zonder
      PvP), `ZoneData.isBuilder` (je zone: alleen vrije blokken en C4, en je ziet de rand niet), `BaseTrust.mayEdit` (je
      apparaten, ook wat je zelf neerzette), `SecurityOs.openFor` (de desktop, ook op afstand; een open desktop gaat bij
      het begin dicht), `ImtRedstone` (je relais, laserzenders, koepels, torentjes en stroomkasten in dat gebied, ook met
      je bril: "Your base does not obey you during the drill"), `ZoneMarkerItem` (je zone veranderen) en `LogicEvents`
      (de keycards die je bij het begin bij je had werken niet, en `player.owner` is false). Daarna is alles meteen
      weer gewoon.
- [x] **3. Hack zonder eigenaarswissel** — de trainee zet de Hacking Laptop op zijn eigen kern (dat mag nu) en speelt
      de terminal uit: de oefening is geslaagd (`HACKED`, "hacked the core"), de kern blijft van hem en hij krijgt de
      laptop terug. Eindigt de oefening anders terwijl de laptop nog op de kern staat, dan krijgt hij hem ook terug.
- [x] **4. Uitbraak** — geslaagd als hij een Training Goal-blok haalt (dat zet je buiten de cel); zonder zo'n blok in
      de basis begint een uitbraak niet. Bij een oefening is "de tijd is om" altijd mislukt (uitzitten is niet genoeg).
- [x] **5. Rapport** — "Break-in drill Vault: Senne hacked the core after 4:12 (closest 0 blocks from the core; 1 hit:
      Guard 1 x1)": bij een inbraak hoe dicht hij bij de kern kwam, en altijd wie hem raakte. De kern bewaart het (ook de
      soort en de afstand; een oud rapport is een gewone training), en de app Training laat het zien.

Tests: de eigenaar als trainee wordt geraakt door zijn eigen torentje en robot; hij kan de zone niet slopen;
een oude kaart opent de lezer niet; een geslaagde hack laat de eigenaar ongemoeid; de oefening eindigt als hij
sterft of uitlogt; creative wordt geweigerd.

- [x] **Afronding** — 294 tests: 291 groen, de 3 rode van hoofdstuk 14 stap 2 (jouw stroomwaarden). Vier nieuwe in
      `DrillGameTests`: de basis tegen je (tijdens het aftellen nog vriend, daarna indringer ook zonder PvP, geen zone, geen
      robot instellen, geen desktop; dood = voorbij met rapport; daarna weer vriend), kaarten (een kaart van het begin
      "not valid here", een gevonden kaart wel), de hack (geslaagd, de kern blijft van hem, de laptop terug) en de regels
      (alleen survival, een uitbraak zonder doel begint niet, naar creative = gestopt). Onderweg gevonden: de test van de
      camera's van de toren ruimde maar één vreemde kern per camera op, en met meer tests in de wereld stonden er soms
      twee te dichtbij; nu ruimt hij ze allemaal op. Screenshots `tr08_drill_start_screen` (Kind = Break-in drill) en
      `tr09_os_drill_report` (`-Ponly=training`). In de showcase de nieuwe functie **"Break-in drill"** (88, na "Training";
      de toren is nu 89). LEESMIJ.md: "Inbraak- en uitbraakoefening" in het stuk over training.

Bouwen gaat in volgorde, één stap per commit, met bij elke stap de tests, de screenshots en de showcase.

### 28. Uit je showcase-notities (16 oktober)

Jouw notities in `showcase-notities.md`:
- bij **34. Relay groups**: "Ik wil dat je de groups niet meer in de redstone tab kunt configureren, dit kan
  enkel nog op de desktop";
- bij **76. Logic: names, sums and loops**: "ik wil dat de repeat(3) zoals een for loop is zodat je je variable
  vb i ook kunt gebruiken" en "de forever loop mag ook weg, een for en while is voldoende";
- bij **83. Soldering modules**: "maak al de robot part non stackble" en "zorg dat de stark core geen drones
  vanzelf maakt" (dat tweede deed hoofdstuk 25 al, `225f0ce`).

**Mijn invulling** (zeg het als je het anders wil):
- De 16 robotonderdelen (hoofd, lijf, armen, benen) stapelen niet meer: één per vakje. De modules wel nog (tot
  16): die soldeer je op een printplaat, het zijn geen stukken van het lijf.
- Groepen: in de Redstone-tab van het G-menu kun je geen groepen meer maken, hernoemen, verplaatsen of
  verwijderen (de knop Groups gaat weg), en in het naamscherm van een relais kies je geen groep meer. Je ziet je
  groepen daar wel nog, met hun knoppen ON, OFF en PULSE, en onderaan staat waar je ze instelt: in de app
  **Groups** op de desktop.
- Lussen zoals in Python: `for i in range(3):` (i is 0, dan 1, dan 2) en `for i in range(1, 4):` (1, 2, 3); het
  bereik mag ook een naam of een som zijn (`range(count)`, `range(1, n + 1)`). `forever:` is weg: dat schrijf je
  `while true:`. `repeat(3):` is weg: dat wordt `for i in range(3):`. Oude programma's zet ik bij het laden om
  (een lus in een lus krijgt `j`, dan `k`...). In de blokjes wordt REPEAT een blok FOR (met de naam en het
  bereik), en FOREVER gaat weg.

Ik doe dit vóór hoofdstuk 27.

- [x] **1. Robotonderdelen stapelen niet** — de 16 onderdelen stapelen tot 1 (de modules nog tot 16); de test
      `robot_parts_are_items_with_recipes` kijkt het na.
- [x] **2. Groepen alleen op de desktop** — in het redstone-scherm (toets ') is de knop **Groups** weg; onderaan staat
      "Set up groups in the Groups app on your Security OS desktop." Je groepen staan er nog als boom met **ON**, **OFF**
      en **PULSE**. In het scherm van een relais zie je onder **Group** zijn groep (alleen te lezen), met eronder
      "Change it in the Groups app on your Security OS desktop."; Save bewaart alleen de naam. Het oude groepen-scherm
      van het G-menu (`RelayGroupsScreen`) en zijn teksten zijn weg; de server en de app Groups veranderden niet.
      Screenshots `rl03` en `rl043` (`-Ponly=relays`); `rl04`, `rl041` en `rl042` (het oude groepen-scherm) bestaan
      niet meer. In de showcase ("Relay: name and group" en "Relay groups, invert and emitter") het groepen-scherm
      eruit. LEESMIJ.md: "Redstone-relais".
- [x] **3. De for-lus** — `for i in range(3):` (i is 0, 1, 2) en `for i in range(1, 4):` (1, 2, 3); het bereik mag
      een naam of een som zijn, en `i` gebruik je zoals elke naam (`say("Round " + i)`). Zoals in Python rekent hij het
      bereik één keer uit, telt hij door ook als je `i` in de lus verandert, en houdt `i` na de lus zijn laatste waarde.
      Een getal met een komma wordt afgerond, en een bereik groter dan een miljard wordt een miljard. `while true:` gaat
      door tot een break. `repeat` en `forever` zijn weg; typ je ze toch, dan zegt de code "repeat is now a for loop:
      write for i in range(3):" of "forever is now written while true:". Nieuwe fouten: "range takes one or two
      numbers", "range needs numbers", "After the name comes in", "A for loop counts with range", "for needs a name that
      counts", "name holds text, so it cannot count". `in`, `range`, `repeat` en `forever` mogen geen naam zijn. Oude
      programma's (op je server, in je robots en drones, in hun items, in backups) worden bij het laden omgezet:
      `repeat(3):` wordt een for met de eerste naam uit i, j, k... die het programma nog niet gebruikt en die geen lus
      eromheen al heeft (een lus in een lus krijgt dus j), en `forever:` wordt `while true:`. De bewaarde code gaat mee,
      met je commentaar erin; past hij daarna niet precies bij de blokjes, dan schrijven we hem opnieuw uit de blokjes.
      De drones van Biebia vliegen nu met `while true:`. Twee tests herschreven (`logic_loops`, `logic_loops_run`), één
      nieuwe (`logic_old_loops`), en de programma's in de andere tests, de showcase en de screenshots omgezet.
- [x] **4. De for-lus in de blokjes** — het blokje **FOR** (roze, in plaats van REPEAT en FOREVER) met de vakken
      **Name (counts, like i)** en **Range (3, or 1, 4)**; op het blokje staat "i in range(3)". De Help heeft onder LOOPS
      `for i in range(3):`, `for i in range(1, 4):`, `while ...:` en `while true:`; Ctrl+Spatie stelt `for i in
      range(3):` voor (met de 3 geselecteerd) en `while true:`, en kent de naam van een for als een van je eigen namen.
      De voorbeelden "Patrol a square" (robot en drone) gebruiken `while true:`. De editor kleurt `for`, `in` en
      `range`. LEESMIJ.md: "Lussen". In de showcase ("Logic: names, sums and loops") telt Blink met
      `for i in range(1, 4):` en zet hij "Blink 1 of 3" in het logboek. Screenshots `l12`-`l14` (`-Ponly=logic`).

### 29. Robots die niet meer bestaan (18 oktober)

Jouw wens: "zorg dat je robots die niet meer bestaan verdwijnen en dat je ze kunt verwijderen en dan niet meer in
je systeem staan".

**Wat ik vond**: in je wereld Bunker (TechModPack) stonden 9 robots in de lijst, maar er bestaan er maar 3 (Guard 7,
8 en 9, op drie docks). Guard 1 tot en met 6 waren spookrobots. Oorzaak: de knop **Pick up** van het robotscherm
pakte de robot op (hij ging uit de lijst), en meldde hem daarna per ongeluk meteen weer aan. Zette je hem weer neer,
dan was zijn naam nog bezet door zijn eigen spook, dus heette hij "Guard 2", dan "Guard 3"... Hetzelfde gebeurde als
een robot afbrandde door een kortsluiting terwijl je hem aanzette. De test van toen pakte hem op zonder de knop,
daarom zag hij het niet.

**Jouw keuze**: Delete alleen voor robots die je systeem niet kan bereiken ("far away"). Een werkende robot haal je
weg door hem op te pakken.

- [x] **1. Geen spook meer** — een robot die al weg is (opgepakt of kapot), meldt zich niet meer aan bij `BotNetwork`.
      Een robot die nieuw in de lijst komt met een naam die een andere robot van dezelfde eigenaar al heeft (hij komt
      terug na Delete, of iemand kopieerde hem met WorldEdit), krijgt een vrije naam ("Guard 3").
- [x] **2. Spoken ruimen zichzelf op** — om de 2 seconden kijkt de server de robots na die zich al 3 seconden niet
      meer meldden (een robot meldt zich om de seconde). Is het stuk wereld waar hij het laatst stond geladen en draait
      het, en is hij er niet, dan bestaat hij niet meer: hij gaat uit de lijst, en het logboek van zijn kern zegt
      "Guard 3 no longer exists, so it was taken off the list". In je wereld Bunker verdwijnen Guard 1 tot en met 6 zo
      vanzelf zodra je in je basis bent. Een robot in een stuk wereld dat niet geladen is, laat de server staan.
- [x] **3. Delete in de app Robots** — een robot die grijs "far away" staat, heeft rechtsboven een rode knop
      **Delete**; die vraagt eerst **Sure?** (nog een keer klikken, 4 seconden). Hij gaat uit de lijst, met een
      programma, opdracht of aan/uit dat op hem wachtte, en het logboek zegt wie het deed ("Senne deleted Guard 3 from
      the list"). Bestaat hij toch nog, dan komt hij vanzelf terug zodra hij weer geladen is. Een robot die er gewoon
      is, haalt Delete niet weg: "Guard 7 is here: pick it up (sneak + right-click) to remove it".

Tests: oppakken met de knop laat geen spook achter en hij heet daarna weer Guard 1; afbranden ook niet
(`robot_pick_up_leaves_no_ghost`); een verdwenen robot gaat na een paar seconden uit de lijst, een robot ver weg en
een robot die er is blijven (`robot_network_forgets_robots_that_are_gone`); Delete werkt alleen voor een robot ver
weg, en die komt terug als Guard 3 als een nieuwe robot intussen Guard 2 heet (`robot_delete_from_the_desktop`).

- [x] **Afronding** — 289 tests: 286 groen, de 3 rode van hoofdstuk 14 stap 2 (jouw stroomwaarden). Onderweg gevonden:
      de testwereld ruimt robots van eerdere tests niet op, en die melden zich bij de dichtstbijzijnde kern; daardoor
      zag `robot_desktop_app` soms 3 robots in plaats van 1. Die test zoekt zijn robot nu op zijn nummer, en de nieuwe
      tests ruimen hun robots op. Screenshot `rb26_os_robot_delete` (`-Ponly=robots`): een robot "far away" met
      **Sure?** op zijn knop Delete. In de showcase (82, "Guard robots") komen er aan het eind twee spookrobots bij:
      Old guard gaat weg met Delete, Lost guard verdwijnt vanzelf. LEESMIJ.md: "Een robot weghalen".

### 30. De rotors van de security drone (19 oktober)

Jouw vraag: "kijk hoever je staat met het maken van de robots en ga verder met wat nog niet klaar is". Hoofdstuk 27
was gebouwd maar nog niet vastgelegd: dat is nu commit `de34f6e`. Daarmee zijn de hoofdstukken 20 tot en met 29 af.
Eén gat bleef over, dat ik zelf bij "Nog nooit in het spel bekeken" had gezet: een security drone vloog zonder geluid.

- [x] **1. Het rotorgeluid** — een nieuw geluid `drone_rotor` uit `tools/generate_sounds.py`: een zacht, snel "brrr"
      van vier rotors die net niet even snel draaien (ze zweven traag tegen elkaar), met een lage toon van de bladen;
      een lus zonder naad. In je spel (`client/robot/DroneRotorSound`) speelt het zolang zijn rotors draaien, heel stil
      (0,12: ongeveer zo stil als het laadzoemen van een robot; jouw wens van 7 oktober: "het mag altijd geluid maken
      maar gewoon heel stil/subtiel"), voor hooguit de 4 dichtste drones binnen 20 blokken. Opstijgen: de rotors spinnen
      op (het geluid begint laag); vliegen of stijgen: iets hoger en voller; landen of vallen: het zakt in ongeveer een
      seconde weg. Eigen volumeknop `droneRotorVolume` in `imttech-client.toml` (1 = zo, 2 = dubbel, 0 = uit). Wanneer de
      rotors draaien, staat op één plek (`GuardDroneEntity.rotorsSpinning`), die het model ook gebruikt: als hij aan
      staat, of uit staat maar nog naar zijn dock vliegt. **Nieuw**: terwijl hij op zijn dock laadt (met `charge()`),
      staan zijn rotors stil; vroeger draaiden ze daar door. Eén nieuwe test (`drone_rotors_spin_only_when_flying`), en
      `drone_falls_when_down_or_empty` kijkt nu ook dat de rotors van een neergevallen drone stilstaan. In de showcase
      (86, "Security drones") staat erbij waar je op moet letten; LEESMIJ.md: "Zijn rotors" bij de security drones.

**Mijn invulling**: alleen de security drones (die horen bij de robothoofdstukken). De drones van je arsenaal en de
raiddrones hebben al geluid bij vertrekken en terugkeren, maar zoemen niet terwijl ze bij je rondvliegen; zeg het als
die het ook moeten krijgen. Bij aan- en uitzetten hoort een drone nog de servo's van een robot.

- [x] **Afronding** — 295 tests: 292 groen, de 3 rode van hoofdstuk 14 stap 2 (jouw stroomwaarden). Luisteren kan ik
      niet, dus ik heb gemeten: de naad van de lus is gladder dan die van het laadzoemen en de rayshields, en in het spel
      is hij ongeveer even zacht als het laadzoemen. De showcase-functie 86 ("Security drones") heb ik twee keer laten
      lopen, de tweede keer met een tijdelijke logregel: de geluidsmotor speelde de rotors van Drone 1 en Drone 2, en die
      van Drone 2 een tweede keer nadat hij geladen had en weer opsteeg (precies zoals bedoeld; de regel is er weer uit).
      Geen fouten in het spellog.

### 31. Uit je showcase-notities (19 oktober)

Jouw notities in `showcase-notities.md` (showcase van 19 oktober, 08:10):
- bij **73. Security OS desktop**: "als je de security os opent ... je slaat op wat er vorige keer opstond tenzij de pc
  wordt uitgezet met de powerknop in de gui"; "bij devices moet er een lijn tussen elke device staan, soort van tabel
  ... de online status en knop moet naast elkaar staan in plaats van onder"; "de robots gui moet beter, zorg dat het ook
  meer in tabelvorm is en dat de knoppen naast elkaar staan";
- bij **74. Security cameras**: "Ik wil fysieke cameraschermen, dit is een multiblock: 1x1 is een normaal scherm, plaats
  je er 2 naast elkaar wordt het 2x1, ... 2x2 en zo voort. Rechtsklik op de monitor en je komt op een GUI van het hele
  scherm, hier kies je welke kamer waar en hoe groot staat";
- bij **76. Logic: names, sums and loops**: "zorg dat string interpolation ook werkt ... `$"dit is de {aantal} test"`";
- bij **82. Guard robots**: "zorg dat de knoppen mooi naast elkaar staan" en "zorg ook dat je sensoren kunt uitlezen of
  activeren of dat je de laser kunt laten schieten";
- bij **85. Robot senses and weapons**: "zorg dat je het schieten ook kunt programmeren";
- bij **87. Training**: "lethal rounds werken niet, je hebt altijd de lives; als je lethal gebruikt moet je geen levens
  kunnen instellen".

**Jouw antwoorden** (19 oktober): sensoren en de laser bedien je in je programma: `read(pin)` geeft de info van die pin
als het kan, en `write(pin, true/false)` zet die pin aan of uit als het kan. En bij het schieten: **richten op wat jij
kiest**.

**Mijn invulling** (zeg het als je het anders wil):
- **Lethal**: bij Lethal verdwijnt Lives uit het instelscherm ("until you die"). Een treffer doet echte schade en telt mee,
  maar kost geen leven; de training stopt als je doodgaat (of het doel haalt, de tijd om is, of hij gestopt wordt). In je
  diagrammen is `lives` bij Lethal 1 zolang je leeft. De app Training toont bij Lethal je gezondheid.
- **`$"..."`**: tussen `{` en `}` mag een naam of een berekening (`{count + 1}`, `{card.level}`); `{{` en `}}` zijn een
  gewone `{` en `}`. Alleen `$"..."`, zoals jij het schreef.
- **Tabellen**: Devices krijgt per apparaat één rij (naam, plek, toestand, knop) met lijntjes ertussen. Robots ook één
  rij per robot, maar alle knoppen van elke robot passen niet naast elkaar in het venster (op 1280 x 720 is het 340
  breed): je klikt een robot aan, en zijn knoppen staan onderaan naast elkaar.
- **De desktop**: zoals een echte pc onthoudt de kern één desktop (welke vensters waar open staan) voor iedereen die hem
  gebruikt, ook op afstand en na een herstart van de server. De aan/uit-knop zit rechts in de taakbalk; uit = de volgende
  keer begint hij met alleen Status. Je basis blijft gewoon beveiligd.
- **Modules aan en uit**: elke module heeft een aan/uit-stand. Sensoren (Scan, Distance, Face Recognition, Pathfinding,
  Solar Panel, Camera) beginnen aan; wapens (Laser, Gun) uit. Een wapen dat aan staat, schiet zodra het klaar is op zijn
  doel, tot je het uitzet. Een sensor die uit staat, ziet niets.
- **Richten**: `aim(x, y, z)` richt de wapens op die plek (bijvoorbeeld `aim(order.x, order.y, order.z)`), `aim()` weer
  op wie zijn Scan ziet.
- **De schermmuur**: een nieuw blok **Camera Monitor**; monitoren naast en op elkaar (dezelfde kant op) worden samen één
  scherm, zolang het een rechthoek is (tot 8 x 6). Rechtsklik (wie hem mag instellen): het hele scherm als rooster, je
  camera's ernaast (ook robots en drones met een camera); kies een camera en sleep een vak op het rooster. Hij moet in het
  gebied van je kern staan en heeft stroom nodig, zoals een camera.

Ik bouw het in deze volgorde, één stap per commit:

- [x] **1. Lethal zonder levens** — wat er misging: ook bij Lethal kostte elke treffer een leven, en met 1 leven (zo
      begint het blok) stopte de training al bij de eerste treffer; je ging dus nooit echt dood. Nu telt alleen
      *Training shots* levens (`Training.Mode.usesLives`). Bij Lethal doet een treffer echte schade en telt hij mee (in
      het rapport en voor `when trainee_hit:`), maar kost hij geen leven, ook niet als Lives op 1 staat; de training
      stopt als je doodgaat ("Senne died after 1:12 (3 hits: Guard 7 x2, Guard 8 x1)"), het doel haalt, de tijd om is of
      iemand hem stopt. `lives` is bij Lethal 1 zolang je leeft en 0 als je dood bent. Het instelscherm toont bij Lethal
      "none: it lasts until you die" zonder knoppen - en + (wat je bij Lives koos, blijft bewaard voor Training shots;
      screenshot `tr10_lethal_start_screen`), de app Training je gezondheid ("health 14/20") in plaats van levens, en het
      logboek "(lethal, until death)". Op Peaceful doen robots en torentjes geen schade (net als monsters): daar telt een
      lethal schot dus niet; dat staat in LEESMIJ.md. De test `lethal_training_hurts_and_ends` is nu
      `lethal_training_lasts_until_death` (twee treffers, de training loopt door; een dodelijk schot, dan voorbij met
      DIED), en `training_start_block_only_for_trusted` kijkt dat een lethal training met Lives 3 toch 1 leven heeft.
- [x] **2. Tekst met namen erin: `$"..."`** — `say($"This is test {count}")` zegt "This is test 3". Tussen `{` en `}` mag
      een naam, een gegeven, een berekening of tekst staan (`{count + 1}`, `{card.level}`, `{card.name + "!"}`); een getal
      zonder overbodige nullen, `true`/`false` als woord; `{{` en `}}` zijn gewone haakjes. Het werkt overal waar een
      waarde mag (een bericht, `naam = ...`, een `if`), niet als naam van een apparaat. Binnenin: een nieuw stukje
      `TEMPLATE` in de lexer (hij gaat dicht bij het aanhalingsteken dat niet in de code staat), en `Expr.Template` met de
      stukjes op volgorde; de code tussen de haakjes wordt streng nagekeken zoals de rest van de regel ("count has no value
      yet", "card.level only works inside when keycard(...)"). Nieuwe fouten: "A { in $"..." is missing its }", "Something
      is missing between { and }", "A } in $"..." needs a { before it (write }} for a } in the text)". De editor kleurt de
      tekst groen, de haakjes roze en de code ertussen in zijn eigen kleuren; Ctrl+Spatie stelt tussen `{` en `}` je
      namen voor; de Help heeft een regel bij NAMES. In de screenshot `l12` en in de showcase ("Logic: names, sums and
      loops": `log($"Blink {i} of 3")`). LEESMIJ.md: "Tekst met namen erin". Twee nieuwe tests (`logic_text_with_names`,
      `logic_text_with_names_run`).
- [x] **3. Devices als tabel** (`e4c0786`, de vakjes hier bleven toen leeg) — per apparaat één regel: naam, plek, toestand
      en rechts de knop, met lijntjes ertussen en de kopjes NAME, WHERE en STATUS.
- [x] **4. Robots als tabel** (`e4c0786`) — per robot één regel (naam, soort, toestand, batterij, programma, squad); klik er
      een aan, en zijn knoppen staan onderaan naast elkaar.
- [x] **5. De desktop onthoudt wat open stond, met een aan/uit-knop** (`e4c0786`) — sluit je de desktop, dan onthoudt de kern
      welke vensters waar stonden; **Shut down** rechts in de taakbalk vergeet ze, en de volgende keer begint hij met alleen
      Status. Test: `os_desktop_remembers_its_windows`.
- [x] **6. Modules aan en uit met `write(pin, true/false)`** — elke module heeft nu een aan/uit-stand per pin
      (`BotPins.isOn`). Sensoren beginnen aan, wapens uit; opnieuw opstarten zet alles terug. Uit betekent: **Scan** ziet
      niemand, **Distance** zegt 99, **Face Recognition** herkent niemand en zijn filter telt niet, **Pathfinding** laat geen
      nieuwe `goto` meer toe (`robot.blocked`, en een regel in het logboek), **Solar Panel** zegt 0 en laadt niet, **Camera**
      geeft geen beeld. `write(facePin, "strangers")` kiest het filter (en zet hem aan). Een **wapen** dat aan staat, schiet
      zodra het klaar is, tot je het uitzet (elk schot kost zoals altijd batterij of een kogel; `write(pin, true)` is dus
      niet meer één schot). Tekst of een getal naar een andere module zegt in het logboek "write(...) takes true or false
      here". **Mijn invulling** (zeg het als je het anders wil): een wapen met een Scan die aan staat, **wacht** tot die
      iemand ziet (het schoot vroeger dan recht vooruit, en dan kon je ook je vriend raken); zonder Scan, of met de Scan uit,
      schiet het recht vooruit. Biebia's drones gebruiken het ook: hun programma zet de laser één keer aan en schiet dan op
      elke vreemde die ze zien. Oude programma's blijven werken. **Tests**: `robot_weapon_stays_on_until_switched_off`,
      `robot_modules_switch_on_and_off`, `robot_with_a_scan_waits_for_someone_to_shoot`; `robot_laser_and_gun_shoot` en
      `robot_shots_stop_at_walls_domes_and_pvp` zijn omgezet.
- [x] **7. Richten: `aim(x, y, z)`**, en de showcase laat de code van de schietende robots zien — een nieuw woord in de taal
      (voor een robot en een drone; ook in de Help, Ctrl+Spatie en de blokjes). `aim(x, y, z)` richt zijn wapens op het midden
      van dat blok, `aim()` weer op wie zijn Scan ziet. Zijn hoofd draait erheen (daar kijkt zijn Scan dan ook), zijn lijf als
      hij stilstaat; **vooruit blijft vooruit**, zodat een `forward(3)` niet ineens een andere kant op gaat. Hij raakt het
      eerste levende wezen op de lijn (een muur en een schildkoepel houden het tegen), en het schot gaat zo ver als een wapen
      schiet, ook voorbij de plek. In `BotShot` is het schot opgesplitst: `fire` (op een doelwit of recht vooruit), `fireAt`
      (de lijn naar een plek), `along` en `shoot`. **Showcase 85** (Robot senses and weapons): de twee robots zetten hun
      wapen aan met `write(pin, true)`, een derde robot krijgt een opdracht met een plek en draait zich naar de husk links
      van hem, en op de desktop staat zijn code in de app Logic. **Test**: `robot_aims_at_a_place`, en in
      `logic_robot_words` de foutmeldingen ("aim needs 0 or 3 values (x, y, z)") en code > blokjes > code. 371 tests, allemaal
      groen. **Screenshot** `-Ponly=robots`: `rb27_robot_aim`.
- [x] **8. De Camera Monitor en de schermmuur** — een nieuw blok in de tab Security, na de Security Camera: een plat scherm van
      3 pixels dik tegen de achterkant van zijn blok (recept: 4 ijzer, 3 zwarte ruiten, een chip en redstone geven er 4). Klik je op een muur,
      dan kijkt het van die muur af. Monitoren die tegen elkaar staan en dezelfde kant op kijken, zijn samen één scherm zolang
      het een rechthoek is van hooguit 8 x 6 (`security/wall/CameraWall`); de randjes gaan weg waar er een monitor tegenaan zit
      (een multipart-blockstate). **Veranderd sinds het plan**: het blok hoort nu bij het netwerk van hoofdstuk 38. Het heeft een
      **netwerkkabel** (of PoE) naar de kern nodig en stroom (`[power] cameraMonitorUpkeep`, 1 per monitor per twee seconden);
      monitoren geven allebei aan elkaar door, dus één kabel is genoeg voor een heel scherm. Een EMP-puls legt het plat. In
      **Devices** een groep CAMERA WALLS met "online", "no power", "not a rectangle" of "too big". **Mijn invulling**: is het geen
      rechthoek, dan is het hele scherm zwart en zegt een rechtsklik wat er mis is (geen losse schermpjes).
- [x] **9. Het instelscherm van de schermmuur** — rechtsklik: links het hele scherm als rooster met één vak per monitor, rechts
      je camera's (met ROBOT of DRONE erbij). Kies een camera en sleep een vak; rechtsklik op een vak haalt het weg; **Clear** en
      **Done**. Elk vak toont meteen zijn live beeld, en elke verandering gaat meteen naar de server, die alles zelf nakijkt: wat
      buiten het scherm valt, over een ander vak ligt of geen camera van deze kern is, valt af. Instellen mag wie alle monitoren
      van het scherm mag instellen (`BaseTrust`). **Mijn invulling**: een vak is minstens één monitor (geen halve); een vak
      staat in de monitor van zijn linkerbovenhoek, dus een monitor weghalen kost alleen het vak dat daar begon.
- [x] **10. Live beelden op de schermmuur** — `client/os/camera/CameraWallRenderer` tekent de beelden op de monitoren, met
      dezelfde beelden als de app Cameras (`CameraFeeds` en zijn planner: alleen wat in beeld is, een paar keer per seconde, binnen
      een tijdbudget per frame). Met de naam, een knipperend REC, scanlijnen en hoekjes; zonder beeld ruis en "NO SIGNAL" of "OUT
      OF RANGE". Een beeld wordt niet uitgerekt: wat niet past, wordt afgesneden (`CameraFeeds.crop`). Vanaf 4 monitoren krijgt
      een vak het scherpe beeld. Een camera die naar het scherm kijkt, ziet geen beelden op het scherm (anders tekende een beeld in
      zichzelf). Een robot op het scherm weet dat er meegekeken wordt zolang er iemand binnen 32 blokken staat. **Tests**
      (`gametest/CameraWallGameTests`): `camera_monitors_merge_into_a_rectangle`, `camera_wall_needs_network_and_power`,
      `camera_wall_layout_is_checked_by_the_server`, `camera_wall_shows_a_robot`. 375 tests, allemaal groen. **Screenshots**
      `-Ponly=camerawall`: `cw01` tot `cw09`. Wat ik daarop zag en rechtzette: de namen waren half weg (de achtergrond van de tekst
      lag op dezelfde diepte als de letters), en de hint in het instelscherm paste niet. **Showcase 105** (Camera wall), achteraan.
      LEESMIJ: "De schermmuur".

### 41. Opgeruimd: de notities van alle showcases (31 oktober)

Jouw vraag: "fix de notities van de showcases, als die fix al is gedaan dan mag je dat ook uit de notitie halen zodat ik het
volgende keer niet weer vraag". Ik heb elke notitie in `showcase-notities.md` nagekeken in de code (de vakjes van hoofdstuk 31
waren niet bijgewerkt: stap 3 tot en met 5 waren al gebouwd in `e4c0786`). Wat al af was, en wat ik nu bouwde, is uit de notities
gehaald.

- [x] **1. De laserbouten verdwijnen** (showcase 100: "zorg dat de afgeschoten bullets verdwijnen en niet zoals pijlen in de
      grond blijven zitten") — de staart van een bout werd gerekend vanaf de kop, en de kop stopt bij de inslag. Daardoor kwam de
      staart er nooit en bleef de bout voor altijd staan. Nu vliegt de staart door tot in de inslag (`weapons/BoltFlight`).
      **Test**: `laser_bolts_disappear_at_the_impact`.
- [x] **2. Stap 6 tot en met 10 van hoofdstuk 31**: modules aan en uit, `aim(x, y, z)` en de schermmuur (showcase 74, 82 en 85).
- [x] **3. Wat al af was, en dus ook weg is uit de notities** — nagekeken in de code, en waar het iets te zien gaf op screenshots:
      **61** "hoe meer range hoe meer de light block verbruikt" (`ArcLampBlockEntity.powerPerTick`: het verbruik gaat mee met het
      lichtniveau, en daar hangt het bereik aan; hoofdstuk 13, test in `LightGameTests`); **34** groepen alleen op de desktop,
      **76** `for i in range(3)` in plaats van `repeat` en `forever`, en **83** robotonderdelen stapelen niet (hoofdstuk 28);
      **83** de kern maakt geen drones (hoofdstuk 25, `225f0ce`); **73** de desktop onthoudt zijn vensters met Status als begin en
      Shut down, Devices en Robots als tabel, en **82** de knoppen naast elkaar (`e4c0786`); **76** `$"dit is de {aantal} test"`
      en **87** Lethal zonder levens (hoofdstuk 31, stap 1 en 2).

### 32. Keycards in groepen in plaats van niveaus (25 oktober)

Jouw wens: "keycards groups ipv levels, je kunt groepen toevoegen en aan die groep keycards adden zodat je in code
vb kunt doen card.group == 'security' zo kunnen alle kaarten van groep security hier in."

**Jouw antwoorden** (25 oktober):
- Een kaart kan in **meerdere groepen** zitten, en je kunt **groepen in groepen** steken. In code wordt het daarom
  `card.in_group('security')` en niet `card.group == 'security'` (een kaart kan in meer dan één groep zitten): true als
  de kaart in "security" zit, of in een groep die (ergens) in "security" zit.
- **Hacken**: de hacker kiest een groep. Hoe moeilijk dat is, stel je niet in: het stroomcircuit is voortaan altijd zo
  moeilijk als niveau 5 nu (8 x 6, 40 seconden, 3 ALARM-punten).
- **Kleur**: kies je per kaart bij het schrijven, los van de groep.
- **Scherm**: in de app Keycards. Links je groepen, rechts de kaarten van de gekozen groep, en je sleept een kaart op een
  groep om hem erin te zetten.

**Mijn invulling** (zeg het als je het anders wil):
- **Niveaus zijn helemaal weg.** Schrijf je toch `card.level` (of `card.group`), dan zegt de controle: "card.level is
  gone: keycards are in groups now. Use card.in_group("security")".
- `card.in_group(...)` is alleen true voor een **geldige** kaart (van dit netwerk en niet geblokkeerd). Zo is
  `if card.in_group("security"): accept()` op zichzelf al veilig; `card.valid` blijft gewoon bestaan. Hoofdletters maken
  niet uit ("Security" = "security"), net als bij de namen van je apparaten. Het werkt onder `when keycard(...)` (en in
  een def die je daar gebruikt), zoals de andere card.-gegevens. Tussen de haakjes mag ook een naam of `$"..."`.
- **De groepen staan in de kern, niet op de kaart.** Zet je een kaart in een andere groep, dan geldt dat meteen, ook als
  iemand hem al bij zich heeft. Op de kaart zelf staan zijn naam, nummer, kleur en netwerk.
- **Groepen**: een groep zit in hooguit één andere groep (een boom, zoals bij je relais), zonder rondjes. Hooguit 32
  groepen per netwerk, een naam van hooguit 24 tekens zonder aanhalingstekens. Gooi je een groep weg, dan schuift wat
  erin zat (groepen en kaarten) één laag omhoog, net als bij je relais: `card.in_group("staff")` geeft dan voor die
  kaarten nog hetzelfde antwoord. Hernoemen kan; de app zegt dan in het oranje "Change it in your diagrams too."
- **Acht kleuren**: wit, groen, blauw, paars en goud (de kleuren van nu), en nieuw rood, oranje en zwart.
- **Oude werelden**: een kaart met een niveau houdt zijn kleur (niveau 1 = wit ... niveau 5 = goud) en komt in de groep
  "level 1" tot en met "level 5". Die groepen zitten in elkaar ("level 5" in "level 4" in "level 3" ...), dus
  `card.in_group("level 3")` doet precies wat `card.level >= 3` deed. Een oud diagram met `card.level` werkt pas weer
  als je het aanpast: tot dan blijft de deur dicht, en het logboek zegt waarom.
- **De app Keycards** wordt breder. Bovenaan schrijf je een kaart: naam, kleur (acht vakjes) en Write card; de nieuwe
  kaart komt in de groep die links gekozen is. Links staan "All cards", je groepen als boom en "No group". Rechts staan
  de kaarten van wat links gekozen is (bij "All cards" met hun groepen erachter), elk met Block/Unblock, en in een
  groep ook Remove (uit die groep). Sleep een groep op een groep om hem erin te steken, of op "All cards" om hem
  bovenaan te zetten. Onder de groepen: New, Rename en Delete (Delete vraagt eerst "Sure?"). Wie een kaart schreef, zie
  je als je er met de muis op staat.
- **Het logboek** toont de groepen van een gelezen kaart: "Senne used card #1001 (security, lab) at Lab door".
- **Hacken**: de hacker ziet de namen van de groepen van dat netwerk. Heeft het netwerk geen groepen, dan maakt hij een
  kaart zonder groep (`card.valid` is dan true, `card.in_group(...)` false). In de config `[bypass]` wordt elk rijtje
  van vijf (per niveau) één getal.
- **Zakkenrollen**: de kaart die je pakt, toont zijn naam en kleur (geen niveau meer).
- **Een SSD die verbrandt** neemt de groepen mee, net als de kaarten; een backup en een opgepakte kern bewaren ze.

Ik bouw het in deze volgorde, één stap per commit:

- [x] **1. Groepen en kleuren in de kern, `card.in_group(...)` in de taal, en hacken met een groep** (het hacken zat
      eerst in een aparte stap, maar zonder niveaus kon het niet blijven zoals het was). **De kern** (`LogicStore`): elke
      kaart heeft een kleur (1-8) en de groepen waar hij zelf in zit; de groepen zijn een boom (per groep: in welke groep
      hij zit). Groepen maken, in een andere groep steken (geen rondjes), hernoemen (de kaarten en groepen erin gaan mee),
      weggooien (wat erin zat schuift een laag omhoog), en een kaart in of uit een groep zetten. Opslaan, een backup, een
      verbrande SSD en een opgepakte kern nemen de groepen mee. **De kaart zelf** (het item) heeft een kleur in plaats van
      een niveau, en de tooltip zegt "Card #1001". Drie nieuwe plaatjes (rood, oranje, zwart; de vijf oude zijn hetzelfde
      gebleven). **De taal**: `card.in_group("security")` (ook met `'security'`, een naam of `$"..."` tussen de haakjes)
      werkt onder `when keycard(...)` in een serverdiagram, en is alleen true voor een geldige kaart. `card.level` en
      `card.group` geven de foutmelding met `card.in_group`. Ctrl+Spatie stelt `card.in_group("...")` voor, en tussen de
      aanhalingstekens je groepen; de Help en het voorbeeld van een nieuw diagram (`if card.valid:`) zijn bijgewerkt.
      **Het logboek**: "Senne used card #1001 (security, lab) at Lab door", of "(no group)", of "(not valid here)". **Oude
      werelden**: kaarten met een niveau komen in "level 1" tot het hoogste niveau (in elkaar), en houden hun kleur.
      **Hacken**: de hacker ziet de groepen van dat netwerk als een boom (bij meer dan 7 scrollt hij; toetsen 1-9) en kiest
      er één; heeft het netwerk geen groepen, dan "A card without a group". De server kijkt na of die groep bestaat. Het
      raster is altijd 8 x 6 met 40 seconden en 3 ALARM-punten; in `[bypass]` staat nu één getal per instelling. Bovenaan
      het raster staat "SECURITY CARD", en bij succes "ACCESS: the reader read a card of "security"." **Zakkenrollen**: de
      kaart die je pakt, toont zijn kleur, naam en nummer. **De app Keycards** kiest in deze stap alleen een kleur
      (Colour - 3 +) in plaats van een niveau; de groepen komen in stap 2. Vier nieuwe tests (`KeycardGroupGameTests`: de
      boom, opslaan en backups, oude niveaus, en de taal); `logic_keycard_opens_vault` gebruikt nu groepen in groepen
      ("night shift" in "security" in "staff") en ook een geblokkeerde kaart, en de hacktests kiezen een groep. 301 tests:
      298 groen, de 3 rode van hoofdstuk 14 stap 2.
- [x] **2. De app Keycards met groepen links** — het venster is breder (396 x 200, past nog net op 1280 x 720).
      Bovenaan schrijf je een kaart: **Name**, **Colour** (acht vakjes met een randje, zodat je zwart ook ziet; wijs je
      er een aan, dan zie je zijn naam) en **Write card**, en eronder "The new card goes into: lab" (de groep die links
      gekozen is, of "no group"). **Links** staan "All cards", je groepen als een boom met mapjes (een groep in een
      groep staat eronder, wat naar rechts) en "No group", elk met hoeveel kaarten er zelf in zitten. **Rechts** de
      kaarten van wat links gekozen is: nummer, kleur, naam, groepen, en de knoppen naast elkaar (**Block**/**Unblock**,
      bij een geblokkeerde kaart **Delete**, en als links een groep gekozen is **Remove**: uit die groep). Bij een
      groep zegt de kop ook hoeveel kaarten er via groepen erin meetellen ("IN SECURITY (1, +1 inside)"). **Slepen**:
      een kaart op een groep (groen = mag, rood = zit er al in), een groep op een groep, of op "All cards" om hem
      bovenaan te zetten (rood met "Can't: ... that would make a loop" als het een rondje zou maken). Onderaan **New**
      (in de gekozen groep), **Rename** (met in het oranje "Change it in your diagrams too.") en **Delete** (eerst
      "Sure?"); New en Rename openen daar een naamvak met OK en Cancel. Wijs je een kaart aan, dan zie je wie hem schreef.
      "Delete blocked" staat bij All cards. De server kijkt elke opdracht na (naam, bestaat al, 32 groepen, rondje) en
      zet hem in het logboek ("Senne put keycard #1001 in 'lab'"). Eén nieuwe test (`keycard_groups_on_the_desktop`).
      Screenshots `-Ponly=keycards`: `y05` (een kaart schrijven in "lab"), `y06` (alle kaarten met hun groepen),
      `y061` ("security" gekozen), `y062` (New), `y063` (Rename), `y07` en `y08` (Delete blocked). Ook gerepareerd: in
      de screenshot van het zakkenrollen (`bp19`) was de gestolen kaart grijs met BLANK; nu blauw met "#1001". 302 tests:
      299 groen, de 3 rode van hoofdstuk 14 stap 2.
- [x] **3. Showcase, screenshots en LEESMIJ** — **LEESMIJ.md**: de app Keycards met groepen (slepen, Remove, New,
      Rename, Delete, oude kaarten in "level 1" tot "level 5"), `card.in_group("...")` bij wat je kunt nakijken (ook
      dat het alleen voor een geldige kaart waar is), de voorbeelden zonder `card.level`, en het hacken met een groep
      (altijd 8 x 6, 40 seconden, 3 ALARM). **De showcase** ("Server racks and Logic", geen nieuwe functie, zodat de
      nummers van je notities blijven kloppen): het diagram van het lab vraagt `card.in_group("lab")` (lab en guest
      zitten in staff), en op de desktop komt eerst de app Keycards met "lab" gekozen, dan de app Logic. Bij het hacken
      kiest het circuit vanzelf de groep lab. Screenshots gecontroleerd: `-Ponly=keycards,bypass,logic,lab`. De test
      van de taal kijkt nu ook naar Ctrl+Spatie: na `if` staat `card.in_group("...")` (niet onder `every`), en tussen
      de aanhalingstekens je groepen. 302 tests: 299 groen, de 3 rode van hoofdstuk 14 stap 2.

### 33. Een laserzender hacken: draden doorknippen (25 oktober)

Jouw wens: "zorg dat je ook een laseremitter kunt hacken door wires door te knippen, dit is een 3de manier van
inbreek minigame". Ik deed voorstellen, jij koos.

**Jouw antwoorden** (25 oktober):
- **De minigame: draden volgen (een kluwen).** Links de stekkers, rechts waar elke draad heen gaat: één draad naar de
  LENS, de rest naar andere dingen. Volg de draad van de LENS terug en knip hem door.
- **Gelukt: de hele poort blijft uit tot de eigenaar hem herstelt.** Herstellen is een knop die **iets kost**.
- **Mislukt: een stroomschok** (schade en vonken), **de poort 5 minuten op slot**, en **het alarm** (alleen bij
  mislukken, zoals bij de lezer en het paneel; gelukt is stil).
- **Gereedschap: dezelfde Bypass Kit** (één van de 16 gebruiken per hack).
- **Elke laserzender** mag, ook een zonder beveiligingskern.
- **Hoe moeilijk groeit met de poort**: meer zenders = meer draden en meer tijd.

**Mijn invulling** (zeg het als je het anders wil):
- **Herstellen kost 1 Computer Chip** per poort (in creative niets). De knop **Repair wires** staat in het scherm van
  de zender (sneak + klik, voor wie de zender neerzette of vertrouwd is in de basis, dezelfde regel als altijd).
- **Draden**: het aantal zenders in de poort + 3, van 4 tot 8. **Tijd**: 40 seconden bij 4 draden, 5 seconden meer per
  draad, 60 bij 8. De klok loopt meteen.
- **Het scherm** (`client/bypass/WireScreen`): binnenin de zender is het donker. Alleen waar je muis is, licht het op
  (een **zaklamp**, koperkleurig). Kiezen doe je **op de stekker links** (klik, of de toetsen 1 tot 8); dat laat niet
  zien waar de draad heen gaat. Dan **CUT** (of Enter). Je kunt maar één keer knippen. Na het knippen zie je de hele
  draad: groen (goed) of rood (fout, met waar hij heen ging: "ZAP! That wire went to the LAMP.").
- **Elke verkeerde draad is fout**; de namen rechts (ALARM, LAMP, FAN, SENSOR, GROUND, BUZZER, CAMERA) zijn sfeer.
- **Te laat** telt als mislukt (op slot en alarm), maar zonder schok: je knipte niets. Esc, weglopen of doodgaan
  stopt zonder iets.
- **Het slot** geldt voor de hele poort en voor iedereen, en staat in de zenders zelf (blijft na een herstart). Een
  poort die op slot zit of al doorgeknipt is, laat zich niet hacken, en dat kost dan niets.
- **De schok**: 4 schade (2 hartjes), via dezelfde weg als de laserwand (telt dus ook mee in een training).
- **Het alarm**: "Someone is tampering with a laser gate at 12 64 -30", met je naam erachter; de beveiliging jaagt op je.
  Een zender zonder kern heeft niemand om te waarschuwen: dan alleen het geluid.
- **Van buiten zie je het**: een doorgeknipte zender vonkt af en toe en geeft rook, zijn scherm zegt rood **WIRES CUT**,
  en de app Devices zegt "cut wires" bij die poort.
- **Let op**: een zender aan de overkant die terugkijkt, is een eigen laserpoort (alleen zenders die tegen elkaar aan
  zitten horen bij elkaar). Die houdt de wand dus gewoon aan; een inbreker moet ze allebei doorknippen.
- Alle getallen staan in de config onder `[bypass]`: `laserWiresMin`/`laserWiresMax`, `laserSecondsMin`/`laserSecondsMax`,
  `laserLockSeconds`, `laserShockDamage` en `laserRepairChips`.

Ik bouwde het in deze volgorde:

- [x] **1. De kluwen, de doorgeknipte poort, het hacken en herstellen** (de stappen 1 tot 4 van het plan in één keer:
      ze werken alleen samen). **De kluwen** (`bypass/WireTangle`): een "ladderspel" van kolommen; in elke kolom
      kruisen een paar draden hun buurdraad (een X). Zo komt elke draad op een eigen plek uit, en gaat er precies één
      naar de LENS. De server stuurt alleen een getal; je spel en de server maken er dezelfde kluwen van, en je spel
      stuurt alleen welke stekker je knipt. De draad naar de LENS loopt nooit rechtdoor en kruist minstens 3 keer;
      vlechten van steeds dezelfde twee draden zijn zeldzaam. **De zender** (`LaserEmitterBlockEntity`) onthoudt of
      zijn draden doorgeknipt zijn en tot wanneer hij op slot zit; is één zender van de poort doorgeknipt, dan wil de
      hele poort niet meer aan (`LaserEmitterBlock.wanted`). Je spel krijgt te horen of hij doorgeknipt is (voor de
      vonken en het scherm). **Hacken** (`bypass/Bypass`, soort LASER): de kit werkt nu ook op een laserzender.
      **Herstellen**: `LaserActionPayload.Op.REPAIR` (de netwerkversie is nu 24). Drie nieuwe tests
      (`BypassGameTests`): honderden kluwens per aantal draden (eerlijk en altijd hetzelfde bij hetzelfde getal, en
      meer zenders = meer draden en tijd); goed knippen (de poort blijft uit, ook met redstone, na opslaan en laden, en
      met een zender erbij), nog eens hacken kost niets, en herstellen (een vreemde niet, zonder chip niet, met chips
      kost het er één, in creative gratis); de verkeerde draad (schok, op slot ook voor een ander, alarm met naam),
      weglopen (niets) en te laat (slot en alarm, geen schok). 305 tests: 302 groen, de 3 rode van hoofdstuk 14 stap 2
      (met de standaardwaarden uit de code allemaal groen).
- [x] **2. Showcase, screenshots en LEESMIJ** — **De showcase**: "Hacking and pickpocketing" (geen nieuwe functie, zodat
      de nummers van je notities blijven kloppen) eindigt nu met een laserpoort van twee zenders in het lab: de zaklamp
      volgt de draad van de LENS terug, de stekker wordt gekozen en geknipt, de wand verdwijnt en de zenders vonken; dan
      als eigenaar het scherm (WIRES CUT) en Repair wires, en de wand komt terug. **Screenshots** `-Ponly=bypass`:
      `bp21` (de poort voor de lezer), `bp22` (de kluwen in het donker), `bp23` (de zaklamp op de draad), `bp24` (de
      stekker gekozen), `bp25` (geknipt: de hele draad groen), `bp26` (de poort uit), `bp27` (het scherm met WIRES CUT en
      Repair wires), `bp28` (hersteld) en `bp29` (een verkeerde draad: ZAP, rood). **LEESMIJ.md**: "Inbreken" heeft een
      stuk over de laserzender, en bij Rayshield staat Repair wires.
- [x] **3. Kapot blijft kapot** — Ik vroeg: een eigenaar kon doorgeknipte zenders weghakken en terugzetten, en dan
      waren de draden gratis heel. Jouw antwoord: "de 2de moet inderdaad worden opgelost door te onthouden dat die laser
      kapot is". Nu valt een doorgeknipte zender eruit als een item dat dat onthoudt (`LASER_CUT`, net zoals de
      beveiligingskern zijn instellingen meeneemt), met in rood "Wires cut: this emitter is broken." en dat hij pas heel
      wordt met Repair wires. Zo'n zender stapelt niet met hele. Zet je hem weer neer, dan is hij nog kapot; zet je hem
      tegen een werkende poort, dan gaat die hele poort meteen uit (één kapotte zender is genoeg). Repair wires maakt de
      hele poort heel, en daarna valt hij er gewoon als een hele uit. Een zender die pas later tegen een doorgeknipte
      poort werd gezet, is zelf niet kapot: die valt er als een hele uit. (Je eerste vraag, de zender aan de overkant
      als eigen poort, liet ik zoals hij was.) Eén nieuwe test (`bypass_laser_stays_cut_when_mined`). De test van de
      kluwens rekent nu met vaste getallen, wat er ook in de config staat. 306 tests, allemaal groen.

### 34. De laserbrug (25 oktober)

Jouw wens: "Maak een nieuwe block laserbridge, dit is basically hetzelfde als een rayshield maar het doet geen
damage en is enkel horizontaal. Stel vragen waar nodig". Ik stelde vier vragen, jij koos.

**Jouw antwoorden** (25 oktober):
- **De hoogte**: "een heel klein beetje onder de rand, dus tussen rand en midden, dichter naar rand dan midden". De
  bovenkant van de plaat ligt nu 2 pixels (1/8 blok) onder de rand van het blok, even hoog als zielenzand. Je zet de
  zender in de rand van de vloer, en stapt zonder springen op de brug en er weer af.
- **Wie erop kan: iedereen** (spelers, dieren, monsters). Een val maak je zelf: zet hem uit met redstone terwijl er
  iemand op staat.
- **Los van de beveiliging**: de schakelaar "Lasers" van de beveiligingskern laat bruggen met rust, en de Bypass Kit
  werkt er niet op.
- **Een eigen look**: een plaat van licht in de gekozen kleur, met felle randen en een fijn raster; de zender heeft een
  brede spleet in plaats van een ronde lens, zodat je hem niet verwart met een rayshieldzender.

**Mijn invulling** (zeg het als je het anders wil):
- **Zoals de rayshield**: hij heeft stroom nodig (1 per tick per zender, en hij gaat pas aan als het net stroom heeft
  voor 5 seconden), gaat aan met redstone (of omgekeerd), zenders die tegen elkaar zitten zijn één brug (drie naast
  elkaar = drie breed, met één kabel en één hendel; ze geven de stroom aan elkaar door), een brugzender aan de overkant
  die terugkijkt sluit de brug af, en de brug schuift uit en in (één plaat per tick, van het eind terug naar de zender).
  Hetzelfde "vwoem" als de rayshield (een tikje hoger) en hetzelfde stille zoemen.
- **Neerzetten**: de brug gaat de kant op waar jij kijkt (altijd opzij, ook als je schuin naar beneden kijkt). Zo zet je
  hem in de rand van de vloer met je gezicht naar het gat. (De rayshieldzender kijkt juist naar jou: die zet je tegen
  een muur.) Op de bovenkant van de zender wijzen twee pijlpunten naar de kant van de brug.
- **Hoe lang**: tot 16 blokken, tot het eerste blok dat in de weg staat.
- **De randen**: aan elke kant waar geen brug meer ligt (opzij en aan het eind), niet tegen een zender of een andere
  plaat. Een brede brug heeft dus alleen randen aan de buitenkant.
- **Kruisingen**: een brug loopt niet over een brug die dwars loopt, maar stopt ertegen. Anders haalde hij bij het
  uitgaan een stuk van die andere brug mee weg (de rayshield heeft dat probleem wel).
- **Monsters en dieren** zien de plaat als vloer (net als zielenzand) en lopen er ook zelf overheen. Er spawnt niets op.
  Items blijven erop liggen en pijlen verbranden niet. Wie erover loopt, schopt geen stofwolkjes op (het is licht).
- **Kleur**: nieuwe bruggen zijn cyaan (een rayshield is blauw). Kleur en omkeren kies je in hetzelfde instelscherm als
  bij de laserzender (sneak + rechtsklik met een lege hand, voor wie hem neerzette of wie vertrouwd is in de basis),
  zonder de regel Wall.
- **Sterkte**: zo sterk als een ijzerblok (een stenen houweel of beter), niet zo sterk als de laserzender: die hoort bij
  de beveiliging, de brug niet. Om dezelfde reden staat de brug niet in de app Devices.
- **Recept**: een laserzender en een gladde stenen plaat (los in het raster) = een laserbrug. Je krijgt het recept
  zodra je een laserzender hebt.
- In de config: `[power] laserBridgeUpkeep` en `laserBridgeStartSeconds`, en `[shields] laserBridgeMaxLength`.

Ik bouwde het in één keer:

- [x] **1. De laserbrug** — **De zender** (`shield/LaserBridgeBlock`): kijkt alleen opzij (`HORIZONTAL_FACING`), werkt
      verder als de laserzender, maar kijkt niet naar de beveiligingskern. Hij zoekt zijn buren met dezelfde zoekfunctie
      als de laserzender (`LaserEmitterBlock.touching`). **De plaat** (`LaserBridgePlateBlock`): 1 pixel dik met de
      bovenkant op 14 pixels, onthoudt in welke richting de brug loopt (`AXIS`) en aan welke kanten er nog brug ligt
      (`NORTH`/`EAST`/`SOUTH`/`WEST`: daar komt geen rand). **De eigenaar** staat in `LaserBridgeBlockEntity`, en
      **het scherm** gaat via `LaserBridges` (dezelfde berichten als de laserzender; `client/shield/LaserEmitterScreen`
      kan nu ook een brug). `LaserAnimations` schuift nu ook brugplaten. **Het uiterlijk**: `laser_bridge_textures()` in
      tools/generate_textures.py (de bovenkant met pijlpunten, de voorkant, de spleet, en per kleur een plaat met
      raster en glans plus een rand) en `laser_bridge_models()` in tools/generate_block_models.py (de zender, en de
      plaat als "multipart": de plaat altijd, en een rand per kant waar de brug ophoudt). **Showcase**: functie 97
      "Laser bridge", achteraan zodat de nummers van je notities blijven kloppen: een gracht met drie zenders, de brug
      schuift uit, je loopt eroverheen, het scherm (paars), en de val in het water. **Screenshots** `-Ponly=bridge`:
      `br01` (de zenders uit, van boven), `br02` (de spleet aan de voorkant), `br03` (van opzij, met een armor stand op
      de brug), `br04` (van boven: randen alleen buiten), `br05` (op de brug), `br06` (paars) en `br07` (het scherm).
      Vijf nieuwe tests (`BridgeGameTests`): de brug schuift uit vanaf de zender tot het eerste blok, de plaat ligt net
      onder de rand, de randen kloppen, een armor stand blijft erop staan zonder schade en valt als de brug weggaat;
      neerzetten kijkt de kant op waar je keek (ook schuin naar beneden), alleen de eigenaar stelt hem in, en een zender
      ernaast neemt kleur en omkeren over; drie naast elkaar met één generator en één redstoneblok; zonder stroom niets;
      en met de lasers van de kern uit blijft een laserzender uit maar komt de brug gewoon, en de Bypass Kit doet niets.
      311 tests, allemaal groen.

### 35. Wapens: EMP-granaten en lasergeweren (25 oktober)

Jouw wens: "Zo'n robot is super OP dus om dit te counteren zijn er droid poppers zoals in Star Wars. Deze schakelen
al de elektrische dingen uit zoals robots maar ook turrets, drones, openliggende elektrische wires, ook wil ik nog
iets van laser achtige guns die zo wat futuristisch zijn, ze moeten goede models/animaties hebben zoals bij TACZ".
Ik stelde acht vragen, jij koos.

**Jouw antwoorden** (25 oktober):
- **De naam: EMP Grenade** (geen Star Wars-naam). Het oude item "E.M.P." (dat illusies stoort) heet daarom voortaan
  **Illusion Jammer**; het doet hetzelfde als vroeger.
- **Twee soorten** (jouw eigen antwoord: "de halve sticky one en de bol die rolt zoals bij Star Wars detonators"): de
  **Sticky EMP Grenade**, een halve bol die vastplakt op wat hij raakt, en de **EMP Grenade**, een bol die stuitert en
  rolt.
- **Tijdelijk uit**: alles in de buurt valt een tijdje uit en gaat daarna vanzelf weer aan. Robots en drones vallen neer
  zoals bij de Ion Blaster.
- **Wat uitvalt**: robots, turrets, drones en kabels die open liggen (een kabel in een muur is beschermd), en ook
  **camera's, keycardlezers en codepanelen**, en **het harnas en de bril** van spelers in de buurt (vliegen stopt, de
  HUD valt weg; ook bij jezelf als je te dichtbij staat). **Niet**: rayshields, laserbruggen, koepels en lampen.
- **Vier lasergeweren**: een **laserpistool** (één klik = één schot), een **lasergeweer** (automatisch: knop
  inhouden), een **lasersniper** (één zware straal, vizier dat inzoomt) en een **laser-shotgun** (een waaier stralen).
- **Munitie: energiecellen.** Een cel is het magazijn; leeg = herladen (cel eruit, nieuwe erin). Lege cellen laad je
  op in het Charging Station. En (jouw toevoeging) **cellen stapelen niet**: elke cel heeft zijn eigen lading.
- **Bediening zoals TACZ**: linkerklik schiet, rechterklik richt door het vizier, R herlaadt, een toets om het geweer
  te bekijken.
- **Stijl: rood-goud** zoals je Tech Suit, met een witte/cyaan gloed.
- **Later die avond** (tijdens het bouwen): "Maak de EMP granades ook allebei explosiebaar zoals de C4 maar net iets
  minder sterk". Ik gaf eerst de EMP-granaten zelf een klap; jij verbeterde: "Het is niet de bedoeling dat de EMP zelf
  damage aan de map doet, het is de bedoeling dat er een extra bom komt dat geen EMP is maar wel schade aan de map doet",
  en daarna: "kopieer de sticky en de bal EMP, hernoem de kopieën en maak er explosieven van". Dus nu **vier**: de EMP
  Grenade en de Sticky EMP Grenade (alleen een EMP, geen schade aan de map), en hun kopieën de **Frag Grenade** en de
  **Sticky Frag Grenade** (een klap zoals C4 maar iets zwakker, geen EMP). De namen koos ik.
- **En daarna**: "Maak voor de bal ook smokebom en flashbang maar geen sticky want dat is voor smoke en flash niet logisch".
  Vier vragen, jouw antwoorden: de **rook blokkeert het zicht van alles wat kijkt** (robots, turrets en drones zien niemand
  in of achter de rook en schieten er niet doorheen; een camera die in de rook kijkt ziet grijs); de **flashbang werkt
  zoals in CS** (een wit scherm dat wegtrekt, het sterkst als je ernaar kijkt, een piep in je oren; ook bij jezelf); de
  flashbang doet **niets met robots, turrets, drones en monsters** (alleen spelers); de rook is **grijs, maar met een
  kleurstof te kleuren**.
- **En op 26 oktober**: "voeg voor de EMP ook nog een remote getriggerde EMP toe, deze ziet er uit als een op de muur
  geplaatste EMP maar dan trigger je die met een item in de inventory, zo kun je bijvoorbeeld een hele powergrid
  uitdoen van op afstand wanneer jij wilt". Drie vragen, jouw antwoorden: een **gewone EMP-puls** (zoals een granaat:
  5 blokken, niet het hele net in één keer; wel zo: zet hem bij de kabels van je doelwit), een **eigen EMP-trigger**
  (niet de detonator van C4), en **eenmalig** (zoals C4).
- **Ook op 26 oktober**: "zorg dat de smoke van smokegranades niet door blokken kan gaan".
- **En nog op 26 oktober**: "haal de C4 weg, ik wil dat er nog enkel zo granaten bestaan; de plak-C4 met remote moet
  zijn zoals een halve sticky frag, maar dan ook met remote zoals de Remote EMP". Drie vragen, jouw antwoorden: een
  **eigen Frag Trigger** ("een nieuwe mooie 3D-model zoals de EMP trigger, en de oude C4-trigger moet weg"), bij een
  zone **alarm en schaar, zoals C4**, en zo sterk als een **Sticky Frag**.

**Mijn invulling** (zeg het als je het anders wil):
- **Gooien**: rechtsklik gooit. Met sneak gooi je zachtjes onderhands (de bol rolt dan over de vloer, zoals bij
  bowlen). Granaten stapelen tot 16.
- **De lont**: de sticky gaat 2 seconden na het vastplakken af (hij plakt ook op een robot, een drone of een speler en
  gaat met hem mee), de bol 3 seconden na het gooien. Ze piepen en knipperen, steeds sneller.
- **Hoe groot**: een bol van 5 blokken rond de granaat, 20 seconden uit. Nog eens raken begint de tijd opnieuw. De puls
  gaat door muren heen (behalve bij kabels in een muur), maar **een schildkoepel beschermt** wat erin staat tegen een
  granaat die buiten de koepel afgaat.
- **Wat er per ding gebeurt**: een guard robot of security drone valt neer zoals bij de Ion Blaster (in een training
  blijft hij liggen tot die voorbij is). Torendrones, raiddrones en je eigen drones zakken naar de grond en blijven daar
  stil liggen (ook een vervoerdrone: wie eraan hangt valt). Een turret laat zijn loop hangen en schiet niet. Een camera
  geeft "no signal". Een lezer of paneel doet niets ("The reader is dead"). Een open kabel geeft geen stroom door, dus
  alles erachter heeft even geen stroom. Bij een speler stopt het vliegen, de laser cannon en de raketten, en in de bril
  zie je ruis met "EMP" en hoeveel seconden nog; daarna start de bril opnieuw op (de opstart-animatie).
- **De geweren zelf** vallen niet uit (anders kun je de robot die je net platlegde niet afmaken).
- **Recepten**: EMP Grenade (2 stuks) = 4 ijzer, 2 redstone, 2 koper en een Computer Chip; Sticky = een EMP Grenade
  met een slijmbal. Geweren en cellen maak je aan de gewone werkbank (met Computer Chips).
- **De geweren**:

  | | schade | tempo | per cel | bereik | herladen |
  |---|---|---|---|---|---|
  | Laser Pistol | 6 | max 6,7 per seconde (per klik) | 20 schoten | 64 | 1,5 s |
  | Laser Rifle | 4,5 | 6,7 per seconde (inhouden) | 33 schoten | 96 | 2,2 s |
  | Laser Sniper | 24 | 1 per 1,25 s | 5 schoten | 192 | 3 s |
  | Laser Shotgun | 8 × 3,5 | 1 per 0,7 s | 8 schoten | 24 | 2,6 s |

  Een **headshot** doet 1,5 keer zoveel. Richten maakt je schoten nauwkeuriger (het pistool en het geweer zoomen een
  beetje in, de sniper 4 keer met een vizier over je scherm) en je loopt dan langzamer. Sprinten laat het geweer zakken;
  schieten stopt je sprint. Een koepel houdt de stralen tegen (zoals andere lasers). Spelers raak je alleen als PvP aan
  staat.
- **Een nieuw geweer is leeg**: druk R en hij pakt de volste cel uit je inventaris. De oude cel komt terug in je
  inventaris met wat er nog in zat (ook een lege, om op te laden). Een volle cel kost 2000 stroom in het Charging
  Station (in het vak van de bril).
- **Toetsen**: R herladen en H bekijken, alleen terwijl je een geweer vasthoudt; zonder geweer doen R en H wat ze
  altijd deden (markeren en de dreigingsscan). Je kunt ze veranderen in Opties > Besturing > I.M.T.
- **Wat je ziet**: de geweren zijn 3D-modellen van blokjes (zoals in Blockbench), met rood-gouden platen, donkere
  delen en strepen die cyaan/wit gloeien, ook in het donker. Op elk geweer zit een lichtbalkje dat laat zien hoe vol de
  cel is. In je eigen beeld zie je je twee armen het geweer vasthouden, met animaties voor pakken, schieten
  (terugslag, mondingsvlam), richten, herladen (cel eruit, nieuwe erin, opladen), bekijken en sprinten. Rechtsonder
  staat hoeveel schoten er nog in zitten en hoeveel cellen je bij je hebt, en het kruisje wordt groter als je minder
  nauwkeurig schiet. Geen extra mod nodig (geen TACZ, geen GeckoLib): alles zit in I.M.T zelf.
- In de config: een nieuw hoofdstuk `[weapons]`.

Zo bouw ik het:

- [x] **1. De EMP-puls** — `weapons/Emp`: een puls is een bol (midden, straal, tot wanneer, van wie). Wat stilstaat
      vraagt zelf of het in een puls ligt (`Emp.isOut`): de turret (`refreshOnline`: zijn loop hangt omlaag), de camera
      (`updateOnline`: geen beeld), de keycardlezer en het codepaneel (`Emp.deadDevice`: "Nothing happens: knocked out
      by an EMP", ook bij een gehackte kaart of gekraakte code), en `PowerGrid` volgt een open kabel in een puls niet
      (`Emp.cutsCable`; een kabel in een volledig blok wel). Wat beweegt krijgt een tijd mee (het attachment
      `EMP_UNTIL`, ook naar je spel gestuurd): robots en security drones vallen neer (`knockDown`, zoals de Ion
      Blaster), en je eigen drones, raiddrones en torendrones zakken naar de grond met hun lampjes uit en de rotors
      stil (een vervoerdrone laat los). Bij een speler doen het harnas (`TechSuitItem.isPowered`) en de bril
      (`ImtData.isOperational`, en de knoppen van de bril) niets; in de bril zie je ruis met "EMP DETECTED" en "I.M.T
      rebooting... 14s" (`client/hud/EmpStaticOverlay`), en daarna de opstart-animatie. Een schildkoepel beschermt wat
      erin staat. In je spel: een bol van blauw licht met bliksems (`client/weapons/EmpEffects`), en vonken op de open
      kabels zolang de puls duurt. Geluiden `emp_blast` en `emp_power_down`. Het oude item "E.M.P." heet nu **Illusion
      Jammer**. Config `[weapons]`: `empRadius` (5), `empSeconds` (20), en alvast de getallen van de granaten en
      geweren. Zes nieuwe tests (`WeaponGameTests`): robots en drones, turrets en camera's, open en ingemetselde
      kabels, harnas en bril, lezers en panelen, en de koepel. 318 tests, allemaal groen.
- [x] **2. De twee granaten** — `weapons/EmpGrenadeEntity` (de lont, piepjes die steeds sneller gaan, het knipperende
      lampje, en de puls), `RollingEmpGrenade` (stuitert tegen muren, de vloer en wezens, rolt verder over de grond en
      draait mee in je spel; gaat 3 seconden na het gooien af) en `StickyEmpGrenade` (plakt op de kant waar hij tegenaan
      botst, of op een wezen en gaat dan mee; gaat 2 seconden later af; valt als het blok of wezen weg is). `EmpGrenadeItem`:
      rechtsklik gooit, met sneak onderhands; stapelt tot 16. **Modellen**: een nieuw script
      `tools/generate_weapon_models.py` maakt het plaatje met materialen (`textures/item/weapons.png`: rood, goud, donker
      metaal, rubber, staal, gloeiend cyaan, ...) en alle wapenmodellen van doosjes, ook alvast de geweren en de
      energiecel. De granaten bestaan uit een lijf en een lampje (`client/weapons/WeaponModels`,
      `EmpGrenadeRenderer`). Geluiden `emp_grenade_beep`, `_stick` en `_bounce`. **Recepten**: 4 ijzer, 2 koper, 2 redstone
      en een Computer Chip = 2 EMP Grenades; een EMP Grenade met een slijmbal = een Sticky. **Screenshots**
      `-Ponly=grenades` (`gr01` tot `gr08`): in je hand, op de muur en de vloer, de bol van licht, alles uit, de robot
      neer, en de ruis in je bril. Vier nieuwe tests: plakken op een muur, meerijden op een robot, stuiteren en rollen, en
      gooien. 322 tests, allemaal groen.
- [x] **2b. De frag-granaten: ontploffen zoals C4, maar zwakker** (jouw wens, na je verbetering) — de **Frag Grenade**
      (bol) en de **Sticky Frag Grenade** zijn kopieën van de EMP-granaten: ze vliegen, stuiteren, plakken, piepen en
      knipperen net zo, maar zijn legergroen met een oranje band en rode lampjes, en als ze afgaan komt er geen EMP maar
      dezelfde klap als C4 (`C4Blast.blast`, nu gedeeld): eerst een klap voor wezens (zoals TNT; wie de granaat gooide is
      de dader, dus zonder PvP raak je geen spelers), dan breekpunten voor blokken. C4 heeft 10 breekpunten op 3,5
      blokken en een klap van 4; een frag-granaat 8 breekpunten op 3 blokken en een klap van 3 (`[weapons]
      grenadeBreach`, `grenadeBreachRadius`, `grenadeEntityPower`). Op een stenen muur blaast hij dus alleen het blok
      eruit waar hij op plakt (de blokken eromheen krijgen barsten), waar C4 er een paar meer uithaalt; obsidiaan houdt
      het. Een zone houdt hem niet tegen (net als C4) en het logboek van de zone schrijft "Frag grenade blast". De
      EMP-granaten doen géén schade aan de map. **Recepten**: 4 ijzer, 4 buskruit en een TNT = 2 Frag Grenades; een Frag
      Grenade met een slijmbal = een Sticky Frag Grenade.
- [x] **2c. De smokebom en de flashbang** (jouw wens, alleen als bal) — **De Smoke Grenade** (donker met een band in de
      kleur van zijn rook en een rooster bovenop): stuitert en rolt, begint na 2 seconden te sissen en maakt een wolk van
      4,5 blokken die 18 seconden blijft (2 seconden groeien, 3 seconden wegtrekken), en verdwijnt dan. Op de server houdt
      `weapons/Smoke` de wolken bij: robots, security drones, je eigen drones, raiddrones, torendrones en Raven-soldaten
      (via `hasLineOfSight`) en turrets (`canSee`) kijken er niet doorheen; van heel dichtbij (1,5 blok rook) nog net wel.
      In je spel blaast hij honderden dikke plukken rook (een eigen deeltje, `client/weapons/SmokeCloudParticle`, de
      rookplaatjes van een kampvuur maar 3 tot 4 blokken groot), en in het beeld van een bewakingscamera tekenen we de wolk
      als een dichte bol (`SmokeView.renderInFeed`): die ziet grijs. **Kleur**: een smokebom met een kleurstof (los in het
      raster) geeft een "Red Smoke Grenade" enzovoort, 16 recepten (`smoke_grenade_<kleur>.json`, het data component
      `smoke_color`). **De Flashbang** (stalen bol met een zwarte band en een gouden pin): gaat na 1,6 seconden af met een
      felle flits en een harde knal (`weapons/Flashbang`). Elke speler die de flits kan zien (geen muur ertussen, tot 20
      blokken) krijgt een wit scherm over alles heen dat even blijft en dan wegtrekt, een piep in zijn oren, en gedempt
      geluid (`client/weapons/FlashOverlay`): recht ernaar kijken = 5 seconden helemaal wit, opzij kijken ongeveer de
      helft, met je rug ernaartoe een beetje; verder dan 4 blokken minder. Ook jijzelf. Robots, turrets, drones en monsters
      merken niets (jouw keuze). Geen schade, geen gat. **Recepten**: 2 ijzer, 2 buskruit en kolen = 2 Smoke Grenades; 2
      ijzer, 2 buskruit en glowstone = 2 Flashbangs. Geluiden `smoke_grenade_hiss`, `flashbang_bang`, `flashbang_ring`.
      Config `[weapons]`: `smokeFuseSeconds`, `smokeSeconds`, `smokeRadius`, `smokeSeeThrough`, `flashFuseSeconds`,
      `flashRadius`, `flashSeconds`. **Screenshots** `-Ponly=grenades` nu tot `gr18`: de frag-granaten in je hand, een
      Sticky Frag op de muur, de klap en het gat, de grijze en rode rook (van buiten en van binnen), en het witte scherm.
      Acht nieuwe tests (rook in de rekensom, robots en turrets blind in de rook, een smokebom die rookt en verdwijnt,
      de flashbang naar wie kijkt, de flashbang zonder schade, gekleurde rook, de frag-sticky en de frag-bol), en de oude
      tests weer met "de robot ligt neer" na een EMP. 330 tests, allemaal groen.
- [x] **3. Energiecellen en de geweren op de server** — **De energiecel** (`weapons/EnergyCellItem`): stapelt niet,
      zijn lading (0 tot 100%) staat in het data component `cell_charge`, het streepje onder het item laat zien hoe vol
      hij is. Een nieuwe cel is leeg; het **Charging Station** laadt hem op in het vak van de bril, net zo snel als een
      bril (een volle cel kost 2000 stroom, `[weapons] cellChargeEnergy`). **De geweren** (`weapons/GunType`,
      `LaserGunItem`): de cel in het geweer staat in het component `gun_cell` (geen = er zit geen cel in); met een geweer
      in je hand breek je geen blokken en zwaai je niet. **Schieten** (`weapons/LaserGuns`, het bericht `GunFirePayload`
      met waar je precies keek): de server controleert of je een geweer vasthoudt, niet aan het herladen bent, niet te
      snel schiet (`gunInterval`, één tick speling) en genoeg in je cel hebt (anders een lege klik). Dan een rechte straal
      vanaf je ogen met spreiding (`gunSpread`, minder als je richt: `gunSpreadAiming`), tot `gunRange` blokken; muren en
      schildkoepels houden hem tegen; een schot boven de ogen min een kwart blok is een headshot (x1,5, `headshot`). De
      shotgun schiet 8 stralen; de schade per wezen telt op en komt in één klap (anders gooien 8 stralen hem 8 keer weg),
      en "even onkwetsbaar na een klap" telt niet (het geweer schiet sneller dan dat). De schade heet "lasered by"
      (`damage_type/laser_gun`); spelers raak je alleen met PvP aan. **Herladen** (`GunReloadPayload`): de volste cel uit
      je inventaris, die voller is dan de cel in het geweer; na de herlaadtijd (`gunReload`) gaat hij erin en komt de
      oude cel op zijn plek (ook een lege, om op te laden). Pak je iets anders vast, dan stopt het. **Recepten**: een
      energiecel = 4 goudklompjes, 2 glazen ruiten, 2 koper en een redstoneblok; het pistool, het geweer, de sniper (met
      een diamant en een verrekijker) en de shotgun met ijzer, goud, glas, redstone en 1 of 2 Computer Chips. Negen
      nieuwe geluiden (vier schoten, leeg, cel eruit, cel erin, grendel/pomp, richten). Vier nieuwe tests: herladen met de
      volste cel (en niet met een legere, en niet sneller schieten dan mag), raken met een headshot en niet door een muur,
      de waaier van de shotgun, en het Charging Station dat een cel oplaadt. 334 tests, allemaal groen.
- [x] **4. De geweren in je eigen beeld** — **De modellen** (`tools/generate_weapon_models.py`): elk geweer van 30 tot 40
      doosjes in vier delen: het lijf, de trekker, de cel en het bewegende deel (de slede van het pistool, de spanhendel
      van het geweer, de grendel van de sniper, de pomp van de shotgun). Rood-goud met donkere delen en cyaan strepen die
      ook in het donker gloeien; het geweer heeft een holografisch vizier met een gloeiend puntje, de sniper een kijker.
      In je inventaris liggen ze opzij, in derde persoon houd je ze met twee handen vast (`WeaponModels.registerExtensions`;
      de loop wijst vooruit). **In je eigen beeld** (`client/weapons/GunRenderer`, de maten per geweer in `GunRig`) tekenen
      we het geweer zelf uit zijn delen, met je twee armen eraan (je eigen skin, met het harnas als je dat draagt):
      pakken (het geweer komt omhoog), terugslag en de trekker bij elk schot, het bewegende deel (de grendel en de pomp
      gaan na een schot heen en weer), een mondingsvlam, richten (het vizier recht voor je oog; de sniper laat zijn
      kijker over je hele scherm zien), herladen (kantelen, de cel eruit en weg, je linkerhand haalt een nieuwe, klik,
      opladen), bekijken met H (eerst de zijkant, dan de bovenkant), sprinten (het geweer zakt) en een beetje ademen. Op
      de zijkant zit een lichtbalkje van tien vakjes dat laat zien hoe vol de cel is. **De bediening**
      (`client/weapons/GunControl`): linkerklik schiet (het geweer: inhouden), zwaait niet en breekt niets; rechterklik
      richt (inzoomen: het pistool 1,25x, het geweer 1,4x, de sniper 4x, en dan een rustigere muis), je loopt dan
      langzamer en kunt niet sprinten; schieten stopt je sprint en duwt je zicht een stukje omhoog. **R en H** zijn nieuwe
      toetsen ("Reload" en "Inspect" in Opties > Besturing > I.M.T) die alleen iets doen met een geweer in je hand; dan
      doen R en H niet ook nog markeren of de dreigingsscan. **De laserbouten** (`GunEffects`): een felle streep, wit met
      cyaan, die razendsnel van de loop naar de inslag vliegt (bij je eigen schoten precies vanaf de loop in je beeld), de
      sniper een volle straal die even blijft hangen, en vonken waar het inslaat. **De HUD** (`GunHud`): rechtsonder de
      schoten (groot), het aantal per cel, de naam en je geladen cellen; "RELOADING" met een balkje; "NO CELL - PRESS R"
      of "EMPTY - PRESS R" in het rood; een eigen kruisje dat uit elkaar gaat na een schot en weg is als je richt.
      **Screenshots** `-Ponly=guns` (`g01` tot `g21`): elk geweer vanuit de heup, gericht en schietend, herladen, bekijken,
      de kijker, de pomp, sprinten, derde persoon van voren en van achteren, en een pistool zonder cel.
- [x] **2d. De Remote EMP** (26 oktober, jouw wens) — `weapons/RemoteEmpBlock`: dezelfde halve bol als de sticky, met
      een stalen ring, een antenne met een rood lampje en lampjes die altijd gloeien; plakt tegen een vloer, muur of
      plafond (zoals C4) en blijft zitten als het blok erachter weg is. Iedereen kan hem eraf trekken (hij valt eruit).
      `RemoteEmpBlockEntity` onthoudt wie hem plakte en bij welke trigger hij hoort. **De EMP Trigger**
      (`weapons/EmpTriggerItem`): een donker handzendertje met een cyaan schermpje, een grote rode knop en een antenne.
      Werkt zoals de detonator van C4, maar los ervan (het component `emp_trigger`): plak je een Remote EMP met de trigger
      bij je, dan hoort hij erbij; anders rechtsklik met de trigger op de Remote EMP. **Rechtsklik** laat al zijn Remote
      EMP's tot 128 blokken ver (`[weapons] empTriggerRange`) afgaan, de dichtste eerst en dan om de 3 ticks de volgende:
      een gewone EMP-puls, en dan zijn ze weg. **Sneak + rechtsklik**: hoeveel er bij hem horen en hoeveel er in bereik
      zijn. **Recepten**: een Sticky EMP Grenade, een redstonefakkel en een koperstaaf = een Remote EMP; een
      redstonefakkel, 4 ijzer, een Computer Chip en een knop = een EMP Trigger. Screenshots `gr19` (de Remote EMP tegen
      de muur en de trigger in je hand) en `gr20` (na de knop). Eén nieuwe test (`remote_emp_goes_off_with_its_trigger`:
      plakken koppelt, de knop legt de robot neer en zet de turret uit, de muur blijft heel, en een Remote EMP van een
      andere trigger blijft zitten). 335 tests, allemaal groen.
- [x] **2e. De rook gaat niet door blokken** (26 oktober, jouw wens) — Een wolk was een bol die dwars door muren
      ging: robots en turrets achter een muur zagen niets, en de grote rookplukken staken aan de andere kant van een muur
      of een plafond uit. Nu **stroomt** de rook (`Smoke.Volume`): hij begint bij de granaat en gaat van blokplek naar
      blokplek (ook schuin), tot hij 4,5 blokken (`smokeRadius`) heeft afgelegd, langs de kortste weg **om blokken heen**.
      Muren, glas, een **dichte deur**, een dicht luik, een ruit of een plaat houden hem tegen (een blok dat bekeken langs
      die richting de hele doorgang dichtzet); door een **open deur** stroomt hij naar buiten, maar omdat die weg langer
      is maar een klein stukje. Een hek, een fakkel of een bloem laat hem door; water en lava vult hij niet. In de open
      lucht is de wolk rond, zoals eerst. Robots, turrets en drones kijken alleen door de plekken met rook (cel voor cel
      langs hun kijklijn). In je spel rekent de mod dezelfde plekken uit en blaast hij de plukken alleen daar; een pluk is
      nooit groter dan de ruimte tot het dichtstbijzijnde blok (midden in een kamer groot, tegen een muur, vloer of
      plafond klein) en drijft hij uit de wolk, dan verdwijnt hij. In het beeld van een bewakingscamera is de wolk nu
      dichte blokjes waar de rook hangt (in plaats van een bol). **Mijn invulling**: welke plekken de rook vult, ligt vast
      als hij begint (een deur die je daarna opendoet, laat niets meer door). Screenshots `gr21` (een smokebom in een
      schuurtje: niets door de muren, wel door het raam te zien), `gr22` (niets door het dak) en `gr23` (een sliertje
      door de open deur). Eén nieuwe test (`smoke_does_not_go_through_blocks`: niet door een muur en een dichte deur, wel
      door een open deur), en de rekensom-test telt nu cel voor cel. 336 tests, allemaal groen.
- [x] **2f. C4 weg: de Remote Frag en de Frag Trigger** (26 oktober, jouw wens) — **Weg**: de C4 (Timer), de C4 (Remote)
      en de detonator, met hun scherm voor de tijd, het aftellende schermpje, de piepjes, de modellen, plaatjes en
      recepten, en de config `[c4]`. **Wat bleef**: de klap met breekpunten en barsten (`c4/C4Blast` heet nu
      `breach/Blast`, met `Cracks` en de Repair Paste in `breach/`), en het alarm van een zone (`breach/BlastAlarm`). De
      sterktes van blokken staan nu onder `[breach]` (TNT en de satelliet ook). **De Remote Frag** (`remote_frag`): de
      halve bol van de Sticky Frag (legergroen, oranje ring, rode lampjes) met een antenne, op een vloer, muur of
      plafond. Hij en de Remote EMP zijn nu één blok met twee soorten (`weapons/RemoteChargeBlock`, `RemoteChargeItem`,
      `RemoteChargeBlockEntity`), zoals de sticky-granaten. Jouw keuzes: zo sterk als een Sticky Frag (8 breekpunten, 3
      blokken ver: één frag blaast een blok steen uit een muur, obsidiaan na vier, de kern na acht); **alarm en schaar**
      zoals C4: met je hand krijg je hem er niet af (alleen een schaar knipt de draad door, dan valt hij eruit), en plakt
      een vreemde hem op een zone, dan ziet de eigenaar een rood bordje "FRAG!" en staat het in het logboek. Een explosie
      maakt hem niet kapot: dan gaat hij zelf af. **De Frag Trigger** (`frag_trigger`, een nieuw 3D-model): een
      legergroen kastje met een oranje gevarenband, rubberen grepen, een rood schermpje, een grote rode knop onder een
      oranje klepje dat openstaat, een tuimelschakelaar met een rood lampje en een dikke antenne. De EMP Trigger en de
      Frag Trigger zijn nu één item met twee soorten (`weapons/TriggerItem`, wat ze onthouden in `TriggerData`, elk in
      een eigen component); een EMP Trigger laat nooit een Remote Frag afgaan. Met een trigger in je hand zie je bordjes
      boven je ladingen: "EMP" in cyaan, "FRAG" in rood (`client/weapons/TriggerMarkers`). Het bereik van allebei is
      `triggerRange` onder `[weapons]` (was `empTriggerRange`). **Recepten**: een Sticky Frag Grenade, een redstonefakkel
      en een koperstaaf = een Remote Frag; een bliksemafleider, ijzer, een Computer Chip en een stenen knop = een Frag
      Trigger (het recept van de detonator). Geluiden: het doorknippen heet nu `remote_frag.defuse`, de klik van de
      triggers `trigger.click`. **Mijn invullingen**: C4 en detonators uit een oude wereld worden vanzelf Remote Frags en
      Frag Triggers (een "alias"; een C4 met een eigen tijd erop gaat verloren); ook de **Remote EMP** mag je nu in de
      zone van een ander plakken (net als de Remote Frag: het zijn inbraakmiddelen). **Showcase**: functie 79 heet nu
      "Remote Frag" (alarm en schaar als eigenaar, dan als inbreker twee frags en de knop, en vier op het obsidiaan).
      **Screenshots** `-Ponly=remote_frag` (`f01` tot `f06`, in plaats van `-Ponly=c4`). **Tests**: `C4GameTests` werd
      `RemoteFragGameTests` (8 tests: een blok uit een muur en met een tweede een gat, obsidiaan na vier, de kern na
      acht, een kettingreactie, koppelen en afgaan, bereik en koppelen, alarm en schaar, het verslag als je weg was).
      334 tests, allemaal groen.
- [x] **5. Showcase, screenshots en LEESMIJ** — **Showcase**: drie nieuwe functies, achteraan zodat de nummers van je
      notities blijven kloppen: **98 "EMP and frag grenades"** (een veld met een robot en een turret voor een muur: een
      EMP-bol, een sticky, een Sticky Frag op de muur, een Frag over de vloer, en een EMP aan je voeten met je bril op),
      **99 "Smoke grenade and flashbang"** (de turret schiet op een husk; grijze rook ertussen en de turret stopt; rode
      rook; een flashbang voor je ogen) en **100 "Laser guns"** (alle vier, met richten, schieten, herladen, bekijken, de
      kijker en de pomp). Getest met `python tools/showcase.py --vanaf 98 --tot 100 --test`. **Screenshots**
      `-Ponly=grenades` (`gr01` tot `gr20`; sinds stap 2e tot `gr23`) en `-Ponly=guns` (`g01` tot `g21`). **LEESMIJ**: een nieuw stuk "Wapens:
      granaten, de Remote EMP en lasergeweren" (wat elke granaat doet, de Remote EMP en de trigger, de bediening van de
      geweren in een tabel, munitie en recepten), en de E.M.P.-zender heet daar nu Illusion Jammer.

### 36. Acht creative-tabbladen (26 oktober)

Jouw wens: "Voeg creative tabs toe in deze volgorde: resources: chip, upgrades... equipment: bril, harnas weapons:
alle guns power: cabel en generators interieur: holotable, workbanch, crusher, compactor... security: hier staan
readers, keypads, laserwall/brige... robots: alle robot parts dan alle robot chips, training items datacenter: alles
voor het datacenter".

- [x] **Het ene tabblad "I.M.T Technology" werd er acht** (`registry/ModCreativeTabs`), achter het vanilla-tabblad
      Combat, in jouw volgorde. Ze heten in het spel **I.M.T Resources, I.M.T Equipment, I.M.T Weapons, I.M.T Power,
      I.M.T Interior, I.M.T Security, I.M.T Robots** en **I.M.T Datacenter**. Wat erin staat:
      **Resources**: Computer Chip, Raven Circuit, de drie bril-upgrades, de vijf harnas-upgrades, Repair Paste.
      **Equipment**: de bril (leeg en opgeladen), het harnas (drie stukken), het Rocket Pack, de Drone, de Illusion
      Jammer. **Weapons**: de vier lasergeweren, de Ion Blaster, de Energy Cell (leeg en vol), alle granaten, de Remote
      EMP/Frag en hun triggers. **Power**: kabel, zonnepaneel, warmtegenerator, Power Cabinet. **Interior**: holotafel,
      Suit Workbench, Crusher, Compactor, laadstation, Drone Dock, Landing Pad, Arc Lamp, lichtschakelaar, relais en
      relay emitter. **Security**: Security Core, Zone Marker, keycard, keycard reader, code panel, laserzender,
      laserbrug, koepelgenerator, camera, de drie turrets, Rocket Ammo, Bypass Kit en Hacking Laptop. **Robots**: eerst
      alle onderdelen (per stijl hoofd, lijf, armen, benen), dan de chips (printplaat en alle modules), dan de drie
      trainingsblokken, en daarna de Robot Assembler, Robot Dock, Guard Robot, Security Drone, Soldering Iron, Robot
      Ammo en de twee spawn eggs. **Datacenter**: het serverrek, CPU, GPU, RAM, SSD en de ventilator.
      **Mijn invullingen** (zeg het als je iets anders wilt): de relais en de Arc Lamp bij Interior (bij de
      lichtschakelaar), de turrets en hun munitie bij Security, de Bypass Kit en de Hacking Laptop bij Security (ze
      werken op de lezers en panelen), de Ion Blaster bij Weapons, de Illusion Jammer bij Equipment, en de spawn egg
      van de Raven-soldaat bij Robots (naast die van de guard robot). Geen tabblad: het gebarsten blok en het
      blokpoeder (die bestaan alleen "van" een blok) en het Raven-geweer (alleen de soldaten hebben het).
      **Test**: `CreativeTabGameTests` (elk item in precies één tabblad, de volgorde klopt, geen vreemde items; zo
      vergeten we nooit een nieuw item). 335 tests, allemaal groen. **Showcase**: functie 96 "I.M.T Technology items"
      bladert nu door de acht tabbladen, elk twee seconden. **Screenshots** `-Ponly=creative` (`ct01_resources` tot
      `ct08_datacenter`).

### 37. Drones en robots herstellen niet vanzelf (26 oktober)

Jouw wens: "drones mogen niet zelf herstellen, dit kan enkel met repair van iron, dus de security drones, arsenal
drones, en ook robots".

- [x] **Alleen ijzer repareert** — **Arsenaal-drones**: het herstel in hun baan (mijn invulling uit hoofdstuk 25,
      0,25 leven per seconde) is weg, met `orbitRepairPerSecond` uit `[drones]`. Repareren gaat zoals het al ging:
      roep hem terug en rechtsklik op hem met ijzer in je rugzak (`repairPerIngot` onder `[drones]`, 3 leven per
      staaf). **Robots en security drones** herstelden niet vanzelf, maar wel als je ze oppakte en weer neerzette (dan
      kwamen ze heel terug), en met ijzer repareren kon nog niet. Nu: **rechtsklik met ijzer** in je hand (wie hem mag
      instellen, net als kogels laden): elke staaf geeft 4 leven terug (nieuw in `[robots]`: `repairPerIngot`), en er
      gaan niet meer staven uit je hand dan nodig. Je hoort een zoem, ziet vonkjes, en onderaan je scherm staat
      "Guard 1 repaired: 13/20 health" (of "is fully repaired", of "is not damaged" en dan kost het niets). Een vreemde
      mag het niet. Het item onthoudt hoe heel hij was (`RobotData` en `DroneData` kregen `health`, van 0 tot 1; een
      item van vóór vandaag is heel), en de tooltip zegt dan in het rood "Damaged: 30% health". **Mijn invullingen**
      (zeg het als je iets anders wilt): een beschadigde robot gaat in de Robot Assembler niet uit elkaar ("It is
      damaged: put it down and right-click it with iron ingots to repair it first."), want anders zette je hem daarna
      heel weer in elkaar; het robotscherm (ook dat van een security drone) heeft een balk **Health** boven de batterij
      (groen, oranje onder de helft, rood onder een kwart), zodat je ziet hoeveel ijzer hij nodig heeft; 4 leven per
      staaf (een Battle-robot is met 5 staven heel, een Super Battle met 11, een security drone met 4); de oude
      "Patrol drones" van vóór hoofdstuk 25 herstellen ook niet vanzelf en krijgen geen reparatie (ze blijven werken
      tot ze neergeschoten worden). **Tests**: `arsenal_drones_repair_in_orbit` werd
      `arsenal_drones_do_not_repair_by_themselves` (vijf seconden lang de hele PlayerTickEvent, en de schade blijft);
      nieuw zijn `robot_repairs_only_with_iron` (niet vanzelf op een dock met stroom, een vreemde niet, één staaf,
      precies genoeg staven, een hele robot kost niets), `robot_keeps_its_damage_when_picked_up` (het item, de
      assembler, weer neerzetten, een oud item) en `drone_repairs_only_with_iron`. 338 tests, allemaal groen.
      **Screenshot** `rb12_robot_screen` (`-Ponly=robots`) toont nu de balk Health.

### 38. Netwerkkabels, de switch, PoE, en een netwerkhendel en -knop (29 oktober)

Jouw wens: "voeg netwerkkabels toe, net zoals de powercables maar dan voor al de security items te zien/configureren in de
starkos. Je hangt een witte netwerkkabel aan de desktop, verbindt die met een keycardreader en dan pas komt die bij de
desktop devices (ook keypad, cameras, turrets). Deze dingen hebben allemaal ook stroom nodig. Ook netwerk lever en
buttons (aan een kabel, power en netwerk) die je gebruikt via de logic in de desktop, een switch waar de netwerkkabels
samenkomen, en POE-kabels die power en ethernet samen nemen." Met kleuren: stroom gewoon grijs, x-ray grijs (jouw
aanpassing van het plan), stromend geel met animatie; netwerk wit, x-ray wit, een signaal = een helder wit vakje dat de
kabel volgt; PoE groen, x-ray gloeiend groen, een signaal = een helder groen vakje.

**Jouw antwoorden** (29 oktober): ook **laserpoorten, de laserbrug en serverracks** krijgen een netwerkkabel (geen relais,
geen Arc Lamp, drones en robots blijven draadloos); de **switch is zoals de stroomkast, met eigen stroom** (aan/uit per
kant, ook vanaf de desktop); **zonder netwerkkabel doet een apparaat niets** (met een config-schakelaar voor het oude
gedrag); **PoE mag ook via een switch**; laserpoorten, brug en je raketturretje **werken los** met alleen stroom (de kabel
is voor de desktop); de laserbrug krijgt in Devices een groep **LASER BRIDGES** met aan/uit. Wat je daarna vroeg (geen
redstone meer voor de mod-apparaten, schakelen op naam) staat in hoofdstuk 39.

**Mijn invulling** (zeg het als je het anders wil): drie aparte kabelrollen (Power, **Network**, **PoE Cable**); ze mogen
op dezelfde plek liggen (dan boven elkaar); aansluiten gaat zoals bij stroom (een apparaat tegen een kabel, de kern of
een switch hangt eraan); raakt een net twee kernen, dan wint de dichtstbijzijnde langs de kabel; de kern geeft stroom
door aan PoE-kabels; lezer, paneel, knop, hendel en switch gebruiken een beetje stroom (betaald door de kern); een EMP
legt open netwerk- en PoE-kabels even plat; iedereen mag netwerkkabels leggen en knippen, ook in andermans zone; de
signalen zijn alleen om naar te kijken (de logica reageert meteen).

Ik bouw het in deze volgorde, één stap per commit:

- [x] **1. Drie soorten kabels** — naast de stroomkabel nu de **Network Cable** (wit, 8 stuks uit 2 koper en een kwarts)
      en de **PoE Cable** (groen, 2 stuks uit een stroomkabel, een netwerkkabel en limoenverf), allebei in het tabblad
      I.M.T Power. Je tekent ze zoals de stroomkabel. Elke soort heeft zijn eigen lijst per chunk (`energy/CableKind`,
      `energy/Cables`; de stroomkabel houdt `imttech:cables`, nieuw zijn `imttech:net_cables` en `imttech:poe_cables`),
      dus op één plek kunnen ze naast elkaar liggen: de eerste (stroom, netwerk, PoE) tegen de vloer of muur, de volgende
      er een kabeldikte boven (`energy/CableGeometry`, de "banen"). Een PoE-kabel zit vast aan een stroom- en aan een
      netwerkkabel ernaast; een stroom- en een netwerkkabel niet aan elkaar. Shift + rechtsklik met een rol raapt alleen
      zijn eigen soort op; een schaar knipt de kabel die het dichtst bij je kijklijn ligt (en geeft een stukje van die
      soort). Met de bril door de muur heen: stroom grijs (half doorzichtig, niet oplichtend, zodat het echt grijs is),
      netwerk wit, PoE gloeiend groen (een felle kern met een zachte gloed). Gebouwen kunnen ook netwerk- en PoE-kabels
      leggen (markers `imttech_net_cable` en `imttech_poe_cable`). Een EMP geeft vonken op alle soorten. In deze stap
      doen ze nog niets: dat komt in stap 2 en 4. **Tests** (`gametest/NetworkGameTests`, batch `lan`):
      `cable_kinds_share_a_cell_but_stay_apart`, `cable_spools_draw_and_pick_up_their_own_kind`,
      `shears_cut_one_cable_at_a_time`, `structure_markers_lay_every_kind_of_cable`. 342 tests, allemaal groen.
      **Screenshots** `-Ponly=network`: `nw01_cable_colours`, `nw02_cable_lanes`, `nw03_cable_xray`, `nw04_cable_xray_off`.
- [x] **2. Het netwerk en de koppeling aan de kern** — nieuw `lan/LanGrid`: vanaf een apparaat zoeken we langs de
      netwerk- en PoE-kabels (zoals bij stroom: tegen elkaar = vast, een apparaat ernaast of erbovenop hangt eraan) tot we
      bij een Security Core komen; staat het apparaat direct tegen de kern, dan is er geen kabel nodig. Serverracks en
      laserzenders geven het netwerk door aan wie ertegenaan staat (een kast vol racks of een laserpoort heeft genoeg aan
      één kabel). Raakt een net twee kernen, dan wint de dichtstbijzijnde langs de kabel. Een open netwerkkabel in een
      EMP-puls geeft even niets door, een kabel in een muur wel. **Zonder netwerkkabel doet een beveiligingsapparaat
      niets** (jouw keuze): een keycardlezer of codepaneel geeft een rood lampje en "Not connected: no network cable to the
      desktop of a security core."; een camera geeft geen beeld ("offline", "no network cable to a security core" als je
      erop klikt); een beveiligingstorentje staat uit en schiet op niemand (ook niet als hij toch aan zou staan: zonder
      kern weet hij niet wie een vriend is); een serverrek zegt "geen netwerk". In Devices staat alleen wat bekabeld is.
      Laserpoorten en je eigen raketturretje werken wel los (jouw keuze), maar volgen de kern (de schakelaar Lasers, de
      regels van Access) pas als ze aan zijn netwerk hangen. **Config**: een nieuwe sectie `[network]` met `required`
      (standaard `true`; op `false` geldt de oude regel: apparaten horen bij de dichtstbijzijnde kern in wiens gebied ze
      staan, voor een oude wereld). **Binnenkant**: `SecurityCores` kreeg `deviceCore`/`linkedTo` (via de kabel) en
      `controllerOf`/`controls` (een apparaat via de kabel, een relais of koepel via zijn gebied); `coreAt` en `ownedBy`
      blijven "in wiens gebied", voor zones, robots, docks en wie er vertrouwd is. Omgezet naar de kabel: de lezers en
      panelen (`LogicEvents`, namen, hernoemen), de Bypass Kit (alleen echt verbonden apparaten zijn te hacken), de camera,
      het serverrek en de server, het torentje (aan, op wie hij schiet, zijn statusregel), de schakelaar Lasers, Devices
      (`SecurityDevices.scan`) en "when turned_on/turned_off". De Security Core en de lezers en panelen tekenen nu een
      stukje kabel naar zich toe (`PowerConnectable`, `NetworkConnectable`). **Mijn invulling**: tijdens
      `gradlew runGameTestServer` geldt de oude regel (anders moesten alle 342 oude tests bekabeld worden); de tests van
      het netwerk zetten het zelf aan (`@BeforeBatch`). **Tests** (batch `lan`): `reader_needs_a_network_cable_to_the_desktop`,
      `network_reaches_through_walls_and_by_touch`, `the_nearest_core_along_the_cable_wins`,
      `without_the_network_rule_the_area_counts`, `camera_turret_and_rack_need_the_network`,
      `rocket_turret_and_laser_gate_work_without_network`, `emp_cuts_open_network_cables`. 349 tests, allemaal groen.
- [x] **3. De Biebia's Industries Tower bekabeld** (eerder dan gepland: anders bleef de beveiliging van de toren in een
      echte wereld uit) — in `tools/generate_structure.py` krijgt elke stroomkabel van de toren een netwerkkabel op
      dezelfde plek (de markers krijgen allebei de labeltjes), dus het netwerk loopt langs dezelfde verborgen wegen als de
      stroom: knip je zo'n kabel door, dan valt wat erachter zit uit. Het netwerk gaat niet door de stroomkast; één
      stukje netwerkkabel om de kast heen (in de mantel van de krachtcentrale) verbindt de beveiliging met de lasers, zodat
      de kern ook de lasergang en de kluis ziet. `biebia_tower.nbt` opnieuw gemaakt (alleen de markers zijn anders: ik
      controleerde dat het script zonder deze wijziging precies het oude bestand maakt). De "serverkasten" van de toren
      zijn decoratie (crafters), die hebben niets nodig. **Test**: `biebia_tower_is_wired_with_network_cables` (met het
      netwerk aan: de 9 torentjes, 6 camera's en 22 laserzenders hangen aan de kern, en Devices ziet 9 torentjes en 6
      camera's). 350 tests, allemaal groen.
- [x] **4. Stroom voor lezer en paneel** — jouw wens: "de keycardreader heeft een netwerkkabel én een powerkabel". Een
      keycardlezer en een codepaneel gebruiken nu een beetje stroom: nieuw onder `[power]` zijn `readerUpkeep` en
      `codePanelUpkeep` (elk 1 per twee seconden). Ze hebben geen eigen klok: de kern waar ze aan hangen, betaalt om de
      twee seconden hun stroom van hun eigen stroomnet (een stroomkabel ernaast, of een generator ertegen). Zonder stroom
      gaat er geen lampje aan: een kaart of een code doet niets ("No power: connect it with a power cable (or a PoE
      cable)."), de Bypass Kit vindt er niets om te hacken, en in Devices staat "no power". Hernoemen (sneak + rechtsklik)
      kan wel. **Mijn invulling**: met `[network] required = false` (het oude gedrag) hebben lezers en panelen ook geen
      stroom nodig, zoals vroeger (anders werkte een oude wereld niet meer, en alle oude tests ook niet). Binnenkant:
      `lan/DevicePower` (straks ook voor knop, hendel en switch). **Test**: `reader_and_panel_need_power`. 351 tests,
      allemaal groen.
- [x] **5. PoE: stroom en netwerk in één kabel** — jouw wens: "POE-kabels nemen power en ethernet samen; dit werkt als je
      een power cable aan de desktop hebt hangen, en dan vertrekt die van de desktop". Een PoE-kabel geeft nu ook stroom
      door (`PowerGrid`), en de Security Core geeft zijn stroom door aan wat erop aangesloten is: één stroomkabel (of een
      generator) aan de kern voedt zo al zijn PoE-kabels, en een camera die alleen met een PoE-kabel aan de kern hangt,
      heeft beeld. Heeft de kern zelf geen stroom, dan geeft de PoE-kabel ook geen stroom (het netwerk werkt wel). Een
      PoE-kabel sluit aan op een stroomkabel ernaast (de stroom gaat erdoor, beide kanten op) en op een netwerkkabel
      ernaast (het netwerk gaat erdoor); een netwerkkabel zelf geeft geen stroom. De stroomkast ziet een PoE-kabel ook
      als "wired". **Mijn invulling**: een apparaat dat direct tegen de kern staat, krijgt zo ook zijn stroom van de kern
      (net als het netwerk). **Tests**: `poe_brings_power_and_network_from_the_desktop`,
      `poe_joins_power_and_network_cables`. 353 tests, allemaal groen.
- [x] **6. De Network Switch** — jouw keuze: "zoals de stroomkast, met stroom". Een nieuw blok (tabblad Power; recept:
      ijzer en netwerkkabels om een Computer Chip met redstone) waar netwerk- en PoE-kabels van alle zes kanten samenkomen.
      Achter het deurtje (rechtsklik) staat per kant een poort met een aan/uit-knop, met erbij of er iets aan hangt
      ("wired"/"nothing"); een poort die uit staat, knipt de tak erachter af. De switch heeft zelf stroom nodig: hij haalt
      elke twee seconden `networkSwitchUpkeep` (3, onder `[power]`) van zijn stroomnet, en zonder stroom gaat er geen
      netwerk door (de lampjes voorop gaan uit). PoE-stroom gaat wel door een switch (jouw keuze): van een PoE-kabel naar
      een PoE-kabel, alleen langs poorten die aan staan (ook als de switch zelf geen stroom heeft: dat is één kabel die
      verder loopt). In een EMP-puls valt hij even uit, zoals een lezer. In **Devices** een groep NETWORK SWITCHES: online
      of "no power", met de knoppen Rename en Ports; Ports klapt een regel open met de zes poorten (groen aan, rood uit,
      grijs = er hangt niets aan), en een klik zet een poort om. Een nieuwe switch heet "Switch 1", "Switch 2"... (of de
      naam die je hem op een aambeeld gaf). **Tests**: `switch_ports_and_power_decide_the_network`,
      `poe_power_passes_through_a_switch`, `desktop_switches_switch_ports`. 356 tests, allemaal groen. **Screenshots**
      `-Ponly=network`: `nw05_switch_block`, `nw06_switch_screen`, `nw07_devices_switch`.
- [x] **7. De laserbrug op het netwerk** — jouw keuze: "rij in Devices". Een laserbrug met een netwerkkabel naar de
      desktop staat nu in **Devices**, in een eigen groep LASER BRIDGES: één regel per brug ("Laser bridge (2 emitters)"),
      met "active" (hij ligt er), "retracted" (ingetrokken) of "no power" (hij wil er liggen maar er is te weinig stroom).
      Brugzenders die tegen elkaar zitten geven het netwerk aan elkaar door, zoals laserzenders: één kabel is genoeg voor
      de hele brug. Zonder kabel werkt een brug gewoon los, op stroom (jouw keuze), en de schakelaar Lasers laat bruggen
      nog steeds met rust. Laserpoorten en serverracks hingen al aan het netwerk (stap 2). **Mijn invulling**: alleen
      bruggen van de eigenaar van de kern (of van niemand, uit een commando) staan erin, net als bij je raketturretjes:
      anders kon iemand met een kabel naar zijn eigen kern jouw brug bedienen (de aan/uit-knop komt in hoofdstuk 39). Met
      `[network] required = false` staan de bruggen in het gebied van de kern erin. **Test**:
      `laser_bridges_join_the_network`. 357 tests, allemaal groen.
- [x] **8. De netwerkhendel en de netwerkknop** — jouw wens: "netwerk lever en buttons, deze hangen ook aan een kabel
      (power en netwerk) en kunnen net zoals een redstone button of lever worden gebruikt maar dan via de logic in de
      desktop". Twee nieuwe blokken (tabblad Security; recept: een stenen knop of een hendel + Computer Chip + Network
      Cable): de **Network Button** en de **Network Lever**, aan de muur, op de vloer of aan het plafond, zoals een gewone
      knop of hendel. Ze geven geen redstone: met een netwerkkabel naar de desktop en een beetje stroom (`buttonUpkeep` en
      `leverUpkeep`, elk 0.5 per twee seconden onder `[power]`; een PoE-kabel geeft allebei) starten ze je diagram:
      `when button("Doorbell"):` (de knop duikt een seconde in) en `when lever("Night mode"):` met `lever.on` (hoe hij nu
      staat). Daarin werken ook `player.name`, `player.trusted` en `player.owner`, en `is_on("Night mode")` leest een
      hendel altijd, ook in een andere trigger. Zonder stroom, kabel of draaiende server gaat hij wel om (het is een echte
      knop), maar de server hoort niets: de speler hoort waarom. Ze heten "Button 1" en "Lever 1"; hernoemen met sneak +
      rechtsklik met een lege hand, of in **Devices** (groepen BUTTONS en LEVERS, een hendel met "on" of "off"). De app
      Logic kent ze overal: Help (START WITH, en de lijst READERS, PANELS, BUTTONS, LEVERS), Ctrl+Spatie en de blokjes.
      In het logboek: "Senne pressed Doorbell", "Senne switched Night mode on". **Mijn invulling**: `accept()` en `deny()`
      blijven voor lezers en panelen (een knop heeft geen lampje); de Bypass Kit doet niets op een knop of hendel (met de
      kit in je hand druk je gewoon); een oude def die `button` of `lever` heette, krijgt vanzelf een andere naam
      (`lever_1`). **Tests**: `network_button_and_lever_start_diagrams`, `logic_buttons_and_levers`. 359 tests, allemaal
      groen. **Screenshot** `-Ponly=network`: `nw08_controls`.
- [x] **9. Signalen en stroom die je ziet lopen** — jouw tabel: "een signaal wordt gestuurd = helder wit vakje dat de
      kabel volgt tot aan destination" (groen over een PoE-kabel), en "als power erdoor stroomt = geel met animatie".
      **Signalen**: een kaart, een code, een knop of een hendel stuurt een helder vakje van het apparaat over de kabels naar
      de kern (zo'n 10 blokken per seconde); `accept()` en `deny()` sturen het antwoord terug naar de lezer, en een
      schakelaar op de desktop (een torentje aan/uit, een poort van een switch) loopt van de kern naar het apparaat. Wit
      over een netwerkkabel, groen over een PoE-kabel; door een switch of de kern zie je het niet (dan zit het binnenin).
      **Stroom**: een apparaat dat stroom haalt, laat gele streepjes lopen van zijn generator naar zich toe, over stroom-
      en PoE-kabels (ook door de kern en een switch heen). Het is alleen om naar te kijken: de logica wacht er niet op. Over
      een kabel in een muur zie je het alleen met de kabel-xray van de bril. **Config**: onder `[network]` `signalRange`
      (48 blokken: wie verder weg is, krijgt niets) en `flowSeconds` (2: zo vaak hooguit stuurt een apparaat zijn stroom;
      0 = nooit). In **G > Information** twee nieuwe knoppen voor je eigen spel: Cable signals en Power flow.
      **Binnenkant**: `LanGrid.path` en `PowerGrid.path` zoeken de weg, `lan/CableTraffic` stuurt hem (een nieuw bericht,
      `CableTrafficPayload`), `client/CableTraffic` tekent hem over de kabels zoals ze liggen (CableGeometry). **Mijn
      invulling**: een generator die direct tegen een apparaat staat, geeft geen streepjes (er is geen kabel om op te
      tekenen). **Test**: `signals_and_power_follow_the_cables`. 360 tests, allemaal groen. **Screenshots**
      `-Ponly=network`: `nw10_signal`, `nw11_power_flow` (stilgezet).
- [x] **10. LEESMIJ, showcase en screenshots** — **LEESMIJ**: een nieuw stuk "Het netwerk: netwerkkabels, PoE, de
      switch, knoppen en hendels" (na De stroomkast), "Stroom en kabels" bijgewerkt (de drie soorten, de kleuren door de
      muur heen, de gele streepjes), de triggers `when button`/`when lever` en `lever.on` bij De server, en de
      verouderde getallen (360 tests, `-Ponly=network`). **Showcase**: twee nieuwe functies, achteraan zodat de nummers
      van je notities blijven kloppen: **101 Network cables** (de drie kabels, een vakje van de lezer naar de kern, de
      gele streepjes, een poort van de switch uit en aan, Devices) en **102 Network button and lever** (een diagram met
      `when lever` en `when button`: I.M.T zegt wat er gebeurt; daarna de code in Logic en Devices). Elke functie zet
      het netwerk zelf aan; de oudere functies werken zoals vroeger zonder kabels. **Publiceren**: een regel erbij in
      `publish/description.md`.

### 39. Apparaten op hun naam, zonder redstone (29 oktober)

Jouw wens: "Ik wil dat alles vanaf nu niet meer met redstone wordt geschakeld maar allemaal met stroom: als het stroom
heeft gaat het aan, zonder stroom uit, voor de rayshield en de raybridge" en "behoud hoe relays nu werken, zorg dat elke
device een eigen naam krijgt die je kunt on/off/pulse/toggle net zoals een relay ... je kunt de poort dan toggelen via
bijvoorbeeld on("poort1"), poort1 is dan de naam die je in de GUI van de rayshield hebt ingesteld. De relays gebruik je dus
enkel nog voor redstone, de security dingen van de mod doe je via script, voice, enzovoort."

- [x] **1. Laserpoorten en laserbruggen op hun naam** — een laserpoort of laserbrug **staat aan zolang hij stroom heeft**;
      redstone doet niets meer (en omkeren, "Inverted", is weg: zonder redstone betekent het niets). Wie hem neerzet,
      krijgt hem in zijn **I.M.T-lijst** (toets '): "Gate 1", "Bridge 2", één regel per poort (een zender ertegenaan geeft
      geen tweede regel). Daarmee schakel je hem zoals een relais: met je bril, je stem ("turn off gate 1"), of een
      diagram (`off("poort1")`, `on`, `toggle`, `pulse`); het geldt voor de hele poort. In zijn **scherm** (sneak +
      rechtsklik) staan nu **Name** (de naam in jouw lijst, met Save) en **Switch** (aan: de wand staat zolang er stroom
      is; uit: open). Hak je de zender met de naam weg, dan schuift de naam naar een zender ernaast; de laatste weg, en
      de naam is weg. Op de **desktop** staan poorten en bruggen in Devices onder hun naam, met een knop **Turn off /
      Turn on** (zoals bij de torentjes). **De toren en de bunker**: de redstoneblokken achter de lasers zijn weg (ze staan
      gewoon aan op hun stroom). De **val in de kluis** werkt nu met een **relay emitter** onder het vloerblok onder de
      drukplaat: de plaat geeft dat blok stroom, de emitter zet de vier vloerzenders aan zolang de plaat ingedrukt is. Hij
      heeft geen eigenaar; in de structuur staat "LinkAround", zodat hij de eerste keer zelf de zenders om hem heen opzoekt
      (ook als de toren gedraaid in de wereld staat). `biebia_tower.nbt` en `biebia_bunker.nbt` opnieuw gemaakt.
      **Mijn invulling**: een nieuwe poort staat aan; een oude omgekeerde poort staat voortaan gewoon aan zodra hij stroom
      heeft; in een diagram werkt een poort alleen als hij met een netwerkkabel aan de kern hangt (zoals alle
      beveiligingsapparaten), met je bril en stem overal. Een speler die vertrouwd is in de basis en de naam in het
      scherm bewaart, zet de poort in zijn eigen lijst. **Tests**: `laser_switched_by_name_and_lying_flat` (was het
      omkeren), `laser_gate_switched_by_name_from_a_diagram`; de tests van de laserpoort, de brug, de Bypass Kit en de
      toren zijn omgezet van redstone naar schakelen op naam. 361 tests, allemaal groen. **Screenshots**
      `-Ponly=laser,bridge`: `75_laser_screen`, `br07_bridge_screen`.
- [x] **2. Torentjes en camera's** — een **torentje** gaat niet meer uit met redstone (ook het Raven-torentje niet:
      sloop het, of leg het even plat met een EMP-granaat); je zet het uit en aan op zijn naam. Wie een raketturretje
      of een beveiligingstorentje neerzet, heeft het in zijn I.M.T-lijst ("Turret 3"). Een **camera** heeft nu een
      eigen aan/uit: wie hem neerzet, heeft hem in zijn lijst ("Camera 1", of de naam uit het aambeeld), en de camera heet
      zelf ook zo (bij zijn beeld op de desktop: één naam overal). Uit = geen beeld ("switched off" als je erop klikt).
      In **Devices** hebben torentjes, laserpoorten, laserbruggen en camera's nu twee knoppen: **Rename** en **Turn off /
      Turn on**. Rename zet de naam in jouw I.M.T-lijst (staat het er nog niet in, zoals een beveiligingstorentje van de
      toren, dan komt het erin), en de server kent hem meteen voor je diagrammen; bij een camera verandert ook de naam bij
      zijn beeld (en Rename in de app Cameras verandert ook de naam in je lijst). **Mijn invulling**: de naam komt in de
      lijst van wie aan de desktop zit; een apparaat neerzetten of weghalen ververst het netwerk van de kern meteen (zodat
      het direct in Devices staat). **Test**: `cameras_and_turrets_switch_and_rename_by_name`; de test van het torentje met
      redstone is nu `turret_ignores_redstone_and_switches_by_name`. 362 tests, allemaal groen. **Screenshot**
      `-Ponly=network`: `nw12_devices_named`.
- [ ] **3. LEESMIJ, showcase en screenshots** (Rayshield, Laserbrug, Torentjes, Redstone-relais: "relais zijn alleen
      voor redstone").

### 40. De laptop overal, en een Computer met Monitor (30 oktober)

Jouw wens: "Ik wil dat je laptops overal kunt gebruiken om je security core te configureren ook wil ik dat je een desktop
toevoegt met monitor deze kun je configureren via de security core wat je exact op die desktop mag en kan doen, dit kun je
dan gebruiken om in bijvoorbeeld de receptie van je base te zetten voor de cams te bekijken of kijken wie er waar
binnengaat etc."

**Jouw antwoorden** (30 oktober):
- **De laptop** is de **Hacking Laptop**, omgebouwd: rechtsklik in de lucht opent het inlogscherm; op een vreemde kern
  klikken blijft hacken zoals nu. Je gebruikt hem gewoon vanuit je inventaris, je zet niets neer.
- **Verbinden** gaat met **IP + wachtwoord** (zoals SSH), niet met een lijstje of door te koppelen.
- **De desktop-pc** zijn **twee blokken**: een **Computer** (de kast, met netwerkkabel en stroom) en een **Monitor** die
  ertegen staat. Rechtsklik op de monitor opent hem.
- **Per pc stel je op de kern in**: welke apps erop staan, per app **alleen kijken** of **ook bedienen**, **wie** hem mag
  gebruiken (iedereen, je vertrouwde spelers, of wie een keycard uit een groep heeft) en een **eigen code**.

**Mijn invulling** (zeg het als je het anders wil):
- **Inloggen met de laptop**: het IP staat in de app Status van je kern. Ook de eigenaar typt het wachtwoord (zo koos je
  het); een kern **zonder wachtwoord** laat niemand van ver binnen, en een kern **zonder stroom** antwoordt niet. Het werkt
  in **dezelfde dimensie** (je basis in de overworld bereik je overal in de overworld, niet vanuit de Nether). Na
  `[security] passwordAttempts` foute wachtwoorden moet je even wachten (`passwordLockoutMillis`), net als in de terminal.
  Elke poging staat in het logboek; logt iemand anders dan de eigenaar in, dan krijgt de eigenaar een I.M.T-alarm. De
  laptop onthoudt het laatste IP (niet het wachtwoord). De sessie sluit als je de laptop niet meer bij je hebt, van
  dimensie wisselt, de kern stroom verliest of het wachtwoord verandert. Zolang ze open is, blijft het stukje wereld met de
  kern geladen. Camera's van ver geven nog "OUT OF RANGE" (dat is stap 10 van hoofdstuk 7, nog steeds opzij).
- **De Computer** heeft een netwerkkabel naar de kern nodig (of PoE) en een beetje stroom, zoals een keycardlezer. De
  **Monitor** heeft niets nodig: hij gebruikt de computer waar hij tegenaan staat (één computer mag meer monitoren hebben).
  Het scherm licht op als de computer werkt.
- **Instellen** doe je in een nieuwe app **Computers** op de kern (ook via de laptop): alle computers op het netwerk van
  die kern, met hun naam, wie, de code, en per app **Off / View / Use**. Status, Logs en Monitor hebben geen knoppen, die
  zijn Off of View. **Settings** (het wachtwoord van de kern), **Groups** (dat zijn je eigen relais) en **Computers** zelf
  kunnen niet op een pc. De instellingen zitten in de computer zelf: hak je hem weg, dan begint hij opnieuw.
- **Een nieuwe computer** staat op "Trusted", zonder code en zonder apps ("Not set up yet: choose its apps in the
  Computers app on your security core").
- **De eigenaar** komt altijd binnen, zonder code, maar ziet dezelfde apps als iedereen op die pc (alles kan hij op de kern
  of met de laptop).
- **Wat een pc niet mag tonen, krijgt hij ook niet**: de server stuurt alleen de gegevens van de apps die erop staan, en
  weigert elke knop van een app die op View staat ("View only on this computer").
- **Logs** krijgt bovenaan een knop **All / Entries**: met Entries zie je alleen wie welke lezer, welk paneel of welke
  knop gebruikte ("wie er waar binnengaat"), ook op de kern zelf.
- Een pc onthoudt zijn eigen vensters (los van de kern), en sluit als je meer dan 8 blokken van de monitor wegloopt.

Ik bouw het in deze volgorde, één stap per commit:

- [x] **1. Inloggen met de Hacking Laptop (IP + wachtwoord)** (`2a09346`) — rechtsklik met de laptop in de lucht (of op een blok
      dat geen kern is) opent het scherm **Hacking Laptop: SSH login**: een vak voor het IP, een vak voor het wachtwoord
      (je ziet sterretjes; alleen a-z en 0-9 passen erin) en **Connect** (of Enter). Het IP van de vorige keer staat er
      al in (de laptop onthoudt het, ook in zijn tooltip: "Last login: 10.x.x.x"), het wachtwoord nooit. Lukt het, dan
      opent de desktop met bovenaan **REMOTE SESSION**; lukt het niet, dan staat er in het rood waarom: "ssh: no system
      answers at ...", "no route ... That system is in another dimension", "does not answer. That system has no power",
      "Set a password in Settings on your core first" (de eigenaar), "This system takes no logins: it has no password"
      (een ander), "Permission denied, please try again", en na `[security] passwordAttempts` foute wachtwoorden "wait
      4 s". Elke foute poging staat als LOGIN_FAIL in het logboek ("Wrong password from Senne (remote)"), een goede als
      LOGIN ("Senne logged in remotely"); logt iemand anders in (of probeert hij het), dan krijgt de eigenaar een
      I.M.T-alarm. Zolang je ingelogd bent, houdt de server het stukje wereld rond de kern geladen (3 x 3 chunks die
      tikken); de verbinding sluit als je geen Hacking Laptop meer bij je hebt ("you no longer carry a Hacking
      Laptop"), als de kern geen stroom meer heeft ("the system has no power"), als je van dimensie wisselt, en (zoals
      al) als iemand het wachtwoord verandert. Hacken werkt zoals altijd: klik met de laptop op een kern die niet van
      jou is. Nieuw: `OsLoginScreen`, `OsLoginPayload`, `OsLoginPromptPayload`, `SecurityOs.login`. **Test**:
      `laptop_logs_in_with_ip_and_password` (zonder laptop, zonder wachtwoord, onbekend IP, fout wachtwoord, het goede,
      het IP op de laptop, een vriend met het wachtwoord, te veel pogingen, een kern zonder stroom). 363 tests, allemaal
      groen.
- [x] **2. De Computer en de Monitor** (`6823449`) — twee nieuwe blokken in de tab Security, na de Security Core. De
      **Computer** is een donkergrijze pc-kast (voorkant: twee schijfvakken, een aan-knop met een ring en een werklampje;
      achterkant: een witte netwerkpoort, de stroomaansluiting en een ventilator). Hij heeft een netwerkkabel (of PoE) naar
      een kern nodig en `[power] computerUpkeep` stroom (2 per twee seconden, zoals een camera). Werkt hij, dan brandt de
      ring cyaan en licht elke **Monitor** ertegen op (aan welke kant ook, ook bovenop): het scherm toont dan de Security
      OS-desktop en geeft wat licht (6). De monitor heeft een voetje en een toetsenbord ervoor, en draait naar wie hem
      neerzet. Rechtsklik op de kast zegt hoe hij ervoor staat ("Computer 1: connected to my-core (10.x.x.x)", of "No
      network: connect the computer to a security core with a network cable", "The computer has no power", "the security
      core ... has no power", "down after an EMP blast"); rechtsklik op de monitor zegt nu nog "Not set up yet" (openen
      komt in stap 4). Een nieuwe computer heet "Computer 1" (of de naam uit een aambeeld; afbreken houdt de naam).
      **Recepten**: Computer = 6 ijzer, een computerchip, redstone en een netwerkkabel; Monitor = 4 ijzer, een zwarte
      glazen ruit, glowstone en 3 stenen knopjes (het toetsenbord). Plaatjes: `generate_textures.computer_textures()`,
      modellen: `generate_block_models.computer_and_monitor()`. **Test**:
      `computer_needs_network_and_power_and_lights_its_monitor`. 364 tests, allemaal groen.
- [x] **3. De app Computers op de kern** (`a9e7b37`) — een nieuwe app **Computers** (icoon P, na Access) op de desktop van de kern
      (en dus ook met de laptop). Links de computers op het netwerk van die kern, met een groen of rood blokje (werkt hij
      of niet); rechts de gekozen computer: zijn naam met **Rename**, of hij werkt (of waarom niet), **Who** (een knop die
      wisselt tussen Everyone, Trusted en Keycard group; bij Keycard group kies je de groep met < en >), **Code** ("set"
      of "none", met **Set code** / **Change** en **Clear**; je typt sterretjes, 4-8 tekens a-z en 0-9 zoals een
      wachtwoord) en de 14 **APPS** in twee kolommen, elk met een knop die wisselt tussen **Off** (grijs), **View**
      (cyaan) en **Use** (groen). Status, Logs en Monitor gaan alleen tussen Off en View. Settings, Groups en Computers
      staan er niet bij. Elke klik gaat meteen naar de server en in het logboek ("Senne set Cameras on Reception to
      view", "Senne set a code on Reception"; de code zelf nooit). Keycard group kan pas als je een groep hebt ("Make a
      keycard group first (in the Keycards app)"). Zonder computers legt de app uit hoe je er een aansluit. Binnenin:
      `DesktopRights` (Off/View/Use per app, en welke opdracht bij welke app hoort; de server weigert voortaan elke
      opdracht die niet mag, "View only on this computer"), de instellingen staan in `ComputerBlockEntity`, de knoppen in
      `Computers.desktop`. **Test**: `computers_app_sets_apps_who_and_code`. 365 tests, allemaal groen.
- [x] **4. De pc gebruiken** (`dc4fcd3`) — rechtsklik op de monitor opent de desktop van de computer, met bovenaan in het groen
      **COMPUTER: Reception**. Eerst kijkt de server: werkt de computer, staat er minstens één app op ("Not set up yet"),
      en mag jij erop: **Everyone**, **Trusted** ("ACCESS DENIED: only trusted players may use Reception") of **Keycard
      group** (een geldige kaart van dit netwerk uit die groep ergens in je inventaris; "... needs a keycard of group
      staff"). De eigenaar mag altijd. Heeft de computer een **code**, dan eerst het codescherm (hetzelfde als het
      inlogscherm van de laptop, met alleen het vak "Code"); een foute code staat in het logboek ("Wrong code on
      Reception from Lotte"), na `passwordAttempts` keer moet je even wachten. De eigenaar typt geen code. Inloggen staat
      als LOGIN in het logboek ("Lotte logged in at Reception").
      Op de pc staan **alleen de toegestane apps** (de icoontjes schuiven op), en de server stuurt **alleen hun
      gegevens** mee (geen keycards, mappen, diagrammen... als die apps er niet op staan). Een app op **View** heeft
      "view only" in zijn titel: in **Cameras** zie je de muur en het grote beeld met alleen Back (geen pijltjes, geen
      Rename), in **Devices** de lijst met de toestand maar zonder knoppen (Drones/Lasers/Cameras als tekst). Elke andere
      knop van een app op View zegt "View only on this computer", en de server weigert hem toch. Op een pc nooit Settings,
      Groups of Computers. Verandert de eigenaar de apps terwijl iemand kijkt, dan verdwijnen die vensters meteen; mag
      iemand er niet meer op (Who veranderd, computer zonder stroom), dan gaat de desktop binnen een seconde dicht. Ook als
      je meer dan 8 blokken van de monitor wegloopt of iemand de computer afbreekt. De pc onthoudt zijn **eigen vensters**
      (Shut down vergeet ze, net als op de kern).
      **Logs** heeft bovenaan een knop **Show: all / Show: entries**: met entries alleen wie welke lezer, welk paneel,
      welke knop of hendel gebruikte en wie waar inlogde ("kijken wie er waar binnengaat"), op de kern en op een pc.
      **Test**: `computer_desktop_shows_only_what_the_core_allows`. 366 tests, allemaal groen.
- [x] **5. LEESMIJ, showcase en screenshots** — LEESMIJ.md heeft een nieuw stuk "Je desktop van overal (de laptop) en
      computers voor je basis" (na het netwerk), en bij het hacken een regel over inloggen met de laptop. Showcase: **103
      Hacking Laptop: log in from anywhere** (eerst een fout wachtwoord, dan het goede: REMOTE SESSION) en **104 Computer
      and monitor** (de app Computers, de pc van de receptie met alleen Status, Cameras, Devices en Logs om te kijken,
      Logs met Show: entries, het codescherm van Office). Screenshots `-Ponly=computers`: `pc01_computer_and_monitor`,
      `pc02_computers_app`, `pc03_pc_desktop`, `pc04_pc_devices_view_only`, `pc05_pc_logs_entries`, `pc06_code_screen`,
      `pc07_laptop_login`, `pc08_remote_session`. Wat ik daarop zag en rechtzette: "COMPUTER: Reception" en "REMOTE
      SESSION" liepen in de kopbalk over het IP heen op een smal scherm; nu staan ze erachter (en worden ze zo nodig
      ingekort). En: een bestand openen kan op een pc alleen als **Notes** erop staat (daar lees je het); met alleen
      Files zie je de mappen (anders flitste er een Notes-venster op dat meteen weer dicht ging).

### 42. De versie telt vanzelf mee (2 november)

Jouw vraag: "waar kan ik zorgen dat als die build dat de versie niet 2.0.0 is want ondertussen zitten we al aan een veel
hoger versie, kun je ook zorgen dat dat telkens aanpast als er updates gedaan worden". De versie stond sinds 19 september
vast op 2.0.0 in `gradle.properties`, dus elke jar heette `imttech-2.0.0.jar`, wat er ook in zat.

- [x] **Het derde getal telt git** — `mod_version` in `gradle.properties` heeft nu alleen de eerste twee getallen, en
      `build.gradle` zet er het aantal commits achter sinds die regel voor het laatst veranderde. Deze commit is
      **2.1.0**, de volgende 2.1.1, enzovoort. Die versie staat in de naam van de jar en in de modlijst van Minecraft.
      Voor een nieuwe upload met nieuwe dingen zet je de regel op 2.2 (dan begint het weer bij 2.2.0). Een nieuwe versie
      die nog niet gecommit is, begint al bij nul; zonder git (een zip van de broncode) wordt het 2.1.0. LEESMIJ.md en
      `publish/HOE-PUBLICEREN.md` zeggen nu hoe het werkt.
      **Mijn invulling** (zeg het als je iets anders wilt): **2.1** als begin, de eerste versie met alles wat er na 2.0.0
      bij kwam. Een ander getal (bijvoorbeeld 3.0) is één regel in `gradle.properties`.

### 43. De taal: soorten, parameters en global scripts (4 november)

Hoofdstukken 43 tot en met 51 komen uit één lijst ideeën van 4 november (negen onderwerpen, één vragenronde, daarna
alles bouwen). Jouw wens voor de taal: "variabelen: string, int, bool, hierdoor kun je vb baseLockdown = true dan kun je
met een if kijken of een keycard reader mag werken als base in lockdown is; parameters naar functies: OpenDeur("box")
opent de deur box maar logt en accepteert ook ineens; global functions: een optie in het script dat het global is, dan
mogen er enkel functies in staan maar kun je die overal gebruiken, vb ActivateAlarm()".

**Jouw antwoorden**: soorten **zoals in Python** (`lockdown: bool = false`, `def open_door(door: string):`); gedeelde
namen leven **in een global script**; een script is global door het **woord `global`** op de eerste regel, en er mogen
**meerdere** global scripts zijn.

**Mijn invulling** (zeg het als je het anders wil): vier soorten, `int` (heel getal, rondt af naar beneden zoals in C++),
`float`, `string` en `bool`; zonder soort-woord raadt de taal de soort zoals vroeger. Een parameter zonder soort-woord
neemt elke waarde. Een apparaat mag voortaan ook code zijn (`pulse(door, 3)`, `on("Lamp " + i)`); `on(Lamp)` zonder
waarde krijgt de tip om aanhalingstekens te zetten. Gedeelde namen blijven bewaard met de kern (ook na een herstart en
na opnieuw bewaren; een nieuwe naam of een andere soort begint opnieuw bij zijn eerste waarde). Een gewoon diagram mag
een gedeelde naam of global def niet zelf nog eens maken (Check zegt in welk script hij al staat). Een def uit een global
script die `accept()` gebruikt, werkt nog steeds alleen onder een lezer of paneel; een robot- of droneprogramma mag een
global def gebruiken als er niets in staat dat alleen de server kan, en leest en schrijft gedeelde namen via zijn
verbinding met de kern. De app Logic toont de gedeelde namen met hun waarde van nu in Help (GLOBAL SCRIPTS), heeft
**+ > Global script**, een oranje **G** in de lijst, en in de blokjes een vinkje **global script** bovenaan het palet,
een vak **Type** bij SET en AT START, een vak **Parameters** bij DEF, een vak **Values** bij use def, en bij een apparaat
een schakelaar **[as code]**.

- [x] **1. De taal** — `VarType` (de vier soorten), `Script.Param` en `Function.params`, `Global.type` en `Assign.type`,
      `Action.textExpr` (een apparaat als code), `Script.global`; de parser leest `global`, `naam: soort = waarde`,
      `def naam(a: int, b):`, `naam(waarden)` en een apparaat als naam; de printer schrijft het terug; `Library` bundelt
      de defs en gedeelde namen van de global scripts; `ScriptChecker` kent de bibliotheek, controleert aanroepen (aantal
      en soort), soorten (`count: int = 2.5` mag niet) en de regels van een global script; de blokjes hebben nieuwe vakken
      (`type`, `textExpr`, de parameters in het code-vak van DEF) en de graaf een vlag `global`.
- [x] **2. Uitvoeren** — de tolk krijgt de gedeelde namen van de kern mee en bindt parameters bij een aanroep (een int
      rondt af); `LogicGraph.withLibrary` voegt de defs van de global scripts bij een diagram (de runner onthoudt dat per
      programma tot een diagram verandert); `LogicStore` bewaart de gedeelde namen (`Shared`) en trekt ze gelijk met de
      global scripts (`syncShared`); de robot doet hetzelfde via zijn verbinding; de Security OS controleert bij Save
      met de bibliotheek en stuurt de gedeelde waarden naar de desktop.
- [x] **3. De app** — Ctrl+Spatie kent `global`, de soorten na `naam:`, `naam: type = value`, defs met hun parameters
      (ook uit global scripts) en de gedeelde namen; de editor kleurt `global` en de soort-woorden; Help heeft nieuwe
      regels onder NAMES, YOUR OWN BLOCKS en GLOBAL SCRIPTS; de blokjes-editor de nieuwe vakken en het vinkje.
- [x] **4. Tests** — `logic_types_params_global` (lezen, terugschrijven, via de blokjes, opslaan en laden, de bibliotheek,
      elf foutmeldingen, Ctrl+Spatie), `logic_shared_names_run` (een hendel zet `lockdown`, een timer in een ander
      diagram ziet het, bewaard na opslaan en laden) en `logic_def_params_run` (`open_door("Lamp", 2)` pulst het apparaat
      dat je meegeeft, en `total: int` rondt 2,5 af naar 2, dan 4). Vijf oude tests verwachtten het oude gedrag (een
      apparaat moest letterlijk zijn, een def zonder parameters) en zijn aangepast. 381 tests, allemaal groen.
- [ ] **5. LEESMIJ, showcase en screenshots** — LEESMIJ.md "De server" heeft de stukken Soorten, Parameters en Global
      scripts. Showcase en screenshots: zie de afronding onderaan hoofdstuk 51.

### 44. De Block Reinforcer (4 november)

Jouw wens: "block reinforcer zodat je base mooi is en ook sterk voor c4".

**Jouw antwoorden**: een **handgereedschap** per blok (sluipen + klik doet de hele muur), een versteviging is **zo sterk
als obsidiaan**, en je ziet het alleen **met de bril en het gereedschap in je hand** (een dun randje).

**Mijn invulling** (zeg het als je het anders wil): de **Block Reinforcer** (drie ijzer, een obsidiaan, een computerchip)
verbruikt per blok één **Reinforcement Plate** (twee ijzer en een obsidiaan = vier platen); sluipen verstevigt alle
aaneengesloten blokken van dezelfde soort, tot `[breach] reinforcerWall` (64), zolang je platen hebt; alleen waar je
mag bouwen (zone); alles wat te breken is behalve bedrock, lucht en vloeistof. Een verstevigd blok telt minstens
`[breach] reinforced` (20) breekpunten; barsten en plamuurbeurten tellen gewoon mee; een ander blok op die plek is de
versteviging kwijt (zelfde regel als barsten, via `CrackData`). De server stuurt wie het gereedschap vasthoudt elke
seconde de verstevigde blokken binnen 24 blokken; de bril tekent er een staalblauw randje om (`ReinforcedRenderer`).
Allebei in de tab Security, na de Zone Marker.

- [x] **1. Gebouwd** — `BlockReinforcerItem`, de vlag `reinforced` in `CrackData.Crack`, `Cracks.reinforce/isReinforced/
      reinforcedNear`, `Blast.baseStrength` (minstens `reinforced`), `ReinforcedBlocksPayload` en `ReinforcedRenderer`,
      twee recepten, twee plaatjes (`generate_textures.block_reinforcer_item()` en `reinforcement_plate_item()`), taal,
      en LEESMIJ.md "Blokken verstevigen: de Block Reinforcer". Test `reinforcer_makes_blocks_strong` (kwarts 2 -> 20,
      één plaat per blok, een tweede keer doet niets, de muur tot de platen op zijn, een frag maakt alleen barsten,
      bewaard na opslaan en laden, een nieuw blok is niet verstevigd).
- [ ] **2. Showcase en screenshots** — showcase **"Block Reinforcer"** (na "Repairing cracks": twee kwartsmuurtjes,
      links verstevigd met sluipen + klik, dezelfde klap op allebei), screenshots `-Ponly=reinforce`
      (`rf01_reinforced_outline`, `rf02_same_blast_left_cracks_right_breaks`). Zie de afronding onderaan hoofdstuk 51.

### 45. De Autoscan van een zone (4 november)

Jouw wens: "Autoscan zodat je makkelijk je bunker kunt scannen en protecten, doet altijd 3 blokken in de muur zodat
eventuele redstone ook wordt meegenomen; als het geen bunker is, is het moeilijker, maar probeer hier ook iets goeds voor
te zoeken."

**Jouw antwoorden**: de knop **Autoscan in de app Zone** (vanaf de kern) én **sluipen + klik met de Zone Marker** (vanaf
die kamer); een open basis krijgt **een doos om de gebouwde blokken** (alles wat geen natuur is) met 3 blokken marge.

**Mijn invulling** (zeg het als je het anders wil): met de marker is het sluipen + rechtsklik **zonder hoeken** (met hoeken
blijft dat "de doos toevoegen"); de scan vult alle kamers die open met elkaar verbonden zijn (lucht en alles zonder
botsing: een open deur, tapijt, een knop; een dichte deur houdt tegen) tot `[security] zoneScanMax` (60.000) blokken en
nooit verder dan het bereik van de kern; loopt hij weg naar buiten, dan is het een open basis en volgt hij de gebouwde
blokken vanaf het aangeklikte blok (de tag `imttech:natural` zegt wat natuur is: steen, aarde, zand, erts, bomen, ook
zandsteen en obsidiaan); de muur is `[security] zoneScanWall` (3) blokken; de kubussen komen uit een gretige verdeling
(eerst oost, dan zuid, dan omhoog) en worden samengevoegd als het er meer dan 48 zijn (de twee die samen het minste lucht
erbij nemen); een zone heeft nu hooguit **48** kubussen (was 16); nog eens scannen voegt alleen toe wat er nog niet in
zat en haalt oude kubussen weg die helemaal in een nieuwe vallen; een kubus die een andere kern in de weg zit, wordt
overgeslagen (dat zegt de melding).

- [x] **1. Gebouwd** — `ZoneScan` (vullen, muur, kubussen, samenvoegen, in de zone zetten), de opdracht `zone_scan` in
      `ZoneApp` (server) met de knop **Autoscan** in de app, `ZoneMarkerItem.autoscan` (sluipen + rechtsklik zonder
      hoeken, op een blok of in de lucht), de tag `data/imttech/tags/block/natural.json`, config en LEESMIJ.md (het stuk
      "Je basis beveiligen: zones"). Test `autoscan_finds_rooms_and_buildings` (een dichte kamer om de kern: 244 blokken
      kamer plus de muur, via de app in de zone; een los kwartsbouwsel: een doos met 3 marge via de marker; een tweede
      scan voegt niets toe; steen is geen gebouw).
- [ ] **2. Showcase en screenshots** — showcase **"Zone autoscan"** (na "Protected zones": een bunker naast de basis
      met de marker, dan de open basis met de knop), screenshots `-Ponly=zones` (`zs01_autoscan_bunker_inside`,
      `zs02_autoscan_bunker_outside`). Zie de afronding onderaan hoofdstuk 51.

### 46. Alle stroom is Forge Energy (4 november)

Jouw wens: "zorg dat de power samen kan werken met Create Crafts & Additions en ook Mekanism". Op mijn vraag hoe de
stroom over moet lopen koos je niet voor een brug via de batterij, maar: **"herwerk alle stroom zodat het FE wordt, hoe
het in de andere mods werkt"**, en de schaal: **"schaal ze zodat het overeenkomt met Mekanism en andere mods"**.

**Mijn invulling** (zeg het als je het anders wil): alles maal tien (een zonnepaneel maal twaalf: 24 FE per tick, precies
het zonnepaneel van Mekanism; een warmtegenerator 100 FE/t; de bril 100.000 FE; het laadstation 300.000), met dezelfde
balans als vroeger. Alles wat bewaard was, rekent zichzelf om bij het laden: een generator, het landingsplatform
(`EnergyUnits` in hun opslag), een robot of drone (`BatteryUnits`), een bril of ander item (`energy_units` op het item,
bij het eerste lezen) en het configbestand (`[power] energyUnits`: staat er 1, dan worden alle stroomgetallen maal tien,
blokken-per-stroom gedeeld door tien, en het bestand bewaard; elk stroomgetal is in de code gemarkeerd met
`energy = true`). Energiecellen staan in procenten en hoeven niets. Samenwerken via de energie-capability van NeoForge
(`FeBridge`): een leverancier (zonnepaneel, warmtegenerator, straks de batterij) is leeg te trekken én duwt elke tick FE
in wat ertegen staat (`fePushRate`, 1000); elk apparaat van ons heeft een buffertje (`feDeviceBuffer`, 4000) dat een
vreemde kabel vult en dat het stroomnet eerst gebruikt; het landingsplatform neemt FE in zijn batterij; een vreemde
leverancier naast een apparaat of kabel van ons is een bron van ons net. De eenheid in het spel heet nu **FE** (was SE).

- [x] **1. Gebouwd** — de schaal (91 getallen in `ImtConfig`, script in de sessie), `Tunable.energy/inverseEnergy`, de
      migratie in `ImtConfigLoader`, `ModDataComponents.ENERGY_UNITS` en `ImtData`, `ChargerBlockEntity` (eenheid +
      duwen), `LandingPadBlockEntity` (eenheid + aannemen), `BotEntity` (eenheid), `FeBridge` en `PowerGrid`
      (buffertje eerst, vreemde bronnen), de taal (FE), LEESMIJ.md "Stroom en kabels". Tests `fe_capability_on_our_blocks`
      en `fe_old_energy_is_converted`.
- [x] **2. Showcase en screenshots** — de bestaande stroomschermen laten FE zien (de showcases van het laadstation,
      de generatoren en de bril); geen nieuwe showcase. Het generatorscherm tekent het netoverzicht niet meer (zie
      hoofdstuk 47).

### 47. De Power Battery (4 november)

Jouw wens: "maak zoals bij Mekanism een multiblock powerbattery, dit is vanaf dan de enige plek waar je de power
netwerk stats kunt zien, dus vb in de zonnepanelen zie je enkel wat dit paneel produceert maar niet hoe het netwerk is."

**Jouw antwoorden**: **zoals Mekanism** (Casing, Glass, Cells erin, Port(s), 3x3x3 tot 18x18x18); het scherm toont
opgewekt / verbruikt / tekort per tick, opslag en capaciteit, een grafiek van de laatste minuten én de lijst van
generatoren en apparaten; het laadstation en de panelen tonen **alleen zichzelf**.

**Mijn invulling** (zeg het als je het anders wil): vier blokken (`battery_casing`, `battery_glass`, `battery_cell`,
`battery_port`) in de tab Power; de doos is een dichte wand van casing/glas/poorten met binnenin cellen of lucht (de
poorten mogen overal in de wand, ook in een hoek); elke cel is `[power] batteryCellCapacity` (1.000.000 FE); de
laagste poort is de baas (bewaart de stroom, meet het net), de andere geven alles aan hem door; laden uit de
generatoren boven hun reserve van 50% tot `batteryChargeRate` (2.000 FE/t); leveren zoals een generator (apparaten
kiezen de voorraad met het meest, dus de batterij); Forge Energy: een vreemde kabel vult of leegt hem, en hij duwt in
vreemde machines ertegen. Het scherm is een gewoon scherm (geen inventaris) dat elke seconde nieuwe cijfers vraagt
(`BatteryRequestPayload` / `BatteryStatsPayload`); de grafiek is 60 punten van 5 seconden; de lijst DEVICES komt uit wat
het stroomnet de laatste 10 seconden zag halen (`PowerGrid.recentDevices`), per soort, alleen wat aan een poort van deze
batterij hangt. Het netoverzicht naast het generatorscherm is weg (de gegevens blijven in het menu, maar worden niet
meer getekend). Recepten: casing 8 ijzer + chip = 8; glas 4 glas + casing = 4; cel 4 redstoneblokken + 4 ijzer + chip;
poort koper, kabel, casing, chip.

- [x] **1. Gebouwd** — `battery/BatteryStructure` (zoeken en nakijken, met uitleg wat er mist), `BatteryCasingBlock`,
      `BatteryCellBlock`, `BatteryPortBlock` + `BatteryPortBlockEntity`, `Batteries`, `PowerGrid.recentDevices`, de
      twee berichten, `client/BatteryScreen`, modellen (`cube_all`), blockstates, texturen
      (`generate_textures.battery_textures()`), recepten, taal, LEESMIJ.md "Stroom en kabels". Tests
      `battery_forms_charges_and_supplies` (vormen, twee cellen, laden uit een paneel boven de reserve, een camera aan een
      tweede poort haalt eruit, glas mag, een gat maakt het ongedaan, dichtmaken brengt de stroom terug) en
      `battery_explains_what_is_missing` (te klein, geen cellen, de laagste poort is de baas, een kabel aan de andere
      poort komt bij dezelfde stroom).
- [x] **3. Jouw opmerking (4 november)**: "het update niet elke tick hoeveel er inzit, ook staan sommige teksten over
      elkaar". Het scherm vraagt nu elke tick nieuwe cijfers (de opslag loopt echt), en de indeling is anders: maakt,
      gebruikt en tekort elk op een eigen regel, het oordeel eronder (hooguit drie regels), de grafiek ernaast, en de
      lijsten over de volle breedte eronder (hooguit drie per lijst, dan "+N more"); het venster is 18 pixels hoger.
- [x] **2. Showcase en screenshots** — showcase 108 **"Power Battery"** (de doos met het raam, het scherm met een
      alvast gevulde grafiek via `BatteryPortBlockEntity.tourHistory`, een gat in het dak en het blok terug, en het
      generatorscherm dat alleen zichzelf toont) en screenshots `-Ponly=battery` (`bt01` de doos, `bt02` het scherm,
      `bt03` 'The wall is not closed').

### 48. Handboeien (4 november)

Jouw wens: "Handboeien zodat je iemand kan vasthouden en meenemen naar een gevangenis".

**Jouw antwoorden**: **rechtsklik ongeveer twee seconden vasthouden** (meteen als hij verblind, slapend of bijna dood is);
geboeid = **niets gebruiken, niet bouwen, geen kisten, traag**; los met een **sleutel of 30 seconden worstelen** (sluipen,
instelbaar); **vastpakken en meetrekken** zoals aan een lijn (tot 3 blokken); **ook Raven-soldaten en guard robots**.

**Mijn invulling** (zeg het als je het anders wil): twee items in de tab Security: **Handcuffs** (vier ijzer en een ketting,
stapel van 16; één paar per gevangene) en de **Handcuff Key** (nugget op een staaf). Wie geboeid is, draagt dat als
attachment `cuffs` (geboeid + wie hem vasthoudt), bewaard (na opnieuw inloggen nog geboeid, na je dood niet: de boeien
vallen op de grond) en naar iedereen gestuurd die hem ziet (het touw en het polsbandje tekent `CuffRenderer`). Weerloos
= meteen: een speler die verblind is (`Flashbang.isBlinded`, nieuw), slaapt of onder `[handcuffs] weakHealth` (een
kwart) zit; een robot die neerligt of door een EMP uit staat; een soldaat onder een kwart. Geboeid: de server weigert
rechtsklikken, slaan, hakken, bouwen, eten en weggooien (de events, op HIGHEST), kisten gaan meteen weer dicht, lopen
op `slow` (de helft), nooit sprinten. Wie boeit, houdt meteen vast: verder dan `leash` (3) wordt hij naar je toe
getrokken (met een sprongetje bij een opstapje), verder dan `letGo` (10) ben je hem kwijt; rechtsklik met de boeien
op een geboeide = loslaten of vastpakken. Losworstelen: `struggleSeconds` (30) sluipen, stoppen loopt dubbel zo snel
terug; de boeien breken dan. Een soldaat die niemand vasthoudt, wurmt zich na dezelfde tijd los; een robot nooit
(zijn toestand is **Handcuffed**, `RobotState.CUFFED`: programma uit, staat stil, `isImmobile`). Creative en
spectator zijn niet te boeien; jezelf ook niet.

- [x] **1. Gebouwd** — `cuffs/Handcuffs` (de toestand `State`, de attachment `CUFFS`, elke tick trekken en worstelen,
      alle blokkades), `HandcuffsItem` (vasthouden via `onUseTick`), `HandcuffKeyItem`, `client/cuffs/CuffRenderer`,
      `BotEntity.isCuffed/onCuffed/onReleased/isImmobile` + `RobotState.CUFFED`, `RavenSoldierEntity.isImmobile` en
      `RavenCombatGoal`, `Flashbang.markBlinded/isBlinded`, config `[handcuffs]` (7 getallen), twee recepten, twee
      plaatjes (`generate_textures.handcuffs_item()` en `handcuff_key_item()`), taal, LEESMIJ.md "Handboeien: iemand
      vangen en meenemen". Tests `handcuffs_catch_block_drag_and_key` (twee seconden, één tick eerder niet, een paar
      boeien op, geen hakken/zetten/slaan, aan de lijn getrokken, te ver = los van de lijn, weer vastpakken, de
      sleutel geeft de boeien terug) en `handcuffs_instant_struggle_robot_and_soldier` (verblind = meteen, losworstelen
      breekt de boeien, een robot die neerligt = meteen en blijft Handcuffed, een soldaat bijna dood = meteen en komt
      mee, losgelaten wurmt hij zich los, de sleutel maakt de robot vrij).
- [x] **2. Showcase en screenshots** — showcase 109 **"Handcuffs"** (Bram twee seconden, de soldaat met één klik en
      achteruit de cel in getrokken, de sleutel, de robot die neerligt), screenshots `-Ponly=cuffs` (`hc01` het
      aftellen, `hc02` geboeid met het touw, `hc03` de soldaat aan het touw).

### 49. Autorisatiecodes en de Auth Console (4 november)

Jouw wens: "autorisatie codes die veranderen, via de laptop of een GSM; oude codes eventueel toestaan maar wel loggen; een
blok waar de code requests binnenkomen om te accepteren of te weigeren" (zoals in Star Wars: "should I hold them?").

**Jouw antwoorden**: de codes werken **op het codepaneel** (`auth.valid`, `auth.old`, `auth.code`); de lengte en de tijd
**stel je zelf in op je security core** (Settings); een **Auth Console**-blok en **accept/deny via laptop of GSM**, met een
wachttijd van 30 seconden; `when request("Gate")` met `request.accepted`; de code is te zien voor **iedereen die de desktop
mag openen**.

**Mijn invulling** (zeg het als je het anders wil): de kern maakt elke `authCodeMinutes` (5) een nieuwe code van
`authCodeLength` (6) cijfers en onthoudt de vorige; Settings zet dat per kern (4-8 cijfers, 1-60 minuten, "old codes"
aan/uit; een andere lengte geeft meteen een nieuwe code). Status toont de code en wanneer de volgende komt (ook via de
laptop, straks de GSM). Op het paneel geeft `LogicEvents.enter` het diagram `auth.valid` (de code van nu, of de vorige
als dat mag) en `auth.old`; de vorige code komt altijd in het logboek. De Auth Console is een derde invoerapparaat
(zelfde kastje als het paneel, een luidsprekerrooster en een knop; naam, stroom, netwerk, EMP zoals een paneel; in
Devices onder AUTH CONSOLES). Een verzoek leeft in het geheugen (`AuthRequests`, één open verzoek per speler per kern),
het lampje blijft oranje, de eigenaar krijgt een bericht, en de app **Requests** (ook op een Computer, als de kern dat
toestaat) toont wie, waar en hoeveel seconden nog, met Accept en Deny. Het antwoord (of de tijd: geweigerd) zet het
lampje groen of rood, komt in het logboek ("Senne accepted Bram at Gate", "Nobody answered ...") en start
`when request("Gate")` met `request.accepted` en `player.name`; `accept()`/`deny()` werken daar ook op de console.

- [x] **1. Gebouwd** — `SecurityCoreBlockEntity` (de code, de vorige, de instellingen, `authTick`, `checkAuthCode`),
      `LogicRunner.Context.withAuth/withRequest` + de gegevens, `Expr.Variable` (4 nieuwe), `Op.REQUEST`,
      `LogicEvents.enter`, `logic/AuthRequests`, `logic/AuthConsoleBlock`, `SecurityOs` (`auth_set`, `req_answer`, de
      gegevens "auth" en "requests"), `DesktopRights`, `client/os/RequestsApp`, Status en Settings in `SecurityOsScreen`,
      Ctrl+Spatie en Help, Devices, EMP, config `[security] authCodeLength/authCodeMinutes/authOldCodes/
      authRequestSeconds` en `[power] authConsoleUpkeep`, blok + item + modellen + plaatjes
      (`generate_textures.auth_console_textures()`), taal, LEESMIJ.md "Autorisatiecodes en de Auth Console". Tests
      `auth_codes_rotate_and_open_the_panel` en `auth_console_requests_are_accepted_denied_or_time_out`.
- [x] **2. Showcase en screenshots** — showcase 110 **"Authorization codes and the Auth Console"**, screenshots
      `-Ponly=auth` (`au01` Status met de code, `au02` Settings, `au03` de app Requests, `au04` de console groen).

### 50. De I.M.T Phone (4 november)

Jouw wens: "een GSM-item, zoals de PC maar minder".

**Jouw antwoorden**: de apps **Status met de code, Logs, Cameras, Devices, Requests (accept/deny) en Messages**;
verbinden **zoals de laptop** (IP + wachtwoord); een gestolen telefoon: **een PIN, en op afstand blokkeren**.

**Mijn invulling** (zeg het als je het anders wil): het item `phone` (tab Security, naast de laptop; stapel van 1) met
op het item alleen het IP, de naam en het nummer van de telefoon (`PhoneLink`); de PIN en of hij geblokkeerd is staan
bij de kern (`SecurityCoreBlockEntity.Phone`), zodat een dief er niets aan heeft. Het inlogscherm van de laptop kreeg
drie standen erbij (`OsLoginScreen.Mode`: verbinden, een PIN kiezen, de PIN intypen); het verbinden zelf deelt de
laptop en de telefoon (`SecurityOs.connect`). De desktop is een sessie op afstand met `DesktopRights.PHONE` (Status en
Logs alleen kijken; Cameras, Devices, Requests en Messages bedienen), bovenaan "I.M.T PHONE"; hij gaat dicht zonder de
telefoon in je rugzak of als de eigenaar hem blokkeert. Drie foute PINs (`[security] phonePinAttempts`) = een minuut op
slot (`phonePinLockSeconds`), in het logboek, en de eigenaar krijgt een alarm. **Messages** is nieuw (ook op de gewone
desktop en op een Computer): de kern bewaart de laatste 60 berichten van I.M.T aan de eigenaar (`say`, alarmen, "X asks
for access", logins, foute PINs) en onderaan stuur je een bericht via de comms (`ImtComms.sendFromPhone`, zonder bril).
In **Access** staan de telefoons van de kern met Block/Unblock (`phone_block`).

- [x] **1. Gebouwd** — `security/PhoneItem`, `PhoneLink`, `ModDataComponents.PHONE`, `security/os/Phones` (verbinden,
      PIN, slot, de telefoon in je rugzak, berichten sturen), `SecurityOs` (`connect`, `openPhone`, de sessie met
      telefoon, de gegevens "phone", "messages" en de telefoons in "access", de opdrachten `phone_block` en
      `msg_send`), `DesktopRights.PHONE`, `SecurityCoreBlockEntity` (telefoons en berichten, met NBT), `LogicRunner` en
      `AuthRequests` (berichten), `ImtComms.sendFromPhone`, `client/os/OsLoginScreen` (de standen), `MessagesApp`,
      `AccessApp` (de telefoons), de kopbalk, drie berichten (`PhoneLoginPayload`, `PhonePinPayload`,
      `PhonePromptPayload`), config `[security] phonePinAttempts/phonePinLockSeconds`, recept, model, plaatje
      (`generate_textures.phone_item()`), taal, LEESMIJ.md "De I.M.T Phone: je kern in je zak". Tests
      `phone_links_sets_pin_and_opens_small_desktop` en `phone_answers_requests_and_can_be_blocked`.
- [x] **2. Showcase en screenshots** — showcase 111 **"I.M.T Phone"**, screenshots `-Ponly=phone` (`ph01` verbinden,
      `ph02` de PIN kiezen, `ph03` de kleine desktop met Status, `ph04` Requests, `ph05` Messages, `ph06` Access met de
      telefoon).

### 51. Torens en bunkers uit kamers (4 november)

Jouw wens: "laat de structures kamer per kamer generen zodat elk gebouw anders is".

**Jouw antwoorden**: **de toren én de bunker**; de lobby, de kluis en het dak (toren) en de ingang en de kluis (bunker)
liggen vast; de rest een willekeurige **keuze, volgorde en aantal** (toren 5-8 verdiepingen, bunker 3-6 zalen);
ongeveer **zes nieuwe kamers** mochten erbij; het satellietwrak blijft zoals het is.

**Mijn invulling** (zeg het als je het anders wil): de klassieke scripts bouwen voortaan ook losse stukken.
`tools/generate_tower_rooms.py` laat `generate_structure.py` elke verdieping apart bouwen (de vloer op 1, de volgende
op 12, de gevel overal op inset 6, zodat ze in elke volgorde passen) en schrijft `structure/tower/`: `lobby` (41 x 13 x
55), elf tussenverdiepingen (41 x 12 x 41: lab, archive, hangar, living, security en de nieuwe gym, greenhouse, cinema,
pool, museum, server_farm) en `top` (41 x 44 x 41: de lasergang, de kluis, het penthouse, het dak en de antennes). Elke
tussenverdieping heeft dezelfde liftkoker en in de koperen zuil een kabel over de volle hoogte (stroom en netwerk); de
beveiligingsverdieping hangt haar stroomkast eraan, de lobby (torentjes, camera) en de top (de lasers en de kluis)
halen er hun stroom uit. Met vijf verdiepingen is de toren precies de klassieke 110 hoog. `TowerLayout.random` kiest
(security altijd, elke kamer hooguit één keer), `BiebiaTowerStructure` (een eigen structuursoort in plaats van de
jigsaw) stapelt de `TowerPiece`s op het oppervlak met één willekeurige draaiing. De bunker: `generate_bunker_rooms.py`
schrijft `structure/bunker/`: `entrance` (35 x 8 x 15), acht zalen van 14 diep (hall en reactor uit de klassieke bunker,
opgeschoven; nieuw armory, lab, cells, garage, mess, comms) en `vault` (15 diep); door elke zaal lopen twee kabels (het
plafond van de hoofdgang voor de lasers, de vloer voor de torentjes). `BunkerLayout.random` kiest (reactor altijd),
`BiebiaBunkerStructure` zoekt de berg zoals vroeger maar met de echte diepte, en probeert bij te weinig berg een zaal
minder; `BiebiaBunkerPiece` kreeg een parameter voor het bestand (de soldaten-markeringen werken in elke zaal). De
klassieke `biebia_tower.nbt` en `biebia_bunker.nbt` blijven (showcase, tests, een oude wereld). `furnish()` in
`generate_structure.py` is per verdieping gesplitst (de klassieke toren blijft byte voor byte gelijk).

- [x] **1. Gebouwd** — `tools/generate_tower_rooms.py`, `tools/generate_bunker_rooms.py`, 23 nieuwe bestanden in
      `structure/tower/` en `structure/bunker/`, `structures/TowerLayout`, `TowerPiece`, `BiebiaTowerStructure`,
      `BunkerLayout`, `RoomPlacer`, `raven/BiebiaBunkerStructure` en `BiebiaBunkerPiece` (zalen), `ModStructures`,
      `worldgen/structure/biebia_tower.json` (de jigsaw-pool weg), LEESMIJ.md "Torens en bunkers uit kamers". Tests
      `tower_rooms_follow_the_rules_and_exist` en `bunker_halls_follow_the_rules_and_exist` (200 indelingen, de regels, de
      maten, de liftkoker, de kern, de docks, de kluis, de lasers, de doorgangen, de soldaten).
- [x] **2. Showcase en screenshots** — showcase 112 **"Towers and bunkers built from rooms"** (twee torens naast elkaar
      en een bunker op vlak land), screenshots `-Ponly=rooms` (`rm01` de torens, `rm02` de bunker van voren, `rm03` van opzij).
- [ ] **3. In een echte wereld** — alleen op vlak land bekeken: `gradlew runStructureCheck` (of `/locate`) in een nieuwe
      wereld om te zien of de toren op het oppervlak komt en de bunker een berg vindt.

### 52. Vuurwapens: de Glock 19 uit onderdelen (6 november)

Jouw wens: "Ik wil dat je guns toevoegd, begin eerst met enkel een Glock. De bedoeling is dat het supper realistisch is":
een pistool uit onderdelen (slede, trekker, hamer, handvat, loop, veren) die elk kapot kunnen, beter materiaal = sterker,
in elkaar te zetten via een GUI met een echt model; een magazijn dat je kogel per kogel laadt (een magazijn van 3 en een
van 5 = 8 kogels, na 3 schoten wisselen); herladen als skill in een skilltree; een holster (Curios of een eigen slot), een
granaatpouch, en één groot wiel om snel te kiezen. Ik stelde zeventien vragen, jij koos.

**Jouw antwoorden** (6 november):
- **Eigen materialen** (polymeer, staal, titanium); beter materiaal = kleinere kans per schot om kapot te gaan ("in code
  moet het percentage om kapot te gaan lager zijn"), en de rest van mijn voorstel (loop, veren, slede, frame, trekker en
  slagpin doen elk iets).
- **Storingen**, met een **tap-rack via R** en een tap-rack-animatie.
- **Een nieuw blok**, "niet met GUI vakjes maar echt een tafel met de onderdelen die je kunt gebruiken en in je inventory
  hebt (een spring zie je wel, een diamond niet), en dan versleep je de onderdelen naar waar het moet komen, met snap in
  place".
- **Merknamen mogen**; het 3D-model moet echt de Glock zijn: eerst de **Glock 19** van airsoftdoctor.be (een Umarex, zwart).
- **Kogels realistisch**: een eigen werkbank waar een huls + buskruit + kogelpunt een 9mm-patroon geeft; bij elk schot
  vliegt de huls eruit en die raap je op met rechtsklik op de grond.
- **Een magazijn vul je** door het vast te houden en per kogel op R te drukken; magazijnen stapelen niet; daarom ook een
  **magazijnpouch** (zoals de holster en de granaatpouch).
- **R opent de magazijnpouch**, je klikt het magazijn dat je wilt; zonder pouch: R op het pistool, scrollen naar het
  magazijn, rechtsklik, en terug naar het pistool ("dit duurt langer, waardoor een magpouch hebben echt belangrijk is").
- **Een patroon in de kamer** (15 + 1), ook met de hand zonder magazijn: een magazijn van 5, herladen = 4 in het magazijn
  en 1 in de kamer; een nieuw magazijn van 6 erin = 1 extra kogel.
- **Skills met skillpoints** om te verdelen (tactiek: waar steek ik veel en waar weinig in); punten krijg je door alles wat
  met een pistool te maken heeft. **Globale skills én per wapen** (aim voor elk pistool, en nog eens voor dit pistool).
- **Curios optioneel**, anders eigen vakjes naast je harnas: "exact hoe ik het bedoel".
- **Trekken uit de holster** laat vallen wat je in je hand had, het pistool komt in dat vakje.
- De granaatpouch (vier vakjes, de volgende komt vanzelf) en het wiel (eigen wiel, linker Alt, de holster en de pouches
  ook op je heup te zien) zoals ik voorstelde.
- **Kogels met een vliegtijd**: heel snel, maar niet meteen raak of mis. De lasers blijven meteen raak (het is licht).

**Mijn invulling** (zeg het als je het anders wil): een Glock heeft geen hamer maar een **slagpin** (striker): dat
onderdeel heet dus zo. Polymeer kan alleen voor het frame en de trekker (zoals echt). Onderdelen zijn **één item per
soort** met hun materiaal erop (`gun_material`) en hun conditie (`part_condition`, 100 = nieuw; een onderdeel uit een
gebruikt pistool houdt die); het pistool onthoudt zijn onderdelen (`gun_parts`), zijn magazijn (`gun_magazine`), of er een
in de kamer zit (`gun_chambered`) en een storing (`gun_jam`). **Slijtage**: per schot per onderdeel een kans van zijn
materiaal (polymeer 4%, staal 2%, titanium 0,8%) op één punt minder; onder 40% versleten (tot 3 keer meer storingen), op 0
kapot. **Storingen**: een huls die klem blijft zitten (0,4% basis, de veren en de slede) en een ketser (0,15%, de slagpin
en de trekker); titanium geeft 0,6 keer zo veel, polymeer 1,5 keer. De loop bepaalt schade en spreiding, de slede de
terugslag, het frame hoe snel trekken en herladen gaan. **Munitie**: 9mm Round (huls van koper, kogelpunt van
ijzerklompjes, buskruit), geperst op de **Reloading Bench** (één per keer, shift-klik een stapel). Hulzen liggen 10
minuten; rechtsklik binnen 1,5 blok raapt alles op. **Magazijn**: 15 (config), elke R één patroon (inhouden gaat door), 6
ticks per patroon. **Herladen zonder pouch**: R = magazijn eruit (8 ticks, in je inventaris), rechtsklik met een magazijn
= erin (12 ticks, in het pistool in je werkbalk dat er geen heeft), en de server laat de slede vanzelf los (7 ticks)
zodra je een pistool vasthoudt met een leeg kamer en patronen in het magazijn. Tap-rack 26 ticks, een patroon met de hand
in de kamer (sneak + R) 22 ticks. **Kogels**: een eigen entity, 16 blokken per tick, een beetje zwaartekracht, volle
schade (5, hoofd x2) tot 24 blokken en de helft op 80; muren, wezens en schildkoepels houden hem tegen; glas breekt
(niet in een zone, geen verstevigd glas). De Glock schiet zo snel als je klikt (max 3 ticks). **De Gunsmith Table**: een
menu zonder zichtbare vakjes (alles gaat als "knop" naar de server: INSTALL + vakje, REMOVE + onderdeel, PUT_GUN,
TAKE_GUN); het scherm tekent de onderdelen in 3D, berekent per onderdeel zijn vak op het scherm (het frame is de basis;
wat ontbreekt ligt uit elkaar geschoven), en klikt vast binnen 16 pixels. Het 3D-model (`tools/generate_gun_models.py`):
een Gen 3 Glock 19 met vingergroeven, rail, vierkante slede met groeven, wit stipje op de korrel en witte U op de keep,
uitwerpopening met de glimmende loop-hood; elk onderdeel in elk materiaal apart (`models/firearm/glock_19/`), en als
item met een override per materiaal. Eigen geluiden (`generate_sounds.py`: schot, klik, slede, magazijn uit/in, patroon,
tap-rack, huls, inslag, holster). Een geladen Glock, de onderdelen per materiaal, de munitie en de banken staan in het
tabblad Weapons; de staven, blends en polymeer in Resources.

- [x] **1. De Glock 19, de onderdelen, de Gunsmith Table, magazijnen en patronen, de Reloading Bench, hulzen en storingen**
      — `guns/` (GunMaterial, GunPart, PartState, GunParts, FirearmType, FirearmItem, GunPartItem, MagazineItem,
      Firearms = schieten + alle handelingen, BulletEntity, CasingEntity, GunsmithTableBlock/BlockEntity/Menu,
      ReloadingBenchBlock/BlockEntity/Menu, de hooks GunSkillHooks en GunGearHooks voor stap 2 en 3), `client/guns/`
      (FirearmModels, FirearmRig, FirearmControl, FirearmRenderer = het pistool en het magazijn in je beeld met alle
      animaties, FirearmHud, BulletRenderer, CasingRenderer, GunsmithScreen = het slepen, ReloadingBenchScreen),
      FirearmFirePayload en FirearmActionPayload (twee kanten: vraag, en hoe lang het duurt), config `[guns]` (45
      getallen), 7 data components, 2 entities, 10 geluiden, 27 recepten, `tools/generate_gun_models.py`, LEESMIJ.md
      "Vuurwapens: de Glock 19 uit onderdelen". Tests in `GunGameTests`: `glock_fires_chambers_next_round_and_ejects_a_casing`,
      `glock_chamber_and_magazine_math` (jouw voorbeeld 5 -> 4 + 1, 6 erin = 7, zonder pouch via uitwerpen, rechtsklik en
      de slede), `glock_jams_and_tap_rack_clears`, `glock_parts_wear_and_break`, `magazine_loads_round_by_round_and_chamber_by_hand`,
      `gunsmith_table_assembles_and_takes_apart`, `reloading_bench_presses_rounds`, `bullets_break_glass_only_when_allowed`,
      `gun_items_carry_their_data`.
- [x] **2. De holster, de granaatpouch en de magazijnpouch** — `guns/GearItem` (de drie items, inhoud in
      `gear_contents`), `GearContents` (een rijtje items met echte equals), `Gear` (waar ze zitten: de attachment `gear`
      of Curios; trekken, wegsteken, aanvoeren, herladen uit de pouch, de klik op een eigen vakje, doodgaan),
      `CuriosGear` (alleen aangeraakt als Curios geladen is; `libs/curios-neoforge-9.5.1+1.21.1-api.jar` om tegen te
      compileren, `data/imttech/curios/` de slots "holster" en "pouch", `data/curios/tags/item/`), `GearActionPayload`
      en `GearClickPayload`, `client/guns/GearWheelScreen` (linker Alt: vasthouden en loslaten, of tikken en klikken),
      `MagPickerScreen` (R met een pouch: klik, 1-3 of nog eens R voor het volste), `GearSlotsOverlay` (de drie vakjes
      rechts van je poppetje, alleen zonder Curios), `GearLayer` (op je heup), `GearControl`; de granaatpouch voert aan
      via `GrenadeItem.use`; LEESMIJ.md "De holster, de pouches en het wiel". Tests in `GearGameTests`:
      `holster_quickdraw_drops_held_item_and_grenade_pouch_feeds`, `mag_pouch_reload_swaps_the_chosen_magazine`,
      `gear_slots_take_and_give_back`. Curios zelf is niet getest (het zit niet in je modpack); de brug is
      geschreven naar de API van 9.5.1.
- [x] **3. De skills** — `guns/GunSkill` (zes skills), `GunSkills` (de attachment `gun_skills`: ervaring, punten,
      niveaus per scope "global" of "glock_19"; een wapenniveau kost zijn nummer in punten, een globaal niveau het
      dubbele; 5% en 8% per niveau, samen tot 70% eraf), `GunSkillsServer` (ervaring geven, een punt uitgeven, een
      kogelkill), `GunSkillHooks` (echt: de tijden en de spreiding, en ervaring voor schieten, herladen, tap-racks,
      patronen en trekken), `SkillLearnPayload`, `client/guns/SkillTreeScreen` (G > Combat > Gun skills, of de toets
      "Gun skills"), `GunSkillsClient` (terugslag en kruisje), config `[guns]` (14 getallen erbij), LEESMIJ.md "De
      skills". Test `skills_earn_points_and_make_reloading_faster`.
- [x] **4. Showcase en screenshots** — showcase 113 **"Glock 19: parts, magazines and casings"**, screenshots
      `-Ponly=glock` (`gl01`-`gl21`: heup, richten, schot, slede, magazijn uit en in, slede los, storing, tap-rack, met de
      hand in de kamer, bekijken, een magazijn vullen, kapotte loop, hulzen, de twee banken, derde persoon); showcase 114
      **"Holster, pouches and the quickdraw wheel"**, screenshots `-Ponly=gear` (`ge01`-`ge08`: de vakjes in je
      inventaris, het wiel, trekken, de magazijnpouch, herladen, op je heup van voren en van achteren); showcase 115
      **"Gun skills"**, screenshots `-Ponly=skills` (`sk01` de boom met punten, `sk02` met niveaus geleerd).

### 53. Uit je showcase-notities van 4 november, en de showcase-inhoud (6 november)

Jouw vraag: "de showcase toont niet juist de nieuwe dingen, kun je dat oplossen; ook wil ik dat je de notities van de showcase
leest, uitvoert en dan leegmaakt".

- [x] **1. Showcase 108, "het glass is niet see-through"** — het blokmodel van Battery Glass stond op `cutout`: dan tekent
      Minecraft elke pixel met een beetje alpha gewoon dicht. Nu `translucent`: het glas mengt echt met wat erachter staat
      (de cellen zijn te zien).
- [x] **2. Showcase 109, "zorg dat de handboeien niet echt een lead zijn maar dat je de speler precies vast hebt"** — geen
      lijn met 3 blokken speling meer: wie je vasthoudt staat elke tick ruim een blok achter je (`[handcuffs] holdDistance`,
      in plaats van `leash`) en draait mee; een klein verschil wordt weggeduwd, raakt hij ver weg dan staat hij in één keer
      weer achter je (`Handcuffs.hold`). Test `handcuffs_catch_block_drag_and_key` aangepast.
- [x] **3. De inhoud van de showcase** — `tools/showcase.py` leest de titels uit `ShowcaseFeatures.java` in de volgorde van
      het bestand, maar de methodes van hoofdstuk 47-52 stonden in een andere volgorde dan het spel ze speelt. Daardoor zei
      het script "de inhoud klopt niet helemaal met het spel" en liet het alles onder "Alles" zien, zonder delen. De methodes
      staan nu in de volgorde van het spel, elk met een eigen kopje (De Power Battery, Handboeien, Autorisatiecodes, De
      I.M.T Phone, Torens en bunkers uit kamers, Vuurwapens: de Glock 19).
- [x] **4. De notities zijn leeg.**

---

### 54. Uit je showcase-notities van 6 november, en een unload (6 november)

Jouw vraag: "Ik wil dat je de notities van de showcase leest, uitvoert en dan leegmaakt. Zijn er dingen niet duidelijk,
vraag het dan. Nog één ding dat ik niet in de notities had gezet maar wel wil: een **unload** voor zowel een gun mag uit een
gun als kogel per kogel uit een mag." Vierentwintig notities bij negentien functies. De kleine en duidelijke zijn in stap 1
gebouwd (hieronder); de grote (Raven-soldaten, torentjes, raiddrones, de toren, het datacenter, de handboeien, het
schild, de koepel, het laserkanon, apps op de kern) komen in stap 2, en bij twee notities heb ik een vraag (onderaan).

**Mijn invulling** (zeg het als je het anders wil):
- **Unload** zit op **U** (met een pistool of magazijn in je hand doet U dan niet de ping): met het pistool gooit U het
  magazijn eruit (ook met een pouch om: het gaat erin als er plek is, anders in je inventaris); zonder magazijn trekt U de
  patroon uit de kamer (de slede naar achteren, de patroon springt er rechts uit, in je inventaris); met een magazijn in je
  hand haalt U er één patroon uit (inhouden gaat door). Zit er nog een magazijn met patronen in, dan kan de kamer niet
  leeg (eerst het magazijn eruit), en bij een storing eerst de tap-rack. Twee nieuwe tijden onder `[guns]`
  (`unloadRoundTicks` 6, `unloadChamberTicks` 16).
- **Gun skills** staan niet meer in het G-menu (jouw notitie 11) maar in het **wiel** (linker Alt), als laatste optie; de
  toets "Gun skills" blijft (standaard geen toets).
- **De pouches openen** (notitie 114): in het wiel staat voor elke pouch die je draagt "Open ...", en er zijn vijf nieuwe
  toetsen zonder standaardtoets (Quickdraw: pistol, Quickdraw: grenade, Open the holster, Open the grenade pouch, Open the
  magazine pouch). Rechtsklik met een holster of pouch in je hand opent hem ook (om hem te vullen voor je hem aantrekt).
  Het scherm (`GearPouchMenu`/`GearPouchScreen`) heeft de vakjes van de pouch boven je inventaris; shift-klik werkt;
  het vakje van je werkbalk met de open pouch erin zit op slot.
- **Het laadstation** (notities 59 en 60): de balk onder de bril, het upgrade-vakje met zijn pijl én het redstone-vakje
  (het vierkant naast de grote balk) zijn weg: het station laadt alleen nog, uit het net. Redstone gaat er dus niet meer
  in (`energyPerRedstone` en `energyPerRedstoneBlock` zijn uit de config); zet een zonnepaneel of warmtegenerator met een
  kabel. Upgrades bouw je in aan de **harnas-werkbank**: de bril is daar het **vierde stuk** op het hologram (boven de
  borstplaat), je legt hem erop en bouwt er een batterij-, scanner- of drone-upgrade in zoals bij het harnas (60 ticks,
  stroom). **Sneak + rechtsklik op de werkbank** kiest het volgende stuk (borst, broek, laarzen, bril: oranje hoekjes op
  het hologram), een gewone klik op de werkbank werkt op dat stuk (ook met iets in je hand). Een bril bouwen kan niet
  (geen blauwdruk: die ligt in de toren).
- **De generatoren** (notitie 61): geen brilvakje meer in het zonnepaneel en de warmtegenerator; alleen de warmtegenerator
  houdt zijn brandstofvakje (rechts), STORED en MADE SO FAR staan groot in het midden. Een bril die er nog in lag bij
  het laden van een oude wereld, is weg.
- **De Gunsmith Table** (notitie 113): de loop, de veren en de slagpin zitten **onder de slede**: zit de slede erop, dan
  kun je er niet bij (geen slepen, geen [x], en erin leggen kan ook niet) tot de slede eraf is, zoals bij een echte
  Glock. De losse onderdelen liggen verder uit elkaar (slede erboven, loop en veren ervoor, slagpin erachter, trekker
  eronder), het pistool is iets kleiner (zoom 6,6), en de namen vallen nooit buiten de tafel of achter het 3D-model.
- **Battery Glass** (notitie 108): zes vlaggetjes per blok (north...down, zoals een hek) zeggen waar nog een Battery Glass
  staat; het model is een "multipart": het glas zelf, plus per vlak vier randstukjes die alleen getekend worden waar geen
  glas naast zit. Zo is een raam één raam met één rand. Ook een blok dat met een commando of in een structuur neergezet
  is, kijkt naar zijn buren (`BatteryGlassBlock.onPlace`).

**Mijn invulling bij stap 2** (zeg het als je het anders wil):
- **Het persoonlijke schild** (notitie 35) is een zachtblauwe bol van ruim een blok om je heen (`PersonalShieldLayer`), die
  iedereen ziet, zachtjes ademt, en flikkert als je batterij onder de 15% zit; in een EMP of onzichtbaar: geen bol.
- **De koepel** (notitie 38) is een hele bol in plaats van een halve: op een berg beschermt hij ook de helling eronder,
  even diep als hij hoog reikt. Getekend als hele bol (`DomeRenderer`), en `contains`/`defend` kijken niet meer naar de
  hoogte.
- **Het laserkanon in je eigen beeld** (notitie 45): de arm is een gestrekte arm met de hand vooruit (geen geknikte
  hand meer), de lichtkring op de handpalm, en de terugslag duwt de hand omhoog (`LaserCannonHand`).
- **Raven-soldaten** (notitie 65): het Raven-geweer is weg (item, model, plaatje, taal). Elke soldaat krijgt bij zijn
  spawn een wapen van de mod: de helft een **Glock 19** (stalen onderdelen, echte kogels met een vliegtijd en hulzen), de
  rest een **laser rifle** of **laser pistol** met een volle cel (dezelfde stralen als die van jou, via
  `LaserGuns.fireFrom`); 35% draagt ook één of twee **granaten** (40% EMP, anders frag): verstop je je vlakbij achter een
  muur, dan gooit hij er een over; op een drone gooit hij een EMP. Zijn wapen valt in 8% van de gevallen. Nieuwe getallen
  onder `[raven]`: `spread` 4°, `grenadeChance`, `grenadeInterval`, `weaponDropChance`. Het pak heeft een vizier op de
  helm, magazijntasjes en een borstband op het vest, een radio met antenne op de rug, een mes op het been en een
  naamplaatje (`generate_textures.raven_soldier`).
- **Twee soorten torentjes** (notitie 66): de **Rocket Turret** (zoals hij was) en de **Laser Turret** (de vroegere
  Security Turret). `TurretKind.RAVEN` is weg; het blok `raven_turret` bestaat nog voor oude werelden en gedraagt zich als
  een Laser Turret ("Laser Turret (old bunker)", in geen enkel creative-tabblad). Een Laser Turret hoort via zijn
  netwerkkabel bij een beveiligingskern en schiet op wie die kern een indringer noemt: in de toren en de bunker
  iedereen (van niemand), bij jou wat Access zegt. De **bunker** heeft daarom nu een eigen **beveiligingskern** bij de
  reactor (van niemand; met de laptop te hacken), elke kabel van de bunker is ook een netwerkkabel, en de zes torentjes
  zijn Laser Turrets. De config `[turrets]` verloor `ravenLaserDamage`, `ravenLaserInterval` en `ravenRange`.
- **Raiddrones** (notitie 67): een raid begint **buiten een basis**. Een basis is elke beveiligingskern (ook die van
  Biebia's) met haar zone, of zonder zone `baseSize` (16) blokken rondom de kern; de drones komen minstens `baseMargin`
  (12) blokken daarbuiten aan, in dezelfde richting verder naar buiten geschoven, en boven de grond als die daar hoger
  ligt. Ze onthouden wie ze kwamen halen (`RaidTarget`, tot 600 blokken) in plaats van alleen de dichtstbijzijnde speler
  binnen 48. Een blok van een **actieve zone** breken ze niet (dat kon al niet): daar gooien ze een **sticky frag** op
  (om de `breachInterval` ticks, het alarm van de zone gaat af); zit hun doelwit in een zone, dan gooien ze een
  **EMP-granaat** op het dichtstbijzijnde torentje of de dichtstbijzijnde laserzender van die kern (`deviceReach` 24,
  om de `empInterval` ticks, alleen met vrij zicht en niet zo dichtbij dat de EMP henzelf raakt).
- **De toren** (notitie 68): een nieuwe verdieping **power** (de generatorkamer) zit er **altijd** in, net als security
  (`TowerLayout.POWER`; 5 tot 8 verdiepingen, dus minstens 3 gekozen). Een kluis van obsidiaan met vier reactorcellen
  (een warmtegenerator in een ring van lava achter glas), de **stroomkast** (de hoofdschakelaar: hij voedt de stijgleiding,
  dus de hele toren) en een echte **serverkast** met CPU, RAM, SSD en fan. De enige doorgang is een **poort van
  rayshields** (zes zenders, drie rijen); rechts ervan de keycardlezer "Power door". Biebia's kern krijgt bij zijn eerste
  tick het diagram **"Generator room"** en de groep **staff** (`TowerGuards.installBiebiaLogic`): een staff-kaart zet
  de poort acht seconden uit, een andere kaart geeft alarm. De **bewakers** (soldaten met de markering `raven_guard`:
  in de lobby en voor de poort) laten bij hun dood zo'n kaart vallen, op dat moment op de kern geschreven; op de
  beveiligingsverdieping staat een gewone soldaat. De **zwakke plek**: in de oostmuur van de kluis zit een luik van twee
  gebarsten deepslate-tegels met het bordje "VENT 3B - DO NOT BLOCK" (obsidiaan hak je minuten, dit in seconden); het
  staat ook in `/home/biebia/power.txt` op de kern. De **uitleg door voorbeelden**: bordjes ("GENERATOR ROOM - STAFF
  CARD", "MAIN CABINET - ALL FLOORS", "LOGIC SERVER - RUNS THE GATE", "GEN 1 - LAVA = HEAT = POWER"), het briefje, en na
  het hacken het diagram in de app Logic. De beveiligingsverdieping heeft geen krachtcentrale meer: de stroom komt via de
  stijgleiding naar de kern en de vier torentjes (vroeger hing het torentje in de noordoosthoek nergens aan). De zenders
  van de poort heten "Power gate" via `TowerGuards.refreshBiebiaDevices` (een kern van niemand heeft geen eigenaar met
  een apparatenlijst). Biebia's bewakers lopen door de laserwanden van hun eigen toren of bunker (in een gehackte toren
  branden ze). De klassieke toren in één stuk (`biebia_tower.nbt`) is niet veranderd.
- **Het datacenter** (notities 75 en 77): alleen het **brein** van I.M.T schaalt anders: zes CPU's (of GPU's) tellen
  samen als één rekenpunt (`cpusPerPower` 6 onder `[logic]`), dus een handvol racks maakt haar traag en een echt
  datacenter (dertig racks) vlot; je redstone (Logic) rekent nog gewoon per CPU, anders werden al je diagrammen zes keer
  zo traag. Racks hoeven niet meer tegen elkaar: alle racks die met een netwerkkabel (of PoE) aan de kern hangen, zijn
  samen één server (`LogicServer`, als "no cable = dead" aan staat, zoals standaard).
- **Apps op de kern** (notitie 105, tweede helft): in **Settings** staat rechts "Apps on this desktop" met een vinkje per
  app (twee kolommen); de eigenaar haalt er apps mee van het bureaublad (`HiddenApps` op de kern, ook via de laptop; de
  telefoon en de Computers hebben hun eigen rechten) en zet ze terug; Settings zelf kan niet weg. Opdracht `app_hide`.
- **De handboeien** (notitie 109): boeien kan **alleen via de rug** (achter hem, de halve cirkel en een beetje meer:
  `Handcuffs.behind`; van voren: "You can only cuff someone from behind"). Wie geboeid is, heeft zijn handen met de
  boeien **op zijn rug** (armhouding `CUFFED`), wie hem vasthoudt heeft zijn handen **vooruit** aan de boeien
  (`HOLDING`; allebei via `enumextensions.json`), en de gevangene staat **recht vóór** de vanger (`holdDistance` 1.4),
  met zijn gezicht naar voren (een speler wordt met een "teleport" op zijn plek omgedraaid). Een korte ketting van je
  handen naar zijn polsen in plaats van het touw.

- [x] **1. Notitie 7, de dreigingsscan** ("de vorm van de pijltjes is niet zo goed, en naar achteren staan ze onder je
      hotbar") — echte pijlpunten (een driehoek met een donker randje en een inkeping, gedraaid in de richting) op een
      **ovaal** rond het midden dat onderaan boven je hartjes en je werkbalk blijft; de afstand staat aan de binnenkant
      van de pijl. Boven een dreiging in beeld wijst dezelfde pijl naar beneden. `ThreatMarkerWidget`; screenshot
      `31_threat_arrows` (`-Ponly=hud`).
- [x] **2. Notitie 11, "gun skills moeten niet in de G-menu staan"** — zie Mijn invulling; `ImtMenuScreen`,
      `GearWheelScreen`.
- [x] **3. Notities 59 en 60, het laadstation en de bril upgraden** — zie Mijn invulling; `ChargingStationBlockEntity`
      (één vakje), `ChargingStationMenu`, `ChargingStationScreen`, `textures/gui/charging_station.png`,
      `HoloPiece.GLASSES`, `SuitWorkbenchBlockEntity` (`selected`, `selectNext`, upgrades van de bril),
      `SuitWorkbenchBlock` (sneak + klik), `doesSneakBypassUse` op het harnas, de bril en de upgrades,
      `SuitHologramRenderer` (de bril met het plaatje van de bril, oranje hoekjes), `WorkbenchHologram` (het kaartje ook
      als je naar de werkbank kijkt). Tests: `workbench_upgrades_the_glasses` (nieuw), `biebia_tower` (het station neemt
      geen redstone of upgrade meer aan), `charging_station_charges_a_cell` (via een generator); `redstone_is_a_small_top_up`
      is weg. Screenshots `w10`-`w13` (`-Ponly=workbench`); showcase 59 en 60 aangepast.
- [x] **4. Notitie 61, de generatoren** — zie Mijn invulling; `ChargerBlockEntity` (`SLOT_FUEL` = 0), `ChargerMenu`,
      `ChargerScreen`, `SolarPanelBlockEntity`, `HeatGeneratorBlockEntity`; test `heat_generator_charges` aangepast
      (geen bril meer); screenshot `pw01` (`-Ponly=power`).
- [x] **5. Notitie 62, het stroomnet** — "zorg dat als een blok open ligt maar je er niet rechtstreeks op kijkt de cable
      x-ray ook werkt": met de xray aan tekent `CableRenderer` dichtbij (24 blokken) **alle** kabels nog eens door alles
      heen, ook open kabels in een andere kamer, en `CableTraffic` de stroom en signalen erover ook. De eerste helft van
      de notitie ("stromende stroom niet door muren zonder x-ray") kon ik niet nadoen: de stroom en signalen over open
      kabels worden met dieptetest getekend en verdwijnen achter een muur; zie de vraag onderaan.
- [x] **6. Notitie 100, de plakgranaten in het creative-vakje** — hun brede kleefkussen stak uit het vakje: in je
      inventaris nu 1,25 in plaats van 1,7 groot (`generate_weapon_models.sticky`); screenshot `ct03_weapons`.
- [x] **7. Notitie 105 (eerste helft), "als je een tabje sluit en dat is de laatste opent die automatisch opnieuw"** — elke
      momentopname van de server opende Status opnieuw als er geen venster meer was. Nu alleen de eerste keer (en als
      de kern net een app wegnam); sluit je zelf het laatste venster, dan blijft het bureaublad leeg.
      `SecurityOsScreen.update`.
- [x] **8. Notitie 108, Battery Glass zoals framed glass van Create** — zie Mijn invulling; `BatteryGlassBlock`,
      `blockstates/battery_glass.json` (multipart), `models/block/battery_glass_edge_*` (24), `battery_glass_pane.png`
      (`generate_textures.battery_textures`). Test `battery_glass_links_to_its_neighbours`; screenshot
      `bt01b_battery_glass_close`.
- [x] **9. Notitie 113, de Glock** ("het 3D-model is nog niet super mooi; je kunt niet goed aan elk onderdeel") — het model
      heeft afgeschuinde bovenranden en een afgeschuinde neus op de slede, de groef van de sledestop, een donkere monding,
      de magazijnknop, de tang achter de greep, de magazijnmond, de pennen van het sluitblok en een schuin hoekje onder de
      trekkerbeugel (`generate_gun_models.py`); de tafel: zie Mijn invulling (`FirearmRig.exploded`, `GunsmithScreen`,
      `GunsmithTableBlockEntity.underSlide`). Screenshots `gl11`, `gl18`.
- [x] **10. Notitie 114, de holster en de pouches** ("de models zien er nog niet geweldig uit; je ziet niet of er iets in zit;
      keybinds voor elke quickdraw en elke pouch") — nieuwe modellen: een gevormde holster met een band en een riemlus,
      een granaatpouch met vier open vakjes (twee naast elkaar, twee diep) en een magazijnpouch met drie open vakjes en
      een elastiek; **wat erin zit zie je**: de granaten steken met hun bovenkant uit de vakjes, de magazijnen staan
      rechtop in de hunne (`GearLayer`, de plekken in `generate_gun_models.GRENADE_POCKETS`/`MAG_POCKETS`). De toetsen
      en het pouchscherm: zie Mijn invulling (`ImtKeys`, `GearControl`, `Gear.openPouch`/`openHeld`, `GearPouchMenu`,
      `GearPouchScreen`, `GearItem.use`, menu `gear_pouch`). Het wiel is een ovaal dat meegroeit met het aantal opties.
      Test `pouch_screen_moves_magazines_in_and_out`; screenshots `ge08b`-`ge08d` (van dichtbij), `ge09` (het scherm).
- [x] **11. De unload** — zie Mijn invulling; `Firearms.ActionKind.UNLOAD_ROUND` en `UNLOAD_CHAMBER`, `FirearmControl`
      (U), `FirearmRenderer` (de patroon eruit, de slede), `ImtKeys.GUN_UNLOAD`, `ImtClientEvents` (de ping). Test
      `unload_takes_rounds_out_of_magazine_and_chamber`; screenshot `gl14b_magazine_unloading`; showcase 113 en 114
      aangepast.
- [x] **12. Notitie 35, het persoonlijke schild** — zie Mijn invulling bij stap 2; `PersonalShieldLayer` (op de
      spelerrenderer, naast `GearLayer`); screenshot `99b_personal_shield_sphere` (`-Ponly=dome`); showcase aangepast.
- [x] **13. Notitie 38, de koepel ook onder de generator** — `DomeGeneratorBlockEntity.contains`, `ImtDomes.defend`,
      `DomeRenderer` (hele bol); screenshots `97_dome_*`; showcase aangepast.
- [x] **14. Notitie 45, het laserkanon in je eigen beeld** — `LaserCannonHand` (gestrekte arm); screenshots `43*`.
- [x] **15. Notitie 65, de Raven-soldaten** — zie Mijn invulling bij stap 2; `RavenSoldierEntity` (het wapen, de
      granaten, `performRangedAttack` met een kogel of een straal), `RavenCombatGoal` (de granaten), `LaserGuns.fireFrom`
      en `scatter`, `ModItems` (het Raven-geweer weg), `generate_textures.raven_soldier`, config `[raven]`. Test
      `raven_soldier_shoots_players` aangepast (een Glock of een lasergeweer); screenshots `h01`-`h03` (`-Ponly=raven`);
      showcase "Raven soldiers" aangepast.
- [x] **16. Notitie 66, twee soorten torentjes** — zie Mijn invulling bij stap 2; `TurretKind` (ROCKET, SECURITY),
      `TurretBlock`, `TurretBlockEntity`, `TurretTargeting`, `TurretRenderer`, `ModBlocks` (`raven_turret` als Laser
      Turret), `ModCreativeTabs`, de taal, config `[turrets]`; `generate_bunker.py` en `generate_bunker_rooms.py` (de
      kern bij de reactor, `security_turret`, elke kabel ook netwerk), de bunkerbestanden opnieuw gemaakt. Tests
      `laser_turret_of_nobody_shoots_players` (was `raven_turret_shoots_players`), `biebia_bunker` (zes Laser Turrets en
      één kern), `bunker_turrets_have_power`, `creative_tabs_hold_every_item` (het oude torentje staat nergens),
      `StructureCheck`; screenshots `t01`-`t05` (`-Ponly=turrets`), `b01`-`b09` (`-Ponly=bunker`); showcase
      "Automatic turrets" aangepast.
- [x] **17. Notitie 67, de raiddrones** — zie Mijn invulling bij stap 2; `ImtRaids` (`basesNear`, `outsideBases`),
      `RaidDroneEntity` (`raidTarget`, `throwStickyFrag`, `empDefences`), config `[raids]` (`breachInterval`,
      `empInterval`, `deviceReach`, `baseMargin`, `baseSize`). Tests `raid_starts_outside_a_base` en
      `raid_drone_blasts_a_zone_wall` (nieuw), `raid_drones_come_for_you` telt alleen zijn eigen drones; showcase
      "Raid drones" aangepast.
- [x] **18. Notitie 68, de toren** — zie Mijn invulling bij stap 2; `generate_tower_rooms.py` (`build_power_room`,
      `security_floor` zonder krachtcentrale, de markeringen `soldier` en `guard`), `structure/tower/power.nbt` (nieuw)
      en de andere stukken opnieuw gemaakt, `TowerLayout.POWER`, `TowerPiece` (de markeringen), `RoomPlacer.placeRoom`,
      `structures/TowerGuards` (nieuw: de bewaker, de staff-kaart, het diagram, het telefoonboek), `RavenSoldierEntity`
      (`keycardGuard`, de kaart valt), `SecurityCoreBlockEntity.ensureSystem` (Biebia's diagram), `LogicRunner.tick`,
      `SystemDefaults` (`/home/biebia/power.txt`), `LaserWallBlock` (de bewakers lopen erdoor). Tests
      `tower_rooms_follow_the_rules_and_exist` (power altijd, met alles erin), `tower_guard_drops_a_staff_keycard` en
      `biebia_core_installs_the_generator_room_logic` (nieuw); screenshots `pr01`-`pr03` (`-Ponly=powerroom`); showcase
      "Towers and bunkers built from rooms" aangepast.
- [x] **19. Notities 75 en 77, het datacenter** — zie Mijn invulling bij stap 2; `LogicStats.brainPower`, config
      `cpusPerPower`, `LogicServer` (racks via het netwerk); tests `DatacenterGameTests` (het brein per zes CPU's) en
      `brain_reaction_time` aangepast.
- [x] **20. Notitie 105 (tweede helft), apps op de kern** — zie Mijn invulling bij stap 2; `SecurityCoreBlockEntity`
      (`hiddenApps`), `SecurityOs` (`app_hide`, "hidden" in de momentopname), `SecurityOsScreen` (`shows`, de lijst in
      Settings, 400 x 200). Test `owner_removes_apps_from_the_core_desktop`; screenshot `au02_settings_auth_codes`
      (`-Ponly=auth`); showcase "Security OS desktop" aangepast.
- [x] **21. Notitie 109, de handboeien** — zie Mijn invulling bij stap 2; `Handcuffs` (`behind`, `hold`, `face`),
      `CuffArmPoses` (nieuw), `enumextensions.json`, `CuffRenderer`, `RavenSoldierRenderer`, config `holdDistance`,
      de taal. `HandcuffGameTests` aangepast (van voren mag niet, de gevangene vóór je, dezelfde kant op); screenshots
      `hc01`-`hc03` (`-Ponly=cuffs`); showcase "Handcuffs" aangepast.

De notities zijn leeg (alleen de kop staat er nog).

**Vragen**:
- Notitie 76 (Logic: names, sums and loops) zegt alleen "sd". Een tikfout, of bedoelde je iets?
- Notitie 62, "stromende stroom of andere dingen niet door muren zonder x-ray": waar zag je dat? De gele streepjes en het
  vakje over een open kabel verdwijnen bij mij achter een muur; over een kabel in een muur zie je ze alleen met de xray
  aan. Zag je het met een shaderpack (Iris/Oculus), of iets anders dan de kabels (de rand van een zone, een laserstraal)?

---

### 55. De lasergeweren in het Glock-systeem: onderdelen, de Laser Workbench, slings en skills (7 november)

Jouw wens: "Kun je zorgen dat de laser weapons ook in het glock wapen systeem zitten. Ik wil dat je de pistol kunt
holsteren en dat de ander soort guns een sling hebben die je kunt rondhangen, elke gun moet zijn eigen sling hebben. Je
kun deze guns met sling dan ook weer in een gun met sling slot steken of als er curios is in een curios slot. Deze guns
met sling kun je dan weer quickdrawen, als je quickdrawt drop je ook weer wat je vast hebt tenzij het een ander gun is
dat je kunt holsteren of wegdraaien met de sling dan doe je dat dus bijvoorbeeld een glock en een holster en dan een
laser rifle. Heb je de glock vast en pak je je laserrifle dan holster je de glock en neem je je laserrifle. Zorg dat die
guns ook aan de weapons skilltree worden gekoppeld." Ik stelde twee keer vier vragen, jij koos.

**Jouw antwoorden** (7 november):
- **De sling hangt aan de gun**: "bij het bouwen van je gun heb je ook de optie om er een sling aan te hangen, als deze
  er aanhangt kun je die in de slingslot doen anders niet. Al de bestaande laserguns wil ik ook dat ze gecraft worden in
  een nieuwe soort tafel op dezelfde manier als de glock. Deze onderdelen moeten wel heel duur zijn, ze moeten endgame
  zijn."
- **Twee sling-vakjes**: een op je rug, een voor je borst.
- **Alles van het Glock-systeem**: "alles maar iets minder slijtage en ook geen jam want een laser gun heeft geen
  hulzen. Dit zijn endgame items en zijn ook heel durable" (dus ook de cellen in de magazijnpouch, uit mijn voorstel).
- **Dezelfde Holster** voor het laserpistool, één pistool tegelijk.
- **Zes onderdelen plus de sling** (frame, receiver, emitter, optics, capacitor, trigger module; het sling-vak alleen bij
  rifle, sniper en shotgun).
- **Twee kwaliteiten: titanium en netherite.**
- **Nog duurder**: ook een Nether Star bij het frame, en netherite onderdelen twee netherite ingots.
- **Slijtage**: "Alles kan verslijten zoals bij de Glock. De netherstar kun je er dan uit halen via de GUI, de netherstar
  is ook een item dat je er visueel moet insteken via de GUI."

**Mijn invulling** (zeg het als je het anders wil): de Nether Star is dus geen ingrediënt maar een eigen vak op de tafel
(`core`): zichtbaar erin, eruit te halen, slijt niet; zonder kern schiet het geweer niet. Wat elk onderdeel doet (het frame
trekken, de receiver terugslag en herladen, de emitter schade en bereik, de optics spreiding, de capacitor lading per
schot, de trigger vuursnelheid) en wat versleten doet: geen storingen maar zijn eigen cijfer tot 50% slechter
(`laserWornPenalty`); slijtage de helft van de Glock (`laserWearFactor` 0,5; netherite 0,3% in `wearChance`, dus 0,15%).
Titanium is de maat: de getallen onder `[weapons]` gelden voor een titanium geweer, netherite doet het iets beter (de
vierde waarden in de materiaalrijtjes onder `[guns]`). De prijs: titanium onderdelen van titaniumstaven met diamanten,
chips, een Laser Module of een Laser Cannon Capacitor (een pistool iets minder); netherite = het titanium onderdeel plus
twee Netherite Ingots (een gebruikt onderdeel wordt daarmee nieuw); een complete netherite rifle kost zo 12 netherite
ingots, zo'n 20 titanium, 10 diamanten, chips, een Laser Module, een capacitor en een Nether Star. De oude recepten van
de vier geweren zijn weg. Een geweer uit een oude wereld krijgt complete titanium onderdelen en een kern zodra het in een
inventaris ligt (het was al van jou); de geweren van Raven-soldaten hebben onderdelen maar geen kern (die zou een te
mooie buit zijn; de soldaat schiet er wel mee). Trekken van je sling duurt 18 ticks (`slingDrawTicks`), uit de holster 12.
Wat je vasthoudt bij het trekken gaat in zijn eigen plek als dat kan (jouw keuze voor guns; ik doe het ook voor een
granaat, magazijn of cel: terug in zijn pouch), anders valt het. De sling-vakjes staan in je inventaris onder het
2x2-craftraster naast het vakje van je andere hand (naast het harnas was geen plek meer); met Curios het slot "sling"
(twee vakjes; de tag laat de drie lange geweren toe, of er een sling aan hangt controleert de mod zelf bij het trekken
en wegsteken; Curios zelf blijft ongetest, het zit niet in je modpack). Het eerste vakje = je rug, het tweede = je borst,
met een leren band over je schouder. Voor de lasergeweren gelden de skills Reload (de cel wisselen), Recoil control,
Steady aim en Quickdraw; Clearing en Magazine handling zijn alleen voor de Glock. In de skilltree kies je het wapen
met tabbladen (het begint op wat je vasthoudt).

- [x] **1. De onderdelen, de Laser Workbench, de kern, de sling en de slijtage** — `weapons/LaserPart` (de acht vakken:
      zes onderdelen die slijten, de kern en de sling), `LaserParts` (het data component `laser_parts`, zoals GunParts),
      `LaserPartItem` (24 items `laser_<geweer>_<onderdeel>`, kwaliteit in `gun_material`, conditie in `part_condition`),
      `GunSlingItem`, `LaserWorkbenchBlock`/`BlockEntity` (de regels; `guns/PartsTable` is wat de Gunsmith Table en de
      werkbank delen, `GunsmithMenu` werkt voor allebei), `GunMaterial.NETHERITE`, `LaserGunItem` (onderdelen, `complete`,
      `loaded`, `works`, de oude geweren), `LaserGuns` (compleet en heel of niet schieten, de onderdelen in de sommen,
      slijtage, `BUSY` tijdens het trekken, herladen uit de pouch met `GunReloadPayload(start, value)`), `Firearms`
      (DRAW ook voor een lasergeweer, `DRAW_FROM_SLING`), config `[guns]` (`laserWearFactor`, `laserWornPenalty`,
      `slingDrawTicks`, een vierde waarde in de vijf materiaalrijtjes), de recepten (48 onderdelen, de werkbank, de sling;
      de vier geweren weg), de creative tab, de Raven-soldaten; client: `client/guns/PartsTableScreen` (het slepen, uit
      GunsmithScreen gehaald), `GunsmithScreen` en `client/weapons/LaserWorkbenchScreen` erop, `LaserRig` (de vakken uit
      het model zelf gemeten, de exploded view), `LaserGunModels` (een geweer uit zijn onderdelen tekenen), `WeaponModels`
      (per onderdeel per kwaliteit, `..._moving`, de kern, de sling, de band), `GunRenderer` (alleen wat erin zit),
      `GunHud` (incompleet, geen kern, kapot), `tools/generate_weapon_models.py` (elk geweer in zes onderdelen, twee
      kwaliteiten, de kern, de sling, de losse onderdelen als item met een override per kwaliteit; vier nieuwe tegels:
      netherite, netherite_dark, leather, star), `tools/generate_gun_models.py` (de werkbank, het slot-plaatje van de
      sling). Tests in `LaserWorkbenchGameTests`: `laser_workbench_builds_a_rifle_from_parts_core_and_sling`,
      `laser_parts_wear_and_a_broken_part_stops_the_gun`, `netherite_upgrade_recipe_and_old_guns_get_parts`;
      `laser_gun_hits_and_headshots` klopte weer zodra titanium de maat werd.
- [x] **2. De holster, de slings, het wiel en de cellen** — `guns/Gear` (de sling-vakjes in de attachment `gear`, `sling`,
      `setSling`, `slingAccepts`, `drawSling`, `stow`: wisselen in plaats van laten vallen, `click` voor vijf vakjes),
      `CuriosGear` (het slot "sling"), `GearItem` (de holster neemt het laserpistool, de magazijnpouch cellen),
      `GearActionPayload` actie `ACTION_DRAW_SLING`, twee toetsen (`draw_sling_back`, `draw_sling_chest`),
      `data/imttech/curios/slots/sling.json` en `data/curios/tags/item/sling.json`; client: `GearSlotsOverlay` (de twee
      vakjes onder het craftraster), `GearWheelScreen` (je geweren op je slings, "Put away" ook naar je sling),
      `GearControl`, `GearLayer` (het laserpistool in de holster, de cellen in de pouch, de geweren op je rug en borst met
      hun band), `MagPickerScreen` (ook voor cellen), `GunGearClient.openCellPicker`, `GunControl` (R met een cel in je
      pouch kiest). Tests in `GearGameTests`: `laser_pistol_holsters_and_slung_guns_swap_on_quickdraw`,
      `energy_cells_fit_the_magazine_pouch_and_reload_from_it`.
- [x] **3. De skills voor alle vijf** — `GunSkill.appliesTo`, `GunSkillHooks` (`scopeOf`, `weaponScopes`, de namen),
      `GunSkills.factor` met een scope als naam, `GunSkillsServer` (leren voor elk wapen, een laserkill telt), `LaserGuns`
      (Reload en Steady aim), `GunControl` en `GunHud` (Recoil control en het kruisje), `SkillTreeScreen` (tabbladen).
      Test `laser_guns_have_their_own_skill_scope`. 419 tests, allemaal groen.
- [x] **4. Showcase en screenshots** — showcase 116 **"Laser guns from parts: the Laser Workbench, the Nether Star and the
      sling"** en 117 **"Laser pistol in the holster, guns on your slings, cells in the pouch"**; screenshots
      `-Ponly=laserparts` (`lp01`-`lp11`: de rifle en de netherite sniper uit hun onderdelen, zonder kern, kapot, het
      pistool, de werkbank, de onderdelen in je inventaris, derde persoon) en `-Ponly=sling` (`sl01`-`sl13`: de
      sling-vakjes, het wiel, trekken van je rug, de cellen kiezen, de Glock terug met de rifle op je rug, van achteren en
      van voren, van dichtbij, de skilltree met het tabblad Rifle). LEESMIJ.md "Lasergeweren uit onderdelen: de Laser
      Workbench, de Nether Star, de slings".

### 56. Het wiel compacter, en slepen in plaats van klikken (7 november)

Jouw vraag: "Ik vind de quick draw menu niet super handig, je moet te veel lezen voordat je weet wat je kunt doen, het
moet compacter zijn en sneller te zien wat wat doet; ik wil ook dat je kunt slepen met je muis en zo selecteert en niet
een knop moet indrukken."

- [x] **1. Waarom je moest klikken** — het wiel keek bij het openen naar `KeyMapping.isDown()` van de wieltoets, maar die
      zegt altijd "nee" zolang er een scherm open is (de toets telt alleen in het spel zelf, en Minecraft zet bij het openen
      van een scherm bovendien alle toetsen op "los"). Daardoor stond het wiel altijd in de klikstand, al zei de hint soms
      iets anders. Nu luistert het scherm zelf naar het loslaten (`keyReleased`, en `mouseReleased` als je een muisknop als
      wieltoets kiest) en vraagt het als vangnet elke tick rechtstreeks aan GLFW of de toets nog ingedrukt is
      (`wheelKeyDown`). Houd Alt vast, schuif naar een tegel, laat los: gekozen. Tik je Alt kort (korter dan 200 ms,
      zonder iets aan te wijzen), dan blijft het wiel staan: dan klik je, of je sleept vanuit het midden naar een tegel en
      laat daar de muisknop los (indrukken kiest niets meer; een klik in het midden, rechtsklik, Esc of Alt sluit). In de
      showcase, die toetsen nadoet zonder GLFW, kiest het script zoals eerst.
- [x] **2. Compacter** — `GearWheelScreen` opnieuw: geen tekstblokken meer maar tegels van 36 px met het item er twee keer
      zo groot in en alleen een getal erop (schoten "15+1", "23", of "--" zonder cel, rood als het leeg is; hoeveel
      granaten). Elke soort heeft zijn vaste plek, zodat je niet hoeft te zoeken: bovenaan je pistool, rechts je geweren op
      je slings (één op 90°, twee op 60° en 120°), links elke granaatsoort (30° uit elkaar, gecentreerd), onderaan
      "wegsteken" (wat je vasthoudt met een pijltje omlaag erop; een groot grijs pijltje als je hand leeg is). Wat je
      aanwijst lees je in het midden: de naam, de munitie of het aantal, en wat er met je hand gebeurt ("Stone drops",
      "Glock 19 into the Holster"; ook als het past in het vakje dat net leegkomt, zoals de server het doet). Een
      stippellijn wijst van het midden naar de tegel. De pouches openen (geen quickdraw) staat als twee kleine
      knoppen rechts naast de ring, met "2/3" gevuld. De ring groeit mee met het scherm (64-96 px) en
      wordt groter als tegels elkaar anders raken; de hint ("Slide to a tile and let go of Left Alt", met je echte toets)
      staat erboven. Lege plekken blijven grijs staan ("Holster: empty", "Slings: empty"), zodat je ziet wat er kan.
- [x] **3. Showcase 115** koos een granaat op volgnummer 1, maar sinds hoofdstuk 55 stond daar de lege sling-tegel. Nu op
      zijn actie (`wheelChooseAction(ACTION_DRAW_GRENADE)`). LEESMIJ.md "De holster, de pouches en het wiel" aangepast.
- [x] **4. De skilltree alleen op zijn toets** (jouw opmerking daarna: "de skill points menu moet niet in de quickdraw
      zitten, dit moet enkel met een shortcut, ook niet in het G-menu") — de knop Skills is uit het wiel; het G-menu had
      hem al niet meer sinds hoofdstuk 54. De toets "Gun skills" staat nu standaard op **F6** (elke letter is al bezet;
      verzetten kan in Opties > Besturing), en het bericht "Skill point earned" noemt die toets (`Component.keybind`,
      dus altijd je echte toets). De lang-sleutels van de wielknop zijn weg; LEESMIJ.md en de showcase-teksten aangepast.
- [x] **5. Alleen zolang je Alt vasthoudt** (jouw opmerking: "zorg ook dat die menu niet toggle-baar is, maar om die te
      openen moet je altijd de shortcut ingedrukt hebben") — de tik-stand uit stap 1 is weg: het wiel staat er zolang je
      Alt vasthoudt, loslaten kiest wat je aanwijst (of sluit, als je niets aanwijst); een tegel aanklikken kan nog, Esc en
      rechtsklik annuleren. Na het sluiten moet je Alt eerst echt loslaten voor het opnieuw opent (`GearControl`: Minecraft
      zet een ingehouden toets bij elke toetsherhaling weer "aan", anders sprong het wiel na een klik meteen weer open). De
      showcase en de screenshots houden het wiel open via `GearWheelScreen.script` (`DevScript.wheel`), want een nagedane
      toets is voor GLFW niet ingedrukt.
      Ook gevonden: `DevScript.close` sloot schermen met `setScreen(null)`; een containerscherm (het pouchscherm, `ge09`)
      meldde dat niet aan de server, die zijn menu open hield en daarna werkbalkwijzigingen niet meer naar het spel
      stuurde, zodat `sl03`/`sl04` sinds hoofdstuk 55 de Glock lieten zien in plaats van de rifle. Nu sluit het als Esc
      (`onClose`).
- [x] **6. Doorlopen met het wiel open** (jouw opmerking: "zorg dat je tijdens dat je quickdraw-menu open is nog steeds
      kunt wandelen, dat maakt dat je beter cover kunt zoeken en je gun tegelijk pakken") — zodra een scherm open is, zet
      Minecraft alle toetsen op "los" en zet het nieuw ingedrukte toetsen niet meer "aan" (alleen loslaten komt nog door),
      dus stond je stil. Het wiel geeft nu lopen, springen, sluipen en sprinten zelf door (`keyPressed`/`keyReleased`, en
      elke tick als vangnet wat GLFW echt ingedrukt ziet, ook wat je al vasthield toen het openging; sluipen of sprinten op
      "toggle" laat het met rust). Het spel pauzeert niet (`isPauseScreen` was al nee). De camera draait niet mee: je muis
      kiest. Nieuw in LEESMIJ.md.
- [x] **7. Je stond toch stil** (jouw opmerking daarna: "kun je zorgen dat je kunt wandelen terwijl je Alt drukt en je
      wapens wil pakken") — stap 6 zette de toetsen wel "aan", maar NeoForge geeft de bewegingstoetsen van Minecraft zelf
      (lopen, springen, sluipen, sprinten) in `Options` de context "alleen in het spel" (`KeyConflictContext.IN_GAME`), en
      zijn `KeyMapping.isDown()` zegt dan nee zolang er een scherm open is, hoe de toets ook staat. Zolang het wiel open is
      tellen die toetsen nu overal (`UNIVERSAL`, `GearWheelScreen.letMovementThrough`); bij het sluiten krijgen ze hun
      eigen context terug (`removed`). Gemeten met echte toetsaanslagen: nieuw `client/dev/DevKeys` zet Windows-
      toetsberichten (WM_KEYDOWN/WM_KEYUP) in de rij van het spelvenster via LWJGL's `User32.PostMessage`, zodat GLFW ze
      verwerkt als van een toetsenbord (een nagedane `setDown` ziet GLFW niet, en het wiel vraagt het aan GLFW). De
      rondleiding heeft het stuk `wheelwalk` (`gradlew runVisualCheck -Ponly=wheelwalk`, screenshot
      `ge12_wheel_while_walking`): het drukt Alt en dan W in, en al lopend Alt, en logt de afstand ("[dev] wheelwalk
      GOED/FOUT"): voor de fix 0,00 blokken, erna 4,27 (Alt eerst) en 4,96 (al lopend) in een seconde; na het loslaten
      loop je door zolang je W vasthoudt.

### 57. Lasergeweren laden en lossen zoals de Glock (7 november)

Jouw vraag: "Zorg ook dat je de laser weapons kunt unloaden, zorg ook dat je de laserweapons niet meer kunt herladen
zoals vroeger, ze moeten nu werken zoals de Glock werkt."

- [x] **Wat weg is**: R pakte zonder pouch de volste cel uit je inventaris (hoofdstuk 35). Dat doet hij niet meer.
- [x] **Zoals de Glock** (`LaserGuns`): R met een vollere cel in je magazijnpouch kiest die (hoofdstuk 55, ongewijzigd);
      zonder zo een cel haalt R de **cel eruit** (`eject`, `GunReloadPayload` met `EJECT`); een cel erin doe je met
      **rechtsklik met de cel in je hand** (`EnergyCellItem.use` → `insertCell`): in het lasergeweer in je werkbalk dat
      er geen heeft, dan de rest van je inventaris; na `gunInsertPart` (0,75) van de herlaadtijd zit hij erin (de klik
      hoor je), en het telt als herladen voor je skills. **U** haalt de cel eruit (`GunControl.unload`): na `gunEjectPart`
      (0,5) van de herlaadtijd komt hij in je magazijnpouch als daar plek is (`Gear.storeMagazine`, die nam al cellen),
      anders in je inventaris. Met een lasergeweer in je hand doet U niet ook de ping. Zonder cel zegt R "No cell in the
      gun: scroll to a charged cell and right-click it" en U "Nothing to unload"; de HUD zegt "NO CELL - RIGHT-CLICK ONE"
      (of "- PRESS R" met een cel in je pouch) en "CELL OUT" tijdens het lossen.
- [x] **De animatie** (`GunRenderer`): alleen eruit is een eigen, korter verloop (kantelen, de cel schuift eruit en valt
      weg, je linkerhand gaat mee en terug) zonder nieuwe cel en zonder de klik; `GunControl.ejectOnly` zegt welk.
- [x] **Tests**: `laser_gun_reloads_with_the_fullest_cell` is nu `laser_gun_ejects_its_cell_and_takes_one_by_hand`
      (R zonder pouch = eruit, rechtsklik = erin, geen tweede geweer zonder cel = niets, dan schieten); nieuw
      `laser_gun_unloads_its_cell_into_the_pouch` (in het lege vakje, en in je inventaris als de pouch vol is). 420 tests,
      allemaal groen. Showcase 100 en de screenshots `g07`/`g08` herladen nu uit een magazijnpouch; nieuw `g08b` (U),
      `g08c` (rechtsklik met de cel) en `g08d` (het geweer weer geladen). LEESMIJ.md (de tabel, "Munitie: energiecellen",
      "Cellen in de magazijnpouch") en de tooltips aangepast.

### 58. De magazijnpouch: zes magazijnen (7 november)

Jouw vraag: "zorg dat een magpouch ook groter is (6 mags)".

- [x] `magPouchSize` staat nu standaard op **6** (was 3; `[guns]`). Let op: een bestaand configbestand houdt zijn eigen
      getal, zet het daar zelf op 6 (of haal de regel weg). Een pouch uit een oude wereld krijgt vanzelf zes vakjes
      (`GearContents.sized`). Het model van de pouch heeft nu twee rijen van drie vakjes (`tools/generate_gun_models.py`,
      `MAG_POCKETS` ook in `GearLayer`), zo diep als de granaatpouch; het keuzescherm (R) zet zes kaarten in twee rijen van
      drie en zegt "press 1-6"; het pouchscherm en het wiel ("2/6") rekenden al met de maat. Test
      `laser_gun_unloads_its_cell_into_the_pouch` vult de pouch helemaal (hoeveel vakjes er ook zijn). LEESMIJ.md aangepast.

### 59. De onderdelen in je inventaris (7 november)

Jouw vraag: "de items voor de guns te maken hebben een slechte inventory 3D model, in de game weet ik niet of het goed
is. Kun je ingame controleren en dan alles oplossen."

- [x] **Wat er mis was** — in het spel (`ct03_weapons`, `lp10`) stonden de losse onderdelen plat van opzij, en veel
      ervan naast of buiten hun vakje: de loop, de veer en de slagpin van de Glock als een streepje links in het vakje,
      het magazijn naar rechts, de capacitors van de lasergeweren als een bruin vlak half buiten beeld, de emitter van het
      laserpistool onzichtbaar (een ring van opzij is 1 pixel dik), de iron sights van het pistool en de shotgun als twee
      stipjes ver uit elkaar. De oorzaak: Minecraft past de verschuiving ("translation") van een display-houding toe NA
      de draaiing, in de richtingen van je scherm (`ItemTransform.apply`); de generatoren schoven langs de assen van het
      model, dus een onderdeel dat in het geweer vooraan zit (z = 2) ging de diepte in in plaats van opzij.
- [x] **De wiskunde op één plek** — `tools/model_display.py`: dezelfde draaiing als het spel (JOML `rotationXYZ`, met de
      eigen draaiing van een doosje zoals de schuine greep erbij) en `fit()`, dat de acht hoeken van elk doosje
      projecteert en de schaal en de verschuiving uitrekent die het onderdeel precies in het midden van zijn vakje zetten,
      zo groot als er past (14 van de 16 pixels, hooguit 4,5 keer, zodat een trekker een trekker blijft). Beide
      generatoren gebruiken het (`centred_display` in `generate_gun_models.py` en `generate_weapon_models.py`). Het
      magazijn, de munitie, de holster, de pouches en de ingots houden hun oude hoek en grootte, maar staan nu exact in
      het midden; op de grond en in je hand (derde persoon) liggen alle onderdelen recht en in het midden.
- [x] **De kijkhoek** — de onderdelen staan schuin van rechtsboven, zoals Minecraft zelf een blok laat zien
      (`model_display.PART_VIEW` = (30, 225, 0), de loop naar rechts): zo zie je de dikte van een loop of een veer, de
      uitwerpopening van de slede, het gloeiende venster van een capacitor en de lens van een emitter. Het hele geweer
      blijft plat van opzij (de loop naar links); dat was goed.
- [x] **De iron sights** van het laserpistool en de shotgun staan als los item dicht bij elkaar (2,5 pixel tussen korrel
      en keep) op een stukje rail (`as_item` in `generate_weapon_models.py`); in het geweer zelf staan ze gewoon voor en
      achter op de slede.
- [x] **Bekijken zonder het spel te starten** — `tools/preview_item_models.py` tekent itemmodellen zoals ze in een vakje
      staan (platte kleuren, Minecrafts schaduw per kant, achterkanten weg): `python tools/preview_item_models.py --out
      preview.png "gun_*" "laser_pistol_*"`; met `--rotation 30,135,0` reken je een andere hoek uit voordat je die in een
      generator zet. Het klopte met de echte screenshots (dezelfde verschuivingen), dus zo zijn de hoeken gekozen. De
      rondleiding (`-Ponly=creative`) zet de muis in de hoek (geen tooltip meer over het plaatje) en scrolt het
      wapentabblad twee keer drie rijen verder: `ct03_weapons_b_glock_parts` en `ct03_weapons_c_laser_parts`.

### 60. Bewakers zoals in Hitman (7 november)

Jouw vraag: "Ik wil guards zoals in hitman, de bewaking van de bunker die guards werken zoals hitman, ze lopen een
bepaald pad, reageren om geluid en het duurt even voor ze je zien, als ze je zien roepen ze tactis versterking, zien
ze een body roepen ze versterking. In de starktower is dit ook maar dan geprogrameert op robots en drones. Deze
programatie neem je ook over."

**Jouw antwoorden** (7 november) op mijn negen vragen:
1. **Routes**: per kamer getekend in de kamerbestanden; een soldaat zonder route staat op een post en draait zijn hoofd;
   je eigen robots schrijven hun route als een lus van goto's.
2. **Zien duurt even**: een meter die volloopt (sneller dichtbij, recht voor hem of rennend; trager ver weg of sluipend),
   halfvol = achterdochtig en komen kijken, vol = gezien. Op je bril een oogje met een pijl naar de bewaker dat volloopt,
   boven zijn hoofd een "?" en dan een "!". "Ja ook volledig wat ik wil."
3. **Geluid**: schoten, ontploffingen, blokken kapotslaan, rennen, deuren, vallen; sluipen is stil; de Glock luid, een
   laserpistool zacht; ze gaan kijken en keren terug. Een demper als Glock-onderdeel: later.
4. **Versterking roepen**, jouw eigen variant: de radio pakken duurt **één seconde**, het hele oproepen **drie**. Stop je
   hem binnen die seconde, dan gebeurt er niets. Stop je hem daarna, dan worden de anderen achterdochtig, je hoort door
   de coms "BB-2358, come in", en ze komen **langzaam** naar je toe. Roept hij het af, dan weet de hele bunker het en komt
   na tien seconden een team van drie of vier man aan de ingang binnen; hooguit twee teams per alarm; na twee minuten
   zonder contact zoeken ze nog even en gaan ze terug naar hun routes.
5. **Lichamen**: een dode soldaat blijft liggen; zijn buit zit **in het lichaam** (doorzoeken met rechtsklik).
6. **De toren**: alleen de twee bewakers met een staff-keycard blijven soldaat; de rest van de bewaking zijn guard robots
   van Biebia op docks (lobby en beveiligingsverdieping) met het programma "Tower guard", plus de drones.
7. **Nieuwe woorden**: `when heard:` (sound.x/y/z/kind), `when spotted:` (target.name/x/y/z), `when body_seen:`
   (body.x/y/z) en `robot.alert`; versterking via `order(...)` en `send(...)`, die bestaan al.
8. **Vermomming** (het pak van een soldaat aantrekken): in het plan, maar er nog niet aan beginnen.
9. **Zoeken**: twee minuten rond je laatste plek (kamers, achter deuren), dan terug naar de route; ze onthouden het: de
   volgende keer zien ze je dubbel zo snel.

Erbij: **geen** minimap en **geen** tekst "ze zoeken je" zoals in Hitman. Alleen het bewakersgedeelte.

**Mijn invulling** (alle getallen in de nieuwe config-sectie `[guards]`):
- **Vier toestanden** per soldaat: *rustig* (route of post), *achterdochtig* (iets gehoord of een halfvolle meter: hij
  gaat kijken), *zoekend* (hij is je kwijt) en *gevecht* (het bestaande RavenCombatGoal). Hij kijkt in een kegel
  (`viewAngle` 140 graden, `viewRange` 28 blokken) en niet door muren of rook. De meter: van 0 naar 100 in
  `spotSecondsNear` (1 s) als je vlak voor hem staat, tot `spotSecondsFar` (8 s) aan de rand; sluipen x0,5, rennen x1,6,
  in zijn ooghoek x0,5; zoekend ziet hij x3; na een alarm `memoryMinutes` lang x2. Uit het zicht zakt hij in
  `decaySeconds` (6). Bij `suspiciousAt` (50) wordt hij achterdochtig, bij 100 heeft hij je. Geraakt worden of een
  maat die je al heeft = meteen gevecht (zoals nu).
- **De radio**: wie je als eerste ziet terwijl zijn bunker nog geen alarm heeft, roept eerst (1 s pakken, dan 2 s
  praten; hij schiet pas daarna). Dood, geboeid of gevloerd tijdens het praten = een half gesprek: zijn maten worden
  achterdochtig en lopen langzaam naar hem toe, en spelers binnen 48 blokken horen "[Radio] BB-2358, come in" (elke
  soldaat heeft een roepnaam BB-xxxx). Afgemaakt = alarm voor de hele **site** (de bunker of toren: alle soldaten binnen
  `siteRange` 160 blokken van de ingangsmarkering), een "Contact!"-bericht, en na `backupDelaySeconds` (10) komt een
  team van `backupSize` (4) soldaten door de ingang gerend naar je laatste plek; `backupWaves` (2) teams per alarm. De
  ingang is een nieuwe markering `raven_entry` in de ingang van de bunker en de lobby van de toren (een nieuwe SavedData
  `RavenSites` onthoudt ze, ook het alarm en de teams). Zonder ingang in de buurt (een spawn egg, een oude wereld) komt er
  geen team, de rest werkt wel.
- **Zoeken**: na `lostSeconds` (10) zonder zicht gaat hij naar je laatste plek en kijkt dan `searchSeconds` (120) lang op
  willekeurige bereikbare plekken binnen 12 blokken (deuren doet hij open); daarna terug naar zijn route. Het alarm van de
  site loopt `alarmSeconds` (120) na het laatste contact af.
- **Geluid**: één plek (`raven/Noises`) waar alles wat lawaai maakt zich meldt met een straal: de Glock 48, een laser
  rifle 16, een laserpistool 12, een ontploffing 56, een flashbang 24, een blok kapotslaan 14, rennen 8 (elke zes ticks),
  een deur 8, een val van meer dan drie blokken 12. Door een muur heen telt de helft. Alleen lawaai van spelers (en
  van hun robots) telt; soldaten horen hun eigen schoten niet als "lawaai", die weten het al. Een rustige soldaat die iets
  hoort, loopt ernaartoe (gewoon tempo), kijkt vier seconden rond en gaat terug; een zoekende neemt de plek over als
  laatste plek.
- **Lichamen**: een nieuw wezen `RavenBodyEntity` met het model van de soldaat plat op de grond (zoals een gevloerde
  robot). Alles wat de soldaat zou laten vallen (zijn Raven-onderdeel, soms zijn wapen, de staff-keycard) gaat erin;
  rechtsklik opent "Search the body" (negen vakjes); sneak + rechtsklik pakt het lichaam vast en sleept het achter je
  aan (nog eens = loslaten). Na `bodyMinutes` (5) verdwijnt het en valt wat erin zat op de grond. Een geboeide soldaat
  en een gevloerde robot tellen ook als lichaam. Een bewaker die een lichaam ziet, gaat erheen en roept via de radio
  ("Man down"): hetzelfde alarm, per lichaam één keer.
- **Je eigen robots en drones**: dezelfde meter (`robot.alert` / `drone.alert`, 0-100, met de Scan en het filter van de
  Face Recognition), `when spotted:` zodra hij vol is (één keer per keer dat hij je ziet), `when heard:` bij elk geluid
  binnen zijn bereik en `when body_seen:` één keer per lichaam. Die drie whens onderbreken wat hij deed, zoals een
  opdracht, en daarna gaat hij verder. Ook boven een robot staat "?" of "!", en ook een robot staat in het oogje op je
  bril. `read(scanPin)` blijft meteen waar (oude programma's veranderen niet).
- **De toren**: de gewone soldaat op de beveiligingsverdieping gaat weg; in de lobby en op de beveiligingsverdieping komen
  Robot Docks aan de kabel, en Biebia's kern zet er (één keer, zoals bij de drones) guard robots op in de squad "Tower
  guards" met het programma **Tower guard** (patrouille langs de punten van die verdieping, `when heard` gaat kijken,
  `when spotted` meldt "contact" aan de kern en schiet, `when body_seen` meldt "man down", `when ordered("alarm")` komt
  eraan). De drones ("Tower drones") krijgen dezelfde whens erbij in "Tower patrol". Biebia's kern krijgt het diagram
  **"Security"**: `when message("contact")` en `when message("man down")` geven de squads de opdracht "alarm" met de plek.
  Daarvoor hoort een robot van niemand voortaan bij een kern van niemand (BotLink): Biebia's robots zijn verbonden met
  Biebia's kern, en na de hack met die van jou. Wie de toren hackt, krijgt de robots zoals de drones.
- **Vermomming** en **demper**: staan onderaan als open stappen, nog niet begonnen.

- [x] **1. De zintuigen van de soldaat** — `raven/RavenAlert` (rustig, achterdochtig, roepend, zoekend, gevecht),
      `raven/RavenSenses` (de kegel van `viewAngle`/`viewRange`; de meter met `spotSecondsNear`/`Far`, sluipen, rennen,
      de ooghoek, zoekend x3 en het geheugen na een alarm; achterdochtig bij `suspiciousAt`, kwijt na `lostSeconds`,
      zoeken `searchSeconds` lang binnen `searchRadius`), `raven/RavenInvestigateGoal` (erheen, `lookSeconds` rondkijken,
      nieuwe zoekplekken die hij echt kan bereiken), `raven/Watcher` (wat je spel ervan ziet: toestand, meter, wie), en
      de sectie `[guards]` in de config. De soldaat heeft geen NearestAttackableTargetGoal voor spelers meer en geen
      LookAtPlayerGoal (die draaide zijn hoofd naar jou, en dan zag hij je altijd); jouw drones schiet hij nog meteen neer,
      en geraakt worden of een maat die je al heeft is nog steeds meteen gevecht. Zijn geweer ligt nu echt aangelegd
      zodra hij een doelwit heeft (`setAggressive`, dat ontbrak). Op je bril (`hud/DetectionWidget`): boven zijn hoofd een
      geel "?" of een rood "!" zodra hij op zijn hoede is, en het oog zodra hij JOU in de gaten krijgt, boven zijn hoofd
      als hij in beeld is, anders op het ovaal van de dreigingspijlen; wit, oranje vanaf `suspiciousAt`, rood als hij je
      heeft (eerst stond het "?"/"!" als tekst in de wereld, zie stap 8). Vier tests in de nieuwe `GuardGameTests` (hij
      heeft je pas na een tijdje, niet achter zijn rug,
      sluipen half zo snel, zoeken waar hij je kwijtraakte).
- [x] **2. Routes en posten** — `raven/RavenRoutes` (de markering `raven_soldier;dx,0,dz;...`: verschuivingen, zodat
      ze meeschuiven met `shift()` en hier meedraaien met het stuk via `StructureTemplate.transform`),
      `raven/RavenPatrolGoal` (`patrolSpeed` 0,75, bij elk punt `patrolWaitMin`-`patrolWaitMax` seconden rondkijken, na
      een gevecht de draad oppakken bij het dichtstbijzijnde punt, een onbereikbaar punt na drie pogingen overslaan;
      zonder route terug naar zijn post, dat is waar hij verscheen). De soldaat bewaart `Post` en `Route`;
      `BiebiaBunkerPiece`, `TowerPiece`, `RoomPlacer` en `TowerGuards` lezen de route uit de markering. Routes getekend
      in `generate_bunker.py` (de hal: twee rondes om de zuilen, west en oost; de slaapzaal langs de bedden; de reactor om
      de bak lava) en `generate_bunker_rooms.py` (wapenkamer en schietbaan, lab en tanks, de cellen langs de tralies, de
      garage om de wagen en door de werkplaats, keuken en eetzaal, radiokamer en kaartenkamer). Posten: de controlepost,
      de wachtpost bij de reactor, de kluis, de achterwand van de hal, de tweede man van de slaapzaal en de twee bewakers
      van de toren. De tien structuurbestanden opnieuw gemaakt (de toren veranderde niet). Drie tests (zijn route lopen,
      terug naar zijn post, de route draait mee met het stuk). 427 tests; `raven_soldier_shoots` en
      `glock_chamber_and_magazine_math` waren elk één keer rood door toeval (spreiding, een storing) en daarna weer groen.
- [x] **3. Geluid** — `raven/Noises`: één plek waar lawaai zich meldt met een straal (`make`), en wie er binnen die
      straal staat hoort het; een muur ertussen (één raycast vanaf zijn oren) laat het maar `noiseWallFactor` (de helft)
      zo ver dragen, en lawaai van een soldaat zelf telt niet. De haakjes: de Glock (`Firearms.fire`, `noiseGlock` 48),
      de lasergeweren (`LaserGuns.fire`: pistool 12, de rest 16), de wapens van jouw robots (`BotShot`), elke ontploffing
      (`ExplosionEvent.Detonate`: frags, de Remote Frag, TNT; 56), een flashbang (24) en een EMP (12, in
      `RollingGrenade.explode`), een blok kapotslaan (`BlockEvent.BreakEvent`, 14), rennen (`PlayerTickEvent.Post`, om de
      zes ticks, 8; gewoon lopen en sluipen zijn stil), een deur, luik of hek (`RightClickBlock`, 8) en een val van meer
      dan drie blokken (`LivingFallEvent`, 12). Een soldaat die iets hoort kijkt er meteen heen (`RavenSenses.heard`),
      wordt achterdochtig en gaat kijken (RavenInvestigateGoal), kijkt `lookSeconds` rond en loopt terug naar zijn route.
      `Noises.Listener` is de haak voor stap 6 (robots en drones). Twee tests (hij gaat kijken en keert terug; een muur
      halveert het bereik en zijn eigen schot telt niet). Het kijken-goal probeert een mislukt pad nu een paar keer
      opnieuw (net na het verschijnen staat een soldaat nog niet "op de grond" en lukt een pad niet). 429 tests, groen.
- [x] **4. De radio en de versterking** — wie je als eerste ziet terwijl er nog niemand van zijn basis vecht, roept eerst
      (`RavenAlert.CALLING`, in `RavenSenses.calling`): hij staat stil, kijkt naar je, en heeft na `radioGrabSeconds`
      (1) zijn radio vast (een klik en geknetter, `raven.radio`, nieuw in `generate_sounds.py`), na `radioCallSeconds`
      (3) is het gesprek af en schiet hij pas. Dood, geboeid of geraakt voor die eerste seconde: niets. Daarna: een half
      gesprek (`RavenSites.halfCall`): zijn maten worden achterdochtig en komen langzaam naar zijn plek, en jij leest in
      grijs cursief "[Radio] BB-2358, come in. BB-2358, do you copy?" (elke soldaat heeft een roepnaam `callsign`,
      BB-xxxx, bewaard in zijn NBT; `RavenRadio` praat tegen spelers binnen `radioRange` 48 en laat hun kant knetteren).
      Afgemaakt (`RavenSites.raiseAlarm`): "Contact! Intruder in the base, all units!", iedereen van de **site** krijgt je
      als doelwit en je laatste plek (`RavenSenses.alarmed`; wie je niet ziet gaat na `lostSeconds` zoeken), is
      `memoryMinutes` lang extra scherp, en "Copy, backup team on the way": na `backupDelaySeconds` (10) komen
      `backupSize` (4) soldaten door de ingang ("Team at the entrance, moving in"), die naar je laatste plek gaan en
      daarna bij de ingang post vatten; hooguit `backupWaves` (2) per alarm (een tweede komt als iemand je opnieuw
      ziet terwijl niemand meer met je vecht). Het alarm loopt `alarmSeconds` (120) na het laatste contact af
      ("All units, stand down"). Een site is de ingang: de nieuwe markering `raven_entry` (net binnen de deur van de
      bunker, `generate_bunker.py`, en net binnen de lobby van de toren, `generate_tower_rooms.py`) wordt bij het bouwen
      een onzichtbare vanilla Marker met de tag `raven_entry` (veilig vanuit de wereldgenerator; het alarm en de teams
      staan per wereld in de SavedData `imttech_raven_sites`); alle soldaten binnen `siteRange` (160) horen erbij. Zonder
      ingang in de buurt (spawn egg, oude wereld) gaat het met de maten binnen 48 blokken en komt er geen team. Zijn
      houding tijdens het roepen: de radio aan zijn oor (linkerarm in de spyglass-houding). Drie tests, elk in een eigen
      batch (een half gesprek, op tijd gestopt, een heel gesprek met team); de andere bewakerstests met een speler ook,
      want een radiogesprek bereikte de soldaten van de test ernaast. 432 tests, groen.
- [x] **5. Lichamen** — `raven/RavenBodyEntity` (een LivingEntity zonder AI, zoals een armor stand: onkwetsbaar behalve
      voor /kill, duwt niets, niemand valt het aan) met het pak van de soldaat plat op zijn rug (`RavenBodyRenderer`:
      het mensmodel een kwartslag achterover, het midden op de plek van het wezen). Een dode soldaat laat er een achter
      (`Bodies.onLivingDrops`: alles wat hij zou laten vallen gaat in de negen vakjes van het lichaam, de soldaat zelf
      verdwijnt meteen; zijn ervaring valt nog gewoon). Rechtsklik: "Search the body" (een gewone kistrij); sneak +
      rechtsklik: vastpakken, het lichaam sleept achter je aan (zoals een geboeide voor je uit loopt; loslaten met nog
      eens sneak + rechtsklik, of als je er meer dan zes blokken vandaan raakt). Na `bodyMinutes` (5) verdwijnt het en
      valt de rest op de grond. **Wie een lichaam ziet** (`Bodies.visible`: in zijn kegel, met zicht; `Bodies.isBody`:
      een lichaam, een geboeide soldaat of een gevloerde robot of drone) loopt erheen (`RavenSenses.lookForBodies`, om de
      tien ticks) en roept op drie blokken via de radio ("Man down! All units, we have an intruder!"): hetzelfde alarm
      als bij een indringer, maar zonder doelwit: iedereen gaat zoeken rond het lichaam en het team komt. Per lichaam
      één keer (`reported`; een onderbroken gesprek geeft het vrij voor een ander), en niet als er al alarm is. Drie
      tests (een lichaam met zijn wapen erin dat later alsnog op de grond valt; een bewaker die een lichaam meldt zodat
      zijn maat gaat zoeken; een geboeide soldaat en een gevloerde robot tellen mee). De test van de staff-keycard zoekt de
      kaart nu ook in het lichaam. 435 tests, groen (`raven_soldier_shoots` blijft af en toe rood door de spreiding).
- [x] **6. De taal** — drie nieuwe whens zonder haakjes voor robots en drones: `when heard:` (sound.x/y/z als blok, zoals
      robot.x, en sound.kind: "shot", "explosion", "block", "steps", "door" of "fall"), `when spotted:` (target.name,
      target.x/y/z) en `when body_seen:` (body.x/y/z), en de waarde `robot.alert` / `drone.alert` (0-100). Ze beginnen
      zoals een opdracht: wat hij deed wacht even (`BotBrain.event`; een echte opdracht die nog moet beginnen gaat voor;
      heeft zijn programma de when niet, dan gebeurt er stil niets). `bot/BotAlert` is zijn meter: dezelfde getallen als
      een soldaat (`[guards]`), met zijn Scan-bereik als afstand en het filter van zijn Face Recognition (wie daar niet
      door komt, vult hem niet); vol = `when spotted:` één keer, tot de meter weer leeg was; lichamen om de tien ticks
      (`Bodies.visible`, per robot zijn eigen lijst); lawaai via `Noises` (elke robot of drone die aan staat, behalve
      zijn eigen schot). `read(scanPin)` blijft meteen waar. Een robot of drone is nu ook een `Watcher`: hetzelfde
      "?"/"!" boven zijn hoofd en het oogje op je bril (DetectionWidget). De Help van een robot- en
      droneprogramma toont de drie whens en `robot.alert`. `heard` en `spotted` zijn nu woorden van de taal (een eigen
      naam zo heten mag niet meer, de controle zegt het). Vier tests (lawaai met plek en soort, niet te ver; de meter en
      `when spotted` één keer; een lichaam één keer en een tweede apart; de woorden mogen niet op de server). 439 tests,
      groen.
- [x] **7. De toren** — twee **Robot Docks** in de lobby (tussen de torentjes bij de lift, op hun kabel) en twee op de
      beveiligingsverdieping (voor de schermenwand, op de kabel langs de noordgevel), in `generate_structure.py`
      (`robot_dock`, dus in de klassieke toren én de toren uit kamers); de gewone soldaat op de beveiligingsverdieping is
      weg (`generate_tower_rooms.py`), de twee bewakers met een staff-keycard blijven. `robot/TowerRobots` (zoals
      TowerDrones): Biebia's kern zet er één keer `towerRobots` (4, `[security]`) guard robots op (battle droids, Scan,
      Face Recognition, Laser en Pathfinding, volle batterij, "Tower guard 1"...), in de squad **"Tower guards"**, met
      het programma **Tower guard**: rondjes lopen (en omkeren als hij vastloopt), `when heard` gaat kijken, `when spotted`
      meldt "contact", richt en schiet, `when body_seen` meldt "man down" en gaat kijken, `when ordered("alarm")` komt
      eraan met zijn laser aan; zolang er geen dock geladen is, probeert de kern het nog tien keer. Biebia's drones zitten
      nu in de squad "Tower drones" en hun "Tower patrol" heeft `when heard`, `when spotted` en `when body_seen` erbij.
      Biebia's kern krijgt het diagram **"Security"** (`TowerGuards.SECURITY_SOURCE`, ook in een bestaande wereld):
      `when message("contact")` en `when message("man down")` geven beide squads de opdracht "alarm" met de plek van de
      melder. Daarvoor hoort een robot van niemand nu bij een kern van niemand (`BotLink.core`): Biebia's robots en
      drones zijn verbonden met Biebia's kern (en na de hack met die van jou: `TowerHack` geeft ook de robots). Een
      lopende opdracht van de server gaat voor: zolang `when ordered` bezig is, beginnen `when heard/spotted/body_seen`
      niet (anders brak `when spotted` het alarm van een drone af). Twee tests (de klassieke toren heeft vier
      bewakersrobots, twee onder en twee boven; "contact" van een robot van niemand geeft de squad de opdracht alarm via
      Biebia's kern). 441 tests, groen.
- [x] **8. Afronding** — showcase **66, "Guards like in Hitman"** (na "Raven soldiers"): een bewaker loopt zijn route
      met zijn rug naar je toe, lawaai vlak voor je ("?", hij komt kijken), het oog loopt vol, "!", de radio, hij schiet,
      en daarna ligt zijn lichaam er. Rondleiding `gradlew runVisualCheck -Ponly=guards`: `hg01_guard_patrol`,
      `hg02_guard_suspicious` (het "?"), `hg03_guard_eye`, `hg04_guard_calling` (het "!" en het rode oog),
      `hg05_guard_body`, `hg06_guard_body_search` (je bent daar even survival en onkwetsbaar: in creative ziet een
      bewaker je niet). Daarbij bleek: tekst uit een entity-renderer (zelfs vanilla's `renderNameTag`) komt in dit spel
      niet op het scherm, en tekst uit het level-render-event (zoals de labels van de Remote Frag) kwam precies achter het
      oog terecht; het "?"/"!" staat nu in de HUD zelf (`DetectionWidget.mark`, twee keer zo groot als gewone tekst, met
      een lichte pulse), boven het oog en in dezelfde projectie; `AlertMarks` is weg. LEESMIJ.md: "Bewakers zoals in
      Hitman" en het lichaam onder Raven-soldaten, Biebia's robots en het diagram "Security" bij de toren, de drie whens
      en `robot.alert` bij "Robots programmeren", `[guards]` in de tabel van de instellingen. 441 tests, allemaal groen.
      Het jar gebouwd (2.1.24).
- [ ] **9. Vermomming** (nog niet beginnen, jouw keuze) — het pak van een dode soldaat aantrekken: bewakers zien je dan
      pas van dichtbij (of als je rent of iets vreemds doet).
- [ ] **10. Een demper** (later) — een Glock-onderdeel dat het schot zachter maakt (lawaai 48 wordt 10).

### 61. Bewakers bijgesteld (8 november)

Jouw opmerkingen: "Het spotten van de speler met het oogje werkt nog niet helemaal goed, er wordt meteen versterking
geroepen. Zorg ook dat je met een zwaard een guard kunt neersteken langs achter zodat je die stealth kunt killen." En
daarna: "Ik heb gemerkt hoe het komt, je ziet dit enkel met je bril, ik wil dat je dit ook zonder bril ziet en dat de
iconen iets kleiner zijn. Ik wil in de bunkers ook minder guards en ze moeten echt een pad afwandelen, niet static
staan."

**Mijn invulling**: de meter zelf werkte zoals afgesproken (een seconde vlak voor hem, acht aan de rand); zonder bril zag
je hem alleen niet vollopen, en dan lijkt het alsof hij meteen roept. De getallen blijven dus gelijk (`[guards]`). De
sluipaanval werkt met elk zwaard (de tag `minecraft:swords`), vanuit de halve cirkel achter zijn rug, zolang hij niet
vecht en niet roept; een geboeide soldaat mag ook. Alleen Raven-soldaten, niet de robots van Biebia. De twee bewakers
met een staff-keycard in de toren blijven op hun post (je vraag ging over de bunkers).

- [x] **1. Het oog zonder bril, en kleiner** — het oog en het "?"/"!" tekenen nu in een eigen laag boven het vizier
      (`client/raven/GuardLayer`), los van de HUD van de bril: ze staan er ook zonder bril, met een lege bril of tijdens
      een EMP. `DetectionWidget` staat niet meer in de lijst van de bril. Het oog is 11 bij 5 pixels (was 17 bij 7), het
      "?"/"!" anderhalf keer zo groot als gewone tekst (was twee keer), met een kleinere pulse.
- [x] **2. Een sluipaanval met een zwaard** — `raven/Takedown`: sla je een soldaat met een zwaard terwijl je achter hem
      staat (`[guards] takedownAngle`, 180 graden: alles achter zijn schouders, gemeten vanaf waar zijn hoofd heen kijkt;
      0 zet het uit) en hij nog geen doelwit heeft en niet roept, dan is hij in één keer neer. Stil: geen lawaai voor de
      andere bewakers, zijn doodsgeluid valt weg, alleen een zachte steek en wat vonkjes. Zijn lichaam blijft liggen zoals
      altijd. Het zwaard slijt één punt, en de gewone slag (met zijn zwaai die een tweede soldaat kan raken) gebeurt niet.
      Onder je vizier staat "Takedown [Left Button]" zolang het kan (ook zonder bril). Van voren, zonder zwaard of als hij
      al vecht is het een gewone slag. Twee tests.
- [x] **3. Minder bewakers, en allemaal een route** — in `generate_bunker.py` en `generate_bunker_rooms.py`:

      | Stuk | Was | Nu |
      |---|---|---|
      | De klassieke bunker | 8 (4 op een post) | 5 |
      | Ingang | 1 (post) | 1 |
      | Hal met slaapzaal | 5 | 2 |
      | Reactor | 5 | 2 |
      | Kluis | 1 (post) | 1 |
      | Cellen, kantine | 3 | 1 |
      | Wapenkamer, lab, garage, radiokamer | 2 | 1 |

      Geen enkele soldaat staat nog op een post. De controlepost loopt de gang op en neer, de hal heeft één grote ronde om
      de zuilen en de kaartentafel, de kluiswacht loopt heen en weer voor de kisten, en in de nieuwe zalen loopt één
      soldaat door de westkamer, de hal en de oostkamer. Een bunker van vijf zalen heeft nu ongeveer negen soldaten in
      plaats van achttien. De generator controleert zelf dat elke soldaat een route heeft en elk punt te voet kan
      bereiken (`check_routes`): klopt dat niet, dan stopt hij met een foutmelding. Daarbij bleek dat het middelste vat in
      de wapenkamer precies voor de deur stond (niemand kwam erin); dat staat nu ernaast. De westkamer van de garage blijft
      dicht (de wagen steekt in de deur), daar loopt dus niemand. Alle elf bunkerbestanden opnieuw gemaakt. De tests
      tellen nu vijf soldaten in de klassieke bunker en per zaal één of twee, allemaal met een route.
- [x] **4. De versterking gaat weer weg** — een team (`RavenSites.spawnTeam`) loopt tijdens het alarm heen en weer
      tussen de ingang en je laatste plek, in plaats van bij de ingang te blijven staan. Bij "stand down" lopen ze naar de
      ingang en zijn ze daar weg (`RavenSoldierEntity.leave`), zodat een bunker na een alarm niet voorgoed voller wordt.
      Een test.
- [x] **5. Rondleiding** — `gradlew runVisualCheck -Ponly=guards` draait nu zonder bril (hg01 tot hg06) en heeft twee
      nieuwe shots: `hg07_guard_takedown` (achter een bewaker met een zwaard, "Takedown" onder je vizier) en
      `hg08_guard_taken_down` (hij ligt er). 444 tests, allemaal groen.
- [x] **6. De sluipaanval als animatie** — jouw tweede opmerking: "Kun je bij de kill langs achter een leuke animatie
      doen zodat het geen instakill is maar een beetje tijd kost met die te killen." Een klik grijpt hem nu: hij wordt
      naar je toe getrokken (vlak voor je, met zijn rug naar je toe), je benen gaan op slot (niet lopen en niet
      springen), en hij worstelt `[guards] takedownSeconds` lang (2 seconden; 0 = meteen, zoals eerst). De animatie
      (`client/raven/TakedownPose`, en het kantelen in `RavenSoldierRenderer`): zijn handen naar je arm om zijn nek en
      wild heen en weer, zijn hoofd achterover en schuddend, trappende benen en een trillend lijf; dan slap (armen
      omlaag, hoofd voorover), door zijn knieën tot hij zit, en achterover tegen je aan, zodat het aansluit op zijn
      lichaam dat op zijn rug ligt. Daarbij geritsel en gesmoorde kreten, je beeld schudt een beetje mee, en onder je
      vizier loopt een balkje vol. Pas aan het eind de steek: dan is hij neer, stil zoals eerst. Laat je hem los
      (weggeduwd, een teleport, uitgelogd), dan komt hij los, draait zich om en heeft je, en roept dus versterking.
      Wie de worsteling ziet, ziet een "lichaam" en komt kijken. Tijdens de worsteling sla je niet. De rondleiding
      fotografeert het in slow motion: `hg08_takedown_struggle` zoals jij het ziet, `hg09` tot `hg11` van voren (de
      camera van F5), `hg12_guard_taken_down` het lichaam. Een nieuwe test (loslaten); de test van de sluipaanval
      controleert nu ook de tijd, het slot op je benen en dat hij naar je toe getrokken wordt.
- [x] **7. Het "?" en "!" nog kleiner** — jouw tweede opmerking: "ik wil dat de ? en ! kleiner zijn boven een guard".
      Ze zijn nu zo groot als gewone tekst (was anderhalf keer), met een kleinere pulse. 445 tests, allemaal groen.

### 62. De harnas-werkbank: één stuk tegelijk (8 november)

Jouw opmerking: "Kun je de workbench het switchen tussen items niet heel het armor laten zien maar enkel het selected
ding, rechtsklik is naar rechts gaan in de items, shift rechts is links in de items."

**Mijn invulling**: "rechtsklik" en "shift + rechtsklik" zijn klikken op de werkbank zelf (het blok). Het werk (bouwen,
pakken, neerleggen, inbouwen) doe je met een rechtsklik op het stuk dat erboven zweeft, zoals eerst; een klik op het
blok doet dat niet meer (dat was de klik "werkt op het gekozen stuk" van hoofdstuk 54). "Rechts" is de volgorde borst,
broek, laarzen, bril, en na de bril weer de borst; "links" andersom. Leg je een stuk neer dat niet gekozen was (laarzen
in je hand, klik op de borstplaat), dan wordt dat stuk gekozen, anders lag het er onzichtbaar. Werkt de werkbank aan
een ander stuk dan het gekozen, dan loopt dat door; het kaartje bij de werkbank laat zien hoe ver hij is.

- [x] **1. Alleen het gekozen stuk op het hologram** — `SuitHologramRenderer` tekent alleen `selected()`; de oranje
      hoekjes voor het gekozen stuk zijn weg (er staat er toch maar één). Je kunt ook alleen dat stuk aanklikken
      (`WorkbenchHologram.pick`), dus niet per ongeluk een onzichtbaar stuk.
- [x] **2. Klik = volgende, sneak + klik = vorige** — `SuitWorkbenchBlock.useWithoutItem` (`selectNext`,
      `selectPrevious`, `HoloPiece.previous`), met een klikje (iets lager bij vorige). Sneak + klik slaat in Minecraft
      het blok over als je iets vasthoudt; een `RightClickBlock`-event zet dat voor de werkbank aan, met wat je ook
      vasthoudt (eerst alleen met het harnas, de bril en de upgrades: die vier uitzonderingen op de items zijn weg).
- [x] **3. Het kaartje bij de werkbank** — kijk je naar het blok, dan staat er welk stuk gekozen is ("Selected: Glasses
      (4/4)"), of het een hologram is of erop ligt, wat de werkbank aan het doen is (met het balkje),
      `[Right Button] > Chestplate`, `[Sneak + Right Button] < Boots` en "Use it: click the piece above". Eerst stond er ook tekst boven
      je hotbar en was het kaartje breder; in de rondleiding viel dat over elkaar heen (onder het vizier, want rechts
      paste het niet). Nu is het kaartje smal genoeg voor rechts van je vizier en staat er niets boven je hotbar.
- [x] **4. Elk stuk op dezelfde hoogte** — jouw tweede opmerking: "Zorg dat elk ding op dezelfde hoogte staat als je
      erdoor scrolt, een halve block boven de workbench." Elk stuk zweeft nu met zijn onderkant op y 1.5, een halve blok
      boven de werkbank (`HoloPiece.HOVER`), in plaats van op zijn plek op een heel harnas (de laarzen zaten vlak op de
      werkbank, de bril hoog in de lucht). Hoe hoog elk stuk echt is, heb ik gemeten aan het model en de plaatjes
      (`HoloPiece`: top en bottom in pixels onder de nek): de borstplaat 0,86 blok hoog (van de schouderplaten tot de
      laser cannons), de broek 0,85 (van de riem tot onder de benen), de laarzen 0,39, de bril 0,21 (alleen de band om
      de ogen). Het klikvak, de scanring, de ring van het inbouwen en de hoekjes gaan mee (`HoloPiece.box`, een klein
      stuk krijgt een klikvak van minstens 0,35 hoog), en een stuk dat naar je toe vliegt, vertrekt van die hoogte.
- [x] **5. Showcase, rondleiding, LEESMIJ** — de showcase "Suit workbench hologram" klikt nu twee keer op de werkbank
      naar de laarzen, "Glasses upgrades" doet één sneak + klik (van de borstplaat rond naar de bril) en klikt op de bril
      erboven; de rondleiding `-Ponly=workbench` legt de laarzen via de borstplaat neer (w06) en gaat met drie keer
      sneak + klik van de laarzen naar de bril (w10). Een nieuwe test (echte klikken op het blok met een zwaard in je
      hand, met en zonder sneak, neerleggen kiest het stuk, en elk klikvak begint op dezelfde hoogte); de test van de bril
      controleert nu ook beide richtingen. 446 tests, allemaal groen.

### 63. How it works: animaties zoals Ponder van Create (8 november)

Jouw wens: "Ken je de ponder functie van create mod? Ik wil dat je ook zoiets maakt voor elke item dat een functie heeft
buiten gebruiken om te craften bijvoorbeeld casings om een multiblock batterij te maken. Ik wil dat je in je inventory
via een knop kunt 'how it works' kunt doen en dan krijg je een soort animatie over hoe het werkt."

Jouw vier antwoorden (alle vier mijn aanbeveling):
1. **Openen**: in elk inventarisscherm (ook kisten, het creative-menu en JEI) houd je je muis op een item en **W**
   ingedrukt, zoals bij Create; een balkje loopt vol en de animatie gaat open. Daarnaast een knopje **How it works** in je
   eigen inventaris dat een **overzicht** van alle onderwerpen opent, per creative-tabblad. De toets staat in Controls.
2. **Zelf bouwen**, niet de Ponder-bibliotheek van Create: geen extra mod nodig, en onze getekende kabels (geen blokken),
   hologrammen, robots en effecten moeten erin. De opbouw van Create (tekstballonnen, tijdlijn, draaien met de muis), de
   kleuren van I.M.T.
3. Opent een item een scherm, dan schuift er een **plaatje van het echte scherm** in beeld, met pijltjes naar de
   belangrijke delen. De rondleiding maakt die plaatjes zelf (`-Ponly=howtopics`).
4. **Per onderwerp, extra waar nodig**: items die bij elkaar horen delen één animatie van 20 tot 40 seconden (casing,
   glass, cell en port: de Power Battery); een item met heel verschillende functies krijgt er meer (de Laser Emitter: de
   poort, de instellingen, draden knippen). Zo'n 50 onderwerpen voor ongeveer 90 items.

**Mijn invulling**: gewone grondstoffen die alleen in een recept gaan (Computer Chip, Raven Circuit, staal, titanium,
polymeer) en de spawn-eieren krijgen geen animatie; onderdelen die in een machine van ons gaan (geweeronderdelen,
robotonderdelen, modules, hulzen) wel. Alle tekst in het spel is Engels, de getallen komen uit de config. Eerst het
systeem met de Power Battery (jouw voorbeeld), dan tabblad voor tabblad. Een test houdt bij welke items nog geen animatie
hebben, zodat er geen vergeten wordt.

- [x] **1. Het systeem** — het draaiboek is gewone gegevens (`howto/`: `HowToCue`, `HowToScene`, een bouwer
      `HowToSceneBuilder` zoals de SceneBuilder van Ponder, met een klok en `idle`), zodat een test het op de server kan
      nalopen; het tekenen staat in `client/howto/`. Het wereldje (`SceneWorld`) is een "BlockAndTintGetter" zonder
      chunks: Minecraft tekent elk blok dan met dezelfde code als in de wereld (schaduw in de hoekjes, verborgen kanten
      tussen blokken, de kleur van gras), en buren die aan elkaar hangen (Battery Glass) worden verbonden. `ScenePlayer`
      bouwt het wereldje op uit de cues tot het huidige moment, dus springen en terugspoelen is gewoon de tijd
      veranderen; blokken vallen in 6 ticks op hun plek (met het geluid van neerzetten), kabels groeien langs hun punten
      (met de tekencode van de echte kabels), randjes kloppen een beetje. `SceneOverlays` tekent de tekstballonnen met
      een lijntje en `*woorden*` in cyaan en `[toetsen]` in oranje, de muis met de ingedrukte knop en wat je vasthoudt
      (die langs een pad loopt als je een kabel tekent), balkjes, en de plaatjes. Het scherm (`HowToScreen`): bovenaan
      het item en het onderwerp (met pijltjes als er meer animaties zijn), onderaan de tijdlijn met een streepje per
      hoofdstukje, de knoppen opnieuw, vorig, pauze en volgend (en spatie, pijltjes links en rechts), klikken of slepen
      op de tijdlijn; slepen draait, scrollen zoomt, rechtsklik zet de camera terug, en wijs je een blok aan, dan zie je
      zijn naam (met een wit randje). Het scherm komt als laag boven je inventaris of kist (Esc brengt je terug, alles
      nog zoals het was), en in je eigen wereld staat het spel stil terwijl je kijkt. Geluid: `howToVolume` in
      `imttech-client.toml`.
- [x] **2. Openen** — `HowToHover`: een item met een animatie krijgt in zijn tooltip "Hold [W]: How it works" (in elk
      scherm, ook het creative-menu en JEI, want we kijken naar het tooltip dat echt getekend wordt); houd je W vast,
      dan loopt de regel in cyaan vol en na 12 ticks gaat de animatie open. Typ je in een zoekvak, of liep je met W
      ingedrukt je inventaris in, dan gebeurt er niets (eerst loslaten). De toets is "How it works" onder I.M.T in
      Controls (KeyConflictContext.GUI, dus geen botsing met lopen). Op jouw AZERTY-toetsenbord heet die toets Z: de
      toetsen van de mod zijn plekken op het toetsenbord, net als bij Create. `HowToButton`: een knopje **?** rechtsboven
      in je inventaris (boven het craftraster) opent het overzicht (`HowToIndexScreen`): per creative-tabblad een tegel
      per onderwerp dat klaar is; wijs je er een aan, dan staat bovenaan hoe het heet.
- [x] **3. Plaatjes van echte schermen** — `gradlew runVisualCheck -Ponly=howtopics` opent het echte scherm, knipt het
      paneel eruit (in de pixels van het venster, bij GUI-schaal 3 dus 900 x 684) en zet het in
      `textures/gui/howto/` (met `blur`, zodat het verkleind niet korrelig is). Alleen als je erom vraagt, want het
      verandert bestanden van de mod. Het batterijscherm krijgt voorbeeldcijfers (twee zonnepanelen en een
      warmtegenerator, drie laserzenders, twee camera's en een holotafel): in de testwereld stond alles op 0 per tick, en
      dat lijkt in een uitleg kapot. In de animatie schuift het plaatje van rechts in beeld, de rest wordt donker, en
      links komen de pijltjes er een voor een bij.
- [x] **4. De Power Battery** — `howto/scenes/PowerScenes.battery`, 44 seconden in zes hoofdstukjes: de doos van 5 x 4 x
      5 van casing laag voor laag (met een raam van 3 x 2 glas in de zuidkant, dat één raam wordt), 18 cellen erin en het
      dak erop, een poort in de westwand (een klik met de poort in je hand), een zonnepaneel en een brandende
      warmtegenerator met een kabel naar de poort (de muis houdt de rechterknop ingedrukt en loopt mee) en het balkje
      STORED dat volloopt, het batterijscherm als plaatje met vier pijltjes, en een gat in het dak (rood randje) dat weer
      dicht gaat (groen randje). De getallen in de tekst (3 x 3 x 3 tot 18 x 18 x 18, 1,000,000 FE per cel, 2,000 FE per
      tick) komen uit de config van je wereld.
- [x] **5. Test, rondleiding, showcase en LEESMIJ** — drie tests (`HowToGameTests`): elk item van de mod hoort bij een
      onderwerp (klaar of nog in het plan) of bij de items die alleen in recepten gaan, en niet allebei; elk draaiboek
      klopt (hoofdstukjes en cues op volgorde, teksten met Engelse tekst en lang genoeg in beeld, blokken op de plaat,
      tussen 10 seconden en anderhalve minuut); en de blokken aan het eind van de animatie van de batterij vormen in een
      testwereld echt een batterij van 5 x 4 x 5 met 18 cellen en één poort. `HowToTopics` heeft alle 51 onderwerpen al,
      met hun items; de onderwerpen die nog niet klaar zijn, zie je in het spel niet. De rondleiding `-Ponly=howto`
      (ht01 tot ht19): de tooltip, W echt ingedrukt (de regel loopt vol), de vloer die valt, elk hoofdstukje, gedraaid en
      ingezoomd, een blok aanwijzen, het knopje, het overzicht en een animatie vanuit het overzicht. De showcase heeft
      "How it works (hold W on an item)" (de laatste), en LEESMIJ een stuk in hoofdstuk 2, 3, 5 en 6. 449 tests, allemaal
      groen.
- [x] **Jouw opmerking: "het lijkt net iets te hard op Create"** — het dambord van wit en grijs beton (zoals Ponder) is
      weg. Onder elke animatie ligt nu het platform van I.M.T: een dunne, donkere plaat in de kleuren van de HUD, met een
      fijn cyaan raster op de naden (zo tel je de blokken nog) en een oplichtende cyaan rand, alsof het een hologram is
      dat in het donker zweeft (`SceneRenderer.renderPlate`). Onder de plaat zit een blok dat je niet ziet, zodat wat erop
      staat schaduw in de hoekjes houdt. Ook de knoppen kregen eigen plaatjes (|◀ opnieuw, ◀◀ en ▶▶ voor de
      hoofdstukjes), en het eerste hoofdstukje begint nu meteen (er stond een leeg hoofdstukje "1/7" voor).
- [x] **6. Jouw volgorde: alle onderwerpen in het overzicht, en eerst de belangrijkste** — jouw opmerking (8 november):
      "Zorg om te beginnen dat elke topic in de menu van de topics staat. Als er nog geen animatie voor is staat dat er
      gewoon bij maar dan weet je dat dat een topic is. Hierna begin je eerst aan de belangrijkste topics die simpel zijn
      en veel in de mod gebruikt worden, denk maar aan keycards, securitycore... dingen die minder belangrijk zijn zoals
      een desktop verbinden aan de core mag je er dan nog uitlaten."
      **Het overzicht** toont nu alle 51 onderwerpen: wat al een animatie heeft is helder met een cyaan randje, de rest
      grijs; wijs je een grijze aan, dan staat bovenaan "*Keycards and readers* - no animation yet", en rechtsboven
      "9 of 51 with an animation". Een klik op een grijze tegel doet niets.
      **Acht nieuwe animaties** (samen met de batterij negen), elk getest en per hoofdstukje gefotografeerd:
      - *Security Core* (32 s): het hart van je basis, op stroom, PoE-kabels naar een lezer en een camera (zonder kabel doet
        een apparaat niets), de desktop als plaatje met Devices, Keycards, Zone en Logic, en dat alleen jij hem opent
        (weghakken houdt alles).
      - *Keycards and readers* (38 s): een lezer naast een ijzeren deur, een PoE-kabel naar de kern, de app Keycards als
        plaatje (schrijven, groepen, blokkeren), de app Logic als plaatje met het diagram (`when keycard`, `card.in_group`,
        `accept()`, `pulse("Lab lock", 3)`), dan een goede kaart (het signaal loopt over de kabel naar de kern en terug,
        groen, het relais "Lab lock" opent de deur 3 seconden) en een verkeerde (rood, de deur blijft dicht).
      - *Code panel* (30 s): hetzelfde met een codepaneel, het scherm met de toetsen als plaatje, en het diagram
        (`when codepanel`, `code == "4321"`).
      - *Laser gates* (35 s): een zender schuift zijn wand uit tot de pilaar, aan zolang hij stroom heeft, drie zenders
        boven elkaar zijn één poort, uit en aan op zijn naam (bril, stem, `off("Gate 1")`), en het instelscherm als
        plaatje (Color, Name, Switch, Wall), waarna de poort rood wordt.
      - *Making power* (22 s): twee zonnepanelen, een warmtegenerator op kolen (het rooster gaat gloeien) en een op
        magma, en een laadstation ertegen dat geen kabel nodig heeft.
      - *Power cables* (23 s): een kabel tekenen met de rechtermuisknop ingedrukt, gele streepjes van stroom, eroverheen
        bouwen (gladde stenen platen), en oprapen of doorknippen.
      - *Network cables and PoE* (25 s): wit naar een lezer, groen (PoE) naar een camera die dan aangaat, signalen heen en
        terug, en een switch in de kabel.
      - *Charging station* (19 s): de bril laden (balkje GLASSES), een energiecel laden, en dat het alleen neemt wat de
        generatoren over hebben.
      Nieuwe bouwstenen: een **signaal** dat over een kabel loopt (wit, groen of geel), een blok **van stand veranderen**
      zonder geluid (een lampje, een deur) en blokken **stil weghalen** (een laserwand die terugschuift). De plaatjes
      (`-Ponly=howtopics`): de desktop van een kern (met een wachtwoord, dus zonder het venster Settings), de app
      Keycards met vier kaarten in groepen, de app Logic met het diagram van de deur (met een serverrek, dus "online"),
      het codepaneel met een getypte code, en het instelscherm van een laserzender. `-Ponly=howtoall` fotografeert elk
      hoofdstukje van elke animatie (`ha_...`), om ze na te kijken; daarbij bleek de paal van de camera voor de kern te
      staan (nu erachter) en stonden twee tekstballonnen over de generator en de kern heen. Tekstballonnen staan nu
      boven de muis die klikt (het voorwerp in je hand viel over de tekst). 449 tests, allemaal groen.
- [x] **7. De volgende tien** — jouw opmerking (8 november): "Ga verder met de 10 andere net iets minder belangrijke
      animaties, maar nog steeds de belangrijkste van wat er nog niet is." Mijn keuze, de belangrijkste van wat er nog was:
      - *I.M.T glasses* (31 s): jij (een speler met jouw skin) zet de bril op, dat je hem alleen in de kluis van een
        Biebia's Industries Tower vindt, dat hij van de eerste drager is, de HUD en het G-menu (het tabblad Drones) als
        plaatje, en laden in het laadstation.
      - *Turrets* (30 s): een Rocket Turret gaat aan op stroom (zonder stroom hangt de loop), een husk loopt erop af en het
        geschut draait mee en schiet hem neer met de laser, raketten met Rocket Ammo, een Laser Turret aan je kern die een
        tweede husk neerhaalt, en uit en aan op hun naam (dan hangen de lopen).
      - *Server racks* (20 s): vier racks tegen de kern zijn één server, het scherm van een rek als plaatje (CPU of GPU,
        fan, RAM, SSD), en dan knipperen de lampjes.
      - *Heat and fans* (20 s): het balkje RACK TEMP (in graden) loopt op zonder fan, een fan erin en het zakt, en te heet: vuur op het
        rek (70, 90 en 110 graden).
      - *Cameras and the camera wall* (20 s): een camera op een paal met een PoE-kabel (dan gaat hij aan), een muur van
        vier monitoren die samen één scherm zijn, en instellen met een rechtsklik.
      - *Protected zones* (26 s): een basis met een kern, jij met de Zone Marker: linksklik hoek 1, rechtsklik hoek 2 (een
        oranje doos), sneak + rechtsklik (de doos wordt cyaan: je zone), wie er mag bouwen, frags, stroom, de app Zone en
        Autoscan.
      - *Laser bridge* (20 s): twee brugzenders in de rand van een gat, de vloer van licht schuift erover, jij loopt erover,
        en een val: de brug gaat uit en de tweede speler valt.
      - *Network button and lever* (22 s): een knop en een hendel aan een muur met PoE, de knop stuurt een signaal naar de
        kern, Logic als plaatje (`when button`, `player.name`, `when lever`, `lever.on`), en de hendel om.
      - *Shield dome* (22 s): de koepel gaat aan met zijn echte show (de straal, het openvouwen, een hele bol) boven een
        huisje, een raiddrone kaatst terug, en het scherm als plaatje (aan/uit, kleur, bereik, sterkte).
      - *Redstone relays* (27 s): een relais en een lamp, een naam geven, aan en uit van overal, groepen, en de relay
        emitter met een hendel die beide relais aanzet.
      Daarvoor kan het wereldje nu meer: **wezens** (een speler met jouw skin en wat hij draagt of vasthoudt, een husk,
      een raiddrone, die langs een pad lopen met hun benen in beweging), de **bewegende delen van blokken** die Minecraft
      apart tekent (het geschut van een torentje, dat op het dichtstbijzijnde monster mikt, rondkijkt of zijn loop laat
      hangen; de koepel; de monitoren), **schoten** (een laserstraal), en gegevens voor zo'n blok op een moment (de
      koepel die opengaat loopt mee met de animatie, ook als je terugspoelt). Een blok waarvan het tekenen buiten de
      wereld niet lukt, slaan we over in plaats van dat het scherm crasht. Nieuwe plaatjes (`-Ponly=howtopics`): de HUD
      en het G-menu van de bril, Logic met een knop en een hendel, het scherm van een rek (het rek opende eerst zonder
      zijn plek, en dan verbrak het spel de verbinding) en het scherm van een koepel. Elk hoofdstukje gefotografeerd
      (`-Ponly=howtoall`, en met `howto:turrets` erbij alleen dat onderwerp); daarbij bleek een schuin schot als een grote
      rode doos getekend te worden (een doos langs de assen, zoals een kabel: nu een echte dunne straal), stonden drie
      tekstballonnen over de monitoren, de relay emitter en het laadstation, en stond de temperatuur in procenten. 449
      tests, allemaal groen.
- [x] **8. De robots** — jouw opmerking (8 november): "Ga verder met alles over de robots maken/bouwen/coden." Het hele
      tabblad Robots, zes animaties in vier onderwerpen (23 van de 51 klaar):
      - *Guard robots*, twee animaties. **Building a guard robot** (56 s): de vier stijlen naast elkaar (met hun lichtjes
        aan), wat elk onderdeel doet, de Robot Assembler (zijn scherm als plaatje, met dezelfde gemengde robot: hoofd en
        benen van een Commando, lijf en armen van een Super Battle), een Robot Dock naast een zonnepaneel en het item erop
        (een balkje BATTERY), zijn scherm als plaatje, aan (twee piepjes, de lichtjes aan, `stepOut` blokken naar voren),
        uit (terug in zijn dock), ijzer en Robot Ammo. **Programming a robot** (47 s): het robotprogramma Patrol in Logic
        als plaatje (rondjes met `send("corner")`, en `when ordered("attack")`), de app Robots met de knop Program als
        plaatje, de robot loopt zijn vierkantje van 3 blokken, een tweede rondje met op elke hoek een berichtje door de
        lucht naar de kern, een opdracht van de kern (een oranje berichtje naar de robot; hij loopt naar het rode vak en
        daarna verder), en `charge(80)`.
      - *Soldering modules*, twee animaties. **Soldering modules** (44 s): een grote Commando op zijn dock, de acht
        modules, het bordscherm met de hittebalk als plaatje, loslaten in het groen (het schoteltje van de Scan verschijnt
        op zijn hoofd), een brug met Inspect als plaatje, aanzetten met een brug (een rode flits en een knal, de laser
        wordt een Burnt Module), en alle acht modules op de robot. **Seeing and shooting** (45 s): het bewakersprogramma
        met `read` en `write` als plaatje, jij loopt langs (het filter "strangers"), een vreemde (een speler met een
        gewone skin) krijgt de laser, een tweede het geweer, jij legt de robot neer met de Ion Blaster (hij ligt op zijn
        rug), en de andere vier modules in een zin.
      - *Security drones* (39 s): een drone die zweeft, een Drone Dock naast een zonnepaneel met de drone erop ("Drone 1
        is charging (56%)"), aan (hij stijgt op), het droneprogramma in Logic als plaatje, een vierkantje vliegen, en
        `charge()` terug naar zijn dock.
      - *Training* (70 s): de drie blokken bij een kern met twee Trainers op hun dock, het instelscherm van Training Start
        als plaatje, klikken (aftellen, de robots van Wave 1 stappen uit), een treffer (een laser, een blauwe flits rond
        jou, het lampje van Training End en een redstonelamp ernaast), de mini-editor als plaatje, jij legt beide robots
        neer met de Ion Blaster (squads en `squad_down`), je stapt op het doel, de app Training met de rapporten als
        plaatje, en de oefeningen (inbraak en uitbraak).
      Daarvoor kan een wezen nu **gegevens** meekrijgen zoals het spel ze bewaart (`HowToCue.Actor.data`, geladen met
      `Entity.load`): een robot met zijn onderdelen, printplaat en of hij aan staat of neerligt; een nieuwe cue met andere
      gegevens verandert hetzelfde wezen (een module erbij, aan, neergeschoten). Een **vreemde** is een speler met een
      gewone skin van Minecraft in plaats van jij (`stranger`), en een randje rond een wezen is een flits (`flash`). De
      "vorige plek" van een wezen staat nu gelijk met zijn plek: anders dacht een drone dat hij vloog en draaiden zijn
      rotors terwijl hij uit stond. Een nieuwe test (`robot_animations_load_like_real_robots`) laadt elke robot en drone
      uit de animaties zoals het spel dat doet en kijkt dat zijn onderdelen, printplaat en toestand kloppen. Elf nieuwe
      plaatjes (`-Ponly=howtopics`, zonder de HUD erachter en zonder de meldingen rechtsboven): de Robot Assembler, het
      robotscherm, het bordscherm met de hittebalk en met Inspect, Logic met het robot-, bewakers- en droneprogramma
      (Logic opent nu het bewaarde programma, in plaats van een nieuw met dezelfde naam dat dan dubbel in de lijst stond),
      de app Robots met Program, de app Training, het instelscherm van Training Start en de mini-editor van Training End.
      Wat ik op de plaatjes zag en verbeterde: in het bordscherm liep "Right-click a pin = desolder." rechts buiten het
      scherm (nu op een tweede regel), en in de mini-editor van Training End werd de uitleg bovenaan vooraan afgekapt
      ("..n trainee_hit"; nu korter: "Runs on every hit (trainee, lives, hits)"). Op de foto's per hoofdstukje stonden
      tekstballonnen voor de kern, de blokken van de training, een robot die uit zijn dock stapte en een vreemde, en de
      robot bij het solderen en de drone waren klein; nu staan de robots en blokken in het midden van het beeld, de
      ballonnen aan de andere kant, en zijn de platen kleiner. Op foto's van precieze momenten nagekeken: de laser (rood),
      het geweer (geel), de Ion Blaster (blauw), de blauwe flits, de berichtjes naar en van de kern en de rode flits van
      de kortsluiting. 454 tests, allemaal groen (in de eerste ronde miste de Raven-soldaat van `raven_soldier_shoots` een
      keer binnen zijn tien seconden; dat hangt aan zijn willekeurige wapen en richten, niet aan dit werk).
- [x] **9. De wapens** — jouw opmerking (8 november): "Ga verder met IMT weapons." Het hele tabblad Weapons: elf
      onderwerpen met elk één animatie (34 van de 51 klaar):
      - *Laser guns* (43 s): de vier geweren zwevend boven het plateau, schieten met het pistool op een husk (blauwe
        stralen), de rifle (automatisch), de sniper (één zware straal ver weg) en de shotgun (een waaier van acht stralen),
        energiecellen (de HUD als plaatje) en laden in het laadstation.
      - *Laser guns from parts* (39 s): de zes onderdelen zwevend op een rij, titanium en netherite, de Nether Star als
        kern, de Laser Workbench (zijn scherm als plaatje), slijtage zonder storingen, en jij met een rifle aan een sling
        op je rug.
      - *Ion Blaster* (20 s): een robot valt op zijn rug, een drone valt naar de grond, een koe merkt niets.
      - *EMP grenades* (30 s): gooien (de granaat vliegt, stuitert en piept steeds sneller), de puls (een blauwe bol: de
        robot en de drone vallen neer, het torentje laat zijn loop hangen, de camera gaat uit), kabels en uitrusting, alles
        weer aan, en een sticky op de robot.
      - *Frag grenades* (26 s): een sticky frag op een stenen muur blaast het blok eruit (een oranje bol), barsten eromheen,
        en een tweede eronder maakt een gat.
      - *Smoke grenade and flashbang* (42 s): een rookbom in een schuurtje met een glazen dak: de wolk blijft binnen en er
        komt een beetje uit de open deur; rode rook in de open lucht; en een flashbang (een witte flits, en je scherm als
        plaatje: nog bijna wit, de wereld schemert erdoor).
      - *Remote EMP and Remote Frag* (35 s): een Remote EMP op de vloer naast een open kabel, van ver af met de EMP Trigger
        (het torentje gaat uit), een Remote Frag op een muur, met de Frag Trigger een blok eruit, en de schaar.
      - *Glock 19* (51 s): schieten op een husk (gele spoortjes, de hulzen springen rechts uit het pistool en blijven
        liggen), hulzen oprapen, het magazijn kogel per kogel, herladen (de HUD als plaatje), storingen en de tap-rack, en
        de skilltree (F6) als plaatje.
      - *Gunsmith Table* (32 s): de zes onderdelen, drie materialen, de tafel als plaatje, en versleten onderdelen.
      - *Reloading Bench* (23 s): een huls, een kogelpunt en buskruit worden een patroon, de bank als plaatje, en je
        hulzen terug.
      - *Holster, pouches and slings* (51 s): je inventaris met de vakjes als plaatje, jij met holster, pouches en een
        rifle op je rug (je draait rond), het wiel als plaatje, het pistool trekken, en een pouch vullen (als plaatje).
      Nieuwe bouwstenen: een **zwevend voorwerp** dat ronddraait (`item`), een **bol van licht** die groeit en wegvaagt
      (`pulse`: de EMP, de flits, een ontploffing, zoals de echte bol van een EMP), **rook** die stap voor stap de lucht
      vult en niet door blokken gaat (`smoke`: door lucht en een open deur wel, door een open deur maar een beetje, door
      glas en muren niet; we tekenen alleen de hobbelige buitenkant van de wolk, en vóór glas, anders verdween hij achter
      een glazen dak), **barsten** in een blok (`crack`: de laag van Minecraft als je aan een blok hakt, net als de echte
      barsten), en een speler met **uitrusting** om (de holster, de pouches en de slings; met Curios in de modpack zie je
      die bij een speler in de animatie niet). Een wezen houdt zijn snelheid na het vliegen, zodat een sticky met zijn
      kussen tegen een muur ligt, en de lont van een granaat telt vanaf het moment dat hij in beeld komt. Tien nieuwe
      plaatjes (`-Ponly=howtopics`): de Gunsmith Table met een half pistool, de Reloading Bench, de Laser Workbench met een
      half geweer, de skilltree, de HUD met een lasergeweer en met de Glock, je inventaris met de vakjes van je uitrusting,
      het wiel, de magazijnpouch en het scherm na een flashbang. Wat ik op de foto's zag en verbeterde: de zwevende
      voorwerpen hingen achter de tekstballon bovenaan (nu lager, en lange teksten in twee stukken), een tekstballon
      tegelijk met een plaatje verdween eronder (nu na elkaar), de rook was achter het glazen dak onzichtbaar, en bij de
      Reloading Bench schoot je op de bank. In de magazijnpouch liepen de twee uitlegregels rechts buiten het scherm (nu
      korter: "Drag or shift-click magazines in and out", "Also from the Alt wheel, or its own key"). Gezien maar niet
      veranderd: op de Laser Workbench liggen de vakjes van de capacitor, de trigger module en de sling van een rifle in de
      uitgeschoven tekening over elkaar, met hun namen erdoor; ze verder uit elkaar schuiven hielp niet meteen (dan schoof
      de capacitor onder het geweer), dus dat blijft een apart klusje. De test van de draaiboeken kijkt nu ook de nieuwe
      bouwstenen na (zwevende voorwerpen niet leeg, een bol met een maat, rook met plek, barsten van 0 tot 9, hooguit vijf
      stuks uitrusting). 454 tests, allemaal groen.
- [x] **10. De beveiliging** — jouw opmerking (8 november): "Ga verder met een volgende categorie: security items." De
      rest van het tabblad Security (`howto/scenes/SecurityItemScenes`): zeven onderwerpen, de Bypass Kit met drie
      animaties (41 van de 51 klaar):
      - *Computer and monitor* (39 s): een computer met een monitor erop, een PoE-kabel naar de kern (de ring rond de
        aan-knop licht op en het scherm gaat aan), instellen in de app Computers (als plaatje: wie hem mag gebruiken, een
        eigen code, per app Off, View of Use), de desktop van de computer (als plaatje), en wie er binnenkwam (Logs).
      - *Block Reinforcer* (39 s): een muur van kwarts, één blok verstevigen (het blauwe randje), sneak + rechtsklik voor
        de hele muur, het randje dat alleen jij met je bril ziet, en twee sticky frags: een gewoon blok vliegt eruit, een
        verstevigd blok krijgt alleen barsten.
      - *Handcuffs* (26 s): een Raven-soldaat van achteren boeien (rechtsklik vasthouden), geboeid met zijn handen op zijn
        rug, meenemen (hij loopt recht voor je, met een ketting van je handen naar zijn polsen) en los met de sleutel.
      - *Auth codes and the Auth Console* (44 s): de code in Status (als plaatje), ermee een codepaneel openen vanuit je
        diagram, een vreemde drukt op de Auth Console (het lampje wordt oranje), jij antwoordt in Requests (als plaatje),
        en de deur gaat open.
      - *Bypass Kit*, drie animaties: *Hacking readers and code panels* (49 s: het stroomcircuit van een lezer en de
        afdrukken op een codepaneel als plaatje, telkens gaat de deur open, en betrapt: rood licht en het alarm), *Cutting
        laser wires* (33 s: de draden in het donker als plaatje, de goede draad zet de hele poort uit tot Repair wires,
        een verkeerde geeft een schok) en *Pickpocketing* (19 s: sluipend achter iemand, de zak als plaatje, en betrapt:
        die draait zich om en jij licht op).
      - *Hacking Laptop* (45 s): inloggen met IP en wachtwoord (als plaatje), je desktop op afstand (als plaatje), en de kern
        van een ander: de laptop staat bovenop de kern, het alarm gaat af, de terminal met de geheugendump (als plaatje).
      - *I.M.T Phone* (26 s): verbinden en een PIN kiezen (als plaatje), de kleine desktop (als plaatje), en een gestolen
        telefoon blokkeren in Access.
      Een speler in een animatie kan nu sluipen en geboeid zijn (`Crouch` en `Cuffed` in zijn gegevens): geboeid tekent het
      spel zelf zijn handen op zijn rug, net als bij een echte geboeide speler of soldaat. Dertien nieuwe plaatjes
      (`-Ponly=howtopics`): de app Computers, de desktop van een computer, de inlog van de laptop, de desktop op afstand,
      Status met de autorisatiecode, een verzoek in Requests, de PIN en de desktop van de telefoon, het stroomcircuit, de
      afdrukken, de draden van een laserzender, de zak van iemand anders en de terminal van de laptop (de rondleiding
      speelt die zelf tot de geheugendump). Wat ik op de plaatjes zag en verbeterde: in de app Computers liep de derde kolom
      apps (Notes, Logs) rechts buiten het vak (de kolommen zijn nu zo breed als er plaats is, en een naam loopt tot vlak
      voor de breedste knop, anders werden Messages en Keycards afgekapt), op het inlogscherm liep de uitleg bij de PIN
      rechts buiten het paneel (nu afgebroken op hooguit twee regels) en die bij de telefoon ook (nu korter: "Your core's IP
      (Status) and password."), op het plaatje van de PIN stond de nep-speler van het veldje half in beeld (die gaat nu
      eerst opzij), en in de terminal van de laptop brak een lange regel midden in een woord af ("they don't mat" / "ch";
      nu op een spatie). Het raster van een lezer, de draden van een laserzender en de woorden van de terminal kiest de
      server zelf, bij elke ronde anders: de rondleiding zet het toeval van de wereld daarvoor op een vast getal, zodat die
      plaatjes elke keer hetzelfde zijn en de pijltjes blijven kloppen. Op de foto's per hoofdstukje stonden
      tekstballonnen voor de muren van de Reinforcer, de Bypass Kit en de Auth Console (nu rechts naast de muur) en voor de
      geboeide soldaat (nu aan de andere kant), wees een pijltje in de app Computers naast de knoppen, en verdween jij bij
      de laptop na het eerste hoofdstukje. 454 tests, allemaal groen.
- [x] **11. De rest** — jouw opmerking (8 november): "Werk de overschot ook nog uit." De laatste tien onderwerpen: nu
      hebben alle 51 een animatie (`PowerScenes.cabinet`, `howto/scenes/InteriorItemScenes`, `howto/scenes/SuitScenes`):
      - *The power cabinet* (31 s): een generator erachter, een torentje en een Arc Lamp aan de zijkanten, stroom die
        als gele streepjes loopt, het deurtje als plaatje (een lampje, "wired" en de schakelaar per kant, Link to I.M.T),
        een kant uit (het torentje laat zijn loop hangen) en schakelen op afstand.
      - *Holo table* (59 s): zonder stroom een rood ringetje, met stroom het hologram, een voorwerp erop toont zijn
        recept, sneak + rechtsklik de zes kleuren, het instelscherm als plaatje, en de kaart, de satelliet en de ertsen
        als plaatje uit de echte wereld (die standen kijken naar de grond om de tafel heen, dus die kunnen niet in het
        wereldje van een animatie).
      - *Suit workbench and upgrades* (50 s): het echte hologram van één stuk (met de gegevens van een echte
        werkbank), een klik op de werkbank kiest het volgende stuk, bouwen (plaatje voor plaatje met de scanring, en dan
        heb jij de borstplaat aan), de laarzen neerleggen, een Thruster Upgrade inbouwen (hij draait om het stuk en
        krimpt erin), en de upgrades van het harnas en van de bril.
      - *Repairing cracks* (37 s): barsten in een muur, Repair Paste (het balkje STRENGTH zakt naar 85%), een blok
        weghakken (het wordt een gebarsten blok), de Crusher en de Compactor naast een generator (gebarsten blok, poeder,
        een nieuw blok), en het scherm van de Crusher als plaatje.
      - *Landing pad* (35 s): het platform met zijn vak van 5 x 5, jij die in het harnas met blauwe vlammen komt
        aanvliegen en landt ("Welcome home"), je bril die laadt, en de batterij van het platform als plaatje.
      - *Arc Lamp and light switch* (48 s): de lamp op een paal aan een kabel, 's nachts als plaatje (het hele veld is
        licht), elke kamer zijn eigen lamp, alleen op netstroom, het instelscherm als plaatje, en de lichtschakelaar op
        de kabel (uit: de lamp gaat uit).
      - *The tech suit* (39 s): de stukken een voor een aan, minder schade (een husk slaat, een blauwe flits), vliegen
        (blauwe straalvlammen onder je laarzen, net als in de wereld), landen, en de laser cannon die een husk wegduwt.
      - *Mini rockets* (31 s): het raketpakket op de borstplaat, een salvo van zes echte mini-raketjes die om de
        beurt links en rechts vertrekken, uitwaaieren en met hun neus naar drie husks draaien (een streepje vuur erachter,
        een ontploffing), herladen, en het magazijn en de springkop.
      - *Drones* (45 s): een drone gebruiken (hij schiet de lucht in), het arsenaal als plaatje (in orbit, beschadigd,
        on a job, grounded), terugroepen in een boog en repareren met ijzer, een zwerm met rode lampjes die op een husk
        duikt en schiet, en de escorte met blauwe lampjes die om je heen vliegt.
      - *Illusion Jammer* (22 s): een valse muur van een onzichtbare cloakingdrone (alleen zijn vonkjes), de jammer
        (een blauwe puls, de muur flikkert en is weg, vonken bij de drone), en de zwakke plekken. Het onderwerp heette in
        de lijst nog "E.M.P."; het item heet in het spel al Illusion Jammer, dus het onderwerp nu ook.
      Nieuwe bouwstenen: een speler met `Thrust` in zijn gegevens krijgt de straalvlammen van het harnas onder zijn
      laarzen (SuitEffects tekent ze nu ook voor een animatie, met hun brede kant naar de kijker), en een mini-raketje
      wijst met zijn neus in de richting waarin het vliegt. Het hologram van de holotafel en van de werkbank tekent de
      animatie met hun echte renderer, gevoed met gegevens zoals een echt blok ze bewaart. Tien nieuwe plaatjes
      (`-Ponly=howtopics`): het deurtje van de stroomkast, het instelscherm van de holotafel en de kaart, de satelliet en
      de ertsen, het scherm van de Crusher, de batterij van een platform, de Arc Lamp 's nachts en zijn instelscherm, en
      het arsenaal. Wat ik op de foto's zag en verbeterde: tekstballonnen voor de lamp van de stroomkast, voor jou
      tijdens het vliegen en voor de doelwitten van de raketten (nu boven de blokken of aan de andere kant), de
      holotafel, de werkbank en het platform waren klein (kleinere platen), en een husk verdween pas seconden na zijn
      ontploffing (een cue van één tick neemt het niet over van een langere; nu duurt elke husk precies tot zijn
      laatste treffer). Op de kaart van de holotafel stond de tafel als een stipje (de rondleiding heeft een brede
      camera; nu van dichterbij en schuin van boven), en bij de ertsen viel de bovenkant weg. Op het scherm van een
      platform met cellen plakte het getal tegen het woord BATTERY ("BATTERY2456 / 400000"; past het niet, dan nu korter:
      "2.5k / 400k"), en de config van de rondleiding (run-visual, niet in git) had voor het platform en de Arc Lamp nog
      de getallen van voor de energie x10 werd (nu gelijk aan de standaard in de code). 454 tests, allemaal groen.

### 64. Grotere datacenters, en racks zonder scherm (8 november)

Jouw wens: "Zorg dat datacenters nog groter moeten zijn, ik wil dat je minstens 100 serverracks hebt om 2 sec delay op imt
en 1 sec delay op diagrams ... vanaf 120 reageert alles instant", en daarna: "het is natuurlijk niet afhankelijk van de
racks maar van hoeveel gpu/cpu. Ik wil dat je voor imt 120 cpu's nodig hebt of 60 gpu's en voor de diagrams heb je 60
gpu's nodig of 30 cpu's. De ssd's en ram zijn hierbij ook belangrijk. Voor elke 10 cpu's of gpu's moet je 3 ssd's en 4
ramsticks hebben om dit vlot te laten verlopen. Druk deze gegevens allemaal in MB, GB en TB uit." En: "dat je vanaf nu met
serverracks kunt interacten niet via een GUI maar echt klikken op het onderdeel zoals bij een boekenkast".

Jouw vier antwoorden:
1. **Rechte lijn** (mijn aanbeveling): de vertraging loopt recht af. Diagrammen: 6 s met bijna niets, elke CPU haalt er
   0,2 s af, 1 s bij 25 CPU's, meteen vanaf 30 CPU's (of 60 GPU's). I.M.T: 12 s met bijna niets, 0,1 s per CPU, 2 s bij 100
   CPU's, meteen vanaf 120 CPU's (of 60 GPU's). Zo kloppen je twee punten (100 = 2 s / 1 s, 120 = meteen) zonder sprong.
2. **Echte maten, limieten weg** (mijn aanbeveling): een RAM-latje is 16 GB, een SSD 1 TB; per 10 CPU's of GPU's 64 GB
   RAM. Minder = naar verhouding trager. (Eerst ook 3 TB SSD per 10; jouw opmerking later die dag: "zorg ook dat de ssd geen
   invloed heeft op de speed, enkel opslag", dus een SSD is alleen opslag. Daarna zette je zelf de standaard op een latje
   van 8 GB, een SSD van 200 GB en 2 MB per blokje; per 10 CPU's of GPU's bleef 64 GB, dus nu 8 latjes.) Een blokje van een diagram is 1 MB en een bezig diagram 64 MB, dus de oude
   grenzen (2 tegelijk per latje, 24 blokjes per SSD) raken praktisch nooit meer op.
3. **Alleen in de Monitor** (jouw eigen antwoord): "je kunt dit enkel op de monitor zien, je kunt elke rack een naam
   geven door die te sneakklikken, dan noem je die bijvoorbeeld rack1 en dan zie je in de monitor op rack1 met zijn stats".
4. **Acht vakjes** (jouw eigen antwoord): "ga van 3 rijen naar 4 rijen, dit geeft je in totaal 8 slots, 2 daarvan kan een
   CPU of GPU, 2 daarvan kan SSD of RAM, 4 daarvan kunnen enkel fans".

**Mijn invulling**: de verhoudingen van 8 oktober blijven (een GPU telt voor je diagrammen half en voor I.M.T dubbel), dus
"120 CPU's of 60 GPU's" en "30 CPU's of 60 GPU's" komen vanzelf uit. Een tekort aan RAM of SSD remt alle CPU's en GPU's
evenveel af (het krapste van de twee telt). De config-getallen hebben nieuwe namen, zodat de nieuwe waarden ook in een
oude wereld gelden. Een oud rack (zes vakjes) wordt bij het laden ingedeeld; wat niet past valt eruit. Hernoemen mag
volgens dezelfde regel als een keycardlezer (wie hem neerzette, of wie vertrouwd is in de basis). In de automatische tests
wacht een diagram hooguit 10 ticks (zoals de netwerkkabel daar ook een uitzondering heeft), anders wacht elke test 6 s.

- [x] **1. De snelheid** — `LogicStats`: een rechte lijn van `logicMaxDelayTicks` (120) naar 1 tick bij `logicInstantPower`
      (30), en van `brainMaxDelayTicks` (240) naar 0 bij `brainInstantPower` (120). `cpusPerPower`, `reactionTicks`,
      `brainDelayTicks` en `brainDelayGoneTicks` zijn weg. Warm: alsof je de helft van je CPU's hebt (zoals altijd).
- [x] **2. Het geheugen** — `ramStickGb` (16), `ssdGb` (1000), `ramGbPerTen` (64): `LogicStats.fed` zegt welk deel van
      de CPU's en GPU's meedraait (alleen het RAM telt; `ssdGbPerTen` is weer weg: een SSD is alleen opslag). `blockMb` (1) en `runMb` (64) vervangen `blocksPerSsd` en `runsPerRam`.
      Alles als tekst in MB, GB en TB (`LogicStats.bytes`): de tooltips ("16 GB per stick. Every 10 CPUs or GPUs need 64 GB
      of RAM"), de app Backup, de melding als een diagram niet past.
- [x] **3. Acht vakjes, klikken zoals een boekenkast** — `RackBay` en de blokstaat `bay_0` tot `bay_7`: bovenaan CPU of
      GPU, dan RAM of SSD, onderaan vier fans. Rechtsklik met een onderdeel op een leeg vakje: erin (past het daar niet,
      dan zegt het "this bay takes a CPU or GPU"); op een vol vakje: eruit, in je rugzak; met iets anders in je hand (een
      kabel) doet de klik wat dat voorwerp doet. Elk onderdeel steekt een halve pixel uit zijn vakje (eigen plaatjes
      `server_bay_*`, aan met knipperende lampjes en draaiende fans; `tools/generate_block_models.py` en
      `generate_textures.py`, functie `server_rack`). Het scherm en het menu van het rack zijn weg.
      Jouw opmerking "de textures zijn niet super mooi" (8 november): alle rijen zijn nu 3 pixels hoog, zodat elk fanvakje
      twee ronde fannetjes heeft (een ring met een naaf, aan loopt er een lichte vlek rond) in plaats van een wit dambord;
      links en rechts rails met gaatjes zoals een echt serverrack, met per rij een lampje; geen dikke cyaan balken meer
      (alleen een streepje bovenaan); rustigere onderdelen (een zilveren CPU-module met greep en sleuf, een zwarte GPU met
      een groen randje en twee fannetjes, RAM met chips en een gouden rand, een grijze SSD-slede met etiket), en een zijkant
      met verticale luchtsleuven.
- [x] **4. Een naam, en de Monitor** — sneak + rechtsklik met een lege hand: het naamscherm van de lezer, dat nu ook een
      rack kent ("In the Monitor app on Security OS: rack1"). De app Monitor (iets groter) toont elk rack met zijn naam;
      klik er een aan en rechts staan zijn acht vakjes in het klein, temperatuur, warmte en koeling, slijtage en wat je moet
      doen; nog eens klikken geeft de hele server (CPU's en GPU's, "RAM 48 GB, need 25.6 GB" in groen of oranje, "SSD 2 TB:
      only storage", hoe snel, koeling, wat er bezig is). De tegels bovenaan (jouw opmerking "laat zien hoeveel er in totaal
      is en hoeveel er al in gebruik is, bv. 100 GB RAM, 1% gebruik: 1 GB"): rechtsboven hoeveel procent er nu in gebruik
      is, eronder hoeveel van het totaal: CPU "3%" van "4 CPU, 2 GPU", BRAIN van je breinkracht, RAM "1 GB / 48 GB" (de
      diagrammen die bezig zijn, plus wat de CPU's en GPU's innemen terwijl ze werken: een stille server zit bijna op 0%,
      denkt I.M.T na dan loopt het op), SSD "4 MB / 2 TB" (wat er echt op staat: diagrammen en backups). Waarschuwingen van
      I.M.T en het logboek noemen het rack bij zijn naam. Diagrammen die nog op de reactietijd van de server wachten, staan
      onder RUNNING als "5 starting (5.7 s)" (eerst stond er "waiting for RAM", maar dat was het niet), en de rij mag zo
      lang zijn als het RAM diagrammen kan houden, zodat een trage server niet "out of memory" zegt (wel "Too slow ... Add
      CPUs" als een run echt te lang wacht).
      De crash die je zag bij het openen van Security OS (`NoClassDefFoundError: SecurityOs$Session`) kwam doordat je spel
      draaide terwijl ik de mod opnieuw bouwde: Gradle haalt de oude klassen even weg, en precies dan wilde je spel ze laden.
      Geen fout in de code (de rondleiding opent de desktop en de Monitor zonder problemen); start het spel opnieuw na een
      build van mij.
- [x] **5. Rondleiding, showcase, How it works en LEESMIJ** — `-Ponly=racks` fotografeert de vakjes van dichtbij, het
      naamscherm en de Monitor per rack (warm, koel, niet verbonden, in brand); de showcase "The datacenter" kijkt in de
      Monitor naar lab-3 in plaats van naar het scherm van het rack; de animatie *Server racks* klikt onderdelen in de
      vakjes, bouwt een muur van racks ("Bigger is faster", met 30 en 120 CPU's en 64 GB uit je config), geeft een
      naam en toont de Monitor als plaatje (`monitor_rack`, door `-Ponly=howtopics`); *Heat and fans* klikt de fans
      onderaan in het rack.
- [x] **6. Tests** — de rechte lijn met jouw getallen, RAM en SSD die de CPU's voeden (62,5% met te weinig RAM), klikken in
      de vakjes en de naam (een vreemde mag niet), en een oud rack dat ingedeeld wordt; de test van de oude grenzen zegt nu
      dat ze weg zijn. 452 tests, groen (`raven_soldier_shoots` blijft af en toe rood door de spreiding, zoals eerder).
- [x] **7. De config en de tooltips nagekeken** (jouw vraag "kun je de config value eens nakijken en kijken of dat in de
      tooltips ook klopt"): de tooltips van de onderdelen halen al hun getallen uit de config (er staat geen getal vast in
      de teksten). Wat niet klopte: in `run/config/imttech.toml` stonden nog 16 GB, 1000 GB en 1 MB, en een getal in het
      bestand wint van de standaard in de code; nu staan daar jouw 8 GB, 200 GB en 2 MB. De uitleg van `ramGbPerTen` zei
      "64 = 4 sticks" (nu 8 latjes van 8 GB), en LEESMIJ en twee tests noemden nog de oude getallen: de tests rekenen nu met
      wat er in de config staat. Een nieuwe test kijkt dat elke tooltip (RAM, SSD, CPU, GPU, fan) de getallen uit de config
      toont en meeverandert als je er een verandert. 453 tests, groen.

### 65. De kijker van de sniper in F5 (8 november)

Jouw vraag: "Kun je zorgen dat scope met sniper in F5 niet meer raar is. Als je nu met een sniper scopet in F5 dan zie je
jezelf door de scope."

**Mijn keuze** (zoals in de meeste shooters): door de kijker kijk je altijd door je eigen ogen. In F5 hangt de camera
achter je hoofd (of ervoor), dus zag je door de kijker je eigen hoofd, 4 keer ingezoomd.

- [x] **Door je eigen ogen** — `ScopeView`: zolang de kijker op je scherm staat, zet hij de camera op eerste persoon, en
      gaat de kijker weg, dan meteen terug naar de stand die je had (F5 van achteren of van voren). Hij wisselt vlak voor
      elk beeld (`RenderFrameEvent.Pre`), precies wanneer de kijker verschijnt of weggaat, zodat de kijker de sprong
      afdekt. Alleen als jij de camera bent (niet als je door een drone kijkt). F5 tijdens het richten: je blijft door de
      kijker kijken en kiest de stand waarin je straks terugkomt. De stand van de camera staat niet in options.txt, dus
      ook als het spel stopt terwijl je richt blijft er niets verkeerd staan.
- [x] **Geen zoom op je rug** — tot de kijker er is (de eerste 4 ticks van het richten) zoomt de sniper in derde
      persoon niet in (`GunControl.zoom`, ook de muis blijft dan even gevoelig); daarna 4 keer, zoals in eerste persoon. De
      andere geweren zoomen in derde persoon zoals vroeger (1,2 tot 1,4 keer, over je schouder).
- [x] **Eén regel voor "door de kijker"** — `GunControl.scoped` (de sniper, meer dan 0,92 gericht): daarmee tekent
      `GunHud` de kijker, laat `GunRenderer` het geweer weg en wisselt `ScopeView` de camera. Die 0,92 stond eerst op twee
      plekken.
- [x] **Bekeken in het spel** — `-Ponly=scopef5`: in F5 richten (nog zonder zoom), door de kijker (de husk in het
      dradenkruis, geen hoofd), schieten, F5 tijdens het richten (je blijft in de kijker), en loslaten (meteen terug, nu
      van voren). De log noemt telkens de stand van de camera en de kijkhoek ("[dev] scopef5"). LEESMIJ aangepast. 454
      tests, groen (in een eerste ronde ging bij `glock_chamber_and_magazine_math` schot 6 niet af: de Glock-tests van
      de batch "guns" lopen tegelijk, en een test die eerder klaar was zette met `restore()` de kans op storingen terug
      voor deze test schoot; niets met de kijker te maken, de tweede ronde was helemaal groen).

### 66. Grote getallen: 5 000, 5.000 of 5k (9 november)

Jouw vraag: "Kun je zorgen dat bij het displayen van eenheden zoals 5000 FE je de optie hebt of scheiding met spatie 5 000
of met punt 5.000 of gebruik grootheden zoals 5k FE of bijvoorbeeld 5000000 -> 5M FE".

**Mijn keuzes:** vijf stijlen, **PLAIN** (5000, zoals vroeger), **SPACE** (5 000, de standaard: leest het makkelijkst en
is overal hetzelfde), **DOT** (5.000, en dan een komma in kommagetallen: 0,5), **COMMA** (5,000, zoals in het Engels, voor
wie de mod van CurseForge haalt) en **SHORT** (5k, 1.73M, 3G: drie cijfers die tellen). Het geldt voor stroom (FE en FE
per tijd); blokken tellen (de app Zones) en RAM/SSD in Monitor blijven zoals ze waren.

- [x] **De stijlen** — `config/NumberStyle`: `format` (hooguit twee cijfers na de komma, zoals `Numbers.text`; kort
      rondt af naar de volgende eenheid, dus 999 999 wordt "1M" en niet "1000k"), `example` (5000 in die stijl, het
      opschrift van het knopje) en `spoken` (voor de stem weer gewone cijfers: "1 500" las ze anders als "een vijfhonderd",
      "5k" wordt "5 thousand"). De spatie is een harde spatie (U+00A0): Minecraft breekt een regel alleen op een gewone
      spatie, dus "1 728 000 per hour" valt nooit in twee stukken. Minecraft kende die spatie niet (dan nam het een teken
      uit unifont); `assets/minecraft/font/include/space.json` geeft hem de breedte van een gewone spatie.
- [x] **Kiezen** — een knopje naast /tick in de generator, de Power Battery, de koepel en de Arc Lamp (er staat 5000 in
      je stijl op), en G > Information > Numbers (I.M.T zegt daarna "Big numbers now look like 5 000."). Bewaard als
      `numberStyle` in `imttech-client.toml`.
- [x] **Overal** — `Numbers.amount`: de schermen van de generator (het getal in het midden, de tooltip, het net ernaast),
      de Power Battery (STORED; de kop met de maat wordt afgekapt vóór de knopjes), het landingsplatform, het laadstation,
      de koepel en de Arc Lamp (via `PowerUnit.rate`, dus ook alles per tick/seconde/minuut/uur), het G-menu (kosten en
      ENERGY), de app Zones, de tooltip van de bril en het lijf van een robot, en de animaties van How it works. Past een
      getal niet (het midden van de generator, STORED, het landingsplatform), dan kort, zoals het landingsplatform al deed
      (die eigen `compact` is weg).
- [x] **Meldingen van de server** — "Not enough energy. Required: 1 500 FE." en "No Battery Cells inside: each cell
      stores 2 000 000 FE" maakt de server, die jouw instellingen niet kent. Je spel stuurt je stijl bij het inloggen en bij
      elke wissel (`NumberStylePayload`, protocol 35); de server onthoudt hem per speler tot je uitlogt
      (`Numbers.amount(player, ...)`). Wie nog niets liet weten, krijgt de standaard. Een onbekende stijl (een nieuwere
      versie) wordt ook de standaard.
- [x] **Test** — `number_styles_for_energy`: elke stijl, kommagetallen, een min, afronden in SHORT, de stem, en de server
      die per speler onthoudt (heen en terug door het netwerk, en een stijl die hij niet kent). LEESMIJ aangepast. 455
      tests, groen.
- [x] **Bekeken in het spel** — `-Ponly=numbers`: de generator en de Power Battery in alle vijf stijlen (telkens met
      een klik op het knopje, zoals een speler), het landingsplatform in alle vijf, het laadstation in drie, het G-menu
      (Systems en Information) met punten, de koepel en de Arc Lamp met komma's. De harde spatie staat er als een gewone
      spatie ("6 005 558 / 18 000 000 FE"). Wat ik zag en verbeterde: **Numbers** stond eerst als achtste optie op
      Systems, en dan lagen de vakjes linksonder en rechtsonder over de hint "Scroll: tab · Release or click: choose"
      (die staat daar lager, onder de info over de bril); nu staat Numbers op **Information**, bij Cable X-ray en Power
      flow, en heeft Systems weer zijn zeven. In het **laadstation** liep "I.M.T energy: 10000 / 10000 FE" al rechts
      buiten het scherm (met spaties nog iets verder): nu het kopje "I.M.T ENERGY (FE)" erboven en de getallen eronder
      (kort als het niet past). Daar liep ook "Upgrades: lay the glasses on the Suit Workbench" buiten het scherm; nu
      "Upgrades: on the Suit Workbench". Het knopje van de koepel paste niet in de rondleiding zonder koepel; die zet ze
      nu eerst neer.

### 67. De armory: het Pegboard en de Ammo Box (9 november)

Jouw vraag: "Kun je een armory items maken zodat je al de guns mooi op de muren kunt hangen, ook wil ik ammoboxens waar je
mee kunt interacten om ammo in en uit te doen zonder een GUI dus door te klikken of schiftklikken. Vragen stel ze dan."

**Jouw antwoorden** (twee rondes vragen):
- Het rek aan de muur is een **gaatjesbord** (Pegboard): je hangt een wapen waar je klikt, vastgeklikt op een raster, en
  panelen naast elkaar worden één wand (een sniper kan over twee panelen hangen).
- Erop mag: **alle wapens** (de Glock en de vier lasergeweren, met alles erin), **magazijnen en cellen**, **granaten** (ook
  de Remote EMP/Frag en hun triggers) en **holster, pouches en sling**.
- **Iedereen** mag eraan (zoals een item frame).
- In een Ammo Box: **9mm-patronen, hulzen en kogelpunten, Glock-magazijnen en energiecellen**, en dan **één soort per kist**
  (wat er als eerste in gaat; leeg = weer vrij), met een plaatje en het aantal op de voorkant.
- Klikken: **rechtsklik** met spullen = de stapel erin, **sneak + rechtsklik** = alles van die soort uit je inventaris;
  **lege hand** = een stapel eruit (of één magazijn/cel), **sneak + lege hand** = er één uit.
- **Rechtsklik inhouden met een magazijn** op een kist patronen = patroon na patroon laden, zo snel als R; een
  **lasergeweer op een kist cellen** = je cel wisselen voor de volste.
- **Afgebroken houdt hij zijn inhoud** (zoals een shulkerdoos), en de tooltip zegt wat erin zit.

**Mijn invulling** (zeg het als je het anders wilt):
- Het raster heeft **8 x 8 gaatjes per paneel** (om de 2 pixels). De maten in gaatjes: sniper 10 x 3, rifle 8 x 3, shotgun
  7 x 3, laserpistool 4 x 4, Glock 4 x 3, magazijn en cel 1 x 3, trigger 1 x 4, granaat, Remote EMP/Frag en pouch 2 x 2,
  holster 2 x 3, sling 3 x 3. De **Ion Blaster** mag er ook op (4 x 4): het is een wapen.
- Een wapen komt met zijn **midden op je klik**; past het niet, dan het dichtstbijzijnde plekje tot 2 gaatjes ernaast. Een
  **blauw kader** laat zien waar het komt (rood: geen plek), een **wit kader** wat je met een lege hand zou pakken.
- **Sneak + rechtsklik met een lege hand draait** wat hangt (loop naar links, rechts, beneden, boven); een stand die niet past
  wordt overgeslagen. Pakken gaat in je hand.
- Wat je uit een **kist** neemt komt in je inventaris, **niet in je hand** (anders stopte de volgende rechtsklik het er
  meteen weer in, met de stapel in je hand).
- **Sneak + rechtsklik inhouden met een magazijn** op een kist patronen: patroon na patroon terug in de kist. Een **Glock op
  een kist magazijnen** wisselt je magazijn voor het volste, zoals een lasergeweer met cellen.
- Een kist neemt **1024** losse patronen, hulzen of kogelpunten (`[armory] ammoBoxLoose`, een echte munitiekist houdt er
  zo'n 1000) of **12** magazijnen of cellen (`ammoBoxPieces`). Hooguit 16 kisten op een stapel; lege kisten stapelen met
  nieuwe.
- Recepten: **4 Pegboards** uit 4 stalen staven en 5 ijzerklompjes (om en om), een **Ammo Box** uit 2 ijzer, een hendel
  (de sluiting) en 5 stalen staven. Beide met een houweel.
- De panelen zijn **lichtgrijs** staal met donkere gaatjes: op een donker paneel vielen de zwarte Glock en magazijnen weg.

- [x] **Het Pegboard** — `armory/PegboardBlock`, `PegboardBlockEntity` (wat erop hangt: het item, zijn gaatje linksonder op
      dit paneel en hoe het hangt) en `Pegboard` (het raster over de hele wand: waar iets past, wat er over een gaatje
      hangt, waar je kijkt). Een paneel van 1 pixel dik; de wapens hangen 1,7 pixel van de muur aan stalen haakjes.
      Waar je klikt rekent het uit op de diepte van de wapens (anders klikte je schuin van opzij een beetje naast). Breek
      je een paneel af, dan valt wat erop hing eraf, ook wat van een paneel links of onder erover hing.
- [x] **De wapens zoals ze zijn** — `client/armory/PegboardRenderer`: een Glock uit zijn onderdelen (in hun materiaal, met
      het magazijn als het erin zit), een lasergeweer uit zijn onderdelen (met de Nether Star, de sling en de cel), de rest
      het gewone 3D-model. Elk model wordt gemeten (eenmalig per stand) en zo groot getekend als in zijn rechthoek past; een
      magazijn staat rechtop (in zijn model staat het schuin, zoals in de greep), een opgerolde sling op zijn kant.
- [x] **De Ammo Box** — `armory/AmmoBoxBlock`, `AmmoBoxBlockEntity` (één soort, stapels van 64 of losse stukken) en
      `AmmoBoxes` (wat elke klik doet, op de server en in je spel hetzelfde). Laden en wisselen gebruiken de echte
      handelingen: een patroon laden is LOAD_ROUND van de Glock met de kist als bron (`Firearms.FROM_BOX`), een magazijn
      wisselen is RELOAD_POUCH met de kist (`Firearms.BOX_SLOT`), een cel wisselen het herladen van een lasergeweer
      (`LaserGuns.FROM_BOX`); dus dezelfde tijden, animaties en ervaring. Minecraft herhaalt een rechtsklik op een blok
      elke 4 ticks zolang je hem inhoudt: zo wordt inhouden patroon na patroon. Sneak + rechtsklik met iets in je hand
      slaat Minecraft normaal over (dan zet je een blok ernaast): voor een Ammo Box zegt `AmmoBoxes.onRightClickBlock`
      "gebruik het blok toch" als wat je vasthoudt iets met de kist te maken heeft. Afbreken: de inhoud gaat mee als
      `minecraft:container` (loot table: copy_components). Het etiket tekent `client/armory/AmmoBoxRenderer`.
- [x] **Met een wapen in je hand** — een pistool of lasergeweer maakt van rechtsklik "richten" en gebruikte dan geen blokken
      (FirearmControl, GunControl). Doet een rechtsklik iets met een Pegboard of Ammo Box, dan richt je nu niet en mag de
      klik door (`ArmoryClient.usesArmory`). De HUD van een magazijn zei bij een kist "No 9mm Rounds in your inventory"
      (rood); kijk je naar een kist patronen, dan nu "Hold right-click: 237 in box". Hulzen oprapen (rechtsklik op de
      grond) gebeurt niet als je op een armory-blok klikt.
- [x] **Modellen** — `tools/generate_armory_models.py` (eigenaar van alles wat het schrijft): het paneel, het haakje, en de
      kist (olijfgroen staal met een rood-gouden streep, een hengsel op het deksel, een sluiting opzij, twee scharnieren
      en een etiket).
- [x] **How it works** — onderwerp 52, "Armory: pegboard and ammo box" (tabblad Weapons, 58 s, `ArmoryScenes`): de
      panelen op een muur, een rifle en een sniper over twee panelen ophangen, de rest erbij, de Glock draaien en pakken,
      de kist vullen (een stapel, dan alles), een stapel eruit, een magazijn laden (de kist telt af), wisselen, en
      afbreken. Eerst stond de wand schuin en klein in beeld; nu een wand van 2 x 2 in het midden, bijna recht van voren.
- [x] **Tests** — `ArmoryGameTests` (6): één soort per kist, erin en eruit (ook sneak, en vol is vol); de inhoud blijft bij
      afbreken en komt terug bij neerzetten (een lege kist stapelt); een magazijn patroon na patroon laden en terug
      (zonder patronen in je inventaris); cellen en magazijnen wisselen voor de volste (en niet voor een legere); ophangen
      op het raster (een sniper niet op één paneel, wel over twee; niets over elkaar; draaien); pakken en draaien door
      iemand die ernaar kijkt (precies hetzelfde item terug), en een paneel weghalen laat een rifle vallen. In een eerste
      ronde draaide de sniper "door de rand heen": de volgende stand is "loop naar rechts", en die past wel; de test was
      fout, en draaien slaat nu standen over die niet passen. 461 tests, groen.
- [x] **Bekeken in het spel** — `-Ponly=armory`: een wand van 5 x 2 panelen vol (`ArmoryDisplay`), vier kisten (240
      patronen, 57 hulzen, 5 magazijnen, 4 cellen), het blauwe kader met een rifle, ophangen, het witte kader, draaien, en
      rechtsklik inhouden met een leeg magazijn (in 2 seconden 8 patronen, de kist telt af). Showcase **120** ("Armory:
      Pegboard and Ammo Box"). LEESMIJ aangepast.

### 68. De Remote EMP, de Remote Frag en hun triggers in hun vakje (9 november)

Jouw vraag (met een screenshot van het wapentabblad): "als je kijkt naar de 4 remote items (de emp en de frag met hun
afstandsbedieningen) dan zie je dat die texture er wat uitvalt, kun je dat oplossen aub, maak de remote frag en emp ook
even groot als de sticky's".

- [x] **Remote EMP en Remote Frag** — `tools/generate_weapon_models.py` (`remote_charge`): in je inventaris en in een
      lijstje nu schaal 1,25 zoals de sticky's (was 1,7), met hetzelfde licht van voren (`gui_light: front`; als blok
      kreeg hij het licht van opzij). Zijn kussen ligt onderaan het blok (y = 0) en zijn antenne steekt omhoog: de
      verschuiving wordt nu uitgerekend (`model_display.gui`), zodat het hele ding met antenne in het midden van zijn
      vakje staat. Vroeger stak hij er onderaan en met zijn antenne bovenaan uit.
- [x] **De triggers** — `trigger_display`: met de antenne erbij zijn ze bijna 15 pixels hoog, en op schaal 1,25 stak de
      antenne bovenaan uit hun vakje. Nu precies 15 pixels hoog in het midden (`TRIGGER_SIZE`, schaal 1,04 en 1,06).
- [x] **Bekeken in het spel** — `-Ponly=creative`: op het wapentabblad passen alle vier in hun vakje, en de Remote EMP en
      Remote Frag zijn even groot als de Sticky EMP en de Sticky Frag. In je hand, op de grond en als blok niets veranderd.
- [x] **Ook op het Pegboard** (jouw vraag: "Zorg dat die ook even groot zijn bij het armory board gedeelte") —
      `PegboardRenderer.sizedLike`: het Pegboard tekent elk item zo groot als in zijn rechthoek past, en door hun antenne
      werden de Remote EMP en Remote Frag zo'n 11 % kleiner dan de sticky's (ook 2 x 2 gaatjes). Nu krijgen ze de schaal
      van hun sticky: de bol is even groot, en de antenne past net in dezelfde rechthoek (4,05 van de 4 pixels). De
      rechthoek zelf bleef 2 x 2, dus waar iets past veranderde niet. De voorbeeldwand (`ArmoryDisplay`) heeft nu ook een
      Remote Frag, boven de Remote EMP met een gaatje ertussen. Bekeken met `-Ponly=armory` en een nieuw plaatje van
      dichtbij (`ar03b_grenades_close`): de sticky's links en de remotes rechts, allebei even breed.
- [x] **De onderkant even hoog** (jouw vraag: "Zorg ook dat de bodem van de remote en de sticky op dezelfde hoogte hangen,
      de antenne moet hoger uitsteken, niet de bodem lager") — een remote stond met antenne en al in het midden van zijn
      rechthoek, dus hing zijn bol lager dan die van de sticky ernaast. Nu meet het Pegboard alles aan de sticky (de
      draaiing, de grootte en het midden) en schuift het de remote in zijn model naar de plek van de bol van de sticky
      (als blok staat de remote op de vloer, y = 0, de sticky op y = 5; de verschuiving wordt gemeten: het verschil
      tussen de hoeken linksonder van beide modellen, `PegboardRenderer.raw`). De bol hangt nu precies waar die van de
      sticky hangt, de antenne steekt zo'n pixel boven de rechthoek uit. Op de voorbeeldwand hangt de Remote EMP weer op
      de rij van de sticky's, de Remote Frag erboven en de Ion Blaster een gaatje hoger. Op `ar03b_grenades_close`: de
      kussens van de twee sticky's en van de Remote EMP op dezelfde lijn.

### 69. Granaten oprapen en teruggooien, ladingen verplaatsen (9 november)

Jouw vraag: "Kun je zorgen dat je granades kunt oppakken en teruggooien/plaatsen, bijvoorbeeld iemand gooit een emp maar
jij gooit die terug of er hangt een remote frag op je base en jij hangt die gewoon ergens anders".

**Jouw antwoorden:**
- **De lont loopt door** als je een scherpe granaat opraapt (zoals in CoD): je hebt de rest van zijn tijd (een bal 3 s
  na het gooien, een sticky 2 s na het plakken). Te lang vast = hij gaat bij jou af.
- Een **sticky** mag je ook **van een wezen** trekken: van een muur, de vloer of het plafond, maar ook van een speler, een
  robot, een drone of jezelf.
- Een **Remote EMP of Remote Frag** haal je eraf met **sneak + rechtsklik met een lege hand**. Hij blijft **scherp, van de
  eigenaar en aan diens trigger**: drukt die, dan gaat hij af waar jij hem ophing, of in jouw inventaris als je hem nog
  draagt en in bereik bent. De **schaar** blijft "onschadelijk maken" (dan is hij van jou).
- **Oprapen = rechtsklik erop, wat je ook vasthoudt** (ook een geweer). De granaat komt in je hand; wat je vasthield
  **valt op de grond**, tenzij het in je **holster of sling** past: dan gaat het daarheen (jouw eigen antwoord).

**Mijn invulling** (zeg het als je het anders wilt):
- Zoals bij trekken uit het wiel (hoofdstuk 52) gaat ook een granaat, magazijn of cel die je vasthield terug in zijn
  pouch als die plek heeft (`Gear.intoHand`, dezelfde regel).
- Een opgeraapte granaat heet **"Live EMP Grenade"**, **glimt**, **piept** in je inventaris (steeds sneller, zoals op de
  grond) en zegt onderaan je scherm hoeveel seconden hij nog heeft ("Live EMP Grenade: 1.6 s! Right-click to throw it
  back", goud, de laatste 0,7 s rood). **Rechtsklik** gooit hem met de rest van zijn lont, **Q** laat hem vallen (dan
  rolt of plakt hij weer als granaat), **wacht je te lang** dan valt hij uit je hand en gaat hij bij jou af. Ook aan je
  muis (met een scherm open) telt hij af.
- Een scherpe granaat past **niet in je granaatpouch, op een Pegboard, in een bundel of shulkerdoos** (daar zou zijn lont
  stilstaan). In een **kist** blijft hij scherp: wie hem eruit haalt, krijgt hem meteen af.
- Een **smokebom die al rookt** raap je niet op (de rook blijft waar hij begon). Een sticky die nog **vliegt** ook niet
  (hij staat pas op scherp als hij plakt).
- Een **sticky die op jou plakt** kun je niet aankijken: zolang er een op je zit, trekt je **eerste rechtsklik** hem eraf.
- Een granaat is nu **aanklikbaar**, maar **kogels, raketten, lasers en de Ion Blaster** vliegen er gewoon langs, en
  linksklik doet niets.
- Een opgeraapte granaat is **altijd op als je hem gooit, ook in creative** (anders bleef hij in je hand en ging hij daar
  af).
- Een scherpe lading die je **draagt** gaat af als de eigenaar drukt (in bereik van zijn trigger, 128 blokken); ook een
  die **op de grond** ligt. In een kist of shulkerdoos niet (daar komt het signaal niet).
- De trigger van de eigenaar **hoort waar zijn lading nu hangt** (de bordjes boven je ladingen kloppen weer), ook als hij
  offline was: dat onthoudt de wereld (`MovedCharges`).
- Hangt iemand hem op de **zone van een ander**, dan gaat het alarm daar af (zoals altijd; van de eigenaar uit gezien). Haalt
  iemand hem eraf, dan gaat de **rode markering weg**, het **logboek** zegt "taken off by ...", en de eigenaar hoort
  "Your Remote Frag at ... was taken off. It still listens to your trigger.".
- Een lading die **al afgaat** (iemand drukte net) haal je er niet meer af. Een lading die bij **geen trigger** hoort,
  is gewoon een losse (niet scherp).

- [x] **Oprapen** — `weapons/GrenadeEntity`: een granaat die op scherp staat is aanklikbaar (`isPickable`, een iets
      grotere klikrand), en `interact` raapt hem op (`GrenadePickup.pickUp`): het item (`asItem`: de juiste soort, een
      smokebom met zijn kleur) krijgt het component `LIVE_FUSE` (de speltijd waarop hij afgaat) en komt via
      `Gear.intoHand` in je hand. `canBeHitByProjectile` = nee, `isAttackable` = nee; `LaserGuns` en de Ion Blaster
      slaan granaten over.
- [x] **Rechtsklik, wat je ook vasthoudt** — `client/weapons/GrenadeGrab` kijkt als eerste (EventPriority.HIGHEST) naar
      de rechtermuisknop: kijk je naar een scherpe granaat (of zit er een sticky op jou), dan stuurt hij de klik op dat
      wezen naar de server en gebeurt er verder niets (anders zou een geweer richten). `GunControl` en `FirearmControl`
      richten dan ook niet.
- [x] **De scherpe granaat** — `GrenadeItem`: `inventoryTick` (piepen, aftellen onderaan je scherm, afgaan bij jou via
      `release`), `onEntityItemUpdate` (gevallen = weer een granaat), `isFoil` (glimt), `canFitInsideContainerItems`,
      de naam "Live ..." en een regel in de tooltip. `use` gooit hem met de rest van zijn lont. `GearItem` (pouch) en
      `Pegboard` weigeren hem.
- [x] **Ladingen verplaatsen** — `RemoteChargeBlock.useWithoutItem`: sneak + lege hand = `takeOff` (het component
      `LIVE_CHARGE`: trigger, eigenaar, naam en waar hij hing; `LiveCharge`). `onRightClick` zegt alleen "gebruik het
      blok toch" als je in je andere hand iets hebt (zoals bij de Ammo Box). `RemoteChargeItem` hangt een scherpe weer op
      met dezelfde eigenaar en trigger (`RemoteChargeBlockEntity.restore`) en zegt het tegen `MovedCharges` (opgeslagen
      in de wereld: per trigger "weg van daar, nu hier"). `TriggerItem.catchUp` neemt dat over (elke tick in de
      inventaris van de eigenaar, en bij drukken); `TriggerItem.fire` laat ook gedragen en losliggende scherpe ladingen
      afgaan (`RemoteChargeBlockEntity.goOffLoose`). `BlastAlarm.takenOff`: markering weg, logboek, de eigenaar hoort het.
- [x] **Teksten** — tooltips ("Thrown at you? Right-click it to pick it up and throw it back", "Sneak + right-click with
      an empty hand takes it off, still live", "Live: Raven's Frag Trigger sets it off, even in your pocket"), de
      meldingen bij eraf halen en ophangen, en de hint bij linksklik op een Remote Frag.
- [x] **How it works** — onderwerp 53, "Throwing grenades back" (tabblad Weapons, 69 s, `GrenadeScenes.throwback`): een
      vreemde gooit een EMP-granaat, jij raapt hem op (je Glock gaat in je holster) en gooit hem terug (hun robot valt
      neer); een sticky op jou trek je eraf en gooi je op hun robot; een Remote Frag op jouw muur haal je eraf, je hangt
      hem op hun muur, en hun eigen knop blaast die open. Het hoort bij elke granaat en lading (na hun eigen uitleg).
      In "Remote EMP and Remote Frag" zegt het laatste hoofdstukje nu ook "sneak + right-click with an empty hand takes
      it off live". Eerst stond de vreemde precies voor het gat in zijn muur; nu staan hij en zijn robot opzij en hangt
      de lading aan het linkeruiteinde.
- [x] **Tests** — `ThrowbackGameTests` (6): oprapen houdt de lont (geen nieuwe), de steen valt, niet in de pouch of op
      het Pegboard, terug met de rest van de lont; de Glock gaat in de holster; te lang vast = afgaan bij jou (de robot
      naast je valt), laten vallen = weer een granaat met de rest van zijn lont; een sticky van een robot trekken en tegen
      een muur gooien (een smokebom die rookt raap je niet op); een Remote Frag eraf (via de echte klik van de server),
      ophangen (niet aan de trigger van de verdediger), de trigger van de aanvaller weet waar hij hangt en blaast die muur
      open; een gedragen Remote EMP gaat af in de rugzak. 467 tests, groen (twee keer).
- [x] **Bekeken in het spel** — `-Ponly=throwback` (tb01 tot tb11), met de echte toetsen: rechtsklik met een Glock in je
      hand raapt de granaat op ("Live EMP Grenade: 4.0 s!", de Glock zat daarna in de holster), terug: de robot ligt; een
      sticky op jou (van voren bekeken), eraf, tegen de muur; een Remote Frag van "Raven": eraf ("Taken off, still live:
      Raven's Frag Trigger can set it off") en op de vloer ("Hung up, still live"). Twee dingen die ik zo vond: de klik
      op een Remote Frag werd in je spel geannuleerd, en dan stuurt NeoForge hem niet naar de server (de tests klikten op
      de server en zagen dat niet); nu doet het blok het zelf (`useWithoutItem`). En in de rondleiding mikte de speler
      voor hij ging sluipen: sluipen zet je ogen lager, het kruisje zakte onder de lading (de muur kreeg de klik). In het
      echt mik je terwijl je sluipt; de rondleiding doet het nu ook zo. `-Ponly=howtoall,howto:grenade_throwback`: de
      animatie per hoofdstukje. Showcase **121** ("Throwing grenades back"). LEESMIJ aangepast.

### 70. Het Pegboard: vastzetten, hoeken, dichtbij, en de flashbang (9 november)

Jouw vraag: "items op armory locken zodat als je het pakt je het de volgende keer simpel kunt terug hangen op de zelfde
plek; zorgen dat je de boards in een hoek kunt plaatsen zodat je een hoek van 90° van boards kunt hebben; de plaats en er
afhalen range minder, nu kun je van 5 blokken ver iets doen, ik wil dat dit maar 1 block is; flashbangs even groot maken
als de rest want ze zijn nu net iets kleiner".

**Jouw antwoorden:**
- **Vastzetten = sneak + linksklik** op wat hangt (een slotje erbij; nog eens = los). Zonder sneak, of op een lege plek,
  breekt linksklik het paneel zoals altijd.
- Pak je een vastgezet item, dan blijft zijn **schaduw**. **Terughangen = klikken op de schaduw zelf** (niet ergens op de
  wand).
- Op een schaduw past **hetzelfde soort** (elke Glock op een Glock-plek, elk Glock-magazijn op een magazijnplek, elke Laser
  Rifle op een rifle-plek); niets anders kan eroverheen.
- **Bereik: maximaal 1 blok ervan**: je staat tegen het bord of in het blok ervoor (2 blokken van je ogen tot waar je klikt).

**Mijn invulling** (zeg het als je het anders wilt):
- De **hoek** is een **binnenhoek**: twee panelen in hetzelfde blok, elk tegen zijn eigen muur. Een buitenhoek (om een
  pilaar) kon al: daar zitten de panelen in verschillende blokken. Het tweede paneel zet je door met een Pegboard op de
  andere muur te klikken. Afbreken haalt ze allebei weg (2 Pegboards terug, en alles wat erop hing).
- In een hoek is het **gaatje direct naast de andere muur dicht** (daar zit dat paneel, 1 pixel dik). De **twee gaatjes
  daarna** liggen binnen het stuk waar de wapens van de andere muur uitsteken (tot 5,2 pixels van de muur): daar mag iets
  hangen zolang op de andere muur op die hoogte niets in zijn eigen hoekstrook hangt. Verder van de hoek botst niets.
  Hangt er al iets in dat eerste gaatje, dan komt er geen tweede paneel ("Take down what hangs in the corner first").
- De **schaduw** is het item zelf, plat (een honderdste pixel dik) en lichtgrijs op het paneel geverfd, zoals op een
  echt gereedschapsbord, met het slotje rechtsboven. Een schaduw is geen echt item: hij valt nooit op de grond. Met een
  lege hand een **grijs kader** op een schaduw; rechtsklik zegt "Locked spot for a Glock 19: right-click it with one to
  hang it back. Sneak + left-click unlocks it."
- Het **slotje**: messing met een stalen beugel, zo'n 1,4 pixel breed, rechtsboven op het item.
- **Iedereen** mag vastzetten en losmaken (zoals iedereen eraan mag).
- **Te ver** met iets dat erop mag: "Too far away: stand right in front of the Pegboard (at most 1 block away)", en verder
  gebeurt er niets (een granaat gooi je dan ook niet tegen je eigen wand). Met een geweer in je hand richt je dan gewoon.
  Van verder weg zie je ook geen kader. Het bereik staat in de config (`[armory] pegboardReach = 2`). De **Ammo Box**
  bleef zoals hij was (jouw vraag ging over ophangen en eraf halen).
- De **flashbang** op het Pegboard krijgt de grootte van de Frag Grenade (dezelfde bol); zijn gouden pin steekt een klein
  beetje boven zijn rechthoek uit, net als de antenne van een remote. In je inventaris en in je hand was hij al even groot.

- [x] **De hoek** — `PegboardBlock`: de blockstate `corner` (none, left, right: de muur links of rechts zoals jij voor het
      eerste paneel staat); `canBeReplaced` (alleen met een Pegboard op de muur ernaast, zoals een halve plaat) en
      `getStateForPlacement` voegen het tweede paneel toe (`Pegboard.cornerFree`); de vorm is beide panelen; `wallOf`
      zegt op welk paneel je klikte; `mirror` wisselt links en rechts. `Pegboard.sideFacing`, `isBoard` (beide muren),
      `otherWall`, `otherOnRight`, en in `fits` de regels voor de hoek (`clearOfCorners`, `CORNER_ZONE` = 3, `FRONT`).
      `PegboardBlockEntity.Hung` weet nu op welk paneel het hangt (`side`). De blockstate is een multipart
      (`tools/generate_armory_models.py`: `pegboard_blockstate`), de loot table geeft er 2 bij een hoek.
- [x] **Vastzetten en de schaduw** — `Hung.locked` en `Hung.shadow`; `take` laat een schaduw achter, `fill` hangt iets op
      een schaduw, `setLocked`; `dropAll`/`dropLoose` laten schaduwen niet vallen. `Pegboard.sameKind`, `shadowFor`;
      `PegboardBlock.hangAt` kijkt eerst of je op een schaduw van hetzelfde soort klikte; `toggleLock`. In je spel:
      `client/armory/PegboardLock` (EventPriority.HIGH, ook met een geweer in je hand) vangt sneak + linksklik op als je
      dichtbij naar iets kijkt dat hangt, eens per klik (Minecraft herhaalt linksklik elke tick), en stuurt
      `PegboardLockPayload`; de server kijkt zelf na (`handleLock`). `PegboardRenderer`: de schaduw (een
      `VertexConsumer` die alles even lichtgrijs verft), het slotje (`models/armory/lock.json`), het kader op een schaduw.
- [x] **Dichtbij** — `Pegboard.inReach` (de config `PEGBOARD_REACH`, de server 0,3 blok speling): ophangen, pakken,
      draaien, vastzetten, het kader en `ArmoryClient.usesArmory` (een geweer richt verder weg gewoon).
- [x] **De flashbang** — `PegboardRenderer.sizedLike`: de flashbang wordt gemeten aan de Frag Grenade.
- [x] **How it works** — onderwerp 52 "Armory": twee nieuwe hoofdstukjes, "Lock a spot" (de Glock weer ophangen,
      sneak + linksklik: slotje; pakken: schaduw; een titanium Glock op de schaduw) en "Corners" (een zijmuur, met een
      Pegboard erop klikken: het tweede paneel; een flashbang, een smokebom en een laserpistool erop). "Take and turn" zegt
      nu ook "only from close by: at most one block away". De camera van deze animatie staat nu op 40 graden (was 14,
      bijna recht van voren): anders zag je het paneel op de zijmuur alleen als een smal streepje.
- [x] **Tests** — `ArmoryGameTests` (3 nieuw): `pegboard_lock_leaves_a_shadow` (draaien, vastzetten, pakken: de
      schaduw blijft zoals hij hing; een magazijn kan er niet over, een titanium Glock wel, precies daar en weer vast;
      de schaduw wegvegen; een losse Glock laat niets achter; een schaduw valt nooit als item), `pegboard_corner_two_walls`
      (een echte klik met een Pegboard op de zijmuur, gaatje 0 dicht en gaatje 1 open, de hoekstrook, ophangen op het
      tweede paneel, geen tweede paneel over een magazijn heen, afbreken = 2 Pegboards en 2 magazijnen),
      `pegboard_only_from_close_by` (van 3 blokken niet pakken en niet ophangen, vlak ervoor wel). In
      `pegboard_take_turn_and_drop` staat de vreemde nu in het blok voor het paneel. 470 tests groen (twee keer; in
      een ronde ertussen faalde `guard_searches_where_he_lost_you` een keer, de bekende wisselvallige bewakerstest).
- [x] **Bekeken in het spel** — `-Ponly=armory` (nieuw: ar11 tot ar16), met de echte toetsen: sneak + linksklik op de
      rifle gaf het slotje ("Locked: Laser Rifle. When you take it, its shadow keeps the spot.") en brak het paneel niet;
      rechtsklik pakte hem (de schaduw bleef, met de rifle in je hand een blauw kader precies op de schaduw); rechtsklik
      hing hem terug (je hand was daarna leeg). De hoek met de zijmuur (ar14, ar15: een vastgezette rifle en de schaduw
      van een shotgun), en van 3 blokken "Too far away" (ar16, het magazijn bleef in je hand). De flashbang van dichtbij:
      even groot als de smokebom, de frag en de EMP ernaast. Wat ik zo vond: de schaduw was eerst donkergrijs, en die van
      een zwarte Glock leek dan op een echte Glock; nu is elke schaduw even lichtgrijs (de "witte flits" van een geraakt
      wezen, over zwart). En in de showcase bouwde het armory-veldje zich opnieuw op de plek van hoofdstuk 67: alles wat
      daar nog hing viel op de grond en de speler raapte het op (een Ion Blaster in zijn "lege" hand); nu ruimt
      `VisualCheck.buildArmoryAt` die items op (dat gebeurde ook als je functie 120 twee keer afspeelde). Het veldje
      heeft nu een zijmuur met panelen (`ArmoryDisplay`), en de rifle-stap van showcase 120 staat in het blok voor de
      wand. `-Ponly=howtoall,howto:armory`: de animatie per hoofdstukje. Showcase **122** ("Pegboard: lock, shadow,
      corner"), bekeken met `tools/showcase.py --test`. LEESMIJ aangepast.

### 71. Het Pegboard bijgesteld: een grijze omtrek, granaten in hun eigen kleur, 2 blokken (9 november)

Jouw vraag: "Kun je het slotje symbool weghalen, dat ziet er wat raar uit, geef ipv een slotje een gray outline op het
board, de range mag ook verhoogd worden naar 2 blokken ipv 1. Zorg ook dat elke bom er net iets anders uitziet van
outline zodat je makkelijk weet wat waar moet".

**Mijn invulling** (zeg het als je het anders wilt):
- Het **slotje is weg**. Van een vastgezet item wordt de **omtrek grijs op het paneel geverfd**, zoals op een echt
  gereedschapsbord: hangt het er, dan zie je een grijs randje eromheen (0,5 pixel); pak je het, dan blijft de hele
  geverfde vorm (de schaduw), zonder haakjes.
- **Elke soort granaat zijn eigen kleur** (nog steeds grijzig, maar duidelijk anders): EMP roodachtig, frag olijfgroen,
  smokebom donkerder grijs (een gekleurde smokebom voor de helft in zijn rookkleur), flashbang bijna wit. De sticky's, de
  Remote EMP/Frag en de EMP/Frag Trigger krijgen de kleur van hun soort; ze verschillen ook van vorm (een bol, een halve
  bol, een antenne). Al de rest blijft grijs.
- **Bereik: 2 blokken ervan** = het punt waar je klikt ligt hooguit **3 blokken** van je ogen (was 2). De melding zegt nu
  "Too far away: stand closer to the Pegboard (at most 2 blocks away)." In `run/config`, `run-visual/config` en
  `run-showcase/config` stond nog 2.0 (een bestaand configbestand houdt zijn eigen getal): daar staat nu 3.0.

- [x] **De omtrek** — `PegboardRenderer.paint`: hetzelfde model, plat net voor het paneel, 8 keer een randje opgeschoven
      (en voor een schaduw ook in het midden), zodat de vorm overal 0,5 pixel groter is dan het item, ook bij een dunne
      loop. De verf is een rendertype zonder plaatje (`RenderType.textBackground`: alleen kleur en het licht van de
      plek), zodat elke omtrek precies zijn kleur heeft; alleen de Ion Blaster (een plat plaatje met doorzichtige pixels)
      houdt zijn plaatje en wordt lichtgrijs met de "witte flits" van hoofdstuk 70. Het slotje (`models/armory/lock.json`,
      `pegboard_lock.png`, de functie `lock` in `tools/generate_armory_models.py`) is weg.
- [x] **De kleuren** — `PegboardRenderer.paintColour` (grijs, EMP, frag, smoke met zijn rookkleur, flashbang).
- [x] **2 blokken** — `ImtConfig.Armory.PEGBOARD_REACH` = 3; de melding; `pegboard_only_from_close_by` test nu ook dat
      ophangen en pakken van 2 blokken ver lukt (en van 3 nog steeds niet).
- [x] **How it works** — in "Lock a spot" zegt de tekst nu "its outline is painted grey on the board", en de EMP-granaat
      en de Frag Grenade worden vastgezet en gepakt: twee schaduwen in hun eigen kleur ("Every kind of grenade has its own
      colour of outline"). De animatie werd daardoor 92 seconden, en de regel (hoofdstuk 63) is hooguit 90: tien
      tekstballonnen staan nu een halve seconde korter (87 s).
- [x] **De voorbeeldwand** — `ArmoryDisplay`: alle granaten op de zijmuur zijn vastgezet, en de flashbang, de frag en de
      EMP zijn gepakt (hun schaduwen naast elkaar). Showcase 122 vertelt het nieuwe.
- [x] **Tests** — 470, groen (de eerste ronde ving de animatie van 92 seconden; na het inkorten groen).
- [x] **Bekeken in het spel** — `-Ponly=armory`: de vastgezette rifle met een grijs randje eromheen (ar11, ar13), op de
      zijmuur de schaduwen van de flashbang (bijna wit), de frag (groen) en de EMP (roodachtig), en de smokebom en sticky
      EMP die nog hangen met een randje in hun kleur (ar15); van 3 blokken "at most 2 blocks away" (ar16).
      `-Ponly=howtoall,howto:armory`: de twee granaatschaduwen in "Lock a spot". LEESMIJ aangepast.

### 72. Militaire rugzakken, MOLLE-pouches en het lichaam van een speler (9 en 10 november)

Jouw vraag: "Kun je backpacks zoals sophisticated backpacks toevoegen maar dan minder OP. Het moeten zo militaire tacticle
backpacks zijn. Vragen of voorstellen stel ze dan."

**Jouw antwoorden** (vier rondes vragen):
- **Drie maten**: een **Assault Pack** (18 vakjes), een **Patrol Pack** (27) en een **Rucksack** (36), elk een eigen item
  met een eigen model op je rug.
- **Minder OP**: **openen kost tijd** (de rits, stilstaan, en met de rugzak open loop je traag), **geen gekke upgrades**
  (geen magneet, compacting, void of stack-upgrades; geen rugzak of shulkerdoos erin), en een ander kan je rugzak **van
  achteren openen** en eruit stelen (met een meter zoals bij zakkenrollen). Gewicht (trager lopen met een volle rugzak)
  koos je niet.
- **MOLLE-pouches** aan de buitenkant, alle vier die ik voorstelde: een **dump pouch** (raapt je hulzen op), een
  **MOLLE-magazijnpouch / -granaatpouch** (als je heup-pouch leeg is, pakt R of de granaatpouch hieruit), een **IFAK**
  (3 genees-items, snel te gebruiken met het wiel) en een **utility pouch** (4 snelle vakjes). Bereikbaar zonder de rits.
- Een **eigen rugvakje** ("Back" naast je harnas, of het Curios-slot "back"); je rug-sling blijft, het geweer hangt over
  de rugzak.
- **Openen**: de rugzaktoets of het wiel als je hem draagt, rechtsklik in je hand; **sneak + rechtsklik op de grond** zet
  hem neer als blok, en dan opent **iedereen** hem met rechtsklik.
- **Oplopend duur**: de Assault Pack vroeg (leer, touw, ijzer), de Patrol Pack halverwege (plus polymeer en staal), de
  Rucksack laat (plus een titanium frame).
- **Kleur**: "16 kleuren van Minecraft + combineren van deze kleuren zoals leather armor + preset camokleuren die je maakt
  van bruin, licht en donker groen, dit is dan een kleur op zich".
- **Extra's**: op het **Pegboard**, **Raven-soldaten** dragen soms een Assault Pack met buit, een **sorteerknop**.
- **Het lichaam van een speler** (jouw eigen extra): "als je als speler dood gaat laat je ook een body achter zoals de
  soldiers, je kunt dan deze body leeghalen, de rugzak is dan een apart deel van deze body om leeg te halen. Je kunt als
  speler je eigen lichaam weer zoeken en looten of andere mensen kunnen je body looten." Het lichaam blijft **tot het leeg
  is** (een getal in de config als je wil dat het verdwijnt); je moet het **zelf terugvinden** (geen coördinaten, geen
  markering); **sneak + rechtsklik op de rugzak** haalt de hele rugzak eraf (3 s stilstaan); de eigenaar heeft een knop
  **"Alles terug"** (3 s stilstaan; wat niet past blijft in het lichaam).

**Mijn invulling** (zeg het als je het anders wilt):
- De rugzaktoets was eerst **Shift + B**, omdat B de Ore Scan van de bril was. Sinds hoofdstuk 73 (jouw wens) staan de
  snelacties van de bril standaard op geen toets, en is de rugzak gewoon **B**. Aan te passen in Opties > Besturing.
- Het rugvakje staat in je inventaris **rechts naast de twee sling-vakjes** (onder het craftraster).
- De recepten: Assault Pack = 6 leer, een touw, een kist, een ijzerstaaf; Patrol Pack = 2 polymeer, 3 leer, een touw, een
  kist, 2 stalen staven; Rucksack = 2 titanium, 2 polymeer, 2 stalen staven, een touw, een kist, een leer.
- Een nieuwe rugzak is **olijfgroen**. "Geen dozen in dozen" geldt voor alles wat zelf spullen bewaart: een rugzak, een
  shulkerdoos, een bundel en een (volle) Ammo Box. Een holster of pouch mag wel.
- De rits duurt 1,5 s (`zipSeconds`), je mag 0,2 blok bewegen (`zipMoveTolerance`). Je hoort hem, en wie bij je staat ook.
- Met de rugzak open loop je met **W, A, S en D** op 40 % (`openWalkSpeed`); springen, sluipen en sprinten gaan niet door
  (Shift heb je nodig voor shift-klikken). Omdat W dan lopen is, gaat "How it works" (ook W) in dat scherm niet open
  terwijl je loopt.
- Een neergezette rugzak staat alleen op iets stevigs (gaat de vloer weg, dan valt hij eraf als item). Kapot = de rugzak
  valt eraf met alles erin, **ook in creative** (zoals een volle shulkerdoos). Oppakken (sneak + lege hand): op je rug als
  die leeg is, anders in je inventaris.
- Hebben twee mensen dezelfde rugzak open, dan delen ze **één inhoud**, zoals bij een kist. Gaat de rugzak ergens anders
  heen terwijl hij open is (je doet hem af, iemand pakt hem op), dan gaat het scherm dicht: zo komt er niets dubbel. Elke
  rugzak krijgt daarvoor een eigen nummer (`pack_id`) zodra iemand hem opent.
- Sorteren: gelijke stapels samen, en dan op de naam van het item (eerst de spullen van I.M.T, dan die van Minecraft); lege
  vakjes achteraan.
- Op je rug zit de bovenkant van de rugzak op je schouders; van voren zie je de **schouderbanden over je borst** met een
  borstband, en bij de Rucksack ook een **heupband**. Met een borstplaat zit alles een pixel verder naar buiten.
- **MOLLE-pouches** (stap 2): eigen items; je hangt ze aan de rugzak in zijn scherm (een strook rechts). De plekken: Assault
  Pack op elke zijkant, Patrol Pack ook op de voorkant boven zijn voorzak, Rucksack ook op elk van zijn twee voorzakken.
  Zoveel past erin: magazijnpouch 3, granaatpouch 2, dump pouch 2 stapels (128 hulzen), IFAK 3, utility pouch 4 (allemaal
  in `[backpacks]`).
- Eerst je heup-pouch, dan de MOLLE-pouches: R toont ze in die volgorde, trekken en aanvoeren pakken eerst uit je heup, en
  wegsteken (of een uitgeworpen magazijn) gaat eerst in je heup-pouch en pas als die vol is (of je er geen draagt) in een
  MOLLE-pouch.
- De dump pouch raapt elke 0,2 s de hulzen binnen 3 blokken op (van iedereen, niet alleen de jouwe), niet terwijl je een
  pouch open hebt. De IFAK neemt gouden appels (ook de betoverde) en drankjes die je drinkt; uit het wiel gebruik je er een
  **meteen** (geen eettijd), daarna 1 s wachten. Een volle pouch of holster mag niet in een rugzak (een lege wel), anders
  stop je 36 volle utility pouches in een Rucksack.
- Recepten (net als de heup-pouches van leer, touw en ijzerklompjes): magazijnpouch 2 touw + klompje + 3 leer, granaatpouch 2
  klompjes + touw + 3 leer, dump pouch 4 leer + touw, IFAK 2 touw + rode kleurstof + 3 leer, utility pouch 4 leer + klompje
  + touw. Verven zoals de rugzak.
- Het wiel had zoveel knoppen dat ze van je scherm vielen: alle knoppen naast de ring zijn nu **compact** (een vierkantje met
  het item en het getal, in kolommen); wat het is lees je in het midden als je hem aanwijst (zoals de tegels). De genees-items
  uit je IFAK staan ook als knopje, niet op de ring (die zat vol).
- **Van achteren** (stap 3): hetzelfde gebaar als zakkenrollen (sluipen, lege hand, rechtsklik van achteren), maar **op zijn
  rugzak**: de bovenste helft van zijn lijf (onder 55 % van zijn lengte is het zakkenrollen). Kijk je zo naar zijn rugzak,
  dan staat de hint onder je vizier. De rits klinkt zachter (`theftZipVolume` 0,35, je eigen rugzak 0,7). Elke greep vult de
  meter met 0,18 (`theftNoisePerTake`; shift-klik dubbel, een MOLLE-pouch eraf driedubbel), hij zakt 0,1 per seconde
  (`theftNoiseDecay`). Betrapt bij een volle meter of als hij je ziet (hetzelfde "zien" als bij zakkenrollen); de
  afstanden, het oplichten (10 s) en de wachttijd (30 s) zijn die van zakkenrollen. Wat je al aan je muis had, houd je ook
  als je betrapt wordt (je had het al gepakt). Geen sorteerknop in de rugzak van een ander.
- **Het lichaam** (stap 4): het ligt op zijn buik met zijn hoofd naar waar je keek, met je huid (als je online bent; anders
  de standaardhuid bij je naam), je harnas en je rugzak bovenop. Ervaring valt gewoon als bolletjes. Met keepInventory, of
  als je niets bij je had, geen lichaam. In de void: waar je het laatst op de grond stond. Erin leggen kan niet (het is
  geen kist). Op de rugzak klikken = het stuk van zijn middel tot zijn schouders, een halve blok breed; daarbuiten is het
  lichaam (de klikdoos is 1,4 breed en 0,75 hoog). Waar je mikt rekent hij uit met je kijklijn op de hoogte van de rugzak
  (de plek die Minecraft geeft is de bovenkant van de doos, en die ligt dichter bij je dan de rugzak die je ziet). Sneak + rechtsklik op het lichaam sleept het mee, zoals een Raven-soldaat. "Everything back" doet
  eerst het scherm dicht (dan zie je het balkje), en wat niet past blijft liggen. Wat andere mods laten vallen (met Curios ook
  de holster, pouches en rugzak) gaat in de laatste 8 vakjes. Een leeg lichaam verdwijnt met een wolkje.
- **Op het Pegboard** (stap 5): een Assault Pack neemt 5 x 6 gaatjes, een Patrol Pack 6 x 7, een Rucksack 7 x 9, een losse
  MOLLE-pouch 3 x 3. Ze hangen met hun voorkant (de MOLLE-banden) naar je toe, een rugzak met zijn pouches erop, en wat erin
  zit blijft erin. Draaien (sneak + rechtsklik) legt hem op zijn zij, zoals de rest; naar links en naar rechts zijn voor een
  rugzak hetzelfde.
- **Raven-soldaten** (stap 5): 35 % (`ravenPackChance`) krijgt bij het verschijnen een Assault Pack, **antracietgrijs** (zwart
  zag je op hun zwarte uniform amper). Valt hij, dan zit de rugzak met de rest van wat hij laat vallen in zijn lichaam, vol
  buit over de vakjes verspreid: 12 tot 32 patronen 9 mm en 40 % kans op een Glock-magazijn (had hij een lasergeweer: een
  energiecel), brood of gebakken vlees, 15 % kans op een stalen onderdeel van een Glock en 30 % op fakkels.
- **How it works** (stap 5): twee onderwerpen bij de wapens, na de armory: "Military backpacks" (zeven hoofdstukjes, 1:00: de
  drie maten, op je rug, de rits, neerzetten, kleuren, van achteren, het lichaam) en "MOLLE pouches" (zes, 0:40: ophangen en
  elke pouch apart). Je opent ze met W op een rugzak of een pouch.

- [x] **Stap 1: de drie rugzakken** — `backpack/` (eigen registratie: `BackpackRegistry`): `BackpackItem` (de maten,
      `minecraft:container` voor de inhoud, `mayHold`), `BackpackPlace` (op je rug, in je hand, neergezet),
      `BackpackInventory` (de gedeelde inhoud per `pack_id`, sorteren), `BackpackMenu`, `BackpackZip` (de rits; met de
      nep-spelers van de tests geen berichten en een gewoon scherm), `BackpackBlock` + `BackpackBlockEntity` (neergezet,
      oppakken, kapot), `BackpackDyeRecipe` (mengen zoals leer, met een eigen rekensom: Minecrafts eigen mengfunctie
      werkt alleen voor "dyeable", en dan zou ook het verfrecept van leer op de rugzak werken; plus camo), `Backpacks`
      (traag lopen in de server-tick, zodat het ook voor de nep-spelers van de tests werkt; de berichten),
      `BackpackActionPayload` en `BackpackZipPayload`. `Gear`: het vakje `BACK` (index 5 in de attachment, een oude wereld
      vult zichzelf aan), `back`/`setBack`, klikken; `CuriosGear`: het slot "back". Config `[backpacks]`. In jouw spel
      (`client/backpack/`): `BackpackScreen` (de sorteerknop, doorlopen zoals het wiel), `BackpackLayer` (op je rug, de
      banden; het geweer van je rug-sling schuift naar achteren, `GearLayer`), `BackpackRenderer` (neergezet),
      `BackpackClient` (de kleur, camo, de rugzaktoets, het balkje van de rits). `GearSlotsOverlay` (het rugvakje),
      `GearWheelScreen` (de knop Backpack), `HowToHover`. Modellen en plaatjes uit `tools/generate_backpack_models.py`
      (ook de banden: `models/backpack/straps.json`, `hip_belt.json`), het ritsgeluid uit `generate_sounds.py`
      (`backpack_zip`). Zes nieuwe tests (`BackpackGameTests`): de rits (halverwege nog dicht, wie beweegt stopt, daarna
      open, traag lopen, weer gewoon na het sluiten), neerzetten en delen en kapot en oppakken, geen dozen in dozen (ook met
      shift-klik, en het vakje van je hand op slot), verven en camo (en alleen dit recept past), sorteren, het rugvakje (en
      het scherm gaat dicht als de rugzak weg is). Bekeken in het spel met `-Ponly=backpack` (pk01 tot pk20: neergezet in
      zes kleuren, het rugvakje, het creative-menu, van achteren en van voren, met een geweer erover, de rits, het scherm,
      sorteren, in je hand, het wiel) en `-Ponly=backpackwalk` (met echte toetsen: 4,27 blokken in 1 s met de rugzak dicht,
      1,71 met hem open = 40 %). Wat ik zo vond: van voren zag je eerst niets van de rugzak (nu de banden over je borst),
      in eerste persoon was hij veel te groot (nu op 0,3), de MOLLE-banden vielen weg in de kleur (nu donkerder), en de
      testserver crashte op een bericht naar een nep-speler (nu `ModNetwork.sendTo`). Showcase 123 ("Military backpacks").
- [x] **Stap 2: MOLLE-pouches** — `backpack/MolleItem` (de vijf soorten, wat erin past; de inhoud in GEAR_CONTENTS, zoals de
      heup-pouches), `Molle` (de pouches op je rug, `magazines`/`grenades`: je heup-pouch en de MOLLE-pouches als één
      rijtje, `store`, openen zonder rits, de dump pouch, de IFAK; alles via `setWorn`, en staat de rugzak open, dan via die
      open inhoud), `MolleMenu` (het scherm van een pouch; de pouch op je rug krijgt een nummer, zodat het scherm dichtgaat
      als iemand hem eraf haalt). `BackpackInventory` en `BackpackMenu`: de MOLLE-plekken achter de vakjes (component
      `imttech:molle`). `Gear` (trekken, aanvoeren, herladen, uitwerpen en wegsteken met de MOLLE-pouches erbij),
      `GunGearClient`, `MagPickerScreen`, `GunHud` en `LaserGuns` (de cellen) lezen het rijtje. In jouw spel: `MolleScreen`,
      de strook in `BackpackScreen` (een klik daar is geen klik ernaast), `BackpackLayer.renderPouches` (op je rug en
      neergezet), kleur en camo, en in `GearWheelScreen` een knop per pouch en per genees-item (de knoppen nu compact).
      Modellen in `generate_backpack_models.py` (de pouches kijken naar voren met hun achterkant in het midden; de IFAK met
      een rood kruis). Zes nieuwe tests: een pouch aan de rugzak hangen (shift-klik, een volle niet in de rugzak), openen
      zonder rits, R uit de MOLLE-magazijnpouch (en uitwerpen erin), trekken en aanvoeren uit de MOLLE-granaatpouch, de dump
      pouch (3 hulzen bij je voeten wel, een op 9 blokken niet), de IFAK (een gouden appel, en meteen nog eens mag niet).
      Bekeken met `-Ponly=backpack` (pk21 tot pk29: de strook, een open IFAK, de pouches op elke maat, neergezet, het wiel,
      R). Wat ik zo vond: de knoppen van het wiel vielen van je scherm en de IFAK-tegels lagen over de geweren (nu compact en
      als knopjes), de derde plek van de Patrol Pack verdween tegen zijn voorzak (nu erboven), en in het scherm van een pouch
      stond "or its own key" (een MOLLE-pouch heeft geen eigen toets). Showcase 124 ("MOLLE pouches").
- [x] **Stap 3: van achteren openen en stelen** — `backpack/BackpackTheft` (beginnen via `EntityInteractSpecific`: die
      geeft ook waar je op zijn lijf klikte; de meter, betrapt, de wachttijd), `BackpackPlace.Theft` (zijn rugzak, dezelfde
      als `Worn`: kijkt hij zelf ook, dan delen ze de inhoud; dicht als hij wegloopt of je niet meer achter hem staat),
      `BackpackTheftPayload` (de meter naar de dief), `BackpackMenu.theft` (elke greep = `BackpackTheft.noise`). In jouw
      spel: de hint onder je vizier, de meter links in `BackpackScreen`, en `BackpackClient` houdt de gewone klik tegen als
      je op een rugzak klikt (de klik met de plek op zijn lijf ging al naar de server; anders begon ook het zakkenrollen).
      Config `stealAllowed`, `theftNoisePerTake`, `theftNoiseDecay`, `theftZipVolume`. Vier nieuwe tests (eigen batch
      `backpacks_theft`, want ze zetten de rits even op 0): van voren niet, van achteren met de echte klik pas na de rits,
      de diamanten zijn weg uit zijn rugzak; te veel grijpen = betrapt (oplichten, wachttijd); omdraaien = betrapt;
      weglopen = dicht zonder betrapt. Bekeken met `-Ponly=backpack` (pk30 tot pk35: de hint achter Lotte, de rits, haar
      scherm met de meter, de meter na drie grepen, ze draait zich om). Showcase 125 ("Opening a backpack from behind").
- [x] **Stap 4: het lichaam van een speler** — `body/PlayerBodyEntity` (54 vakjes op vaste plekken, de rugzak apart als
      gesynchroniseerd item, de klik op de rugzak of het lichaam via `interactAt`, slepen, leeg = weg, opslaan), `PlayerBodies`
      (doodgaan als allerlaatste `LivingDeathEvent`, wat andere mods laten vallen via `LivingDropsEvent`, de rugzak eraf
      halen, "Everything back"), `BodyMenu` (doorzoeken, alleen eruit). `Gear.onDeath` laat niets meer vallen als het
      lichaam aan staat. `BackpackPlace.Body`; de rits kan nu ook iets anders dat even duurt (`BackpackZip.startTimed`, met
      een eigen tekst onder het balkje). In jouw spel: `PlayerBodyRenderer` (het mensmodel breed of smal met de huid van de
      eigenaar, op zijn buik zoals zwemmen, met het harnas en de rugzak), `BodyScreen` (met de knop), de hints. Config
      `playerBodies`, `bodyMinutes`, `bodyTakePackSeconds`, `bodyRestoreSeconds`. Vijf nieuwe tests (batch `bodies`):
      doodgaan (alles op zijn plek, de rugzak apart, de vloek van verdwijnen weg, niets op de grond), keepInventory (geen
      lichaam), "Everything back" (en wat bezet is blijft liggen: de testspeler draagt de bril, dus zijn helm blijft), de
      rugzak openritsen en eraf halen (en de klik op zijn benen is het lichaam), doorzoeken en een leeg lichaam verdwijnt.
      Bekeken met `-Ponly=backpack` (pk36 tot pk42: het lichaam van Lotte op haar buik met haar rugzak, de hints, haar
      scherm, je eigen lichaam met de knop, "Everything is back (7 things)"). Wat ik zo vond: de klikdoos was te laag (de
      rugzak stak erboven uit, dus geen hint), en in de eerste test ging de helm niet terug: de testspeler draagt de bril,
      precies de regel "wat niet past blijft liggen" (de test was fout), en met de hogere doos zei de hint op de rugzak
      toch "search" (de plek van Minecraft is de bovenkant van de doos; nu met je kijklijn). Onderweg: een raiddrone koos
      ook een **dode** speler als doelwit (iemand in zijn doodscherm) en bleef daarop wachten; nu niet meer
      (`RaidDroneEntity`). De test `raid_starts_outside_a_base` viel een paar keer om met "hij heeft geen doelwit"; hij zegt
      nu ook op wie de drone wel mikt, of dat hij gestoord is door een EMP (mijn vermoeden: een EMP van een test ernaast; de
      granaattest van de MOLLE-pouch ruimt zijn gegooide EMP nu meteen op). Showcase 126 ("Player bodies").
- [x] **Stap 5: Pegboard, Raven-soldaten, How it works** — `Pegboard.size` (de maten van de rugzakken en pouches) en
      `PegboardRenderer` (een rugzak of pouch met zijn voorkant naar je toe, een rugzak met zijn pouches erop via
      `BackpackLayer.renderPouches`): mijn enige regels in die twee bestanden; het werk van hoofdstuk 70 en 71 van de andere
      sessie zit er ook in, maar niet in mijn commit. `backpack/RavenPacks` (de rugzak en de buit), `RavenSoldierEntity` (de
      rugzak als gesynchroniseerd item, bewaard als "Pack"; de kans bij het verschijnen; vol buit in `dropCustomDeathLoot`,
      zodat hij met de rest in zijn lichaam komt), `RavenSoldierRenderer` (een laag met de rugzak, zoals bij een speler). How
      it works: `howto/scenes/BackpackScenes` (de twee animaties), `HowToTopics`, `SceneLife` (een speler in een animatie
      draagt nu ook een rugzak: de zesde plek in `worn`), `HowToGameTests` (zes plekken uitrusting mag nu). Twee nieuwe tests:
      een Raven-soldaat met een rugzak valt (de rugzak zit in zijn lichaam, met minstens twee stapels buit), en rugzakken en
      pouches op het Pegboard (de maten, en een Assault Pack met brood erin hangt). Bekeken met `-Ponly=backpack` (pk43 tot
      pk46: de wand, de soldaat van achteren, zijn lichaam met de rugzak, de buit in je hand) en
      `-Ponly=howtoall,howto:backpacks` en `howto:molle_pouches` (elk hoofdstukje). Wat ik zo vond: de zwarte rugzak zag je op
      het zwarte uniform van een Raven-soldaat amper (nu antracietgrijs), en in de animatie lag het lichaam half achter het
      tekstvak (nu gedraaid, met een olijfgroene rugzak). Showcase 127 ("Backpacks on the pegboard, Raven packs").

### 73. Snelacties van de bril zonder standaardtoets, de rugzak op B (10 november)

Jouw wens: "Verander dat al de quick dingen zoals ore scan, satelliet... standaard geen instelling hebben, zo heb je meer
knoppen voor andere dingen; zo kan rugzak standaard op B staan."

- [x] **Geen standaardtoets meer voor de snelacties** — `ImtKeys`: nachtzicht (was N), ore scan (B), dreigingsscan (H),
      markeren (R), zwerm (J), één drone (L), de drie satellietaanvallen (K, P, C), drone-camera (Y), navigatie (M), ping
      (U), comms (O), spionagedrone (.), stand down (,), redstone-scherm ('), hacken (;), laser cannon (I) en mini-raketten
      (X) staan standaard op geen toets. Ze staan allemaal in het G-menu en je kunt ze nog steeds zelf op een toets zetten
      (Opties > Besturing > I.M.T). Hun toets houden: het G-menu, zoom (V) en de spraaktoets (Z) (vasthouden kan niet uit
      een menu), het wiel (linker Alt), de skilltree (F6), de geweertoetsen R, U en H (alleen met een geweer in je hand) en
      How it works (W in een scherm).
- [x] **De rugzak op B** — `BackpackClient.OPEN_BACKPACK`: gewoon B (was Shift + B).
- [x] **Markeren in het G-menu** — markeren had alleen een toets, dus nu staat **Mark target** vooraan in G > Drones:
      het markeert wat in je vizier staat (het menu laat je blijven kijken waar je keek); eronder staat de naam van wat je
      aankijkt, of "look at someone".
- [x] **Je oude toetsen gaan één keer om** — Minecraft onthoudt elke toets in options.txt, dus wie de mod al had hield
      anders B = ore scan en Shift + B voor de rugzak. `ImtKeys.migrateOnce` (bij de eerste tick) zet elke snelactie die
      nog op zijn **oude** letter staat op geen toets, en de rugzak van Shift + B naar B; zelf gekozen toetsen blijven. Daarna
      staat `keyLayout = 2` in `imttech-client.toml` en gebeurt het nooit meer.
- [x] **Teksten** — de drone-camera zei onderaan "Y: return"; nu de toets als je er een koos, anders "G > Recon: return"
      (`DroneFeedWidget`). How it works: de laser cannon, de mini-raketten, de drones en de rugzak noemen het G-menu (of
      [B]) in plaats van de oude letters. LEESMIJ: de toetsentabel (hoofdstuk 3) met een tabel "snelactie, waar in het
      G-menu, vroeger", en de oude letters elders eruit. De rondleidingen (`VisualCheck` dreigingsscan, `ShowcaseFeatures`
      raketten) drukten de oude toets in; die sturen nu de actie zelf (een lege toets indrukken drukt alle lege toetsen in).

### 74. De Glock in de stijl van TACZ: een echt model, een gedetailleerd magazijn en echte geluiden (10 november)

Jouw wens: "Kijk eens naar het glock model, dit is nog niet super mooi, kijk eens hoe dat bij tacz is, die stijl van
realisme en ook de sounds wil ik. Kopieer niet letterlijk van daar maar haal daar inspiratie. Ook het glock mag is niet
super gedetailleerd, doe dat in dezelfde stijl als tacz mags (ze hebben daar geen items voor, enkel animatie)."

Wat ik bij TACZ heb bekeken (in je CurseForge-instance "Guns & Tanks", alleen gemeten en bekeken, niets gekopieerd):
hun Glock 17 is 264 kleine doosjes (142 schuin), een texture van 8 texels per pixel met vlakke donkergrijze tinten en
lichtere randjes, het magazijn 12 doosjes plus de patronen. Hun schot is een cluster harde knallen in de eerste 80 ms
met veel dreun en een galm van een seconde; hun mechanische geluiden zijn helder (2 tot 16 kHz) en kort.

- [x] **Een echt 3D-model** — Minecrafts eigen json-modellen laten alleen hoeken van 22,5 en 45 graden toe, om één as.
      Daarom zijn de Glock, het magazijn en de munitie nu **OBJ-modellen** (NeoForge laadt die in en bakt ze tot een
      gewoon item-model, dus de holster, het Pegboard, de Gunsmith Table en How it works blijven werken). De omtrek is
      nagemeten van een foto van een echte Glock 19 Gen 3 (Wikimedia Commons, "Glock 19 (transparent background)"), de
      breedtes komen uit de maten van Glock. Het frame bestaat uit vijf stukken met hun eigen breedte (het lijf met de
      beavertail, de greep met drie vingergroeven en de duimsteun, de trekkerbeugel, de stofkap met de rail, zijn groef
      en de dwarsgleuf), met afgeronde randen. De slede: een afgeronde neus, afgeschuinde bovenranden, de uitwerpopening
      (rechts en bovenop, met het blok van de loop erin en een donker randje), zeven echte groeven achteraan, de
      extractor, de korrel met een wit stipje en de keep met een witte U (allebei gloeiend). Verder: een ronde loop met een
      boring, de terugslagveer op zijn stang (het puntje zie je vooraan), de slagpin met zijn veer, een gebogen trekker
      met het veiligheidslipje, de slide stop (links), de slide lock (beide kanten) in zijn ovale uitsparing, drie stalen
      pinnen en de magazijnknop (links; rechts zie je zijn platte uiteinde).
- [x] **De texture schildert het script zelf** — 16 texels per pixel (twee keer TACZ), met het korreltje van de greep,
      dwarse groefjes op de voor- en achterkant, lichtere afgesleten randen, en tekst die je kunt lezen: GLOCK 19 /
      AUSTRIA 9x19 links op de slede, MADE IN AUSTRIA / GLOCK, INC., SMYRNA, GA. en de patenten rechts op het frame, 9x19
      op het blok van de loop. Elk onderdeel heeft drie plaatjes met dezelfde indeling (polymeer, staal, titanium).
- [x] **Het magazijn** — een polymeer lijf dat bovenaan smaller wordt (twee rijen patronen worden er één), een stalen
      rand met lipjes, de bodemplaat met een lip vooraan, gaatjes met 15 / 12 / 10 / 8 / 5 achterop, de inkeping voor de
      magazijnknop vooraan, en bovenin een patroon (messing huls met slaghoedje, koperen kogel). **Leeg ziet het er leeg
      uit**: de opvolger in plaats van een patroon, in je inventaris, in je hand, in je magazijnpouch en als het uit het
      pistool valt (`FirearmModels.magazine(stack)`, de eigenschap `imttech:empty`).
- [x] **De munitie op ware grootte** — een patroon is nu 29,7 mm naast het pistool (vroeger bijna twee keer te groot), met
      de rand, de groef voor de extractor en een ronde kogelpunt; de huls is van binnen donker. Een huls op de grond
      tekent `CasingRenderer` 0,85 keer zo groot (was 0,5), zodat je hem even goed terugvindt als vroeger.
- [x] **Een fout in het oude model** — de greep helde de verkeerde kant op: een json-doosje dat 22,5 graden om x draait
      zet zijn onderkant naar de loop toe, niet naar achteren. Het nieuwe model helt naar achteren zoals op de foto, en
      het magazijn ook; `FirearmRig.magazineUpright()` zet het in je hand en in de pouch rechtop (ze rekenden elk met de
      oude, verkeerde hoek).
- [x] **In je hand** — `FirearmRig` heeft de maten van het nieuwe model (greep, steun, vizierlijn: de witte stip van de
      korrel staat in de U van de keep, de loop, de uitwerpopening, de dozen voor de Gunsmith Table). Bij het magazijn
      eruit gaat je linkerhand onder de greep (vroeger naar de knop, en dan stond je arm voor het pistool), de slede pak je
      bij de groeven, bekijken (H) draait de rechterkant naar je toe (de uitwerpopening, MADE IN AUSTRIA), en de patroon
      die je in een magazijn duwt ligt met de punt naar voren.
- [x] **Echte geluiden** — `tools/generate_sounds.py` bouwt ze na zoals ze ontstaan: tikken op metaal en polymeer (een
      ruisstootje dat de trillingen van het onderdeel aanslaat), schuiven met kleine haakjes, en galm met vroege echo's.
      Het schot: een krak, een klap over het hele spectrum, een dreun, de slede die tegen zijn aanslag en weer naar voren
      slaat, en de galm; de energieverdeling ligt dicht bij die van TACZ (46 / 12 / 8 / 17 / 16 % tegen 43 / 11 / 13 / 19 /
      14 % in 20-150 / 150-500 / 500-2000 / 2000-6000 / 6000+ Hz, en 40 dB zachter na 0,25 s tegen 0,23 s). **Drie varianten**
      van het schot (Minecraft kiest er telkens één), de slede (grijpen, schuren, aanslag, de klap naar voren), een eigen
      **slide release** (de slide stop: alleen de klap naar voren), magazijn eruit en erin, een patroon laden, droog klikken,
      tap-rack, de holster, en **drie varianten** van een huls die stuitert. Hogere OGG-kwaliteit voor deze geluiden.
- [x] **Een schot van ver** — vroeger hoorde je een Glock maar tot 16 blokken. Nu, zoals de "3p"-geluiden van TACZ: tot
      32 blokken het schot zelf en vanaf 12 tot 128 blokken een dof schot van ver met late echo's (ertussen lopen ze in
      elkaar over; `Firearms.shotSound`, ook voor de Raven-soldaten met een Glock). Eén nieuwe test.
- [x] **Gereedschap** — `tools/gunmesh.py` (de bouwdoos: platen uit een omtrek met afgeronde randen, buizen,
      omwentelingslichamen, tekstplaatjes, de texture-indeling, OBJ schrijven), `tools/glock19_model.py` (de Glock zelf) en
      `tools/preview_obj.py` (bekijk een OBJ-model zonder het spel te starten). `python tools/generate_gun_models.py`
      maakt alles opnieuw.
- [ ] **Nog door jou te beluisteren** — ik kan geen geluid horen: ik heb de geluiden gemeten en als spectrogram
      vergeleken met die van TACZ (de verhoudingen kloppen), maar of ze echt goed klinken moet jij zeggen. Zeg welk geluid
      te dof, te schel, te luid of te lang is, dan stel ik het bij.

### 75. Raiddrones minder vervelend: kleiner bereik, en weg na je dood (10 november)

Jouw playtest (met anderen): "de raiddrones zijn heel irritant, ze kunnen van een te grote range jou zien en aanvallen +
als je niks meer hebt is je spul terug krijgen moeilijk. Zorg dat ze na je 1x te killen verdwijnen."

**Jouw antwoord:** het bereik ook kleiner: **schieten tot 12 blokken** (was 18), **spelers opmerken tot 24** (was 48).
En daarna: "maak ook een gamerule die deze raiddrones aan en uit laat togglen".

**Mijn invulling** (zeg het als je het anders wilt):
- **Elke dood telt**, niet alleen een laserschot van een drone: val je tijdens een raid van een klif of pakt een zombie
  je, dan zit je net zo goed zonder spullen. Weg gaan: elke raiddrone die **voor jou kwam** (in elke dimensie) en elke
  die **op dat moment op jou schoot** (ook een die voor een ander kwam). Een drone die voor een andere speler kwam en op
  die ander mikte, blijft voor die ander.
- **Wegvliegen, niet wegflitsen**: de duik waarmee ze komen, maar achterstevoren (`SkyDive.takeoff`): rustig optrekken,
  schuin omhoog weg van waar je stierf, steeds sneller de lucht in, met het geluid van een drone die je terugroept en een
  rookspoor; boven (of tegen een plafond in een grot, of na 10 seconden) een wolkje en weg. **Geen buit en geen
  ervaring**: die krijg je alleen als je er een neerschiet, ook tijdens het wegvliegen. Ligt er een plat door een EMP, dan
  vertrekt hij zodra hij weer kan. Een drone die wegvloog en in een chunk lag die uitlaadde, vliegt na het laden verder
  weg (`Leaving` in zijn opslag).
- **In de chat** (die zie je ook op het doodscherm; de melding van de bril niet, want die ligt in je lichaam): "The raid
  drones got what they came for and are pulling out. Go get your things."
- **Nieuwe namen in de config**: `shootRange` (was `attackRange`) en `spotRange` (was `findRange`) onder `[raids]`.
  Een configbestand houdt zijn oude getallen, ook als de standaard in de code verandert; met een nieuwe naam krijgt elk
  bestaand bestand (ook dat van je CurseForge-instance) meteen 12 en 24. De oude regels laat NeoForge vallen. Wie het
  bereik zelf had veranderd, moet dat opnieuw doen.
- Ze merken je tot 24 blokken nog steeds **ook achter een muur** op, en wie ze **kwamen halen** blijven ze zoeken (tot
  600 blokken, hoofdstuk 54): het opmerken gaat over andere spelers in de buurt. Ze cirkelen op zo'n 7 blokken om je
  heen, dus 12 blokken schieten is genoeg om je te raken terwijl ze cirkelen.
- **De gamerule** heet `imttechRaidDrones` (standaard `true`, naast `imttechRaidGriefing`). Uit: geen golf meer (niet
  vanzelf, niet bij de toren, niet bij een wrak, ook `ImtRaids.startRaid` niet), en elke raiddrone die al vliegt, stijgt
  op en vertrekt zoals na je dood. Weer aan: de klok begint opnieuw (zoals na een herstart), anders kwam er meteen een golf
  die al lang "te laat" was. `rules.raidsEnabled` in de config blijft bestaan (voor elke wereld tegelijk); allebei moeten
  aan staan.

- [x] **Weg na je dood** — `ImtRaids.onPlayerDeath` (als allerlaatste op `LivingDeathEvent`, net als het lichaam: redt
      een andere mod je nog, dan blijven ze), `RaidDroneEntity.leave` en `flyAway`, `SkyDive.takeoff`. Eén nieuwe test
      (`raid_drones_leave_after_killing_you`, een eigen groep): de drone die voor jou kwam en een die op jou schoot vliegen
      op en zijn weg, zonder ervaring; de drone van een andere speler jaagt nog op die ander.
- [x] **Kleiner bereik** — `ImtConfig.Raids.SHOOT_RANGE` 12 en `SPOT_RANGE` 24; ook in `run/config` en `run-visual/config`.
- [x] **Gamerule `imttechRaidDrones`** — `ModGameRules.RAID_DRONES` en `raidsOn`; de drone kijkt elke tick. Eén nieuwe test
      (`raid_drones_gamerule_off`, een eigen groep: met de regel uit vertrekt een drone die al vloog en komt er geen golf).
- [x] **Bekeken** met `-Ponly=raidleave` (rl01 tot rl05): drie drones hangen voor je, trekken rustig op, stijgen schuin
      weg met een rookspoor, en zijn dan weg ("0 van de 3 raiddrones nog in de wereld" in de log). De rondleiding wacht
      eerst tot de verse plek echt draait: anders stonden de drones stil en sloot het spel af terwijl er nog chunks gemaakt
      werden, en dan bleef het hangen op "Saving worlds" (dat was de rondleiding, niet de drones). De showcase "Raid
      drones" eindigt nu met het wegvliegen (`ImtRaids.callOff`, zonder echt dood te gaan; geen nieuw nummer).
- [x] LEESMIJ (Raiddrones) bijgewerkt.

### 76. Crushed Gunpowder: één buskruit is tien patronen (10 november)

Jouw wens: "Voeg een recepie toe via de crusher: crushed gunpowder, van 1 gunpowder krijg je 10, voor kogels te maken
heb je nu crushed gunpowder nodig dat betekend dus dat je met 1 gunpowder 10 9mm bullets kunt maken."

**Mijn invulling** (zeg het als je het anders wilt):
- De **Crusher** neemt nu ook **buskruit** aan (met de hand, shift-klik, of een trechter van boven of opzij) en doet er
  even lang over als over een gebarsten blok (5 seconden, 40 FE per tick). Eruit komen **10 Crushed Gunpowder**.
  Elk buskruit telt, ook dat van een andere mod (de tag `c:gunpowders`, zoals in de recepten van de granaten). Het getal
  staat in de config: `[guns] crushedPerGunpowder = 10` (1 tot 64, meer past niet op één stapel).
- De **Reloading Bench** neemt in het derde vakje alleen nog **Crushed Gunpowder**, één per patroon; gewoon buskruit past
  daar niet meer. Lag er in een bestaande wereld nog buskruit in dat vakje, dan haal je het er gewoon uit (en door de
  Crusher).
- Crushed Gunpowder gaat niet nog eens door de Crusher, en de Compactor maakt er geen buskruit van terug.
- Alleen de **patronen** veranderen: granaten, flashbangs, rookgranaten, raketmunitie, Robot Ammo en het raketpakket
  blijven gewoon buskruit gebruiken.
- Het **icoontje**: een glad, puntig hoopje donkergrijs poeder met een paar glinsteringen, zodat je het niet verwart met
  het grove buskruit van Minecraft of het ronde, lichte poeder van een blok. De tooltip: "One per 9mm round on the
  Reloading Bench." en "A Crusher makes 10 from one gunpowder."
- **De Crusher**: in het lege vakje staat om de beurt een gebarsten blok en buskruit (vaag), en onderaan "Cracked blocks
  or gunpowder." (de oude zin met "or gunpowder" erbij was te lang voor het scherm). Terwijl hij buskruit maalt, stuiven
  er grijze korrels bovenuit, en als het klaar is knerpt het zacht, zoals zand.
- **De Reloading Bench**: de tooltip van het lege derde vakje zegt "Crushed Gunpowder (Crusher: 1 gunpowder = 10)", en
  de regel onderaan "Put a casing, a bullet and crushed gunpowder in." Die past niet op één regel, dus nu op twee (een
  te lange regel gaat vanzelf naar een tweede, nooit meer over de rand).
- Geen nieuwe showcase: in showcase 113 ("Glock 19: parts, magazines and casings") ligt op de Reloading Bench nu
  Crushed Gunpowder.

- [x] **Crushed Gunpowder** — `guns/CrushedGunpowderItem` (de tooltip, en `perGunpowder()`: het getal uit de config),
      `ModItems.CRUSHED_GUNPOWDER`, in het creative-tabblad na de kogelpunten. Icoon: `crushed_gunpowder_item` in
      `tools/generate_textures.py` (alleen die functie gedraaid).
- [x] **De Crusher** — `CrackMachineBlock.Kind.convert` (buskruit), `doneSound(ItemStack)` en `examples()`;
      `CrackMachineBlockEntity` (stof en geluid van buskruit); `CrackMachineScreen` (de voorbeelden om de beurt).
- [x] **De Reloading Bench** — `ReloadingBenchBlockEntity.fits`; `ReloadingBenchScreen` (de tooltip, en de regel onderaan
      op twee regels als hij te lang is).
- [x] **Tests** — één nieuwe (`crusher_crushes_gunpowder`: twee buskruit erin, na vijf seconden 10 Crushed Gunpowder eruit
      en nog één buskruit erin; daarmee pers je precies 10 patronen; Crushed Gunpowder neemt de Crusher niet aan,
      buskruit de Compactor niet). `reloading_bench_presses_rounds` perst nu met Crushed Gunpowder, en gewoon buskruit
      past niet meer in het derde vakje.
- [x] **How it works** (Reloading Bench) — "A round" toont Crushed Gunpowder, en een nieuw hoofdstukje "Crushed
      Gunpowder": een Crusher naast een warmtegenerator maalt een buskruit, en er verschijnen er 10 op een rij voor de
      machines. Daarna gaan de Crusher en de generator weer weg, zodat de hoofdstukjes over de bank er zijn zoals
      voorheen. Het plaatje van de bank is opnieuw gemaakt (`-Ponly=howtopics`; de andere plaatjes die dat ook opnieuw
      maakte, heb ik teruggezet). W op Crushed Gunpowder opent deze animatie.
- [x] **Bekeken** met `-Ponly=repair` (r05: buskruit erin, Crushed Gunpowder eruit, en de tooltip; r06: leeg, met het
      voorbeeld om de beurt), `-Ponly=glock` (gl19, en gl19b: de lege bank, de regel op twee regels en de tooltip van het
      derde vakje) en `-Ponly=howtoall,howto:reloading`. Wat ik zo vond: het rijtje van 10 zag je eerst amper (klein en
      donker, eerst op de grond en daarna tegen de donkere achtergrond; nu groter, boven het platform voor de machines),
      en in het laatste hoofdstukje stond de tekst half over de Crusher (nu gaat die eerst weg). In r05 kwam de tooltip er
      de eerste keer niet op: tussen de muis verzetten en de foto zat een seconde, en dan zet Minecraft de muis terug
      waar de echte muis staat zodra die beweegt (mijn vermoeden). Nu neemt hij de foto vlak erna, en toen stond hij er wel.
- [x] LEESMIJ (Barsten herstellen, Patronen en de Reloading Bench) bijgewerkt.

### 77. De licentie wordt All Rights Reserved, en alles op GitHub (11 november)

Jouw vraag: "Kun je alles naar github pushen en de licentie veranderen naar all right reserved dankjewel".

- [x] **De licentie** — `LICENSE` is nu een All Rights Reserved-tekst: copyright tunnelsensor, en niemand mag de mod
      (code, textures, modellen, geluiden, gebouwen, teksten) kopiëren, aanpassen of verspreiden zonder jouw schriftelijke
      toestemming. Onderaan staat nog steeds dat de bibliotheken van anderen in de jar hun eigen MIT- en Apache-licentie
      houden (`THIRD-PARTY-LICENSES.txt` bleef hetzelfde). `mod_license=All Rights Reserved` in `gradle.properties` (zo
      staat het in de modlijst van Minecraft), en `publish/description.md`, `publish/HOE-PUBLICEREN.md` (stap 7),
      LEESMIJ (hoofdstuk 9) en de uitleg in `build.gradle` zeggen het nu ook. De oude jar in `publish/release/` (2.0.0)
      heeft nog de MIT-tekst; een nieuwe `gradlew build` heeft de nieuwe.
- [x] **Op GitHub** — eerst gecommit wat nog in de werkmap lag: het Pegboard-werk van hoofdstuk 70 en 71 en jouw eigen
      logo (`publish/I.M.T technology logo.png`, in de plaats van het gemaakte `avatar-400.png`). Daarna alles naar
      GitHub gepusht (`origin/master`; de vorige push was op 17 oktober).
- [x] **Tests** — vooraf twee keer alle 497 gedraaid: telkens 496 groen en één van de bekende wisselvallige tests rood
      (de eerste keer `guard_searches_where_he_lost_you`, de tweede keer `raven_soldier_shoots`; elk was groen in de
      andere ronde).

### 78. Onderdelen alleen op de Gunsmith Table, en geen twee recepten hetzelfde (11 november)

Jouw wens: "Zorg dat je gun onderdelen niet meer in de crafting table kunt maken maar enkel in de gun smit table, zorg dat
geen enkel recepie het zelfde is, nu zijn de laser shotgun frame en laser sniper frame het zelfde."

Wat ik vond: niet alleen die twee frames. Bij de rifle, de sniper en de shotgun waren ook de receiver, de emitter en de
capacitor hetzelfde, en de trigger was bij alle vier de lasergeweren hetzelfde. Minecraft kiest dan één recept, dus de
andere kon je nooit maken.

Jouw antwoorden op mijn drie vragen:
- **Alles op de Gunsmith Table**: de onderdelen van de Glock én die van de lasergeweren (titanium en netherite). De Laser
  Workbench zet alleen nog lasergeweren in elkaar.
- **Een tabblad met tegels** (mijn voorstel): een tweede tabblad "Make parts", met een tegel per onderdeel; rechts wat het
  kost (wat je hebt en wat nodig is, groen of rood) en een knop Make. Geen craftraster (de tafel heeft nog altijd geen
  vakjes, jouw wens van hoofdstuk 52).
- **De Gun Sling en het Glock 19 Magazine ook**: alleen nog op de Gunsmith Table.

**Mijn invulling** (zeg het als je het anders wilt):
- **De kosten.** De Glock blijft wat hij was, behalve de veren: die kostten evenveel als de loop (2 materiaal), dus nu 1
  materiaal en 3 ijzerklompjes (de slagpin is 1 en een ijzerklompje). Het laserpistool blijft hetzelfde, alleen zijn
  trigger kost 1 titanium minder (die was gelijk aan die van de rifle). De rifle, de sniper en de shotgun hielden hun
  prijs, en elk onderdeel heeft nu het **kenmerk van zijn geweer** erbij: **2 Nether Quartz** (de rifle, snel vuur), **2
  Amethyst Shards** (de sniper, precisie) of **2 Glowstone Dust** (de shotgun, een waaier van licht). Netherite blijft het
  titanium onderdeel plus 2 Netherite Ingots. Het magazijn (7 staal en Glock-veren) en de sling (3 leer en 2 touw) ook.
  Alle 64 recepten staan in LEESMIJ.
- **Een eigen soort recept**, `imttech:gunsmithing`: een lijstje ingrediënten met elk een aantal, in dezelfde
  JSON-bestanden als eerst (`data/imttech/recipe`, dezelfde namen). De crafting table kent ze niet. Ze gaan naar de
  spelers zoals alle recepten, zodat het scherm ze toont; met een datapack kun je ze veranderen. Een recept dat bij geen
  wapen hoort (van een datapack), staat onder een extra wapen "Other".
- **Het materiaal** komt uit je inventaris en je tweede hand (niet wat je aanhebt), zoals bij de harnas-werkbank. Ook in
  creative betaal je, zoals aan de crafting table. Heb je meer onderdelen die passen (twee titanium emitters voor een
  netherite emitter), dan gaat het **meest versleten** weg.
- **De lijst links**: de Glock 19 en de vier lasergeweren, elk met hoeveel recepten je nu kunt maken ("3 to make"); scrollen
  op die lijst gaat naar het volgende wapen. **In het midden** een rij per onderdeel en een kolom per materiaal (de Glock:
  polymeer, staal, titanium, en het magazijn onderaan; een lasergeweer: titanium en netherite, en bij de rifle, sniper en
  shotgun de sling onderaan, bij alle drie hetzelfde recept). Groene rand = dat kun je nu maken, donker = niet, cyaan = gekozen.
  **Rechts** het gekozen onderdeel, wat het kost (met de muis op een ingrediënt zie je zijn naam, en bij glazen ruiten dat
  elke soort goed is), "You can make 2" of "Not enough materials", en Make op de plek van "Take the pistol".
- **De tafel onthoudt** welk tabblad, wapen en onderdeel je het laatst open had (zolang het spel draait).
- **Een geluid**: het hamergeluid van de smithing table als je iets maakt; een klikje als je een tabblad, wapen of tegel kiest.
- **Tooltips**: onder elk onderdeel, de sling en het magazijn staat nu "Made on a Gunsmith Table (tab Make parts)".
- **Niet veranderd**: de Laser Workbench wordt nog altijd gemaakt met een Gunsmith Table erin. Wie een Laser Workbench
  bouwt, heeft dus een tweede Gunsmith Table nodig om laseronderdelen te maken. Zeg het als dat recept anders moet.

- [x] **Het recept** — `guns/GunsmithRecipe` (de codec met `SizedIngredient`, wat je hebt, `plan`/`take`/`times`, het
      meest versleten eerst, `all()` op naam gesorteerd zodat de server en jouw spel hetzelfde nummer gebruiken; "special",
      zodat het receptenboek van Minecraft er niet over klaagt), `registry/ModRecipes` (het type en de serializer).
- [x] **De tafel** — `GunsmithMenu.MAKE` (1000 + nummer), `PartsTable.make` (standaard niets: de Laser Workbench),
      `GunsmithTableBlockEntity.make` (neemt het materiaal, geeft het onderdeel, telt mee in je statistieken).
- [x] **Het scherm** — `client/guns/GunsmithMakePanel` (het tabblad: de wapens, de tegels, de kosten, tooltips, klikken
      en scrollen; tekst die te breed is, wordt afgekapt met "...", zodat er niets over elkaar staat), en in
      `PartsTableScreen` de twee tabbladen in de titelbalk en de knop Make (`makesParts()`: alleen de Gunsmith Table).
- [x] **De 64 recepten** omgezet (de 14 van de Glock, het magazijn, de 48 laseronderdelen en de sling); in de crafting
      table maakt geen enkel recept nog een onderdeel, een sling of een magazijn.
- [x] **Tests** — vier nieuwe (`GunsmithRecipeGameTests`): geen ander recept maakt een onderdeel, sling of magazijn; precies
      één recept per onderdeel, materiaal, sling en magazijn (64); geen twee recepten hetzelfde (op de tafel: dezelfde
      ingrediënten met dezelfde aantallen; in de crafting table: hetzelfde patroon, ook gespiegeld, of dezelfde
      ingrediënten zonder vorm, telkens als er een van ons bij is); en Make via het menu (5 staal geeft een slede, een
      tweede keer lukt niet en neemt niets, de meest versleten emitter gaat weg, de ingot uit je tweede hand telt mee, de
      Laser Workbench maakt niets). `netherite_upgrade_recipe_and_old_guns_get_parts` kijkt nu op de tafel. 501 tests,
      twee rondes: de eerste 500 groen en de bekende wisselvallige `guard_searches_where_he_lost_you` rood, de tweede
      alle 501 groen.
- [x] **How it works** (Gunsmith Table) — een nieuw hoofdstukje "Making parts" met een plaatje van het tabblad
      (`gunsmith_make`), en "Craft each part" is weg; bij de lasergeweren staat dat je de onderdelen op de Gunsmith Table
      maakt.
- [x] **Showcase 113** (Glock 19) — na het slepen het tabblad Make parts: een stalen slede maken (5 / 5 wordt 0 / 5), en de
      onderdelen van de laser rifle. Geen nieuwe showcase.
- [x] **Bekeken** met `-Ponly=glock` (`gl18`: Build met de twee tabbladen; `gl18b`: Make parts met de Glock, de stalen
      slede gekozen, 5 / 5; `gl18c`: na Make 0 / 5 in het rood, "Not enough materials", de Glock van "8 to make" naar "4
      to make"; `gl18d`: de laser rifle met titanium, netherite en de sling; `gl18e`: de tooltip van een tegel met "Made on
      a Gunsmith Table"), `-Ponly=howtopics` (alleen `gunsmith_table` en het nieuwe `gunsmith_make` gehouden, de andere
      plaatjes teruggezet) en `-Ponly=howtoall,howto:gunsmith,howto:laser_workbench`. Wat ik zo vond: "Polymer", "Laser
      Shotgun" en "Trigger Module" werden eerst afgekapt ("Polym...", "Laser Shot..."). Nu is de lijst links op dit tabblad
      breder, en is de kolom met namen precies zo breed als de langste naam van dat wapen; de namen van de materialen zijn
      nooit breder dan hun kolom. De meldingen rechtsboven ("New Recipes Unlocked") lagen in de rondleiding over de kosten:
      die haalt de rondleiding nu eerst weg. Een pijltje in How it works wees op "5 / 5" en bedekte het; nu wijst het naar
      het icoontje.
- [x] LEESMIJ (de Glock, de Gunsmith Table, de lasergeweren, het magazijn en de sling) bijgewerkt.

### 79. De Reloading Bench legt de patroon meteen klaar (11 november)

Jouw melding: "je moet uit reloadbench gaan voor aleer de bullets er zijn dit is raar kun je dat oplossen".

Wat er mis was: het menu rekende alleen bij het openen uit of er een patroon klaarlag. De drie vakjes horen bij de bank
zelf (niet bij het menu, zoals bij de crafting table), dus als je er iets in legde, kreeg het menu daar geen seintje van.
De regel eronder ("Enough for 2 round(s).") zag het wel, want die leest de vakjes zelf; alleen het vakje rechts bleef leeg tot je
de bank dicht en weer open deed. De eerste test (`reloading_bench_presses_rounds`) vulde de bank al vóór het openen, en
zag het daarom niet.

- [x] **De oplossing** — in `ReloadingBenchMenu` rekenen de drie vakjes het resultaat opnieuw uit zodra er iets in of uit
      gaat (`setChanged`), en `broadcastChanges` doet het elke tick nog eens: staan er twee spelers aan dezelfde bank, dan
      ziet de tweede het ook meteen. Het resultaat wordt alleen vervangen als het echt verandert.
- [x] **Test** — `reloading_bench_updates_while_open`: de bank leeg openen, huls, kogelpunt en Crushed Gunpowder erin
      klikken zoals een speler; pas bij het derde ligt de patroon er, meteen. Pak je hem, dan ligt de volgende klaar; haal
      je de kogelpunten eruit, dan is hij weg. Een tweede speler met de bank open ziet telkens hetzelfde.
      502 tests, twee rondes: de eerste 501 groen en de bekende wisselvallige `guard_searches_where_he_lost_you` rood, de
      tweede alle 502 groen.

### 81. De blokken van de Power Battery vallen terug en breken vlot met een pickaxe (11 november)

Jouw melding: "Als je iets van de battery breekt zoals een casing, glass, cel... dan krijg je dat niet terug, ook duurt
het heel lang om te breken. Kun je dit fixen".

Wat er mis was: de vier blokken van de batterij (Battery Casing, Battery Glass, Battery Cell en Battery Port, hoofdstuk
47) hadden geen loot table, dus Minecraft liet niets vallen. En ze vragen het goede gereedschap
(`requiresCorrectToolForDrops`), maar stonden bij geen enkel gereedschap (`mineable/pickaxe`). Dan is elk gereedschap het
verkeerde: ook met een pickaxe brak je zo'n blok even traag als met je hand (ongeveer 15 seconden).

- [x] **Loot tables** voor de vier blokken (`loot_table/blocks/battery_*.json`): elk blok valt terug als zichzelf, net als
      de andere machines (bij een explosie niet altijd, zoals bij gewone blokken).
- [x] **Bij de pickaxe** (`tags/block/mineable/pickaxe.json`): met een ijzeren pickaxe breek je er nu een in ongeveer
      0,75 seconde (hardheid 3, zoals ijzererts), met een houten in ruim 2 seconden. Met je hand blijft het traag en valt
      er niets, zoals bij de andere machines.
- [x] **De hele mod nagekeken**: geen ander blok had dit probleem. Alleen de laserwand van een gate heeft geen loot table,
      en die kun je niet breken.
- [x] **Tests** — twee nieuwe (`BatteryGameTests`): `battery_blocks_drop_themselves` (de vier blokken staan bij de pickaxe,
      een ijzeren pickaxe is het goede gereedschap, en elk valt terug als één blok) en `every_block_has_drops_and_a_tool`
      (elk breekbaar blok van de mod heeft een loot table, en wie het goede gereedschap vraagt, staat bij een gereedschap;
      zo komt deze fout bij een nieuw blok niet terug). 508 tests (met de vier van hoofdstuk 80 erbij), in één ronde
      allemaal groen.

### 82. Je coördinaten in de bril, aan en uit te zetten (12 november)

Jouw vraag: "Kun je zorgen dat je cords op IMT glasses kunt aan en uitzetten".

De bril liet je coördinaten nog nergens zien (alleen I.M.T zei ze als je ernaar vroeg). Nu wel, en je zet ze zelf aan of uit.

- [x] **Rechtsboven** (`CoordinatesWidget`): `X 0  Y -60  Z -4` (de letters cyaan, de getallen wit), even ver van de rand
      als het paneel links. Links je batterij en wat er actief is, rechts je coördinaten (jouw tweede wens: "Zet de cords
      rechts boven zodat er in de linker hoek je batterlij en active dingen staan en rechts je cords"; eerst stonden ze
      onder de batterij). Het zijn de blokcoördinaten, dezelfde als "Block" in F3, gewoon als getal (zonder punten of "k"
      van G > Information > Numbers). Zonder stroom in de bril (OFFLINE) en met het G-menu open zie je ze niet, zoals de
      rest van de HUD.
- [x] **De icoontjes van drankeffecten** tekent Minecraft ook rechtsboven. Zolang je de coördinaten ziet, schuiven die 21
      pixels naar onder, net onder de coördinaten (zoals Xaero's Minimap dat ook doet). Zo blijven de coördinaten altijd op
      dezelfde plek en valt er niets over elkaar. Coördinaten uit: de icoontjes staan weer bovenaan. (Mijn keuze; eerst
      probeerde ik de coördinaten naast de icoontjes te zetten, maar op een kleiner scherm paste er naast één icoontje al
      niets meer, en dan sprongen ze naar onder zodra je een drankje dronk.)
- [x] **Aan/uit** in **G > Navigation > Coordinates** (ON/OFF), of met een eigen toets in Opties > Besturing > I.M.T
      ("Coordinates on/off", standaard geen toets, zoals de andere snelacties). I.M.T meldt het: "Coordinates enabled." /
      "Coordinates disabled.". Het is een instelling van je eigen spel (`coordinates` in `imttech-client.toml`), er gaat
      niets naar de server. **Standaard aan** (mijn keuze: zo zie je ze meteen; uitzetten is één klik).
- [x] **Gezien** op screenshots (`gradlew runVisualCheck -Ponly=coords`: `32_coords_on` tot `32f_coords_off_effects_up`):
      aan, uit (met de melding), het G-menu met de optie op OFF, weer aan op een andere plek met negatieve getallen, met
      drie goede en twee slechte effecten (de icoontjes onder de coördinaten), en uit met die effecten (icoontjes bovenaan).
      Geen nieuwe automatische test: het is alleen beeld en een instelling van je eigen spel, en die draaien de tests
      (zonder beeld) niet.

### 83. Het laadstation zegt niets meer over upgrades (12 november)

Jouw wens: "bij chargin station niks zeggen over dat de upgrades ergns ander zijn, die oranje tekst moet dus weg".

- [x] **De oranje regel is weg** uit het scherm van het laadstation: "Upgrades: on the Suit Workbench" stond er sinds
      hoofdstuk 54 linksonder, over de energiebalk van het station heen. De tekst zelf
      (`screen.imttech.station.upgrades_moved`) is ook weg uit `en_us.json`.
- [x] **De uitleg van de bril** (W houden), hoofdstuk "Energy": de ballon bij het laadstation zegt nu alleen "Charge them
      in a *Charging Station*." en niet meer dat de upgrades aan de Suit Workbench ingebouwd worden. Dat staat al in de
      uitleg van de Suit Workbench (hoofdstuk "Glasses upgrades"), en die krijg je ook als je W houdt op een upgrade.
- [x] **Ook opgelost**: de tooltip van de Battery, Scanner en Drone Upgrade zei nog "Install it in a Charging Station",
      maar dat kan daar sinds hoofdstuk 54 niet meer. Nu staat er "Hold it and right-click the glasses on the Suit
      Workbench hologram", zoals bij de upgrades van het harnas. (Mijn keuze: zonder de oranje regel stuurde de tooltip
      je anders naar het laadstation, waar niets over upgrades staat.)
- [x] **Gezien** op screenshots (`gradlew runVisualCheck -Ponly=numbers`: `nb04_station_plain`, `_space` en `_short`):
      het scherm heeft alleen nog de balk van het station, het vakje met de bril en de energie ernaast. Geen nieuwe
      automatische test: het is alleen tekst, en die draaien de tests (zonder beeld) niet.

### 84. Het laadstation: niets naast een leeg vakje, "Glasses or cell" in het midden (12 november)

Jouw wens, met een screenshot: "de tekst 'put your glasse...' mag weg. de tekst 'glass or cell' mag mooi in het midden
onder het vakje".

- [x] **"Put your glasses (or an energy cell) here" is weg.** Die stond naast een leeg vakje en liep rechts buiten het
      scherm. Nu staat er niets naast een leeg vakje. Ligt er een bril in, dan staat daar zijn energie; ligt er een cel
      in, dan zijn naam, net als eerst. De tekst zelf (`screen.imttech.station.empty`) is ook weg uit `en_us.json`.
- [x] **"GLASSES OR CELL" staat in het midden onder het vakje** (eerst begon hij links gelijk met het vakje). Het midden
      van het vakje is `GLASSES_X + 8`; de kleine tekst (75%) schuift de helft van zijn breedte naar links, min de lege
      kolom die Minecraft na de laatste letter meetelt (anders stond hij net één pixel te ver naar links).
- [x] **Gezien** op screenshots (`gradlew runVisualCheck -Ponly=numbers`): nieuw zijn `nb04_station_empty` (leeg vakje)
      en `nb04_station_cell` (een energiecel), naast `nb04_station_plain`, `_space` en `_short` (met de bril). Het midden
      van het opschrift en van het vakje liggen op dezelfde pixel. Geen nieuwe automatische test: het is alleen beeld.

### 85. Kabels van koperdraad en plastic, de Polymerizer, en drie soorten kabels die niet aan elkaar vastzitten (12 november)

Jouw wens: "Verander de cable, netwerkcable en POE cable recepie: ik wil dat een cabel word gemaakt van copper wire en
plastic, dit krijg je door copper te crushen in een crusher, plastic krijg je van polymers, polymers krijg je van coal met
water in een nieuwe machiene zoals een crusher. Een netwerkkabel krijg je van een copper wire met een power cable en
plastic. Een poe krijg je van powerkable met netwerk cable en plastic."

En daarna: "Zorg ook dat al de blokken die niet in een netwerk horen, denk aan een crusher, geen power kunnen krijgen via
een POE. Elke blok die in een netwerk kan, kan een power, netwerk en poe kabel hebben. Iets dat niet in een netwerk kan,
kan enkel een powercable, geen netwerk of poe. Zorg ook dat power cable, netwerk en poe niet connecten aan elkaar."

De weg van koper en kool naar een kabel:

```
koperstaaf      --Crusher------->  3 Copper Wire
kool + 250 mB   --Polymerizer--->  2 Polymer
Polymer         --oven---------->  Plastic

Copper Wire + Plastic                    =  8 Power Cables
Copper Wire + Power Cable + Plastic      =  4 Network Cables
Power Cable + Network Cable + Plastic    =  2 PoE Cables
```

- [x] **Copper Wire uit de Crusher** (`CrackMachineBlock.Kind.convert`): één koperstaaf wordt 3 Copper Wire, in 5 seconden,
      net als een gebarsten blok. Elke koperstaaf telt (de tag `c:ingots/copper`), ook die van een andere mod. Het vage
      voorbeeld in het lege vakje toont nu ook een koperstaaf, en onderaan staat "Cracked blocks, gunpowder or copper
      ingots.". Een regel die te lang is voor het scherm breekt nu af over twee regels (dat was ook al nodig voor "No power:
      connect it with power cables.", die rechts uit het scherm liep).
- [x] **De Polymerizer** (nieuw blok, `plastic/PolymerizerBlock`): een kast op netstroom zoals de Crusher, met een vakje
      voor kool (of houtskool), een emmervakje, een vakje eruit, en een **watertank** van 4 emmers. Water erin doe je met
      rechtsklik met een emmer water (je houdt de lege emmer over), met een emmer in het emmervakje (de lege emmer blijft
      daar liggen, een trechter eronder haalt hem eruit), of met buizen van een andere mod zoals Create (hij heeft de
      vloeistof-capability; alleen water, en eruit halen kan niet). Per kool gaat er 250 mB water uit en komen er 2 Polymer
      bij, in 5 seconden, voor 40 stroom per tick. Zijn scherm: links de tank (met de muis erop: "Water: 750 / 4000 mB"),
      dan kool en emmer, een pijl en het polymeer; onderaan "Making polymer...", "No water: add a water bucket.", "No
      power" of "Output full". Trechters: kool en emmers water van boven of opzij, polymeer en lege emmers van onder.
      Recept: ijzer, een emmer, glas, een computerchip en een oven. Hij staat in het tabblad Interior, naast de Crusher.
- [x] **Plastic**: Polymer in een oven smelten. Het oude recept van polymeer (slijm en kool) is weg: polymeer komt nu
      alleen nog uit de Polymerizer (ook voor de polymeer-onderdelen van de Glock en de rugzakken).
- [x] **De recepten van de kabels** (`cable.json`, `network_cable.json`, `poe_cable.json`): zoals hierboven. Koperdraad
      zit in de tag `c:wires/copper`, dus de koperdraad van een andere mod (Create Crafts & Additions, Immersive Engineering)
      werkt ook. De oude recepten (koper + redstone, koper + kwarts, PoE met groene kleurstof) zijn weg.
- [x] **De drie soorten kabels zitten niet meer aan elkaar vast** (`CableKind.joins`): een stroomkabel loopt alleen verder
      in een stroomkabel, een netwerkkabel in een netwerkkabel, een PoE-kabel in een PoE-kabel. Liggen ze tegen elkaar aan,
      dan gaat er niets over en tekent de kabel ook geen verbindingsstukje meer. Van de ene soort naar de andere gaat het
      alleen via een blok: één stroomkabel op de Security Core voedt zijn PoE-kabels, zoals eerst. Het stroomnet
      (`PowerGrid`) en het netwerk (`LanGrid`) zoeken daarvoor nu per soort kabel: op één plek kunnen een stroomkabel en
      een PoE-kabel liggen, en dat zijn dan twee aparte kabels.
- [x] **PoE alleen voor netwerkblokken** (`CableKind.connectsTo`, `PowerGrid.takesPoe`): een blok dat in een netwerk kan
      (de kern, lezers, panelen, camera's, torentjes, laserzenders, bruggen, racks, monitors, computers, de switch, de
      netwerkknop en -hendel) neemt een stroomkabel, een netwerkkabel en een PoE-kabel. Elk ander blok (een Crusher, de
      Polymerizer, een generator, een stroomkast, een lichtschakelaar, een drone-dock...) alleen een stroomkabel. Zo'n blok
      krijgt nooit stroom via PoE, ook niet als het op de PoE-kabel staat, en een generator tegen een PoE-kabel voedt die
      kabel niet. Het scherm van de stroomkast telt een PoE-kabel ernaast niet meer als "wired".
- [x] **Tooltips**: elke kabelrol zegt nu "Power, network and PoE cables never join each other", en de PoE-kabel "Power and
      network in one cable for network devices, fed by a Security Core that has power".
- [x] **How it works**: een nieuw onderwerp **Making cables** (tabblad Power; W op de Polymerizer, Copper Wire, Plastic of
      Polymer): de Crusher maalt koper, de Polymerizer krijgt een emmer water en maakt polymeer, de oven maakt plastic, en
      de drie recepten zweven op een rij. In "Network cables and PoE" zegt de PoE-ballon nu ook dat PoE alleen op
      netwerkapparaten past en dat de drie soorten niet aan elkaar vastzitten.
- [x] **Config** (`[materials]`, nieuw): `copperWirePerIngot` (3), `polymerPerCoal` (2), `waterPerCoal` (250 mB),
      `polymerizerTicks` (100) en `polymerizerTank` (4000 mB). Bij `[power]`: `polymerizer` (40 per tick).
- [x] **Mijn invullingen** (zeg het als je het anders wilt): 3 koperdraad per koperstaaf; 2 polymeer per kool met 250 mB
      water; plastic uit een gewone oven (jij zei niet hoe); de aantallen van de recepten (8 stroomkabels, 4 netwerkkabels,
      2 PoE-kabels, zoals de PoE eerst ook 2 gaf); de tank van 4 emmers; houtskool telt als kool; het recept van de
      Polymerizer; en dat een blok dat stroom doorgeeft (een drone-dock, een rack...) nog steeds stroom doorgeeft aan een
      blok dat ertegen staat.
- [x] **Tests**: `crusher_grinds_copper_into_wire`, `polymerizer_makes_polymer_from_coal_and_water`,
      `polymerizer_needs_water_and_power` (ook rechtsklik met emmers tot de tank vol is, en een buis die water maar geen lava
      in de tank pompt), `cable_recipes_use_copper_wire_and_plastic` (de nieuwe recepten maken het juiste aantal, de oude
      niets meer, polymeer smelt tot plastic), `power_network_and_poe_cables_do_not_join` (vervangt
      `poe_joins_power_and_network_cables`, die het omgekeerde testte) en `poe_only_powers_network_devices` (een Crusher
      aan of op een PoE-kabel heeft geen stroom, een lezer aan dezelfde kabel wel, en een generator tegen een PoE-kabel
      voedt niets). 514 tests, twee rondes (de tweede door een andere
      sessie, met dit hoofdstuk erin): in de ene alleen de bekende wisselvallige `raven_soldier_shoots` rood, in de
      andere alleen de wisselvallige `a_guard_who_sees_a_body_calls_it_in`.
- [x] **Gezien** op screenshots (`gradlew runVisualCheck -Ponly=repair`): `r07_crusher_copper` (koper erin, koperdraad
      eruit), `r08_polymerizer_working` (de voorkant naast de Compactor), `r09_polymerizer_screen` (kool, een halve tank,
      polymeer eruit), `r09b_polymerizer_tank` ("Water: 1750 / 4000 mB"), `r10_polymerizer_empty` en
      `r11_polymerizer_no_water`, en `r06_crusher_empty_b` (de regel van de Crusher over twee regels). Niets valt over
      elkaar.
- [x] **Onderweg gevonden**: tijdens de eerste testrondes stond er een dev-client open in `run/` (met de oude versie van
      de mod). Die en de testserver "verbeterden" om beurten `run/config/imttech.toml` naar hun eigen versie, honderden
      keren, en elke keer gingen de getallen terug naar die uit het bestand. Tests die even een getal veranderen (de
      generator, de rookgranaat, de handboeien...) faalden daardoor. Sluit de dev-client voor je de tests draait.

### 86. Diagrammen: 1 CPU per 10 apparaten, en I.M.T het dubbele (12 november)

Jouw wens: "ik wil dat het gebruik van diagrams simpeler is, het is logisch dat je voor weinig devices ook maar weinig
CPU's nodig hebt. Zorg dat je met 1 CPU 10 devices zoals keycard readers tegelijk kunt gebruiken, als je er meer dan 10
hebt wordt het per device 10% trager. Je hebt wel nog steeds voldoende SSD's nodig en de RAM in verhouding tot CPU blijft.
Voor IMT glasses wil ik dat je het dubbele van de CPU's nu nodig hebt en ook het dubbele van de GPU's. Voor de diagrams kun
je ook GPU's gebruiken, dat is dan dubbel zoveel voor hetzelfde effect."

**Mijn invulling** (zeg het als je het anders wil, het zijn allemaal getallen in de config):
- **Wat een apparaat is**: alles wat een diagram dat aan staat bij naam noemt. Ingangen in `when keycard(...)`,
  `codepanel`, `button`, `lever` en `request` (elk blok met die naam: twee lezers die allebei "Lab door" heten zijn er
  twee), en uitgangen in `on`, `off`, `toggle`, `pulse`, `when turned_on` en `turned_off` (alles wat het telefoonboek
  onder die naam heeft: een groep telt voor alles wat erin zit). Een apparaat dat je vaker noemt telt één keer. Apparaten
  die geen enkel diagram noemt (camera's, een lezer die nergens in staat) tellen niet: daar heeft de server geen werk aan.
  Een naam die pas tijdens het draaien uitgerekend wordt (`pulse(door)` met een parameter) telt ook niet.
- **"10% trager"**: zolang het past reageert een diagram meteen (1 tick). Elk apparaat te veel haalt 10% van de snelheid
  af, dus 10% van de langste wachttijd (6 s, zoals vroeger met bijna geen CPU's) erbij: 11 apparaten op 1 CPU = 0,6 s,
  15 = 3 s, en vanaf 20 (10 te veel = 100%) 6 s. Trager dan 6 s wordt het niet.
- **GPU's**: een GPU telt voor je diagrammen half (zoals al sinds 8 oktober), dus 2 GPU's = 10 apparaten.
- **Warm** (een rack vanaf 70 graden): de server kan de helft van de apparaten aan, zoals warm altijd alles halveert.
- **RAM en SSD** blijven zoals ze waren: per 10 CPU's of GPU's 64 GB RAM (minder = naar verhouding minder kracht, dus ook
  minder apparaten), en een SSD is opslag (een diagram dat niet meer op je SSD's past, draait niet en telt dus ook niet).

- [x] **De rekenregel** — `LogicStats.deviceCapacity`, `devicesTooMany`, `slower` en `reactionTicks(kracht, apparaten)`.
      De rechte lijn naar 30 CPU's voor de diagrammen is weg (`logicInstantPower`); nieuw zijn `logicDevicesPerCpu` (10)
      en `logicSlowerPerDevice` (0.1). `logicMaxDelayTicks` (120 = 6 s) is nu de langste wachttijd bij 100% trager.
- [x] **De apparaten tellen** — `LogicDevices.used`: de namen uit de blokjes van de diagrammen die draaien (aan, en op de
      SSD's), en daarvan de echte blokken (ingangen op hun plek, uitgangen uit het telefoonboek). `LogicServer.Stats` heeft
      nu `devices`, en telt opnieuw zodra je een diagram bewaart (niet pas na 2 seconden).
- [x] **I.M.T het dubbele** — `brainFullSpeedPower` (240) vervangt `brainInstantPower` (120), met een nieuwe naam zodat de
      240 ook in een bestaand configbestand geldt. Dezelfde rechte lijn van 12 s: 7 s met 100 CPU's, 2 s met 200, meteen
      vanaf 240 CPU's of 120 GPU's (een GPU telt voor het brein nog altijd dubbel). Dat zijn 120 racks met CPU's, of 60 met
      GPU's.
- [x] **Wat je ziet** — de app **Monitor** (rechts, zonder rack gekozen): "2 CPUs, 2 GPUs: 16 / 14 devices, 20% slower"
      (oranje als het er te veel zijn) en daaronder "Diagrams 1.2 s, I.M.T +11.9 s (RAM 94%)". Die tweede regel is korter
      dan eerst ("(94% speed)" viel er rechts af). Gezien op een screenshot (`gradlew runVisualCheck -Ponly=racks`, nieuw:
      `r05c_monitor_server_devices`, met 16 lampen in een diagram op een warme server). De tooltip van een CPU zegt "Logic power 1 (10
      devices in your diagrams at once), brain power 1 (240 = I.M.T without delay)", van een GPU 5 apparaten en 120 GPU's.
      De uitleg (W op een serverrack, "Bigger is faster") en LEESMIJ zeggen hetzelfde.
- [x] **Tests** — `datacenter_straight_line_to_30_and_120` heet nu `datacenter_ten_devices_per_cpu` (de rekenregel: 10, 11,
      15, 20 apparaten op 1 CPU, 1 en 2 GPU's, en I.M.T met 100, 200 en 240 CPU's). Nieuw is
      `datacenter_counts_the_devices_in_your_diagrams`: twee lezers met dezelfde naam, een codepaneel en zeven relais in een
      diagram zijn 10 apparaten (meteen); een diagram dat uit staat telt niet; zet je het aan, dan zijn het er 12 en is de
      server 20% trager. De tooltiptest en de test met het warme rack volgen de nieuwe regel.
      Twee diagramtests rekenden nog op de oude reactietijd in de tests (10 ticks) en zijn bijgesteld:
      `logic_def_params_run` kijkt nu vóór de tweede tik van de timer, en in `logic_keycard_opens_vault` komt de
      geblokkeerde kaart pas als het rode lampje van de valse kaart uit is (een tweede rood binnen 30 ticks wordt door de
      "uit" van het eerste afgekapt, dat was al zo). Eén test erbij. Volledige ronde: 514 tests (met het werk van de
      andere sessies), alleen de wisselvallige bewakertest `a_guard_who_sees_a_body_calls_it_in` rood (willekeurige AI,
      niets met het datacenter). Een eerdere ronde had meer rood doordat je dev-client met een oude versie tegelijk
      `run/config/imttech.toml` steeds terugschreef.

### 87. De Electric Furnace: smelten op netstroom (12 november)

Jouw wens: "Voeg een electrische furnace toe zodat je net zoals een crusher enzo dingen kunt smelten op power".

**Mijn invulling** (zeg het als je het anders wil; de getallen staan in de config):
- **Wat hij smelt**: alles wat een gewone oven smelt, met dezelfde recepten (`minecraft:smelting`, dus ook die van andere
  mods): erts, eten, zand, klei, polymeer tot plastic... Geen brandstof: hij werkt op netstroom, net als de Crusher (via
  een stroomkabel, of direct tegen een generator). Geen PoE: hij hoort niet in een netwerk.
- **Snelheid en stroom**: **twee keer zo snel** als een oven (5 seconden per erts in plaats van 10, `electricFurnaceSpeed`
  bij `[materials]`), voor **40 FE per tick** zolang hij smelt (net als de Crusher, `electricFurnace` bij `[power]`). Eén
  erts kost zo 4.000 FE; een warmtegenerator maakt op één kool genoeg voor veel meer dan de 8 van een gewone oven.
- **Ervaring** zoals bij een oven: hij spaart ze op, en je krijgt ze als je het resultaat eruit haalt (ook met shift-klik),
  of als je hem afbreekt.
- **Twee vakjes**: erin en eruit (geen brandstofvakje). Trechters doen er van boven of opzij iets in (alleen wat smelt) en
  halen het resultaat er van onder uit; buizen en lopende banden van andere mods ook (de item-capability).
- **Recept**: drie ijzer, koperdraad - oven - koperdraad, ijzer - computerchip - ijzer (de koperdraad uit hoofdstuk 85).
- **Hoe hij eruitziet**: de kast van de Crusher, met achter het venster twee gloeispiralen: uit dof koper, aan oranje met
  gele hete puntjes en een donkerrood venster. Opzij luchtgaten en een koperen stroomaansluiting, bovenop een rooster
  waar af en toe een dun rookpluimpje uit komt. Terwijl hij werkt geeft hij **licht 13**, zoals een brandende oven. Geen
  geknetter: er brandt niets.

- [x] **Het blok** — `furnace/ElectricFurnaceBlock` (draait naar wie hem neerzet, `working` voor de gloed en het licht,
      comparator leest hoe vol hij zit, afbreken laat alles vallen en geeft de ervaring) en
      `furnace/ElectricFurnaceBlockEntity` (zoekt het recept met een `RecipeManager.CachedCheck` zoals een gewone oven, de
      tijd is de kooktijd van het recept gedeeld door de snelheid, wacht zonder stroom of met een vol vakje eruit en
      vergeet niet hoe ver hij was). Een nieuwe oven gloeit niet eerst even op voor hij iets gedaan heeft.
- [x] **Het scherm** — `ElectricFurnaceMenu` en `client/furnace/ElectricFurnaceScreen`, met de vakjes op dezelfde plek als
      bij de Crusher. Leeg vakje erin: om de beurt een vaag ruw ijzer, zand en polymeer. De pijl loopt oranje vol, en
      eronder gloeit een spiraal zolang hij smelt. Onderaan "Smelting...", "Anything a furnace smelts. No fuel needed.",
      "No power: connect it with power cables." of "Output full: take it out." (over twee regels als het niet past).
- [x] **Overal bij** — de creative-tab (na de Polymerizer), de pickaxe-tag, de loot table, de textures
      (`electric_furnace_textures()` in `tools/generate_textures.py`, alleen die functie gedraaid), en de config.
- [x] **De uitleg** — W op de Electric Furnace: "Electric Furnace" in het tabblad Power (onderwerp 57): ruw ijzer wordt een
      ijzerstaaf tegen een generator, een trechter erop met zand (glas eruit), en de ervaring.
- [x] **Screenshots** — `gradlew runVisualCheck -Ponly=repair`: `r12_electric_furnace_working` (de gloed naast de
      Polymerizer), `r13_electric_furnace_screen`, `r14_electric_furnace_empty` en `r15_electric_furnace_no_power`.
- [x] **Tests** — `electric_furnace_smelts_on_power` (twee ruw goud: na 100 ticks één goudstaaf, stroom gebruikt, aan en
      licht 13, 1 ervaring bewaard; shift-klik op de goudstaaf zet hem in je inventaris en laat 1 ervaring vrij; aarde mag
      er niet in, polymeer wel) en `electric_furnace_waits_and_takes_hoppers` (zonder stroom "No power" en niet aan, met
      een vol vakje "Output full", een trechter van boven en opzij doet ruw ijzer erin maar geen aarde, van onder haalt
      hij de ijzerstaven eruit en van boven niets, en afbreken laat het ruwe koper vallen met 3 ervaring). Volledige ronde:
      516 tests (met het werk van de andere sessies), alle groen.

### 89. Met een magazijn in je hand: geen grijze regel meer rechtsonder (12 november)

Jouw wens: "r to reload mag... de gray tekst rechtsonder als je een mag vast hebt mag weg".

- [x] **"R: load a round (23 rounds in your inventory)" is weg** (`FirearmHud.magazine`). Rechtsonder staan nu alleen de
      naam van het magazijn en hoe vol het is ("0 / 15"). Ook "Full" (op dezelfde plek, ook grijs) is weg: dat zie je al
      aan "15 / 15", en drukken op R zegt het ook ("The magazine is full"). De twee teksten zijn ook weg uit
      `en_us.json`.
- [x] **Wat er wel nog staat** (mijn keuze, zeg het als die ook weg mogen): de rode regel "No 9mm Rounds in your
      inventory" als je geen losse patronen hebt, en "Hold right-click: 120 in box" als je naar een Ammo Box kijkt
      (hoofdstuk 67). Die verschijnen alleen als er iets bijzonders is.
- [x] **Gezien** op screenshots (`gradlew runVisualCheck -Ponly=glock`): `gl13_magazine_in_hand` (23 patronen bij je,
      niets onder "0 / 15") en `gl14_magazine_loading` (alleen het blauwe balkje erboven). Geen nieuwe automatische test:
      het is alleen beeld.

### 90. De MOLLE-pouches: geen tekst meer onder de vakjes (12 november)

Jouw wens: "in de utility pouch is de tekst te groot, het valt uit de GUI, dit moet opgelost worden, de hele tekst die
daar staat mag gewoon weg".

- [x] **Beide regels zijn weg** (`MolleScreen.renderLabels`): "Anything, but no boxes and no full pouches." en "On your
      backpack: open it from the Alt wheel, no zip needed" (die tweede liep rechts uit het scherm). Bovenaan staan nog de
      naam en hoeveel vakjes vol zijn ("2 / 4"), daaronder de vakjes.
- [x] **Bij alle vijf MOLLE-pouches** (mijn keuze): de MOLLE-magazijnpouch, -granaatpouch, dump pouch, IFAK en utility
      pouch hebben hetzelfde scherm, en de lange tweede regel liep bij elk ervan uit het scherm. Wat erin mag, staat nog in
      de tooltip van de pouch. De zes teksten (`screen.imttech.molle.hint_*`) zijn ook weg uit `en_us.json`. Het scherm
      van de holster en de pouches op je heup (`GearPouchScreen`) is niet veranderd.
- [x] **Gezien** op screenshots (`gradlew runVisualCheck -Ponly=backpack`): `pk22_ifak_open` en nieuw
      `pk22b_utility_pouch_open` (een schaar en 12 fakkels, "2 / 4"). Geen nieuwe automatische test: het is alleen beeld.

### 91. In singleplayer weer schieten nadat je op een server was (12 november)

Jouw melding: "ik weet niet waarom maar in sigleplayer kan ik niet schieten met een glock op geen enkele manier, in
multiplayer server lukt dit wel".

Hoe het komt: je spel onthoudt wanneer je weer mag schieten (`FirearmControl.nextShot`: het tijdstip van je laatste schot
plus 6 ticks), in de speltijd van de wereld waar je bent. Maar elke wereld heeft zijn eigen klok: je server draait dag en
nacht en staat op miljoenen ticks, je wereld "Test" stond op 1178. Bij het verlaten van een wereld zette je spel veel
terug, maar die tijd niet. Na één schot op de server wachtte je spel in singleplayer dus op een tijd die daar pas na
dagen spelen komt: geen schot, geen klik, geen melding (en de trekker bleef ingedrukt getekend). Op de server ging het
wel, want daar staat de klok altijd verder dan in je eigen wereld. In de logs van je Modrinth-profiel: elke keer dat je
in singleplayer ging, had je eerst op de server gespeeld zonder het spel ertussen opnieuw te starten.

- [x] **Alle tijden terug als je een wereld verlaat** (`FirearmControl.reset`): ook `nextShot`, en die van de animaties
      (het pakken, het schot, de klik, R inhouden). `reset` draait nu ook zolang je in geen wereld zit (het titelscherm,
      tussen twee werelden): Minecraft speelt na Disconnect nog een paar ticks in de oude wereld af, en die konden er
      anders weer een oude tijd in zetten. Het zit alleen in je spel; de server hoeft niet mee (het protocol bleef 35).
- [x] **De lasergeweren hadden hetzelfde** (`GunControl.nextShot`), op dezelfde manier opgelost. De Laser Cannon zette
      zijn tijd al terug, en de server vergat de tijden al bij het uitloggen: daar was niets mis.
- [x] **Gezien** met een nieuw stukje rondleiding (`gradlew runVisualCheck -Ponly=worldclock`; alleen als je erom vraagt,
      want je verlaat de testwereld): een schot met de Glock en met het laserpistool in een wereld met de klok op 5
      miljoen, Disconnect, een nieuwe wereld (klok 0) en daar weer. Zonder de oplossing ging in de nieuwe wereld niets
      af (de Glock hield 16 patronen, het laserpistool 20 van 20 schoten), met de oplossing allebei wel (screenshot
      `wc02_shot_in_new_world`: "14 +1"). Geen nieuwe automatische test: die draaien op een server zonder spelvenster, en
      dit zat in je spel.

## Nog nooit in het spel bekeken

De automatische tests draaien zonder beeld: ze controleren wat er gebeurt, niet hoe het
eruitziet. Dit is gebouwd maar door mij nooit gezien. Laat het weten als iets er raar uitziet.

- [ ] **De Electric Furnace (hoofdstuk 87)**: niet met de muis gespeeld. Probeer: iets smelten tegen een generator en met
      een stroomkabel, shift-klik op het resultaat (krijg je de ervaring?), trechters erboven, opzij en eronder, en kijk of
      hij gloeit en licht geeft terwijl hij werkt en weer uitgaat als hij klaar is. Is twee keer zo snel als een oven
      goed, of wil je hem sneller of trager (`electricFurnaceSpeed`)?

- [ ] **Kabels maken en de drie soorten kabels (hoofdstuk 85)**: het scherm van de Polymerizer en de Crusher met koper
      zijn gezien op screenshots, maar niet met de muis gespeeld. Probeer: rechtsklik met een emmer water (tot de tank
      vol is), een emmer in het emmervakje, shift-klik met kool, trechters erboven en eronder, en buizen van Create die
      water in de tank pompen. Kijk ook of een PoE-kabel naast een stroomkabel of een Crusher er echt los van ligt (geen
      verbindingsstukje meer), en bekijk de nieuwe uitleg "Making cables" (W op de Polymerizer).

- [ ] **Diagrammen: 1 CPU per 10 apparaten (hoofdstuk 86)**: de rekenregel is getest en de app Monitor gezien op een
      screenshot, maar niet gespeeld. Probeer een basis met 1 CPU: tot 10 apparaten in je diagrammen gaat alles meteen;
      zet er 15 in (de app Monitor zegt "15 / 10 devices, 50% slower") en voel hoe lang een lezer dan op zijn groene lampje
      wacht (3 s). Voelt 10% per apparaat goed, of moet het anders (`logicSlowerPerDevice`, `logicDevicesPerCpu`)?

- [ ] **Het tabblad "Make parts" van de Gunsmith Table (hoofdstuk 78)**: gezien op screenshots (`-Ponly=glock`: `gl18` tot
      `gl18e`), niet met de muis gespeeld. Probeer: de tabbladen bovenaan, een wapen links kiezen (ook scrollen op die
      lijst), een tegel kiezen, Make (komt het onderdeel in je inventaris, worden de getallen rechts meteen lager?), en of
      de kleine tekst en de tegels goed leesbaar zijn op jouw GUI-schaal.

- [ ] **How it works (hoofdstuk 63)**: gezien op screenshots (`-Ponly=howto`, ht01 tot ht19), niet met de muis gespeeld.
      Probeer: in je inventaris je muis op een Battery Casing en W vasthouden (op jouw AZERTY-toetsenbord: Z); voelt 0,6
      seconde goed? Slepen en scrollen in het wereldje, de knoppen onderaan, klikken op de tijdlijn, een blok aanwijzen,
      Esc (ben je terug in je inventaris?), en het knopje ? rechtsboven. Ook in een kist en in JEI. Hoor je de tikjes van
      de blokken (niet te hard? `howToVolume`), en is het platform mooi genoeg op jouw GUI-schaal? Sinds stap 6 ook: kloppen
      de acht nieuwe animaties met hoe jij het speelt (de kern, keycards, het codepaneel, laserpoorten, stroom, kabels,
      netwerk, het laadstation), en zijn de plaatjes van de desktop en de apps leesbaar?

- [ ] **De sluipaanval en het oog zonder bril (hoofdstuk 61)**: gezien op screenshots (`-Ponly=guards`, hg02, hg03,
      hg07 tot hg12), niet gespeeld. Probeer: sluip achter een bewaker met een zwaard (staat "Takedown" onder je vizier,
      ook net naast zijn rug?), klik (grijp je hem, worstelt hij twee seconden en sta je zolang stil? schudt je beeld niet
      te hard?), laat eens los door weg te vliegen of te teleporteren (draait hij zich om?), en kijk of het kleinere oog
      en het "?"/"!" op jouw GUI-schaal nog goed te zien zijn.

- [ ] **De onderdelen in je inventaris (hoofdstuk 59)**: gezien op screenshots (`-Ponly=creative`: `ct03_weapons`,
      `ct03_weapons_b_glock_parts`, `ct03_weapons_c_laser_parts`; en `lp10`), niet gespeeld. Kijk of je op jouw
      GUI-schaal de onderdelen uit elkaar houdt (frame, slede, loop, veer, slagpin, trekker; de zes laseronderdelen per
      geweer, titanium en netherite) en of een onderdeel op de grond en in je hand (F5) goed ligt.
- [ ] **Het nieuwe wiel (hoofdstuk 56)**: gezien op screenshots (`-Ponly=gear,sling`), maar niet met de muis gespeeld.
      Lopen met Alt vast is sinds stap 7 wel gemeten met echte toetsaanslagen (W, ook als je al liep). Probeer: Alt
      vasthouden, naar een tegel schuiven en loslaten (kiest hij meteen, zonder klik?); Alt kort tikken (gaat hij meteen
      weer dicht?); met Alt vast Shift, Ctrl en de spatiebalk (sluip, sprint en spring je door? en met "sluipen: toggle"
      aan?); na een klik op een tegel Alt vasthouden (blijft het wiel dicht tot je loslaat?); de twee pouch-knoppen
      ernaast; en of het midden leesbaar is op jouw GUI-schaal.
- [ ] **Lasergeweren laden en lossen (hoofdstuk 57)**: getest en op screenshots (`-Ponly=guns`, `g08b`-`g08d`), niet
      gespeeld. Probeer: R zonder pouch (komt de cel eruit, met de korte animatie?), rechtsklik met een cel in je hand
      (gaat hij in het geweer in je werkbalk, hoor je de klik?), U met een pouch om (komt hij in de pouch?).
- [ ] **De magazijnpouch met zes vakjes (hoofdstuk 58)**: op screenshots (ge05, ge08, sl05), niet gespeeld. Kijk of de
      pouch op je heup (twee rijen diep) niet raar uitsteekt en of het keuzescherm met twee rijen van drie leesbaar is.
- [ ] **De lasergeweren uit onderdelen en de slings (hoofdstuk 55)**: gezien op screenshots (`-Ponly=laserparts,sling`) en
      met tests, maar niet met de muis gespeeld. Probeer: de Laser Workbench (sleep een frame, de rest, de Nether Star en de
      sling; haal de kern er weer uit), een netherite onderdeel maken (een titanium onderdeel plus twee netherite ingots),
      schieten tot een onderdeel versleten is (zet `laserWearFactor` hoog om het snel te zien), een rifle met sling in het
      sling-vakje onder het craftraster, het wiel met je Glock in je hand (de Glock moet in de holster gaan), R met cellen in
      je pouch, en in F5 de geweren op je rug en voor je borst (hangen ze goed, zitten de banden mooi?). Met Curios erbij: het
      slot "Sling" (ongetest).

- [ ] **De generatorkamer van de toren, de bewakers en het diagram (hoofdstuk 54)**: gezien op screenshots (`-Ponly=powerroom`)
      als los stuk, en met tests, maar niet in een echte toren gespeeld. Probeer: een nieuwe wereld, `/locate structure
      imttech:biebia_tower`, versla de bewaker in de lobby, houd zijn staff-kaart tegen de lezer "Power door" (de poort
      moet acht seconden uitgaan en in het logboek van de kern komt "Staff card at the generator room"), hak het luik
      VENT 3B weg, en trek de stroomkast eruit (de hele toren moet donker worden). Hack de kern en kijk of "Generator
      room" in de app Logic staat en de zenders "Power gate" heten in Devices.
- [ ] **Raiddrones tegen een zone (hoofdstuk 54)**: alleen met tests. Probeer: een zone met torentjes, wacht op een raid
      (of `/imttech raid`), en kijk of de drones buiten je basis aankomen, sticky frags op je muur gooien en EMP's op je
      torentjes.
- [ ] **Raven-soldaten met de wapens van de mod (hoofdstuk 54)**: gezien op screenshots, de bunker niet opnieuw gespeeld.
      Probeer: de bunker, en kijk of de soldaten met Glocks en lasergeweren je raken, of de granaten over muren komen,
      en of de Laser Turrets van de bunker op je schieten tot je de kern bij de reactor hackt.
- [ ] **Torens en bunkers uit kamers (hoofdstuk 51)**: gezien op screenshots (`-Ponly=rooms`, en showcase 112) op vlak land,
      maar niet in een echte wereld. Probeer: `/locate structure imttech:biebia_tower` in een nieuwe wereld en kijk of de
      verdiepingen netjes op elkaar staan (de terrassen, de liftkoker), of de kern het hele gebouw beveiligt en of de
      lasers in de lasergang aan staan; `/locate structure imttech:biebia_bunker` en kijk of de zalen aansluiten en de
      laserwanden stroom hebben. Een oude wereld houdt zijn oude toren en bunker.
- [ ] **De I.M.T Phone (hoofdstuk 50)**: gezien op screenshots (`-Ponly=phone`, en showcase 111), maar niet zelf gespeeld.
      Probeer: een telefoon verbinden, een PIN kiezen, hem ver van je basis openen en een verzoek beantwoorden; een vriend
      laten proberen met een foute PIN (jij moet het horen); hem blokkeren in Access. Kijk of de kleine desktop met zes
      apps er goed uitziet, en of de app Messages leesbaar is.
- [ ] **Autorisatiecodes en de Auth Console (hoofdstuk 49)**: gezien op screenshots (`-Ponly=auth`, en showcase 110), maar
      niet zelf gespeeld. Probeer: de code in Status aflezen en op een paneel typen met `auth.valid` in je diagram; in
      Settings de lengte en de minuten veranderen (de code verspringt meteen bij een andere lengte); een vriend op de
      console laten drukken en antwoorden in Requests (ook via de laptop). Kijk of de knoppen [-] [+] in Settings op jouw
      scherm netjes naast elkaar staan.
- [ ] **Handboeien (hoofdstuk 48)**: gezien op screenshots (`-Ponly=cuffs`, en showcase 109), maar niet zelf gespeeld met
      een echte tweede speler. Probeer: iemand boeien (twee seconden vasthouden), kijk of het **touw** en het bandje om
      zijn polsen er goed uitzien, of hij lekker **meekomt** als je wegloopt (ook trappen op), of hij echt niets kan, en
      of het worstelen (30 seconden sluipen) klopt. Een robot: na de sleutel moet zijn programma weer lopen.
- [ ] **De Power Battery (hoofdstuk 47)**: gezien op screenshots (`-Ponly=battery`, en showcase 108), maar niet zelf gespeeld.
      Probeer: een doos van casing bouwen met cellen erin en een poort in de wand, een kabel van je generatoren naar de
      poort, en dan rechtsklik op de poort. Kijk of het scherm klopt met wat je net doet (laden, verbruiken, tekort),
      of de lijst DEVICES je apparaten noemt, en of een vreemde kabel (Mekanism, Create Crafts & Additions) de
      batterij vult of leegt.
- [ ] **Forge Energy (hoofdstuk 46)**: alleen getest. Probeer een oude wereld: na het laden moeten je bril, je
      generatoren en je robots evenveel stroom hebben als eerst (maar dan in FE, maal tien), en `imttech.toml`
      moet `energyUnits = 2` hebben met alle stroomgetallen maal tien. Zet eens een machine van een andere mod
      tegen een zonnepaneel: hij moet vollopen.
- [ ] **De schermmuur (hoofdstuk 31)**: gezien op screenshots (`-Ponly=camerawall`, en showcase 105), maar niet zelf gespeeld.
      Probeer: monitoren tegen een muur, een PoE-kabel naar je kern, en dan slepen in het instelscherm. Kijk of het **slepen** lekker
      werkt, of de beelden scherp genoeg zijn, of je spel niet trager wordt bij veel beelden tegelijk (`cameraFeedFps` en
      `cameraFeedBudgetMs` in `imttech-client.toml`), en of het **donkerblauwe icoontje** op een lege monitor mooi is.
- [ ] **Schieten met `write` en `aim` (hoofdstuk 31)**: een wapen dat aan staat, schiet zodra het klaar is. Klopt het zo voor jou dat
      een robot met een Scan wacht tot hij iemand ziet, in plaats van recht vooruit te schieten?
- [ ] **De laptop overal en de Computer met Monitor (hoofdstuk 40)**: gezien op screenshots (`-Ponly=computers`, en
      showcase 103 en 104), maar niet zelf gespeeld. Probeer: een wachtwoord op je kern zetten (Settings), het IP uit
      Status, en ver weg rechtsklikken met de Hacking Laptop; een Computer met een netwerkkabel en stroom, een Monitor
      ertegen, en in de app Computers een paar apps op View en Use; dan als vriend (of met een tweede account) op de
      monitor klikken. Kijk of het **model van de monitor** goed staat (het voetje, het toetsenbord, het licht van het
      scherm), of de app Computers op jouw schermgrootte past, en of "view only" duidelijk genoeg is.
- [ ] **Het netwerk (hoofdstuk 38)**: gezien op screenshots (`-Ponly=network`, en showcase 101 en 102), maar niet zelf
      gespeeld. Probeer: kern + generator + netwerkkabel naar een lezer (en zonder kabel: niets); een schaar door de
      netwerkkabel; één stroomkabel op de kern en een PoE-kabel naar een camera; een poort van de switch uit; een
      netwerkknop en -hendel in een diagram. Kijk of de **kleuren** goed zijn (grijs, wit, groen; door de muur heen met de
      bril), of het **vakje** en de **gele streepjes** niet te druk of te snel zijn (de snelheid staat in
      `client/CableTraffic`, hoe vaak in `[network] flowSeconds`), en of de modellen van de knop en de hendel er goed
      uitzien (aan de muur, op de vloer, aan het plafond). Een oude wereld: zet `[network] required = false` in
      `imttech.toml`, of leg de kabels.
- [ ] **De wapens (hoofdstuk 35)**: gezien op screenshots, maar niet zelf gespeeld. Probeer vooral hoe de **geweren
      voelen** (de terugslag, hoe snel het geweer schiet, hoe groot het geweer en je armen in beeld zijn, het richten en
      de kijker van de sniper) en of de **getallen** kloppen (schade, schoten per cel, herlaadtijd: alles in `[weapons]`).
      En de **geluiden**: ik kan niet luisteren. Klinken de vier schoten, de cel eruit en erin, de grendel, de EMP-klap,
      het sissen van de rook, de knal en de piep van de flashbang goed, en niet te hard? Van de flashbang: is het witte
      scherm niet te lang, en mag het andere geluid gedempt blijven?
Sinds de screenshot-rondleiding wél gezien en in orde: de HUD (met doelanalyse), het G-menu,
alle schermen, de holotafel in alle drie de standen, het harnas op je lijf en hoe de drones en
raiddrones eruitzien.

- [ ] Hoe het vliegen *aanvoelt* (de getallen kloppen, maar dat moet je zelf proberen)
- [x] De blauwe straalvlammen onder de laarzen: gezien op 26 september (`47_flight_jets`), in orde
- [ ] Raiddrones: hoe ze bewegen en aanvallen
- [ ] De **rayshield-geluiden** (7 oktober): ik kan niet luisteren. Klinken het zoemen, "vwoem" en
      de knetter goed, en is het zoemen niet te hard of te zacht?
      Jouw reactie: het zoemen "mag zachter en minder vaak", en daarna: "het mag altijd geluid maken
      maar gewoon heel stil/subtiel". Nu: het zoemt weer aan één stuk door, maar heel stil
      (laserwand 0,07 in plaats van 0,45, koepel 0,12 in plaats van 1,0); zenders tot 8 blokken uit
      elkaar zoemen samen (een poort zoemt niet dubbel); en het geluid zelf is rustiger (bijna geen
      knetter, een tragere "wom", de hoge tonen eraf). Zelf bijstellen: `rayshieldHumVolume` in
      `config/imttech-client.toml` (1 = zo, 2 = dubbel, 0 = uit).
- [ ] **De blokjes-editor met de muis**: slepen, draadjes trekken en zoomen heb ik alleen
      nagerekend en op screenshots gezien, niet zelf met een muis gedaan.
- [ ] **Het datacenter (8 oktober)**: de knoppen van de apps Backup en Monitor heb ik alleen via de
      server getest en op screenshots gezien, niet met de muis aangeklikt. En speel zelf of de
      getallen goed voelen: hoeveel fans je nodig hebt, hoe snel het warm wordt (ongeveer een
      minuut), hoe lang I.M.T erbij nadenkt (1 CPU +8 s), en of vuur van een brandend rack niet
      te makkelijk een houten basis afbrandt. Alles staat in de config onder `[logic]`.
- [ ] **Zones en de Remote Frag (8-9 en 26 oktober)**: gezien op screenshots, niet zelf gespeeld. Kijk zelf of de
      rand niet te fel of te zwak is (vooral dichtbij, tegen je eigen muur), of de muisklikken in de app Zone goed
      werken (typen in de vakjes, de kaart), of het doorknippen goed klinkt, en of de getallen goed voelen: hoeveel
      frags een muur kost (steen 1, obsidiaan 4, de kern 8), hoe groot het gat is, en hoeveel stroom een grote zone
      kost. Op obsidiaan zie je de barsten bijna niet (zo tekent Minecraft barsten). Alles staat in de config onder
      `[breach]`, `[weapons]` en `[security]`.
- [ ] **Barsten herstellen (9 oktober)**: gezien op screenshots en in de showcase, niet zelf gespeeld.
      Luister of de Crusher (een heel zachte slijpsteen) en de Compactor (een heel zachte zuiger) goed
      klinken, en of de getallen goed voelen: 15% zwakker per keer plamuren, 5 seconden per blok in een
      machine. Een gebarsten blok van obsidiaan ziet er in je hand bijna uit als gewoon obsidiaan (net
      als de barsten in de wereld). Alles staat in de config onder `[breach]` en `[repair]`.
- [ ] **Inbreken (9 oktober)**: gezien op screenshots en in de showcase, maar niet zelf met een muis
      gespeeld. Probeer of het draaien met links- en rechtsklik, het vegen met de muisknop ingedrukt en
      het slepen in de zak goed aanvoelen, en of de getallen kloppen: de tijden van het stroomcircuit
      (60 tot 40 seconden), 1 seconde voor ALARM, de lengte + 2 pogingen bij een paneel, en hoe snel de
      meter NOTICED vol loopt (te makkelijk of te moeilijk?). Alles staat in de config onder `[bypass]`,
      behalve de meter (die staat in `client/pickpocket/PocketScreen`).
- [ ] **De draden van een laserzender (25 oktober)**: gezien op screenshots en in de showcase, niet zelf met een muis
      gespeeld. Voelt de zaklamp goed (zijn de draden in het donker te goed of te slecht te zien?), is 40 tot 60
      seconden goed, en is 1 Computer Chip voor herstellen genoeg? Alles staat in de config onder `[bypass]`; hoe donker
      de draden zijn, staat in `client/bypass/WireScreen` (`WIRE_DARK`).

Opgevallen in de showcase (door mij, op screenshots; nog uitzoeken):

- [x] De **schildkoepel** zie je van een afstand bijna niet — opgelost met de nieuwe koepel
      (`e0d2b64`).
- [x] **Illusie "cloak"**: op het screenshot was de koe in het veld nog gewoon te zien — `c615d11`
      Het veld ging naar de muur achter de koe (alleen blokken telden bij het richten), en
      raakte je de zijkant van een muur, dan kwam de drone ín de muur en stikte hij. Nu komt het
      veld bij het wezen waar je naar kijkt, of op de vrije plek voor de muur, en stikken drones
      niet meer. Twee nieuwe tests.
- [ ] Raiddrones die komen omdat je in een Biebia's Industries Tower sloopt. Dat kan de test niet nadoen:
      een testwereld weet niet dat een geplaatst gebouw een "Biebia's Industries Tower" is.
- [ ] **De Arc Lamp (9 oktober)**: gezien op screenshots en in de showcase, op een vlak veld. Kijk zelf
      in je eigen wereld (heuvels, grotten, een huis) of het licht er goed uitziet, of het opnieuw meten
      na bouwen snel genoeg gaat, en of je spel niet hapert als je de lamp aan- of uitzet. (De gloed: "mag
      volledig weg", 13 oktober, is weg.) Sinds 13 oktober ook: zet een lamp in een kamer en kijk of de
      kamers ernaast donker blijven, en of de bundel door een open deur er goed uitziet.
- [ ] **Het scherm van de laserzender (13 oktober)**: gezien op screenshots en in de showcase. Probeer zelf
      of sluipen + klikken met een lege hand fijn werkt, of je met een kabelrol nu makkelijk kabels legt,
      en of een liggende wand er goed uitziet (op de screenshots wel: `73_laser_flat`).
- [ ] **Inbraak- en uitbraakoefening (18 oktober)**: getest met 4 nieuwe tests en gezien op `tr08`-`tr09`. Probeer zelf in
      survival: zet het Start-blok op Break-in drill en breek in je eigen basis in. Schieten je torentjes, laserwanden en
      robots echt op je, zie je je zone-rand niet meer, en voelt de hack met de laptop goed? Een uitbraak: bouw een cel,
      zet het Goal-blok erbuiten en ontsnap (met vrije klimblokken of C4). Zeg het als je een gat vindt waardoor je je
      basis tijdens de oefening toch nog kunt bedienen.
- [ ] **Robots die niet meer bestaan (18 oktober)**: getest met 3 nieuwe tests en gezien op `rb26`. Probeer zelf in je
      wereld Bunker (met het nieuwe jar): ga naar je basis en open de app Robots. Na een paar seconden zijn Guard 1 tot
      en met 6 weg (het logboek van je kern zegt het per robot), en Guard 7, 8 en 9 blijven. Pak er een op met Pick up
      en zet hem terug: hij houdt zijn naam.
- [ ] **Training (16 oktober)**: gezien op screenshots (`tr01`-`tr07`) en in de showcase (87), getest met 9 nieuwe
      tests. Niet gehoord: de "zap" van een treffer, het aftellen en de "go!" (tikjes van een nootblok). Probeer zelf
      met een vriend als trainee (of jezelf, in survival): of 1 seconde trager en 1 seconde zonder nieuwe treffer goed
      voelen (`slowTicks`, `hitCooldown`), of je de blauwe flits rond je lijf nog genoeg ziet, of het knipperen van een
      lamp aan het End-blok duidelijk is (`endPulse`), en of de golven met `squad_down` lekker lopen. Ook: het
      instelscherm (Enter in het naamvak) en de mini-editor van End met de muis.
- [ ] **Security drones (16 oktober)**: gezien op screenshots (`sd01`-`sd11`, `t25`) en in de showcase (86 en 87),
      getest met 8 nieuwe tests. Niet gehoord: de rotors (sinds 19 oktober een eigen, heel zacht "brrr": is het stil
      genoeg, of juist te stil? `droneRotorVolume`; bij aan- en uitzetten nog de servo's van een robot) en het zachte
      laadzoemen op het dock. Probeer zelf: hoe het vliegen
      eruitziet (3 blokken per seconde, `droneSpeed`; blok voor blok met een klein stopje in elk midden), opstijgen
      en landen op het dock (ook als je hem uitzet terwijl hij ver weg is), `goto` rond een muur, een drone met
      lege batterij die valt, en of Biebia's drones in de hangar van een echte toren lastig genoeg (of te lastig)
      zijn. Ook: een drone op een dock van een vriend zetten (in zijn basis, als hij je vertrouwt).
- [ ] **Zien en schieten (16 oktober)**: gezien op screenshots (`rb20`-`rb25`) en in de showcase (85), getest met 7
      nieuwe tests. Probeer zelf: of de laser en het geweer goed voelen (schade, hoe snel, de geluiden "pew" en de
      knal: die heb ik niet gehoord), of een robot je echt niet door een muur ziet, de Ion Blaster op een robot die
      loopt (valt hij mooi neer? staat hij na een minuut weer op?), en het beeld van een robotcamera als hij loopt.
- [ ] **Robots programmeren (16 oktober)**: gezien op screenshots (`l16`-`l19`, `rb13`, `rb18`, `rb19`) en in de
      showcase (84), getest met 16 nieuwe tests. Probeer zelf: hoe het lopen eruitziet (blok voor blok, met een
      klein rukje als hij precies in het midden gaat staan: te snel of te traag? zie `walkFactor`), of
      `charge()` netjes zijn dock in en uit loopt vanaf verschillende plekken, en of een opdracht van de server
      (`order`) lekker voelt (hij stopt midden in zijn rondje). Ook: een robot die tegen een speler of dier
      aanloopt (na 2 seconden `robot.blocked`).
- [ ] **Solderen (16 oktober)**: gezien op screenshots (`rb14`-`rb17`) en in de showcase (83), getest met 4 nieuwe
      tests. Probeer zelf met de muis: een module slepen en vasthouden (voelt de hittebalk goed? is het groene vak
      te makkelijk of te moeilijk? zie `solderCold`/`solderHot`), of je een brug zonder Inspect echt over het
      hoofd ziet, rechtsklik om los te solderen, en de knal bij een kortsluiting (die heb ik niet gehoord).
- [ ] **Guard robots (16 oktober)**: gezien op screenshots (`rb01`-`rb13`) en in de showcase (82), getest met 15
      nieuwe tests. Niet gehoord: luister zelf naar de servo-stapjes, opstarten en uitschakelen, de klang als je
      hem slaat, en het laadzoemen op het dock (is het stil genoeg? `robotHumVolume`). Probeer zelf: slepen in de
      Robot Assembler (shift-klik zet een onderdeel in zijn eigen vakje), het draaiende voorbeeld, een robot op
      een dock zetten, aan (stapt hij mooi uit?) en uit (loopt hij terug en gaat hij er netjes in staan, ook
      als je hem eerst ver weg zet?), het robotscherm (Enter in het naamvak), en de app Robots.
- [ ] **De taal groeit (15 oktober)**: gezien op screenshots (`l12`-`l15`), getest met 8 nieuwe tests. Probeer
      zelf met de muis: het palet scrollen met het muiswiel, een SET-blokje met zijn vak Value, een lus-blokje
      met "do" en "after" verbinden, en of de fouten in begrijpelijk Engels staan als je iets verkeerd typt
      (`count has no value yet`, `break only works inside ...`).
- [ ] **Relaisgroepen (14 oktober)**: gezien op screenshots (`rl03`-`rl061`) en in de showcase. Probeer zelf
      of slepen in de app Groups op de desktop fijn werkt (ook met veel relais, als je moet scrollen). Sinds
      16 oktober stel je groepen alleen daar in.
- [ ] **Het naamscherm van de keycardlezer en het codepaneel (13 oktober)**: gezien op screenshots
      (`dr4`-`dr7`) en in de showcase. Probeer zelf of sluipen + rechtsklik met een lege hand fijn werkt, ook
      met een vriend in je Access en met een vreemde (die hoort bij een lezer "This keycard reader belongs to
      ...", en krijgt bij een paneel gewoon de toetsen).
- [ ] **Keycard-groepen (25 oktober)**: gezien op screenshots (`y05`-`y08`, `bp02`, `bp07`, `bp19`, `l01`). Probeer
      zelf of slepen in de app Keycards fijn werkt (een kaart op een groep, een groep op een groep, ook met veel
      kaarten of groepen, als je moet scrollen), en of de drie nieuwe kaartkleuren (rood, oranje, zwart) er in je
      hand goed uitzien.

---

## Open vragen

- [x] **Moeten de getallen uit `ImtBalance.java` naar een configbestand?** Ja, per wereld
      (jouw keuze op 26 september) — `d26a25e`
      120 getallen staan nu in `config/imttech-balance.toml` (kosten, schade, bereik,
      wachttijden, verbruik; per kopje: bril, drones, satelliet, harnas, torentjes, Raven...).
      Het bestand geldt voor al je werelden; een kopie in `<wereld>/serverconfig/` geldt alleen
      voor die wereld (zo werkt NeoForge 21.1; ook de raid-instellingen staan zo). Niet erin:
      wat bij het opstarten vastligt (bescherming van het harnas, levens van wezens) en de
      natuurkunde van het vliegen. Eén nieuwe test.
- [x] **Wil je bij elke nieuwe functie automatische tests?** (mijn invulling: ja, ik schrijf ze
      mee; er zijn er nu 76, en ze vingen deze week o.a. een lege zwarte doos en een stikkende
      drone)
- [x] Moeten de Nederlandse foutmeldingen in de gametests ook Engels? (mijn invulling: nee, die
      zie je alleen tijdens het ontwikkelen, en Nederlands leest voor jou makkelijker)
- [x] **Nieuwe namen voor wat nog van Marvel of Star Wars komt?** (17 oktober) Jouw keuzes:
      Bibie wordt Biebia; Hydra wordt Raven (mijn keuze: zwart met rood, zoals een raaf); de
      repulsor wordt de laser cannon; JARVIS, FRIDAY en KAREN in de hackterminal worden BiTech
      (Biebia Technologie); Happy, Pepper (Potts) en Rhodey worden Seppe, Lotte en Jens (mijn
      keuze); Domino squad wordt Rookie squad en Kamino Bootcamp (mijn keuze); de prestatie
      "You shall not pass" heet nu "No trespassing". MALIBU en SHIELD in de wachtwoordlijst werden
      TUNNEL en BUNKER. Rayshield, de robotstijlen en de Ion Blaster blijven zo.
- [ ] **Het harnas is rood en goud, de kleuren van Iron Man.** Andere kleuren? (nog niet gevraagd)

---

## Ideeën die we nog niet gekozen hebben

Wel besproken, maar niet ingepland. Hier zodat ze niet verdwijnen.

**Gevecht**
- Laserkanon-handschoen als los item (nu zit de laser cannon in de borstplaat)
- ~~Mini-raketten die hun doel volgen~~ (gebouwd: `c030a4a`)
- "Veronica": onderdelen van een harnas die naar je toe vliegen
- ~~Drone-formaties (schild, pijl, cirkel)~~ (gebouwd: `065664e`)
- Het harnas: onder water ademen

**Basis en techniek**
- ~~Een werkbank die harnasdelen maakt~~ (gebouwd: de Suit Workbench, `61fde87`)
- Een I.M.T-AI-kern die passief onderzoek doet
- ~~Automatische torentjes die aan je relais-groepen hangen~~ (gebouwd: `53ff0b9`, ze staan in je redstone-lijst)
- ~~Energie-netwerk: kabels of een draadloos I.M.T-net~~ (gebouwd: de stroomkabels)
- ~~Holotafel: een stand die de ertsen in de buurt laat zien~~ (gebouwd: `6cd20ed`)

**Sfeer**
- Eigen geluiden. Alle 18 geluiden in `sounds.json` zijn nu vanilla-geluiden onder een andere
  naam (`beacon.activate`, `note_block.pling`). Echte I.M.T-tonen zouden veel schelen.
- Betere foutmeldingen bij Spotify en de stem-installatie

**Wereld**
- ~~Een tweede soort structuur met de onderdelen voor de volgende fase~~ (gebouwd: de Biebia's Industries-bunker, `163771b`)

---

## Besloten om niet te doen

- **Linux en Mac ondersteunen.** Alleen Windows; de stem en de AI draaien op Windows-programma's.
- **De mod publiceren.** Blijft voorlopig privé.
- **Naar een nieuwere Minecraft-versie.** Voorlopig 1.21.1.
