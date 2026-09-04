package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.event.TransferGroupUpdatedEvent;
import com.company.s3explorer.transfer.model.TransferGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UI seviyesinde TransferGroup kayıtlarını tutar.
 *
 * TransferStateStore task bazlı çalışmaya devam eder.
 * Bu sınıf ise grup bazlı görünümün state'idir.
 *
 * Aynı TransferGroup UUID'si yaşam döngüsü boyunca tek kayıttır:
 *
 * Preparing -> Running -> Finished
 *
 * Running'den Finished'a geçerken yeni bir grup oluşturulmaz.
 */
public class TransferGroupStateStore {

    private final Map<UUID, GroupRecord> groups =
            new LinkedHashMap<>();

    /**
     * Group event'ini store'a ekler veya mevcut kaydı günceller.
     */
    public synchronized void upsert(
            TransferGroupUpdatedEvent event) {

        if (event == null || event.getGroup() == null) {
            return;
        }

        TransferGroup group = event.getGroup();

        GroupRecord record =
                groups.computeIfAbsent(
                        group.getId(),
                        id -> new GroupRecord());

        record.update(event);
    }

    /**
     * Group tamamlandığında mevcut kaydı günceller.
     *
     * Aynı UUID korunur.
     */
    public synchronized void complete(
            TransferGroupCompletedEvent event) {

        if (event == null || event.getGroup() == null) {
            return;
        }

        TransferGroup group = event.getGroup();

        GroupRecord record =
                groups.computeIfAbsent(
                        group.getId(),
                        id -> new GroupRecord());

        record.complete(event);
    }

    /**
     * UUID ile kayıt getirir.
     */
    public synchronized GroupRecord get(UUID id) {

        if (id == null) {
            return null;
        }

        return groups.get(id);
    }

    /**
     * Bütün kayıtların snapshot'ını döndürür.
     */
    public synchronized List<GroupRecord> snapshot() {

        return Collections.unmodifiableList(
                new ArrayList<>(groups.values()));
    }

    /**
     * Running durumundaki grupları döndürür.
     */
    public synchronized List<GroupRecord> runningSnapshot() {

        List<GroupRecord> result =
                new ArrayList<>();

        for (GroupRecord record : groups.values()) {

            if (record.isRunning()) {
                result.add(record);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Finished durumundaki grupları döndürür.
     */
    public synchronized List<GroupRecord> finishedSnapshot() {

        List<GroupRecord> result =
                new ArrayList<>();

        for (GroupRecord record : groups.values()) {

            if (record.isFinished()) {
                result.add(record);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Belirli bir group var mı?
     */
    public synchronized boolean contains(UUID id) {

        return id != null && groups.containsKey(id);
    }

    /**
     * UI state temizliği için.
     */
    public synchronized void clear() {

        groups.clear();
    }

    /**
     * UI'da gösterilecek grup kaydı.
     *
     * Gerçek TransferGroup nesnesi burada tutulur.
     * Böylece TransferTask -> TransferGroup ilişkisi korunur.
     */
    public static final class GroupRecord {

        private TransferGroup group;

        private String repository;
        private String bucket;
        private String prefix;

        private boolean sourceRefreshRequired;

        private boolean finished;

        private GroupRecord() {
        }

        private void update(
                TransferGroupUpdatedEvent event) {

            this.group = event.getGroup();

            this.repository =
                    event.getRepository();

            this.bucket =
                    event.getBucket();

            this.prefix =
                    event.getPrefix();

            this.sourceRefreshRequired =
                    event.isSourceRefreshRequired();

            this.finished =
                    event.isFinished();
        }

        private void complete(
                TransferGroupCompletedEvent event) {

            this.group = event.getGroup();

            this.repository =
                    event.getRepository();

            this.bucket =
                    event.getBucket();

            this.prefix =
                    event.getPrefix();

            this.sourceRefreshRequired =
                    event.isSourceRefreshRequired();

            this.finished = true;
        }

        public TransferGroup getGroup() {
            return group;
        }

        public UUID getId() {

            return group != null
                    ? group.getId()
                    : null;
        }

        public String getDisplayName() {

            return group != null
                    ? group.getDisplayName()
                    : "";
        }

        public String getRepository() {
            return repository;
        }

        public String getBucket() {
            return bucket;
        }

        public String getPrefix() {
            return prefix;
        }

        public boolean isPreparing() {

            return !finished
                    && group != null
                    && group.isPreparing();
        }

        public boolean isRunning() {

            return !finished
                    && group != null
                    && (
                    group.isRunning()
                            || group.isPreparing()
            );
        }

        public boolean isFinished() {

            return finished
                    || (
                    group != null
                            && group.isFinished()
            );
        }

        public boolean isSuccessful() {

            return group != null
                    && group.isFullySuccessful();
        }

        public boolean isFailed() {

            return group != null
                    && group.isFailed();
        }

        public long getDetected() {

            return group != null
                    ? group.getDetected()
                    : 0;
        }

        public int getQueued() {

            return group != null
                    ? group.getQueued()
                    : 0;
        }

        public int getRunningCount() {

            return group != null
                    ? group.getRunning()
                    : 0;
        }

        public int getCompleted() {

            return group != null
                    ? group.getCompleted()
                    : 0;
        }

        public int getFailedCount() {

            return group != null
                    ? group.getFailed()
                    : 0;
        }

        public int getCancelled() {

            return group != null
                    ? group.getCancelled()
                    : 0;
        }

        public int getSkipped() {

            return group != null
                    ? group.getSkipped()
                    : 0;
        }

        public long getDetectedBytes() {

            return group != null
                    ? group.getDetectedBytes()
                    : 0;
        }

        public boolean isSourceRefreshRequired() {
            return sourceRefreshRequired;
        }
    }
}