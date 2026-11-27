package com.batclient.web;

import io.github.nilsen84.ultralight.UltralightFilesystem;
import com.batclient.platform.Platforms;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** JAR-backed UI assets, plus a confined read-only screenshot alias. */
public class BatWebFileSystem implements UltralightFilesystem {
    private final Path webRoot = WebAssetManager.webRoot();
    @Override public boolean exists(String filePath) {
        Path relative = relative(filePath);
        return relative != null && (WebAssetManager.contains(relative.toString()) || screenshot(relative) != null);
    }
    @Override public String getMimeType(String filePath) {
        if (filePath != null) {
            String lower = filePath.toLowerCase(Locale.ROOT);
            int query = lower.indexOf('?');
            if (query >= 0) lower = lower.substring(0, query);
            int hash = lower.indexOf('#');
            if (hash >= 0) lower = lower.substring(0, hash);
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
            if (lower.endsWith(".css")) return "text/css";
            if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".json")) return "application/json";
        }
        String guess = URLConnection.guessContentTypeFromName(filePath);
        return guess == null ? "application/octet-stream" : guess;
    }
    @Override public String getCharset(String filePath) { return StandardCharsets.UTF_8.name(); }
    @Override public byte[] read(String filePath) {
        Path relative = relative(filePath);
        if (relative != null && WebAssetManager.contains(relative.toString())) return WebAssetManager.read(relative.toString());
        Path shot = relative == null ? null : screenshot(relative);
        if (shot == null) throw new IllegalArgumentException("Unknown UI path: " + filePath);
        try { return Files.readAllBytes(shot); }
        catch (IOException e) { throw new IllegalStateException("Could not read screenshot", e); }
    }
    private Path relative(String filePath) {
        if (filePath == null) return null;
        try {
            String normalized = filePath.replace('\\', '/').trim();
            int query = normalized.indexOf('?');
            if (query >= 0) normalized = normalized.substring(0, query);
            int hash = normalized.indexOf('#');
            if (hash >= 0) normalized = normalized.substring(0, hash);
            Path candidate = null;
            if (normalized.startsWith("file:")) {
                try {
                    candidate = Path.of(URI.create(normalized));
                } catch (IllegalArgumentException ignored) {
                    normalized = normalized.replaceFirst("^file:/+", "/");
                }
            }
            if (candidate == null) {
                if (normalized.indexOf('%') >= 0) {
                    try {
                        normalized = URLDecoder.decode(normalized.replace("+", "%2B"), StandardCharsets.UTF_8.name());
                    } catch (IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {}
                }
                if (normalized.matches("^/[A-Za-z]:/.*")) normalized = normalized.substring(1);
                candidate = resolveCandidate(normalized);
            }
            candidate = candidate.toAbsolutePath().normalize();
            return candidate.startsWith(webRoot) ? webRoot.relativize(candidate) : null;
        } catch (RuntimeException e) { return null; }
    }
    private Path resolveCandidate(String normalized) {
        try {
            Path direct = Path.of(normalized);
            if (direct.isAbsolute()) {
                Path abs = direct.toAbsolutePath().normalize();
                if (abs.startsWith(webRoot)) return abs;
            } else {
                Path root = webRoot.getRoot();
                if (root != null) {
                    Path rooted = root.resolve(normalized).toAbsolutePath().normalize();
                    if (rooted.startsWith(webRoot)) return rooted;
                }
            }
        } catch (RuntimeException ignored) {}
        int marker = normalized.lastIndexOf("/.batclient-ui/");
        if (marker >= 0 && !normalized.substring(0, marker).contains("..")) {
            return webRoot.resolve(normalized.substring(marker + "/.batclient-ui/".length()));
        }
        if (normalized.startsWith(".batclient-ui/")) {
            return webRoot.resolve(normalized.substring(".batclient-ui/".length()));
        }
        return webRoot.resolve(normalized);
    }
    private Path screenshot(Path relative) {
        if (relative.getNameCount() != 2 || !relative.getName(0).toString().equals("screenshots")) return null;
        try {
            Path shots = Platforms.get().getGameDir().resolve("screenshots").toRealPath();
            Path shot = shots.resolve(relative.getFileName()).toRealPath();
            return shot.startsWith(shots) && Files.isRegularFile(shot) ? shot : null;
        } catch (IOException e) { return null; }
    }
}
