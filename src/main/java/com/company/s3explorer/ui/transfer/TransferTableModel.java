package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.company.s3explorer.util.DateFormatter;
import com.company.s3explorer.util.S3Util;
import com.company.s3explorer.util.SizeFormatter;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.*;

public class TransferTableModel extends AbstractTableModel {
    private final LinkedHashMap<UUID, TransferRuntime> runtimes = new LinkedHashMap<>();
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Integer> rows = new HashMap<>();
    private final Map<UUID, TransferStatus> publishedStatuses = new HashMap<>();
    private static final String[] COLUMNS = {
            "Process",
            "Process Detail",
            "Size",
            "Progress",
            "Status",
            "Start Time",
            "End Time",
            "Elapsed Time (ms)",
            "Error Message"
    };

    @Override
    public int getRowCount() {
        return order.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        TransferRuntime runtime = getRuntime(row);
        if (runtime == null) {
            return "";
        }

        return switch (column) {
            case 0 -> runtime.getTask().getType();
            case 1 -> buildDisplayName(runtime.getTask());
            case 2 -> runtime.getTask().getSize();
            case 3 -> runtime;
            case 4 -> runtime.getStatus();
            case 5 -> runtime.getStartTime();
            case 6 -> runtime.getEndTime();
            case 7 -> runtime.getElapsedTime();
            case 8 -> runtime.getMessage();
            default -> "";
        };
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 0 -> TransferTask.class;
            case 1 -> String.class;
            case 2 -> Long.class;
            case 3 -> TransferRuntime.class;
            case 4 -> TransferStatus.class;
            case 5 -> Instant.class;
            case 6 -> Instant.class;
            case 7 -> Long.class;
            case 8 -> String.class;
            default -> Object.class;
        };
    }

    private String displayBucket(String repository, String bucket) {
        return "<b><font color='" + UIThemeManager.TRANSFER_PANEL_COLOR_BUCKET+ "'>" + repository + " | " + bucket + "</font> / </b>";
    }

    private String displayLastFileFolder(String path) {
        path = path.replace("\\", "/");
        String folderPath = S3Util.extractParentPrefix(path);
        return "<b>" + folderPath.replace("/", " / ") + "<font color='" + UIThemeManager.TRANSFER_PANEL_COLOR_FILEFOLDER + "'>" + path.substring(folderPath.length()) + "</font></b>";
    }

    private String buildDisplayName(TransferTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        if (task.getGroup() != null) {
            sb.append(displayGroupName(task));
            sb.append("<br />");
        }
        sb.append("\uD83D\uDFB3 ");
        sb.append(buildSourceDisplayName(task));
        String targetDisplayName = buildTargetDisplayName(task);
        if (targetDisplayName.length() > 0) {
            sb.append("<br />=\uD83D\uDF82 ");
            sb.append(targetDisplayName);
        }
        sb.append("</html>");

        return sb.toString();
    }

    private String buildSourceDisplayName(TransferTask task) {
        StringBuilder displayName = new StringBuilder();

        if (task.getType().equals(TransferType.CREATE_FOLDER)) {
            displayName.append(displayBucket(task.getRepositoryName(), task.getBucket()));
            displayName.append(displayLastFileFolder(task.getObjectKey()));
        } else if (task.getType().equals(TransferType.UPLOAD)) {
            displayName.append(displayLastFileFolder(task.getLocalPath().toString()));
        } else if (task.getType().equals(TransferType.DOWNLOAD)) {
            displayName.append(displayBucket(task.getRepositoryName(), task.getBucket()));
            displayName.append(displayLastFileFolder(task.getObjectKey()));
        } else if (task.getType().equals(TransferType.DELETE)) {
            displayName.append(displayBucket(task.getRepositoryName(), task.getBucket()));
            displayName.append(displayLastFileFolder(task.getObjectKey()));
        } else if (task.getType().equals(TransferType.COPY) || task.getType().equals(TransferType.MOVE)) {
            displayName.append(displayBucket(task.getRepositoryName(), task.getBucket()));
            displayName.append(displayLastFileFolder(task.getObjectKey()));
        }

        return displayName.toString();
    }

    private static String displayGroupName(TransferTask task) {
        TransferGroup group = task.getGroup();
        String groupName = null;
        if (group != null) {
            groupName = group.getDisplayName();
            return "<b><font color='" + UIThemeManager.TRANSFER_PANEL_COLOR_GROUP + "'>" + groupName + "</font></b>";
        }

        return "";
    }

    private String buildTargetDisplayName(TransferTask task) {
        StringBuilder displayName = new StringBuilder();
        if (task.getType().equals(TransferType.CREATE_FOLDER)) {
        }
        else if (task.getType().equals(TransferType.UPLOAD)) {
            displayName.append(displayBucket(task.getTargetRepositoryName(), task.getTargetBucket()));
            displayName.append(displayLastFileFolder(task.getTargetObjectKey()));
        }
        else if (task.getType().equals(TransferType.DOWNLOAD)) {
            displayName.append(displayLastFileFolder(task.getLocalPath().toString()));
        }
        else if (task.getType().equals(TransferType.DELETE)) {
        }
        else if (task.getType().equals(TransferType.COPY) || task.getType().equals(TransferType.MOVE)) {
            displayName.append(displayBucket(task.getTargetRepositoryName(), task.getTargetBucket()));
            displayName.append(displayLastFileFolder(task.getTargetObjectKey()));
        }

        return displayName.toString();
    }
