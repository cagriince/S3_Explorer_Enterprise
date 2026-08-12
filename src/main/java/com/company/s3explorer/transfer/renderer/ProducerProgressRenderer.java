package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.producer.ProducerRuntime;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ProducerProgressRenderer
        implements TableCellRenderer {

    private final JLabel label = new JLabel();

    public ProducerProgressRenderer() {
        label.setOpaque(true);
        label.setHorizontalAlignment(
                SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        if (value instanceof ProducerRuntime runtime) {

            label.setText(
                    String.format(
                            "%,d files queued",
                            runtime.getDiscoveredCount()));

        } else {

            label.setText("");
        }

        if (value instanceof ProducerRuntime runtime
                && runtime.getStatus().isFinished()) {

            label.setFont(
                    label.getFont().deriveFont(
                            Font.BOLD));
        }

        return label;
    }
}