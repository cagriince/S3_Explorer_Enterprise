package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.util.DateFormatter;

import javax.swing.table.DefaultTableCellRenderer;
import java.time.Instant;

public class InstantRenderer extends DefaultTableCellRenderer {

    @Override
    protected void setValue(Object value) {
        super.setValue(DateFormatter.format((Instant) value));
    }
}