/*
    private String buildDisplayName(TransferTask task) {
         String name;

        if (task.getLocalPath() != null) {
            name = task.getLocalPath().getFileName().toString();
        }
        else if (task.getObjectKey() == null) {
            name = "";
        }
        else {
            int index = task.getObjectKey().lastIndexOf('/');
            if (index >= 0) {
                name = task.getObjectKey().substring(index + 1);
            } else {
                name = task.getObjectKey();
            }
        }

        TransferGroup group = task.getGroup();
        if (group == null) {
            return name;
        }

        return group.getDisplayName(name);
*/
        /*
        String name;

        if (task.getLocalPath() != null) {
            name = task.getLocalPath().getFileName().toString();
        }
        else if (task.getObjectKey() == null) {
            name = "";
        }
        else {
            int index = task.getObjectKey().lastIndexOf('/');
            if (index >= 0) {
                name = task.getObjectKey().substring(index + 1);
            } else {
                name = task.getObjectKey();
            }
        }

        TransferGroup group = task.getGroup();
        if (group == null) {
            return name;
        }

        return group.getDisplayName(name);
    }*/

    public TransferRuntime getRuntime(int row) {
        if (row < 0 || row >= order.size()) {
            return null;
        }

        UUID id = order.get(row);
        return runtimes.get(id);
    }

    public TransferRuntime getRuntimeAtModelRow(int modelRow) {
        return getRuntime(modelRow);
    }

    public TransferUpdateType upsertTask(TransferRuntime runtime) {
        UUID id = runtime.getTask().getId();
        Integer row = rows.get(id);
        if (row == null) {
            runtimes.put(id, runtime);
            order.addFirst(id);
            rebuildIndexes();
            publishedStatuses.put(id, runtime.getStatus());
            fireTableRowsInserted(0, 0);
            return TransferUpdateType.INSERTED;
        }

        TransferStatus oldStatus = publishedStatuses.get(id);
        TransferStatus newStatus = runtime.getStatus();

        runtimes.put(id, runtime);
        publishedStatuses.put(id, newStatus);

        fireTableRowsUpdated(row, row);

        if (oldStatus != newStatus) {
            return TransferUpdateType.STATUS_CHANGED;
        }

        return TransferUpdateType.UPDATED;
/*
        UUID id = runtime.getTask().getId();
        Integer existingRow = rows.get(id);
        if (existingRow == null) {
            runtimes.put(id, runtime);
            order.addFirst(id);
            rebuildIndexes();
            fireTableRowsInserted(0, 0);
        } else {
            runtimes.put(id, runtime);
            fireTableRowsUpdated(existingRow, existingRow);
        }
        fireTableDataChanged();*/
    }

    public void removeCompleted() {
        Iterator<TransferRuntime> it = runtimes.values().iterator();
        while (it.hasNext()) {
            TransferRuntime runtime = it.next();
            if (runtime.getStatus().isFinished()) {
                UUID id = runtime.getTask().getId();
                it.remove();
                order.remove(id);
                rows.remove(id);
                publishedStatuses.remove(id);
            }
        }

        rebuildIndexes();
        fireTableDataChanged();
    }

    public long getQueuedCount() {
        return getCount(TransferStatus.QUEUED);
    }

    public long getRunningCount() {
        return getCount(TransferStatus.RUNNING);
    }

    public long getCompletedCount() {
        return getCount(TransferStatus.COMPLETED);
    }

    public long getFailedCount() {
        return getCount(TransferStatus.FAILED);
    }

    public long getCancelledCount() {
        return getCount(TransferStatus.CANCELLED);
    }

    private long getCount(TransferStatus status) {
        return runtimes.values()
                .stream()
                .filter(t -> t.getStatus() == status)
                .count();
    }

    public void removeFinished() {
        Iterator<TransferRuntime> it = runtimes.values().iterator();
        while (it.hasNext()) {
            TransferRuntime runtime = it.next();
            TransferStatus status = runtime.getStatus();

            if (status.isFinished()) {
                UUID id = runtime.getTask().getId();
                it.remove();
                order.remove(id);
                rows.remove(id);
            }
        }

        rebuildIndexes();
        fireTableDataChanged();
    }

    private void rebuildIndexes() {
        rows.clear();
        for (int i = 0; i < order.size(); i++) {
            rows.put(order.get(i), i);
        }
    }

    public void fireStructureRefresh() {
        fireTableDataChanged();
    }
}