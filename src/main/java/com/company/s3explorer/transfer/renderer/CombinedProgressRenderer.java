package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.ui.transfer.TransferCombinedTableModel;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class CombinedProgressRenderer
        extends JPanel
        implements TableCellRenderer {
    
    private final JProgressBar progressBar =
            new JProgressBar(
                    0,
                    100);

    public CombinedProgressRenderer() {

        setLayout(
                new BorderLayout());

        progressBar.setStringPainted(
                true);

        progressBar.setBorderPainted(
                true);

        add(
                progressBar,
                BorderLayout.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        int percent = 0;

        if (value instanceof TransferCombinedTableModel.GroupProgress groupProgress) {

            percent =
                    groupProgress.getPercent();

        } else if (value instanceof TransferRuntime runtime) {

            percent =
                    runtime.getPercent();
        }

        percent =
                Math.max(
                        0,
                        Math.min(
                                100,
                                percent));

        progressBar.setValue(
                percent);

        progressBar.setString(
                percent + "%");

        if (isSelected) {

            setBackground(
                    table.getSelectionBackground());

        } else {

            setBackground(
                    table.getBackground());
        }

        progressBar.setBackground(
                getBackground());

        return this;
    }
}