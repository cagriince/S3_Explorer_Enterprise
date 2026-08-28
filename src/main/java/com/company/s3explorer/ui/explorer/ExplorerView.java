package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.transfer.renderer.FileSizeRenderer;
import com.company.s3explorer.transfer.renderer.InstantRenderer;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.theme.UITheme;
import com.company.s3explorer.ui.theme.UIThemeManager;

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
    private final Action deleteAction;
    private final Action copyAction;
    private final Action cutAction;
    private final Action pasteAction;
    private final Action uploadAction;
    private final Action newFolderAction;
    private final Action refreshAction;
    private final Action manageRepositoryAction;

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
    private JLabel fileFolderInfo;
    private JPopupMenu filePopup;

    private Consumer<Integer> fileTableRowLimitSelectionListener;
    private Consumer<Integer> threadCountSelectionListener;
    private boolean suppressThreadCountSelectionEvent;

    public ExplorerView(
            Action downloadAction,
            Action deleteAction,
            Action copyAction,
            Action cutAction,
            Action pasteAction,
            Action uploadAction,
            Action newFolderAction,
            Action refreshAction,
            Action manageRepositoryAction,
            Consumer<S3TreeNode> treeExpansionListener,
            Runnable openSelectedFileItem,
            Runnable reloadCurrentFileTable,
            Runnable updateActionStates,
            BooleanSupplier clipboardEmpty,
            Consumer<Integer> resizeExplorerPool) {

        this.downloadAction = downloadAction;
        this.deleteAction = deleteAction;
        this.copyAction = copyAction;
        this.cutAction = cutAction;
        this.pasteAction = pasteAction;
        this.uploadAction = uploadAction;
        this.newFolderAction = newFolderAction;
        this.refreshAction = refreshAction;
        this.manageRepositoryAction = manageRepositoryAction;
        this.treeExpansionListener = treeExpansionListener;
        this.openSelectedFileItem = openSelectedFileItem;
        this.reloadCurrentFileTable = reloadCurrentFileTable;
        this.updateActionStates = updateActionStates;
        this.clipboardEmpty = clipboardEmpty;
        this.resizeExplorerPool = resizeExplorerPool;
    }

    public JSplitPane createMainSplit() {
        S3TreeNode root = new S3TreeNode(
                S3TreeNode.ROOT_PREFIX,
                S3TreeNode.ROOT_PREFIX,
                S3TreeNode.ROOT_PREFIX);

        folderTree = new JTree(root);
        treeModel = (DefaultTreeModel) folderTree.getModel();

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
        setFolderTreeLeafIcon();

        fileTableModel = new FileTableModel();
        fileTable = createFileTable(fileTableModel);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(createTopBar(), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(folderTree), BorderLayout.CENTER);

        JScrollPane tableScrollPane = new JScrollPane(fileTable);
        installPopup(tableScrollPane.getViewport());
        installPopup(fileTable);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(createIconButton(newFolderAction));
        buttonPanel.add(createIconButton(uploadAction));
        buttonPanel.add(createIconButton(deleteAction));
        buttonPanel.add(createIconButton(downloadAction));
        buttonPanel.add(createSeparator());
        buttonPanel.add(createIconButton(copyAction));
        buttonPanel.add(createIconButton(cutAction));
        buttonPanel.add(createIconButton(pasteAction));

        JPanel themePanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 5, 5, 5);

        fileTableRowLimitCombo = new JComboBox<>(FILE_TABLE_ROW_LIMITS);
        fileTableRowLimitCombo.setSelectedItem(500);
        fileTableRowLimitCombo.setToolTipText("Max Item Count");
        alignComboBoxRight(fileTableRowLimitCombo);
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

        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        JPanel bottomButtonPanel = new JPanel(new BorderLayout());
        bottomButtonPanel.setPreferredSize(new Dimension(0, 40));
        bottomButtonPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        bottomButtonPanel.add(breadcrumbPanel, BorderLayout.CENTER);

        fileFolderInfo = new JLabel("");
        JPanel fileFolderInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        fileFolderInfoPanel.add(fileFolderInfo);
        bottomButtonPanel.add(fileFolderInfoPanel, BorderLayout.EAST);
        topButtonPanel.add(bottomButtonPanel, BorderLayout.SOUTH);

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
        button.setToolTipText((String) action.getValue(Action.NAME));
        button.setHideActionText(true);
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
        JMenuItem createFolderMenu = new JMenuItem(newFolderAction);
        JMenuItem uploadMenu = new JMenuItem(uploadAction);
        JMenuItem downloadMenu = new JMenuItem(downloadAction);
        JMenuItem deleteMenu = new JMenuItem(deleteAction);
        JMenuItem copyMenu = new JMenuItem(copyAction);
        JMenuItem cutMenu = new JMenuItem(cutAction);
        JMenuItem pasteMenu = new JMenuItem(pasteAction);

        filePopup.add(createFolderMenu);
        filePopup.add(uploadMenu);
        filePopup.add(downloadMenu);
        filePopup.add(deleteMenu);
        filePopup.addSeparator();
        filePopup.add(copyMenu);
        filePopup.add(cutMenu);
        filePopup.add(pasteMenu);
        filePopup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
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

    public void setFolderTreeLeafIcon() {
        DefaultTreeCellRenderer renderer =
                (DefaultTreeCellRenderer) folderTree.getCellRenderer();
        renderer.setLeafIcon(renderer.getClosedIcon());
    }

    public void setButtonIcons() {
        repositoryLabel.setIcon(IconProvider.ICON_REPOSITORY);
        bucketLabel.setIcon(IconProvider.ICON_BUCKET);
        setActionIcon(manageRepositoryAction, IconProvider.ICON_SETTINGS);
        setActionIcon(refreshAction, IconProvider.ICON_REFRESH);
        setActionIcon(uploadAction, IconProvider.ICON_UPLOAD);
        setActionIcon(newFolderAction, IconProvider.ICON_CREATE_FOLDER);
        setActionIcon(downloadAction, IconProvider.ICON_DOWNLOAD);
        setActionIcon(deleteAction, IconProvider.ICON_DELETE);
        setActionIcon(copyAction, IconProvider.ICON_COPY);
        setActionIcon(cutAction, IconProvider.ICON_CUT);
        setActionIcon(pasteAction, IconProvider.ICON_PASTE);
    }

    private void setActionIcon(Action action, Icon icon) {
        action.putValue(Action.LARGE_ICON_KEY, icon);
    }

    public JTree getFolderTree() { return folderTree; }
    public DefaultTreeModel getTreeModel() { return treeModel; }
    public JTable getFileTable() { return fileTable; }
    public FileTableModel getFileTableModel() { return fileTableModel; }
    public JComboBox<RepositoryDefinition> getRepositoryCombo() { return repositoryCombo; }
    public JComboBox<String> getBucketCombo() { return bucketCombo; }
    public JComboBox<UITheme> getThemeCombo() { return themeCombo; }
    public JComboBox<Integer> getFileTableRowLimitCombo() { return fileTableRowLimitCombo; }
    public JComboBox<Integer> getThreadCountCombo() { return threadCountCombo; }
    public JPanel getBreadcrumbPanel() { return breadcrumbPanel; }
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
}