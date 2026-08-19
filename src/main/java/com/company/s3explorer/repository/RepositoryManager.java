package com.company.s3explorer.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RepositoryManager {

    private final List<RepositoryDefinition> repositories =
            new ArrayList<>();

    private final RepositoryConfigStore store =
            new RepositoryConfigStore();

    private final List<Runnable> repositoryChangeListeners =
            new CopyOnWriteArrayList<>();

    public RepositoryManager() {
        repositories.addAll(store.load());
    }

    public List<RepositoryDefinition> getRepositories() {
        return new ArrayList<>(repositories);
    }

    public void addRepositoryChangeListener(Runnable listener) {
        if (listener != null) {
            repositoryChangeListeners.add(listener);
        }
    }

    public void removeRepositoryChangeListener(Runnable listener) {
        if (listener != null) {
            repositoryChangeListeners.remove(listener);
        }
    }

    private void persist() {
        store.save(repositories);
    }

    private void fireRepositoryChanged() {
        for (Runnable listener : repositoryChangeListeners) {
            try {
                listener.run();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void addRepository(RepositoryDefinition repo) {
        repositories.add(repo);
        persist();
        fireRepositoryChanged();
    }

    public void removeRepository(RepositoryDefinition repo) {
        repositories.remove(repo);
        persist();
        fireRepositoryChanged();
    }

    public void updateRepository(
            RepositoryDefinition oldRepo,
            RepositoryDefinition newRepo) {

        int idx = repositories.indexOf(oldRepo);

        if (idx >= 0) {
            repositories.set(idx, newRepo);
            persist();
            fireRepositoryChanged();
        }
    }

    public RepositoryDefinition findByName(String name) {

        if (name == null) {
            return null;
        }

        RepositoryDefinition repository =
                repositories.stream()
                        .filter(r -> name.equals(r.getName()))
                        .findFirst()
                        .orElse(null);

        return repository == null
                ? RepositoryDefinition.EMPTY_REPOSITORY
                : repository;
    }
}