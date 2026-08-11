package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferRuntime;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
    private static final float BAR_SCALE = (float) 0.6;

    public ProgressBarRenderer() {
        setMinimum(0);
        setMaximum(100);

        setBorderPainted(false);
        setStringPainted(true);

        Font mevcutFont = this.getFont();
        this.setFont(mevcutFont.deriveFont(16.0f));
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        TransferRuntime runtime = (TransferRuntime) value;
        int percent = runtime.getPercent();
        setValue(percent);
        setString(percent + " %");

        return this;
    }
/*
    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        int y = (height - BAR_HEIGHT) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Arka plan
            g2.setColor(getBackground());
            g2.fillRect(0, y, width, BAR_HEIGHT);

            // Progress
            int progressWidth = (int) (width * getValue() / 100.0);

            Color progressBarBGColor = UIManager.getColor("ProgressBar.background");
            g2.setColor(Color.ORANGE);
            g2.fillRect(0, y, progressWidth, BAR_HEIGHT);

            // Yazı
            String text = getValue() + "%";
            FontMetrics fm = g2.getFontMetrics(getFont());

            int textWidth = fm.stringWidth(text);
            int textX = (width - textWidth) / 2;
            int textY = (height - fm.getHeight()) / 2 + fm.getAscent();

            //g2.setColor(Color.BLACK);

            g2.drawString(text, textX, textY);
        } finally {
            g2.dispose();
        }
    }*/

    @Override
    protected void paintComponent(Graphics g) {
        int y = Math.round((getHeight() * (1-BAR_SCALE)) / (float) 2.0);
        Graphics2D g2 = (Graphics2D) g.create();

        try {
            g2.translate(0, y);
            // Progress bar'ın çizimini küçük alana sıkıştır
            g2.scale(1.0, BAR_SCALE);
            super.paintComponent(g2);
        } finally {
            g2.dispose();
        }
    }
}
