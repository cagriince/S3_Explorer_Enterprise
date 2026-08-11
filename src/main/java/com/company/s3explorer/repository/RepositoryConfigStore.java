package com.company.s3explorer.repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RepositoryConfigStore {
    private final File file;
    private final ObjectMapper mapper = new ObjectMapper();

    public RepositoryConfigStore() {
        this.file = new File(System.getProperty("user.home"), ".s3explorer/repositories.json");
    }

    public List<RepositoryDefinition> load() {
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try {
            return mapper.readValue(file, mapper.getTypeFactory().constructCollectionType(List.class, RepositoryDefinition.class));
        } catch (Exception e) {
            throw new RuntimeException("Cannot load repositories", e);
        }
    }

    public void save(List<RepositoryDefinition> repos) {
        try {
            file.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, repos);
        } catch (Exception e) {
            throw new RuntimeException("Cannot save repositories", e);
        }
    }
}