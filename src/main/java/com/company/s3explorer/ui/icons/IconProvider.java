package com.company.s3explorer.ui.icons;

import com.company.s3explorer.ui.theme.UITheme;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;

public class IconProvider {
    public static Icon ICON_SYSTEM_FOLDER_HOME;
    public static Icon ICON_SYSTEM_UP_FOLDER;
    public static Icon ICON_SYSTEM_NEW_FOLDER;
    public static Icon ICON_SYSTEM_FOLDER;
    public static Icon ICON_SYSTEM_FILE;
    public static Icon ICON_SYSTEM_OPEN_FOLDER;
    public static Icon ICON_SYSTEM_CLOSED_FOLDER;
    public static Icon ICON_SYSTEM_LEAF_FILE;

    public static ImageIcon ICON_LOGO = createImage("src/main/java/com/company/s3explorer/ui/icons/logo.png", 32);

    public static Icon ICON_UPLOAD = null;
    public static ImageIcon ICON_UPLOAD_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/upload.png", 16);
    public static ImageIcon ICON_UPLOAD_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/upload-reverse.png", 16);
    public static ImageIcon ICON_DELETE = null;
    public static ImageIcon ICON_DELETE_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/delete.png", 16);
    public static ImageIcon ICON_DELETE_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/delete-reverse.png", 16);
    public static ImageIcon ICON_REFRESH = null;
    public static ImageIcon ICON_REFRESH_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/refresh.png", 16);
    public static ImageIcon ICON_REFRESH_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/refresh-reverse.png", 16);
    public static ImageIcon ICON_CUT = null;
    public static ImageIcon ICON_CUT_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/cut.png", 16);
    public static ImageIcon ICON_CUT_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/cut-reverse.png", 16);
    public static ImageIcon ICON_PASTE = null;
    public static ImageIcon ICON_PASTE_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/paste.png", 16);
    public static ImageIcon ICON_PASTE_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/paste-reverse.png", 16);
    public static ImageIcon ICON_COPY = null;
    public static ImageIcon ICON_COPY_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/copy.png", 16);
    public static ImageIcon ICON_COPY_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/copy-reverse.png", 16);
    public static ImageIcon ICON_DOWNLOAD = null;
    public static ImageIcon ICON_DOWNLOAD_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/download.png", 16);
    public static ImageIcon ICON_DOWNLOAD_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/download-reverse.png", 16);
    public static ImageIcon ICON_CREATE_FOLDER = null;
    public static ImageIcon ICON_CREATE_FOLDER_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/create-folder.png", 16);
    public static ImageIcon ICON_CREATE_FOLDER_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/create-folder-reverse.png", 16);
    public static ImageIcon ICON_REPOSITORY = null;
    public static ImageIcon ICON_REPOSITORY_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/db.png", 20);
    public static ImageIcon ICON_REPOSITORY_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/db-reverse.png", 20);
    public static ImageIcon ICON_BUCKET = null;
    public static ImageIcon ICON_BUCKET_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/hdd.png", 24);
    public static ImageIcon ICON_BUCKET_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/hdd-reverse.png", 24);
    public static ImageIcon ICON_SETTINGS = null;
    public static ImageIcon ICON_SETTINGS_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/settings.png", 16);
    public static ImageIcon ICON_SETTINGS_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/settings-reverse.png", 16);
    public static ImageIcon ICON_CANCEL = null;
    public static ImageIcon ICON_CANCEL_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/cancel.png", 16);
    public static ImageIcon ICON_CANCEL_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/cancel-reverse.png", 16);
    public static ImageIcon ICON_CANCEL_ALL = null;
    public static ImageIcon ICON_CANCEL_ALL_NORMAL = createImage("src/main/java/com/company/s3explorer/ui/icons/cancel-all.png", 16);
    public static ImageIcon ICON_CANCEL_ALL_REVERSE = createImage("src/main/java/com/company/s3explorer/ui/icons/cancel-all-reverse.png", 16);

    public static ImageIcon ICON_FOLDER_HOME_24 = createImage("src/main/java/com/company/s3explorer/ui/icons/home.png", 24);
    public static ImageIcon ICON_FOLDER_HOME_32 = createImage("src/main/java/com/company/s3explorer/ui/icons/home.png", 32);

    static {
        reloadSystemIcons(null);
    }

    public static void reloadSystemIcons(UITheme theme) {
        ICON_SYSTEM_FOLDER_HOME = UIManager.getIcon("FileChooser.homeFolderIcon");
        ICON_SYSTEM_UP_FOLDER = UIManager.getIcon("FileChooser.upFolderIcon");
        ICON_SYSTEM_NEW_FOLDER = UIManager.getIcon("FileChooser.newFolderIcon");
        ICON_SYSTEM_FOLDER = UIManager.getIcon("FileView.folderIcon");
        ICON_SYSTEM_FILE = UIManager.getIcon("FileView.fileIcon");
        ICON_SYSTEM_OPEN_FOLDER  = UIManager.getIcon("Tree.openIcon");
        ICON_SYSTEM_CLOSED_FOLDER = UIManager.getIcon("Tree.closedIcon");
        ICON_SYSTEM_LEAF_FILE     = UIManager.getIcon("Tree.leafIcon");

        if (theme == null) {
            theme = UIThemeManager.DEFAULT_THEME;
        }

        loadImages(theme.name());
    }

    private static ImageIcon createImage(String src, int size) {
        ImageIcon originalImageIcon = new ImageIcon(src);
        Image originalImage = originalImageIcon.getImage();
        Image scaledImage = originalImage.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }

    private static void loadImages(String themeName) {
        UITheme theme = UIThemeManager.getThemeByName(themeName);
        boolean normal = !theme.dark();

        ICON_UPLOAD = normal ? ICON_UPLOAD_NORMAL : ICON_UPLOAD_REVERSE;
        ICON_DELETE = normal ? ICON_DELETE_NORMAL : ICON_DELETE_REVERSE;
        ICON_REFRESH = normal ? ICON_REFRESH_NORMAL : ICON_REFRESH_REVERSE;
        ICON_CUT = normal ? ICON_CUT_NORMAL : ICON_CUT_REVERSE;
        ICON_PASTE = normal ? ICON_PASTE_NORMAL : ICON_PASTE_REVERSE;
        ICON_COPY = normal ? ICON_COPY_NORMAL : ICON_COPY_REVERSE;
        ICON_DOWNLOAD = normal ? ICON_DOWNLOAD_NORMAL : ICON_DOWNLOAD_REVERSE;
        ICON_CREATE_FOLDER = normal ? ICON_CREATE_FOLDER_NORMAL : ICON_CREATE_FOLDER_REVERSE;
        ICON_REPOSITORY = normal ? ICON_REPOSITORY_NORMAL : ICON_REPOSITORY_REVERSE;
        ICON_BUCKET = normal ? ICON_BUCKET_NORMAL : ICON_BUCKET_REVERSE;
        ICON_SETTINGS = normal ? ICON_SETTINGS_NORMAL : ICON_SETTINGS_REVERSE;
        ICON_CANCEL = normal ? ICON_CANCEL_NORMAL : ICON_CANCEL_REVERSE;
        ICON_CANCEL_ALL = normal ? ICON_CANCEL_ALL_NORMAL : ICON_CANCEL_ALL_REVERSE;
    }

    public static Icon loadSvgIcon(
            String resourcePath,
            int size) {

        String normalizedPath =
                resourcePath.startsWith("/")
                        ? resourcePath.substring(1)
                        : resourcePath;

        return new FlatSVGIcon(
                normalizedPath,
                size,
                size);
    }
}
