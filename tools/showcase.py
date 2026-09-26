"""
Stark Tech showcase: start het spel en laat de functies één voor één zien.

Gebruik (vanuit de projectmap):
    python tools/showcase.py
    python tools/showcase.py --vanaf 12     # begin bij functie 12
    python tools/showcase.py --vanaf 12 --tot 15   # alleen functie 12 tot en met 15

Eerst zie je de INHOUD: alle functies per deel (De bril, Drones, ...), met bovenaan het kopje
NIEUW voor wat je nog niet gezien hebt. Terwijl je leest, start het spel al op. Dan kies je
wat je wilt zien:
    Enter        alles
    n            alleen de nieuwe
    3 7 12       die functies
    10-15        functie 10 tot en met 15
    drones       een heel deel (een stukje van de naam is genoeg: bril, harnas, vijanden...)
Je mag ze mengen, bijvoorbeeld "n 3-5 harnas".

Een functie gaat uit NIEUW zodra je hem bekeken hebt: hij speelde tot het eind, of je stopte
hem pas na 8 seconden. Wat je meteen overslaat, blijft nieuw. Wat je gezien hebt, staat in
showcase-gezien.json (niet in git); gooi dat bestand weg en alles is weer nieuw.

Daarna vraagt het script of je een pauze wilt tussen de functies. Met pauze wacht het na elke
functie op jou. Je typt iets en drukt op Enter:
    Enter        volgende functie
    s            volgende functie (overslaan)
    v            vorige functie
    r            deze functie nog eens
    i            de inhoud weer zien en iets anders kiezen
    q            stoppen
    een getal    naar die functie springen
    iets anders  wordt als notitie bij deze functie bewaard (in showcase-notities.md)

Dat typen kan ook al terwijl een functie speelt: een notitie wordt meteen bewaard en de functie
speelt gewoon door. Typ je s, v, r, i, q of een getal (en Enter), dan stopt de functie meteen
en gaat het script daarheen. Een S midden in een notitie telt dus niet als overslaan: alleen
wat je in zijn geheel typt, telt.
Let op: de terminal moet dan actief zijn (klik er eerst op), anders krijgt het spel je toetsen.

Hoe het werkt: het script start "gradlew runShowcase". Het spel maakt een testwereld en
luistert op 127.0.0.1:25599 (alleen je eigen computer). Het script vraagt het spel via die
verbinding om een functie te tonen, en het spel zegt wanneer die klaar is. Zie
src/main/java/com/senne/starktech/client/dev/Showcase.java voor de kant van het spel.
De inhoud leest het script zelf uit ShowcaseFeatures.java (de titels en de kopjes
"// ----- Naam"), zodat je hem al ziet voordat het spel klaar is.

Geen extra Python-pakketten nodig.
"""
import argparse
import datetime
import json
import os
import queue
import re
import select
import shutil
import socket
import subprocess
import sys
import textwrap
import threading
import time
from pathlib import Path

try:
    import msvcrt  # alleen op Windows: toetsen lezen zonder op Enter te wachten
except ImportError:
    msvcrt = None

ROOT = Path(__file__).resolve().parent.parent
PORT = 25599
LOG = ROOT / "run-showcase" / "showcase-gradle.log"
NOTES = ROOT / "showcase-notities.md"
SEEN = ROOT / "showcase-gezien.json"
FEATURES_JAVA = ROOT / "src" / "main" / "java" / "com" / "senne" / "starktech" / "client" / "dev" / "ShowcaseFeatures.java"
# Zo lang (seconden) moet een functie gespeeld hebben om als bekeken te tellen, als je hem stopt.
SEEN_AFTER = 8

# Kleurtjes in de terminal (werkt in Windows Terminal en de terminal van VS Code).
os.system("")
CYAN, GREEN, ORANGE, GREY, BOLD, RESET = "\033[96m", "\033[92m", "\033[93m", "\033[90m", "\033[1m", "\033[0m"

# Wat voor je tekst staat als je begint te typen terwijl een functie speelt.
TYPING_PROMPT = f"   {GREY}>{RESET} "
PAUSE_PROMPT = (f"   {GREY}[Enter] volgende  [v] vorige  [r] opnieuw  [i] inhoud  [q] stoppen  "
                f"of typ een notitie >{RESET} ")


class GameClosed(Exception):
    """Het spel is weg (gesloten of gecrasht)."""


