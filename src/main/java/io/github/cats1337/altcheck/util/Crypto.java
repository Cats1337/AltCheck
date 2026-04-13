package io.github.cats1337.altcheck.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

public class Crypto {

    private static final String ALGO = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private static final byte[] KEY = loadKey();

    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] loadKey() {
        try {
            Path path = Path.of("config/altcheck.key");

            if (Files.exists(path)) {
                byte[] existing = Files.readAllBytes(path);

                if (existing.length != 32) {
                    throw new IllegalStateException("Invalid AltCheck encryption key file (must be 32 bytes)");
                }
                return existing;
            }

            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);

            Files.createDirectories(path.getParent());
            Files.write(path, key);

            return key;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load encryption key", e);
        }
    }

    public static String encrypt(String input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] encrypted = cipher.doFinal(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // store IV + ciphertext together
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    public static String decrypt(String input) {
        try {
            byte[] data = Base64.getDecoder().decode(input);

            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[data.length - IV_LENGTH];

            System.arraycopy(data, 0, iv, 0, IV_LENGTH);
            System.arraycopy(data, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            return new String(cipher.doFinal(cipherText), java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed", e);
        }
    }
}