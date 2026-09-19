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

Alles testen dat op de server gebeurt: `gradlew runGameTestServer` (7 automatische tests).

---

## 2. Beginnen in het spel

- Craft een **Stark-chip** (koper, goud, redstone, diamant) — daar heb je bijna alles mee nodig.
- Craft de **E.D.I.T.H.-bril** en zet hem op je hoofd (helm-slot).
  De eerste die hem opzet is de eigenaar; anderen krijgen "toegang geweigerd".
- Craft het **laadstation** en stop er redstone in om de bril op te laden.
  In het station bouw je ook upgrades in (batterij, scanner, drones).
- Zoek een **Stark Tower** in je wereld: een hoge glazen toren met een laadstation, kisten
  en een landingsplatform. Daar vind je een bril als je er geen wil craften.

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
| **I** | (vrij, zie Opties > Besturing > E.D.I.T.H.) |

Alle toetsen kun je aanpassen: **Opties > Besturing > E.D.I.T.H.**

---

## 4. Het G-menu

| Tabblad | Wat erin zit |
|---------|--------------|
| **Gevecht** | Rayshield, drones, één drone, escorte, satelliet + stand, stand down, markeringen wissen |
| **Drones** | Drone-camera, spionagedrone, meekijken, rapport |
| **Transport** | Zelf vliegen, autopiloot, vrachtdrone, iemand vervoeren |
| **Tech** | Redstone-relais, muziek (Spotify), hacken |
| **Illusie** | Valse muur, nep-leger, wereld kopiëren, onzichtbaar veld, stoppen |
| **Scanner** | Ertsen-scan, nachtzicht, doelwit-analyse, dreigingsscan |
| **Navigatie** | Waypoints, ping, comms |
| **Systemen** | Eigendom overdragen, stem, spraakbesturing, commando's bewerken, spraaktekst aan/uit |

---

## 5. De grote functies

### Drones
Ze komen altijd **uit de lucht** duiken. Hoeveel er maximaal zijn, staat in de config
(`<wereld>/serverconfig/starktech-server.toml`), standaard zwerm 16 en escorte 8.

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
- **Precisie** — veel schade, **geen** blokschade.
- **Bombardement** — vijf inslagen, flinke krater.
- **Schuivende laser** — een streep die van je af over de grond kruipt.
Hij volgt bewegende doelwitten (lock-on), richt tot 500 blokken, en je kunt ook op je waypoint
of je laatste ping mikken. Bij de inslag komt er een schokgolf en schudt je beeld.

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
- **Laserzender** (blok): zet er twee tegenover elkaar → een rode wand waar niemand door komt.
  Aan met redstone óf met E.D.I.T.H. (hij krijgt ook een naam/groep).
- **Schildkoepel-generator** (blok): koepel van 20 blokken die pijlen laat verdampen en
  monsters naar buiten duwt. Spelers en dieren mogen erdoor.

### Illusies (cloakingdrones)
Een onzichtbare drone projecteert een beeld in een bol van 16 blokken. Alles is **nep**: in de
wereld verandert niets.
- **Valse muur** — van het blok dat je in je hand houdt.
- **Nep-leger** — van het spawn-ei dat je in je hand houdt.
- **Wereld kopiëren** — kopieert 9x9x9 waar je naar kijkt en projecteert dat.
- **Onzichtbaar veld** — alles in het veld wordt niet meer getekend.

Zwakke plekken (net als in de film): waar twee velden overlappen gaat het beeld **flikkeren**,
de drone blijft raakbaar (neerhalen = beeld weg), hij verraadt zich met vonkjes en een zacht
zoempje, en met de **E.M.P.-zender** (item) leg je alle illusies binnen 24 blokken 30 seconden
plat.

### Muziek (Spotify)
Zeg *"E.D.I.T.H., speel Thunderstruck van AC/DC"*. Ook: pauze, verder, volgende, vorige en
*"wat speelt er"*.

Eenmalig instellen (G > Tech > Muziek legt het ook stap voor stap uit):
1. Ga naar `developer.spotify.com/dashboard` en maak een gratis app ("Create app").
2. Vul als **Redirect URI** exact in: `http://127.0.0.1:8899/starktech`
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
| `<wereld>/serverconfig/starktech-server.toml` | Max drones, hoogte waaruit ze duiken, hacken aan/uit |
| `config/starktech-client.toml` | Stem, volume, spraaktekst, Spotify Client ID, GPU voor de AI |
| `config/starktech-voice.json` | Alle spraakcommando's (zelf uit te breiden) |
| `config/starktech-spotify.json` | Je Spotify-token (niet delen) |
| `src/main/java/.../edith/EdithBalance.java` | Alle getallen: kosten, schade, bereik, wachttijden |

---

## 7. Zelf verder bouwen

- **Getallen aanpassen** (sterker/zwakker): `EdithBalance.java`, dan `gradlew build`.
- **Textures**: `python tools/generate_textures.py` maakt ze allemaal opnieuw. Teken je liever
  zelf? Teken dan over het bestand heen en draai het script niet meer.
- **De toren verbouwen**: `python tools/generate_structure.py`, of bouw hem in het spel met een
  structuurblok en sla hem op als `starktech:stark_tower`.
- **Tests**: `gradlew runGameTestServer` — 7 tests over de toren, drones, satelliet, navigatie,
  spion en de spraakcommando's.
