package com.company.s3explorer.transfer.logging;

import com.company.s3explorer.transfer.TransferRuntime;
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
import java.util.List;
import java.util.Map;
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

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE;

    private static final long FLUSH_INTERVAL_MILLIS = 500;

    private final Path rootDirectory;

    private final BlockingQueue<LogEntryWithPath> queue =
            new LinkedBlockingQueue<>();

    /*
     * Bir görevin ilk kaydedildiği dosya sabit kalır.
     *
     * Böylece gece yarısını geçen görevler sonraki
     * günün dosyasına taşınmaz.
     */
    private final Map<UUID, Path> taskFiles =
            new ConcurrentHashMap<>();

    /*
     * Dosyaların bellekte tutulan son durumları.
     *
     * Her dosya için Task ID -> CSV kaydı.
     */
    private final Map<Path, LinkedHashMap<UUID, LogEntry>> records =
            new LinkedHashMap<>();

    private final Map<Path, Boolean> loadedFiles =
            new LinkedHashMap<>();

    private final Map<Path, Boolean> dirtyFiles =
            new LinkedHashMap<>();

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

    /**
     * Transfer durum güncellemesini kayıt kuyruğuna ekler.
     *
     * Runtime ve Task nesnelerinin değişebilir alanları
     * burada okunarak değişmez bir LogEntry oluşturulur.
     */
    public void record(TransferRuntime runtime) {

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

        LogEntry entry = createEntry(
                runtime,
                task);

        queue.offer(
                new LogEntryWithPath(
                        file,
                        entry));
    }

    /**
     * Cancel All sırasında toplu olarak iptal edilen
     * queued görevleri kayıt kuyruğuna ekler.
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

    private Path resolveFile(TransferTask task) {

        LocalDate date = LocalDate.now();

        TransferGroup group = task.getGroup();

        String fileName =
                group == null
                        ? "individual.csv"
                        : group.getId() + ".csv";

        return rootDirectory
                .resolve(DATE_FORMAT.format(date))
                .resolve(fileName);
    }

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
                LogEntryWithPath item =
                        queue.poll(
                                FLUSH_INTERVAL_MILLIS,
                                TimeUnit.MILLISECONDS);

                if (item != null) {
                    process(item);
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

        List<LogEntryWithPath> batch =
                new ArrayList<>();

        queue.drainTo(batch);

        for (LogEntryWithPath item : batch) {
            process(item);
        }
    }

    private void process(LogEntryWithPath item) {

        try {
            loadFileIfNecessary(item.path());

            LinkedHashMap<UUID, LogEntry> fileRecords =
                    records.get(item.path());

            UUID taskId =
                    UUID.fromString(item.entry().taskId());

            fileRecords.put(
                    taskId,
                    item.entry());

            dirtyFiles.put(
                    item.path(),
                    true);

        } catch (Exception ex) {

            log.error(
                    "Could not process transfer log entry for {}.",
                    item.path(),
                    ex);
        }
    }

    // ---------------------------------------------------------------------
    // FILE LOADING
    // ---------------------------------------------------------------------

    private void loadFileIfNecessary(Path file)
            throws IOException {

        if (loadedFiles.containsKey(file)) {
            return;
        }

        LinkedHashMap<UUID, LogEntry> fileRecords =
                new LinkedHashMap<>();

        if (Files.exists(file)) {

            List<List<String>> rows =
                    readCsv(file);

            for (int i = 1; i < rows.size(); i++) {

                List<String> row = rows.get(i);

                if (row.size() != HEADERS.size()) {
                    continue;
                }

                try {
                    LogEntry entry =
                            fromCsvRow(row);

                    UUID taskId =
                            UUID.fromString(entry.taskId());

                    fileRecords.put(
                            taskId,
                            entry);

                } catch (IllegalArgumentException ex) {

                    log.warn(
                            "Ignoring invalid transfer log row in {}.",
                            file);
                }
            }
        }

        records.put(
                file,
                fileRecords);

        loadedFiles.put(
                file,
                true);
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

        List<List<String>> rows =
                new ArrayList<>();

        List<String> row =
                new ArrayList<>();

        StringBuilder field =
                new StringBuilder();

        boolean quoted = false;

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
                    }

                } else {
                    field.append(c);
                }

                continue;
            }

            if (c == '"') {

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

        if (field.length() > 0
                || !row.isEmpty()) {

            row.add(field.toString());
            rows.add(row);
        }

        return rows;
    }

    // ---------------------------------------------------------------------
    // CSV WRITING
    // ---------------------------------------------------------------------

    private void flushDirtyFiles() {

        List<Path> files =
                new ArrayList<>(dirtyFiles.keySet());

        for (Path file : files) {

            try {
                writeFile(file);

                dirtyFiles.remove(file);

            } catch (IOException ex) {

                /*
                 * Dosya dirty olarak kalır.
                 * Sonraki flush turunda tekrar denenir.
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

        Files.createDirectories(
                file.getParent());

        Path temporaryFile =
                file.resolveSibling(
                        file.getFileName() + ".tmp");

        try {

            StringBuilder content =
                    new StringBuilder();

            content.append(
                    String.join(";", HEADERS));

            content.append("\r\n");

            for (LogEntry entry :
                    fileRecords.values()) {

                content.append(
                        toCsvRow(entry));

                content.append("\r\n");
            }

            /*
             * UTF-8 BOM, Excel uyumluluğu için.
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

            Files.deleteIfExists(
                    temporaryFile);
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

            writerThread.join(
                    10_000);

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

    private record LogEntryWithPath(
            Path path,
            LogEntry entry) {
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