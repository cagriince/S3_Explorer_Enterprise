package com.company.s3explorer.ui.theme;

import com.company.s3explorer.ui.explorer.ExplorerPanel;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.transfer.TransferPanel;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UIThemeManager {
    private final ExplorerPanel explorerPanel;
    private final TransferPanel transferPanel;
    public static String TRANSFER_PANEL_COLOR_BUCKET;
    public static String TRANSFER_PANEL_COLOR_FILEFOLDER;
    public static String TRANSFER_PANEL_COLOR_GROUP;
    private static List<UITheme> themes;

    static {
        themes = new ArrayList<>();
        UIThemeManager.themes.add(UITheme.createThemeTitle("Standard Themes"));
        UIManager.LookAndFeelInfo installedThemes[] = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo theme : installedThemes) {
            themes.add(new UITheme(theme.getName(), theme.getClassName(), false));
        }

        UIThemeManager.themes.add(UITheme.createThemeTitle(null));
        UIThemeManager.themes.add(UITheme.createThemeTitle("Flat Laf Themes"));
        themes.add(new UITheme("FlatLightLaf", "com.formdev.flatlaf.FlatLightLaf", false));
        themes.add(new UITheme("FlatDarkLaf", "com.formdev.flatlaf.FlatDarkLaf", true));
        themes.add(new UITheme("FlatIntelliJLaf", "com.formdev.flatlaf.FlatIntelliJLaf", false));
        themes.add(new UITheme("FlatDarculaLaf", "com.formdev.flatlaf.FlatDarculaLaf",true));
        themes.add(new UITheme("FlatMacLightLaf", "com.formdev.flatlaf.themes.FlatMacLightLaf",false));
        themes.add(new UITheme("FlatMacDarkLaf", "com.formdev.flatlaf.themes.FlatMacDarkLaf",true));

        UIThemeManager.themes.add(UITheme.createThemeTitle(null));
        UIThemeManager.themes.add(UITheme.createThemeTitle("IntelliJ Themes"));
        boolean materialFound = false;
        for (FlatAllIJThemes.FlatIJLookAndFeelInfo theme : FlatAllIJThemes.INFOS) {
            if (!materialFound && theme.getName().indexOf("(Material)") > -1) {
                UIThemeManager.themes.add(UITheme.createThemeTitle(null));
                UIThemeManager.themes.add(UITheme.createThemeTitle("IntelliJ Material Themes"));
                materialFound = true;
            }
            themes.add(new UITheme(theme.getName(), theme.getClassName(), theme.isDark()));
        }
    }

    public static UITheme DEFAULT_THEME = UIThemeManager.getThemeFromClassName(UIManager.getSystemLookAndFeelClassName());

    public UIThemeManager(ExplorerPanel explorerPanel, TransferPanel transferPanel) {
        this.explorerPanel = explorerPanel;
        this.transferPanel = transferPanel;
    }

    public static UITheme getThemeByName(String name) {
        if (name == null) {
            return UIThemeManager.DEFAULT_THEME;
        }
        return themes.stream().filter(theme -> theme.name().equals(name)).findFirst().get();
    }

    public static UITheme getThemeFromClassName(String className) {
        return themes.stream().filter(theme -> theme.className().equals(className)).findFirst().get();
    }

    public static List<UITheme> getThemes() {
        return themes;
    }

    public void changeTheme(UITheme theme) {
        try {
            UIManager.setLookAndFeel(theme.className());
            IconProvider.reloadSystemIcons(theme);
            setTransferPanelColors(theme);
            com.formdev.flatlaf.FlatLaf.updateUI();
            explorerPanel.updateBreadcrumb(null);
            explorerPanel.setFolderTreeLeafIcon();
            explorerPanel.setButtonIcons();
            transferPanel.setButtonIcons();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setTransferPanelColors(UITheme theme) {
        boolean normal = !theme.dark();

        if (normal) {
            TRANSFER_PANEL_COLOR_BUCKET = "green";
            TRANSFER_PANEL_COLOR_FILEFOLDER = "blue";
            TRANSFER_PANEL_COLOR_GROUP = "red";
        }
        else {
            TRANSFER_PANEL_COLOR_BUCKET = "#AAFF00";
            TRANSFER_PANEL_COLOR_FILEFOLDER = "yellow";
            TRANSFER_PANEL_COLOR_GROUP = "orange";
        }
    }
}
