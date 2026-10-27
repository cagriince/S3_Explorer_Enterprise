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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.List;

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
    private final Action propertiesAction;
    private final Action backAction;
    private final Action forwardAction;
    private final Action reloadFolderAction;
    private final Action flatBrowsingAction;
    private final Action folderEditAction;
    private final Action exportFileListAction;

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
    private JToggleButton flatBrowsingButton;
    private JToggleButton editLocationButton;
    private JTextField locationField;
    private boolean locationEditMode;
    private JTextField filterTextField;
    private JComboBox<String> itemTypeCombo;
    private JLabel fileFolderInfo;
    private JLabel fileSelectionInfo;
    private JSeparator fileSelectionSeparator;
    private JPopupMenu filePopup;

    private JButton uploadEncryptedButton;
    private JButton downloadDecryptedButton;

    private JPanel breadcrumbHost;
    private JPanel locationEditorPanel;

    private Consumer<Integer> fileTableRowLimitSelectionListener;
    private Consumer<Integer> threadCountSelectionListener;
    private boolean suppressThreadCountSelectionEvent;

    private final BooleanSupplier encryptionConfigured;
    private Runnable serverFilterApplyListener;

    private Timer fileTableSortReloadTimer;

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
            Action backAction,
            Action forwardAction,
            Action reloadFolderAction,
            Action flatBrowsingAction,
            Action folderEditAction,
            Action exportFileListAction,
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
        this.backAction = backAction;
        this.forwardAction = forwardAction;
        this.reloadFolderAction = reloadFolderAction;
        this.flatBrowsingAction = flatBrowsingAction;
        this.folderEditAction = folderEditAction;
        this.exportFileListAction = exportFileListAction;
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
        flatBrowsingButton = createToggleIconButton(flatBrowsingAction);
        editLocationButton = createToggleIconButton(folderEditAction);

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
        navigationButtons.add(flatBrowsingButton, navigationConstraints);

        navigationConstraints.gridx = 5;
        navigationButtons.add(editLocationButton, navigationConstraints);

        navigationConstraints.gridx = 6;
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

        breadcrumbContainer.add(
                createBreadcrumbFilterControls(),
                BorderLayout.EAST);

        // Keep the breadcrumb row directly below the toolbar.
        JPanel breadcrumbRow = new JPanel(new BorderLayout());
        breadcrumbRow.setPreferredSize(new Dimension(0, 40));
        breadcrumbRow.setBorder(
                BorderFactory.createBevelBorder(BevelBorder.RAISED));
        breadcrumbRow.add(breadcrumbContainer, BorderLayout.CENTER);

        topButtonPanel.add(
                breadcrumbRow,
                BorderLayout.CENTER);

        // Preserve the bottom panel and file information label.
        JPanel fileTablePanel = new JPanel(new BorderLayout());

        fileTablePanel.add(
                topButtonPanel,
                BorderLayout.NORTH);

        fileTablePanel.add(
                tableScrollPane,
                BorderLayout.CENTER);

        fileTablePanel.add(
                createFileTableFooter(),
                BorderLayout.SOUTH);

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

        // Repository label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.2;

        repositoryLabel = new JLabel("Repository", SwingConstants.LEFT);
        repositoryLabel.setIconTextGap(14);
        container.add(repositoryLabel, gbc);

        // Repository ComboBox
        repositoryCombo = new JComboBox<>();

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0.8;
        container.add(repositoryCombo, gbc);

        // Bucket label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.2;

        bucketLabel = new JLabel("Bucket", SwingConstants.LEFT);
        bucketLabel.setIconTextGap(10);
        container.add(bucketLabel, gbc);

        // Bucket ComboBox - no refresh button
        bucketCombo = new JComboBox<>();

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0.8;
        container.add(bucketCombo, gbc);

        return container;
    }

    private JButton createIconButton(Action action) {
        return (JButton) createIconButton(action, false);
    }

    private JToggleButton createToggleIconButton(Action action) {
        return (JToggleButton) createIconButton(action, true);
    }

    private AbstractButton createIconButton(Action action, boolean toogle) {
        AbstractButton button = toogle ? new JToggleButton(action): new JButton(action);
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

        sorter.addRowSorterListener(e -> {
            if (e.getType()
                    == javax.swing.event.RowSorterEvent.Type.SORTED) {
                System.out.printf(
                        "[SORT TIMING] sorter event completed: rows=%d, time=%s%n",
                        table.getRowCount(),
                        java.time.LocalTime.now());
            }
        });

        table.getTableHeader().setReorderingAllowed(false);

        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewColumn = table.columnAtPoint(e.getPoint());

                if (viewColumn < 0) {
                    return;
                }

                int modelColumn =
                        table.convertColumnIndexToModel(viewColumn);

                if (modelColumn == FileTableModel.COL_FOLDER) {
                    return;
                }

                if (fileTableSortReloadTimer == null) {
                    fileTableSortReloadTimer = new Timer(
                            600,
                            event -> {
                                if (reloadCurrentFileTable != null) {
                                    reloadCurrentFileTable.run();
                                }
                            });

                    fileTableSortReloadTimer.setRepeats(false);
                }

                fileTableSortReloadTimer.restart();
                table.getTableHeader().repaint();
            }
        });

        final javax.swing.table.TableCellRenderer originalRenderer =
                table.getTableHeader().getDefaultRenderer();

        table.getTableHeader().setDefaultRenderer(
                (table1, value, isSelected, hasFocus, row, column) -> {
                    Component component =
                            originalRenderer.getTableCellRendererComponent(
                                    table1,
                                    value,
                                    isSelected,
                                    hasFocus,
                                    row,
                                    column);

                    if (component instanceof JLabel label) {
                        label.setIcon(null);

                        if (table1.getRowSorter()
                                instanceof FileTableRowSorter fileSorter) {

                            List<? extends RowSorter.SortKey> sortKeys =
                                    fileSorter.getRequestedSortKeys();

                            for (RowSorter.SortKey key : sortKeys) {
                                if (key.getColumn() == column && column != 0) {
                                    if (key.getSortOrder()
                                            == SortOrder.ASCENDING) {
                                        label.setIcon(
                                                UIManager.getIcon(
                                                        "Table.ascendingSortIcon"));
                                    } else if (key.getSortOrder()
                                            == SortOrder.DESCENDING) {
                                        label.setIcon(
                                                UIManager.getIcon(
                                                        "Table.descendingSortIcon"));
                                    }
                                }
                            }
                        }
                    }

                    return component;
                });

        // Set the initial sort directly; do not create a pending request.
        sorter.setSortKeys(List.of(
                new RowSorter.SortKey(
                        FileTableModel.COL_FOLDER,
                        SortOrder.ASCENDING),
                new RowSorter.SortKey(
                        FileTableModel.COL_NAME,
                        SortOrder.ASCENDING)));

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
        S3Util.setActionIcon(flatBrowsingAction, IconProvider.ICON_FLAT_BROWSING);
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

    public JTextField getLocationField() {
        return locationField;
    }

    public void setLocationEditMode(boolean editMode) {
        locationEditMode = editMode;

        if (editLocationButton != null) {
            editLocationButton.setSelected(editMode);
        }

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

    public JLabel getFileSelectionInfo() {
        return fileSelectionInfo;
    }

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

    private void updateFileTableRowHeight(JTable table) {
        if (table == null) {
            return;
        }

        FontMetrics fontMetrics = table.getFontMetrics(table.getFont());
        int rowHeight = fontMetrics.getHeight() + 8;

        table.setRowHeight(rowHeight);
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

    public void setFlatBrowsingEnabled(boolean enabled) {
        if (flatBrowsingButton == null) {
            return;
        }

        flatBrowsingButton.setSelected(enabled);
        flatBrowsingButton.setToolTipText(
                enabled
                        ? "Flat Browsing (On)"
                        : "Flat Browsing (Off)");
    }



    private JPanel createFileTableFooter() {

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(
                BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JButton exportButton =
                new JButton(exportFileListAction);

        exportButton.setHideActionText(false);

        footer.add(
                exportButton,
                BorderLayout.EAST);

        fileFolderInfo = new JLabel("");
        fileSelectionInfo = new JLabel("");
        fileSelectionInfo.setVisible(false);

        fileSelectionSeparator =
                new JSeparator(SwingConstants.VERTICAL);

        fileSelectionSeparator.setPreferredSize(
                new Dimension(2, 18));

        fileSelectionSeparator.setVisible(false);

        // Keep all information components in one left-aligned row.
        JPanel informationRow =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        informationRow.setOpaque(false);
        informationRow.add(fileFolderInfo);
        informationRow.add(fileSelectionSeparator);
        informationRow.add(fileSelectionInfo);

        // Center the row vertically within the available footer height.
        JPanel informationPanel =
                new JPanel(new GridBagLayout());

        informationPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        informationPanel.add(informationRow, gbc);

        footer.add(
                informationPanel,
                BorderLayout.CENTER);

        return footer;
    }

    public void setFileSelectionSeparatorVisible(boolean visible) {

        if (fileSelectionSeparator == null) {
            return;
        }

        fileSelectionSeparator.setVisible(visible);
    }

    private JPanel createBreadcrumbFilterControls() {

        JPanel controls =
                new JPanel(new GridBagLayout());

        controls.setOpaque(false);

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 6);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;

        JLabel filterLabel =
                new JLabel("Filter Text");

        filterTextField =
                new JTextField();

        filterTextField.setToolTipText("Filter Text");
        filterTextField.setPreferredSize(
                new Dimension(150, 26));
        filterTextField.setMinimumSize(
                new Dimension(100, 26));

        filterLabel.setLabelFor(filterTextField);

        filterTextField.addActionListener(e -> {
            if (serverFilterApplyListener != null) {
                serverFilterApplyListener.run();
            }
        });

        filterTextField.addFocusListener(
                new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(
                            java.awt.event.FocusEvent e) {

                        if (serverFilterApplyListener != null) {
                            serverFilterApplyListener.run();
                        }
                    }
                });

        itemTypeCombo =
                new JComboBox<>(
                        new String[]{
                                "All",
                                "Folders Only",
                                "Files Only"
                        });

        itemTypeCombo.setSelectedItem("All");
        itemTypeCombo.setToolTipText("Item Type");
        itemTypeCombo.setPreferredSize(
                new Dimension(130, 26));

        itemTypeCombo.addActionListener(e -> {
            System.err.println(
                    "[FILTER DEBUG] itemTypeCombo action: "
                            + itemTypeCombo.getSelectedItem()
                            + ", time="
                            + java.time.LocalDateTime.now()
            );

            if (serverFilterApplyListener != null) {
                serverFilterApplyListener.run();
            }
        });

        gbc.gridx = 0;
        gbc.weightx = 0;
        controls.add(filterLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        controls.add(filterTextField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 0, 0);
        controls.add(itemTypeCombo, gbc);

        return controls;
    }

    public String getFilterText() {
        return filterTextField == null
                ? ""
                : filterTextField.getText();
    }

    public String getItemTypeFilter() {
        return itemTypeCombo == null
                ? "All"
                : (String) itemTypeCombo.getSelectedItem();
    }

    public void setServerFilterApplyListener(Runnable listener) {
        this.serverFilterApplyListener = listener;
    }
}