package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.table.DefaultTableCellRenderer;

/**

 * Combined Transfer tablosundaki Process kolonunu render eder.
 *
 * Individual task:
 * 
 TransferType
 
 *
 * Group:
 * 
 String operation
 

 */
public class CombinedTypeRenderer
        extends DefaultTableCellRenderer {


    @Override
    protected void setValue(Object value) {

        if (value instanceof TransferType type) {

            switch (type) {

                case UPLOAD, UPLOAD_GROUP -> {
                        setText("Upload" + (type == TransferType.UPLOAD_GROUP ? " Group" : ""));
                        setIcon(IconProvider.ICON_UPLOAD);
                }
                case DOWNLOAD, DOWNLOAD_GROUP -> {
                        setText("Download" + (type == TransferType.DOWNLOAD_GROUP ? " Group" : ""));
                        setIcon(IconProvider.ICON_DOWNLOAD);
                }
                case DELETE, DELETE_GROUP -> {
                        setText("Delete" + (type == TransferType.DELETE_GROUP ? " Group" : ""));
                        setIcon(IconProvider.ICON_DELETE);
                }
                case COPY, COPY_GROUP -> {
                        setText("Copy" + (type == TransferType.COPY_GROUP ? " Group" : ""));
                        setIcon(IconProvider.ICON_COPY);
                }
                case MOVE, MOVE_GROUP -> {
                        setText("Move" + (type == TransferType.MOVE_GROUP ? " Group" : ""));
                        setIcon(IconProvider.ICON_CUT);
                }
                case CREATE_FOLDER -> {
                        setText("Create Folder");
                        setIcon(IconProvider.ICON_CREATE_FOLDER);
                }
            }

            return;
        }
/*
        if (value instanceof String operation) {

            String normalized =
                    operation.trim();

            switch (normalized.toUpperCase()) {

                case "COPY" ->
                        setText("📄 Copy Group");

                case "MOVE" ->
                        setText("🚚 Move Group");

                case "DELETE" ->
                        setText("🗑 Delete Group");

                default ->
                        setText(normalized);
            }

            return;
        }
*/
        super.setValue(value);
    }


}