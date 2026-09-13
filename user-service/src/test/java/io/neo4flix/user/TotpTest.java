package io.neo4flix.user;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TotpTest {

    private final String secret = Totp.encode(
        "12345678901234567890".getBytes(StandardCharsets.US_ASCII)
    );

    @Test
    void matchesRfc6238Sha1Vectors() {
        long[] times = { 59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L };
        String[] expected = {
            "94287082",
            "07081804",
            "14050471",
            "89005924",
            "69279037",
            "65353130",
        };
        for (int i = 0; i < times.length; i++) assertEquals(
            expected[i],
            Totp.code(secret, times[i] / 30, 8)
        );
    }

    @Test
    void toleratesOneStepAndRejectsReplay() {
        long now = 1234567890L,
            step = now / 30;
        String code = Totp.code(secret, step);
        assertEquals(step, Totp.verify(secret, code, -1, now));
        assertEquals(step, Totp.verify(secret, code, -1, now + 30));
        assertEquals(-1, Totp.verify(secret, code, step, now));
        assertEquals(-1, Totp.verify(secret, code, -1, now + 60));
        assertEquals(-1, Totp.verify(secret, "bad", -1, now));
        assertEquals(-1, Totp.verify(secret, null, -1, now));
    }

    @Test
    void base32UsesStandardAlphabet() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", secret);
        assertEquals(32, Totp.secret().length());
    }
}
