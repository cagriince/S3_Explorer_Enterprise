package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.ui.transfer.TransferCombinedTableModel;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class CombinedProgressRenderer
        extends JProgressBar
        implements TableCellRenderer {

    private static final float BAR_SCALE = 0.45f;

    public CombinedProgressRenderer() {

        super(
                0,
                100);

        setStringPainted(
                true);
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

        setValue(
                percent);

        setString(
                percent + "%");

        Dimension preferredSize =
                getPreferredSize();

        int preferredHeight =
                Math.max(
                        8,
                        Math.round(
                                table.getRowHeight(row)
                                        * BAR_SCALE));

        setPreferredSize(
                new Dimension(
                        preferredSize.width,
                        preferredHeight));

        return this;
    }
}