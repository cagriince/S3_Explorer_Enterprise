package com.company.s3explorer.ui.file;

import com.company.s3explorer.ui.icons.FileIconRegistry;

public final class FileTypeResolver {

    private FileTypeResolver() {
    }

    public static FileType resolve(
            String key,
            boolean folder) {

        if (folder) {
            return FileType.FOLDER;
        }

        if (key == null
                || key.isBlank()) {

            return FileType.OTHER;
        }

        return FileType.OTHER;
    }

    public static String resolveDisplayName(
            String key,
            boolean folder) {

        if (folder) {
            return "Folder";
        }

        if (key == null
                || key.isBlank()) {

            return "File";
        }

        return FileIconRegistry
                .findFileType(key)
                .displayName();
    }
}