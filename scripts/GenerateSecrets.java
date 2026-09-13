import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

class GenerateSecrets {

    static SecureRandom random = new SecureRandom();

    static String token(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static void pem(Path file, String type, byte[] bytes) throws Exception {
        Files.writeString(
            file,
            "-----BEGIN " +
                type +
                "-----\n" +
                Base64.getMimeEncoder(64, new byte[] { 10 }).encodeToString(bytes) +
                "\n-----END " +
                type +
                "-----\n"
        );
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0])
            .toAbsolutePath()
            .normalize();
        if (Files.exists(root.resolve(".env")) || Files.exists(root.resolve("secrets"))) {
            System.out.println("Existing configuration preserved. No secrets were changed.");
            return;
        }
        Files.createDirectories(root.resolve("secrets"));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        pem(root.resolve("secrets/jwt-private.pem"), "PRIVATE KEY", pair.getPrivate().getEncoded());
        pem(root.resolve("secrets/jwt-public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
        byte[] aes = new byte[32];
        random.nextBytes(aes);
        Files.writeString(
            root.resolve(".env"),
            "APP_ORIGIN=https://localhost:8443\nNEO4J_PASSWORD=" +
                token(32) +
                "\nENCRYPTION_KEY=" +
                Base64.getEncoder().encodeToString(aes) +
                "\nADMIN_EMAIL=admin@neo4flix.local\nADMIN_PASSWORD=N4!" +
                token(24) +
                "\n",
            StandardCharsets.UTF_8
        );
        System.out.println(
            "Generated .env and signing keys. Administrator credentials are in .env; keep them private."
        );
    }
}
