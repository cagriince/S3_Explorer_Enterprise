package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.table.DefaultTableCellRenderer;

public class TypeRenderer
        extends DefaultTableCellRenderer {

    @Override
    protected void setValue(Object value) {

        if (!(value instanceof TransferType type)) {

            super.setValue(value);
            return;
        }

        switch (type) {

            case UPLOAD -> {

                setText("Upload");
                setIcon(
                        IconProvider.ICON_UPLOAD);
            }

            case DOWNLOAD -> {

                setText("Download");
                setIcon(
                        IconProvider.ICON_DOWNLOAD);
            }

            case DELETE -> {

                setText("Delete");
                setIcon(
                        IconProvider.ICON_DELETE);
            }

            case COPY -> {

                setText("Copy");
                setIcon(
                        IconProvider.ICON_COPY);
            }

            case MOVE -> {

                setText("Move");
                setIcon(
                        IconProvider.ICON_CUT);
            }

            case RENAME -> {

                setText("Rename");
                setIcon(
                        IconProvider.ICON_RENAME);
            }

            case CREATE_FOLDER -> {

                setText("Create Folder");
                setIcon(
                        IconProvider.ICON_CREATE_FOLDER);
            }
        }
    }
}