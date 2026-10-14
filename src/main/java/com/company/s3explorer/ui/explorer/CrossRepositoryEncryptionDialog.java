
package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.security.CrossRepositoryEncryptionOptions;
import com.company.s3explorer.security.EncryptionConfig;

import javax.swing.*;
import java.awt.*;

public final class CrossRepositoryEncryptionDialog {

    private CrossRepositoryEncryptionDialog() {
    }

    /**
     * @return Kullanıcının seçtiği seçenekler.
     *         Kullanıcı Cancel seçerse null döner.
     */
    public static CrossRepositoryEncryptionOptions showDialog(
            Component parent,
            RepositoryDefinition source,
            RepositoryDefinition target) {

        if (source == null || target == null) {
            throw new IllegalArgumentException(
                    "Source and target repositories are required.");
        }

        boolean sourceEncrypted =
                source.hasEncryptionConfiguration();

        boolean targetEncrypted =
                target.hasEncryptionConfiguration();

        if (!sourceEncrypted && !targetEncrypted) {
            return CrossRepositoryEncryptionOptions.none();
        }

        JCheckBox decryptCheckBox =
                new JCheckBox("Decrypt", sourceEncrypted);

        JCheckBox encryptCheckBox =
                new JCheckBox("Encrypt", targetEncrypted);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        panel.add(new JLabel(
                "Choose how encryption should be handled during transfer."));
        panel.add(Box.createVerticalStrut(12));

        if (sourceEncrypted) {
            decryptCheckBox.setToolTipText(
                    "Decrypt the source using its repository encryption settings.");
            panel.add(decryptCheckBox);
        }

        if (targetEncrypted) {
            encryptCheckBox.setToolTipText(
                    "Encrypt the destination using its repository encryption settings.");
            panel.add(encryptCheckBox);
        }

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Copy / Move Encryption",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        boolean decryptSource =
                sourceEncrypted && decryptCheckBox.isSelected();

        boolean encryptTarget =
                targetEncrypted && encryptCheckBox.isSelected();

        EncryptionConfig sourceConfig = decryptSource
                ? createEncryptionConfig(source)
                : null;

        EncryptionConfig targetConfig = encryptTarget
                ? createEncryptionConfig(target)
                : null;

        return new CrossRepositoryEncryptionOptions(
                decryptSource,
                encryptTarget,
                sourceConfig,
                targetConfig);
    }

    private static EncryptionConfig createEncryptionConfig(
            RepositoryDefinition repository) {

        return new EncryptionConfig(
                repository.getEncryptionTransformation(),
                repository.getEncryptionIv(),
                repository.getEncryptionKey());
    }
}