def ask_yes_no(question, default=True):
    """Vraag met (Y/n) of (y/N): Enter kiest wat in HOOFDLETTERS staat."""
    hint = "Y/n" if default else "y/N"
    while True:
        answer = input(f"{question} ({hint}): ").strip().lower()
        if answer == "":
            return default
        if answer in ("y", "yes", "j", "ja"):
            return True
        if answer in ("n", "no", "nee"):
            return False
        print("  Typ Y (ja) of N (nee), of druk op Enter voor de standaard.")


# ================================================================ De inhoud

def read_contents():
    """
    Leest de functies uit ShowcaseFeatures.java: elke add("Titel", ...) is een functie, en een
    kopje "// ------ Naam" erboven zegt bij welk deel hij hoort. Geeft een lijst van
    (deel, titel), in de volgorde van de showcase (dus functie 1 is de eerste).
    """
    contents = []
    section = "Overig"
    try:
        lines = FEATURES_JAVA.read_text(encoding="utf-8").splitlines()
    except OSError:
        return contents
    for line in lines:
        heading = re.match(r"\s*// -{10,}\s*(.+?)\s*$", line)
        if heading:
            section = heading.group(1)
            continue
        feature = re.match(r'\s*add\("((?:[^"\\]|\\.)*)"', line)
        if feature:
            contents.append((section, feature.group(1).replace('\\"', '"')))
    return contents


def load_seen():
    """De titels van de functies die je al bekeken hebt."""
    try:
        return set(json.loads(SEEN.read_text(encoding="utf-8")).get("gezien", []))
    except (OSError, ValueError):
        return set()


