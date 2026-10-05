package com.company.s3explorer;

import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.main.MainFrame;

import javax.swing.*;

public class Application {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setIconImage(IconProvider.ICON_LOGO.getImage());
            frame.setVisible(true);
        });
    }
}