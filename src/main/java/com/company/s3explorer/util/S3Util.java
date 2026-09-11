package com.company.s3explorer.util;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.ui.main.MainFrame;
import com.company.s3explorer.ui.theme.UIThemeManager;

import java.awt.*;
import java.nio.file.Path;
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

    public static String getTransferPanelProcessDetail(
        TransferType type,
        String groupName,
        String sourceRepository,
        String sourceBucket,
        String sourcePrefix,
        String targetRepository,
        String targetBucket,
        String targetPrefix,
        Path localPath
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("<html>");
        if (groupName != null) {
            sb.append(S3Util.getTransferPanelGroupName(groupName));
            sb.append("<br />");
        }
        sb.append("\uD83D\uDFB3 ");
        sb.append(S3Util.getTransferPanelSourceDisplayName(type, sourceRepository, sourceBucket, sourcePrefix, localPath));
        String target = S3Util.getTransferPanelTargetDisplayName(type, targetRepository, targetBucket, targetPrefix, localPath);
        if (!target.isEmpty()) {
            sb.append("<br />=\uD83D\uDF82 ");
            sb.append(target);
        }

        sb.append("</html>");

        return sb.toString();
    }
    
    private static String getTransferPanelDisplayBucketName(String repository, String bucket) {
        return "<b><font color='"
                + UIThemeManager.TRANSFER_PANEL_COLOR_BUCKET
                + "'>"
                + S3Util.escapeHtml(repository)
                + " | "
                + S3Util.escapeHtml(bucket)
                + "</font> / </b>";
    }

    private static String getTransferPanelDisplayLastFileFolder(String path) {
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

    private static String getTransferPanelSourceDisplayName(TransferType transferType, String sourceRepository, String sourceBucket, String soruceObjectKey, Path localPath) {

        StringBuilder display = new StringBuilder();

        if (transferType == TransferType.CREATE_FOLDER) {
            display.append(
                    S3Util.getTransferPanelDisplayBucketName(
                            sourceRepository,
                            sourceBucket));
            display.append(
                    S3Util.getTransferPanelDisplayLastFileFolder(
                            soruceObjectKey));
        } else if (transferType == TransferType.UPLOAD) {
            if (localPath != null) {
                display.append(
                        S3Util.getTransferPanelDisplayLastFileFolder(
                                localPath.toString()));
            }
        } else if (transferType == TransferType.DOWNLOAD || transferType == TransferType.DELETE || transferType == TransferType.COPY || transferType == TransferType.MOVE) {
            display.append(
                    S3Util.getTransferPanelDisplayBucketName(
                            sourceRepository,
                            sourceBucket));
            display.append(
                    S3Util.getTransferPanelDisplayLastFileFolder(
                            soruceObjectKey));
        }

        return display.toString();
    }

    private static String getTransferPanelTargetDisplayName(TransferType transferType, String targetRepository, String targetBucket, String targetObjectKey, Path localPath) {
        StringBuilder display = new StringBuilder();

        if (transferType  == TransferType.UPLOAD) {
            display.append(
                    S3Util.getTransferPanelDisplayBucketName(
                            targetRepository,
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
                            targetRepository,
                            targetBucket));

            display.append(
                    S3Util.getTransferPanelDisplayLastFileFolder(
                            targetObjectKey));
        }

        return display.toString();
    }

    private static String getTransferPanelGroupName(String groupName) {
        if (groupName == null) {
            return "";
        }

        return "<b><font color='"
                + UIThemeManager.TRANSFER_PANEL_COLOR_GROUP
                + "'>"
                + S3Util.escapeHtml(groupName)
                + "</font></b>";
    }

    public static String formatWithThousandSeparator(long number) {
        return String.format("%,d", number);
    }
}