def save_seen(seen):
    SEEN.write_text(json.dumps({"gezien": sorted(seen)}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def print_contents(contents, seen):
    """Alle functies per deel, met bovenaan NIEUW. Twee kolommen als de terminal breed genoeg is."""
    width = shutil.get_terminal_size((100, 30)).columns
    columns = 2 if width >= 96 else 1
    column_width = (width - 4) // columns

    def entry(number, title, new):
        mark = f"{ORANGE}*{RESET}" if new else " "
        text = title if len(title) <= column_width - 7 else title[:column_width - 8] + "…"
        # De kleurcodes tellen niet mee voor de breedte: daarom zelf aanvullen met spaties.
        return f"{mark}{GREY}{number:>3}{RESET}  {text}" + " " * (column_width - 6 - len(text))

    def block(heading, items, colour=CYAN):
        print(f"\n  {BOLD}{colour}{heading}{RESET}")
        rows = (len(items) + columns - 1) // columns
        for row in range(rows):
            # Van boven naar beneden, dan pas de volgende kolom: dan loopt de nummering netjes.
            cells = [items[row + rows * c] for c in range(columns) if row + rows * c < len(items)]
            print("  " + "".join(entry(*cell) for cell in cells).rstrip())

    print(f"\n{BOLD}Inhoud{RESET}  {GREY}({len(contents)} functies; {ORANGE}*{GREY} = nieuw){RESET}")
    new = [(n, title, True) for n, (_, title) in enumerate(contents, 1) if title not in seen]
    if new:
        block(f"NIEUW ({len(new)})", new, ORANGE)
    sections = []
    for n, (section, title) in enumerate(contents, 1):
        if not sections or sections[-1][0] != section:
            sections.append((section, []))
        sections[-1][1].append((n, title, title not in seen))
    for section, items in sections:
        block(section, items)
    print()


def pick(answer, contents, seen):
    """
    Wat je koos, als lijst van nummers (in showcase-volgorde), of None als iets niet klopte
    (dan heeft dit al gezegd wat).
    """
    total = len(contents)
    words = answer.replace(",", " ").lower().split()
    if not words or words in (["a"], ["alles"], ["all"]):
        return list(range(1, total + 1))
    chosen = set()
    for word in words:
        if word in ("n", "nieuw", "new"):
            new = [n for n, (_, title) in enumerate(contents, 1) if title not in seen]
            if not new:
                print(f"  {ORANGE}Er is niets nieuws: je hebt alles al gezien.{RESET}")
                return None
            chosen.update(new)
        elif word in ("a", "alles", "all"):
            chosen.update(range(1, total + 1))
        elif re.fullmatch(r"\d+", word):
            if not 1 <= int(word) <= total:
                print(f"  {ORANGE}Er is geen functie {word} (1 tot {total}).{RESET}")
                return None
            chosen.add(int(word))
        elif re.fullmatch(r"\d+-\d+", word):
            first, last = sorted(int(x) for x in word.split("-"))
            chosen.update(range(max(1, first), min(total, last) + 1))
        else:
            matches = [n for n, (section, _) in enumerate(contents, 1) if word in section.lower()]
            if not matches:
                print(f"  {ORANGE}Geen deel met '{word}' in de naam.{RESET}")
                return None
            chosen.update(matches)
    return sorted(chosen)


def ask_playlist(contents, seen):
    """Laat de inhoud zien en vraag wat je wilt zien. Geeft de nummers terug."""
    print_contents(contents, seen)
    hint = "n = de nieuwe, " if any(title not in seen for _, title in contents) else ""
    while True:
        answer = input(f"Wat wil je zien? {GREY}(Enter = alles, {hint}nummers zoals 3 7 12, "
                       f"van-tot zoals 10-15, of een deel zoals drones){RESET}: ")
        playlist = pick(answer, contents, seen)
        if playlist:
            return playlist


# ================================================================ Het spel

def start_game():
    """Start het spel via Gradle; alles wat Gradle zegt gaat naar een logbestand."""
    LOG.parent.mkdir(parents=True, exist_ok=True)
    gradle = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    log = open(LOG, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen([str(gradle), "runShowcase", "--console=plain"], cwd=ROOT,
                            stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)


def connect(game):
    """Wacht tot het spel klaar is om te praten. Geeft de verbinding terug, of None."""
    begin = time.time()
    while True:
        if game.poll() is not None:
            print(f"\n{ORANGE}Het spel is gestopt voordat het klaar was. Kijk in {LOG}{RESET}")
            return None
        try:
            sock = socket.create_connection(("127.0.0.1", PORT), timeout=2)
            # De 2 seconden gelden alleen voor het verbinden. Daarna mag het spel zo lang stil
            # zijn als het wil (het klaarzetten van de wereld duurt even).
            sock.settimeout(None)
            return sock
        except OSError:
            seconds = int(time.time() - begin)
            print(f"\r{GREY}Het spel start op... {seconds} s{RESET}", end="", flush=True)
            time.sleep(1)


class Link:
    """De verbinding met het spel: berichten van één regel, met tabs ertussen."""

    def __init__(self, sock):
        self.sock = sock
        self.messages = queue.Queue()
        self.reader = sock.makefile("r", encoding="utf-8", newline="\n")
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        try:
            for line in self.reader:
                # Beide soorten regeleinde weghalen (Windows stuurt soms "\r\n").
                self.messages.put(line.rstrip("\r\n").split("\t"))
        except OSError:
            pass
        self.messages.put(None)  # verbinding weg: het spel is gesloten

    def send(self, *parts):
        try:
            self.sock.sendall(("\t".join(str(p) for p in parts) + "\n").encode("utf-8"))
        except OSError:
            pass

    def next(self, timeout=None):
        try:
            return self.messages.get(timeout=timeout)
        except queue.Empty:
            return []


class Keyboard:
    """
    Wat je in de terminal typt. Op Windows lezen we letter voor letter, zonder te wachten, zodat
    het spel gewoon doorspeelt terwijl jij typt. Pas als je op Enter drukt, telt de regel: een
    commando (s, v, r, i, q, een getal) of anders een notitie.

    Alles wat het script print, gaat via say(): dan komt het boven je halve regel te staan in
    plaats van er dwars doorheen.
    """

    def __init__(self):
        self.text = ""    # wat je al getypt hebt, nog zonder Enter
        self.prompt = ""  # wat er voor je tekst staat; "" = je typregel staat niet in beeld

    def _redraw(self):
        # \r = terug naar het begin van de regel, \033[K = de rest van de regel wissen.
        print(f"\r\033[K{self.prompt}{self.text}", end="", flush=True)

    def poll(self):
        """Kijkt even naar het toetsenbord, wacht nooit. Na Enter: de regel, anders None."""
        if msvcrt is None:
            # Mac en Linux: de terminal toont zelf wat je typt en geeft het pas na Enter door.
            if sys.stdin.isatty() and select.select([sys.stdin], [], [], 0)[0]:
                return sys.stdin.readline().strip()
            return None
        while msvcrt.kbhit():
            key = msvcrt.getwch()
            if key in ("\x00", "\xe0"):
                msvcrt.getwch()  # pijltjes en F-toetsen bestaan uit twee delen: negeren
            elif key in ("\r", "\n"):
                line = self.text.strip()
                if self.prompt:
                    print()  # je regel blijft staan, verder op de volgende
                self.text, self.prompt = "", ""
                return line
            elif key in ("\x08", "\x7f"):  # Backspace
                self.text = self.text[:-1]
                if self.prompt:
                    self._redraw()
            elif key == "\x1b":  # Esc: alles wissen
                self.text = ""
                if self.prompt:
                    self._redraw()
            elif key.isprintable():
                if not self.prompt:
                    self.prompt = TYPING_PROMPT
                self.text += key
                self._redraw()
        return None

    def forget(self):
        """Wat je half getypt had weggooien (voordat we met input() iets vragen)."""
        while msvcrt is not None and msvcrt.kbhit():
            msvcrt.getwch()
        self.text, self.prompt = "", ""

    def say(self, line=""):
        """Print een regel boven wat je aan het typen bent."""
        if self.prompt:
            print("\r\033[K", end="")  # je typregel even weghalen
        print(line)
        if self.prompt:
            self._redraw()  # en weer terugzetten

    def ask(self, prompt):
        """Wacht op een hele regel (de pauze na een functie). Wat je al typte, blijft staan."""
        if msvcrt is None:
            return input(prompt).strip()
        self.prompt = prompt
        self._redraw()
        while True:
            line = self.poll()
            if line is not None:
                return line
            time.sleep(0.05)


def choose(answer, total):
    """
    Wat bedoel je met wat je typte? Een commando: ("next",), ("prev",), ("again",),
    ("contents",), ("quit",) of ("goto", nummer). None = het is een notitie.
    """
    word = answer.lower()
    if word in ("", "s", "skip", "volgende"):
        return ("next",)
    if word in ("v", "vorige"):
        return ("prev",)
    if word in ("r", "opnieuw"):
        return ("again",)
    if word in ("i", "inhoud"):
        return ("contents",)
    if word in ("q", "stop", "stoppen"):
        return ("quit",)
    if word.isdigit() and 1 <= int(word) <= total:
        return ("goto", int(word))
    return None


def say_text(parts):
    level, text = (parts[1], parts[2]) if len(parts) >= 3 else ("INFO", parts[-1])
    colour = ORANGE if level in ("WARNING", "ALERT") else GREY
    return f"      {colour}E.D.I.T.H.: {text}{RESET}"


def wait_for_ready(link):
    """Leest de lijst met functies tot het spel READY zegt."""
    features = []
    print(f"\r{GREY}Verbonden. De testwereld wordt klaargezet...{RESET}      ")
    while True:
        parts = link.next()
        if parts is None:
            return None
        if parts and parts[0] == "FEATURE":
            features.append((parts[2], parts[3] if len(parts) > 3 else ""))
        elif parts and parts[0] == "READY":
            return features


def show(link, keyboard, header, number, total, title, watch, session):
    """
    Laat één functie zien en wacht tot het spel zegt dat hij klaar is. Ondertussen kun je typen:
    een notitie wordt meteen bewaard. Typ je een commando (s, v, r, i, q, een getal), dan stopt
    de functie. Geeft (commando of None, hoeveel seconden hij speelde) terug.
    """
    keyboard.say()
    keyboard.say(f"{BOLD}{CYAN}{header} {title}{RESET}")
    for line in textwrap.wrap(watch, width=90):
        keyboard.say(f"      {line}")
    link.send("SHOW", number)
    begin = time.time()
    chosen = None
    played = None
    while True:
        # Hooguit 0,1 seconde wachten op een bericht, zodat we tussendoor steeds naar het
        # toetsenbord kunnen kijken.
        parts = link.next(timeout=0.1)
        if parts is None:
            raise GameClosed()
        if parts and parts[0] == "SAY":
            keyboard.say(say_text(parts))
        elif parts and parts[0] == "DONE" and parts[1] == str(number):
            return chosen, played if played is not None else time.time() - begin
        if chosen is not None:
            continue  # al gestopt, wachten op DONE van het spel
        answer = keyboard.poll()
        if not answer:
            continue  # niets getypt, of alleen Enter: dat telt tijdens een functie niet
        chosen = choose(answer, total)
        if chosen is None:
            save_note(number, title, answer, session)
            keyboard.say(f"   {GREEN}Notitie bewaard.{RESET}")
        else:
            # Het spel stopt de functie en stuurt dan gewoon DONE; daar wachten we nog op.
            played = time.time() - begin
            link.send("SKIP", number)
            keyboard.say(f"      {ORANGE}Gestopt.{RESET}")


def save_note(number, title, note, session):
    new_file = not NOTES.exists()
    with open(NOTES, "a", encoding="utf-8") as f:
        if new_file:
            f.write("# Notities bij de Stark Tech showcase\n")
        if not session["header"]:
            stamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
            f.write(f"\n## Showcase van {stamp}\n")
            session["header"] = True
        if session.get("last") != number:
            f.write(f"\n### {number}. {title}\n")
            session["last"] = number
        f.write(f"- {note}\n")


def main():
    parser = argparse.ArgumentParser(description="Laat de functies van Stark Tech zien.")
    parser.add_argument("--vanaf", type=int, default=0, help="begin bij deze functie (nummer); zonder: kies uit de inhoud")
    parser.add_argument("--tot", type=int, default=0, help="stop na deze functie (nummer); 0 = tot het einde")
    parser.add_argument("--test", action="store_true",
                        help="voor het ontwikkelen: geen vragen, geen pauzes, een screenshot van elke "
                             "functie, en niets telt als bekeken")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        # line_buffering: elke regel meteen tonen, ook als de uitvoer naar een bestand gaat.
        sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)

    print(f"{BOLD}Stark Tech showcase{RESET}")
    # Het spel start meteen op; ondertussen lees jij de inhoud en kies je.
    game = start_game()
    contents = read_contents()
    seen = load_seen()
    playlist = None  # None = kies straks, als we het totaal kennen (met --vanaf/--tot of --test)
    if not args.test and args.vanaf == 0 and args.tot == 0 and contents:
        playlist = ask_playlist(contents, seen)
    pauses = False if args.test else ask_yes_no("Wil je een pauze tussen elke functie")
    print(f"{GREY}Typ (ook tijdens een functie) en druk op Enter: s = overslaan, v = vorige, "
          f"r = opnieuw, i = inhoud, q = stoppen, een getal = naar die functie, iets anders = "
          f"notitie (komt in {NOTES.name}). Klik eerst op deze terminal.{RESET}")
    print()

    sock = connect(game)
    if sock is None:
        return
    link = Link(sock)
    features = wait_for_ready(link)
    if features is None:
        print(f"{ORANGE}Het spel is gesloten.{RESET}")
        return
    total = len(features)
    if [title for _, title in contents] != [title for title, _ in features]:
        # Bv. een functie toegevoegd zonder add("...") aan het begin van de regel. Dan geldt de
        # lijst van het spel, en zonder delen.
        print(f"{ORANGE}Let op: de inhoud uit ShowcaseFeatures.java klopt niet helemaal met het spel.{RESET}")
        contents = [("Alles", title) for title, _ in features]
        if playlist is not None:
            playlist = [n for n in playlist if n <= total]
    if playlist is None:
        first = max(1, min(args.vanaf or 1, total))
        last = min(args.tot, total) if args.tot > 0 else total
        playlist = list(range(first, last + 1))
    print(f"{GREEN}Klaar: {len(playlist)} van de {total} functies. Kijk naar het spelvenster.{RESET}")
    if args.test:
        link.send("SHOTS")

    keyboard = Keyboard()
    session = {"header": False}
    position = 0  # waar we zijn in de lijst die je koos

    def go(command):
        """Voer een commando uit; geeft False terug als we moeten stoppen."""
        nonlocal position, playlist
        if command[0] == "next":
            position += 1
        elif command[0] == "prev":
            position = max(0, position - 1)
        elif command[0] == "quit":
            position = len(playlist)
        elif command[0] == "goto":
            if command[1] not in playlist:
                # Niet in je keuze: speel hem hierna, en ga daarna verder waar je was.
                playlist.insert(position + 1, command[1])
            position = playlist.index(command[1])
        elif command[0] == "contents":
            keyboard.forget()
            playlist = ask_playlist(contents, seen)
            position = 0
        return position < len(playlist)

    try:
        while position < len(playlist):
            number = playlist[position]
            title, watch = features[number - 1]
            new = f" {ORANGE}(nieuw){CYAN}" if title not in seen else ""
            header = f"[{position + 1}/{len(playlist)}] {number}.{new}"
            chosen, played = show(link, keyboard, header, number, total, title, watch, session)
            if not args.test and (chosen is None or played >= SEEN_AFTER) and title not in seen:
                seen.add(title)
                save_seen(seen)
            if chosen is not None:
                # Je koos tijdens de functie al waar het heen moet: geen pauze.
                go(chosen)
                continue
            if not pauses:
                time.sleep(2)
                position += 1
                continue
            while True:
                answer = keyboard.ask(PAUSE_PROMPT)
                command = choose(answer, total)
                if command is not None:
                    go(command)
                    break
                save_note(number, title, answer, session)
                keyboard.say(f"   {GREEN}Notitie bewaard.{RESET}")
        print(f"\n{GREEN}Klaar!{RESET}", end=" ")
        if session["header"]:
            print(f"Je notities staan in {NOTES}")
        else:
            print()
    except GameClosed:
        print(f"\n{ORANGE}Het spel is gesloten.{RESET}")
    except KeyboardInterrupt:
        print()
    finally:
        link.send("QUIT")
        try:
            game.wait(timeout=30)
        except subprocess.TimeoutExpired:
            game.terminate()


if __name__ == "__main__":
    main()
