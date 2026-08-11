package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferType;

import javax.swing.table.DefaultTableCellRenderer;

public class TypeRenderer extends DefaultTableCellRenderer {

    @Override
    protected void setValue(Object value) {

        if (!(value instanceof TransferType type)) {
            super.setValue(value);
            return;
        }

        switch (type) {
            case UPLOAD -> setText("⬆ Upload");
            case DOWNLOAD -> setText("⬇ Download");
            case DELETE -> setText("🗑 Delete");
            case COPY -> setText("📄 Copy");
            case MOVE -> setText("🚚 Move");
            case CREATE_FOLDER -> setText("📁 Create Folder");
        }
    }
}
