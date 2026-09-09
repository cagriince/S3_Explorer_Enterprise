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

    private final List<Consumer<RepositoryDefinition>>
            repositoryChangeListeners =
            new CopyOnWriteArrayList<>();

    public RepositoryManager() {
        repositories.addAll(store.load());
    }

    public List<RepositoryDefinition> getRepositories() {
        return new ArrayList<>(repositories);
    }

    public void addRepositoryChangeListener(
            Consumer<RepositoryDefinition> listener) {

        if (listener != null) {
            repositoryChangeListeners.add(listener);
        }
    }

    public void removeRepositoryChangeListener(
            Consumer<RepositoryDefinition> listener) {

        if (listener != null) {
            repositoryChangeListeners.remove(listener);
        }
    }

    private void persist() {
        store.save(repositories);
    }

    private void fireRepositoryChanged(
            RepositoryDefinition repository) {

        for (Consumer<RepositoryDefinition> listener :
                repositoryChangeListeners) {

            try {
                listener.accept(repository);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void addRepository(
            RepositoryDefinition repo) {

        repositories.add(repo);
        persist();

        fireRepositoryChanged(repo);
    }

    public void removeRepository(
            RepositoryDefinition repo) {

        repositories.remove(repo);
        persist();

        fireRepositoryChanged(repo);
    }

    public void updateRepository(
            RepositoryDefinition oldRepo,
            RepositoryDefinition newRepo) {

        if (oldRepo == null
                || newRepo == null) {
            throw new IllegalArgumentException(
                    "Repository must not be null");
        }

        String newName =
                newRepo.getName() == null
                        ? ""
                        : newRepo.getName().trim();

        if (newName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Repository name must not be empty");
        }

        boolean nameExists =
                repositories.stream()
                        .anyMatch(repository ->
                                !repository.equals(oldRepo)
                                        && newName.equals(
                                        repository.getName()));

        if (nameExists) {
            throw new IllegalArgumentException(
                    "A repository with the name \""
                            + newName
                            + "\" already exists.");
        }

        newRepo.setName(newName);

        int idx =
                repositories.indexOf(oldRepo);

        if (idx >= 0) {

            repositories.set(
                    idx,
                    newRepo);

            persist();

            fireRepositoryChanged(
                    newRepo);
        }
    }
    
    public RepositoryDefinition findByName(
            String name) {

        if (name == null) {
            return null;
        }

        RepositoryDefinition repository =
                repositories.stream()
                        .filter(r ->
                                name.equals(
                                        r.getName()))
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

        boolean nameExists =
                repositories.stream()
                        .anyMatch(repository ->
                                name.equals(
                                        repository.getName()));

        if (nameExists) {
            throw new IllegalArgumentException(
                    "A repository with the name \""
                            + name
                            + "\" already exists.");
        }

        RepositoryDefinition duplicate =
                new RepositoryDefinition();

        duplicate.setName(name);
        duplicate.setEndpoint(
                source.getEndpoint());
        duplicate.setAccessKey(
                source.getAccessKey());
        duplicate.setSecretKey(
                source.getSecretKey());
        duplicate.setExternalBuckets(
                source.getExternalBuckets());

        repositories.add(duplicate);

        persist();

        fireRepositoryChanged(
                duplicate);

        return duplicate;
    }
}