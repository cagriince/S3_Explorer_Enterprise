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
}