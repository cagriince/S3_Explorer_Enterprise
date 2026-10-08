package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.util.SizeFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class FileSizeRenderer
        extends DefaultTableCellRenderer {

    private static final int RIGHT_PADDING = 10;

    public FileSizeRenderer() {

        setHorizontalAlignment(
                SwingConstants.RIGHT);
    }

    @Override
    protected void setValue(
            Object value) {

        if (value == null) {

            setText("");

            return;
        }

        if (value instanceof Number number) {

            setText(
                    SizeFormatter.format(
                            number.longValue()));

            return;
        }

        setText(
                value.toString());
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        Component component =
                super.getTableCellRendererComponent(
                        table,
                        value,
                        isSelected,
                        hasFocus,
                        row,
                        column);

        setHorizontalAlignment(
                SwingConstants.RIGHT);

        Insets insets =
                getInsets();

        setBorder(
                new EmptyBorder(
                        insets.top,
                        insets.left,
                        insets.bottom,
                        RIGHT_PADDING));

        return component;
    }
}