package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**

 * Group ve individual transfer satırlarını aynı JTable
 * içerisinde göstermek için kullanılan birleşik UI modelidir.
 *
 * Satır düzeni:
 *
 * 
 GROUP
 
 * 
 GROUP
 
 * 
 GROUP
 
 * 
 TRANSFER
 
 * 
 TRANSFER
 
 * 
 TRANSFER
 
 *
 * Group satırları startTime DESC sıralanır.
 *
 * Transfer satırlarının sırası ise TransferStateStore tarafından
 * oluşturulan snapshot sırasına aynen sadık kalır.
 *
 * Domain state'leri birleştirilmez:
 *
 * 
 TransferStateStore       -> individual task state
 
 * 
 TransferGroupStateStore  -> logical group state
 
 *
 * Bu sınıf yalnızca UI seviyesinde iki state'i tek tabloya
 * dönüştürür.
 */
public class TransferCombinedTableModel
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
          "Summary"
          

};

private final List<Row> rows =
        new ArrayList<>();

/*

 * Individual transfer satırlarının mevcut UI gösterim
 * mantığını tekrar uygulamamak için mevcut model
 * içeride kullanılır.
 */
private final TransferTableModel transferModel;

public TransferCombinedTableModel() {

  
    this(1000);
  

}

public TransferCombinedTableModel(
        int maxTransferRows) {

  
    if (maxTransferRows < 1) {
        throw new IllegalArgumentException(
                "maxTransferRows must be greater than zero");
    }

    transferModel =
            new TransferTableModel(
                    maxTransferRows);
  

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

  
    return COLUMNS[column];
  

}

@Override
public Object getValueAt(
        int row,
        int column) {

  
    if (row < 0
            || row >= rows.size()) {

        return "";
    }

    Row combinedRow =
            rows.get(row);

    if (combinedRow.isGroup()) {

        return getGroupValue(
                combinedRow.getGroup(),
                column);
    }

    /*
     * Individual transfer satırında mevcut
     * TransferTableModel davranışını aynen koru.
     */
    return transferModel.getValueAt(
            combinedRow.getTransferModelRow(),
            column);
  

}

