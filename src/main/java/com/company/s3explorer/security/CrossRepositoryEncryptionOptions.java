
package com.company.s3explorer.security;

public final class CrossRepositoryEncryptionOptions {

    private static final CrossRepositoryEncryptionOptions NONE =
            new CrossRepositoryEncryptionOptions(
                    false,
                    false,
                    null,
                    null);

    private final boolean decryptSource;
    private final boolean encryptTarget;
    private final EncryptionConfig sourceEncryptionConfig;
    private final EncryptionConfig targetEncryptionConfig;

    public CrossRepositoryEncryptionOptions(
            boolean decryptSource,
            boolean encryptTarget,
            EncryptionConfig sourceEncryptionConfig,
            EncryptionConfig targetEncryptionConfig) {

        if (decryptSource && sourceEncryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Source encryption configuration is required for decryption.");
        }

        if (encryptTarget && targetEncryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Target encryption configuration is required for encryption.");
        }

        this.decryptSource = decryptSource;
        this.encryptTarget = encryptTarget;
        this.sourceEncryptionConfig = sourceEncryptionConfig;
        this.targetEncryptionConfig = targetEncryptionConfig;
    }

    public static CrossRepositoryEncryptionOptions none() {
        return NONE;
    }

    public boolean isDecryptSource() {
        return decryptSource;
    }

    public boolean isEncryptTarget() {
        return encryptTarget;
    }

    public EncryptionConfig getSourceEncryptionConfig() {
        return sourceEncryptionConfig;
    }

    public EncryptionConfig getTargetEncryptionConfig() {
        return targetEncryptionConfig;
    }
}
