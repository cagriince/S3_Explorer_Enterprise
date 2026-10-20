package com.company.s3explorer.service;

import java.util.Locale;

public record FileTableFilter(
        String text,
        ItemTypeFilter itemType) {

    private static final Locale TURKISH_LOCALE =
            Locale.forLanguageTag("tr-TR");

    public FileTableFilter {
        text = text == null ? "" : text.trim();

        itemType = itemType == null
                ? ItemTypeFilter.ALL
                : itemType;
    }

    public static FileTableFilter noFilter() {
        return new FileTableFilter(
                "",
                ItemTypeFilter.ALL);
    }

    public boolean hasTextFilter() {
        return !text.isEmpty();
    }

    public boolean includesFolders() {
        return itemType != ItemTypeFilter.FILES_ONLY;
    }

    public boolean includesFiles() {
        return itemType != ItemTypeFilter.FOLDERS_ONLY;
    }

    public boolean matchesText(String displayName) {
        if (!hasTextFilter()) {
            return true;
        }

        if (displayName == null) {
            return false;
        }

        return displayName
                .toLowerCase(TURKISH_LOCALE)
                .contains(text.toLowerCase(TURKISH_LOCALE));
    }
}