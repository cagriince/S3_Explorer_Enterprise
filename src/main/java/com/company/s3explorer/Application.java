package com.company.s3explorer;

import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.main.MainFrame;
import com.formdev.flatlaf.*;

import javax.swing.*;
import java.awt.*;

public class Application {

    public static void main(String[] args) {
        //FlatDarkLaf.setup();
        //FlatIntelliJLaf.setup();
        //FlatDarculaLaf.setup();
        //FlatLightLaf.setup();
        /*try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }*/
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setIconImage(IconProvider.ICON_LOGO.getImage());
            frame.setVisible(true);
        });
    }
}