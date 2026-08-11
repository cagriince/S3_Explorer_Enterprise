package com.company.s3explorer.repository;

import java.util.ArrayList;
import java.util.List;

public class RepositoryManager {
    private final List<RepositoryDefinition> repositories = new ArrayList<>();
    private final RepositoryConfigStore store = new RepositoryConfigStore();

    public RepositoryManager() {
        repositories.addAll(store.load());
    }

    public List<RepositoryDefinition> getRepositories() {
        return new ArrayList<>(repositories);
    }

    private void persist() {
        store.save(repositories);
    }

    public void addRepository(RepositoryDefinition repo) {
        repositories.add(repo);
        persist();
    }

    public void removeRepository(RepositoryDefinition repo) {
        repositories.remove(repo);
        persist();
    }

    public void updateRepository(RepositoryDefinition oldRepo, RepositoryDefinition newRepo) {
        int idx = repositories.indexOf(oldRepo);
        if (idx >= 0) {
            repositories.set(idx, newRepo);
            persist();
        }
    }

    public RepositoryDefinition findByName(String name) {
        if (name == null) {
            return null;
        }

        RepositoryDefinition repository = repositories.stream()
                .filter(r -> name.equals(r.getName()))
                .findFirst()
                .orElse(null);
        return repository == null ? RepositoryDefinition.EMPTY_REPOSITORY : repository;
    }
}