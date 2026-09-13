package io.neo4flix.user;

import static org.junit.jupiter.api.Assertions.*;

import io.neo4flix.common.ApiException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecurityPrimitivesTest {

    @Test
    void rejectsWeakAndBcryptTruncatedPasswords() {
        for (String password : new String[] {
            "short",
            "lowercaseonly123!",
            "UPPERCASEONLY123!",
            "NoSymbolsHere123",
            "NoNumbersHere!",
            "A1!" + "a".repeat(70),
            "A1!" + "é".repeat(35),
        })
            assertThrows(ApiException.class, () -> AuthService.strongPassword(password));
        assertDoesNotThrow(() -> AuthService.strongPassword("ValidLongPassword!123"));
    }

    @Test
    void encryptionIsRandomizedAndAuthenticated() {
        var cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        String one = cipher.encrypt("authenticator-secret"),
            two = cipher.encrypt("authenticator-secret");
        assertNotEquals(one, two);
        assertEquals("authenticator-secret", cipher.decrypt(one));
        byte[] modified = Base64.getDecoder().decode(one);
        modified[modified.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () ->
            cipher.decrypt(Base64.getEncoder().encodeToString(modified))
        );
        assertThrows(IllegalArgumentException.class, () -> new SecretCipher("bad-key"));
    }

    @Test
    void refreshTokenHashMatchesSha256KnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AuthService.hash("abc")
        );
    }
}
