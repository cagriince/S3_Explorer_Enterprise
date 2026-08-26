package com.company.s3explorer.ui.icons;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class FileIconRegistry {

    private static final String DEFAULT_ICON =
            "file";

    private static final String RESOURCE =
            "file-icons/icons.properties";

    private static final Map<String, String>
            FILE_NAME_INDEX;

    private static final Map<String, String>
            EXTENSION_INDEX;

    static {
        RegistryData data =
                loadDefinitions();

        FILE_NAME_INDEX =
                data.fileNames();

        EXTENSION_INDEX =
                data.extensions();
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

        List<String> iconNames =
                new ArrayList<>();

        for (String key :
                properties.stringPropertyNames()) {

            if (key.startsWith("icon.")
                    && key.endsWith(
                    ".extensions")) {

                String iconName =
                        key.substring(
                                "icon.".length(),
                                key.length()
                                        - ".extensions"
                                        .length());

                iconNames.add(iconName);
            }
        }

        for (String iconName : iconNames) {

            String extensionValue =
                    properties.getProperty(
                            "icon."
                                    + iconName
                                    + ".extensions",
                            "");

            String fileValue =
                    properties.getProperty(
                            "icon."
                                    + iconName
                                    + ".files",
                            "");

            for (String extension :
                    split(extensionValue)) {

                extensions.put(
                        extension.toLowerCase(
                                Locale.ROOT),
                        iconName);
            }

            for (String file :
                    split(fileValue)) {

                fileNames.put(
                        file.toLowerCase(
                                Locale.ROOT),
                        iconName);
            }
        }

        return new RegistryData(
                Collections.unmodifiableMap(
                        fileNames),
                Collections.unmodifiableMap(
                        extensions));
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
            Map<String, String> extensions) {
    }
}