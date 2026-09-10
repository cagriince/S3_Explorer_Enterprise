package com.company.s3explorer.security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class EncryptionConfig {

    private final String transformation;
    private final byte[] iv;
    private final byte[] key;

    public EncryptionConfig(
            String transformation,
            String iv,
            String key) {

        EncryptionConfigValidator.validate(
                transformation,
                iv,
                key);

        this.transformation = transformation;
        this.iv = parseBytes(iv);
        this.key = parseBytes(key);
    }

    public Cipher createCipher(int mode) throws Exception {

        Cipher cipher =
                Cipher.getInstance(transformation);

        String algorithm =
                transformation.substring(
                        0,
                        transformation.indexOf('/'));

        cipher.init(
                mode,
                new SecretKeySpec(
                        key,
                        algorithm),
                new IvParameterSpec(iv));

        return cipher;
    }

    private byte[] parseBytes(String value) {

        String[] parts =
                value.split(",");

        byte[] result =
                new byte[parts.length];

        for (int i = 0; i < parts.length; i++) {
            result[i] =
                    (byte) Integer.parseInt(
                            parts[i].trim());
        }

        return result;
    }
}