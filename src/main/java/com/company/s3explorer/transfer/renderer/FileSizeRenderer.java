package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.util.SizeFormatter;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class FileSizeRenderer extends DefaultTableCellRenderer {
    private int RIGHT_PADDING = 10;
    
    public FileSizeRenderer() {
        setHorizontalAlignment(SwingConstants.RIGHT);
    }
    
    @Override
    protected void setValue(Object value) {
        super.setValue(SizeFormatter.format((Long) value));
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

        Insets insets = getInsets();

        setBorder(
                BorderFactory.createEmptyBorder(
                        insets.top,
                        insets.left,
                        insets.bottom,
                        RIGHT_PADDING));

        return component;
    }
}
