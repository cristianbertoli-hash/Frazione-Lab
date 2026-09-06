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
        fra1eAes256CompatibilityVector();
        fra1eAes128RoundTrip();
        fra1eWrongPasswordFails();
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

    private static void fra1eAes256CompatibilityVector() throws Exception {
        byte[] plain = hex("46524131030000003a0005000a00000003ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad612e6a7067696d6167652f6a706567616263");
        byte[] salt = new byte[16];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte)i;
        byte[] iv = new byte[12];
        for (int i = 0; i < iv.length; i++) iv[i] = (byte)(0xa0 + i);
        byte[] encrypted = Fra1Codec.encryptFra1(plain, "Test password 123!", 256, salt, iv, 600000);
        String expected = "46524131450101010001000927c0100c00000053000102030405060708090a0b0c0d0e0fa0a1a2a3a4a5a6a7a8a9aaab5f37567eeaa243d7e69eced3f9145dace37dbe7d7191ba586bf3aa031fa2fa85b53900cfce244e1f2c52392d35ffdbf0e682c6f881cab8a97e8d22b8d331e6a178abd08353230613b167ea824664f14e9ed7c8";
        checkEq(expected, Fra1Codec.toHex(encrypted), "FRA1E AES-256 cross-platform vector");
        Fra1Codec.ContainerInfo info = Fra1Codec.inspectContainer(encrypted);
        check(info.encrypted, "FRA1E detected automatically");
        check(info.keyBits == 256, "AES-256 detected automatically");
        check(info.iterations == 600000, "PBKDF2 iterations detected");
        check(Arrays.equals(plain, Fra1Codec.decryptFra1(encrypted, "Test password 123!")), "FRA1E AES-256 decrypts byte-for-byte");
    }

    private static void fra1eAes128RoundTrip() throws Exception {
        byte[] fra1 = Fra1Codec.packOriginalFile("foto.jpg", "image/jpeg", new byte[]{9,8,7,6,5,4,3,2,1});
        byte[] encrypted = Fra1Codec.encryptFra1(fra1, "Una password sicura 2026!", 128);
        Fra1Codec.ContainerInfo info = Fra1Codec.inspectContainer(encrypted);
        check(info.encrypted && info.keyBits == 128, "AES-128 header detection");
        byte[] decrypted = Fra1Codec.decryptFra1(encrypted, "Una password sicura 2026!");
        check(Arrays.equals(fra1, decrypted), "AES-128 round trip");
        check(encrypted.length == fra1.length + 64, "FRA1E fixed overhead is 64 bytes");
    }

    private static void fra1eWrongPasswordFails() throws Exception {
        byte[] fra1 = Fra1Codec.packOriginalFile("x.bin", "application/octet-stream", new byte[]{1,2,3});
        byte[] encrypted = Fra1Codec.encryptFra1(fra1, "Password corretta 123", 256);
        boolean failed = false;
        try { Fra1Codec.decryptFra1(encrypted, "Password sbagliata"); }
        catch (Fra1Codec.Fra1Exception expected) { failed = true; }
        check(failed, "wrong password must fail authenticated decryption");
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length()/2];
        for (int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return out;
    }
}
