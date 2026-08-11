package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.util.SizeFormatter;

import javax.swing.table.DefaultTableCellRenderer;

public class FileSizeRenderer extends DefaultTableCellRenderer {

    @Override
    protected void setValue(Object value) {
        super.setValue(SizeFormatter.format((Long) value));
    }
}
