package com.company.s3explorer.ui.icons;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public final class FileIconRegistry {

    private static final String DEFAULT_ICON =
            "file";

    private static final String RESOURCE =
            "file-icons/icons.properties";

    private static final Map<String, String>
            FILE_NAME_INDEX;

    private static final Map<String, String>
            EXTENSION_INDEX;

    private static final Map<String, String>
            DISPLAY_NAMES;

    static {
        RegistryData data =
                loadDefinitions();

        FILE_NAME_INDEX =
                data.fileNames();

        EXTENSION_INDEX =
                data.extensions();

        DISPLAY_NAMES =
                data.displayNames();
    }

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

        String exact =
                FILE_NAME_INDEX.get(normalized);

        if (exact != null) {
            return exact;
        }

        String matchedIcon = null;
        int matchLength = 0;

        for (Map.Entry<String, String> entry :
                EXTENSION_INDEX.entrySet()) {

            String extension =
                    entry.getKey();

            if (extension.length()
                    > matchLength
                    && normalized.endsWith(
                    "." + extension)) {

                matchedIcon =
                        entry.getValue();

                matchLength =
                        extension.length();
            }
        }

        return matchedIcon != null
                ? matchedIcon
                : DEFAULT_ICON;
    }

    public static FileTypeDefinition findFileType(
            String filename) {

        String iconName =
                findIconName(filename);

        String displayName =
                DISPLAY_NAMES.get(iconName);

        if (displayName == null
                || displayName.isBlank()) {

            displayName =
                    createDisplayName(iconName);
        }

        return new FileTypeDefinition(
                iconName,
                displayName);
    }

    private static String createDisplayName(
            String iconName) {

        if (iconName == null
                || iconName.isBlank()) {

            return "File";
        }

        String normalized =
                iconName.replace(
                        '-',
                        ' ');

        StringBuilder result =
                new StringBuilder();

        for (String word :
                normalized.split("\\s+")) {

            if (word.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(
                    Character.toUpperCase(
                            word.charAt(0)));

            if (word.length() > 1) {
                result.append(
                        word.substring(1));
            }
        }

        return result.toString();
    }
    
    private static RegistryData loadDefinitions() {

        Properties properties =
                new Properties();

        try (InputStream input =
                     FileIconRegistry.class
                             .getClassLoader()
                             .getResourceAsStream(
                                     RESOURCE)) {

            if (input == null) {
                throw new IllegalStateException(
                        "File icon registry not found: "
                                + RESOURCE);
            }

            properties.load(input);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not load file icon registry: "
                            + RESOURCE,
                    e);
        }

        Map<String, String> fileNames =
                new HashMap<>();

        Map<String, String> extensions =
                new HashMap<>();

        Map<String, String> displayNames =
                new HashMap<>();

        for (String key :
                properties.stringPropertyNames()) {

            if (!key.startsWith("icon.")) {
                continue;
            }

            String remainder =
                    key.substring("icon.".length());

            int separator =
                    remainder.lastIndexOf('.');

            if (separator <= 0) {
                continue;
            }

            String iconName =
                    remainder.substring(
                            0,
                            separator);

            String propertyType =
                    remainder.substring(
                            separator + 1);

            String value =
                    properties.getProperty(key);

            switch (propertyType) {

                case "displayName" -> {

                    displayNames.put(
                            iconName,
                            value);
                }

                case "extensions" -> {

                    for (String extension :
                            split(value)) {

                        extensions.put(
                                extension.toLowerCase(
                                        Locale.ROOT),
                                iconName);
                    }
                }

                case "files" -> {

                    for (String file :
                            split(value)) {

                        fileNames.put(
                                file.toLowerCase(
                                        Locale.ROOT),
                                iconName);
                    }
                }

                default -> {
                    // Unknown property; ignore.
                }
            }
        }

        return new RegistryData(
                Collections.unmodifiableMap(
                        fileNames),
                Collections.unmodifiableMap(
                        extensions),
                Collections.unmodifiableMap(
                        displayNames));
    }

    private static List<String> split(
            String value) {

        if (value == null
                || value.isBlank()) {

            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        for (String item :
                value.split(",")) {

            String trimmed =
                    item.trim();

            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }

        return result;
    }

    private record RegistryData(
            Map<String, String> fileNames,
            Map<String, String> extensions,
            Map<String, String> displayNames) {
    }
}