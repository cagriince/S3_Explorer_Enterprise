package com.company.s3explorer.ui.explorer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public final class BulkDownloadDialog {

    private final JDialog dialog;
    private final JTextArea objectKeysArea;
    private final JButton downloadButton;
    private final JButton downloadDecryptedButton;
    private static String lastUsedObjectKeys = "";
    
    public enum Result {
        CANCEL,
        DOWNLOAD,
        DOWNLOAD_DECRYPTED
    }
    private Result result = Result.CANCEL;
    
    public BulkDownloadDialog(
            Window owner,
            String repositoryName,
            String bucket,
            boolean encryptionConfigured) {

        dialog =
                new JDialog(
                        owner,
                        "Bulk Download",
                        Dialog.ModalityType.APPLICATION_MODAL);

        dialog.getRootPane().registerKeyboardAction(
                e -> {
                    result = Result.CANCEL;
                    dialog.dispose();
                },
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        objectKeysArea = new JTextArea(15, 60);
        objectKeysArea.setLineWrap(false);
        objectKeysArea.setWrapStyleWord(false);
        objectKeysArea.setFont(
                objectKeysArea.getFont().deriveFont(
                        objectKeysArea.getFont().getSize2D() + 1f));
        objectKeysArea.setText(lastUsedObjectKeys);
        
        JScrollPane scrollPane =
                new JScrollPane(objectKeysArea);

        JLabel informationLabel =
                new JLabel(
                        "<html>"
                                + "Enter S3 object keys, one per line. All keys must belong to the current repository and bucket."
                                + "<br><br>"
                                + "<table><tr><td><b>Repository:</b></td><td>"
                                + repositoryName
                                + "</td></tr><tr><td><b>Bucket:</b></td><td>"
                                + bucket
                                + "</td></tr></table></html>");

        downloadButton =
                new JButton("Download");

        downloadDecryptedButton =
                new JButton("Download Decrypted");

        downloadDecryptedButton.setVisible(
                encryptionConfigured);

        JButton cancelButton =
                new JButton("Cancel");

        downloadButton.addActionListener(
                e -> {
                    lastUsedObjectKeys =
                            objectKeysArea.getText();

                    result = Result.DOWNLOAD;
                    dialog.dispose();
                });

        downloadDecryptedButton.addActionListener(
                e -> {
                    lastUsedObjectKeys =
                            objectKeysArea.getText();

                    result = Result.DOWNLOAD_DECRYPTED;
                    dialog.dispose();
                });

        cancelButton.addActionListener(
                e -> {
                    result = Result.CANCEL;
                    dialog.dispose();
                });

        JPanel buttonPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT));

        buttonPanel.add(downloadButton);
        buttonPanel.add(downloadDecryptedButton);
        buttonPanel.add(cancelButton);

        JPanel contentPanel =
                new JPanel(
                        new BorderLayout(10, 10));

        contentPanel.setBorder(
                new EmptyBorder(
                        12,
                        12,
                        12,
                        12));

        contentPanel.add(
                informationLabel,
                BorderLayout.NORTH);

        contentPanel.add(
                scrollPane,
                BorderLayout.CENTER);

        contentPanel.add(
                buttonPanel,
                BorderLayout.SOUTH);

        dialog.setContentPane(contentPanel);
        dialog.setMinimumSize(
                new Dimension(700, 450));
        dialog.setSize(
                new Dimension(800, 550));
        dialog.setLocationRelativeTo(owner);
    }
    
    public void setVisible(boolean visible) {
        dialog.setVisible(visible);
    }

    public Result getResult() {
        return result;
    }

    public List<String> getObjectKeys() {
        return Arrays.stream(
                        objectKeysArea.getText().split("\\R"))
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .toList();
    }
}