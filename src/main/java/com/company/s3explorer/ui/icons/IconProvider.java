package com.company.s3explorer.ui.icons;

import com.company.s3explorer.ui.theme.UITheme;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IconProvider {
    private static final Map<String, ImageIcon> FILE_TYPE_ICONS = new ConcurrentHashMap<>();
    
    public static ImageIcon ICON_SYSTEM_FOLDER_HOME;
    public static ImageIcon ICON_SYSTEM_UP_FOLDER;
    public static ImageIcon ICON_SYSTEM_NEW_FOLDER;
    public static ImageIcon ICON_SYSTEM_FOLDER;
    public static ImageIcon ICON_SYSTEM_FILE;
    public static ImageIcon ICON_SYSTEM_OPEN_FOLDER;
    public static ImageIcon ICON_SYSTEM_CLOSED_FOLDER;
    public static ImageIcon ICON_SYSTEM_LEAF_FILE;

    public static ImageIcon ICON_LOGO = loadIcon("logo", 32);//32
    public static ImageIcon ICON_REPOSITORY = null;
    public static ImageIcon ICON_REPOSITORY_NORMAL = loadIcon("db", 24);//20
    public static ImageIcon ICON_REPOSITORY_REVERSE = loadIcon("db-reverse", 24);//20
    public static ImageIcon ICON_BUCKET = null;
    public static ImageIcon ICON_BUCKET_NORMAL = loadIcon("hdd", 24);//24
    public static ImageIcon ICON_BUCKET_REVERSE = loadIcon("hdd-reverse", 24);//24
    
    public static ImageIcon ICON_UPLOAD = null;
    public static ImageIcon ICON_UPLOAD_NORMAL = loadIcon("upload");
    public static ImageIcon ICON_UPLOAD_REVERSE = loadIcon("upload-reverse");
    public static ImageIcon ICON_UPLOAD_ENCRYPTED = null;
    public static ImageIcon ICON_UPLOAD_ENCRYPTED_NORMAL = loadIcon("upload-encrypted");
    public static ImageIcon ICON_UPLOAD_ENCRYPTED_REVERSE = loadIcon("upload-encrypted-reverse");
    public static ImageIcon ICON_DELETE = null;
    public static ImageIcon ICON_DELETE_NORMAL = loadIcon("delete");
    public static ImageIcon ICON_DELETE_REVERSE = loadIcon("delete-reverse");
    public static ImageIcon ICON_RENAME = null;
    public static ImageIcon ICON_RENAME_NORMAL = loadIcon("rename");
    public static ImageIcon ICON_RENAME_REVERSE = loadIcon("rename-reverse");
    public static ImageIcon ICON_PROPERTIES = null;
    public static ImageIcon ICON_PROPERTIES_NORMAL = loadIcon("properties");
    public static ImageIcon ICON_PROPERTIES_REVERSE = loadIcon("properties-reverse");
    public static ImageIcon ICON_REFRESH = null;
    public static ImageIcon ICON_REFRESH_NORMAL = loadIcon("refresh");
    public static ImageIcon ICON_REFRESH_REVERSE = loadIcon("refresh-reverse");
    public static ImageIcon ICON_CUT = null;
    public static ImageIcon ICON_CUT_NORMAL = loadIcon("cut");
    public static ImageIcon ICON_CUT_REVERSE = loadIcon("cut-reverse");
    public static ImageIcon ICON_PASTE = null;
    public static ImageIcon ICON_PASTE_NORMAL = loadIcon("paste");
    public static ImageIcon ICON_PASTE_REVERSE = loadIcon("paste-reverse");
    public static ImageIcon ICON_COPY = null;
    public static ImageIcon ICON_COPY_NORMAL = loadIcon("copy");
    public static ImageIcon ICON_COPY_REVERSE = loadIcon("copy-reverse");
    public static ImageIcon ICON_DOWNLOAD = null;
    public static ImageIcon ICON_DOWNLOAD_NORMAL = loadIcon("download");
    public static ImageIcon ICON_DOWNLOAD_REVERSE = loadIcon("download-reverse");
    public static ImageIcon ICON_DOWNLOAD_DECRYPTED = null;
    public static ImageIcon ICON_DOWNLOAD_DECRYPTED_NORMAL = loadIcon("download-decrypted");
    public static ImageIcon ICON_DOWNLOAD_DECRYPTED_REVERSE = loadIcon("download-decrypted-reverse");
    public static ImageIcon ICON_CREATE_FOLDER = null;
    public static ImageIcon ICON_CREATE_FOLDER_NORMAL = loadIcon("create-folder");
    public static ImageIcon ICON_CREATE_FOLDER_REVERSE = loadIcon("create-folder-reverse");
    public static ImageIcon ICON_SETTINGS = null;
    public static ImageIcon ICON_SETTINGS_NORMAL = loadIcon("settings");
    public static ImageIcon ICON_SETTINGS_REVERSE = loadIcon("settings-reverse");
    public static ImageIcon ICON_CANCEL = null;
    public static ImageIcon ICON_CANCEL_NORMAL = loadIcon("cancel");
    public static ImageIcon ICON_CANCEL_REVERSE = loadIcon("cancel-reverse");
    public static ImageIcon ICON_CANCEL_ALL = null;
    public static ImageIcon ICON_CANCEL_ALL_NORMAL = loadIcon("cancel-all");
    public static ImageIcon ICON_CANCEL_ALL_REVERSE = loadIcon("cancel-all-reverse");
    public static ImageIcon ICON_BULK_DOWNLOAD = null;
    public static ImageIcon ICON_BULK_DOWNLOAD_NORMAL = loadIcon("download-all");
    public static ImageIcon ICON_BULK_DOWNLOAD_REVERSE = loadIcon("download-all-reverse");

    static {
        reloadSystemIcons(null);
    }

    public static void reloadSystemIcons(UITheme theme) {
        ICON_SYSTEM_FOLDER_HOME = convertIconToImageIcon(UIManager.getIcon("FileChooser.homeFolderIcon"));
        ICON_SYSTEM_UP_FOLDER = convertIconToImageIcon(UIManager.getIcon("FileChooser.upFolderIcon"));
        ICON_SYSTEM_NEW_FOLDER = convertIconToImageIcon(UIManager.getIcon("FileChooser.newFolderIcon"));
        ICON_SYSTEM_FOLDER = convertIconToImageIcon(UIManager.getIcon("FileView.folderIcon"));
        ICON_SYSTEM_FILE = convertIconToImageIcon(UIManager.getIcon("FileView.fileIcon"));
        ICON_SYSTEM_OPEN_FOLDER  = convertIconToImageIcon(UIManager.getIcon("Tree.openIcon"));
        ICON_SYSTEM_CLOSED_FOLDER = convertIconToImageIcon(UIManager.getIcon("Tree.closedIcon"));
        ICON_SYSTEM_LEAF_FILE     = convertIconToImageIcon(UIManager.getIcon("Tree.leafIcon"));

        if (theme == null) {
            theme = UIThemeManager.DEFAULT_THEME;
        }

        loadImages(theme.name());
    }
/*
    private static ImageIcon createImage(String src, int size) {
        ImageIcon originalImageIcon = new ImageIcon(src);
        Image originalImage = originalImageIcon.getImage();
        Image scaledImage = originalImage.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }*/

    private static void loadImages(String themeName) {
        UITheme theme = UIThemeManager.getThemeByName(themeName);
        boolean normal = !theme.dark();

        ICON_UPLOAD = normal ? ICON_UPLOAD_NORMAL : ICON_UPLOAD_REVERSE;
        ICON_UPLOAD_ENCRYPTED = normal ? ICON_UPLOAD_ENCRYPTED_NORMAL : ICON_UPLOAD_ENCRYPTED_REVERSE;
        ICON_DELETE = normal ? ICON_DELETE_NORMAL : ICON_DELETE_REVERSE;
        ICON_RENAME = normal ? ICON_RENAME_NORMAL : ICON_RENAME_REVERSE;
        ICON_PROPERTIES = normal ? ICON_PROPERTIES_NORMAL : ICON_PROPERTIES_REVERSE;
        ICON_REFRESH = normal ? ICON_REFRESH_NORMAL : ICON_REFRESH_REVERSE;
        ICON_CUT = normal ? ICON_CUT_NORMAL : ICON_CUT_REVERSE;
        ICON_PASTE = normal ? ICON_PASTE_NORMAL : ICON_PASTE_REVERSE;
        ICON_COPY = normal ? ICON_COPY_NORMAL : ICON_COPY_REVERSE;
        ICON_DOWNLOAD = normal ? ICON_DOWNLOAD_NORMAL : ICON_DOWNLOAD_REVERSE;
        ICON_DOWNLOAD_DECRYPTED = normal ? ICON_DOWNLOAD_DECRYPTED_NORMAL : ICON_DOWNLOAD_DECRYPTED_REVERSE;
        ICON_CREATE_FOLDER = normal ? ICON_CREATE_FOLDER_NORMAL : ICON_CREATE_FOLDER_REVERSE;
        ICON_REPOSITORY = normal ? ICON_REPOSITORY_NORMAL : ICON_REPOSITORY_REVERSE;
        ICON_BUCKET = normal ? ICON_BUCKET_NORMAL : ICON_BUCKET_REVERSE;
        ICON_SETTINGS = normal ? ICON_SETTINGS_NORMAL : ICON_SETTINGS_REVERSE;
        ICON_CANCEL = normal ? ICON_CANCEL_NORMAL : ICON_CANCEL_REVERSE;
        ICON_CANCEL_ALL = normal ? ICON_CANCEL_ALL_NORMAL : ICON_CANCEL_ALL_REVERSE;
        ICON_BULK_DOWNLOAD = normal ? ICON_BULK_DOWNLOAD_NORMAL : ICON_BULK_DOWNLOAD_REVERSE;
    }

    public static ImageIcon loadSvgIcon(
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

    public static ImageIcon getFileTypeIcon(
            String filename) {

        String iconName =
                FileIconRegistry.findIconName(
                        filename);

        return FILE_TYPE_ICONS.computeIfAbsent(
                iconName,
                IconProvider::loadFileTypeIcon);
    }

    private static ImageIcon loadFileTypeIcon(
            String iconName) {

        String resourcePath =
                "file-icons/svg/"
                        + iconName
                        + ".svg";

        ClassLoader classLoader =
                IconProvider.class
                        .getClassLoader();

        if (classLoader.getResource(
                resourcePath) == null) {

            return ICON_SYSTEM_FILE;
        }

        return loadSvgIcon(
                resourcePath,
                16);
    }

    private static ImageIcon loadIcon(String iconName) {
        return loadIcon(
                iconName,
                20);
    }
    
    private static ImageIcon loadIcon(String iconName, int size) {

        String resourcePath =
                "icons/"
                        + iconName
                        + ".svg";

        ClassLoader classLoader =
                IconProvider.class
                        .getClassLoader();

        if (classLoader.getResource(
                resourcePath) == null) {

            return ICON_SYSTEM_FILE;
        }

        return loadSvgIcon(
                resourcePath,
                size);
    }

    private static ImageIcon convertIconToImageIcon(Icon icon) {
        if (icon == null) {
            return null;
        }
        
        if (icon instanceof ImageIcon) {
            return (ImageIcon) icon;
        }

        int width = icon.getIconWidth();
        int height = icon.getIconHeight();

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();

        icon.paintIcon(null, g2d, 0, 0);
        g2d.dispose();

        return new ImageIcon(bufferedImage);
    }
}
