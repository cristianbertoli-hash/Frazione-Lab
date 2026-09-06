package com.frazionelab.offline;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Fra1Codec {
    private static final byte[] MAGIC = new byte[] {'F','R','A','1'};
    public static final int TYPE_TEXT = 1;
    public static final int TYPE_ORIGINAL_FILE = 3;
    private static final int HEADER = 9;
    private static final int META = 2 + 2 + 4 + 32;
    private static final Pattern FRACTION = Pattern.compile("^\\s*(\\d+)\\s*/\\s*2\\^(\\d+)\\s*$");

    private Fra1Codec() {}

    public static final class Fra1Exception extends Exception {
        public Fra1Exception(String message) { super(message); }
        public Fra1Exception(String message, Throwable cause) { super(message, cause); }
    }

    public static final class OriginalFile {
        public final String name;
        public final String mime;
        public final byte[] bytes;
        public final String sha256;
        public final boolean hashVerified;

        OriginalFile(String name, String mime, byte[] bytes, String sha256, boolean hashVerified) {
            this.name = name;
            this.mime = mime;
            this.bytes = bytes;
            this.sha256 = sha256;
            this.hashVerified = hashVerified;
        }
    }

    public static String encodeTextFraction(String text) {
        byte[] payload = String.valueOf(text).getBytes(StandardCharsets.UTF_8);
        return packetToFraction(makePacket(TYPE_TEXT, payload));
    }

    public static String decodeTextFraction(String fraction) throws Fra1Exception {
        byte[] packet = parseFraction(fraction);
        validateHeader(packet, TYPE_TEXT);
        int len = readU32(packet, 5);
        if (len != packet.length - HEADER) throw new Fra1Exception("Lunghezza FRA1 non valida");
        byte[] payload = Arrays.copyOfRange(packet, HEADER, packet.length);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException e) {
            throw new Fra1Exception("Testo UTF-8 non valido", e);
        }
    }

    public static byte[] packOriginalFile(String name, String mime, byte[] bytes) throws Fra1Exception {
        if (bytes == null) bytes = new byte[0];
        if (name == null || name.isEmpty()) name = "file.bin";
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
        byte[] nb = name.getBytes(StandardCharsets.UTF_8);
        byte[] mb = mime.getBytes(StandardCharsets.UTF_8);
        if (nb.length > 0xffff || mb.length > 0xffff) throw new Fra1Exception("Metadati troppo lunghi");
        long payloadLong = (long) META + nb.length + mb.length + bytes.length;
        if (payloadLong > 0xffffffffL || payloadLong > Integer.MAX_VALUE - HEADER) throw new Fra1Exception("File troppo grande");

        byte[] hash = sha256(bytes);
        int payloadLen = (int) payloadLong;
        byte[] out = new byte[HEADER + payloadLen];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[4] = (byte) TYPE_ORIGINAL_FILE;
        writeU32(out, 5, payloadLen);
        int o = HEADER;
        writeU16(out, o, nb.length); o += 2;
        writeU16(out, o, mb.length); o += 2;
        writeU32(out, o, bytes.length); o += 4;
        System.arraycopy(hash, 0, out, o, hash.length); o += hash.length;
        System.arraycopy(nb, 0, out, o, nb.length); o += nb.length;
        System.arraycopy(mb, 0, out, o, mb.length); o += mb.length;
        System.arraycopy(bytes, 0, out, o, bytes.length);
        return out;
    }

    public static OriginalFile unpackOriginalFile(byte[] packet) throws Fra1Exception {
        if (packet == null || packet.length < HEADER + META) throw new Fra1Exception("Pacchetto FRA1 troppo corto");
        validateHeader(packet, TYPE_ORIGINAL_FILE);
        int payloadLen = readU32(packet, 5);
        if (payloadLen != packet.length - HEADER) throw new Fra1Exception("Lunghezza FRA1 non valida");
        int o = HEADER;
        int nameLen = readU16(packet, o); o += 2;
        int mimeLen = readU16(packet, o); o += 2;
        long fileLenLong = readU32Unsigned(packet, o); o += 4;
        if (fileLenLong > Integer.MAX_VALUE) throw new Fra1Exception("File troppo grande per questa app");
        int fileLen = (int) fileLenLong;
        byte[] storedHash = Arrays.copyOfRange(packet, o, o + 32); o += 32;
        long expectedEnd = (long) o + nameLen + mimeLen + fileLen;
        if (expectedEnd != packet.length) throw new Fra1Exception("Metadati FRA1 incoerenti");

        String name = decodeUtf8(packet, o, nameLen, "Nome file UTF-8 non valido"); o += nameLen;
        String mime = decodeUtf8(packet, o, mimeLen, "MIME UTF-8 non valido"); o += mimeLen;
        byte[] bytes = Arrays.copyOfRange(packet, o, o + fileLen);
        byte[] actual = sha256(bytes);
        boolean verified = MessageDigest.isEqual(storedHash, actual);
        if (!verified) throw new Fra1Exception("Verifica SHA-256 fallita: file FRA1 corrotto");
        return new OriginalFile(name, mime, bytes, toHex(storedHash), true);
    }

    public static String sha256Hex(byte[] bytes) throws Fra1Exception { return toHex(sha256(bytes)); }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static byte[] sha256(byte[] bytes) throws Fra1Exception {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new Fra1Exception("SHA-256 non disponibile", e); }
    }

    private static byte[] makePacket(int type, byte[] payload) {
        byte[] out = new byte[HEADER + payload.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[4] = (byte) type;
        writeU32(out, 5, payload.length);
        System.arraycopy(payload, 0, out, HEADER, payload.length);
        return out;
    }

    private static String packetToFraction(byte[] packet) {
        BigInteger n = new BigInteger(1, packet);
        return n.toString() + " / 2^" + (packet.length * 8L);
    }

    private static byte[] parseFraction(String value) throws Fra1Exception {
        Matcher m = FRACTION.matcher(String.valueOf(value));
        if (!m.matches()) throw new Fra1Exception("Formato frazione non valido: usa N / 2^K");
        long k;
        try { k = Long.parseLong(m.group(2)); } catch (NumberFormatException e) { throw new Fra1Exception("Esponente K non valido"); }
        if (k <= 0 || (k % 8) != 0 || k / 8 > 2_000_000) throw new Fra1Exception("Esponente K non valido");
        int length = (int) (k / 8);
        BigInteger n;
        try { n = new BigInteger(m.group(1)); } catch (NumberFormatException e) { throw new Fra1Exception("Numeratore non valido"); }
        if (n.signum() < 0 || n.bitLength() > k) throw new Fra1Exception("Numeratore incompatibile");
        byte[] raw = n.toByteArray();
        if (raw.length > 1 && raw[0] == 0) raw = Arrays.copyOfRange(raw, 1, raw.length);
        if (raw.length > length) throw new Fra1Exception("Numeratore incompatibile");
        byte[] out = new byte[length];
        System.arraycopy(raw, 0, out, length - raw.length, raw.length);
        return out;
    }

    private static void validateHeader(byte[] packet, int expectedType) throws Fra1Exception {
        if (packet.length < HEADER) throw new Fra1Exception("Pacchetto FRA1 troppo corto");
        for (int i = 0; i < MAGIC.length; i++) if (packet[i] != MAGIC[i]) throw new Fra1Exception("Firma FRA1 non valida");
        if ((packet[4] & 0xff) != expectedType) throw new Fra1Exception("Tipo FRA1 non supportato");
    }

    private static String decodeUtf8(byte[] data, int offset, int length, String error) throws Fra1Exception {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, offset, length)).toString();
        } catch (CharacterCodingException e) { throw new Fra1Exception(error, e); }
    }

    private static void writeU16(byte[] out, int o, int v) { out[o] = (byte) ((v >>> 8) & 0xff); out[o+1] = (byte) (v & 0xff); }
    private static int readU16(byte[] b, int o) { return ((b[o] & 0xff) << 8) | (b[o+1] & 0xff); }
    private static void writeU32(byte[] out, int o, long v) {
        out[o] = (byte) ((v >>> 24) & 0xff); out[o+1] = (byte) ((v >>> 16) & 0xff); out[o+2] = (byte) ((v >>> 8) & 0xff); out[o+3] = (byte) (v & 0xff);
    }
    private static int readU32(byte[] b, int o) throws Fra1Exception {
        long v = readU32Unsigned(b, o);
        if (v > Integer.MAX_VALUE) throw new Fra1Exception("Valore FRA1 troppo grande");
        return (int) v;
    }
    private static long readU32Unsigned(byte[] b, int o) {
        return ((long)(b[o]&0xff)<<24) | ((long)(b[o+1]&0xff)<<16) | ((long)(b[o+2]&0xff)<<8) | (long)(b[o+3]&0xff);
    }
}
