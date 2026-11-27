package io.github.nilsen84.ultralight;
/** Test-only API shape. Never packaged. */
public interface UltralightFilesystem {
    boolean exists(String path);
    String getMimeType(String path);
    String getCharset(String path);
    byte[] read(String path);
}
