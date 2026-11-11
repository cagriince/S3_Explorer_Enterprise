
package com.company.s3explorer.ui.explorer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ExplorerOperationDialogManager {

    private static final int SHOW_DELAY_MS = 250;

    public enum Type {
        BUCKET,
        FILE_TABLE,
        FILTER_REFRESH
    }

    private final Component parent;

    private final Map<Type, OperationDialog> dialogs =
            new EnumMap<>(Type.class);

    private final List<OperationDialog> visibleDialogs =
            new ArrayList<>();

    public ExplorerOperationDialogManager(Component parent) {
        this.parent = parent;
    }

    public boolean isVisible(Type type) {
        OperationDialog operationDialog =
                dialogs.get(type);

        return operationDialog != null
                && visibleDialogs.contains(operationDialog);
    }


    public void show(Type type, String message) {

        SwingUtilities.invokeLater(() -> {

            OperationDialog operationDialog =
                    getOrCreateDialog(type);

            operationDialog.message.setText(message);
            operationDialog.dialog.pack();

            /*
             * Dialog zaten görünüyorsa yalnızca mesajı güncelle.
             * Dialogu tekrar gizleme.
             */
            if (operationDialog.dialog.isVisible()) {

                positionDialogs();
                return;
            }

            /*
             * Dialog gösterilmek üzere bekliyorsa
             * mevcut Timer'ı yeniden başlatma.
             */
            if (operationDialog.showTimer != null
                    && operationDialog.showTimer.isRunning()) {

                return;
            }

            if (!visibleDialogs.contains(operationDialog)) {
                visibleDialogs.add(operationDialog);
            }

            Timer timer = new Timer(
                    SHOW_DELAY_MS,
                    e -> {

                        Timer completedTimer =
                                (Timer) e.getSource();

                        completedTimer.stop();

                        if (operationDialog.showTimer
                                == completedTimer) {

                            operationDialog.showTimer = null;
                        }

                        if (!visibleDialogs.contains(
                                operationDialog)) {

                            return;
                        }

                        operationDialog.dialog.setVisible(true);

                        positionDialogs();
                    });

            timer.setRepeats(false);

            operationDialog.showTimer = timer;

            timer.start();
        });
    }

    public void hide(Type type) {

        SwingUtilities.invokeLater(() -> {

            OperationDialog operationDialog =
                    dialogs.get(type);

            if (operationDialog == null) {
                return;
            }

            if (operationDialog.showTimer != null) {
                operationDialog.showTimer.stop();
                operationDialog.showTimer = null;
            }

            operationDialog.dialog.setVisible(false);

            visibleDialogs.remove(operationDialog);

            positionDialogs();
        });
    }

    /**
     * Updates an already-created dialog without creating a new one.
     */
    public void updateMessage(
            Type type,
            String message) {

        OperationDialog operationDialog =
                dialogs.get(type);

        if (operationDialog == null) {
            return;
        }

        operationDialog.message.setText(message);
        operationDialog.dialog.pack();

        positionDialogs();
    }

    private OperationDialog getOrCreateDialog(Type type) {

        OperationDialog existing =
                dialogs.get(type);

        if (existing != null) {
            return existing;
        }

        String title;
        Dimension minimumSize;

        switch (type) {

            case BUCKET:
                title = "Bucket Loading";
                minimumSize = new Dimension(450, 150);
                break;

            case FILE_TABLE:
                title = "File Table";
                minimumSize = new Dimension(450, 150);
                break;

            case FILTER_REFRESH:
                title = "Updating File Table";
                minimumSize = new Dimension(320, 95);
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported operation dialog type: " + type);
        }

        OperationDialog created =
                createDialog(type, title, minimumSize);

        dialogs.put(type, created);

        return created;
    }

    private OperationDialog createDialog(
            Type type,
            String title,
            Dimension minimumSize) {

        Window owner =
                SwingUtilities.getWindowAncestor(parent);

        JDialog dialog =
                new JDialog(
                        owner,
                        title,
                        Dialog.ModalityType.MODELESS);

        dialog.setDefaultCloseOperation(
                WindowConstants.DO_NOTHING_ON_CLOSE);

        dialog.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        hide(type);
                    }
                });

        dialog.setResizable(false);

        JPanel panel =
                new JPanel(
                        new BorderLayout(15, 15));

        panel.setBorder(
                new EmptyBorder(
                        14,
                        18,
                        14,
                        18));

        JLabel message =
                new JLabel("Preparing file table...");

        panel.add(
                message,
                BorderLayout.CENTER);

        JButton hideButton =
                new JButton("Hide");

        hideButton.addActionListener(
                e -> hide(type));

        dialog.getRootPane()
                .getInputMap(
                        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_ESCAPE,
                                0),
                        "hideOperationDialog");

        dialog.getRootPane()
                .getActionMap()
                .put(
                        "hideOperationDialog",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                hide(type);
                            }
                        });

        JPanel buttonPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                0,
                                0));

        buttonPanel.add(hideButton);

        panel.add(
                buttonPanel,
                BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setMinimumSize(minimumSize);

        return new OperationDialog(dialog, message);
    }

    private void positionDialogs() {

        Window owner =
                SwingUtilities.getWindowAncestor(parent);

        if (owner == null) {
            return;
        }

        int centerX =
                owner.getX()
                        + owner.getWidth() / 2;

        int currentY =
                owner.getY()
                        + owner.getHeight() * 30 / 100;

        int gap = 12;

        for (OperationDialog operationDialog : visibleDialogs) {

            if (operationDialog == null
                    || !operationDialog.dialog.isVisible()) {
                continue;
            }

            int x =
                    centerX
                            - operationDialog.dialog.getWidth() / 2;

            operationDialog.dialog.setLocation(x, currentY);

            currentY +=
                    operationDialog.dialog.getHeight() + gap;
        }
    }

    private static final class OperationDialog {

        private final JDialog dialog;
        private final JLabel message;

        private Timer showTimer;

        private OperationDialog(
                JDialog dialog,
                JLabel message) {

            this.dialog = dialog;
            this.message = message;
        }
    }
}
