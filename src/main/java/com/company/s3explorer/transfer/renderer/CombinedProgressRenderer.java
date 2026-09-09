package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.ui.transfer.TransferCombinedTableModel;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class CombinedProgressRenderer
        extends JPanel
        implements TableCellRenderer {

    private static final float BAR_SCALE = 0.45f;

    private final JProgressBar progressBar =
            new JProgressBar(
                    0,
                    100);

    public CombinedProgressRenderer() {

        setLayout(
                new GridBagLayout());

        setOpaque(true);

        progressBar.setStringPainted(
                true);

        progressBar.setBorderPainted(
                true);

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

        if (value instanceof
                TransferCombinedTableModel.GroupProgress groupProgress) {

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

        /*
         * Progress bar'ın kendi yüksekliğini
         * hücrenin tamamına yayılmasını engelle.
         *
         * GridBagLayout ile yatay genişlik korunuyor,
         * dikeyde ise preferred height kullanılıyor.
         */
        Dimension preferredSize =
                progressBar.getPreferredSize();

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
}