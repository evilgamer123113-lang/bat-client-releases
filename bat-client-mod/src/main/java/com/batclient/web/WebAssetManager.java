package com.batclient.web;

import com.batclient.platform.Platforms;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Serves bundled UI resources in memory; the URL root is virtual, never extracted. */
public final class WebAssetManager {
    private static final String RESOURCE_ROOT = "/assets/batclient/web/";
    private static final Set<String> WEB_FILES = Set.of(
        "index.html", "overlay.html", "pause.html", "respacks.html",
        "style.css", "hud-theme.css", "app.js", "client-tools.js", "waypoints.js", "bg.png", "bat-logo.png",
        "mod-icons.png", "brand.html", "list-icons.html");
    public static final String WEB_VERSION = "121";
    private static final Map<String, byte[]> ASSETS = new ConcurrentHashMap<>();
    private WebAssetManager() {}

    /** Stable base URL for relative requests. No directory is created. */
    public static Path webRoot() {
        return Platforms.get().getGameDir().toAbsolutePath().normalize().resolve(".batclient-ui");
    }

    public static boolean contains(String name) { return WEB_FILES.contains(name); }

    public static byte[] read(String name) {
        if (!contains(name)) throw new IllegalArgumentException("Unknown UI asset: " + name);
        return ASSETS.computeIfAbsent(name, key -> {
            try (InputStream stream = WebAssetManager.class.getResourceAsStream(RESOURCE_ROOT + key + ".bin")) {
                if (stream == null) throw new IllegalStateException("Missing bundled UI asset: " + key);
                return decrypt(key,stream.readAllBytes());
            } catch (IOException e) {
                throw new IllegalStateException("Could not load UI asset: " + key, e);
            }
        });
    }
    private static byte[] decrypt(String name,byte[] envelope) {
        if(envelope.length<32||envelope[0]!='B'||envelope[1]!='A'||envelope[2]!='T'||envelope[3]!='2')
            throw new IllegalStateException("Invalid protected UI asset: "+name);
        byte[] key=UiAssetKey.bytes();
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,envelope,4,12));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(envelope,16,envelope.length-16);
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Protected UI asset authentication failed: "+name,e);}
        finally{Arrays.fill(key,(byte)0);}
    }
}
