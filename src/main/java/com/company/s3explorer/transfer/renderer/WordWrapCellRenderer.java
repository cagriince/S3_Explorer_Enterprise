package com.company.s3explorer.transfer.renderer;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class WordWrapCellRenderer extends JTextArea implements TableCellRenderer {
    public WordWrapCellRenderer() {
        setLineWrap(false);
        setWrapStyleWord(false);
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setText(value == null ? "" : value.toString());

        // Satır seçildiğinde arkaplan renklerini JTable uyumuna göre ayarlama
        if (isSelected) {
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else {
            setBackground(table.getBackground());
            setForeground(table.getForeground());
        }
        return this;
    }
}
