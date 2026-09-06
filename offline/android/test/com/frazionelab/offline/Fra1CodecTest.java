package com.frazionelab.offline;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Fra1CodecTest {
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void checkEq(String expected, String actual, String message) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(message + "\nexpected=" + expected + "\nactual=" + actual);
    }

    public static void main(String[] args) throws Exception {
        textRoundTrip();
        originalFileRoundTrip();
        deterministicCompatibilityVector();
        tamperDetection();
        System.out.println("PASS " + checks + " checks");
    }

    private static void textRoundTrip() throws Exception {
        String original = "Ciao Cris — FRA1 ✓ 🌱";
        String fraction = Fra1Codec.encodeTextFraction(original);
        check(fraction.contains(" / 2^"), "fraction syntax");
        checkEq(original, Fra1Codec.decodeTextFraction(fraction), "UTF-8 text round trip");
    }

    private static void originalFileRoundTrip() throws Exception {
        byte[] bytes = new byte[] {0, 1, 2, 3, (byte) 0x80, (byte) 0xff, 0, 42};
        byte[] packet = Fra1Codec.packOriginalFile("DSC_é.JPG", "image/jpeg", bytes);
        Fra1Codec.OriginalFile restored = Fra1Codec.unpackOriginalFile(packet);
        checkEq("DSC_é.JPG", restored.name, "name preserved");
        checkEq("image/jpeg", restored.mime, "mime preserved");
        check(Arrays.equals(bytes, restored.bytes), "payload byte-for-byte");
        check(restored.hashVerified, "SHA-256 verified");
    }

    private static void deterministicCompatibilityVector() throws Exception {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] packet = Fra1Codec.packOriginalFile("a.jpg", "image/jpeg", bytes);
        String expectedHex = "46524131030000003a0005000a00000003ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad612e6a7067696d6167652f6a706567616263";
        checkEq(expectedHex, Fra1Codec.toHex(packet), "FRA1 binary format compatibility vector");
    }

    private static void tamperDetection() throws Exception {
        byte[] packet = Fra1Codec.packOriginalFile("a.jpg", "image/jpeg", new byte[] {1,2,3,4});
        packet[packet.length - 1] ^= 1;
        boolean failed = false;
        try {
            Fra1Codec.unpackOriginalFile(packet);
        } catch (Fra1Codec.Fra1Exception expected) {
            failed = expected.getMessage().contains("SHA-256");
        }
        check(failed, "tampered packet must fail SHA-256 verification");
    }
}
