package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.ui.file.FileTypeIconProvider;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.util.S3Util;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class FileTableCellRenderer extends DefaultTableCellRenderer {

    private final Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");

    private final Icon fileIcon = UIManager.getIcon("FileView.fileIcon");

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        JLabel label = (JLabel) super.getTableCellRendererComponent(
                                table,
                                value,
                                isSelected,
                                hasFocus,
                                row,
                                column);

        if (value instanceof S3FileItem item) {
            label.setText(S3Util.extractFolderName(item.getKey()));
            label.setIcon(FileTypeIconProvider.getIcon(item));
        }

        return label;
    }
}