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

**Stand van zaken:** 95 automatische tests, allemaal groen (`gradlew runGameTestServer`).
Alles zelf bekijken: `python tools/showcase.py` laat alle 77 functies één voor één zien (of een
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
