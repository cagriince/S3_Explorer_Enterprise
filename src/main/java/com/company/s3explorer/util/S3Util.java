package com.company.s3explorer.util;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.ui.main.MainFrame;
import com.company.s3explorer.ui.theme.UIThemeManager;

import java.awt.*;
import java.time.Instant;
import java.util.Date;

public class S3Util {
    public static String combineKey(String prefix, String name) {
        if (prefix == null || prefix.isBlank()) {
            return name;
        }

        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }

    public static String extractParentPrefix(String fullPrefix) {
        String value = fullPrefix;
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        int idx = value.lastIndexOf('/');
        if (idx < 0) {
            return "";
        }

        return value.substring(0, idx + 1);
    }

    public static MainFrame getMainFrameAncestor(Component c) {
        for(Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof MainFrame) {
                return (MainFrame)p;
            }
        }
        return null;
    }

    public static boolean isFolder(String key) {
        return key.endsWith("/");
    }

    public static String extractFileName(String key) {
        int idx = key.lastIndexOf('/');
        if (idx >= 0) {
            return key.substring(idx + 1);
        }

        return key;
    }

    public static String extractFolderName(String prefix) {
        String value = prefix;
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        int idx = value.lastIndexOf('/');
        if (idx >= 0) {
            return value.substring(idx + 1);
        }

        return value;
    }

    public static Date instantToDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Date.from(instant);
    }

    public static String escapeHtml(
            String value) {

        if (value == null
                || value.isEmpty()) {

            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String getTransferPanelDisplayBucketName(String repository, String bucket) {
        return "<b><font color='"
                + UIThemeManager.TRANSFER_PANEL_COLOR_BUCKET
                + "'>"
                + S3Util.escapeHtml(repository)
                + " | "
                + S3Util.escapeHtml(bucket)
                + "</font> / </b>";
    }

    public static String getTransferPanelDisplayLastFileFolder(String path) {
        if (path == null) {
            return "";
        }

        path = path.replace("\\", "/");

        String folderPath = S3Util.extractParentPrefix(path);

        return "<b>"
                + folderPath.replace(
                "/",
                " / ")
                + "<font color='"
                + UIThemeManager.TRANSFER_PANEL_COLOR_FILEFOLDER
                + "'>"
                + S3Util.escapeHtml(path.substring(folderPath.length()))
                + "</font></b>";
    }

    public static String getTransferPanelTargetDisplayName(TransferType transferType, String repositoryName, String targetBucket, String targetObjectKey, String localPath) {
        StringBuilder display = new StringBuilder();

        if (transferType  == TransferType.UPLOAD) {
            display.append(
                    S3Util.getTransferPanelDisplayBucketName(
                            repositoryName,
                            targetBucket));

            display.append(
                    S3Util.getTransferPanelDisplayLastFileFolder(
                            targetObjectKey));
        } else if (transferType == TransferType.DOWNLOAD) {
            if (localPath != null) {
                display.append(S3Util.getTransferPanelDisplayLastFileFolder( localPath.toString()));
            }

        } else if (transferType == TransferType.COPY  || transferType == TransferType.MOVE) {
            display.append(
                    S3Util.getTransferPanelDisplayBucketName(
                            repositoryName,
                            targetBucket));

            display.append(
                    S3Util.getTransferPanelDisplayLastFileFolder(
                            targetObjectKey));
        }

        return display.toString();
    }
}
