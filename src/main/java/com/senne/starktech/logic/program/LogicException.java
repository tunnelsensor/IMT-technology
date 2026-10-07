package com.senne.starktech.logic.program;

/**
 * Er klopt iets niet in een diagram of in de code (een typfout, een onbekend blokje). Het bericht
 * is in begrijpelijk Engels, want je ziet het in het spel; {@link #line()} zegt op welke regel (of 0
 * als het niet bij een regel hoort, zoals in de blokjes).
 */
public class LogicException extends RuntimeException {
    private final int line;

    public LogicException(String message) {
        this(0, message);
    }

    public LogicException(int line, String message) {
        super(message);
        this.line = line;
    }

    public int line() {
        return line;
    }

    /** Dezelfde fout, maar nu met het regelnummer erbij. */
    public LogicException atLine(int line) {
        return this.line > 0 ? this : new LogicException(line, getMessage());
    }

    /** "Line 3: ..." (of alleen het bericht als er geen regel is). */
    public String describe() {
        return line > 0 ? "Line " + line + ": " + getMessage() : getMessage();
    }
}
