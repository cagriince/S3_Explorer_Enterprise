package com.company.s3explorer.application;

import com.company.s3explorer.repository.RepositoryDefinition;

public class ActiveRepositoryContext {
    private RepositoryDefinition activeRepository;

    public RepositoryDefinition getActiveRepository() {
        return activeRepository;
    }

    public void setActiveRepository(RepositoryDefinition activeRepository) {
        this.activeRepository = activeRepository;
    }

    public boolean hasActiveRepository() {
        return !activeRepository.isEmpty();
    }
}