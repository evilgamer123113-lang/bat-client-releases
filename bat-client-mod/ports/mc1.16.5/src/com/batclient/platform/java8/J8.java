package com.batclient.platform.java8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java 8 drop-in helpers for Java 9-17 stdlib APIs. */
public final class J8 {
    private J8() {}

    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
    }

    public static <T> List<T> listCopyOf(Collection<? extends T> c) {
        return Collections.unmodifiableList(new ArrayList<>(c));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(items)));
    }

    public static <T> Set<T> setCopyOf(Collection<? extends T> c) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(c));
    }

    public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> m) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    public static <T> T requireNonNullElse(T obj, T defaultObj) {
        return obj != null ? obj : java.util.Objects.requireNonNull(defaultObj, "defaultObj");
    }

    public static String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }

    public static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }

    public static boolean isBatLauncherJ8(String launcherPidEnv) {
        String expected = System.getenv(launcherPidEnv);
        if (expected == null || expected.isEmpty()) return false;
        try {
            Class<?> phClass = Class.forName("java.lang.ProcessHandle");
            Object cur = phClass.getMethod("current").invoke(null);
            @SuppressWarnings("unchecked")
            java.util.Optional<Object> parentOpt = (java.util.Optional<Object>) phClass.getMethod("parent").invoke(cur);
            if (!parentOpt.isPresent()) return false;
            Object parent = parentOpt.get();
            long pid = (Long) phClass.getMethod("pid").invoke(parent);
            if (!Long.toString(pid).equals(expected)) return false;
            Object info = phClass.getMethod("info").invoke(parent);
            Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
            @SuppressWarnings("unchecked")
            java.util.Optional<String> cmd = (java.util.Optional<String>) infoClass.getMethod("command").invoke(info);
            if (!cmd.isPresent()) return false;
            String name = java.nio.file.Paths.get(cmd.get()).getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            return name.equals("bat-client-next") || name.equals("bat-client-next.exe");
        } catch (ClassNotFoundException e) {
            // Pure Java 8 fallback via PowerShell/WMIC on Windows
            try {
                String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
                String myPid = jvmName.split("@")[0];
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "$p = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $env:BAT_MY_PID); " +
                    "if ($p) { $pp = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $p.ParentProcessId); " +
                    "if ($pp) { Write-Output ($pp.ProcessId.ToString() + '|' + $pp.Name) } }");
                pb.environment().put("BAT_MY_PID", myPid);
                Process p = pb.start();
                String out = new String(readAllBytes(p.getInputStream()), StandardCharsets.UTF_8).trim();
                p.waitFor();
                String[] parts = out.split("\\|");
                if (parts.length == 2 && parts[0].equals(expected)) {
                    String name = parts[1].toLowerCase(java.util.Locale.ROOT);
                    return name.equals("bat-client-next") || name.equals("bat-client-next.exe");
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return false;
    }

    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... kv) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put((K) kv[i], (V) kv[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static <T> T eval(java.util.function.Supplier<T> s) {
        return s.get();
    }

    public static <K, T> T eval(K key, java.util.function.Function<K, T> fn) {
        return fn.apply(key);
    }

    public static byte[] readNBytes(InputStream in, int len) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[Math.min(8192, Math.max(256, len))];
        int remaining = len;
        while (remaining > 0) {
            int n = in.read(buf, 0, Math.min(buf.length, remaining));
            if (n < 0) break;
            out.write(buf, 0, n);
            remaining -= n;
        }
        return out.toByteArray();
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    public static String readString(Path path) throws IOException {
        return readString(path, StandardCharsets.UTF_8);
    }

    public static String readString(Path path, Charset cs) throws IOException {
        return new String(Files.readAllBytes(path), cs);
    }

    public static Path writeString(Path path, CharSequence text, OpenOption... opts) throws IOException {
        return writeString(path, text, StandardCharsets.UTF_8, opts);
    }

    public static Path writeString(Path path, CharSequence text, Charset cs, OpenOption... opts) throws IOException {
        return Files.write(path, text.toString().getBytes(cs), opts);
    }

    public static final class HttpClient {
        public enum Redirect { NEVER, ALWAYS, NORMAL }
        private final int connectTimeoutMs;
        private final boolean followRedirects;

        private HttpClient(int connectTimeoutMs, boolean followRedirects) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.followRedirects = followRedirects;
        }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private int connectTimeoutMs = 10000;
            private boolean followRedirects = true;
            public Builder connectTimeout(Duration d) { this.connectTimeoutMs = (int) d.toMillis(); return this; }
            public Builder followRedirects(Redirect r) { this.followRedirects = r != Redirect.NEVER; return this; }
            public HttpClient build() { return new HttpClient(connectTimeoutMs, followRedirects); }
        }

        public <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) req.uri.toURL().openConnection();
            conn.setInstanceFollowRedirects(followRedirects);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(req.timeoutMs > 0 ? req.timeoutMs : 15000);
            conn.setRequestMethod(req.method);
            for (Map.Entry<String, String> h : req.headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
            if (req.body != null) {
                conn.setDoOutput(true);
                byte[] bytes = req.body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int status = conn.getResponseCode();
            InputStream raw = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] data = raw != null ? readAllBytes(raw) : new byte[0];
            if (raw != null) raw.close();
            conn.disconnect();
            return new HttpResponse<>(status, handler.handle(data));
        }
    }

    public static final class HttpRequest {
        final URI uri;
        final String method;
        final String body;
        final int timeoutMs;
        final Map<String, String> headers;

        HttpRequest(URI uri, String method, String body, int timeoutMs, Map<String, String> headers) {
            this.uri = uri; this.method = method; this.body = body;
            this.timeoutMs = timeoutMs; this.headers = headers;
        }

        public static Builder newBuilder(URI uri) { return new Builder(uri); }

        public static final class BodyPublishers {
            public static String ofString(String s) { return s; }
        }

        public static final class Builder {
            private final URI uri;
            private String method = "GET";
            private String body;
            private int timeoutMs = 15000;
            private final Map<String, String> headers = new LinkedHashMap<>();

            Builder(URI uri) { this.uri = uri; }
            public Builder timeout(Duration d) { this.timeoutMs = (int) d.toMillis(); return this; }
            public Builder header(String k, String v) { headers.put(k, v); return this; }
            public Builder GET() { this.method = "GET"; this.body = null; return this; }
            public Builder POST(String body) { this.method = "POST"; this.body = body; return this; }
            public HttpRequest build() { return new HttpRequest(uri, method, body, timeoutMs, headers); }
        }
    }

    public static final class HttpResponse<T> {
        private final int statusCode;
        private final T body;

        HttpResponse(int statusCode, T body) {
            this.statusCode = statusCode; this.body = body;
        }

        public int statusCode() { return statusCode; }
        public T body() { return body; }

        public interface BodyHandler<T> {
            T handle(byte[] bytes);
        }

        public static final class BodyHandlers {
            public static BodyHandler<String> ofString() {
                return bytes -> new String(bytes, StandardCharsets.UTF_8);
            }
            public static BodyHandler<byte[]> ofByteArray() {
                return bytes -> bytes;
            }
            public static BodyHandler<InputStream> ofInputStream() {
                return ByteArrayInputStream::new;
            }
        }
    }
}
