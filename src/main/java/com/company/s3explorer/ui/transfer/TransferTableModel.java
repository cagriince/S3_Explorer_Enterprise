package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.company.s3explorer.util.S3Util;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TransferTableModel
        extends AbstractTableModel {

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

    private final List<TransferRuntime> runtimes =
            new ArrayList<>();

    private final int maxRows;

    public TransferTableModel() {
        this(1000);
    }

    public TransferTableModel(
            int maxRows) {

        if (maxRows < 1) {
            throw new IllegalArgumentException(
                    "maxRows must be greater than zero");
        }

        this.maxRows = maxRows;
    }

    @Override
    public int getRowCount() {
        return runtimes.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(
            int column) {

        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(
            int row,
            int column) {

        if (row < 0
                || row >= runtimes.size()) {

            return "";
        }

        TransferRuntime runtime =
                runtimes.get(row);

        return switch (column) {

            case 0 ->
                    runtime.getTask().getType();

            case 1 ->
                    buildDisplayName(
                            runtime.getTask());

            case 2 ->
                    runtime.getTask().getSize();

            case 3 ->
                    runtime;

            case 4 ->
                    runtime.getStatus();

            case 5 ->
                    runtime.getStartTime();

            case 6 ->
                    runtime.getEndTime();

            case 7 ->
                    runtime.getElapsedTime();

            case 8 ->
                    runtime.getMessage();

            default ->
                    "";
        };
    }

    @Override
    public Class<?> getColumnClass(
            int column) {

        return switch (column) {

            case 0 ->
                    TransferType.class;

            case 1 ->
                    String.class;

            case 2 ->
                    Long.class;

            case 3 ->
                    TransferRuntime.class;

            case 4 ->
                    TransferStatus.class;

            case 5, 6 ->
                    Instant.class;

            case 7 ->
                    Long.class;

            case 8 ->
                    String.class;

            default ->
                    Object.class;
        };
    }

    public void setSnapshot(
            List<TransferRuntime> snapshot) {

        runtimes.clear();

        if (snapshot != null) {
            runtimes.addAll(snapshot);
        }

        fireTableDataChanged();
    }

    public TransferRuntime getRuntime(
            int row) {

        if (row < 0
                || row >= runtimes.size()) {

            return null;
        }

        return runtimes.get(row);
    }

    public TransferRuntime getRuntimeAtModelRow(
            int modelRow) {

        return getRuntime(modelRow);
    }

    public int getMaxRows() {
        return maxRows;
    }

    private String displayBucket(
            String repository,
            String bucket) {

        return "<b><font color='"
                + UIThemeManager.TRANSFER_PANEL_COLOR_BUCKET
                + "'>"
                + repository
                + " | "
                + bucket
                + "</font> / </b>";
    }

    private String displayLastFileFolder(
            String path) {

        if (path == null) {
            return "";
        }

        path = path.replace("\\", "/");

        String folderPath =
                S3Util.extractParentPrefix(path);

        return "<b>"
                + folderPath.replace(
                "/",
                " / ")
                + "<font color='"
                + UIThemeManager.TRANSFER_PANEL_COLOR_FILEFOLDER
                + "'>"
                + path.substring(
                folderPath.length())
                + "</font></b>";
    }

    private String buildDisplayName(
            TransferTask task) {

        StringBuilder sb =
                new StringBuilder();

        sb.append("<html>");

        if (task.getGroup() != null) {

            sb.append(
                    displayGroupName(task));

            sb.append("<br />");
        }

        sb.append("\uD83D\uDFB3 ");

        sb.append(
                buildSourceDisplayName(task));

        String target =
                buildTargetDisplayName(task);

        if (!target.isEmpty()) {

            sb.append(
                    "<br />=\uD83D\uDF82 ");

            sb.append(target);
        }

        sb.append("</html>");

        return sb.toString();
    }

    private String buildSourceDisplayName(
            TransferTask task) {

        StringBuilder display =
                new StringBuilder();

        if (task.getType()
                == TransferType.CREATE_FOLDER) {

            display.append(
                    displayBucket(
                            task.getRepositoryName(),
                            task.getBucket()));

            display.append(
                    displayLastFileFolder(
                            task.getObjectKey()));

        } else if (task.getType()
                == TransferType.UPLOAD) {

            if (task.getLocalPath() != null) {

                display.append(
                        displayLastFileFolder(
                                task.getLocalPath()
                                        .toString()));
            }

        } else if (task.getType()
                == TransferType.DOWNLOAD
                || task.getType()
                == TransferType.DELETE
                || task.getType()
                == TransferType.COPY
                || task.getType()
                == TransferType.MOVE) {

            display.append(
                    displayBucket(
                            task.getRepositoryName(),
                            task.getBucket()));

            display.append(
                    displayLastFileFolder(
                            task.getObjectKey()));
        }

        return display.toString();
    }

    private static String displayGroupName(
            TransferTask task) {

        TransferGroup group =
                task.getGroup();

        if (group == null) {
            return "";
        }

        return "<b><font color='"
                + UIThemeManager.TRANSFER_PANEL_COLOR_GROUP
                + "'>"
                + group.getDisplayName()
                + "</font></b>";
    }

    private String buildTargetDisplayName(
            TransferTask task) {

        StringBuilder display =
                new StringBuilder();

        if (task.getType()
                == TransferType.UPLOAD) {

            display.append(
                    displayBucket(
                            task.getTargetRepositoryName(),
                            task.getTargetBucket()));

            display.append(
                    displayLastFileFolder(
                            task.getTargetObjectKey()));

        } else if (task.getType()
                == TransferType.DOWNLOAD) {

            if (task.getLocalPath() != null) {

                display.append(
                        displayLastFileFolder(
                                task.getLocalPath()
                                        .toString()));
            }

        } else if (task.getType()
                == TransferType.COPY
                || task.getType()
                == TransferType.MOVE) {

            display.append(
                    displayBucket(
                            task.getTargetRepositoryName(),
                            task.getTargetBucket()));

            display.append(
                    displayLastFileFolder(
                            task.getTargetObjectKey()));
        }

        return display.toString();
    }
}