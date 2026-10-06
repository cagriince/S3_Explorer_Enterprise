package com.company.s3explorer.repository;

public class RepositoryChangeEvent {

    public enum Type {
        ADD,
        UPDATE,
        REMOVE
    }

    private final Type type;

    private final RepositoryDefinition oldRepository;

    private final RepositoryDefinition newRepository;

    private RepositoryChangeEvent(
            Type type,
            RepositoryDefinition oldRepository,
            RepositoryDefinition newRepository) {

        this.type = type;
        this.oldRepository = oldRepository;
        this.newRepository = newRepository;
    }

    public static RepositoryChangeEvent added(
            RepositoryDefinition repository) {

        return new RepositoryChangeEvent(
                Type.ADD,
                null,
                repository);
    }

    public static RepositoryChangeEvent updated(
            RepositoryDefinition oldRepository,
            RepositoryDefinition newRepository) {

        return new RepositoryChangeEvent(
                Type.UPDATE,
                oldRepository,
                newRepository);
    }

    public static RepositoryChangeEvent removed(
            RepositoryDefinition repository) {

        return new RepositoryChangeEvent(
                Type.REMOVE,
                repository,
                null);
    }

    public Type getType() {
        return type;
    }

    public RepositoryDefinition getOldRepository() {
        return oldRepository;
    }

    public RepositoryDefinition getNewRepository() {
        return newRepository;
    }
}