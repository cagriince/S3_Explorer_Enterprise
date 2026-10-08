package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.ui.transfer.TransferCombinedTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class TransferProgressRenderer
        extends DefaultTableCellRenderer {

    private static final int BAR_HEIGHT = 6;

    private static final int HORIZONTAL_PADDING = 6;

    private int percent;

    public TransferProgressRenderer() {

        //setOpaque(false);

        setPreferredSize(
                new Dimension(
                        100,
                        BAR_HEIGHT + 20));
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        percent = extractPercent(value);

        percent =
                Math.max(
                        0,
                        Math.min(
                                100,
                                percent));

        int rowHeight =
                table.getRowHeight(row);

        setPreferredSize(
                new Dimension(
                        100,
                        Math.max(
                                BAR_HEIGHT,
                                rowHeight)));

        return this;
    }

    private int extractPercent(
            Object value) {

        if (value instanceof
                TransferCombinedTableModel.GroupProgress groupProgress) {

            return groupProgress.getPercent();
        }

        if (value instanceof TransferRuntime runtime) {

            return runtime.getPercent();
        }

        return 0;
    }

    @Override
    protected void paintComponent(
            Graphics graphics) {

        super.paintComponent(
                graphics);

        Graphics2D g =
                (Graphics2D)
                        graphics.create();

        try {

            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int width =
                    getWidth();

            int height =
                    getHeight();

            int barWidth =
                    Math.max(
                            0,
                            width
                                    - (HORIZONTAL_PADDING * 2));

            int x =
                    HORIZONTAL_PADDING;

            int y =
                    (height - BAR_HEIGHT) / 2;

            /*
             * Track
             */
            g.setColor(
                    getTrackColor());

            g.fillRoundRect(
                    x,
                    y,
                    barWidth,
                    BAR_HEIGHT,
                    BAR_HEIGHT,
                    BAR_HEIGHT);

            /*
             * Progress
             */
            int progressWidth =
                    Math.round(
                            barWidth
                                    * (percent / 100.0f));

            if (progressWidth > 0) {

                g.setColor(
                        getProgressColor());

                g.fillRoundRect(
                        x,
                        y,
                        progressWidth,
                        BAR_HEIGHT,
                        BAR_HEIGHT,
                        BAR_HEIGHT);
            }

            /*
             * Percentage text
             */
            String text =
                    percent + "%";

            FontMetrics metrics =
                    g.getFontMetrics();

            int textWidth =
                    metrics.stringWidth(text);

            int textX =
                    width
                            - HORIZONTAL_PADDING
                            - textWidth;

            int textY =
                    y
                            + BAR_HEIGHT
                            + metrics.getAscent()
                            + 2;

            g.setColor(
                    getForeground());

            g.drawString(
                    text,
                    Math.max(
                            HORIZONTAL_PADDING,
                            textX),
                    Math.min(
                            height - 1,
                            textY));

        } finally {

            g.dispose();
        }
    }

    private Color getTrackColor() {

        Color background =
                UIManager.getColor(
                        "ProgressBar.background");

        if (background != null) {
            return background;
        }

        return new Color(
                180,
                180,
                180);
    }

    private Color getProgressColor() {

        Color foreground =
                UIManager.getColor(
                        "ProgressBar.foreground");

        if (foreground != null) {
            return foreground;
        }

        return new Color(
                80,
                140,
                220);
    }
}