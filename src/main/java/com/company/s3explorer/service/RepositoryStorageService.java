package com.company.s3explorer.service;

import com.company.s3explorer.config.ConfigPaths;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RepositoryStorageService {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<RepositoryDefinition> load() {
        try {
            if (!Files.exists(ConfigPaths.REPOSITORIES)) {
                return new ArrayList<>();
            }

            return List.of(mapper.readValue(ConfigPaths.REPOSITORIES.toFile(), RepositoryDefinition[].class));
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void save(
        List<RepositoryDefinition> repositories) {
            try {
                Files.createDirectories(ConfigPaths.APP_HOME);
                mapper.writerWithDefaultPrettyPrinter().writeValue(ConfigPaths.REPOSITORIES.toFile(), repositories);
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
}