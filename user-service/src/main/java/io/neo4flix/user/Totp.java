package io.neo4flix.user;

import java.nio.ByteBuffer;
import java.security.*;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238, SHA-1, six digits, 30-second steps. One adjacent step tolerates clock skew. */
public final class Totp {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    public static String secret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return encode(bytes);
    }

    public static String encode(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0,
            bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(ALPHABET.charAt((buffer >> bits) & 31));
            }
        }
        if (bits > 0) out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    static byte[] decode(String input) {
        var out = new java.io.ByteArrayOutputStream();
        int buffer = 0,
            bits = 0;
        for (char c : input.toCharArray()) {
            int val = ALPHABET.indexOf(c);
            if (val < 0) throw new IllegalArgumentException("Invalid secret");
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 255);
            }
        }
        return out.toByteArray();
    }

    public static String code(String secret, long step) {
        return code(secret, step, 6);
    }

    static String code(String secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int value = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            int modulus = digits == 8 ? 100000000 : 1000000;
            return String.format(java.util.Locale.ROOT, "%0" + digits + "d", value % modulus);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static long verify(String secret, String input, long lastStep, long now) {
        if (input == null || !input.matches("[0-9]{6}")) return -1;
        long current = now / 30;
        for (long step = current - 1; step <= current + 1; step++) if (
            step > lastStep &&
            MessageDigest.isEqual(
                code(secret, step).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                input.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
            )
        ) return step;
        return -1;
    }
}
