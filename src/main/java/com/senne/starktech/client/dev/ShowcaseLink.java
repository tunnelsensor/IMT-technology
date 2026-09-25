package com.senne.starktech.client.dev;

import com.senne.starktech.StarkTech;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jetbrains.annotations.Nullable;

/**
 * De verbinding met tools/showcase.py: het script in je terminal vraagt het spel om de
 * volgende functie te tonen, en het spel zegt wanneer die klaar is.
 *
 * Het is een heel eenvoudig gesprek over je eigen computer (127.0.0.1, niet van buitenaf te
 * bereiken): elke regel tekst is één bericht, met tabs tussen de onderdelen.
 *
 * Van het script naar het spel:
 * - {@code SHOW <nummer>}: laat die functie zien
 * - {@code SKIP <nummer>}: stop die functie nu (het spel stuurt dan meteen DONE)
 * - {@code QUIT}: sluit het spel af
 *
 * Van het spel naar het script:
 * - {@code FEATURE <nummer> <titel> <waar je op let>}: de lijst, één regel per functie
 * - {@code READY <aantal>}: de wereld staat klaar, het script mag beginnen
 * - {@code START <nummer>} en {@code DONE <nummer>}: een functie begint en is klaar
 * - {@code SAY <tekst>}: wat E.D.I.T.H. zei (zo zie je waarschuwingen ook in je terminal)
 *
 * Het luisteren gebeurt op een eigen draad (thread), want wachten op een bericht mag het spel
 * niet stilzetten. Wat binnenkomt, gaat in een wachtrij die het spel elke tick leest.
 */
final class ShowcaseLink {
    /** De poort; tools/showcase.py gebruikt dezelfde. */
    static final int PORT = Integer.getInteger("starktech.showcase.port", 25599);

    private final ConcurrentLinkedQueue<String> incoming = new ConcurrentLinkedQueue<>();
    private volatile @Nullable PrintWriter out;
    private volatile boolean connected;
    private volatile boolean everConnected;

    /** Begint te luisteren (op de achtergrond) tot het script verbinding maakt. */
    void start() {
        Thread thread = new Thread(this::listen, "Stark Tech showcase link");
        thread.setDaemon(true);
        thread.start();
    }

    private void listen() {
        // Vast 127.0.0.1: "het loopback-adres" was in het spel het IPv6-adres ::1, en daar
        // kon het Python-script (dat 127.0.0.1 belt) geen verbinding mee maken.
        try (ServerSocket server = new ServerSocket(PORT, 1, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
            StarkTech.LOGGER.info("[showcase] wacht op tools/showcase.py op poort {}", PORT);
            while (true) {
                try (Socket socket = server.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                    connected = true;
                    everConnected = true;
                    String line;
                    while ((line = in.readLine()) != null) {
                        incoming.add(line.trim());
                    }
                } catch (IOException e) {
                    StarkTech.LOGGER.info("[showcase] verbinding met het script verbroken");
                } finally {
                    connected = false;
                    out = null;
                }
            }
        } catch (IOException e) {
            StarkTech.LOGGER.error("[showcase] kan niet luisteren op poort {}", PORT, e);
        }
    }

    boolean isConnected() {
        return connected;
    }

    /** Is het script ooit verbonden geweest (en nu weg)? Dan is de showcase voorbij. */
    boolean wasDisconnected() {
        return everConnected && !connected;
    }

    /** Het volgende bericht van het script, of null als er niets is. */
    @Nullable String poll() {
        return incoming.poll();
    }

    /** Stuurt een bericht; de onderdelen komen met tabs ertussen. */
    void send(Object... parts) {
        PrintWriter writer = out;
        if (writer == null) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                line.append('\t');
            }
            // Tabs en regeleinden in de tekst zelf zouden het bericht in stukken hakken.
            line.append(String.valueOf(parts[i]).replace('\t', ' ').replace('\n', ' '));
        }
        // Zelf een regeleinde erachter in plaats van println: op Windows zet println er "\r\n"
        // achter, en dan las het script "DONE 1\r" in plaats van "DONE 1" en wachtte het eeuwig.
        writer.print(line.append('\n'));
        writer.flush();
    }
}
