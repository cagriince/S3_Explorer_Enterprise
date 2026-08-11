package com.company.s3explorer.ui.action;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ExplorerAction extends AbstractAction {

    private final Runnable action;

    public ExplorerAction(String name, Runnable action) {
        this(name, null, action);
    }

    public ExplorerAction(String name, Icon icon, Runnable action) {
        super(name, icon);
        this.action = action;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        action.run();
    }
}
