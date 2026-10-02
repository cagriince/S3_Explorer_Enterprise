package com.company.s3explorer.util;

import java.net.*;
import java.util.List;
import java.util.Locale;

public class ProxyConfigurer {
    public static void configureSystemProxies() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        if (osName.contains("win")) {
            configureWindowsProxies();
        } else {
            configureUnixProxies();
        }
    }

    /**
     * WINDOWS: Windows Registry ve sistem seviyesindeki proxy ayarlarını çeker.
     */
    private static void configureWindowsProxies() {
        // Java'nın yerel Windows (WinINet) API proxy sürücüsünü aktif eder
        System.setProperty("java.net.useSystemProxies", "true");

        // Windows Registry'deki nonProxyHosts ayarlarını sorgular (örn: <local>;*.example.com)
        // Eğer sistem seviyesinde manuel tanımlama gerekirse:
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(new URI("http://www.google.com"));
            for (Proxy proxy : proxies) {
                if (proxy.type() == Proxy.Type.HTTP) {
                    InetSocketAddress addr = (InetSocketAddress) proxy.address();
                    System.setProperty("http.proxyHost", addr.getHostString());
                    System.setProperty("http.proxyPort", String.valueOf(addr.getPort()));
                    System.setProperty("https.proxyHost", addr.getHostString());
                    System.setProperty("https.proxyPort", String.valueOf(addr.getPort()));
                    System.out.println("[Windows Proxy] Sistem proxy'si algılandı: " + addr);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[Windows Proxy] Ayarlar okunurken hata: " + e.getMessage());
        }

        // Windows "<local>" temsilini Java formatına ekle
        String existingBypass = System.getProperty("http.nonProxyHosts", "");
        if (existingBypass.isBlank()) {
            System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1|<local>");
        }
    }

    /**
     * LINUX / MACOS: Ortam değişkenlerini (http_proxy, NO_PROXY) okur ve dönüştürür.
     */
    private static void configureUnixProxies() {
        // 1. HTTP / HTTPS Proxy
        String httpProxy = getEnvValue("http_proxy", "HTTP_PROXY");
        if (httpProxy != null && !httpProxy.isBlank()) {
            setProxyUrlProperties(httpProxy, "http");
        }

        String httpsProxy = getEnvValue("https_proxy", "HTTPS_PROXY");
        if (httpsProxy != null && !httpsProxy.isBlank()) {
            setProxyUrlProperties(httpsProxy, "https");
        }

        // 2. NO_PROXY (Bypass Listesi)
        String noProxy = getEnvValue("no_proxy", "NO_PROXY");
        if (noProxy != null && !noProxy.isBlank()) {
            String javaNonProxy = convertNoProxyToJavaFormat(noProxy);
            System.setProperty("http.nonProxyHosts", javaNonProxy);
            System.out.println("[Unix Proxy] http.nonProxyHosts set edildi: " + javaNonProxy);
        }
    }

    // --- YARDIMCI METOTLAR ---

    private static void setProxyUrlProperties(String proxyUrlString, String protocol) {
        try {
            if (!proxyUrlString.startsWith("http://") && !proxyUrlString.startsWith("https://")) {
                proxyUrlString = "http://" + proxyUrlString;
            }

            URI uri = new URI(proxyUrlString);
            if (uri.getHost() != null) {
                System.setProperty(protocol + ".proxyHost", uri.getHost());
                System.out.println("[Unix Proxy] " + protocol + ".proxyHost set edildi: " + uri.getHost());
            }

            int port = uri.getPort() != -1 ? uri.getPort() : (protocol.equalsIgnoreCase("https") ? 443 : 80);
            System.setProperty(protocol + ".proxyPort", String.valueOf(port));
            System.out.println("[Unix Proxy] " + protocol + ".proxyPort set edildi: " + String.valueOf(port));

            // Basic Auth varsa Authenticator set eder
            if (uri.getUserInfo() != null) {
                String[] userInfo = uri.getUserInfo().split(":", 2);
                String user = userInfo[0];
                String pass = userInfo.length > 1 ? userInfo[1] : "";

                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == RequestorType.PROXY) {
                            return new PasswordAuthentication(user, pass.toCharArray());
                        }
                        return super.getPasswordAuthentication();
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[" + protocol + " Proxy] Hata: " + e.getMessage());
        }
    }

    private static String convertNoProxyToJavaFormat(String noProxy) {
        String[] entries = noProxy.split(",");
        StringBuilder javaFormat = new StringBuilder();

        for (String entry : entries) {
            String item = entry.trim();
            if (item.isEmpty()) continue;

            if (javaFormat.length() > 0) {
                javaFormat.append("|");
            }

            // 1. CIDR kontrolü (Örn: 10.11.0.0/16 -> 10.11.*)
            if (item.contains("/")) {
                item = convertCidrToWildcard(item);
            }
            // 2. Yalın IP adresi mi kontrol et (Örn: 127.0.0.1, 10.11.1.5) -> Olduğu gibi bırak
            else if (item.matches("^[0-9.]+$")) {
                // IP adreslerine dokunma
            }
            // 3. Domain dönüşümü (Örn: .example.com -> *.example.com veya example.com -> *.example.com)
            else {
                if (item.startsWith(".")) {
                    item = "*" + item;
                } else if (!item.startsWith("*")) {
                    item = "*." + item;
                }
            }

            javaFormat.append(item);
        }

        return javaFormat.toString();
    }

    private static String convertCidrToWildcard(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) return cidr;

        String ip = parts[0].trim();
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return ip;
        }

        String[] octets = ip.split("\\.");
        if (octets.length != 4) return ip;

        // Maske uzunluğuna göre doğru oktetleri koru
        if (prefixLength == 16) {
            // 10.11.0.0/16 -> 10.11.*
            return octets[0] + "." + octets[1] + ".*";
        } else if (prefixLength == 24) {
            // 192.168.1.0/24 -> 192.168.1.*
            return octets[0] + "." + octets[1] + "." + octets[2] + ".*";
        } else if (prefixLength == 8) {
            // 10.0.0.0/8 -> 10.*
            return octets[0] + ".*";
        }

        // Ara CIDR blokları için güvenli üst oktet
        if (prefixLength > 16 && prefixLength < 24) {
            return octets[0] + "." + octets[1] + ".*";
        } else if (prefixLength > 8 && prefixLength < 16) {
            return octets[0] + ".*";
        }

        return ip;
    }

    private static String getEnvValue(String lowerKey, String upperKey) {
        String val = System.getenv(lowerKey);
        return (val != null && !val.isBlank()) ? val : System.getenv(upperKey);
    }

    public static void main(String[] args) {
        // Uygulama başlangıcında çağrılır
        ProxyConfigurer.configureSystemProxies();
    }
}