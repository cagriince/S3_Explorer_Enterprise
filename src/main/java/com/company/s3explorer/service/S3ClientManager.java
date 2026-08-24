package com.company.s3explorer.service;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.HashMap;
import java.util.Map;

public class S3ClientManager implements AutoCloseable {

    private final Map<RepositoryDefinition, S3Client> clientMap;

    private final RepositoryManager repositoryManager;

    private final S3ClientFactory clientFactory;

    private final ActiveRepositoryContext repositoryContext;

    private final java.util.function.Consumer<RepositoryDefinition>
            repositoryChangeListener;

    public S3ClientManager(
            RepositoryManager repositoryManager,
            S3ClientFactory clientFactory,
            ActiveRepositoryContext repositoryContext) {

        this.repositoryManager =
                repositoryManager;

        this.clientFactory =
                clientFactory;

        this.repositoryContext =
                repositoryContext;

        this.clientMap =
                new HashMap<>();

        /*
         * Repository değiştiğinde cache'teki S3
         * client'larının artık güvenilir olmadığını
         * kabul ediyoruz.
         */
        this.repositoryChangeListener =
                repository ->
                        invalidateAllClients();

        repositoryManager.addRepositoryChangeListener(
                repositoryChangeListener);
    }

    public S3Client getClient(
            String repositoryName) {

        if (repositoryName == null) {

            return getClient(
                    repositoryContext
                            .getActiveRepository());
        }

        return getClient(
                repositoryManager
                        .findByName(repositoryName));
    }

    public synchronized S3Client getClient(
            RepositoryDefinition repository) {

        if (repository == null
                || repository.isEmpty()) {

            throw new IllegalArgumentException(
                    "Repository cannot be empty");
        }

        S3Client existingClient =
                clientMap.get(repository);

        if (existingClient != null) {

            return existingClient;
        }

        S3Client client =
                clientFactory.create(repository);

        clientMap.put(
                repository,
                client);

        return client;
    }

    private synchronized void invalidateAllClients() {

        if (clientMap.isEmpty()) {
            return;
        }

        for (S3Client client :
                clientMap.values()) {

            try {

                client.close();

            } catch (Exception ex) {

                ex.printStackTrace();
            }
        }

        clientMap.clear();
    }

    @Override
    public synchronized void close() {

        /*
         * Uygulama kapanırken listener'ın tekrar
         * çalışmasını istemiyoruz.
         */
        repositoryManager
                .removeRepositoryChangeListener(
                        repositoryChangeListener);

        invalidateAllClients();
    }
}