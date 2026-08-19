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

        int idx =
                repositories.indexOf(oldRepo);

        if (idx >= 0) {

            repositories.set(
                    idx,
                    newRepo);

            persist();

            fireRepositoryChanged(newRepo);
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
}