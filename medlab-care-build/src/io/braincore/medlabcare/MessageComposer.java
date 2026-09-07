package io.braincore.medlabcare;

public final class MessageComposer {
    private MessageComposer() {}

    public static String compose(
            String person,
            String timestamp,
            TriageEngine.Result result,
            Boolean breathingOk,
            Boolean chestPressure,
            Boolean swallowOk,
            Integer systolic,
            Integer diastolic,
            Integer heartRate,
            Integer spo2,
            String note) {
        StringBuilder sb = new StringBuilder();
        sb.append("MEDLAB Care — riepilogo\n");
        sb.append("Persona: ").append(safe(person, "Non indicato")).append('\n');
        sb.append("Data/ora: ").append(safe(timestamp, "Non indicata")).append('\n');
        if (result != null) sb.append("Esito app: ").append(levelIt(result.level)).append('\n');
        if (breathingOk != null) sb.append("Respira e parla normalmente: ").append(yesNo(breathingOk)).append('\n');
        if (chestPressure != null) sb.append("Dolore/pressione al petto: ").append(yesNo(chestPressure)).append('\n');
        if (swallowOk != null) sb.append("Deglutisce acqua e saliva: ").append(yesNo(swallowOk)).append('\n');
        if (systolic != null || diastolic != null) {
            sb.append("Pressione: ")
                    .append(systolic == null ? "?" : systolic)
                    .append('/')
                    .append(diastolic == null ? "?" : diastolic)
                    .append(" mmHg\n");
        }
        if (heartRate != null) sb.append("Battito: ").append(heartRate).append(" bpm\n");
        if (spo2 != null) sb.append("SpO2: ").append(spo2).append("%\n");
        if (note != null && !note.trim().isEmpty()) sb.append("Nota: ").append(note.trim()).append('\n');
        if (result != null) sb.append("Indicazione: ").append(result.guidance).append('\n');
        sb.append("Bozza non validata clinicamente: non fa diagnosi e non sostituisce medico, CAU, 112/118 o Pronto Soccorso.");
        return sb.toString();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Sì" : "No";
    }

    private static String levelIt(TriageEngine.Level level) {
        if (level == TriageEngine.Level.RED) return "ROSSO";
        if (level == TriageEngine.Level.YELLOW) return "GIALLO";
        return "VERDE";
    }
}
