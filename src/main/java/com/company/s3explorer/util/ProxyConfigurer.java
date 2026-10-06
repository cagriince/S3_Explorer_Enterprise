package com.company.s3explorer.util;

import com.company.s3explorer.config.ProxySettings;

import java.io.InputStream;
import java.net.*;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class ProxyConfigurer {
    public static void configureSystemProxies(ProxySettings proxySettings) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        if (proxySettings.getMode().equals(ProxySettings.Mode.DO_NOT_USE_PROXY)) {
            clearProxySettings();
        }
        else if (proxySettings.getMode().equals(ProxySettings.Mode.DO_NOT_USE_PROXY)) {
            if (osName.contains("win")) {
                configureWindowsProxies(proxySettings);
            } else {
                configureUnixProxies(proxySettings);
            }
        }
        else if (proxySettings.getMode().equals(ProxySettings.Mode.MANUAL_PROXY_CONFIGURATION)) {
            if (osName.contains("win")) {
                setManuelProxy(proxySettings, convertToApacheNonProxyHosts(proxySettings.getNoProxy()));
            } else {
                setManuelProxy(proxySettings, convertNoProxyToJavaFormat(proxySettings.getNoProxy()));
            }
        }
    }

    /**
     * WINDOWS: Windows Registry ve sistem seviyesindeki proxy ayarlarını çeker.
     */
    private static void configureWindowsProxies(ProxySettings proxySettings) {
        // 1. Native Windows proxy seçim mekanizmasını aktif et
        // Neden? java.net.useSystemProxies=true satırı Java'nın tüm TCP soket
        // kontrolünü Windows 11 WinINet sürücüsüne devreder. Bu satır silindiğinde
        // System.getProperty("http.proxyHost") gibi değerler hala okunabilir kalır
        // ancak alt seviye soketler Windows tarafından manipüle edilmez.
        // System.setProperty("java.net.useSystemProxies", "true");

        // 2. Windows'tan 'nonProxyHosts' / 'NO_PROXY' değerini çek
        String nonProxyHosts = getWindowsNonProxyHosts();

        if (nonProxyHosts != null && !nonProxyHosts.isBlank()) {
            System.setProperty("http.nonProxyHosts", nonProxyHosts);
            System.out.println("[Windows Proxy] http.nonProxyHosts set edildi: " + nonProxyHosts);
        }

        // 3. Etkin Proxy Adresini Algıla ve Set Et
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(new URI("http://www.google.com"));
            for (Proxy proxy : proxies) {
                if (proxy.type() == Proxy.Type.HTTP) {
                    InetSocketAddress addr = (InetSocketAddress) proxy.address();
                    System.setProperty("http.proxyHost", addr.getHostString());
                    System.setProperty("http.proxyPort", String.valueOf(addr.getPort()));
                    System.setProperty("https.proxyHost", addr.getHostString());
                    System.setProperty("https.proxyPort", String.valueOf(addr.getPort()));
                    System.out.println("[Windows Proxy] Aktif Proxy: " + addr);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[Windows Proxy] Proxy adresi sorgulanırken hata: " + e.getMessage());
        }
    }

    /**
     * Windows 11 / 10 üzerinde Proxy Bypass listesini okur ve ApacheHttpClient / Java
     * uyumlu 'http.nonProxyHosts' (|) formatına dönüştürür.
     */
    private static String getWindowsNonProxyHosts() {
        String rawNoProxy = null;

        // 1. Öncelik: Windows Ortam Değişkenleri (NO_PROXY veya no_proxy)
        String envNoProxy = System.getenv("NO_PROXY");
        if (envNoProxy == null || envNoProxy.isBlank()) {
            envNoProxy = System.getenv("no_proxy");
        }

        if (envNoProxy != null && !envNoProxy.isBlank()) {
            rawNoProxy = envNoProxy;
        }
        // 2. İkinci Yol: Windows Registry (WinINet / Internet Settings ProxyOverride)
        else {
            rawNoProxy = readWindowsRegistryProxyOverride();
        }

        // Değer bulunduysa ApacheHttpClient'ın beklediği formata dönüştür
        if (rawNoProxy != null && !rawNoProxy.isBlank()) {
            return convertToApacheNonProxyHosts(rawNoProxy);
        }

        // 3. Fallback (Varsayılan Yerel Adresler)
        return "localhost|127.0.0.1|<local>";
    }

    /**
     * Windows Registry üzerinden 'ProxyOverride' değerini okur.
     */
    private static String readWindowsRegistryProxyOverride() {
        try {
            Process process = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "ProxyOverride"
            ).start();

            try (InputStream is = process.getInputStream();
                 Scanner scanner = new Scanner(is)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("ProxyOverride")) {
                        String[] parts = line.split("REG_SZ");
                        if (parts.length > 1) {
                            return parts[1].trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Windows Registry] ProxyOverride okuma hatası: " + e.getMessage());
        }
        return null;
    }

    /**
     * Windows'tan gelen ham no_proxy değerlerini (virgül, noktalı virgül, CIDR, yıldız)
     * ApacheHttpClient'ın regex motorunu bozmayacak formata dönüştürür.
     */
    private static String convertToApacheNonProxyHosts(String rawNoProxy) {
        String[] entries = rawNoProxy.split("[,;]");
        StringBuilder javaFormat = new StringBuilder();

        for (String entry : entries) {
            String item = entry.trim();
            if (item.isEmpty()) continue;

            if (javaFormat.length() > 0) {
                javaFormat.append("|");
            }

            // 1. IP Yıldız Wildcard'ları (örn: "10.11.*", "10.11.*.*" -> "10.11*")
            // Yıldızdan önceki noktayı silerek Apache'nin çift nokta koymasını engelliyoruz.
            if (item.matches("^[0-9.]+\\*.*$")) {
                String baseIp = item.replaceAll("\\.\\*.*$", ""); // "10.11"
                item = baseIp + "*";                             // "10.11*"
            }
            // 2. CIDR Dönüşümü (10.11.0.0/16 -> "10.11*")
            else if (item.contains("/")) {
                String[] parts = item.split("/");
                String ip = parts[0].trim();
                int prefix = Integer.parseInt(parts[1].trim());
                String[] octets = ip.split("\\.");

                if (prefix == 16 && octets.length >= 2) {
                    item = octets[0] + "." + octets[1] + "*"; // "10.11*"
                } else if (prefix == 24 && octets.length >= 3) {
                    item = octets[0] + "." + octets[1] + "." + octets[2] + "*"; // "192.168.1*"
                } else if (prefix == 8 && octets.length >= 1) {
                    item = octets[0] + "*"; // "10*"
                } else {
                    item = ip;
                }
            }
            // 3. Domain Dönüşümü (*.abc.com -> .abc.com)
            else if (item.startsWith("*.")) {
                item = item.substring(1); // ".abc.com"
            }

            javaFormat.append(item);
        }

        return javaFormat.toString();
    }

    /**
     * LINUX / MACOS: Ortam değişkenlerini (http_proxy, NO_PROXY) okur ve dönüştürür.
     */
    private static void configureUnixProxies(ProxySettings proxySettings) {
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

    private static void setManuelProxy(ProxySettings proxySettings, String noProxyStr) {
        String[] httpProxyStrs = proxySettings.getHttpProxy().split(":");
        String[] httpsProxyStrs = proxySettings.getHttpsProxy().split(":");
        System.setProperty("http.proxyHost", httpProxyStrs[0]);
        System.setProperty("http.proxyPort", (httpProxyStrs.length >= 2 ? httpProxyStrs[1] : "80"));
        System.setProperty("https.proxyHost", httpsProxyStrs[0]);
        System.setProperty("https.proxyPort", (httpsProxyStrs.length >= 2 ? httpsProxyStrs[1] : "443"));
        if (proxySettings.getNoProxy() != null) {
            System.setProperty("http.nonProxyHosts", noProxyStr);
        }
        if (proxySettings.isUseAuthentication()) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    // Yalnızca PROXY türündeki istekler için kimlik doğrula
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(proxySettings.getUsername(), proxySettings.getPassword().toCharArray());
                    }
                    return null;
                }
            });
        }
    }

    private static void clearProxySettings() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("https.nonProxyHosts");
        Authenticator.setDefault(null);
    }

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
            // 2. IP Yıldız Wildcard'ları (örn: "10.11.*", "10.11.*.*" -> "10.11*")
            // Yıldızdan önceki noktayı silerek Apache'nin çift nokta koymasını engelliyoruz.
            else if (item.matches("^[0-9.]+\\*.*$")) {
                String baseIp = item.replaceAll("\\.\\*.*$", ""); // "10.11"
                item = baseIp + "*";                             // "10.11*"
            }
            // 3. Yalın IP adresi mi kontrol et (Örn: 127.0.0.1, 10.11.1.5) -> Olduğu gibi bırak
            else if (item.matches("^[0-9.]+$")) {
                // IP adreslerine dokunma
            }
            // 4. Domain dönüşümü (Örn: .example.com -> *.example.com veya example.com -> *.example.com)
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
}