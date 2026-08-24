package com.company.s3explorer.security;

import com.company.s3explorer.service.CryptoService;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

public class AesCryptoService implements CryptoService {

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final int GCM_TAG_LENGTH = 128;

    private static final int IV_LENGTH = 12;

    private static final int AES_KEY_LENGTH = 256;

    private final SecretKey secretKey;

    public AesCryptoService() {

        byte[] key = loadOrCreateKey();

        secretKey = new SecretKeySpec(
                key,
                "AES");
    }

    @Override
    public String encrypt(String value) {

        if (value == null) {
            return null;
        }

        try {

            byte[] iv =
                    new byte[IV_LENGTH];

            SecureRandom secureRandom =
                    new SecureRandom();

            secureRandom.nextBytes(iv);

            Cipher cipher =
                    Cipher.getInstance(
                            TRANSFORMATION);

            GCMParameterSpec gcmSpec =
                    new GCMParameterSpec(
                            GCM_TAG_LENGTH,
                            iv);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    gcmSpec);

            byte[] encrypted =
                    cipher.doFinal(
                            value.getBytes(
                                    StandardCharsets.UTF_8));

            /*
             * IV + encrypted data
             *
             * Böylece decrypt sırasında
             * IV ayrıca saklanmak zorunda kalmaz.
             */
            byte[] result =
                    new byte[
                            iv.length
                                    + encrypted.length];

            System.arraycopy(
                    iv,
                    0,
                    result,
                    0,
                    iv.length);

            System.arraycopy(
                    encrypted,
                    0,
                    result,
                    iv.length,
                    encrypted.length);

            return Base64.getEncoder()
                    .encodeToString(result);

        } catch (Exception ex) {

            throw new RuntimeException(
                    "AES encryption failed",
                    ex);
        }
    }

    @Override
    public String decrypt(String value) {

        if (value == null) {
            return null;
        }

        try {

            byte[] combined =
                    Base64.getDecoder()
                            .decode(value);

            if (combined.length <= IV_LENGTH) {

                throw new IllegalArgumentException(
                        "Invalid encrypted value");
            }

            byte[] iv =
                    new byte[IV_LENGTH];

            byte[] encrypted =
                    new byte[
                            combined.length
                                    - IV_LENGTH];

            System.arraycopy(
                    combined,
                    0,
                    iv,
                    0,
                    IV_LENGTH);

            System.arraycopy(
                    combined,
                    IV_LENGTH,
                    encrypted,
                    0,
                    encrypted.length);

            Cipher cipher =
                    Cipher.getInstance(
                            TRANSFORMATION);

            GCMParameterSpec gcmSpec =
                    new GCMParameterSpec(
                            GCM_TAG_LENGTH,
                            iv);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    gcmSpec);

            byte[] decrypted =
                    cipher.doFinal(encrypted);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8);

        } catch (Exception ex) {

            throw new RuntimeException(
                    "AES decryption failed",
                    ex);
        }
    }

    private byte[] loadOrCreateKey() {

        try {

            Path keyPath =
                    Paths.get(
                            System.getProperty(
                                    "user.home"),
                            ".s3explorer",
                            "master.key");

            if (Files.exists(keyPath)) {

                byte[] existingKey =
                        Files.readAllBytes(keyPath);

                if (existingKey.length != 32) {

                    throw new IllegalStateException(
                            "Invalid AES key length: "
                                    + existingKey.length);
                }

                return existingKey;
            }

            KeyGenerator keyGenerator =
                    KeyGenerator.getInstance("AES");

            keyGenerator.init(
                    AES_KEY_LENGTH);

            SecretKey key =
                    keyGenerator.generateKey();

            byte[] encoded =
                    key.getEncoded();

            Files.createDirectories(
                    keyPath.getParent());

            Files.write(
                    keyPath,
                    encoded);

            return encoded;

        } catch (Exception ex) {

            throw new RuntimeException(
                    "AES key load/create failed",
                    ex);
        }
    }
}