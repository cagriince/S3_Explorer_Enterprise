
package com.company.s3explorer.transfer.logging;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransferLogWriter implements AutoCloseable {

    private static final Logger log =
            LoggerFactory.getLogger(TransferLogWriter.class);

    private static final List<String> HEADERS = List.of(
            "Group ID",
            "Group Name",
            "Operation",
            "Task ID",
            "Source",
            "Target",
            "Size",
            "Status",
            "Start Time",
            "End Time",
            "Duration (ms)",
            "Message"
    );

    private static final int COLUMN_COUNT = HEADERS.size();

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE;

    private static final long FLUSH_INTERVAL_MILLIS = 500;

    private final Path rootDirectory;

    private final BlockingQueue<WriterCommand> queue =
            new LinkedBlockingQueue<>();

    /*
     * Task ID -> CSV path.
     *
     * QUEUED aşamasında atanır. Görev gece yarısını
     * geçerek tamamlansa bile ilk tarih dosyasında kalır.
     */
    private final Map<UUID, Path> taskFiles =
            new ConcurrentHashMap<>();

    /*
     * Group ID -> CSV path.
     *
     * Grup için ilk kayıt anında atanır.
     */
    private final Map<UUID, Path> groupFiles =
            new ConcurrentHashMap<>();

    /*
     * Aşağıdaki koleksiyonlar yalnızca writer thread
     * tarafından kullanılır.
     */
    private final Map<Path, LinkedHashMap<UUID, LogEntry>> records =
            new LinkedHashMap<>();

    private final Map<Path, Set<UUID>> groupTaskIds =
            new LinkedHashMap<>();

    /*
     * Her grup için gerçekten TransferRuntime oluşturulmuş
     * görevlerin UUID listesi.
     *
     * register() farklı worker thread'lerinden çağrılabileceği
     * için ConcurrentHashMap ve concurrent Set kullanılır.
     */
    private final Map<Path, Set<UUID>> registeredGroupTaskIds =
            new ConcurrentHashMap<>();

    private final Map<Path, GroupSummary> pendingGroupSummaries =
            new LinkedHashMap<>();

    private final Set<Path> finalizedGroupFiles =
            new LinkedHashSet<>();

    private final Set<Path> loadedFiles =
            new LinkedHashSet<>();

    private final Set<Path> dirtyFiles =
            new LinkedHashSet<>();

    private final AtomicBoolean closed =
            new AtomicBoolean(false);

    private final Thread writerThread;

    public TransferLogWriter() {
        this(Path.of("logs", "transfers"));
    }

    public TransferLogWriter(Path rootDirectory) {

        if (rootDirectory == null) {
            throw new IllegalArgumentException(
                    "Log directory cannot be null.");
        }

        this.rootDirectory = rootDirectory;

        writerThread = new Thread(
                this::runWriter,
                "TransferLogWriter");

        writerThread.setDaemon(true);
        writerThread.start();
    }

    // ---------------------------------------------------------------------
    // REGISTRATION
    // ---------------------------------------------------------------------

    /**
     * Görevin ilk görüldüğü anda dosya yolunu belirler.
     *
     * Bu metot CSV kaydı oluşturmaz.
     * QUEUED olayında çağrılması amaçlanmıştır.
     */
    public void register(TransferRuntime runtime) {

        if (runtime == null || closed.get()) {
            return;
        }

        TransferTask task = runtime.getTask();

        if (task == null || task.getId() == null) {
            return;
        }

        UUID taskId = task.getId();

        Path file = taskFiles.computeIfAbsent(
                taskId,
                id -> resolveFile(task));

        TransferGroup group = task.getGroup();

        if (group == null || group.getId() == null) {
            return;
        }

        registerGroup(group);

        /*
         * Bu görev için gerçekten TransferRuntime oluşturuldu.
         * Grup özeti hazırlanırken bu görev de beklenmelidir.
         */
        registeredGroupTaskIds
                .computeIfAbsent(
                        file,
                        ignored -> ConcurrentHashMap.newKeySet())
                .add(taskId);
    }

    /**
     * Görev üretimi başlamadan önce grup için tarih
     * dosyasının sabitlenmesini sağlar.
     *
     * Özellikle hiç dosya üretmeyen gruplarda da
     * grup dosyasının doğru tarihte oluşmasını sağlar.
     */
    public void registerGroup(TransferGroup group) {

        if (group == null
                || group.getId() == null
                || closed.get()) {
            return;
        }

        groupFiles.computeIfAbsent(
                group.getId(),
                id -> resolveGroupFile(id));
    }

    // ---------------------------------------------------------------------
    // RECORDING
    // ---------------------------------------------------------------------

    /**
     * Yalnızca terminal durumdaki görevler kaydedilir.
     *
     * QUEUED ve RUNNING olayları CSV'ye yazılmaz.
     */
    public void record(TransferRuntime runtime) {

        if (runtime == null || closed.get()) {
            return;
        }

        TransferStatus status = runtime.getStatus();

        if (status == null || !status.isFinished()) {
            return;
        }

        TransferTask task = runtime.getTask();

        if (task == null || task.getId() == null) {
            return;
        }

        /*
         * Görevin dosya yolunu ve grup kaydını garanti altına al.
         */
        register(runtime);

        UUID taskId = task.getId();

        Path file = taskFiles.get(taskId);

        if (file == null) {
            log.error(
                    "Could not resolve transfer log file for task {}.",
                    taskId);
            return;
        }

        LogEntry entry = createEntry(runtime, task);

        queue.offer(
                new TaskResultCommand(file, entry));
    }

    /**
     * Cancel All sırasında toplu iptal edilen görevler.
     */
    public void recordQueuedCancelled(
            List<TransferRuntime> runtimes) {

        if (runtimes == null || runtimes.isEmpty()) {
            return;
        }

        for (TransferRuntime runtime : runtimes) {
            record(runtime);
        }
    }

    /**
     * Grup tamamlandığında çağrılır.
     *
     * Bu metot özeti doğrudan dosyaya yazmaz.
     * Writer thread, terminal görev kayıtlarının tamamını
     * işledikten sonra özeti dosyaya ekler.
     */
    public void recordGroupCompleted(TransferGroup group) {

        if (group == null
                || group.getId() == null
                || closed.get()) {
            return;
        }

        if (!group.isFinished()) {
            log.warn(
                    "Ignoring incomplete transfer group completion: {}",
                    group.getId());
            return;
        }

        registerGroup(group);

        Path file = groupFiles.get(group.getId());

        if (file == null) {
            return;
        }

        GroupSummary summary = new GroupSummary(
                group.getId().toString(),
                nullToEmpty(group.getDisplayName()),
                group.getDetected(),
                group.getCompleted(),
                group.getFailed(),
                group.getCancelled(),
                group.getSkipped(),
                group.getErrorMessage(),
                group.isFullySuccessful()
        );

        queue.offer(
                new GroupCompletedCommand(file, summary));
    }

    // ---------------------------------------------------------------------
    // FILE PATHS
    // ---------------------------------------------------------------------

    private Path resolveFile(TransferTask task) {

        TransferGroup group = task.getGroup();

        if (group == null || group.getId() == null) {
            return resolveIndividualFile(LocalDate.now());
        }

        return groupFiles.computeIfAbsent(
                group.getId(),
                this::resolveGroupFile);
    }

    private Path resolveGroupFile(UUID groupId) {

        return rootDirectory
                .resolve(DATE_FORMAT.format(LocalDate.now()))
                .resolve(groupId + ".csv");
    }

    private Path resolveIndividualFile(LocalDate date) {

        return rootDirectory
                .resolve(DATE_FORMAT.format(date))
                .resolve("individual.csv");
    }

    // ---------------------------------------------------------------------
    // ENTRY CREATION
    // ---------------------------------------------------------------------

    private LogEntry createEntry(
            TransferRuntime runtime,
            TransferTask task) {

        TransferGroup group = task.getGroup();

        String groupId =
                group == null || group.getId() == null
                        ? ""
                        : group.getId().toString();

        String groupName =
                group == null
                        ? ""
                        : nullToEmpty(group.getDisplayName());

        String operation =
                task.getType() == null
                        ? ""
                        : task.getType().name();

        return new LogEntry(
                groupId,
                groupName,
                operation,
                task.getId().toString(),
                buildSource(task),
                buildTarget(task),
                Long.toString(task.getSize()),
                runtime.getStatus() == null
                        ? ""
                        : runtime.getStatus().name(),
                runtime.getStartTime() == null
                        ? ""
                        : runtime.getStartTime().toString(),
                runtime.getEndTime() == null
                        ? ""
                        : runtime.getEndTime().toString(),
                Long.toString(runtime.getElapsedTime()),
                nullToEmpty(runtime.getMessage())
        );
    }

    private String buildSource(TransferTask task) {

        if (task == null || task.getType() == null) {
            return "";
        }

        return switch (task.getType()) {
            case UPLOAD -> task.getLocalPath() == null
                    ? ""
                    : task.getLocalPath().toString();

            case DOWNLOAD,
                 DELETE,
                 COPY,
                 MOVE,
                 RENAME,
                 CREATE_FOLDER -> joinLocation(
                    task.getRepositoryId(),
                    task.getBucket(),
                    task.getObjectKey());

            default -> "";
        };
    }

    private String buildTarget(TransferTask task) {

        if (task == null || task.getType() == null) {
            return "";
        }

        return switch (task.getType()) {
            case UPLOAD,
                 COPY,
                 MOVE,
                 RENAME -> joinLocation(
                    task.getTargetRepositoryId(),
                    task.getTargetBucket(),
                    task.getTargetObjectKey());

            case DOWNLOAD -> task.getLocalPath() == null
                    ? ""
                    : task.getLocalPath().toString();

            case CREATE_FOLDER -> joinLocation(
                    task.getRepositoryId(),
                    task.getBucket(),
                    task.getObjectKey());

            case DELETE -> "";

            default -> "";
        };
    }

    private String joinLocation(String... parts) {

        List<String> values = new ArrayList<>();

        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                values.add(part);
            }
        }

        return String.join("/", values);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ---------------------------------------------------------------------
    // WRITER THREAD
    // ---------------------------------------------------------------------

    private void runWriter() {

        while (!closed.get() || !queue.isEmpty()) {

            try {
                WriterCommand command = queue.poll(
                        FLUSH_INTERVAL_MILLIS,
                        TimeUnit.MILLISECONDS);

                if (command != null) {
                    process(command);
                }

                drainQueue();
                flushDirtyFiles();

            } catch (InterruptedException ex) {

                if (!closed.get()) {
                    Thread.currentThread().interrupt();
                    break;
                }

            } catch (Exception ex) {

                log.error(
                        "Unexpected error in transfer log writer.",
                        ex);
            }
        }

        flushDirtyFiles();
    }

    private void drainQueue() {

        List<WriterCommand> batch = new ArrayList<>();

        queue.drainTo(batch);

        for (WriterCommand command : batch) {
            process(command);
        }
    }

    private void process(WriterCommand command) {

        if (command instanceof TaskResultCommand taskCommand) {
            processTaskResult(taskCommand);
            return;
        }

        if (command instanceof GroupCompletedCommand groupCommand) {
            processGroupCompleted(groupCommand);
        }
    }

    // ---------------------------------------------------------------------
    // TASK RESULT PROCESSING
    // ---------------------------------------------------------------------

    private void processTaskResult(TaskResultCommand command) {

        Path file = command.path();
        LogEntry entry = command.entry();

        try {
            loadFileIfNecessary(file);

            LinkedHashMap<UUID, LogEntry> fileRecords =
                    records.get(file);

            UUID taskId = UUID.fromString(entry.taskId());

            /*
             * Aynı terminal olayın tekrar gelmesi durumunda
             * aynı Task ID üzerine yazılır.
             */
            fileRecords.put(taskId, entry);

            if (!entry.groupId().isBlank()) {

                groupTaskIds
                        .computeIfAbsent(
                                file,
                                ignored -> new LinkedHashSet<>())
                        .add(taskId);

            } else {
                dirtyFiles.add(file);
            }

            /*
             * Grup özeti daha önce geldiyse ve son görev
             * kaydı şimdi işlendi ise grup dosyası tamamlanır.
             */
            tryFinalizeGroup(file);

        } catch (Exception ex) {

            log.error(
                    "Could not process transfer log entry for {}.",
                    file,
                    ex);
        }
    }

    // ---------------------------------------------------------------------
    // GROUP SUMMARY PROCESSING
    // ---------------------------------------------------------------------

    private void processGroupCompleted(
            GroupCompletedCommand command) {

        Path file = command.path();

        try {
            loadFileIfNecessary(file);

            if (finalizedGroupFiles.contains(file)) {
                return;
            }

            pendingGroupSummaries.put(
                    file,
                    command.summary());

            tryFinalizeGroup(file);

        } catch (Exception ex) {

            log.error(
                    "Could not process group completion for {}.",
                    file,
                    ex);
        }
    }

    /**
     * Grup özeti ancak beklenen terminal görev sayısı
     * kadar benzersiz görev kaydı işlendiğinde yazılır.
     */
    private void tryFinalizeGroup(Path file) {

        GroupSummary summary =
                pendingGroupSummaries.get(file);

        if (summary == null
                || finalizedGroupFiles.contains(file)) {
            return;
        }

        Set<UUID> taskIds =
                groupTaskIds.computeIfAbsent(
                        file,
                        ignored -> new LinkedHashSet<>());

        Set<UUID> registeredTaskIds =
                registeredGroupTaskIds.get(file);

        int expectedTaskCount =
                registeredTaskIds == null
                        ? 0
                        : registeredTaskIds.size();

        /*
         * Yalnızca gerçekten TransferRuntime oluşturulmuş
         * görevlerin terminal kayıtlarını bekle.
         *
         * Kuyruğa hiç alınmamış ve yalnızca grup sayacında
         * cancelled olarak bulunan görevler beklenmez.
         */
        if (taskIds.size() < expectedTaskCount) {
            return;
        }

        LinkedHashMap<UUID, LogEntry> fileRecords =
                records.get(file);

        if (fileRecords == null) {
            return;
        }

        /*
         * Boyut yalnızca bu gruba ait görev kayıtlarından
         * toplanır. Bilinmeyen / geçersiz değerler atlanır.
         */
        long totalSize = 0L;

        for (UUID taskId : taskIds) {

            LogEntry entry = fileRecords.get(taskId);

            if (entry == null) {
                continue;
            }

            try {
                totalSize = Math.addExact(
                        totalSize,
                        Long.parseLong(entry.size()));

            } catch (NumberFormatException
                     | ArithmeticException ex) {

                log.warn(
                        "Could not include task size in group summary. Task ID: {}",
                        taskId);
            }
        }

        appendSummary(file, summary, totalSize);

        finalizedGroupFiles.add(file);
        pendingGroupSummaries.remove(file);
        dirtyFiles.add(file);

        log.info(
                "Transfer group log completed. Group ID: {}, Registered Tasks: {}, Terminal Records: {}",
                summary.groupId(),
                expectedTaskCount,
                taskIds.size());
    }

    private void appendSummary(
            Path file,
            GroupSummary summary,
            long totalSize) {

        LinkedHashMap<UUID, LogEntry> fileRecords =
                records.get(file);

        if (fileRecords == null) {
            return;
        }

        /*
         * Özet satırları da aynı 12 sütun yapısında tutulur.
         *
         * İlk sütun: SUMMARY / alan adı
         * İkinci sütun: değer
         */
        fileRecords.put(
                summaryRowId(summary.groupId(), "section"),
                summaryRow("SUMMARY", ""));

        fileRecords.put(
                summaryRowId(summary.groupId(), "total"),
                summaryRow("Total Tasks",
                        Long.toString(summary.detected())));

        fileRecords.put(
                summaryRowId(summary.groupId(), "completed"),
                summaryRow("Successful",
                        Long.toString(summary.completed())));

        fileRecords.put(
                summaryRowId(summary.groupId(), "failed"),
                summaryRow("Failed",
                        Long.toString(summary.failed())));

        fileRecords.put(
                summaryRowId(summary.groupId(), "cancelled"),
                summaryRow("Cancelled",
                        Long.toString(summary.cancelled())));

        fileRecords.put(
                summaryRowId(summary.groupId(), "skipped"),
                summaryRow("Skipped",
                        Long.toString(summary.skipped())));

        fileRecords.put(
                summaryRowId(summary.groupId(), "size"),
                summaryRow("Total Size",
                        Long.toString(totalSize)));

        fileRecords.put(
                summaryRowId(summary.groupId(), "error"),
                summaryRow("Production Error",
                        nullToEmpty(summary.errorMessage())));

        String result;

        if (summary.fullySuccessful()) {
            result = "SUCCESS";
        } else if (summary.completed() > 0) {
            result = "PARTIAL SUCCESS";
        } else {
            result = "FAILED";
        }

        fileRecords.put(
                summaryRowId(summary.groupId(), "result"),
                summaryRow("Result", result));
    }

    private UUID summaryRowId(
            String groupId,
            String key) {

        return UUID.nameUUIDFromBytes(
                (groupId + ":SUMMARY:" + key)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private LogEntry summaryRow(
            String label,
            String value) {

        return new LogEntry(
                label,
                value,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );
    }

    // ---------------------------------------------------------------------
    // FILE LOADING
    // ---------------------------------------------------------------------

    private void loadFileIfNecessary(Path file)
            throws IOException {

        if (loadedFiles.contains(file)) {
            return;
        }

        LinkedHashMap<UUID, LogEntry> fileRecords =
                new LinkedHashMap<>();

        Set<UUID> taskIds =
                new LinkedHashSet<>();

        if (Files.exists(file)) {

            List<List<String>> rows = readCsv(file);

            if (!rows.isEmpty()
                    && !rows.get(0).equals(HEADERS)) {

                throw new IOException(
                        "Unexpected CSV header in " + file);
            }

            for (int i = 1; i < rows.size(); i++) {

                List<String> row = rows.get(i);

                if (row.size() != COLUMN_COUNT) {
                    throw new IOException(
                            "Invalid CSV column count in "
                                    + file
                                    + " at row "
                                    + (i + 1));
                }

                LogEntry entry = fromCsvRow(row);

                /*
                 * Task satırları UUID taşır.
                 * SUMMARY satırları ise özel anahtar kullanır.
                 */
                try {
                    UUID taskId = UUID.fromString(entry.taskId());

                    fileRecords.put(taskId, entry);

                    if (!entry.groupId().isBlank()) {
                        taskIds.add(taskId);
                    }

                } catch (IllegalArgumentException ex) {

                    if (!entry.taskId().isBlank()) {
                        throw new IOException(
                                "Invalid task ID in " + file,
                                ex);
                    }

                    UUID summaryId = UUID.nameUUIDFromBytes(
                            (entry.groupId()
                                    + ":EXISTING_SUMMARY:"
                                    + i)
                                    .getBytes(StandardCharsets.UTF_8));

                    fileRecords.put(summaryId, entry);
                }
            }
        }

        records.put(file, fileRecords);
        groupTaskIds.put(file, taskIds);
        loadedFiles.add(file);
    }

    private LogEntry fromCsvRow(List<String> row) {

        return new LogEntry(
                row.get(0),
                row.get(1),
                row.get(2),
                row.get(3),
                row.get(4),
                row.get(5),
                row.get(6),
                row.get(7),
                row.get(8),
                row.get(9),
                row.get(10),
                row.get(11)
        );
    }

    /**
     * CSV okuyucu:
     * - quoted fields
     * - delimiter inside quoted fields
     * - escaped quotes
     * - multiline fields
     * - CRLF / LF
     *
     * Hatalı quote yapısı tespit edilirse dosyanın
     * bozuk veriyle yeniden yazılmasını önlemek için hata verir.
     */
    private List<List<String>> readCsv(Path file)
            throws IOException {

        String content = Files.readString(
                file,
                StandardCharsets.UTF_8);

        if (!content.isEmpty()
                && content.charAt(0) == '\uFEFF') {

            content = content.substring(1);
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean quoted = false;
        boolean fieldClosed = false;

        for (int i = 0; i < content.length(); i++) {

            char c = content.charAt(i);

            if (quoted) {

                if (c == '"') {

                    if (i + 1 < content.length()
                            && content.charAt(i + 1) == '"') {

                        field.append('"');
                        i++;

                    } else {
                        quoted = false;
                        fieldClosed = true;
                    }

                } else {
                    field.append(c);
                }

                continue;
            }

            if (fieldClosed) {

                if (c == ';') {
                    row.add(field.toString());
                    field.setLength(0);
                    fieldClosed = false;

                } else if (c == '\n' || c == '\r') {

                    row.add(field.toString());
                    field.setLength(0);
                    fieldClosed = false;

                    rows.add(row);
                    row = new ArrayList<>();

                    if (c == '\r'
                            && i + 1 < content.length()
                            && content.charAt(i + 1) == '\n') {
                        i++;
                    }

                } else {
                    throw new IOException(
                            "Unexpected character after quoted CSV field.");
                }

                continue;
            }

            if (c == '"') {

                if (field.length() != 0) {
                    throw new IOException(
                            "Unexpected quote inside unquoted CSV field.");
                }

                quoted = true;

            } else if (c == ';') {

                row.add(field.toString());
                field.setLength(0);

            } else if (c == '\n' || c == '\r') {

                row.add(field.toString());
                field.setLength(0);

                rows.add(row);
                row = new ArrayList<>();

                if (c == '\r'
                        && i + 1 < content.length()
                        && content.charAt(i + 1) == '\n') {
                    i++;
                }

            } else {
                field.append(c);
            }
        }

        if (quoted) {
            throw new IOException(
                    "Unclosed quoted CSV field.");
        }

        if (fieldClosed
                || field.length() > 0
                || !row.isEmpty()) {

            row.add(field.toString());
            rows.add(row);
        }

        /*
         * Dosyanın sonunda bulunan tek satır sonu nedeniyle
         * boş bir kayıt oluşturulmaz.
         */
        return rows;
    }

    // ---------------------------------------------------------------------
    // CSV WRITING
    // ---------------------------------------------------------------------

    private void flushDirtyFiles() {

        List<Path> files = new ArrayList<>(dirtyFiles);

        for (Path file : files) {

            try {
                writeFile(file);
                dirtyFiles.remove(file);

            } catch (IOException ex) {

                /*
                 * Dirty durum korunur.
                 * Bir sonraki turda tekrar denenir.
                 */
                log.error(
                        "Could not write transfer log file: {}",
                        file,
                        ex);
            }
        }
    }

    private void writeFile(Path file)
            throws IOException {

        LinkedHashMap<UUID, LogEntry> fileRecords =
                records.get(file);

        if (fileRecords == null) {
            return;
        }

        Files.createDirectories(file.getParent());

        Path temporaryFile = file.resolveSibling(
                file.getFileName() + ".tmp");

        try {

            StringBuilder content = new StringBuilder();

            content.append(String.join(";", HEADERS));
            content.append("\r\n");

            for (LogEntry entry : fileRecords.values()) {
                content.append(toCsvRow(entry));
                content.append("\r\n");
            }

            /*
             * UTF-8 BOM: Excel uyumluluğu.
             */
            Files.writeString(
                    temporaryFile,
                    "\uFEFF" + content,
                    StandardCharsets.UTF_8);

            try {

                Files.move(
                        temporaryFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

            } catch (AtomicMoveNotSupportedException ex) {

                Files.move(
                        temporaryFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private String toCsvRow(LogEntry entry) {

        return String.join(
                ";",
                escape(entry.groupId()),
                escape(entry.groupName()),
                escape(entry.operation()),
                escape(entry.taskId()),
                escape(entry.source()),
                escape(entry.target()),
                escape(entry.size()),
                escape(entry.status()),
                escape(entry.startTime()),
                escape(entry.endTime()),
                escape(entry.duration()),
                escape(entry.message())
        );
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        boolean needsQuotes =
                value.contains(";")
                        || value.contains("\"")
                        || value.contains("\n")
                        || value.contains("\r");

        if (!needsQuotes) {
            return value;
        }

        return "\""
                + value.replace("\"", "\"\"")
                + "\"";
    }

    // ---------------------------------------------------------------------
    // SHUTDOWN
    // ---------------------------------------------------------------------

    @Override
    public void close() {

        if (!closed.compareAndSet(false, true)) {
            return;
        }

        writerThread.interrupt();

        try {

            writerThread.join(10_000);

            if (writerThread.isAlive()) {
                log.error(
                        "Transfer log writer did not stop within 10 seconds.");
            }

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            log.warn(
                    "Interrupted while waiting for transfer log writer.",
                    ex);
        }
    }

    // ---------------------------------------------------------------------
    // INTERNAL DATA
    // ---------------------------------------------------------------------

    private interface WriterCommand {
    }

    private record TaskResultCommand(
            Path path,
            LogEntry entry) implements WriterCommand {
    }

    private record GroupCompletedCommand(
            Path path,
            GroupSummary summary) implements WriterCommand {
    }

    private record GroupSummary(
            String groupId,
            String groupName,
            long detected,
            int completed,
            int failed,
            int cancelled,
            int skipped,
            String errorMessage,
            boolean fullySuccessful) {
    }

    private record LogEntry(
            String groupId,
            String groupName,
            String operation,
            String taskId,
            String source,
            String target,
            String size,
            String status,
            String startTime,
            String endTime,
            String duration,
            String message) {
    }
}
