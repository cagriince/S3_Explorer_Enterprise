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

    public S3ClientManager(RepositoryManager repositoryManager, S3ClientFactory clientFactory, ActiveRepositoryContext repositoryContext) {
        this.repositoryManager = repositoryManager;
        this.clientFactory = clientFactory;
        this.repositoryContext = repositoryContext;
        clientMap = new HashMap<>();
    }

    public S3Client getClient(String repositoryName) {
        if (repositoryName == null) {
            return this.getClient(repositoryContext.getActiveRepository());
        }
        return this.getClient(repositoryManager.findByName(repositoryName));
    }

    public synchronized S3Client getClient(RepositoryDefinition repository) {
        if (clientMap.containsKey(repository)) {
            return clientMap.get(repository);
        }

        S3Client client = clientFactory.create(repository);
        clientMap.put(repository, client);

        return client;
    }

    @Override
    public void close() {
        for (S3Client client : clientMap.values()) {
            client.close();
        }
    }
}