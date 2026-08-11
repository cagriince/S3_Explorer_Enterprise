package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class FolderTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value == tree.getModel().getRoot()) {
            setIcon(IconProvider.ICON_SYSTEM_CLOSED_FOLDER);
        }

        return this;
    }
}