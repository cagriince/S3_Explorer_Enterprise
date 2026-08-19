package com.company.s3explorer.transfer.worker;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.queue.TransferQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerExecutor
        implements AutoCloseable {

    private static final int MAX_THREAD_COUNT = 100;

    private final ExecutorService executor;

    private final TransferQueue queue;
    private final TransferContext context;
    private final TransferOperationFactory factory;

    private final AtomicInteger activeWorkerCount =
            new AtomicInteger();

    private volatile int targetThreadCount;

    public WorkerExecutor(
            int threadCount,
            TransferQueue queue,
            TransferContext context,
            TransferOperationFactory factory) {

        this.queue = queue;
        this.context = context;
        this.factory = factory;

        this.targetThreadCount =
                normalizeThreadCount(
                        threadCount);

        /*
         * Executor'un maksimum kapasitesi 100.
         *
         * Gerçekte sadece ihtiyaç kadar worker submit
         * edildiği için başlangıçta 100 thread oluşmaz.
         */
        executor =
                Executors.newFixedThreadPool(
                        MAX_THREAD_COUNT,
                        r -> {

                            Thread t =
                                    new Thread(r);

                            t.setName(
                                    "TransferWorker");

                            t.setDaemon(true);

                            return t;
                        });

        startWorkers(
                this.targetThreadCount);
    }

    private int normalizeThreadCount(
            int threadCount) {

        if (threadCount < 1) {
            return 1;
        }

        if (threadCount > MAX_THREAD_COUNT) {
            return MAX_THREAD_COUNT;
        }

        return threadCount;
    }

    private synchronized void startWorkers(
            int count) {

        for (int i = 0; i < count; i++) {
            startWorker();
        }
    }

    private synchronized void startWorker() {

        activeWorkerCount.incrementAndGet();

        try {

            executor.submit(
                    new TransferWorker(
                            queue,
                            context,
                            factory,
                            this::shouldRetireWorker,
                            this::workerFinished));

        }
        catch (RuntimeException ex) {

            activeWorkerCount.decrementAndGet();

            throw ex;
        }
    }

    private boolean shouldRetireWorker() {

        return activeWorkerCount.get()
                > targetThreadCount;
    }

    private void workerFinished() {

        activeWorkerCount.decrementAndGet();
    }

    public synchronized void setThreadCount(
            int threadCount) {

        int newCount =
                normalizeThreadCount(
                        threadCount);

        int oldCount =
                this.targetThreadCount;

        if (oldCount == newCount) {
            return;
        }

        this.targetThreadCount =
                newCount;

        System.out.println(
                "[WORKER POOL] thread count "
                        + oldCount
                        + " -> "
                        + newCount);

        /*
         * Thread sayısı artırılıyorsa hemen worker ekle.
         */
        if (newCount > oldCount) {

            int current =
                    activeWorkerCount.get();

            int required =
                    newCount - current;

            for (int i = 0;
                 i < required;
                 i++) {

                startWorker();
            }
        }

        /*
         * Thread sayısı azaltılıyorsa hiçbir çalışan
         * transferi kesmiyoruz.
         *
         * Fazla worker'lar mevcut transferlerini bitirip
         * queue'dan çıkacak.
         */
    }

    public int getThreadCount() {
        return targetThreadCount;
    }

    public int getActiveWorkerCount() {
        return activeWorkerCount.get();
    }

    @Override
    public void close() {

        executor.shutdownNow();
    }
}