package com.company.s3explorer.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigPaths {

    private ConfigPaths() {
    }

    public static final Path APP_HOME = Paths.get(System.getProperty("user.home"),".s3explorer");
    public static final Path REPOSITORIES = APP_HOME.resolve("repositories.json");
}