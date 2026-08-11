package com.company.s3explorer.security;

import com.company.s3explorer.service.CryptoService;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class AesCryptoService implements CryptoService {
    private final SecretKey secretKey;

    public AesCryptoService() {
        byte[] key = loadOrCreateKey();

        secretKey = new SecretKeySpec(key, "AES");
    }

    @Override
    public String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes()));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String decrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(value)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private byte[] loadOrCreateKey() {
        try {
            Path keyPath = Paths.get(System.getProperty("user.home"), ".s3explorer", "master.key");

            // 1. Key varsa oku
            if (Files.exists(keyPath)) {
                return Files.readAllBytes(keyPath);
            }

            // 2. Yoksa oluştur
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256); // AES-256
            SecretKey key = keyGen.generateKey();
            byte[] encoded = key.getEncoded();

            // 3. Klasörü oluştur
            Files.createDirectories(keyPath.getParent());

            // 4. Diske yaz
            Files.write(keyPath, encoded);

            return encoded;
        } catch (Exception e) {
            throw new RuntimeException("AES key load/create failed", e);
        }
    }
}