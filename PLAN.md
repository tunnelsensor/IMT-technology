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

**Stand van zaken:** 243 automatische tests (`gradlew runGameTestServer`). Met de oude stroomwaarden
allemaal groen; met jouw nieuwe (zonnepaneel 0.3, warmte 0.7 per tick) 3 rood (zie hoofdstuk 14, stap 2).
Alles zelf bekijken: `python tools/showcase.py` laat alle 89 functies één voor één zien (of een
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
      De aarde met de Stark-satelliet en al je drones in hun baan, als echte modellen met hun
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
      hem toch met E.D.I.T.H. schakelen: zet er een relais naast. Oude laserpoorten verdwijnen
      vanzelf uit je relaislijst. Nagekeken op screenshots (`-Ponly=laser`), ook van boven.
- [x] De illusie "wereld kopiëren" weghalen ("cloak" blijft) — `ab4ab2d`
- [x] Repulsor: niet bij elk schot "hit 1" zeggen of tonen; alleen geluid — `c7c7e7a`
- [x] Raiddrones iets minder sterk: minder schade, minder leven en trager schieten — `bc46a61`
      3 schade (was 5), 10 leven (was 16), om de 2 seconden (was 1,25).
- [x] G > Systems > Commands: bij een nieuw commando staan de acties over elkaar of buiten
      beeld; een echte scrollbalk erbij — `a8b14b5`
      Past het gekozen commando niet, dan scrolt de rechterkant (wieltje of balk slepen); de
      lijst links heeft ook een balk. Nagekeken met vier acties op een klein scherm
      (`-Ponly=commands`).

**Geluid en opstarten**
- [x] De bevestigingsgeluiden (zoals "aangekomen op bestemming") klinken lelijk: eigen
      E.D.I.T.H.-tonen, en meteen ook de andere geleende Minecraft-geluiden vervangen — `270ae6b`
      Alle 17 geleende geluiden zijn nu eigen geluiden (`tools/generate_sounds.py`). Door jou
      beluisterd: "voorlopig goed".
- [x] Opstartanimatie: een laadbalk, duidelijk "E.D.I.T.H." in beeld, de bril zegt iets, en
      je identiteit wordt gecontroleerd — `6761fd5`, geluid `668fabb`
      Groot logo in reactorringen, een laadbalk met wat er geladen wordt (je echte drones en
      batterij), dan scant hij je eigen gezicht terwijl de balk op 90% wacht. Bevestigd: groen,
      je naam, en E.D.I.T.H. zegt haar welkomstzin (die werd vroeger ongezien meteen gezegd).
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
      De Stark-kabel (koper, redstone, koper = 8 kabels) sluit vanzelf aan op kabels en
      apparaten; een apparaat dat direct tegen een generator staat, doet ook mee. Het
      laadstation haalt alleen stroom voor een bril die nog niet vol is (anders zoog het het
      net leeg). Een holotafel zonder stroom toont alleen een knipperend rood ringetje.
      De **Stark Tower** heeft nu een verborgen krachtcentrale: een dichtgemetseld blok op de
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
- [x] Repulsor: de hand beweegt en gloeit bij een schot, en het schot is een eigen, gedetailleerd
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
      Een eigen model bovenop het gewone harnas (`client/suit/StarkSuitModel.java`), met de
      plaatjes nu 128x64 zodat de extra stukken een plek hebben: een uitstekende reactor die
      blauw gloeit (ook in het donker), borstplaten, drie gouden buikplaten, een rugplaat met
      twee straalopeningen, schouderplaten die iets naar buiten kantelen, onderarmbeschermers
      met een repulsor in de handpalm, laarzen met een schaal, gouden neus en zool, en aan de
      broek kniestukken, dijplaten en een gesp. Je eigen arm (repulsor) toont de harnasmouw
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
- [x] Stark Tower mooier, overal meer detail — `23d7f84`
      Onderweg gevonden en gerepareerd: het STARK-logo miste de bovenste rij van elke letter
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
      zich een hut in), "Stark Tech items" (het creative-tabblad) en het persoonlijke schild
      vangt nu echt pijlen op. De sweep kiest een begin en eind, het leger valt Happy aan.
      Onderweg gevonden: Happy ging dood door raiddrones (en was daarna niet meer te hacken of af
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
      COMBAT (repulsor, raketten, rayshield) · SATELLITE & ARSENAL (precisie-straal,
      bombardement en schuivende laser elk apart: kiezen = die stand en meteen vuren, LOCKED
      als hij nog op slot zit, PICK END als de laser op zijn eindpunt wacht; plus het
      satellietscherm en het arsenaal) · DRONES (zwerm, één drone, escorte, vorm, stand down,
      markeringen wissen) · RECON DRONES (camera, spion, meekijken, rapport) · TRANSPORT ·
      TECH · ILLUSION · INFORMATION (ertsen, dreigingen, doelanalyse) · NAVIGATION · SYSTEMS
      (nu ook nachtzicht). Cijfers 1-9 en 0 springen naar een tabblad. Eén nieuwe test.
      (mijn invulling) Het tweede, lege puntje onder Satellite & Arsenal: het satellietscherm
      en het arsenaal.

### 4. Meer in de wereld

- [x] Meer structuren (het satellietwrak en de Hydra-bunker)
  - [x] **Neergestorte Stark-satelliet** — `2510e03`, controle `4629359`
        Een oude Stark-satelliet uit zijn baan gevallen: een krater van zo'n 25 blokken met
        verschroeide grond, opgeworpen aarde en de voor waar hij binnenkwam; in het midden de
        opengescheurde romp (de reactor gloeit nog), een zonnepaneel dat schuin omhoog steekt,
        een afgebroken paneel, de schotel op de helling en rookende brokstukken. In de romp de
        **zwarte doos** met wat de satelliet-upgrades nodig hebben (netherite, diamanten, goud,
        Stark-chips, soms een upgrade of drone), eromheen kratten met schroot. In vlaktes,
        woestijnen, savannes, badlands, bossen, taiga's en sneeuwvlaktes; hij ligt 8 blokken in
        de grond, zodat de krater echt is uitgegraven. **Bewaakt**: kom je er met de bril, dan
        duiken er raiddrones op je af (hooguit eens per 10 minuten). Bouwscript
        `tools/generate_crash_site.py`, screenshots `-Ponly=crash`, showcase "Crashed satellite".
        Nieuw: `gradlew runStructureCheck` kijkt in een echte wereld of de structuren er komen
        (beide gevonden, met al hun kisten, in een savanne en een berkenbos).
        Aannames (zeg het als je het anders wilt): de buit, hoe vaak hij voorkomt (ongeveer even
        vaak als de toren) en de raiddrones als bewaking.
  - [x] **Hydra-bunker, in een berg** (Winter Soldier-stijl) — `163771b`
        Een eigen structuursoort (HydraBunkerStructure) zoekt in bergbiomen een rotswand: voor de
        deur vlak en open (16 blokken uitzicht, dus geen kuil of grot), achter de deur over de
        hele bunker minstens 9 blokken berg. De bunker draait mee zodat de ingang naar buiten
        kijkt. Binnen (tools/generate_bunker.py): gevel met embleem en twee torentjes, gang met
        controlepost, grote hal, slaapzaal, reactorkamer (generatoren in lava, kabel over het
        plafond), kluisgang met twee laserwanden van 3 x 3 die uitgaan zonder reactor, en de
        kluis (altijd 2-3 Hydra-onderdelen, cryo-cel). Acht soldaten (geen spawner, dus niet te
        farmen) en vijf Hydra-torentjes. Vinden: eerst lag er een kaart "Hydra Intel" in de zwarte
        doos van een satellietwrak; die heb jij er op 26 september uitgehaald (`29a674c`). In drie
        testwerelden lag de dichtstbijzijnde bunker op 0,7 tot 3 km. Drie nieuwe tests
        (kluis en bewakers, lasers en reactor, alle buittabellen laden).
        (mijn invulling) De indeling, en dat soldaten niet terugkomen.
- [x] **De Stark Tower verder uitbreiden** (jouw keuzes op 26 september), in deze volgorde:
  - [x] **Beveiliging die leeft** — `af0d24f`
        Een Stark Security Core op de beveiligingsverdieping (in de rij serverkasten) beschermt
        de hele toren. Beveiligingstorentjes (wit met rood) bij de lift, op de
        beveiligingsverdieping en in de kluis (in plaats van de vier spawners), en vier
        beveiligingsdrones die rond de kern patrouilleren; een neergeschoten drone vervangt de
        kern na 45 s. Zolang niemand de kern hackt, is iedereen een indringer (behalve in
        creative); daarna laten torentjes, drones én laserwanden de eigenaar en zijn team met
        rust. De drones zijn geen "monsters", zodat je eigen torentjes en escorte ze na de hack
        niet neerschieten. Drie nieuwe tests.
  - [x] **De toren overnemen met een laptop** (jouw idee) — `9cbf5ab`
        Stark Laptop (zwart glas, redstone, Stark-chip, ijzer; geen bril nodig): klik op de kern,
        het alarm gaat af en alle drones komen op je af. De minigame heeft drie lagen (Firewall,
        Encryption, Override; 3, 4 en 5 treffers): spatie of klik als de heen-en-weer schuivende
        cursor in het groene vak staat, elke laag sneller met een kleiner vak; mis = een treffer
        kwijt. Het spel loopt door, dus je wordt beschoten. Weglopen (> 6 blokken), doodgaan of
        Esc breekt de hack af; de server keurt een onmogelijk snelle hack af. Daarna is de toren
        van jou. Showcase: "Stark Tower: security and hack". Drie nieuwe tests.
        (mijn invulling) De vorm van de minigame (timing met een balk).
  - [x] **Tony's lab** met een harnas-werkbank — `61fde87`
        De Suit Workbench (op stroom van het net): negen vakken voor materialen, knoppen om een
        borstplaat, broek of laarzen te bouwen (zelfde materialen als het oude recept; de muis op
        de knop laat zien wat het kost), en een vak voor een stuk harnas plus een upgrade. Upgrades:
        Thruster Upgrade (laarzen, tot 3: sneller en zuiniger vliegen), Armor Plating (elk stuk, tot
        2: meer schade weg), Repulsor Capacitor (borstplaat, tot 2: sterkere repulsor), en het
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
        erop met de stuwraketten aan, dan zegt E.D.I.T.H. "Welcome home"; zolang je erop staat,
        laadt je bril op en wordt je harnas gerepareerd. Je platforms worden per speler onthouden,
        dus de weg naar huis werkt ook van ver. Showcase: "Stark Tower: hangar and landing pad".
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
- [x] **Meer high tech**, in deze volgorde (eerst wat meteen kan, dan de Hydra-tech):
  - [x] **Holotafel: ertsen** — een vijfde stand die de ertsen onder de tafel laat zien — `6cd20ed`
        Tot 16 blokken opzij en 48 diep, als gekleurde blokjes in een doorzichtig blok aarde,
        in de kleuren van de ertsscanner (diamant en smaragd knipperen, kolen en koper zwakker).
        Het holotafelscherm zet de vijf standen nu in twee rijen, anders pasten de namen niet.
  - [x] **Drone-formaties** — je escorte in een vorm: schild om je heen, pijl, cirkel — `065664e`
        G > Drones > Formation wisselt tussen Orbit (zoals altijd), Shield (snel draaiende ring
        dicht om je heen), Arrow (een V schuin achter je die meedraait) en Circle (wijde ring).
        In Shield en Arrow blijven ze in formatie als ze schieten; de bril onthoudt de keuze.
  - [x] **Hydra-onderdeel** (het item uit de bunker) en **Hydra-soldaten** — `f4888be`
        Een nieuw vijandig wezen: zwart tactisch pak, rode bril en het rode embleem. Ziet hij
        je, dan legt hij zijn lasergeweer aan en schiet hij rode laserstralen (4 schade, om de
        ~2 seconden, tot 18 blokken; een rayshield houdt ze tegen). Hij schiet ook op je drones
        en roept zijn maten als je hem raakt. Het **Hydra-onderdeel** valt soms (7%) als jij er
        een verslaat; in de bunker ligt het zeker. Er is een spawn-ei. Eén nieuwe test.
  - [x] **Mini-raketten** uit het harnas — `c030a4a`
        Toets X (of G > Gevecht, of "fire rockets"): zes raketten uit twee buizen op je rug,
        verdeeld over je gemarkeerde doelwitten (anders wat je aankijkt, anders de plek waar je
        kijkt). Ze vliegen eerst omhoog, sturen dan bij (dichtbij steeds scherper) en ontploffen
        zonder blokken te slopen; jou en je team raken ze nooit. Hele harnas nodig, 900 energie,
        12 s herladen. Het **raketpakket** (Hydra-onderdeel, Stark-chip, buskruit, ijzer) klik je
        met de borstplaat aan op die borstplaat; dan zie je de buizen op je rug. Twee nieuwe tests.
  - [x] **Automatische torentjes** — `53ff0b9`
        Stark Turret (Hydra-onderdeel, 2 Stark-chips, dispenser, observer, ijzer): een draaiende
        kop die op monsters, raiddrones en Hydra-soldaten richt (spelers alleen met PvP, nooit
        jij of je team) en op hetzelfde doelwit blijft tot het neer is. Laser: 16 blokken, 4
        schade per seconde, 60 stroom per schot van het net. Raketten: Rocket Ammo (rechtsklik of
        trechter), om de 2,5 s, tot 24 blokken. Redstone zet hem uit (de loop hangt omlaag), en
        hij staat in de redstone-lijst van E.D.I.T.H. onder Turrets. Rechtsklik = status, shift =
        raketten eruit. Hydra Turret: zwart/rood, geen stroom nodig, schiet op spelers en drones;
        gesloopt geeft hij ijzer en soms een Hydra-onderdeel. Vier nieuwe tests.
  - [x] Daarna de bunker (zie hierboven), met de soldaten en torentjes erin

### 5. De AI-stem (als laatste)

Alles hieronder zit in `1e91264` (showcase: "AI Brain", `52bc4eb`). Twee nieuwe tests, en een
visuele check (`runVisualCheck -Ponly=brain`) die zinnen naar het echte lokale model stuurt.

- [x] **Drie standen om uit te kiezen** in **G > Systems > AI Brain**: Phrase book (het
      woordenboek), Local model, of Claude
- [x] Ze **praat én doet dingen**: gewone taal wordt een antwoord en/of acties. Beide breinen
      krijgen dezelfde instructies (assets/starktech/edith/brain_prompt.txt), je toestand (leven,
      batterij, tijd, weer, plek, vijanden, waar je naar kijkt, route, waypoints, relais) en wat ze
      onthouden heeft, en moeten één vast JSON-antwoord geven (het model kán niets anders
      schrijven). Lukt het brein niet (geen internet, geen sleutel, model start niet), dan zegt ze
      dat en gebruikt ze het woordenboek.
- [x] De API-sleutel vul je **in het spel** in (sterretjes; bewaard in
      .minecraft/starktech/ai/claude-key.txt, gaat alleen naar Anthropic). Claude loopt via de
      officiële Anthropic-SDK, model claude-opus-5 (te veranderen in config/starktech-client.toml),
      met "effort low" (snel), prompt caching en een automatische fallback als Claude een vraag
      weigert. Getest tot en met het inloggen (een verkeerde sleutel geeft netjes "Claude did not
      accept the API key"); met een echte sleutel nog niet, want dat kost geld: probeer jij het.
- [x] De mod kiest aan de hand van je pc welk lokaal model hij downloadt: Qwen3.5 2B, 4B of 9B,
      naar het geheugen van je videokaart (jouw pc: 4B, 2,7 GB; ± 1 seconde per zin). De vorige
      keer (19 september) vond je 2B "nog niet zo slim"; 4B deed het in de test goed.
- [x] Ze **onthoudt dingen permanent** ("onthoud dat mijn basis Stark Hoeve heet" → later "hoe
      heet mijn basis?" → "Je basis heet Stark Hoeve"), in .minecraft/starktech/ai/memory.json;
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
- [x] Staat "Voice text" uit, dan toont E.D.I.T.H. **geen enkele** tekst meer (ook niet "Target 1
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
      schade, ook de splash). Inbouwen aan de werkbank; recepten met Stark-chips, ijzer en buskruit
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
- [x] Hydra-soldaten slimmer: samen aanvallen (niet allemaal recht op je af), en zit je achter een
      muur, dan zoeken ze een weg naar je toe in plaats van te blijven staan — `7ac5399`
      Een eigen gevechts-AI (`HydraCombatGoal`): soldaten met hetzelfde doelwit vormen een ploeg.
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
      Stark-, Hydra- en beveiligingstorentjes: zonder stroom hangt de loop omlaag en valt er geen
      schot; elk laserschot (60) en elke raket (20) kost stroom. De beveiligingskern van de toren
      ook: zonder stroom geen drones, en de drones zakken met gedoofde lampjes naar de grond.
- [x] Een elektriciteitskast waar kabels samenkomen, met schakelaars om stroomgroepen aan en uit te
      zetten; ook te bedienen via het redstone-tabblad (op afstand alleen door de eigenaar). Wie
      bij de kast staat kan hem bedienen, dus in de toren en de bunker staat hij in een zwaar
      bewaakte, dichte kamer. Een kabel doorknippen zet een deel van de stroom uit. Jouw keuze.
      — `744a668`
      De **Power Cabinet**: een schakelaar per kant (voor, achter, links, rechts, boven, onder);
      het net loopt alleen door kanten die aan staan. Rechtsklik = het scherm; "Link to E.D.I.T.H."
      (alleen de eigenaar) zet de zes schakelaars in het redstone-tabblad, ook met je stem.
      *Toren:* de krachtcentrale kreeg een mantel van obsidiaan met de kast erin: één kant voor
      de lasers en de kluis (met de vier kluistorentjes), één voor de kern en alle andere
      torentjes (de lobby via de koperen zuil van de lift). Twee torentjes erbij op de
      beveiligingsverdieping (één bij de krachtcentrale, één bij de lift; er stond er een ín
      de krachtcentrale, die was al die tijd weg). *Bunker:* de kast in de hoek van de
      reactorkamer, met een torentje en een soldaat ernaast; de lasers aan de ene kant, alle
      zes Hydra-torentjes (door de vloer) aan de andere.
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
      README is een briefje van Tony met aanwijzingen (config in /etc, nep-namen in `ps`, override
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
      IP (10.x.y.z, uniek in de wereld; een nieuw bestand `starktech_network.dat` onthoudt welk
      IP bij welke kern hoort), SSH-poort, naam (`stark-sec-core` in de toren), het wachtwoord
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
- [x] **4. De Stark OS-desktop** — `78916d6` (met de toegang/gegevens eronder: `20d2b34`)
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
      een regel in het logboek, een E.D.I.T.H.-alarm voor de eigenaar, de lezer vliegt eruit en is
      30 s buitengesloten (`TRAP_LOCKOUT` in de config), en de beveiliging jaagt dan op hem, ook
      als "attack players" uit staat. Alleen de eigenaar ziet welk bestand een valstrik is. Fouten
      en meldingen staan nu rechts in de taakbalk (het actiebalk-bericht zag je niet achter de
      desktop). Screenshots: `-Ponly=os`. Eén nieuwe test.
      De terminal-commando's (`mkdir`, `mv`, `rm`, `nano`) horen bij de geparkeerde terminal (stap 2). Commit: `eb241c4`.
- [x] **6. Apparaten en toegang, en de kern zelf bouwen** (de gegevens eronder: `20d2b34`)
      Twee nieuwe apps. **Devices** toont wat bij de kern hoort: de beveiligingstorentjes in het
      gebied en de Stark-torentjes van de eigenaar (aan/uit per torentje, stroom, raketten, doelwit),
      de drones (levens, wat ze doen) en de laserpoorten; bovenaan "Drones" (uit = ze landen naast
      de kern en er komen er geen nieuwe) en "Lasers" (uit = alle laserpoorten in het gebied gaan
      open, ook al staat er redstone op). **Access**: "Attack monsters", "Attack other players" en
      de vertrouwde spelers (toevoegen op naam, weghalen). Een Stark-torentje van de eigenaar in zijn
      eigen gebied volgt nu ook die regels. **Zelf bouwen**: een recept voor de Security Core
      (ijzerblokken, Stark-chip, observers, Hydra-circuit, redstoneblok); neerzetten maakt hem van
      jou met een IP, en de desktop opent dan meteen Settings om een wachtwoord te zetten. Een kern
      waarvan het gebied een ander netwerk overlapt (binnen 60 blokken) lukt niet: "too close to
      another security network", en je houdt het item. Afbreken geeft de kern terug.
      Screenshots: `-Ponly=os`. Twee nieuwe tests. Commit: `0cdbeca`.
- [x] **7. Security Camera en de bewakingsmuur** (in de buurt)
      Een nieuw blok: de **Security Camera** (recept: ijzer, glas, observer, Stark-chip; 2 stuks), aan
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
      5 s weggegooid; instelbaar in `config/starktech-client.toml`. Devices kreeg de schakelaar
      "Cameras". Nieuw: een access transformer (`META-INF/accesstransformer.cfg`). Met
      "Fabulous!"-graphics zie je geen water/glas in het beeld (dat tekent Minecraft dan apart).
      Screenshots: `-Ponly=cameras`. Twee nieuwe tests. Commit: `33883fe`.
- [x] **8. Echte camera's in de toren**
      De drie nep-camera's (observers) op de beveiligingsverdieping zijn echte camera's aan het
      plafond (45 graden naar de kamer gedraaid), en er hangen er drie bij: "Lobby" (op de
      logowand, met zicht op de receptie en de ingang), "Laser hall" (aan het plafond van de
      lasergang) en "Vault" (boven het luik, met zicht op de sokkel). Elk hangt met een verborgen
      kabel aan het net van de toren (de lobby aan de beveiliging, de rest aan de lasers: knip je
      die door, dan geven ook de camera's NO SIGNAL). Tony's kern heeft nog steeds geen wachtwoord.
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
- [x] **Showcase**: twee nieuwe functies na "Stark Tower: security and hack": "Stark OS desktop"
      (Files met security.conf, het rechtsklik-menu, Notes, Devices, Access, Logs) en "Security
      cameras" (de bewakingsmuur van de toren, het grote beeld, draaien, NO SIGNAL, een camera van
      dichtbij). Ze staan in de showcase als NIEUW.

### 8. Redstone programmeren op een server (7 oktober)

Jouw wensen: je redstone programmeren in een diagram (een relais gaat aan, dan gaat een ander
relais aan; met if/else); een keycardlezer of codepaneel start iets, en een centraal systeem
kijkt wat er moet gebeuren. Dat systeem draait op een **server**: een multiblok van
**serverracks** die je naast of op elkaar zet. In de racks steek je **CPU, RAM en opslag**, zodat
je meer kunt programmeren en je redstone sneller reageert. De server hangt aan je eigen
beveiliging (Stark OS). En de rayshields krijgen **Star Wars-geluiden**.

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
      Een nieuw blok, de **Server Rack** (recept: ijzer, ijzeren tralies, Stark-chip, redstone), met
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
      **Keycard** (recept: Stark-chip, 2 papier, ijzer = 4 lege kaarten). Nieuwe app **Keycards** op de
      desktop: naam en niveau (1-5) invullen, "Write card" schrijft de eerste lege kaart in je
      rugzak; hij krijgt het volgende nummer (#1001, #1002...) en de kleur van zijn niveau (wit,
      groen, blauw, paars, goud). Daaronder alle kaarten van dit netwerk met **Block/Unblock**. Een
      kaart is alleen geldig in het netwerk dat hem schreef, als hij niet geblokkeerd is. De kaarten
      staan in de kern (dus wie je kern overneemt, krijgt de lijst ook). De **Keycard Reader**
      (recept: ijzer, glas, redstone, Stark-chip = 2 stuks) hangt aan de muur, krijgt een naam
      ("Reader 1", of uit een aambeeld) en heeft een lampje: oranje = gelezen, groen = goed, rood =
      geweigerd of de server draait niet. Elke gelezen kaart komt in het logboek. Screenshots:
      `-Ponly=keycards`. Eén nieuwe test. Commit: `68de29b`.
- [x] **4. Het codepaneel**
      De **Code Panel** (recept: ijzer, stenen knoppen, redstone, Stark-chip = 2 stuks) hangt aan de
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
      rood en E.D.I.T.H. zegt "Someone tried the lab door", de code 4321 zet de lamp aan; daarna de
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
ander datacenter, anders ben je bij een brand je hele config kwijt. En het **brein van E.D.I.T.H.**
draait op de server: een **GPU** in plaats van een CPU is dubbel zo snel voor het brein, maar half
zo snel voor je redstone (Logic). Zo moet je goed nadenken over wat je in je racks steekt, of een
heel groot datacenter bouwen. Het brein zelf werkt zoals nu; het datacenter zet er alleen een
vertraging bij zolang het nog niet sterk is.

Jouw antwoorden:

- **Fans** gaan in de gewone vakjes, maar een rack krijgt er **6 in plaats van 4**: twee kolommen
  van drie.
- **Te warm** gaat stap voor stap: vanaf **70°** trager en E.D.I.T.H. waarschuwt; vanaf **90°**
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
      goud, redstone, 2 Stark-chips en een diamant) en de **Server Fan** (ijzer, ijzeren tralies en
      redstone = 2 fans; koelen doet hij vanaf stap 2). De tooltip zegt hoeveel een CPU of GPU telt
      voor Logic en voor het brein. Een server draait nu met een CPU **of** een GPU. Het rackscherm
      is wat hoger en toont ook GPU's, fans en "E.D.I.T.H. +2.0 s" (hoe lang het brein op dit
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
      E.D.I.T.H. waarschuwt de eigenaar één keer (bij 70°, bij 90°, en als het weer koel is,
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
      weg, en E.D.I.T.H. zegt welk. Blijft het **10 seconden boven 110°**, dan vat het heetste rack
      vlam (het rack dat de meeste warmte maakt): vuur erop en ernaast, vlammen en rook, en het scherm
      van het rack knippert "ON FIRE! Take your parts out: gone in 18 s." Na **10 seconden** is het
      weg, met alles wat er nog in zit (er valt niets uit; op zijn plek brandt nog wat vuur). Is het
      daarna nog steeds te heet, dan is het volgende rack aan de beurt. Gaat er een **SSD** kapot of
      verbrandt hij, dan zijn alle diagrammen en keycards van de server weg (alarm in het logboek en
      van E.D.I.T.H.); kaartnummers komen ook dan nooit terug. Een rack afbreken of een SSD eruit
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
      niet, dan zegt hij waarom (automatisch: één keer, ook door E.D.I.T.H., en over een minuut
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
      creative) werkt er geen enkel brein: druk je op de spraaktoets, dan zegt E.D.I.T.H. "I can't
      think without a datacenter..." en luistert ze niet; ze zegt ook uit zichzelf niets meer. Met
      een datacenter: de tijd van het model + de vertraging van je datacenter (1 CPU +8 s, 1 GPU
      +4 s, warm twee keer zo lang; vanaf 8 GPU's niets), ook bij het woordenboek; in de HUD staat
      zolang "THINKING...". **In creative** heb je geen datacenter nodig en is er geen vertraging.
      Terwijl ze nadenkt, zegt je spel dat tegen de server: dan werken de CPU's en GPU's van dat
      datacenter op vol (BRAIN 100% in de Monitor, met "E.D.I.T.H. thinking (naam)", en het wordt
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
      alleen de CPU's en GPU's in dat rack trager worden. E.D.I.T.H. en het logboek zeggen nu ook
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
  zone, dan zegt **E.D.I.T.H.** waar (aan jou en je vertrouwde spelers). De timer piept steeds
  sneller.
- **Alleen plaatsen en breken** is beveiligd: "zo realistisch mogelijk: in het echt kun je moeilijk
  inbreken, maar als je binnen bent kun je alles opendoen en stelen." Kisten, deuren, knoppen, item
  frames en dieren blijven dus voor iedereen bruikbaar. (Tip: een houten deur is geen slot; een
  ijzeren deur met een keycard-lezer of codepaneel aan de buitenkant wel.)
- **Iedereen mag plaatsen en weer weghalen**: ladders, scaffolding, klimplanten (vines, twisting en
  weeping vines) en fakkels en lantaarns. C4 kan altijd.
- **De rand zien alleen de eigenaar en zijn vrienden** (team en vertrouwde spelers). Anderen zien
  niets; ze merken het pas als een blok niet breekt.
- **C4 werkt altijd**, ook als de eigenaar offline is. Logt hij weer in, dan vertelt E.D.I.T.H. wat
  er gebeurd is.

Mijn invulling (zeg het als je iets anders wil):

- **Vrienden** zijn dezelfde als bij de torentjes: de eigenaar, zijn team en zijn vertrouwde
  spelers. Wie in **creative** speelt, mag altijd (anders kan ik de showcase niet bouwen). Alleen de
  eigenaar verandert de zone (op de desktop, of via SSH met het wachtwoord).
- **Hoe groot**: hooguit 16 kubussen; wat buiten het gebied van je kern steekt, valt eraf. Hoe groter
  de zone, hoe meer stroom: bovenop de 20 van de kern 1 per 2000 blokken (om de twee seconden). Een
  basis van 30 × 20 × 30 kost zo 9 extra, het hele gebied 216 (ongeveer één zonnepaneel in de volle
  zon). Valt de stroom uit, dan staat de zone uit en waarschuwt E.D.I.T.H. je één keer. Zet je
  generatoren en kabels dus **in** je zone!
- **De kern zelf** hoort altijd bij zijn zone (anders hak je de kern weg en is de zone weg).
- **Wat ook als plaatsen of breken telt** voor wie geen toestemming heeft: een emmer leeggieten
  (water, lava, poedersneeuw), iets aansteken, met een bijl, schep of schoffel een blok veranderen,
  beendermeel, en Stark-kabels leggen of doorknippen (anders knip je de stroom van de zone eruit).
- **Alleen C4 komt erdoor**: andere explosies (TNT, creepers, vuurballen, de satelliet) breken geen
  blokken in een zone (ze doen wel pijn), behalve als iemand met toestemming ze aanstak; vuur brandt
  geen blokken van een zone weg en lava steekt ze niet aan; een zuiger buiten de zone duwt of trekt
  geen blokken in of uit de zone; endermen, withers, ravagers en zombies (deuren) breken geen
  blokken in een zone. Anders kon je met een aansteker of TNT om de zone heen.
- **De Stark Tower en de Hydra-bunker** hebben geen zone (je moet er nog steeds in kunnen hakken);
  na het overnemen van de toren kun je er zelf een tekenen.
- **De rand**: doorzichtige blauwe wanden met een fijn raster aan de buitenkant van de zone, van
  binnen en van buiten te zien tot ongeveer 48 blokken ver. Aan/uit voor jezelf: **G > Information >
  Zone border**, of een eigen toets (standaard geen, zoals de kabel-xray). Met de Zone Marker in je
  hand zie je je zone altijd, met nummers op de kubussen. Wie geen toestemming heeft en iets
  probeert, ziet onderaan "Protected zone" en hoort een kort piepje.
- **Zone Marker**: linksklik op een blok = hoek 1, rechtsklik = hoek 2 (je ziet een oranje doos en
  "12 × 6 × 20"); sluipen + rechtsklik = deze kubus aan je zone toevoegen; sluipen + linksklik in een
  kubus = die kubus weghalen (twee keer klikken). Recept: een redstonefakkel, een Stark-chip en een
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
  detonator = een bliksemafleider, een Stark-chip, ijzer en een knop.
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
  E.D.I.T.H.
- **Het alarm** gaat af als er C4 in een zone geplakt wordt of binnen 3 blokken ervan: E.D.I.T.H.
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
      schoffel op een blok, beendermeel, een emmer leeggieten of vullen, en Stark-kabels leggen,
      oprapen of knippen niet. Onderaan staat dan "Protected zone: you can't build or break here. Only
      C4 gets through." met een kort piepje. Ladders, scaffolding, klimplanten, fakkels en lantaarns
      mag iedereen plaatsen en weghalen (de tag `starktech:zone_free`, ook zelf aan te vullen). De kern
      zelf hoort altijd bij zijn zone. Stroom: de zone kost bovenop de kern 1 per 2000 blokken (om de
      twee seconden, `zoneBlocksPerPower` in de config onder `[security]`); is er te weinig, dan staat alleen de zone uit,
      met een regel in het logboek (nieuwe blauwe soort, ZONE) en een waarschuwing van E.D.I.T.H. aan
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
      Nieuw item **Zone Marker** (recept: een redstonefakkel, een Stark-chip en een stok onder
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
      aangemeld in `neoforge.mods.toml` en `starktech.mixins.json`): vlak voordat vuur een buurblok
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
      en de **Detonator** (een bliksemafleider boven, ijzer, een Stark-chip, ijzer, en een knop eronder).
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
      the wire with shears". Wie hem plakte, hoort van E.D.I.T.H. "Your C4 at ... was defused".
      **Alarm** (`c4/C4Alarm`): plakt iemand die er niet mag bouwen C4 in een zone of tot 3 blokken
      ervan, dan zegt E.D.I.T.H. tegen de eigenaar en zijn vrienden die online zijn "C4 planted at your
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
      een derde kubus erbij), de app Zone, zonder stroom rood; dan is de basis van "Pepper" en ben jij
      een vreemde: slaan geeft "Protected zone", een steen neerzetten lukt niet, een ladder wel. **75,
      "C4"**: eerst het alarm ("C4 planted at your zone" en het rode bordje) en ontmantelen met een
      schaar; dan ben jij de inbreker: de timer op 0:05, plakken, piepen en een gat in de stenen muur;
      daarna drie keer een remote C4 met de detonator tegen het obsidiaan, en na de derde een gat. Ik
      heb ze afgespeeld met `--test` (en de stappen apart gefotografeerd). Onderweg gevonden: een
      nep-speler die voor de tweede keer inlogt laat NeoForge struikelen; de showcase geeft de basis
      daarom gewoon op naam aan Pepper (`setOwner(id, naam)` op de kern). LEESMIJ.md heeft een nieuw
      stuk "Je basis beveiligen: zones en C4", en de toetsen, het G-menu en de instellingen noemen de
      zonerand.
- [x] **9. De rand alleen met de bril** (jouw wens, 9 oktober: "zorg dat je de border enkel met bril
      kunt zien")
      De blauwe rand en de bordjes "Box 1" boven je kubussen zie je nu alleen met je eigen
      E.D.I.T.H.-bril op (met energie), net als de kabel-xray. Ook het aan/uit-knopje (G > Information >
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
      doet, staat in het logboek ("TNT blast at ...", "Satellite strike blast at ...") en E.D.I.T.H. zegt
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
      schrapen) of **schoffel** op een blok; en **Stark-kabels leggen, oprapen en knippen**. We kijken
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
      `starktech:packed_core`); de server bewaart de rest (`security/PackedCores`, in
      `data/starktech_packed_cores.dat`). Eén kern, één keer neerzetten: daarna is het etiket eraf.
      **Mijn keuzes** (zeg het als je iets anders wil): (1) **de eigenaar blijft de eigenaar**, ook als een
      ander de kern wegneemt en neerzet. Met de kern gaan zijn wachtwoord en bestanden mee, dus een
      gestolen kern moet je nog steeds hacken; anders was weghakken en neerzetten een makkelijke manier
      om het hacken over te slaan. Wie hem neerzet krijgt "This security core still belongs to ...", en
      in het logboek staat wie hem waar neerzette (de eigenaar kan dat via SSH lezen). (2) In
      **creative** valt een kern met instellingen er toch uit (zoals een volle shulkerdoos), anders was
      alles met één klik weg. (3) Een kern **van niemand** (de Stark Tower, nog niet gehackt) valt als
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
      meer dan 400 blokken van een andere kern staan (ook van een Stark Tower); met een kleiner getal
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
- Recepten: **Crusher** = ijzer, een slijpsteen, een Stark-chip en een zuiger; **Compactor** = ijzer,
  twee zuigers, een Stark-chip en een aambeeld.

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
      blok ze komen (de data component `starktech:from_block`) en heten daarnaar ("Cracked Stone Bricks",
      "Obsidian Powder"). Een gebarsten blok ziet eruit als het blok zelf met de barsten erover die je
      ook ziet als je aan een blok hakt (`client/repair/CrackedBlockRenderer`), in je hand, op de grond
      en in je inventaris; het poeder is een hoopje in de kleur van het blok (zoals op een kaart; heel
      donker wordt wat lichter, anders zie je het niet). **De Crusher** (ijzer, een slijpsteen, een
      Stark-chip en een zuiger): een metalen kast met twee maalwielen achter een venster, die op stroom
      (via kabels of direct tegen een generator) van een gebarsten blok poeder maakt: 5 seconden per
      blok, 4 stroom per tick (`machineTicks` en `machinePower` in de config onder `[repair]`). Rechtsklik
      opent zijn scherm: het vakje erin (leeg: een vaag voorbeeld van wat erin hoort), een blauwe pijl die
      vol loopt, het vakje eruit, en onderaan wat hij doet ("Crushing...", "No power: connect it with Stark
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
      **De Compactor** (ijzer, een aambeeld, twee zuigers en een Stark-chip): dezelfde kast als de Crusher,
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

- **Het gereedschap: de Bypass Kit** (een inbraakset; recept zonder vaste vorm: Stark-chip, schaar,
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
  alarm), E.D.I.T.H. waarschuwt de eigenaar, de beveiliging jaagt op je, en dat apparaat laat jou 60
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
  gebeurde. Een server kan het uitzetten met `pickpocket.allowed` in `starktech-server.toml`, net als
  "hack player".
- Alle getallen staan in de config onder `[bypass]`: de tijden, hoe lang ALARM mag, de wachttijd na
  een mislukte hack, de extra pogingen, en het bereik en de wachttijd van zakkenrollen.

De stappen, één per commit:

- [x] **1. De Bypass Kit en een keycardlezer hacken**
      De **Bypass Kit** (zonder vaste vorm: Stark-chip, schaar, glowstonestof, redstone en ijzer; gaat 16
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
      server zet het uit met `pickpocket.allowed = false` in `starktech-server.toml`; het bereik, de
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
      de vierde poging CODE CRACKED 4321: de lamp gaat aan. Dan zakkenrollen bij Happy (die in de
      showcase-wereld echt een speler is): je sluipt achter hem, schuift de rommel opzij en trekt een
      kaart uit zijn zak, en die zit daarna echt in je hotbar. De tweede keer draait Happy zich om:
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

- **Arc Lamp** (ijzer, vier glazen ruiten, gloeisteen en een Stark-chip): een stalen blok met aan vier
  kanten glas. **2 stroom per tick** zolang hij brandt (een volle warmtegenerator: ruim acht minuten).
  Een lamp die uit is, gaat pas aan als het net stroom heeft voor 5 seconden, zodat hij aan een te
  zwakke generator niet elke tick knippert. Alle drie de getallen staan onder `[lights]` in de config
  (`arcLampMaxLevel`, het hoogste lichtniveau, tussen 15 en 128).
- **Lichtschakelaar** (een hendel, een ijzeren klompje en een Stark-kabel = 2): een plaatje met een
  wipschakelaar, aan de muur, op de vloer of aan het plafond. Rechtsklik zet hem om. Aan: blauw lampje,
  de stroom gaat erdoor alsof het kabel is. Uit: rood lampje, daar houdt de stroom op (ook als er een
  getekende kabel door de schakelaar loopt). Hij geldt voor álles achter hem, niet alleen lampen.
- **Ook via E.D.I.T.H.**: wie een schakelaar neerzet, krijgt hem in de redstone-lijst ("Light switch 1"),
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
      `light/LightSwitchBlock`, met een uitzondering in `energy/StarkGrid`: een schakelaar die aan staat
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

- **`config/StarkConfig.java`**: alle getallen van de mod, in 27 hoofdstukken (Rules, Glasses,
  Scanner, **Power**, Drones, **Satellite**, ... Raids), met een inhoudsopgave en uitleg over de
  eenheden bovenaan, en bij elk getal een regel Nederlands. De code leest overal rechtstreeks hieruit,
  bijvoorbeeld `StarkConfig.Satellite.COOLDOWN`.
- **`config/starktech.toml`**: wordt daar vanzelf uit gemaakt (`config/StarkConfigLoader`), met
  dezelfde hoofdstukken en Engelse uitleg. **344 instelbare getallen** (eerst 204 in het ene bestand en 14 in het andere). Het vervangt
  `starktech-balance.toml` en `starktech-server.toml`; de oude namen `drones.orbitHeight`,
  `drones.swarmMax` enzovoort zijn gebleven. Weg: `EdithBalance`, `EdithConfig`, `BalanceConfig` en
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
- **Vast gebleven** (staan wél in `StarkConfig`, met `final` en "VAST" erbij): wat het spel bij het
  opstarten al nodig heeft (levens van drones en vijanden, het pantser van het harnas, hoe vaak een
  Bypass Kit meegaat, hoeveel kubussen een zone mag hebben) en hoe schermen eruitzien (vakjes in een
  serverkast of vrachtdrone).
- **Mijn invulling**: je persoonlijke instellingen (stem, volume, Spotify, zone-rand) blijven in
  `starktech-client.toml`: die kiest elke speler zelf, en het wereldbestand komt op een server van de
  server. Eén piepklein verschil: een bombardement op niveau 0 (dat op slot zit) rekent nu met de
  explosie van niveau 1.

De stappen:

- [x] **1. StarkConfig, de lader en alles omzetten**
      118 bestanden lezen nu uit `StarkConfig`; `logic/LogicStats` en `CircuitPuzzle` kregen de kleine
      rekenhulpjes uit de oude bestanden. De bestaande configtest kijkt nu ook of een rijtje per niveau
      telt (de satelliet wacht dan echt korter), of een aan/uit-regel telt, en of een rijtje met het
      verkeerde aantal getallen terugvalt op de standaard. Alle 203 tests groen. LEESMIJ.md hoofdstuk 6
      en de stukken die naar de oude bestanden verwezen, zijn bijgewerkt.
- [x] **2. Kommagetallen voor stroom** (11 oktober: "ik heb bij power ints naar floats veranderd ... dit
      werkt niet ... zorg dat alles in floats staat zodat ik makkelijk heel kleine getallen kan gebruiken")
      Er gingen twee dingen mis. Het net rekende in hele getallen, dus 0.3 per tick werd 0. En een
      nieuwe standaardwaarde in `StarkConfig` verandert een bestaand `starktech.toml` niet: daar stond
      nog 6. Nu zijn **alle energiegetallen in `[power]` kommagetallen** (float; aantallen zoals "hete
      blokken" en tijden in ticks blijven hele getallen). Generatoren bewaren hun stroom als kommagetal;
      apparaten betalen met `StarkGrid.pay`, dat per apparaat onthoudt wat nog geen hele stroom was
      (0.25 per tick = elke vierde tick 1); snelheden zoals "zoveel per tick in de bril" gaan via
      `energy/EnergyCarry`. Het scherm van een generator toont nu ook "0.3 per tick". Jouw vijf nieuwe
      waarden staan nu ook in `run/config/starktech.toml`, en `StarkConfig` waarschuwt bovenaan voor die
      valkuil. Een nieuwe test (`power_works_with_fractions`). Met de oude waarden (6 en 14 per tick)
      zijn alle 204 tests groen. **Met jouw nieuwe waarden** (0.3 en 0.7) zijn er 3 rood:
      `hydra_bunker_lasers_need_the_reactor`, `stark_tower_vault_trap` en `stark_tower_lasers_have_power`.
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
      zone kost in de app Zones, en blijft bewaard in `starktech-client.toml` (`powerUnit`). **Mijn
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
      (`StarkGrid.missed`: ook een laserwand of Arc Lamp die niet aan kan, een kern zonder stroom, een
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
      onthoudt welke tag in welke tag zit (`EdithRelays.tagParents`). Een tag mag in meerdere tags zitten,
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
      (`EdithRedstone.otherOwner`). Een ander hoort "This relay belongs to ...". Stond jouw relais van vroeger
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
      in een gebouw van deze mod (de Stark Tower, de Hydra-bunker): anders kon een indringer de lasers van
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
      Onderweg werd `stark_tower_has_powered_cameras` rood: met de nieuwe batches lopen de tests in een andere
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
      groep), en een groep in hooguit één andere groep (`EdithRelays.groupParents`, leeg = bovenaan). Zo is
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
- [x] **4. Op de desktop: de app Groups** (`GroupsApp`, icoon **G** op de Stark OS-desktop). Dezelfde boom,
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
security drones, training zoals Domino squad, en een inbraak- of uitbraakoefening. Ze bouwen op elkaar:
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
      number, so you cannot give it text", "card.level is information from E.D.I.T.H.: you can read it,
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
      `Host`: op de server is dat `LogicRunner.ServerHost` (relais, lampjes, E.D.I.T.H., het logboek), straks
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
- [ ] **7. Soorten programma's** — *verschoven naar hoofdstuk 23*: een soort "Robot" of "Drone" doet pas iets
      als er robots zijn. Daar komt hij als eerste stap, met de controle per soort (een `accept()` kan niet in
      een robotprogramma).
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
      code 4321, E.D.I.T.H. telt mee, de deur gaat drie keer open, en dan de code en de blokjes). LEESMIJ.md,
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

- [x] **1. Config en de robot** — `[robots]` in `StarkConfig` (7 lijstjes met elk 4 getallen, één per stijl:
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
      Battle een ijzerblok: het zwaarst); een hoofd heeft een Stark Chip en een glasruit, een lijf een
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
      en een Stark Chip; icoon: een groene printplaat met 8 gouden pinnen). Wat erop zit staat in het item
      (`BotBoard` in de nieuwe map `bot/`, die robots en later drones delen): 8 pinnen met elk de naam van
      een module of leeg, en de soldeerbruggen tussen naburige pinnen (voor hoofdstuk 22). Een nieuwe
      printplaat is leeg. Eén nieuwe test (8 pinnen, bewaren en laden, recept). Vorige stap: `ca743d5`.
- [x] **5. Robot Assembler** — een stalen werkbank (recept: ijzer, Stark Chip, zuigers en een werkbank; voorop
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
      `starktech_bots`): elke robot met naam, eigenaar, laatste plek, dock, onderdelen, toestand, batterij en
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
- [x] **8. App Robots** — een nieuwe app op de Stark OS-desktop (icoon "R", na Groups): de robots in het gebied
      van de kern (uit `BotNetwork`), elk met naam, plek, toestand, batterij en of hij een dock heeft, en de
      knoppen **Rename** (Enter bewaart, Esc niet) en **Switch on/off**. Een robot in een stuk wereld dat
      niet geladen is, staat er grijs bij ("far away") zonder knoppen. Werkt ook op afstand (SSH), zoals
      Devices. De desktop bedient alleen robots in het gebied van zijn eigen kern. "Verbinding" komt in
      hoofdstuk 23 (dan pas praat een robot met de server). Eén nieuwe test (de lijst; aanzetten en
      hernoemen van de desktop; een rare id doet niets). Screenshot `rb13`. Vorige stap: `1f9c408`.
- [ ] **9. Geluid, showcase, LEESMIJ** — servogeluiden, opstarten en uitzetten, zacht zoemen op het dock
      (met een volumeknop).

Tests: samenstellen en uit elkaar halen; een robot op een dock laadt via de kabel en niet zonder stroom;
aan = eruit, uit = terug; een vreemde kan hem niet bewerken; opslaan en laden klopt.

### 22. De printplaat: solderen (14 oktober)

Jouw antwoorden: je soldeert de upgrades op de **pinnen** van de printplaat, met een **soldeerbout** en
vasthouden. Het is een **klein spelletje**: je kunt per ongeluk een **kortsluiting** maken (een soldeerbrug
naar de pin ernaast). Zet je de robot niet aan, dan is dat geen probleem: je kunt lossolderen en opnieuw
proberen. Zag je het niet en zet je hem wel aan, dan kunnen de robot of de onderdelen afbranden.

**Mijn invulling**: acht modules (Scan, Distance, Face Recognition, Pathfinding, Laser, Gun, Solar Panel,
Camera). Het spelletje: een hittebalk; loslaten in het groene vak = goede verbinding; te vroeg = niets
(opnieuw); te laat = een brug. Een brug zie je alleen als je goed kijkt, of met de knop Inspect. De kans op
brand staat in de config.

- [ ] **1. Modules** — acht modules plus de *Burnt Module*, recepten, texturen.
- [ ] **2. Het bord** — pinnen, soldeerbruggen, solderen en lossolderen; de server bepaalt de uitkomst.
- [ ] **3. Het spelletje** — hittebalk, groen vak, een brug bij te laat, Inspect, en de soldeerbout (slijt).
- [ ] **4. Aanzetten met een brug** — de modules op die pinnen verbranden; met een kans brandt de hele robot
      af; logboek en E.D.I.T.H.-melding met de pinnummers.
- [ ] **5. Zichtbaar op de robot** — laser op de arm, zonnepaneel op de rug, antenne, cameralens.

Tests: goed solderen geeft een module; te laat geeft een brug; aanzetten met een brug verbrandt de modules;
zonder brug gebeurt er niets; lossolderen geeft de module terug; solderen aan een robot die aan staat wordt
geweigerd.

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
  `when started:` en `when order("attack"):`.

- [ ] **1. Het brein van de robot** — dezelfde tolk, met een stappenbudget per tick; bewegen stap voor stap;
      `blocked` als er iets in de weg staat.
- [ ] **2. Programma's naar de robot** — uploaden via de verbinding; de robot bewaart zijn eigen kopie.
- [ ] **3. Verbinding** — alleen binnen het gebied van de core, met stroom en een werkende server. Zonder
      verbinding geen alarm en geen relais, wel lopen en scannen.
- [ ] **4. Robot naar server** — alarm, say, log, relais, en `send(...)` met `sender.name`.
- [ ] **5. Server naar robot** — order, activate, deactivate; een opdracht aan een robot die niet geladen is,
      wacht tot die weer geladen wordt.
- [ ] **6. De app Logic** — soort Robot, Help en Ctrl+Spatie met de pinnen van de gekozen robot, en
      voorbeeldprogramma's.

Tests: vierkantje lopen (vier keer `turn_right()` na `forward(2)` komt op de plek waar hij begon); batterij
laden en terug; een opdracht onderbreekt het programma en het hervat; zonder verbinding geen alarm maar wel
lopen; een relais via `pulse`; een wachtende opdracht bij laden.

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

- [ ] **1. Scan en Distance** — zicht via de kijkhoek van zijn hoofd; niet door muren.
- [ ] **2. Face Recognition** — de naam van de herkende speler, en het filter.
- [ ] **3. Laser en Gun** — een straal zoals bij de Hydra-soldaat; Robot Ammo; batterijkosten; koepels
      houden ze tegen.
- [ ] **4. Zonnepaneel** — laadt overdag, met dezelfde zonberekening als het zonnepaneel van het net.
- [ ] **5. Camera** — de robot in de app Cameras; alleen binnen het volgbereik; zijn eigen model wordt
      overgeslagen.
- [ ] **6. Ion Blaster** — de robot valt neer (DOWN); buiten een training staat hij na 60 s weer op.

Tests: de scan ziet een speler in zicht en niet door een muur; het filter "strangers" ziet een vreemde en
niet de eigenaar; zonder verbinding ziet hij niemand; een koepel houdt de laser tegen; zon laadt overdag; de
Ion Blaster zet een robot DOWN en een speler blijft ongeschonden.

### 25. Programmeerbare security drones (14 oktober)

Jouw antwoorden: de security drones krijgen dezelfde programmeerbaarheid, plus **omhoog en omlaag**. Hun
laadstation wordt de **Drone Dock**. De **arsenal-drones** laden daar niet meer, die blijven in hun baan.
Drones bouw je zelf, met een eigen printplaat. De Stark Tower krijgt docks met drones die Tony al
geprogrammeerd heeft.

**Mijn invulling**: de core maakt geen gratis drones meer; de schakelaar "Drones" in de app Devices zet al je
drones aan of uit. Arsenal-drones herstellen zich in hun baan, even snel als vroeger op een dock, zodat je
niets kwijtraakt. Oude drones (zonder printplaat) blijven werken tot ze neergeschoten worden.

- [ ] **1. Vliegende beweging** — `up(n)` en `down(n)`, in drie assen.
- [ ] **2. De drone bouwen** — item met printplaat en modules; plaatsen op een dock.
- [ ] **3. De Drone Dock opnieuw** — laadt zijn eigen drone; arsenal-drones komen er niet meer op; herstel in
      de baan.
- [ ] **4. De core** — geen gratis drones meer; schakelaar "Drones" in Devices.
- [ ] **5. De Stark Tower** — vier docks bij de kern met Tony's drones (programma "Tower patrol"). Wie de
      toren hackt, krijgt ze.

Tests: bouwen en laden op een dock; `up(3)` stijgt; de core spawnt zelf geen drones meer; een dock pakt geen
arsenal-drone meer; de toren heeft vier gewapende drones; een oude drone patrouilleert nog.

### 26. Training zoals Domino squad (14 oktober)

Jouw wens: programma's die gecoördineerde golven op de speler sturen, die non-lethal of lethal schieten (jouw
keuze). Een **Training End**-blok geeft redstone als de trainee geraakt wordt, en daarin programmeer je wat
er dan gebeurt (robots aan of uit).

Jouw antwoorden: een **Training Start**-blok; wie erop klikt, is de trainee; daar kies je de training en
lethal of non-lethal. Het Training End-blok heeft een **mini-code-editor** in het blok, dezelfde taal. Je
diagram hoort `when training_started("Kamino"):`.

**Mijn invulling**: standaard 1 leven (één treffer en je ligt eruit). Non-lethal = geen schade, een blauwe
flits en 1 seconde trager. Alleen de eigenaar, zijn team en vertrouwde spelers starten een training. Golven
via squads: `activate("Wave 1")`, `when squad_down("Wave 1"):`. Een **Training Goal**-blok: erop komen is
geslaagd. Na afloop een rapport van E.D.I.T.H. en de app Training.

- [ ] **1. Centraal schot** — één plek (`Shots`) voor lethal en non-lethal; torentjes, drones, laserwanden en
      robots sluiten erop aan.
- [ ] **2. Training-sessie** — per core: trainee, naam, soort, lethal, levens, treffers; de triggers
      training_started, training_hit en training_ended.
- [ ] **3. Training Start-blok** — instellingen (sneak + klik), aftellen, alleen voor vertrouwden.
- [ ] **4. Squads** — robots in een squad; activate, deactivate, squad_down, active.
- [ ] **5. Training End-blok** — redstone-puls bij elke treffer, en de mini-editor (dezelfde taal, eigen soort
      programma).
- [ ] **6. Training Goal-blok** — geslaagd als de trainee erop komt.
- [ ] **7. Einde en rapport** — robots die down zijn, staan weer op en lopen naar hun dock; rapport van
      E.D.I.T.H.; app Training.

Tests: een non-lethal treffer doet geen schade maar telt; lethal doet schade; levens op beëindigt de training;
het End-blok geeft een puls en deactiveert een squad; een inbreker kan geen training starten; het Goal-blok
beëindigt met succes; down-robots staan na afloop weer op.

### 27. Inbraak- en uitbraakoefening (14 oktober)

Jouw wens: "test hoe moeilijk het is om tot bij de security core te geraken en hem te hacken, of hoe moeilijk
het is om uit je prison uit te breken." Jouw antwoord op de vraag wat er daarna gebeurt: **niets wordt
teruggezet**. Wat kapot gaat, blijft kapot; robots staan weer op, tenzij ze echt vernietigd zijn.

**Mijn invulling**: de soorten **Break-in** en **Break-out**, alleen in survival. Tijdens zo'n oefening
behandelt je hele basis de trainee als indringer, ook de eigenaar zelf: torentjes, drones, robots (filter
"strangers"), laserwanden, de zone (niets bouwen of slopen, behalve vrije blokken en C4), de desktop (niet te
openen) en de bril (niet op afstand schakelen). Kaarten die hij bij het begin had, werken niet. Een geslaagde
hack (de Stark Laptop-minigame) telt als rapport; de core blijft van jou. Break-out is geslaagd als hij het
Goal-blok buiten de cel haalt. Het rapport zegt hoe dicht hij bij de core kwam en wie hem raakte.

- [ ] **1. Soorten en sessie** — Break-in en Break-out, alleen in survival, één sessie per core.
- [ ] **2. Indringer in je eigen basis** — één plek (`Training.isDrillTrainee`) die alle uitzonderingen
      uitschakelt: torentjes, drones, robots, laserwanden, zones, kaarten, desktop en de bril.
- [ ] **3. Hack zonder eigenaarswissel** — de laptop-hack van de trainee maakt hem geen eigenaar; het rapport
      zegt "core hacked in 4:12".
- [ ] **4. Uitbraak** — geslaagd als hij het Goal-blok buiten de cel bereikt.
- [ ] **5. Rapport** — dichtste afstand tot de core, en wie hem raakte.

Tests: de eigenaar als trainee wordt geraakt door zijn eigen torentje en robot; hij kan de zone niet slopen;
een oude kaart opent de lezer niet; een geslaagde hack laat de eigenaar ongemoeid; de oefening eindigt als hij
sterft of uitlogt; creative wordt geweigerd.

Bouwen gaat in volgorde, één stap per commit, met bij elke stap de tests, de screenshots en de showcase.

---

## Nog nooit in het spel bekeken

De automatische tests draaien zonder beeld: ze controleren wat er gebeurt, niet hoe het
eruitziet. Dit is gebouwd maar door mij nooit gezien. Laat het weten als iets er raar uitziet.

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
      `config/starktech-client.toml` (1 = zo, 2 = dubbel, 0 = uit).
- [ ] **De blokjes-editor met de muis**: slepen, draadjes trekken en zoomen heb ik alleen
      nagerekend en op screenshots gezien, niet zelf met een muis gedaan.
- [ ] **Het datacenter (8 oktober)**: de knoppen van de apps Backup en Monitor heb ik alleen via de
      server getest en op screenshots gezien, niet met de muis aangeklikt. En speel zelf of de
      getallen goed voelen: hoeveel fans je nodig hebt, hoe snel het warm wordt (ongeveer een
      minuut), hoe lang E.D.I.T.H. erbij nadenkt (1 CPU +8 s), en of vuur van een brandend rack niet
      te makkelijk een houten basis afbrandt. Alles staat in de config onder `[logic]`.
- [ ] **Zones en C4 (8-9 oktober)**: gezien op screenshots, niet zelf gespeeld. Kijk zelf of de rand
      niet te fel of te zwak is (vooral dichtbij, tegen je eigen muur), of de muisklikken in de app
      Zone goed werken (typen in de vakjes, de kaart), of de piepjes van C4 en het doorknippen goed
      klinken, en of de getallen goed voelen: hoeveel C4 een muur kost (steen 1, obsidiaan 3, de kern
      6), hoe groot het gat is, en hoeveel stroom een grote zone kost. Op obsidiaan zie je de barsten
      bijna niet (zo tekent Minecraft barsten). Alles staat in de config onder `[c4]` en `[security]`.
- [ ] **Barsten herstellen (9 oktober)**: gezien op screenshots en in de showcase, niet zelf gespeeld.
      Luister of de Crusher (een heel zachte slijpsteen) en de Compactor (een heel zachte zuiger) goed
      klinken, en of de getallen goed voelen: 15% zwakker per keer plamuren, 5 seconden per blok in een
      machine. Een gebarsten blok van obsidiaan ziet er in je hand bijna uit als gewoon obsidiaan (net
      als de barsten in de wereld). Alles staat in de config onder `[c4]` en `[repair]`.
- [ ] **Inbreken (9 oktober)**: gezien op screenshots en in de showcase, maar niet zelf met een muis
      gespeeld. Probeer of het draaien met links- en rechtsklik, het vegen met de muisknop ingedrukt en
      het slepen in de zak goed aanvoelen, en of de getallen kloppen: de tijden van het stroomcircuit
      (60 tot 40 seconden), 1 seconde voor ALARM, de lengte + 2 pogingen bij een paneel, en hoe snel de
      meter NOTICED vol loopt (te makkelijk of te moeilijk?). Alles staat in de config onder `[bypass]`,
      behalve de meter (die staat in `client/pickpocket/PocketScreen`).

Opgevallen in de showcase (door mij, op screenshots; nog uitzoeken):

- [x] De **schildkoepel** zie je van een afstand bijna niet — opgelost met de nieuwe koepel
      (`e0d2b64`).
- [x] **Illusie "cloak"**: op het screenshot was de koe in het veld nog gewoon te zien — `c615d11`
      Het veld ging naar de muur achter de koe (alleen blokken telden bij het richten), en
      raakte je de zijkant van een muur, dan kwam de drone ín de muur en stikte hij. Nu komt het
      veld bij het wezen waar je naar kijkt, of op de vrije plek voor de muur, en stikken drones
      niet meer. Twee nieuwe tests.
- [ ] Raiddrones die komen omdat je in een Stark Tower sloopt. Dat kan de test niet nadoen:
      een testwereld weet niet dat een geplaatst gebouw een "Stark Tower" is.
- [ ] **De Arc Lamp (9 oktober)**: gezien op screenshots en in de showcase, op een vlak veld. Kijk zelf
      in je eigen wereld (heuvels, grotten, een huis) of het licht er goed uitziet, of het opnieuw meten
      na bouwen snel genoeg gaat, en of je spel niet hapert als je de lamp aan- of uitzet. (De gloed: "mag
      volledig weg", 13 oktober, is weg.) Sinds 13 oktober ook: zet een lamp in een kamer en kijk of de
      kamers ernaast donker blijven, en of de bundel door een open deur er goed uitziet.
- [ ] **Het scherm van de laserzender (13 oktober)**: gezien op screenshots en in de showcase. Probeer zelf
      of sluipen + klikken met een lege hand fijn werkt, of je met een kabelrol nu makkelijk kabels legt,
      en of een liggende wand er goed uitziet (op de screenshots wel: `73_laser_flat`).
- [ ] **De taal groeit (15 oktober)**: gezien op screenshots (`l12`-`l15`), getest met 8 nieuwe tests. Probeer
      zelf met de muis: het palet scrollen met het muiswiel, een SET-blokje met zijn vak Value, een lus-blokje
      met "do" en "after" verbinden, en of de fouten in begrijpelijk Engels staan als je iets verkeerd typt
      (`count has no value yet`, `break only works inside ...`).
- [ ] **Relaisgroepen (14 oktober)**: gezien op screenshots (`rl03`-`rl061`) en in de showcase. Probeer zelf
      of slepen in de app Groups op de desktop fijn werkt (ook met veel relais, als je moet scrollen), en of
      het groepen-scherm duidelijk is: een nieuwe groep komt in de groep die je gekozen hebt.
- [ ] **Het naamscherm van de keycardlezer en het codepaneel (13 oktober)**: gezien op screenshots
      (`dr4`-`dr7`) en in de showcase. Probeer zelf of sluipen + rechtsklik met een lege hand fijn werkt, ook
      met een vriend in je Access en met een vreemde (die hoort bij een lezer "This keycard reader belongs to
      ...", en krijgt bij een paneel gewoon de toetsen).

---

## Open vragen

- [x] **Moeten de getallen uit `EdithBalance.java` naar een configbestand?** Ja, per wereld
      (jouw keuze op 26 september) — `d26a25e`
      120 getallen staan nu in `config/starktech-balance.toml` (kosten, schade, bereik,
      wachttijden, verbruik; per kopje: bril, drones, satelliet, harnas, torentjes, Hydra...).
      Het bestand geldt voor al je werelden; een kopie in `<wereld>/serverconfig/` geldt alleen
      voor die wereld (zo werkt NeoForge 21.1; ook de raid-instellingen staan zo). Niet erin:
      wat bij het opstarten vastligt (bescherming van het harnas, levens van wezens) en de
      natuurkunde van het vliegen. Eén nieuwe test.
- [x] **Wil je bij elke nieuwe functie automatische tests?** (mijn invulling: ja, ik schrijf ze
      mee; er zijn er nu 76, en ze vingen deze week o.a. een lege zwarte doos en een stikkende
      drone)
- [x] Moeten de Nederlandse foutmeldingen in de gametests ook Engels? (mijn invulling: nee, die
      zie je alleen tijdens het ontwikkelen, en Nederlands leest voor jou makkelijker)

---

## Ideeën die we nog niet gekozen hebben

Wel besproken, maar niet ingepland. Hier zodat ze niet verdwijnen.

**Gevecht**
- Repulsor-handschoen als los item (nu zit de repulsor in de borstplaat)
- ~~Mini-raketten die hun doel volgen~~ (gebouwd: `c030a4a`)
- "Veronica": onderdelen van een harnas die naar je toe vliegen
- ~~Drone-formaties (schild, pijl, cirkel)~~ (gebouwd: `065664e`)
- Het harnas: onder water ademen

**Basis en techniek**
- ~~Een werkbank die harnasdelen maakt~~ (gebouwd: de Suit Workbench, `61fde87`)
- Een Stark-AI-kern die passief onderzoek doet
- ~~Automatische torentjes die aan je relais-groepen hangen~~ (gebouwd: `53ff0b9`, ze staan in je redstone-lijst)
- ~~Energie-netwerk: kabels of een draadloos Stark-net~~ (gebouwd: de Stark-kabels)
- ~~Holotafel: een stand die de ertsen in de buurt laat zien~~ (gebouwd: `6cd20ed`)

**Sfeer**
- Eigen geluiden. Alle 18 geluiden in `sounds.json` zijn nu vanilla-geluiden onder een andere
  naam (`beacon.activate`, `note_block.pling`). Echte E.D.I.T.H.-tonen zouden veel schelen.
- Betere foutmeldingen bij Spotify en de stem-installatie

**Wereld**
- ~~Een tweede soort structuur met de onderdelen voor de volgende fase~~ (gebouwd: de Hydra-bunker, `163771b`)

---

## Besloten om niet te doen

- **Linux en Mac ondersteunen.** Alleen Windows; de stem en de AI draaien op Windows-programma's.
- **De mod publiceren.** Blijft voorlopig privé.
- **Naar een nieuwere Minecraft-versie.** Voorlopig 1.21.1.
