# I.M.T Technology publiceren op CurseForge

Alles wat je nodig hebt, staat in deze map:

| Bestand | Waarvoor |
|---------|----------|
| `summary.txt` | De samenvatting (één zin) |
| `description.md` | De beschrijving van je projectpagina (Engels, in Markdown) |
| `changelog-2.0.0.md` | Wat er in deze versie zit (bij het uploaden van het bestand) |
| `avatar-400.png` | Het projectplaatje (CurseForge wil precies 400 x 400) |
| `release/imttech-2.0.0.jar` | De jar om te uploaden (niet in git, zie stap 1) |
| `screenshots/` | Echte screenshots uit het spel voor je galerij (niet in git) |

## 1. De jar die je uploadt

Upload altijd een jar die gebouwd is uit een **schone versie** van het project (alles
gecommit, niets half af). In je werkmap stond nog onafgewerkt werk van hoofdstuk 28
(robotonderdelen die niet stapelen): een jar daaruit heeft een kapotte assembler.

De jar in `release/` is zo gebouwd, en alle tests zijn erop gedraaid. Wil je hem zelf opnieuw
maken, doe dan eerst alles af en commit het, en dan:

    gradlew build

De jar staat dan in `build/libs/imttech-2.0.0.jar`.

## 2. Het project aanmaken

1. Log in op [curseforge.com](https://www.curseforge.com) en maak een nieuw project
   (**Create a Project**). Kies het spel **Minecraft** en de soort **Mods**.
2. **Name**: `I.M.T Technology` (zonder "mod" of versienummer erin, dat wil CurseForge niet).
3. **Summary**: plak `summary.txt`.
4. **Description**: plak `description.md`. Kan je kiezen tussen een gewone editor en
   Markdown, kies dan Markdown; anders zet je de kopjes en lijstjes zelf goed.
5. **Avatar**: `avatar-400.png`.
6. **Categories**: hoofdcategorie **Technology**. Extra (tot vier): bijvoorbeeld **Armor,
   Tools, and Weapons**, **Redstone**, **Structures** en **Energy**.
7. **License**: **MIT**.
8. Links (bron, issues, Discord) mag je leeg laten: je repo is privé, en spelers kunnen
   reageren op je projectpagina.

## 3. Het bestand uploaden

- **File**: `release/imttech-2.0.0.jar`
- **Display name**: `I.M.T Technology 2.0.0`
- **Release type**: **Release**. (Beta en Alpha ziet de CurseForge-app alleen bij wie dat zelf
  aanzet.)
- **Game version**: **1.21.1**. **Mod loader**: **NeoForge**. **Java**: **21**.
  Vraagt het formulier naar de omgeving: **Client** en **Server** (de mod moet op allebei).
- **Changelog**: plak `changelog-2.0.0.md`.
- **Dependencies**: geen (NeoForge zelf hoef je niet op te geven).

Daarna kijkt een moderator van CurseForge je project na. Dat kan uren tot enkele dagen duren.
Krijg je een opmerking terug, verbeter dan precies dat en dien het opnieuw in (geen nieuw
project maken).

## 4. Screenshots

In `screenshots/` staan echte plaatjes uit het spel (gemaakt door de VisualCheck). Mooiere
maak je zelf met **F2** in het spel; ze staan dan in de map `screenshots` van je
Minecraft-installatie. Gebruik geen plaatjes die met AI gemaakt of bewerkt zijn. Doe je dat toch,
dan wil CurseForge er een duidelijke vermelding bij.

## 5. Goed om te weten

- In `description.md` staat dat je de mod maakte met de hulp van Claude Code. Dat hoeft niet op
  CurseForge, dus je mag die zin weghalen. Op **Modrinth** mag de mod niet: hun regels (6.2,
  13 augustus 2026) verbieden projecten die vooral door AI gemaakt zijn.
- Na de goedkeuring heb je een link naar je projectpagina. Zet die bij de volgende versie in
  `src/main/templates/META-INF/neoforge.mods.toml` als `displayURL`; dan staat hij in de
  modlijst van Minecraft.
- Elke nieuwe upload krijgt een hoger versienummer: verander `mod_version` in
  `gradle.properties` (bijvoorbeeld `2.0.1` voor een fix, `2.1.0` voor nieuwe dingen), bouw
  opnieuw en schrijf een nieuwe changelog.
- Je oude `starktech-2.0.0.jar` in je CurseForge-instances (BuildingsForMod en PVP) vervang je
  door de nieuwe jar. Je spraakmodellen en instellingen verhuizen vanzelf mee.
- Het logo en het projectplaatje maak je opnieuw met `python tools/generate_logo.py`.
