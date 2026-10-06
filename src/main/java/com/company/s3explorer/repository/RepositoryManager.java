package com.company.s3explorer.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class RepositoryManager {

    private final List<RepositoryDefinition> repositories =
            new ArrayList<>();

    private final RepositoryConfigStore store =
            new RepositoryConfigStore();

    private final List<Consumer<RepositoryChangeEvent>>
            repositoryChangeListeners =
            new CopyOnWriteArrayList<>();

    public RepositoryManager() {
        repositories.addAll(
                store.load());
    }

    public List<RepositoryDefinition> getRepositories() {
        return new ArrayList<>(
                repositories);
    }

    public void addRepositoryChangeListener(
            Consumer<RepositoryChangeEvent> listener) {

        if (listener != null) {
            repositoryChangeListeners.add(
                    listener);
        }
    }

    public void removeRepositoryChangeListener(
            Consumer<RepositoryChangeEvent> listener) {

        if (listener != null) {
            repositoryChangeListeners.remove(
                    listener);
        }
    }

    private void persist() {
        store.save(
                repositories);
    }

    private void fireRepositoryChanged(
            RepositoryChangeEvent event) {

        for (Consumer<RepositoryChangeEvent> listener :
                repositoryChangeListeners) {

            try {

                listener.accept(
                        event);

            } catch (Exception ex) {

                ex.printStackTrace();
            }
        }
    }

    public void addRepository(
            RepositoryDefinition repo) {

        repositories.add(
                repo);

        persist();

        fireRepositoryChanged(
                RepositoryChangeEvent.added(
                        repo));
    }

    public void removeRepository(
            RepositoryDefinition repo) {

        repositories.remove(
                repo);

        persist();

        fireRepositoryChanged(
                RepositoryChangeEvent.removed(
                        repo));
    }

    public void updateRepository(
            RepositoryDefinition oldRepo,
            RepositoryDefinition newRepo) {

        if (oldRepo == null
                || newRepo == null) {

            throw new IllegalArgumentException(
                    "Repository must not be null");
        }

        boolean repositoryExists =
                repositories.stream()
                        .anyMatch(repository ->
                                !repository.equals(oldRepo)
                                        && repository.equals(newRepo));

        if (repositoryExists) {

            throw new IllegalArgumentException(
                    "A repository with the name and environment \""
                            + newRepo.getId()
                            + "\" already exists.");
        }

        int idx =
                repositories.indexOf(
                        oldRepo);

        if (idx >= 0) {

            repositories.set(
                    idx,
                    newRepo);

            persist();

            fireRepositoryChanged(
                    RepositoryChangeEvent.updated(
                            oldRepo,
                            newRepo));
        }
    }

    public RepositoryDefinition findById(
            String id) {

        if (id == null) {
            return null;
        }

        RepositoryDefinition repository =
                repositories.stream()
                        .filter(r ->
                                id.equals(
                                        r.getId()))
                        .findFirst()
                        .orElse(null);

        return repository == null
                ? RepositoryDefinition.EMPTY_REPOSITORY
                : repository;
    }

    public RepositoryDefinition duplicateRepository(
            RepositoryDefinition source,
            String newName) {

        if (source == null) {

            throw new IllegalArgumentException(
                    "Source repository must not be null");
        }

        if (newName == null
                || newName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Repository name must not be empty");
        }

        String name =
                newName.trim();

        boolean repositoryExists =
                repositories.stream()
                        .anyMatch(repository ->
                                newName.equals(
                                        repository.getName())
                                        && source.getEnvironment()
                                        .equals(repository.getEnvironment()));

        if (repositoryExists) {

            throw new IllegalArgumentException(
                    "A repository with the name and environment \""
                            + newName
                            + " - "
                            + source.getEnvironment()
                            + "\" already exists.");
        }

        RepositoryDefinition duplicate =
                new RepositoryDefinition(
                        name,
                        source.getEnvironment());

        duplicate.setEndpoint(
                source.getEndpoint());

        duplicate.setAccessKey(
                source.getAccessKey());

        duplicate.setSecretKey(
                source.getSecretKey());

        duplicate.setExternalBuckets(
                source.getExternalBuckets());

        duplicate.setEncryptionTransformation(
                source.getEncryptionTransformation());

        duplicate.setEncryptionIv(
                source.getEncryptionIv());

        duplicate.setEncryptionKey(
                source.getEncryptionKey());

        repositories.add(
                duplicate);

        persist();

        fireRepositoryChanged(
                RepositoryChangeEvent.added(
                        duplicate));

        return duplicate;
    }
}