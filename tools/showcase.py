"""
Stark Tech showcase: start het spel en laat alle functies één voor één zien.

Gebruik (vanuit de projectmap):
    python tools/showcase.py
    python tools/showcase.py --vanaf 12     # begin bij functie 12
    python tools/showcase.py --vanaf 12 --tot 15   # alleen functie 12 tot en met 15

Eerst vraagt het script of je een pauze wilt tussen de functies. Met pauze wacht het na elke
functie op jou. Je typt iets en drukt op Enter:
    Enter        volgende functie
    s            volgende functie (overslaan)
    v            vorige functie
    r            deze functie nog eens
    q            stoppen
    een getal    naar die functie springen
    iets anders  wordt als notitie bij deze functie bewaard (in showcase-notities.md)

Dat typen kan ook al terwijl een functie speelt: een notitie wordt meteen bewaard en de functie
speelt gewoon door. Typ je s, v, r, q of een getal (en Enter), dan stopt de functie meteen en
gaat het script daarheen. Een S midden in een notitie telt dus niet als overslaan: alleen wat
je in zijn geheel typt, telt.
Let op: de terminal moet dan actief zijn (klik er eerst op), anders krijgt het spel je toetsen.

Hoe het werkt: het script start "gradlew runShowcase". Het spel maakt een testwereld en
luistert op 127.0.0.1:25599 (alleen je eigen computer). Het script vraagt het spel via die
verbinding om een functie te tonen, en het spel zegt wanneer die klaar is. Zie
src/main/java/com/senne/starktech/client/dev/Showcase.java voor de kant van het spel.

Geen extra Python-pakketten nodig.
"""
import argparse
import datetime
import os
import queue
import select
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

# Kleurtjes in de terminal (werkt in Windows Terminal en de terminal van VS Code).
os.system("")
CYAN, GREEN, ORANGE, GREY, BOLD, RESET = "\033[96m", "\033[92m", "\033[93m", "\033[90m", "\033[1m", "\033[0m"

# Wat voor je tekst staat als je begint te typen terwijl een functie speelt.
TYPING_PROMPT = f"   {GREY}>{RESET} "
PAUSE_PROMPT = (f"   {GREY}[Enter] volgende  [v] vorige  [r] opnieuw  [q] stoppen  "
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
    commando (s, v, r, q, een getal) of anders een notitie.

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


def choose(answer, number, total):
    """
    Wat bedoel je met wat je typte? Geeft het nummer van de functie waar je heen wilt, of None
    als het een notitie is. Een nummer hoger dan het totaal betekent: stoppen.
    """
    word = answer.lower()
    if word in ("", "s", "skip", "volgende"):
        return number + 1
    if word in ("v", "vorige"):
        return max(1, number - 1)
    if word in ("r", "opnieuw"):
        return number
    if word in ("q", "stop", "stoppen"):
        return total + 1
    if word.isdigit() and 1 <= int(word) <= total:
        return int(word)
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


def show(link, keyboard, number, total, title, watch, session):
    """
    Laat één functie zien en wacht tot het spel zegt dat hij klaar is. Ondertussen kun je typen:
    een notitie wordt meteen bewaard. Typ je een commando (s, v, r, q, een getal), dan stopt de
    functie en geeft dit het nummer terug waar je heen wilt. Vanzelf klaar = None.
    """
    keyboard.say()
    keyboard.say(f"{BOLD}{CYAN}[{number}/{total}] {title}{RESET}")
    for line in textwrap.wrap(watch, width=90):
        keyboard.say(f"      {line}")
    link.send("SHOW", number)
    chosen = None
    while True:
        # Hooguit 0,1 seconde wachten op een bericht, zodat we tussendoor steeds naar het
        # toetsenbord kunnen kijken.
        parts = link.next(timeout=0.1)
        if parts is None:
            raise GameClosed()
        if parts and parts[0] == "SAY":
            keyboard.say(say_text(parts))
        elif parts and parts[0] == "DONE" and parts[1] == str(number):
            return chosen
        if chosen is not None:
            continue  # al gestopt, wachten op DONE van het spel
        answer = keyboard.poll()
        if not answer:
            continue  # niets getypt, of alleen Enter: dat telt tijdens een functie niet
        chosen = choose(answer, number, total)
        if chosen is None:
            save_note(number, title, answer, session)
            keyboard.say(f"   {GREEN}Notitie bewaard.{RESET}")
        else:
            # Het spel stopt de functie en stuurt dan gewoon DONE; daar wachten we nog op.
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
    parser = argparse.ArgumentParser(description="Laat alle functies van Stark Tech zien.")
    parser.add_argument("--vanaf", type=int, default=1, help="begin bij deze functie (nummer)")
    parser.add_argument("--tot", type=int, default=0, help="stop na deze functie (nummer); 0 = tot het einde")
    parser.add_argument("--test", action="store_true",
                        help="voor het ontwikkelen: geen pauzes en een screenshot van elke functie")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        # line_buffering: elke regel meteen tonen, ook als de uitvoer naar een bestand gaat.
        sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)

    print(f"{BOLD}Stark Tech showcase{RESET}")
    pauses = False if args.test else ask_yes_no("Wil je een pauze tussen elke functie")
    print(f"{GREY}Typ (ook tijdens een functie) en druk op Enter: s = overslaan, v = vorige, "
          f"r = opnieuw, q = stoppen, een getal = naar die functie, iets anders = notitie "
          f"(komt in {NOTES.name}). Klik eerst op deze terminal.{RESET}")
    print()

    game = start_game()
    sock = connect(game)
    if sock is None:
        return
    link = Link(sock)
    features = wait_for_ready(link)
    if features is None:
        print(f"{ORANGE}Het spel is gesloten.{RESET}")
        return
    total = len(features)
    print(f"{GREEN}Klaar: {total} functies. Kijk naar het spelvenster.{RESET}")
    last = min(args.tot, total) if args.tot > 0 else total
    if args.test:
        link.send("SHOTS")

    keyboard = Keyboard()
    session = {"header": False}
    number = max(1, min(args.vanaf, total))
    try:
        while number <= last:
            title, watch = features[number - 1]
            chosen = show(link, keyboard, number, total, title, watch, session)
            if chosen is not None:
                # Je koos tijdens de functie al waar het heen moet: geen pauze.
                number = chosen
                continue
            if not pauses:
                time.sleep(2)
                number += 1
                continue
            while True:
                answer = keyboard.ask(PAUSE_PROMPT)
                chosen = choose(answer, number, total)
                if chosen is not None:
                    number = chosen
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
