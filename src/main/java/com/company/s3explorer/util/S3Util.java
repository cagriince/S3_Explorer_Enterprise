package com.company.s3explorer.util;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.ui.explorer.S3FileItem;
import com.company.s3explorer.ui.main.MainFrame;
import com.company.s3explorer.ui.theme.UIThemeManager;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.text.CollationKey;
import java.text.Collator;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        } else if (transferType == TransferType.UPLOAD || transferType == TransferType.UPLOAD_GROUP) {
            if (localPath != null) {
                display.append(
                        S3Util.getTransferPanelDisplayLastFileFolder(
                                localPath.toString()));
            }
        } else if (transferType == TransferType.DOWNLOAD || transferType == TransferType.DOWNLOAD_GROUP || transferType == TransferType.DELETE || transferType == TransferType.DELETE_GROUP || transferType == TransferType.COPY || transferType == TransferType.COPY_GROUP || transferType == TransferType.MOVE || transferType == TransferType.MOVE_GROUP || transferType == TransferType.RENAME || transferType == TransferType.RENAME_GROUP) {
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

        if (transferType  == TransferType.UPLOAD || transferType  == TransferType.UPLOAD_GROUP) {
            display.append(
                    S3Util.getTransferPanelDisplayBucketName(
                            targetRepository,
                            targetBucket));

            display.append(
                    S3Util.getTransferPanelDisplayLastFileFolder(
                            targetObjectKey));
        } else if (transferType == TransferType.DOWNLOAD || transferType == TransferType.DOWNLOAD_GROUP) {
            if (localPath != null) {
                display.append(S3Util.getTransferPanelDisplayLastFileFolder( localPath.toString()));
            }

        } else if (transferType == TransferType.COPY || transferType == TransferType.COPY_GROUP || transferType == TransferType.MOVE || transferType == TransferType.MOVE_GROUP || transferType == TransferType.RENAME || transferType == TransferType.RENAME_GROUP) {
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

    public static int naturalTurkishCompare(
            String left,
            String right,
            Collator collator) {

        return naturalTurkishCompare(
                left,
                right,
                collator,
                new HashMap<>());
    }

    public static int naturalTurkishCompare(
            String left,
            String right,
            Collator collator,
            Map<Character, CollationKey> characterKeyCache) {

        if (left == right) {
            return 0;
        }

        if (left == null) {
            return -1;
        }

        if (right == null) {
            return 1;
        }

        int leftIndex = 0;
        int rightIndex = 0;

        while (leftIndex < left.length()
                && rightIndex < right.length()) {

            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);

            /*
             * Preserve natural numeric ordering:
             * file2 sorts before file10.
             */
            if (Character.isDigit(leftChar)
                    && Character.isDigit(rightChar)) {

                int leftStart = leftIndex;
                int rightStart = rightIndex;

                while (leftIndex < left.length()
                        && Character.isDigit(
                        left.charAt(leftIndex))) {
                    leftIndex++;
                }

                while (rightIndex < right.length()
                        && Character.isDigit(
                        right.charAt(rightIndex))) {
                    rightIndex++;
                }

                /*
                 * Ignore leading zeroes without allocating
                 * normalized strings or executing a regex.
                 * Keep at least one digit for an all-zero number.
                 */
                int normalizedLeftStart = leftStart;
                int normalizedRightStart = rightStart;

                while (normalizedLeftStart < leftIndex - 1
                        && left.charAt(normalizedLeftStart) == '0') {
                    normalizedLeftStart++;
                }

                while (normalizedRightStart < rightIndex - 1
                        && right.charAt(normalizedRightStart) == '0') {
                    normalizedRightStart++;
                }

                int leftDigits =
                        leftIndex - normalizedLeftStart;

                int rightDigits =
                        rightIndex - normalizedRightStart;

                if (leftDigits != rightDigits) {
                    return Integer.compare(
                            leftDigits,
                            rightDigits);
                }

                for (int i = 0; i < leftDigits; i++) {

                    char leftDigit =
                            left.charAt(normalizedLeftStart + i);

                    char rightDigit =
                            right.charAt(normalizedRightStart + i);

                    if (leftDigit != rightDigit) {
                        return Character.compare(
                                leftDigit,
                                rightDigit);
                    }
                }

                /*
                 * Numeric values are equal. Continue after
                 * both complete digit sequences, preserving
                 * the existing comparison behavior.
                 */
                continue;
            }

            /*
             * Cache the collation key for each individual
             * character. This preserves the existing algorithm's
             * character-by-character Collator comparison.
             */
            CollationKey leftKey =
                    characterKeyCache.computeIfAbsent(
                            leftChar,
                            ch -> collator.getCollationKey(
                                    String.valueOf(ch)));

            CollationKey rightKey =
                    characterKeyCache.computeIfAbsent(
                            rightChar,
                            ch -> collator.getCollationKey(
                                    String.valueOf(ch)));

            int charCompare =
                    leftKey.compareTo(rightKey);

            if (charCompare != 0) {
                return charCompare;
            }

            leftIndex++;
            rightIndex++;
        }

        /*
         * Preserve the existing prefix rule:
         * the shorter string sorts first when the common
         * prefix has matched completely.
         */
        if (leftIndex == left.length()
                && rightIndex < right.length()) {
            return -1;
        }

        if (rightIndex == right.length()
                && leftIndex < left.length()) {
            return 1;
        }

        return 0;
    }

    public static void setActionIcon(Action action, Icon icon) {
        action.putValue(Action.SMALL_ICON, icon);
        action.putValue(Action.LARGE_ICON_KEY, icon);
    }

    public static String getOperationGroupName(
            List<S3FileItem> items) {

        if (items == null
                || items.isEmpty()) {
            return "";
        }

        int size = items.size();
        int displayedCount = Math.min(size, 3);

        String groupName =
                items.stream()
                        .limit(displayedCount)
                        .map(S3FileItem::getName)
                        .collect(
                                java.util.stream.Collectors.joining(", "));

        if (size <= 3) {
            return groupName;
        }

        int remainingCount = size - displayedCount;

        return groupName
                + " and "
                + remainingCount
                + (remainingCount == 1 ? " other" : " others");
    }
}
