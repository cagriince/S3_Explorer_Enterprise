package com.company.s3explorer;

import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.main.MainFrame;
import com.company.s3explorer.util.ProxyConfigurer;

import javax.swing.*;

public class Application {

    public static void main(String[] args) {
        ProxyConfigurer.configureSystemProxies();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setIconImage(IconProvider.ICON_LOGO.getImage());
            frame.setVisible(true);
        });
    }
}