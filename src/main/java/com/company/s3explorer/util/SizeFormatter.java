package com.company.s3explorer.util;

public final class SizeFormatter {

    private SizeFormatter() {
    }

    public static String format(Long bytes) {
        if (bytes == null) {
            return "";
        }

        if (bytes < 1024)
            return bytes + " B";

        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);

        if (bytes < 1024L * 1024L * 1024L)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));

        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}