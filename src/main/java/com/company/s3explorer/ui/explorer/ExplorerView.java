package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.transfer.renderer.FileSizeRenderer;
import com.company.s3explorer.transfer.renderer.InstantRenderer;
import com.company.s3explorer.transfer.renderer.PaddedTableCellRenderer;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.theme.UITheme;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.company.s3explorer.util.S3Util;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Swing view for the explorer. It owns explorer UI components and wiring
 * local to those components; business logic remains in ExplorerPanel.
 */
public final class ExplorerView {

    private static final Integer[] FILE_TABLE_ROW_LIMITS = {
            100, 250, 500, 1000, 2000, 5000, 10000, 20000,
            50000, 100000, 200000, 500000, 1000000, 10000000
    };

    private static final Integer[] THREAD_COUNTS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            15, 20, 25, 30, 40, 50, 60, 80, 100
    };

    private final Action downloadAction;
    private final Action downloadDecryptedAction;
    private final Action bulkDownloadAction;
    private final Action deleteAction;
    private final Action renameAction;
    private final Action copyAction;
    private final Action cutAction;
    private final Action pasteAction;
    private final Action copyTextAction;
    private final Action uploadAction;
    private final Action uploadEncryptedAction;
    private final Action newFolderAction;
    private final Action refreshAction;
    private final Action manageRepositoryAction;
    private final Action propertiesAction;
    private final Action backAction;
    private final Action forwardAction;
    private final Action reloadFolderAction;
    private final Action folderEditAction;
    
    private final Consumer<S3TreeNode> treeExpansionListener;
    private final Runnable openSelectedFileItem;
    private final Runnable reloadCurrentFileTable;
    private final Runnable updateActionStates;
    private final BooleanSupplier clipboardEmpty;
    private final Consumer<Integer> resizeExplorerPool;

    private JComboBox<UITheme> themeCombo;
    private JComboBox<RepositoryDefinition> repositoryCombo;
    private JLabel repositoryLabel;
    private JComboBox<String> bucketCombo;
    private JLabel bucketLabel;
    private JTree folderTree;
    private DefaultTreeModel treeModel;
    private JTable fileTable;
    private FileTableModel fileTableModel;
    private JComboBox<Integer> fileTableRowLimitCombo;
    private JComboBox<Integer> threadCountCombo;
    private JPanel breadcrumbPanel;
    private JPanel breadcrumbContainer;
    private JButton backNavigationButton;
    private JButton forwardNavigationButton;
    private JButton editLocationButton;
    private JTextField locationField;
    private boolean locationEditMode;
    private JLabel fileFolderInfo;
    private JPopupMenu filePopup;

    private JButton uploadEncryptedButton;
    private JButton downloadDecryptedButton;

    private JPanel breadcrumbHost;
    private JPanel locationEditorPanel;

    private Consumer<Integer> fileTableRowLimitSelectionListener;
    private Consumer<Integer> threadCountSelectionListener;
    private boolean suppressThreadCountSelectionEvent;

    private final BooleanSupplier encryptionConfigured;
    
    public ExplorerView(
            Action downloadAction,
            Action downloadDecryptedAction,
            Action bulkDownloadAction,
            Action deleteAction,
            Action copyAction,
            Action renameAction,
            Action propertiesAction,
            Action cutAction,
            Action pasteAction,
            Action copyTextAction,
            Action uploadAction,
            Action uploadEncryptedAction,
            Action newFolderAction,
            Action refreshAction,
            Action manageRepositoryAction,
            Action backAction,
            Action forwardAction,
            Action reloadFolderAction,
            Action folderEditAction,
            Consumer<S3TreeNode> treeExpansionListener,
            Runnable openSelectedFileItem,
            Runnable reloadCurrentFileTable,
            Runnable updateActionStates,
            BooleanSupplier clipboardEmpty,
            BooleanSupplier encryptionConfigured,
            Consumer<Integer> resizeExplorerPool) {

        this.downloadAction = downloadAction;
        this.downloadDecryptedAction = downloadDecryptedAction;
        this.bulkDownloadAction = bulkDownloadAction;
        this.deleteAction = deleteAction;
        this.copyAction = copyAction;
        this.renameAction = renameAction;
        this.propertiesAction = propertiesAction;
        this.cutAction = cutAction;
        this.pasteAction = pasteAction;
        this.copyTextAction = copyTextAction;
        this.uploadAction = uploadAction;
        this.uploadEncryptedAction = uploadEncryptedAction;
        this.newFolderAction = newFolderAction;
        this.refreshAction = refreshAction;
        this.manageRepositoryAction = manageRepositoryAction;
        this.backAction = backAction;
        this.forwardAction = forwardAction;
        this.reloadFolderAction = reloadFolderAction;
        this.folderEditAction = folderEditAction;
        this.treeExpansionListener = treeExpansionListener;
        this.openSelectedFileItem = openSelectedFileItem;
        this.reloadCurrentFileTable = reloadCurrentFileTable;
        this.updateActionStates = updateActionStates;
        this.clipboardEmpty = clipboardEmpty;
        this.encryptionConfigured = encryptionConfigured;
        this.resizeExplorerPool = resizeExplorerPool;
    }





    public JSplitPane createMainSplit() {
        S3TreeNode root = new S3TreeNode(
                S3TreeNode.ROOT_PREFIX,
                S3TreeNode.ROOT_PREFIX,
                S3TreeNode.ROOT_PREFIX);

        folderTree = new JTree(root);
        treeModel = (DefaultTreeModel) folderTree.getModel();
        updateFolderTreeRowHeight();

        folderTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                if (treeExpansionListener != null) {
                    treeExpansionListener.accept(
                            (S3TreeNode) event.getPath().getLastPathComponent());
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // no-op
            }
        });

        folderTree.setCellRenderer(new FolderTreeCellRenderer());
        folderTree.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setFolderTreeLeafIcon();

        fileTableModel = new FileTableModel();
        fileTable = createFileTable(fileTableModel);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(createTopBar(), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(folderTree), BorderLayout.CENTER);

        JScrollPane tableScrollPane = new JScrollPane(fileTable);
        installPopup(tableScrollPane.getViewport());
        installPopup(fileTable);
        installFileTableBlankAreaFocus(tableScrollPane);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(createIconButton(newFolderAction));
        buttonPanel.add(createIconButton(deleteAction));
        buttonPanel.add(createIconButton(renameAction));
        buttonPanel.add(createIconButton(propertiesAction));
        buttonPanel.add(createSeparator());

        buttonPanel.add(createIconButton(uploadAction));
        uploadEncryptedButton = createIconButton(uploadEncryptedAction);
        downloadDecryptedButton = createIconButton(downloadDecryptedAction);
        buttonPanel.add(uploadEncryptedButton);
        buttonPanel.add(createIconButton(downloadAction));
        buttonPanel.add(downloadDecryptedButton);
        buttonPanel.add(createIconButton(bulkDownloadAction));
        buttonPanel.add(createSeparator());

        buttonPanel.add(createIconButton(copyAction));
        buttonPanel.add(createIconButton(cutAction));
        buttonPanel.add(createIconButton(pasteAction));

        buttonPanel.add(createSeparator());
        buttonPanel.add(createIconButton(copyTextAction));

        JPanel themePanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 5, 5, 5);

        fileTableRowLimitCombo = new JComboBox<>(FILE_TABLE_ROW_LIMITS);
        fileTableRowLimitCombo.setSelectedItem(500);
        fileTableRowLimitCombo.setToolTipText("Max Item Count");
        fileTableRowLimitCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {

                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list,
                        value,
                        index,
                        isSelected,
                        cellHasFocus);

                if (value instanceof Integer number) {
                    label.setText(String.format("%,d", number));
                }

                label.setHorizontalAlignment(SwingConstants.RIGHT);
                return label;
            }
        });

        fileTableRowLimitCombo.addActionListener(e -> {
            Integer selected = (Integer) fileTableRowLimitCombo.getSelectedItem();
            if (selected != null && fileTableRowLimitSelectionListener != null) {
                fileTableRowLimitSelectionListener.accept(selected);
            }
        });
        themePanel.add(fileTableRowLimitCombo, c);

        threadCountCombo = new JComboBox<>(THREAD_COUNTS);
        threadCountCombo.setSelectedItem(15);
        threadCountCombo.setToolTipText("Thread Count");
        alignComboBoxRight(threadCountCombo);

        threadCountCombo.addActionListener(e -> {
            if (suppressThreadCountSelectionEvent) {
                return;
            }

            Integer selected = (Integer) threadCountCombo.getSelectedItem();
            if (selected == null) {
                return;
            }

            if (resizeExplorerPool != null) {
                resizeExplorerPool.accept(selected);
            }

            if (threadCountSelectionListener != null) {
                threadCountSelectionListener.accept(selected);
            }
        });
        themePanel.add(threadCountCombo, c);

        themeCombo = new JComboBox<>() {
            @Override
            public void setSelectedIndex(int index) {
                UITheme theme = this.getItemAt(index);
                if (theme.isDisabled()) {
                    return;
                }
                super.setSelectedIndex(index);
            }
        };

        themeCombo.setToolTipText("Theme");
        UIThemeManager.getThemes().forEach(themeCombo::addItem);

        c.insets = new Insets(10, 5, 5, 10);
        themePanel.add(themeCombo, c);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(buttonPanel, BorderLayout.WEST);
        topPanel.add(themePanel, BorderLayout.EAST);

        JPanel topButtonPanel = new JPanel(new BorderLayout());
        topButtonPanel.add(topPanel, BorderLayout.NORTH);

        // Breadcrumb components
        breadcrumbPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 0, 0));

        // Vertically center breadcrumb items while keeping them left-aligned.
        breadcrumbHost = new JPanel(new GridBagLayout());
        breadcrumbHost.setOpaque(false);

        GridBagConstraints breadcrumbConstraints = new GridBagConstraints();
        breadcrumbConstraints.gridx = 0;
        breadcrumbConstraints.gridy = 0;
        breadcrumbConstraints.anchor = GridBagConstraints.WEST;
        breadcrumbConstraints.weightx = 1.0;
        breadcrumbConstraints.weighty = 1.0;
        breadcrumbConstraints.fill = GridBagConstraints.NONE;

        breadcrumbHost.add(breadcrumbPanel, breadcrumbConstraints);

        breadcrumbContainer = new JPanel(new BorderLayout(4, 0));

        backNavigationButton = createIconButton(backAction);
        forwardNavigationButton = createIconButton(forwardAction);
        editLocationButton = createIconButton(folderEditAction);

        // Vertically center the navigation buttons without changing their sizes.
        JPanel navigationButtons = new JPanel(new GridBagLayout());
        navigationButtons.setOpaque(false);

        GridBagConstraints navigationConstraints = new GridBagConstraints();
        navigationConstraints.gridy = 0;
        navigationConstraints.anchor = GridBagConstraints.WEST;
        navigationConstraints.fill = GridBagConstraints.NONE;
        navigationConstraints.weighty = 1.0;
        navigationConstraints.insets = new Insets(0, 3, 0, 2);

        navigationConstraints.gridx = 0;
        navigationButtons.add(backNavigationButton, navigationConstraints);

        navigationConstraints.gridx = 1;
        navigationButtons.add(forwardNavigationButton, navigationConstraints);

        navigationConstraints.gridx = 2;
        navigationButtons.add(createIconButton(reloadFolderAction), navigationConstraints);

        navigationConstraints.gridx = 3;
        navigationButtons.add(createSeparator(), navigationConstraints);

        navigationConstraints.gridx = 4;
        navigationButtons.add(editLocationButton, navigationConstraints);

        navigationConstraints.gridx = 5;
        navigationButtons.add(createSeparator(), navigationConstraints);

        // Folder path editor
        locationField = new JTextField();
        locationField.setToolTipText("Enter folder path");
        locationField.setMargin(new Insets(2, 7, 2, 7));
        locationField.setPreferredSize(new Dimension(360, 26));
        locationField.setMinimumSize(new Dimension(180, 26));
        locationField.setMaximumSize(new Dimension(480, 26));
        locationField.setVisible(false);


        locationEditorPanel = new JPanel(new GridBagLayout());
        locationEditorPanel.setOpaque(false);

        GridBagConstraints locationConstraints = new GridBagConstraints();
        locationConstraints.gridx = 0;
        locationConstraints.gridy = 0;
        locationConstraints.anchor = GridBagConstraints.WEST;
        locationConstraints.fill = GridBagConstraints.NONE;
        locationConstraints.weightx = 0;
        locationConstraints.weighty = 1;
        locationConstraints.insets = new Insets(0, 4, 0, 0);

        locationEditorPanel.add(locationField, locationConstraints);

        breadcrumbContainer.add(
                navigationButtons,
                BorderLayout.WEST);

        breadcrumbContainer.add(
                breadcrumbHost,
                BorderLayout.CENTER);

        // Preserve the bottom panel and file information label.
        JPanel bottomButtonPanel = new JPanel(new BorderLayout());
        bottomButtonPanel.setPreferredSize(new Dimension(0, 40));
        bottomButtonPanel.setBorder(
                BorderFactory.createBevelBorder(BevelBorder.RAISED));

        bottomButtonPanel.add(
                breadcrumbContainer,
                BorderLayout.CENTER);


        fileFolderInfo = new JLabel("");

        JPanel fileFolderInfoPanel = new JPanel(new GridBagLayout());
        fileFolderInfoPanel.setOpaque(false);

        GridBagConstraints fileFolderInfoConstraints = new GridBagConstraints();
        fileFolderInfoConstraints.gridx = 0;
        fileFolderInfoConstraints.gridy = 0;
        fileFolderInfoConstraints.anchor = GridBagConstraints.CENTER;
        fileFolderInfoConstraints.weightx = 1.0;
        fileFolderInfoConstraints.weighty = 1.0;

        fileFolderInfoPanel.add(fileFolderInfo, fileFolderInfoConstraints);

        bottomButtonPanel.add(
                fileFolderInfoPanel,
                BorderLayout.EAST);

        topButtonPanel.add(
                bottomButtonPanel,
                BorderLayout.SOUTH);

        JPanel fileTablePanel = new JPanel(new BorderLayout());
        fileTablePanel.add(topButtonPanel, BorderLayout.NORTH);
        fileTablePanel.add(tableScrollPane, BorderLayout.CENTER);

        createPopupMenu();

        return new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                fileTablePanel);
    }

    private JPanel createTopBar() {
        JPanel container = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 10, 5, 10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.2;
        repositoryLabel = new JLabel("Repository", SwingConstants.LEFT);
        repositoryLabel.setIconTextGap(14);
        container.add(repositoryLabel, gbc);

        repositoryCombo = new JComboBox<>();
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        container.add(repositoryCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.2;
        container.add(createIconButton(manageRepositoryAction), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        bucketLabel = new JLabel("Bucket", SwingConstants.LEFT);
        bucketLabel.setIconTextGap(10);
        container.add(bucketLabel, gbc);

        bucketCombo = new JComboBox<>();
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        container.add(bucketCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.2;
        container.add(createIconButton(refreshAction), gbc);

        return container;
    }


    private JButton createIconButton(Action action) {
        JButton button = new JButton(action);
        button.setHideActionText(true);

        String actionName = String.valueOf(
                action.getValue(Action.NAME));

        String shortcut = switch (actionName) {
            case "Back" -> "Alt + Left";
            case "Forward" -> "Alt + Right";
            case "Copy" -> "Ctrl + C";
            case "Cut" -> "Ctrl + X";
            case "Paste" -> "Ctrl + V";
            case "Delete" -> "Delete";
            case "Rename" -> "F2";
            case "Properties" -> "Alt + Enter";
            default -> null;
        };

        if (shortcut != null) {
            button.setToolTipText(
                    actionName + " (" + shortcut + ")");
        } else {
            button.setToolTipText(actionName);
        }

        return button;
    }

    private JPanel createSeparator() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.setPreferredSize(new Dimension(20, 25));
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        wrapper.add(sep, gbc);
        return wrapper;
    }

    private JTable createFileTable(FileTableModel model) {
        JTable table = new JTable(model);
        table.setDefaultRenderer(
                Object.class,
                new PaddedTableCellRenderer());
        TableColumn hidden = table.getColumnModel().getColumn(0);
        hidden.setMinWidth(0);
        hidden.setMaxWidth(0);
        hidden.setPreferredWidth(0);
        hidden.setResizable(false);
        table.getColumnModel().getColumn(FileTableModel.COL_NAME)
                .setCellRenderer(new FileTableCellRenderer());
        table.getColumnModel().getColumn(FileTableModel.COL_SIZE)
                .setCellRenderer(new FileSizeRenderer());
        table.getColumnModel().getColumn(FileTableModel.COL_LAST_MODIFIED)
                .setCellRenderer(new InstantRenderer());
        updateFileTableRowHeight(table);
        
        FileTableRowSorter sorter = new FileTableRowSorter(model);
        table.setRowSorter(sorter);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewColumn = table.columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    return;
                }
                int modelColumn = table.convertColumnIndexToModel(viewColumn);
                if (modelColumn == FileTableModel.COL_FOLDER) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (reloadCurrentFileTable != null) {
                        reloadCurrentFileTable.run();
                    }
                });
            }
        });

        final javax.swing.table.TableCellRenderer originalRenderer =
                table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer(
                (table1, value, isSelected, hasFocus, row, column) -> {
                    Component component = originalRenderer
                            .getTableCellRendererComponent(
                                    table1, value, isSelected, hasFocus, row, column);
                    if (component instanceof JLabel label) {
                        label.setIcon(null);
                        if (table1.getRowSorter() != null) {
                            List<? extends RowSorter.SortKey> sortKeys =
                                    table1.getRowSorter().getSortKeys();
                            for (RowSorter.SortKey key : sortKeys) {
                                if (key.getColumn() == column && column != 0) {
                                    if (key.getSortOrder() == SortOrder.ASCENDING) {
                                        label.setIcon(UIManager.getIcon("Table.ascendingSortIcon"));
                                    } else if (key.getSortOrder() == SortOrder.DESCENDING) {
                                        label.setIcon(UIManager.getIcon("Table.descendingSortIcon"));
                                    }
                                }
                            }
                        }
                    }
                    return component;
                });

        table.getRowSorter().toggleSortOrder(1);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && openSelectedFileItem != null) {
                    openSelectedFileItem.run();
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && updateActionStates != null) {
                updateActionStates.run();
            }
        });
        return table;
    }

    private void updateFileTableRowHeight(JTable table) {
        if (table == null) {
            return;
        }

        FontMetrics fontMetrics = table.getFontMetrics(table.getFont());
        int rowHeight = fontMetrics.getHeight() + 8;

        table.setRowHeight(rowHeight);
    }
    
    private void installPopup(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }
        });
    }

    private void showPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), fileTable);
        int row = fileTable.rowAtPoint(p);
        if (row < 0) {
            fileTable.clearSelection();
        } else if (!fileTable.isRowSelected(row)) {
            fileTable.setRowSelectionInterval(row, row);
        }
        if (updateActionStates != null) {
            updateActionStates.run();
        }
        filePopup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void createPopupMenu() {
        filePopup = new JPopupMenu();
        JMenuItem downloadMenu = new JMenuItem(downloadAction);
        JMenuItem downloadDecryptedMenu = new JMenuItem(downloadDecryptedAction);
        JMenuItem deleteMenu = new JMenuItem(deleteAction);
        JMenuItem renameMenu = new JMenuItem(renameAction);
        JMenuItem propertiesMenu = new JMenuItem(propertiesAction);
        JMenuItem copyMenu = new JMenuItem(copyAction);
        JMenuItem cutMenu = new JMenuItem(cutAction);
        JMenuItem pasteMenu = new JMenuItem(pasteAction);
        JMenuItem copyTextMenu = new JMenuItem(copyTextAction);

        filePopup.add(propertiesMenu);
        filePopup.addSeparator();

        filePopup.add(deleteMenu);
        filePopup.add(renameMenu);
        filePopup.addSeparator();

        filePopup.add(downloadMenu);
        filePopup.add(downloadDecryptedMenu);
        filePopup.addSeparator();
        
        filePopup.add(copyMenu);
        filePopup.add(cutMenu);
        filePopup.add(pasteMenu);

        filePopup.addSeparator();
        filePopup.add(copyTextMenu);
        
        filePopup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                downloadDecryptedMenu.setVisible(
                        isEncryptionConfigured());
                
                pasteMenu.setEnabled(clipboardEmpty == null || !clipboardEmpty.getAsBoolean());
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    private boolean isEncryptionConfigured() {
        return encryptionConfigured != null
                && encryptionConfigured.getAsBoolean();
    }
    
    public void setFolderTreeLeafIcon() {
        DefaultTreeCellRenderer renderer =
                (DefaultTreeCellRenderer) folderTree.getCellRenderer();
        renderer.setLeafIcon(renderer.getClosedIcon());
    }

    public void setButtonIcons() {
        repositoryLabel.setIcon(IconProvider.ICON_REPOSITORY);
        bucketLabel.setIcon(IconProvider.ICON_BUCKET);
        S3Util.setActionIcon(manageRepositoryAction, IconProvider.ICON_SETTINGS);
        S3Util.setActionIcon(refreshAction, IconProvider.ICON_REFRESH);
        S3Util.setActionIcon(uploadAction, IconProvider.ICON_UPLOAD);
        S3Util.setActionIcon(uploadEncryptedAction, IconProvider.ICON_UPLOAD_ENCRYPTED);
        S3Util.setActionIcon(newFolderAction, IconProvider.ICON_CREATE_FOLDER);
        S3Util.setActionIcon(downloadAction, IconProvider.ICON_DOWNLOAD);
        S3Util.setActionIcon(downloadDecryptedAction, IconProvider.ICON_DOWNLOAD_DECRYPTED);
        S3Util.setActionIcon(deleteAction, IconProvider.ICON_DELETE);
        S3Util.setActionIcon(renameAction, IconProvider.ICON_RENAME);
        S3Util.setActionIcon(propertiesAction, IconProvider.ICON_PROPERTIES);
        S3Util.setActionIcon(copyAction, IconProvider.ICON_COPY);
        S3Util.setActionIcon(cutAction, IconProvider.ICON_CUT);
        S3Util.setActionIcon(pasteAction, IconProvider.ICON_PASTE);
        S3Util.setActionIcon(copyTextAction, IconProvider.ICON_COPY_TEXT);
        S3Util.setActionIcon(bulkDownloadAction, IconProvider.ICON_BULK_DOWNLOAD);
        S3Util.setActionIcon(copyTextAction, IconProvider.ICON_COPY_TEXT);
        S3Util.setActionIcon(backAction, IconProvider.ICON_BACK);
        S3Util.setActionIcon(forwardAction, IconProvider.ICON_FORWARD);
        S3Util.setActionIcon(reloadFolderAction, IconProvider.ICON_FOLDER_REFRESH);
        S3Util.setActionIcon(folderEditAction, IconProvider.ICON_FOLDER_EDIT);
    }

    public JTree getFolderTree() { return folderTree; }
    public DefaultTreeModel getTreeModel() { return treeModel; }
    public JTable getFileTable() { return fileTable; }
    public FileTableModel getFileTableModel() { return fileTableModel; }
    public JComboBox<RepositoryDefinition> getRepositoryCombo() { return repositoryCombo; }
    public JComboBox<String> getBucketCombo() { return bucketCombo; }
    public JComboBox<UITheme> getThemeCombo() { return themeCombo; }
    public JComboBox<Integer> getFileTableRowLimitCombo() { return fileTableRowLimitCombo; }
    public JPanel getBreadcrumbPanel() { return breadcrumbPanel; }
    public JButton getBackNavigationButton() {
        return backNavigationButton;
    }

    public JButton getForwardNavigationButton() {
        return forwardNavigationButton;
    }

    public JButton getEditLocationButton() {
        return editLocationButton;
    }

    public JTextField getLocationField() {
        return locationField;
    }



    public void setLocationEditMode(boolean editMode) {
        locationEditMode = editMode;

        if (editMode) {
            breadcrumbContainer.remove(breadcrumbHost);

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            constraints.weighty = 1.0;
            constraints.insets = new Insets(0, 4, 0, 4);

            locationEditorPanel.removeAll();
            locationEditorPanel.add(locationField, constraints);

            breadcrumbContainer.add(
                    locationEditorPanel,
                    BorderLayout.CENTER);

            locationField.setMinimumSize(
                    new Dimension(0, 26));

            locationField.setPreferredSize(
                    new Dimension(360, 26));

            locationField.setMaximumSize(
                    new Dimension(Integer.MAX_VALUE, 26));

            locationField.setVisible(true);
            locationField.requestFocusInWindow();
            locationField.selectAll();

        } else {
            breadcrumbContainer.remove(locationEditorPanel);

            // Breadcrumb görünmeden önce mevcut Look & Feel'i uygula.
            SwingUtilities.updateComponentTreeUI(breadcrumbHost);

            breadcrumbContainer.add(
                    breadcrumbHost,
                    BorderLayout.CENTER);
        }

        breadcrumbContainer.revalidate();
        breadcrumbContainer.repaint();
    }

    public boolean isLocationEditMode() {
        return locationEditMode;
    }

    public void setNavigationButtonStates(
            boolean canGoBack,
            boolean canGoForward) {

        backNavigationButton.setEnabled(canGoBack);
        forwardNavigationButton.setEnabled(canGoForward);
    }

    public JLabel getFileFolderInfo() { return fileFolderInfo; }

    public void setFileTableRowLimitSelectionListener(Consumer<Integer> listener) {
        this.fileTableRowLimitSelectionListener = listener;
    }

    public void setThreadCountSelectionListener(Consumer<Integer> listener) {
        this.threadCountSelectionListener = listener;
    }

    public void selectThreadCount(int threadCount) {
        if (threadCount <= 0 || threadCountCombo == null) {
            return;
        }
        suppressThreadCountSelectionEvent = true;
        try {
            threadCountCombo.setSelectedItem(threadCount);
        } finally {
            suppressThreadCountSelectionEvent = false;
        }
        if (resizeExplorerPool != null) {
            resizeExplorerPool.accept(threadCount);
        }
    }

    private static void alignComboBoxRight(JComboBox<?> comboBox) {
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setHorizontalAlignment(SwingConstants.RIGHT);
                return label;
            }
        });
    }

    public void updateEncryptionActionVisibility() {
        boolean configured =
                isEncryptionConfigured();

        uploadEncryptedButton.setVisible(
                configured);

        downloadDecryptedButton.setVisible(
                configured);
    }

    public void updateFileTableRowHeight() {
        updateFileTableRowHeight(fileTable);
    }

    public void updateFolderTreeRowHeight() {
        updateFolderTreeRowHeightInternal();
    }
    
    private void updateFolderTreeRowHeightInternal() {
        if (folderTree == null) {
            return;
        }

        FontMetrics fm =
                folderTree.getFontMetrics(folderTree.getFont());

        int rowHeight =
                fm.getHeight() + 10;

        folderTree.setRowHeight(rowHeight);
    }

    public void updatePopupUI() {
        if (filePopup == null) {
            return;
        }

        SwingUtilities.updateComponentTreeUI(filePopup);
        filePopup.revalidate();
        filePopup.repaint();
    }

    private void installFileTableBlankAreaFocus(JScrollPane tableScrollPane) {
        JViewport tableViewport = tableScrollPane.getViewport();
        tableViewport.setFocusable(true);

        tableViewport.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point point = SwingUtilities.convertPoint(
                        tableViewport,
                        e.getPoint(),
                        fileTable);

                if (fileTable.rowAtPoint(point) < 0) {
                    SwingUtilities.invokeLater(
                            tableViewport::requestFocusInWindow);
                }
            }
        });
    }
}