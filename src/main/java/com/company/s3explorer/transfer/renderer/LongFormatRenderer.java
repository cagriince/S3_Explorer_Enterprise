package com.company.s3explorer.transfer.renderer;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JTable;
import javax.swing.JLabel;
import java.text.NumberFormat;
import java.awt.Component;

public class LongFormatRenderer extends DefaultTableCellRenderer {

    private final NumberFormat numberFormat;

    public LongFormatRenderer() {
        // Yerel ayarlara göre biçimlendirici oluştur
        this.numberFormat = NumberFormat.getNumberInstance();
        // Sayıları sağa hizalayarak okunurluğu artır
        setHorizontalAlignment(JLabel.RIGHT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof Long) {
            // Long değerini binler ayracı ile formatla
            value = numberFormat.format((Long) value);
        } else if (value instanceof Number) {
            // Diğer sayı tiplerini de destekle
            value = numberFormat.format(((Number) value).longValue());
        }

        value = value + " ms";

        // Üst sınıftaki metodun atanmış formatlanmış değeri işlemesini sağla
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
}
