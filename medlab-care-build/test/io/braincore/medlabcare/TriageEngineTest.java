package io.braincore.medlabcare;

public final class TriageEngineTest {
    public static void main(String[] args) {
        expect(TriageEngine.Level.RED, TriageEngine.assess(false, false, true, true).level, "breathing");
        expect(TriageEngine.Level.RED, TriageEngine.assess(true, true, true, true).level, "chest");
        expect(TriageEngine.Level.RED, TriageEngine.assess(true, false, false, true).level, "swallow");
        expect(TriageEngine.Level.YELLOW, TriageEngine.assess(true, false, true, true).level, "unwell");
        expect(TriageEngine.Level.GREEN, TriageEngine.assess(true, false, true, false).level, "well");

        String msg = MessageComposer.compose(
                "Persona", "07/09/2026 16:30",
                TriageEngine.assess(true, false, true, true),
                true, false, true,
                120, 75, 65, 98,
                "test"
        );
        if (!msg.contains("Pressione: 120/75 mmHg")) throw new AssertionError("message pressure");
        if (!msg.contains("SpO2: 98%")) throw new AssertionError("message spo2");
        if (!msg.contains("Esito app: GIALLO")) throw new AssertionError("message level");
        System.out.println("MEDLAB Care core tests: PASS");
    }

    private static void expect(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + " but got " + actual);
        }
    }
}
