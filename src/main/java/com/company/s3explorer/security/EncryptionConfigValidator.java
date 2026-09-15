package com.company.s3explorer.security;

import javax.crypto.Cipher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EncryptionConfigValidator {
    public static String BYTES_REGEX = "[-+]?\\d+";
    public enum ENCRYPTION_FIELDS {
        ENCRYPTION_IV("Encryption IV"),
        ENCRYPTION_KEY("Encryption key");

        private final String text;
        
        ENCRYPTION_FIELDS(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }
    }
    
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
                    ENCRYPTION_FIELDS.ENCRYPTION_IV + " is required.");
        }

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    ENCRYPTION_FIELDS.ENCRYPTION_KEY + " is required.");
        }

        validateTransformation(transformation);

        byte[] ivBytes = parseBytes(
                iv,
                ENCRYPTION_FIELDS.ENCRYPTION_IV);

        byte[] keyBytes = parseBytes(
                key,
                ENCRYPTION_FIELDS.ENCRYPTION_KEY);

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

    public static byte[] parseBytes(
            String value,
            ENCRYPTION_FIELDS fieldName) {

        Pattern pattern = Pattern.compile(BYTES_REGEX);
        Matcher matcher = pattern.matcher(value);

        List<Byte> bytes =
                new ArrayList<>();

        while (matcher.find()) {

            String part = matcher.group();

            try {

                int number =
                        Integer.parseInt(part);

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
                                + part,
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
                    ENCRYPTION_FIELDS.ENCRYPTION_IV + " must contain exactly 16 bytes for CBC.");
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
                        "AES " + ENCRYPTION_FIELDS.ENCRYPTION_KEY + " must contain 16, 24 or 32 bytes.");
            }
        }
    }
}