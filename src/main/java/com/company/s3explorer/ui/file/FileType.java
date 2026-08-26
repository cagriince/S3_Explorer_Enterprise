package com.company.s3explorer.ui.file;

public enum FileType {

    FOLDER("Folder"),
    PDF("PDF Document"),
    WORD("Word Document"),
    EXCEL("Excel Spreadsheet"),
    POWERPOINT("PowerPoint Presentation"),
    IMAGE("Image"),
    AUDIO("Audio"),
    VIDEO("Video"),
    ARCHIVE("Archive"),
    TEXT("Text Document"),
    CSV("CSV File"),
    OTHER("File");

    private final String displayName;

    FileType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}