package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferType;

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

                case UPLOAD ->
                        setText("⬆ Upload");

                case DOWNLOAD ->
                        setText("⬇ Download");

                case DELETE ->
                        setText("🗑 Delete");

                case COPY ->
                        setText("📄 Copy");

                case MOVE ->
                        setText("🚚 Move");

                case CREATE_FOLDER ->
                        setText("📁 Create Folder");
            }

            return;
        }

        if (value instanceof String operation) {

            String normalized =
                    operation.trim();

            switch (normalized.toUpperCase()) {

                case "COPY" ->
                        setText("📄 Copy");

                case "MOVE" ->
                        setText("🚚 Move");

                case "DELETE" ->
                        setText("🗑 Delete");

                default ->
                        setText(normalized);
            }

            return;
        }

        super.setValue(value);
    }


}