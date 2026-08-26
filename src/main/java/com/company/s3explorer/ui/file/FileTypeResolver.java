package com.company.s3explorer.ui.file;

import java.util.Locale;

public final class FileTypeResolver {

    private FileTypeResolver() {
    }

    public static FileType resolve(
            String key,
            boolean folder) {

        if (folder) {
            return FileType.FOLDER;
        }

        if (key == null || key.isBlank()) {
            return FileType.OTHER;
        }

        int slashIndex =
                key.lastIndexOf('/');

        int dotIndex =
                key.lastIndexOf('.');

        if (dotIndex <= slashIndex
                || dotIndex == key.length() - 1) {

            return FileType.OTHER;
        }

        String extension =
                key.substring(dotIndex + 1)
                        .toLowerCase(
                                Locale.ROOT);

        return switch (extension) {

            case "pdf" ->
                    FileType.PDF;

            case "doc", "docx",
                 "odt", "rtf" ->
                    FileType.WORD;

            case "xls", "xlsx",
                 "ods" ->
                    FileType.EXCEL;

            case "ppt", "pptx",
                 "odp" ->
                    FileType.POWERPOINT;

            case "jpg", "jpeg",
                 "png", "gif",
                 "bmp", "webp",
                 "svg", "tif", "tiff" ->
                    FileType.IMAGE;

            case "mp3", "wav",
                 "flac", "aac",
                 "ogg", "m4a" ->
                    FileType.AUDIO;

            case "mp4", "avi",
                 "mkv", "mov",
                 "wmv", "webm" ->
                    FileType.VIDEO;

            case "zip", "rar",
                 "7z", "tar",
                 "gz", "bz2" ->
                    FileType.ARCHIVE;

            case "txt", "log",
                 "md", "xml",
                 "json", "yaml",
                 "yml" ->
                    FileType.TEXT;

            case "csv" ->
                    FileType.CSV;

            default ->
                    FileType.OTHER;
        };
    }
}