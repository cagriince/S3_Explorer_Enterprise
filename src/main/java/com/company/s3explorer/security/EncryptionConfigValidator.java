package com.company.s3explorer.security;

import javax.crypto.Cipher;
import java.util.ArrayList;
import java.util.List;

public final class EncryptionConfigValidator {

    private EncryptionConfigValidator() {
    }

    public static void validate(
            String transformation,
            String iv,
            String key) {

        if (transformation == null || transformation.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption transformation is required.");
        }

        if (iv == null || iv.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption IV is required.");
        }

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption key is required.");
        }

        validateTransformation(transformation);

        byte[] ivBytes = parseBytes(
                iv,
                "Encryption IV");

        byte[] keyBytes = parseBytes(
                key,
                "Encryption key");

        validateIv(
                transformation,
                ivBytes);

        validateKey(
                transformation,
                keyBytes);
    }

    private static void validateTransformation(
            String transformation) {

        try {
            Cipher.getInstance(
                    transformation);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid encryption transformation: "
                            + transformation,
                    ex);
        }
    }

    private static byte[] parseBytes(
            String value,
            String fieldName) {

        String[] parts =
                value.split(",");

        List<Byte> bytes =
                new ArrayList<>();

        for (String part : parts) {

            String trimmed =
                    part.trim();

            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(
                        fieldName + " contains an empty value.");
            }

            try {

                int number =
                        Integer.parseInt(trimmed);

                if (number < -128 || number > 127) {
                    throw new IllegalArgumentException(
                            fieldName
                                    + " values must be between -128 and 127.");
                }

                bytes.add(
                        (byte) number);

            } catch (NumberFormatException ex) {

                throw new IllegalArgumentException(
                        fieldName
                                + " contains an invalid byte value: "
                                + trimmed,
                        ex);
            }
        }

        byte[] result =
                new byte[bytes.size()];

        for (int i = 0; i < bytes.size(); i++) {
            result[i] =
                    bytes.get(i);
        }

        return result;
    }

    private static void validateIv(
            String transformation,
            byte[] iv) {

        if (transformation.contains("/CBC/")
                && iv.length != 16) {

            throw new IllegalArgumentException(
                    "Encryption IV must contain exactly 16 bytes for CBC.");
        }
    }

    private static void validateKey(
            String transformation,
            byte[] key) {

        if (transformation.startsWith("AES/")) {

            int length =
                    key.length;

            if (length != 16
                    && length != 24
                    && length != 32) {

                throw new IllegalArgumentException(
                        "AES encryption key must contain 16, 24 or 32 bytes.");
            }
        }
    }
}