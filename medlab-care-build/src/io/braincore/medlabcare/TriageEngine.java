package io.braincore.medlabcare;

public final class TriageEngine {
    public enum Level { GREEN, YELLOW, RED }

    public static final class Result {
        public final Level level;
        public final String guidance;
        public Result(Level level, String guidance) {
            this.level = level;
            this.guidance = guidance;
        }
    }

    private TriageEngine() {}

    public static Result assess(boolean breathingOk, boolean chestPressure, boolean swallowOk, boolean userFeelsUnwell) {
        if (!breathingOk || chestPressure || !swallowOk) {
            return new Result(
                    Level.RED,
                    "Una risposta richiede prudenza. Chiedi aiuto subito: chiama 112/118 o fatti aiutare da una persona vicina."
            );
        }
        if (userFeelsUnwell) {
            return new Result(
                    Level.YELLOW,
                    "Non sono emersi i tre segnali critici controllati da questa bozza. Se continui a non sentirti bene, avvisa un familiare o un professionista sanitario."
            );
        }
        return new Result(
                Level.GREEN,
                "Hai indicato che stai bene. Se qualcosa cambia, usa NON STO BENE."
        );
    }
}
