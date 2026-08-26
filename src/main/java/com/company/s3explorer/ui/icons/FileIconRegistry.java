package com.company.s3explorer.ui.icons;

import java.util.Locale;

public final class FileIconRegistry {

    private static final String
            DEFAULT_ICON = "file";

    private static final List<
            FileIconDefinition> ICONS =
            loadDefinitions();

    private FileIconRegistry() {
    }

    public static String findIconName(
            String filename) {

        if (filename == null
                || filename.isBlank()) {

            return DEFAULT_ICON;
        }

        String normalized =
                filename.toLowerCase(
                        Locale.ROOT);

        // 1. Exact filename match
        for (FileIconDefinition icon : ICONS) {

            for (String file : icon.files()) {

                if (file.equals(normalized)) {
                    return icon.name();
                }
            }
        }

        // 2. Longest extension match
        String matchedIcon = null;
        int matchLength = 0;

        for (FileIconDefinition icon : ICONS) {

            for (String extension :
                    icon.extensions()) {

                if (extension.length()
                        > matchLength
                        && normalized.endsWith(
                        "." + extension)) {

                    matchedIcon = icon.name();
                    matchLength = extension.length();
                }
            }
        }

        return matchedIcon != null
                ? matchedIcon
                : DEFAULT_ICON;
    }
}