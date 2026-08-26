package com.company.s3explorer.ui.file;

import com.company.s3explorer.ui.explorer.S3FileItem;
import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.*;

public final class FileTypeIconProvider {

    private FileTypeIconProvider() {
    }

    public static Icon getIcon(
            S3FileItem item) {

        if (item == null) {
            return null;
        }

        FileType type =
                FileTypeResolver.resolve(
                        item.getKey(),
                        item.isFolder());

        return switch (type) {

            case FOLDER ->
                    IconProvider.ICON_SYSTEM_CLOSED_FOLDER;
/*
            case PDF ->
                    IconProvider.ICON_FILE_PDF;

            case WORD ->
                    IconProvider.ICON_FILE_WORD;

            case EXCEL ->
                    IconProvider.ICON_FILE_EXCEL;

            case POWERPOINT ->
                    IconProvider.ICON_FILE_POWERPOINT;

            case IMAGE ->
                    IconProvider.ICON_FILE_IMAGE;

            case AUDIO ->
                    IconProvider.ICON_FILE_AUDIO;

            case VIDEO ->
                    IconProvider.ICON_FILE_VIDEO;

            case ARCHIVE ->
                    IconProvider.ICON_FILE_ARCHIVE;

            case TEXT, CSV ->
                    IconProvider.ICON_FILE_TEXT;
*/
            default ->
                    IconProvider.ICON_SYSTEM_CLOSED_FOLDER;
        };
    }
}