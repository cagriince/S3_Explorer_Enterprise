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
    public String getColumnName(int column) {

        if (column < 0 || column >= COLUMNS.length) {
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
     * Operation şu anda GroupRecord'da ayrı bir alan olarak
     * tutulmadığı için güvenli fallback kullanıyoruz.
     *
     * TransferPanel/operation metadata entegrasyonunda gerçek
     * COPY/MOVE değeri buraya bağlanacaktır.
     */
    private String getOperation(
            TransferGroupStateStore.GroupRecord record) {

        String name = record.getDisplayName();

        if (name == null || name.isBlank()) {
            return "";
        }

        String lower = name.trim().toLowerCase();

        if (lower.startsWith("copy ")) {
            return "COPY";
        }

        if (lower.startsWith("move ")) {
            return "MOVE";
        }

        return "";
    }

    /**
     * Kaynak -> hedef bilgisini oluşturur.
     *
     * Event'in taşıdığı repository/bucket/prefix bilgileri
     * kullanılır.
     */
    private String buildProcessDetail(
            TransferGroupStateStore.GroupRecord record) {

        String source = buildLocation(
                record.getRepository(),
                record.getBucket(),
                record.getPrefix());

        return source + " → " + source;
    }

    private String buildLocation(
            String repository,
            String bucket,
            String prefix) {

        StringBuilder value =
                new StringBuilder();

        if (repository != null
                && !repository.isBlank()) {

            value.append(repository);
        }

        if (bucket != null
                && !bucket.isBlank()) {

            if (!value.isEmpty()) {
                value.append("/");
            }

            value.append(bucket);
        }

        if (prefix != null
                && !prefix.isBlank()) {

            if (!value.isEmpty()
                    && !value.toString().endsWith("/")) {

                value.append("/");
            }

            value.append(prefix);
        }

        return value.toString();
    }

    private String getStatus(
            TransferGroupStateStore.GroupRecord record) {

        if (record.isFinished()) {

            if (record.isFailed()) {
                return "Failed";
            }

            return "Finished";
        }

        if (record.isPreparing()) {
            return "Preparing";
        }

        if (record.isRunning()) {
            return "Running";
        }

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

        return detected
                + " detected / "
                + completed
                + " completed";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
}