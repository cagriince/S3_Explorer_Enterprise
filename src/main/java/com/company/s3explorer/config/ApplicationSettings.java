package com.company.s3explorer.config;

public class ApplicationSettings {
    private String lastSelectedRepository;
    private String lastSelectedBucket;
    private String lastSelectedTheme;

    private int windowWidth = 1200;
    private int windowHeight = 800;

    private int windowX = -1;
    private int windowY = -1;

    public String getLastSelectedRepository() {
        return lastSelectedRepository;
    }

    public void setLastSelectedRepository(String lastSelectedRepository) {
        this.lastSelectedRepository = lastSelectedRepository;
    }

    public String getLastSelectedBucket() {
        return lastSelectedBucket;
    }

    public void setLastSelectedBucket(String lastSelectedBucket) {
        this.lastSelectedBucket = lastSelectedBucket;
    }

    public String getLastSelectedTheme() {
        return lastSelectedTheme;
    }

    public void setLastSelectedTheme(String lastSelectedTheme) {
        this.lastSelectedTheme = lastSelectedTheme;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public int getWindowX() {
        return windowX;
    }

    public void setWindowX(int windowX) {
        this.windowX = windowX;
    }

    public int getWindowY() {
        return windowY;
    }

    public void setWindowY(int windowY) {
        this.windowY = windowY;
    }
}