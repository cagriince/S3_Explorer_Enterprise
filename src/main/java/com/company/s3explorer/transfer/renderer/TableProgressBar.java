package com.company.s3explorer.transfer.renderer;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

public class TableProgressBar
        extends JPanel {

    private final JProgressBar progressBar =
            new JProgressBar(
                    0,
                    100);

    public TableProgressBar() {

        setLayout(
                new BorderLayout());

        setOpaque(false);

        progressBar.setStringPainted(
                true);

        progressBar.setBorderPainted(
                false);

        add(
                progressBar,
                BorderLayout.CENTER);
    }

    public void setValue(
            int value) {

        progressBar.setValue(
                value);
    }

    public void setString(
            String text) {

        progressBar.setString(
                text);
    }

    public Dimension getProgressBarPreferredSize() {

        return progressBar.getPreferredSize();
    }

    public void setProgressBarBackground(
            Color color) {

        progressBar.setBackground(
                color);
    }

    public void setTableBorderColor(
            Color color) {

        if (color == null) {

            setBorder(null);

            return;
        }

        setBorder(
                new LineBorder(
                        color,
                        1));
    }
}