package com.company.s3explorer.config;

public class ProxySettings {

    public enum Mode {

        DO_NOT_USE_PROXY(
                "Do not use proxy"),

        AUTODETECT_PROXY_CONFIGURATION(
                "Autodetect proxy configuration"),

        MANUAL_PROXY_CONFIGURATION(
                "Manual proxy configuration");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private Mode mode =
            Mode.DO_NOT_USE_PROXY;

    private String httpProxy;

    private String httpsProxy;

    private String noProxy;

    private boolean useAuthentication;

    private String username;

    private String password;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getHttpProxy() {
        return httpProxy;
    }

    public void setHttpProxy(String httpProxy) {
        this.httpProxy = httpProxy;
    }

    public String getHttpsProxy() {
        return httpsProxy;
    }

    public void setHttpsProxy(String httpsProxy) {
        this.httpsProxy = httpsProxy;
    }

    public String getNoProxy() {
        return noProxy;
    }

    public void setNoProxy(String noProxy) {
        this.noProxy = noProxy;
    }

    public boolean isUseAuthentication() {
        return useAuthentication;
    }

    public void setUseAuthentication(
            boolean useAuthentication) {

        this.useAuthentication =
                useAuthentication;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(
            String username) {

        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(
            String password) {

        this.password = password;
    }
}