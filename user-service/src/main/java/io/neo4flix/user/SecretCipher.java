package io.neo4flix.user;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

    private final SecretKeySpec key;

    public SecretCipher(@Value("${app.encryption-key}") String encoded) {
        byte[] raw = Base64.getDecoder().decode(encoded);
        if (raw.length != 32) throw new IllegalArgumentException(
            "ENCRYPTION_KEY must be 32 bytes in base64"
        );
        key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String text) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] payload = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + payload.length];
            System.arraycopy(nonce, 0, out, 0, 12);
            System.arraycopy(payload, 0, out, 12, payload.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String text) {
        try {
            byte[] data = Base64.getDecoder().decode(text);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(128, Arrays.copyOfRange(data, 0, 12))
            );
            return new String(
                cipher.doFinal(Arrays.copyOfRange(data, 12, data.length)),
                StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
