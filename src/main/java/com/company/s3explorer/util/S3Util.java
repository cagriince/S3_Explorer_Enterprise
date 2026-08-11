package com.company.s3explorer.util;

import com.company.s3explorer.ui.main.MainFrame;

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
}
