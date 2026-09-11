package com.company.s3explorer.ui.explorer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class BulkDownloadDialog {

    private final JDialog dialog;
    private final JTextArea objectKeysArea;
    private final JButton downloadButton;
    private final JButton downloadDecryptedButton;

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

        objectKeysArea = new JTextArea(20, 70);
        objectKeysArea.setLineWrap(false);
        objectKeysArea.setWrapStyleWord(false);

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
                e -> dialog.dispose());

        downloadDecryptedButton.addActionListener(
                e -> dialog.dispose());

        cancelButton.addActionListener(
                e -> dialog.dispose());

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
}