@Override
public Class<?> getColumnClass(
        int column) {

  
    /*
     * Group ve Transfer satırlarında aynı sütunda
     * farklı tipler bulunabildiği için bazı kolonlar
     * Object olarak tanımlanır.
     *
     * Renderer tarafında gerçek değer tipine göre
     * davranılacaktır.
     */
    return switch (column) {

        case 0 ->
                Object.class;

        case 1 ->
                String.class;

        case 2 ->
                Object.class;

        case 3 ->
                Object.class;

        case 4 ->
                Object.class;

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

/**

 * Group ve transfer snapshot'larını birleştirir.
 *
 * Group satırları:
 * 
 startTime DESC
 
 *
 * Transfer satırları:
 * 
 snapshot sırası
 
 *
 * Sonuç:
 *
 * 
 [groups...][transfers...]
 

 */
public void setSnapshot(
        List<TransferGroupStateStore.GroupRecord> groups,
        List<TransferRuntime> transfers) {

  
    rows.clear();

    /*
     * ---------------------------------------------------------
     * GROUP ROWS
     * ---------------------------------------------------------
     */

    if (groups != null
            && !groups.isEmpty()) {

        List<TransferGroupStateStore.GroupRecord>
                sortedGroups =
                new ArrayList<>(groups);

        sortedGroups.sort(
                Comparator.comparing(
                        TransferGroupStateStore.GroupRecord::getStartTime,
                        Comparator.nullsLast(
                                Comparator.reverseOrder())));

        for (TransferGroupStateStore.GroupRecord group :
                sortedGroups) {

            if (group == null) {
                continue;
            }

            rows.add(
                    Row.group(group));
        }
    }

    /*
     * ---------------------------------------------------------
     * TRANSFER ROWS
     * ---------------------------------------------------------
     */

    transferModel.setSnapshot(
            transfers);

    if (transfers != null
            && !transfers.isEmpty()) {

        for (int i = 0;
             i < transfers.size();
             i++) {

            TransferRuntime runtime =
                    transfers.get(i);

            if (runtime == null) {
                continue;
            }

            rows.add(
                    Row.transfer(i));
        }
    }

    fireTableDataChanged();
  

}

/**

 * Belirli bir model satırının group satırı olup olmadığını
 * döndürür.
 */
public boolean isGroupRow(
        int modelRow) {

    if (modelRow < 0
            || modelRow >= rows.size()) {

    
        return false;
    

    }

    return rows.get(modelRow).isGroup();
}

/**

 * Belirli model satırındaki GroupRecord'u döndürür.
 *
 * Individual transfer satırlarında null döner.
 */
public TransferGroupStateStore.GroupRecord getGroup(
        int modelRow) {

    if (modelRow < 0
            || modelRow >= rows.size()) {

    
        return null;
    

    }

    Row row =
            rows.get(modelRow);

    return row.isGroup()
            ? row.getGroup()
            : null;
}

/**

 * Belirli model satırındaki TransferRuntime'ı döndürür.
 *
 * Group satırlarında null döner.
 */
public TransferRuntime getRuntime(
        int modelRow) {

    if (modelRow < 0
            || modelRow >= rows.size()) {

    
        return null;
    

    }

    Row row =
            rows.get(modelRow);

    if (row.isGroup()) {
        return null;
    }

    return transferModel.getRuntime(
            row.getTransferModelRow());
}

/**

 * JTable selection / cancel gibi işlemlerde kullanılmak
 * üzere model satırını transfer task model satırına çevirir.
 *
 * Group satırlarında -1 döner.
 */
public int getTransferModelRow(
        int modelRow) {

    if (modelRow < 0
            || modelRow >= rows.size()) {

    
        return -1;
    

    }

    Row row =
            rows.get(modelRow);

    if (row.isGroup()) {
        return -1;
    }

    return row.getTransferModelRow();
}

/**

 * Group satırının JTable'da taşınabilir / cancellable
 * olmadığını açıkça belirtir.
 */
public boolean isTransferRow(
        int modelRow) {

    return !isGroupRow(modelRow);
}

private Object getGroupValue(
        TransferGroupStateStore.GroupRecord group,
        int column) {

  
    if (group == null) {
        return "";
    }

    switch (column) {

        /*
         * Process
         *
         * Kullanıcı istedi:
         *
         *     Process <- Operation
         *
         * Örn:
         *     COPY
         *     MOVE
         *     DELETE
         */
        case 0:

            return safe(
                    group.getGroup() != null
                            ? group.getGroup().getOperation()
                            : null);

        /*
         * Process Detail
         *
         * Kullanıcı istedi:
         *
         *     Group Name + Process Detail
         *
         * Örn:
         *
         *     Photos
         *     source/... -> target/...
         */
        case 1:

            return buildGroupProcessDetail(
                    group);

        /*
         * Size
         *
         * Group satırında boş.
         */
        case 2:

            return null;

        /*
         * Progress
         *
         *     Completed / Detected
         *
         * Örn:
         *
         *     73 / 125
         */
        case 3:

            return new GroupProgress(
                    group.getCompleted(),
                    group.getDetected(),
                    group.isPreparing());

        /*
         * Status
         */
        case 4:

            return getGroupStatus(
                    group);

        /*
         * Start Time
         */
        case 5:

            return group.getStartTime();

        /*
         * End Time
         */
        case 6:

            return group.getEndTime();

        /*
         * Elapsed Time
         *
         * Running:
         *     start -> now
         *
         * Finished:
         *     start -> end
         */
        case 7:

            return group.getElapsedTime();

        /*
         * Summary
         */
        case 8:

            return buildGroupSummary(
                    group);

        default:

            return "";
    }
  

}

private String buildGroupProcessDetail(
        TransferGroupStateStore.GroupRecord group) {

  
    String groupName =
            safe(group.getDisplayName());

    String source =
            group.getGroup() != null
                    ? safe(group.getGroup().getSource())
                    : "";

    String target =
            group.getGroup() != null
                    ? safe(group.getGroup().getTarget())
                    : "";

    StringBuilder result =
            new StringBuilder();

    result.append(groupName);

    String processDetail =
            buildSourceTarget(
                    source,
                    target);

    if (!processDetail.isEmpty()) {

        result.append(
                " - ");

        result.append(
                processDetail);
    }

    return result.toString();
  

}

private String buildSourceTarget(
        String source,
        String target) {

  
    if (source == null) {
        source = "";
    }

    if (target == null) {
        target = "";
    }

    source = source.trim();
    target = target.trim();

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
            + " \u2192 "
            + target;
  

}

private String getGroupStatus(
        TransferGroupStateStore.GroupRecord group) {

  
    if (group.isFinished()) {

        if (group.isFailed()) {
            return "Failed";
        }

        return "Finished";
    }

    if (group.isPreparing()) {
        return "Preparing";
    }

    if (group.isRunning()) {
        return "Running";
    }

    return "Preparing";
  

}

private String buildGroupSummary(
        TransferGroupStateStore.GroupRecord group) {

  
    long detected =
            group.getDetected();

    int completed =
            group.getCompleted();

    int failed =
            group.getFailedCount();

    int cancelled =
            group.getCancelled();

    int skipped =
            group.getSkipped();

    StringBuilder summary =
            new StringBuilder();

    summary.append(
            completed);

    summary.append(
            " / ");

    summary.append(
            detected);

    if (failed > 0) {

        summary.append(
                ", failed ");

        summary.append(
                failed);
    }

    if (cancelled > 0) {

        summary.append(
                ", cancelled ");

        summary.append(
                cancelled);
    }

    if (skipped > 0) {

        summary.append(
                ", skipped ");

        summary.append(
                skipped);
    }

    return summary.toString();
  

}

private String safe(
        String value) {

  
    return value != null
            ? value
            : "";
  

}

/**

 * Group progress bilgisini taşıyan immutable UI objesi.
 *
 * Renderer bu objeyi kullanarak:
 *
 * 
 Completed / Detected
 
 *
 * bilgisini ve yüzdeyi gösterebilir.
 */
public static final class GroupProgress {

    private final int completed;
    private final long detected;
    private final boolean preparing;

    private GroupProgress(
            int completed,
            long detected,
            boolean preparing) {

    
        this.completed =
                Math.max(
                        0,
                        completed);

        this.detected =
                Math.max(
                        0L,
                        detected);

        this.preparing =
                preparing;
    

    }

    public int getCompleted() {

    
        return completed;
    

    }

    public long getDetected() {

    
        return detected;
    

    }

    public boolean isPreparing() {

    
        return preparing;
    

    }

    public int getPercent() {

    
        if (detected <= 0) {
            return 0;
        }

        long percent =
                completed * 100L / detected;

        return (int) Math.max(
                0L,
                Math.min(
                        100L,
                        percent));
    

    }

    public String getText() {

    
        return completed
                + " / "
                + detected;
    

    }
}

/**

 * Unified UI row.
 *
 * Domain state'leri burada birleştirilmez.
 * Sadece hangi tip satır olduğunu ve ilgili kaynağın
 * referansını tutar.
 */
private static final class Row {

    private enum Type {
        GROUP,
        TRANSFER
    }

    private final Type type;

    private final TransferGroupStateStore.GroupRecord group;

    /*

     * TransferTableModel içerisindeki satır numarası.
     *
     * Böylece mevcut TransferTableModel'in bütün
     * gösterim mantığı korunur.
     */
    private final int transferModelRow;

    private Row(
            Type type,
            TransferGroupStateStore.GroupRecord group,
            int transferModelRow) {

    
        this.type =
                type;

        this.group =
                group;

        this.transferModelRow =
                transferModelRow;
    

    }

    private static Row group(
            TransferGroupStateStore.GroupRecord group) {

    
        return new Row(
                Type.GROUP,
                group,
                -1);
    

    }

    private static Row transfer(
            int transferModelRow) {

    
        return new Row(
                Type.TRANSFER,
                null,
                transferModelRow);
    

    }

    private boolean isGroup() {

    
        return type == Type.GROUP;
    

    }

    private TransferGroupStateStore.GroupRecord getGroup() {

    
        return group;
    

    }

    private int getTransferModelRow() {

    
        return transferModelRow;
    

    }
}
    }