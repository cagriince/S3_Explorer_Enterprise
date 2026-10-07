package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferRuntime;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ProgressBarRenderer
        extends JPanel
        implements TableCellRenderer {

    private static final float BAR_SCALE = 0.45f;

    private final TableProgressBar progressBar =
            new TableProgressBar();

    public ProgressBarRenderer() {

        setLayout(
                new GridBagLayout());

        setOpaque(false);

        GridBagConstraints constraints =
                new GridBagConstraints();

        constraints.gridx = 0;
        constraints.gridy = 0;

        constraints.weightx = 1.0;
        constraints.weighty = 1.0;

        constraints.fill =
                GridBagConstraints.HORIZONTAL;

        constraints.anchor =
                GridBagConstraints.CENTER;

        constraints.insets =
                new Insets(
                        0,
                        4,
                        0,
                        4);

        add(
                progressBar,
                constraints);
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

        if (value instanceof TransferRuntime runtime) {

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

        Color cellBackground =
                getCellBackground(
                        table,
                        isSelected,
                        row);

        progressBar.setProgressBarBackground(
                cellBackground);

        progressBar.setTableBorderColor(
                getTableBorderColor(
                        table));

        Dimension preferredSize =
                progressBar.getProgressBarPreferredSize();

        int preferredHeight =
                Math.max(
                        8,
                        Math.round(
                                table.getRowHeight(row)
                                        * BAR_SCALE));

        progressBar.setPreferredSize(
                new Dimension(
                        preferredSize.width,
                        preferredHeight));

        return this;
    }

    private Color getCellBackground(
            JTable table,
            boolean isSelected,
            int row) {

        if (isSelected) {

            return table.getSelectionBackground();
        }

        Color alternateRowColor =
                UIManager.getColor(
                        "Table.alternateRowColor");

        if (alternateRowColor != null
                && (row & 1) == 1) {

            return alternateRowColor;
        }

        return table.getBackground();
    }

    private Color getTableBorderColor(
            JTable table) {

        Color color =
                table.getGridColor();

        if (color != null) {
            return color;
        }

        color =
                UIManager.getColor(
                        "Table.gridColor");

        if (color != null) {
            return color;
        }

        color =
                UIManager.getColor(
                        "Separator.foreground");

        if (color != null) {
            return color;
        }

        return Color.GRAY;
    }
}