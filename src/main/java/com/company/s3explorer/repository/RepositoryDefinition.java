package com.company.s3explorer.repository;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public class RepositoryDefinition {
    public static RepositoryDefinition EMPTY_REPOSITORY = new RepositoryDefinition(null, null, null, null);

    private String name;
    private String endpoint;
    private String accessKey;
    private String secretKey;

    public RepositoryDefinition() {
    }

    public RepositoryDefinition(
            String name,
            String endpoint,
            String accessKey,
            String secretKey) {

        this.name = name;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepositoryDefinition that = (RepositoryDefinition) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                endpoint,
                accessKey);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return getName() == null;
    }
}