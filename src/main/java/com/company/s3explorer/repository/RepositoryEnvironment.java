package com.company.s3explorer.repository;

public enum RepositoryEnvironment {

    DEVELOPMENT("Development"),
    TEST("Test"),
    PILOT("Pilot"),
    PRODUCTION("Production");

    private final String displayName;

    RepositoryEnvironment(
            String displayName) {

        this.displayName =
                displayName;
    }

    @Override
    public String toString() {

        return displayName;
    }
}