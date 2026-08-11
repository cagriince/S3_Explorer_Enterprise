package com.company.s3explorer.transfer.renderer;

import com.company.s3explorer.transfer.TransferStatus;

import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class StatusRenderer extends DefaultTableCellRenderer {

    @Override
    protected void setValue(Object value) {

        if (!(value instanceof TransferStatus status)) {
            super.setValue(value);
            return;
        }

        setHorizontalAlignment(CENTER);
        setOpaque(true);

        switch (status) {
            case QUEUED -> {
                setText("🟡 Queued");
                setForeground(new Color(180,120,0));
            }

            case RUNNING -> {
                setText("🔵 Running");
                setForeground(new Color(0,90,200));
            }

            case COMPLETED -> {
                setText("🟢 Completed");
                setForeground(new Color(0,140,60));
            }

            case FAILED -> {
                setText("🔴 Failed");
                setForeground(Color.RED);
            }

            case CANCELLED -> {
                setText("⚫ Cancelled");
                setForeground(Color.GRAY);
            }
        }
    }
}