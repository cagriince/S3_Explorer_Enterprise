package com.company.s3explorer.transfer.renderer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class PaddedTableCellRenderer extends DefaultTableCellRenderer {

    private static final int HORIZONTAL_PADDING = 6;
    private static final int VERTICAL_PADDING = 2;

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

        setBorder(
                BorderFactory.createEmptyBorder(
                        VERTICAL_PADDING,
                        HORIZONTAL_PADDING,
                        VERTICAL_PADDING,
                        HORIZONTAL_PADDING));

        return component;
    }
}