package com.company.s3explorer.ui.transfer;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * TransferGroup tablosunun modelidir.
 *
 * Her satır bir TransferGroup'u temsil eder.
 *
 * TransferTask'lar bu tabloda gösterilmez.
 */
public class TransferGroupTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "Operation",
            "Group Name",
            "Process Detail",
            "Status",
            "Summary"
    };

    private final List<TransferGroupStateStore.GroupRecord> rows =
            new ArrayList<>();

    /**
     * Store snapshot'ını tabloya uygular.
     *
     * EDT üzerinden çağrılması beklenir.
     */
    public void setRows(
            List<TransferGroupStateStore.GroupRecord> records) {

        rows.clear();

        if (records != null) {
            rows.addAll(records);
        }

        fireTableDataChanged();
    }

    /**
     * Mevcut tabloyu temizler.
     */
    public void clear() {

        if (rows.isEmpty()) {
            return;
        }

        rows.clear();

        fireTableDataChanged();
    }

    /**
     * Tek bir group'ı ekler veya günceller.
     *
     * UUID üzerinden çalışır.
     */
    public void upsert(
            TransferGroupStateStore.GroupRecord record) {

        if (record == null || record.getId() == null) {
            return;
        }

        for (int i = 0; i < rows.size(); i++) {

            TransferGroupStateStore.GroupRecord existing =
                    rows.get(i);

            if (record.getId().equals(existing.getId())) {

                rows.set(i, record);

                fireTableRowsUpdated(i, i);

                return;
            }
        }

        rows.add(0, record);

        fireTableRowsInserted(0, 0);
    }

    /**
     * UUID ile satırı kaldırır.
     */
    public void remove(java.util.UUID id) {

        if (id == null) {
            return;
        }

        for (int i = 0; i < rows.size(); i++) {

            TransferGroupStateStore.GroupRecord record =
                    rows.get(i);

            if (id.equals(record.getId())) {

                rows.remove(i);

                fireTableRowsDeleted(i, i);

                return;
            }
        }
    }

    public TransferGroupStateStore.GroupRecord getRecord(
            int row) {

        if (row < 0 || row >= rows.size()) {
            return null;
        }

        return rows.get(row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(
            int column) {

        if (column < 0
                || column >= COLUMNS.length) {

            return "";
        }

        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(
            int rowIndex,
            int columnIndex) {

        TransferGroupStateStore.GroupRecord record =
                getRecord(rowIndex);

        if (record == null) {
            return "";
        }

        switch (columnIndex) {

            case 0:
                return getOperation(record);

            case 1:
                return record.getDisplayName();

            case 2:
                return buildProcessDetail(record);

            case 3:
                return getStatus(record);

            case 4:
                return buildSummary(record);

            default:
                return "";
        }
    }

    /**
     * Operation doğrudan TransferGroup metadata'sından alınır.
     *
     * COPY / MOVE gibi değerler artık displayName'den
     * tahmin edilmez.
     */
    private String getOperation(
            TransferGroupStateStore.GroupRecord record) {

        if (record.getGroup() == null) {
            return "";
        }

        String operation =
                record.getGroup().getOperation();

        if (operation == null
                || operation.isBlank()) {

            return "";
        }

        return operation;
    }

    /**
     * Kaynak -> hedef bilgisini doğrudan TransferGroup
     * metadata'sından oluşturur.
     */
    private String buildProcessDetail(
            TransferGroupStateStore.GroupRecord record) {

        if (record.getGroup() == null) {
            return "";
        }

        String source =
                record.getGroup().getSource();

        String target =
                record.getGroup().getTarget();

        if (source == null
                || source.isBlank()) {

            source = "";
        }

        if (target == null
                || target.isBlank()) {

            target = "";
        }

        if (source.isEmpty()
                && target.isEmpty()) {

            return "";
        }

        if (source.isEmpty()) {
            return target;
        }

        if (target.isEmpty()) {
            return source;
        }

        return source
                + " → "
                + target;
    }

    private String getStatus(
            TransferGroupStateStore.GroupRecord record) {

        if (record.isFinished()) {

            if (record.isFailed()) {
                return "Failed";
            }

            return "Finished";
        }

        /*
         * Preparing ayrı bir panel değildir.
         *
         * Grup Running tablosunda kalır;
         * Status sütununda Preparing görünür.
         */
        if (record.isPreparing()) {
            return "Preparing";
        }

        if (record.isRunning()) {
            return "Running";
        }

        /*
         * Producer henüz ilk task'ı üretmeden önceki
         * kısa geçiş durumu.
         */
        return "Preparing";
    }

    private String buildSummary(
            TransferGroupStateStore.GroupRecord record) {

        long detected =
                record.getDetected();

        int completed =
                record.getCompleted();

        int failed =
                record.getFailedCount();

        int skipped =
                record.getSkipped();

        int cancelled =
                record.getCancelled();

        if (record.isFinished()) {

            return detected
                    + " detected / "
                    + completed
                    + " completed"
                    + ", "
                    + failed
                    + " failed"
                    + ", "
                    + skipped
                    + " skipped";
        }

        String summary =
                detected
                        + " detected / "
                        + completed
                        + " completed";

        if (failed > 0) {

            summary +=
                    ", "
                            + failed
                            + " failed";
        }

        if (skipped > 0) {

            summary +=
                    ", "
                            + skipped
                            + " skipped";
        }

        if (cancelled > 0) {

            summary +=
                    ", "
                            + cancelled
                            + " cancelled";
        }

        return summary;
    }

    @Override
    public Class<?> getColumnClass(
            int columnIndex) {

        return String.class;
    }
}