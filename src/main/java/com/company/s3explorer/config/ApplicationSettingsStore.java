package com.company.s3explorer.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ApplicationSettingsStore {

    private static final Path FILE = new File(System.getProperty("user.home"), ".s3explorer/application.json").toPath();

    private final ObjectMapper mapper = new ObjectMapper();

    public ApplicationSettings load() {
        try {
            if (Files.notExists(FILE)) {
                return new ApplicationSettings();
            }
            return mapper.readValue(FILE.toFile(), ApplicationSettings.class);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ApplicationSettings();
        }
    }

    public void save(ApplicationSettings settings) {
        try {
            Files.createDirectories(FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(FILE.toFile(), settings);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
