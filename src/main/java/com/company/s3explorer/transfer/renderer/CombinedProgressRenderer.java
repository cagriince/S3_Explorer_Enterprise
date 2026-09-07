package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.ui.transfer.TransferCombinedTableModel;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**

 * Combined Transfer tablosundaki Progress kolonunu render eder.
 *
 * Individual task:
 * 
 TransferRuntime -> percent
 
 *
 * Group:
 * 
 GroupProgress -> completed / detected
 

 */
public class CombinedProgressRenderer
        extends JProgressBar
        implements TableCellRenderer {


    private static final float BAR_SCALE =
            0.6f;

    public CombinedProgressRenderer() {

        setMinimum(0);
        setMaximum(100);

        setBorderPainted(false);
        setStringPainted(true);

        Font currentFont =
                getFont();

        setFont(
                currentFont.deriveFont(
                        16.0f));
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        if (value instanceof TransferRuntime runtime) {

            int percent =
                    runtime.getPercent();

            setValue(percent);

            setString(
                    percent + " %");

            return this;
        }

        if (value instanceof TransferCombinedTableModel.GroupProgress progress) {

            int percent =
                    progress.getPercent();

            setValue(percent);

            setString(
                    progress.getText());

            return this;
        }

        setValue(0);
        setString("");

        return this;
    }

    @Override
    protected void paintComponent(
            Graphics g) {

        int y =
                Math.round(
                        getHeight()
                                * (1 - BAR_SCALE)
                                / 2.0f);

        Graphics2D g2 =
                (Graphics2D) g.create();

        try {

            g2.translate(
                    0,
                    y);

            g2.scale(
                    1.0,
                    BAR_SCALE);

            super.paintComponent(g2);

        } finally {

            g2.dispose();
        }
    }


}