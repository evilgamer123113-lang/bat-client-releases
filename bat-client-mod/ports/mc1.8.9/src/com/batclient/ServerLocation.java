package com.batclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ServerAddress;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Approximate IP hosting location, cached for a day; all DNS/HTTP runs off-thread. */
public final class ServerLocation {
    public static final class Info {
        private final String country, code, city, isp;
        private final Identifier flag;
        private final boolean loading;
        public Info(String country, String code, String city, String isp, Identifier flag, boolean loading) {
            this.country = country; this.code = code; this.city = city; this.isp = isp; this.flag = flag; this.loading = loading;
        }
        public String country() { return country; }
        public String code() { return code; }
        public String city() { return city; }
        public String isp() { return isp; }
        public Identifier flag() { return flag; }
        public boolean loading() { return loading; }
    }

    private static final ConcurrentHashMap<String, Info> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Identifier> FLAGS = new ConcurrentHashMap<>();
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Bat-server-location"); thread.setDaemon(true); return thread;
    });

    public static Info get(String address) {
        String key = ServerPreferences.key(address);
        if (CACHE.size() > 512 && !CACHE.containsKey(key)) return new Info("Unavailable", "", "", "", null, false);
        return CACHE.computeIfAbsent(key, value -> {
            WORKER.execute(() -> lookup(value));
            return new Info("Looking up location", "", "", "", null, true);
        });
    }

    private static String str(JsonObject object, String key) { return object.has(key) ? object.get(key).getAsString() : ""; }

    private static byte[] httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setRequestProperty("User-Agent", "BatClient");
        if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conn.getResponseCode());
        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void lookup(String address) {
        try {
            JsonObject saved = ServerPreferences.get(address);
            JsonObject geo;
            if (saved.has("geo") && saved.has("geoTime") && System.currentTimeMillis() - saved.get("geoTime").getAsLong() < 86400000) {
                geo = saved.getAsJsonObject("geo");
            } else {
                InetAddress ip = InetAddress.getByName(ServerAddress.parse(address).getAddress());
                if (ip.isSiteLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isAnyLocalAddress()
                        || ip.isMulticastAddress() || (ip.getAddress().length == 16 && (ip.getAddress()[0] & 0xfe) == 0xfc)) {
                    CACHE.put(address, new Info("Local network", "LAN", "", "", null, false)); return;
                }
                String body = new String(httpGet("https://ipwho.is/" + ip.getHostAddress()), StandardCharsets.UTF_8);
                geo = new JsonParser().parse(body).getAsJsonObject();
                if (!geo.has("success") || !geo.get("success").getAsBoolean()) throw new IllegalStateException("location unavailable");
                JsonObject patch = new JsonObject(); patch.add("geo", geo); patch.addProperty("geoTime", System.currentTimeMillis());
                ServerPreferences.put(address, patch);
            }
            String country = str(geo, "country"), code = str(geo, "country_code"), city = str(geo, "city");
            String isp = geo.has("connection") ? str(geo.getAsJsonObject("connection"), "isp") : "";
            CACHE.put(address, new Info(country, code, city, isp, FLAGS.get(code), false));
            if (!code.matches("[A-Z]{2}") || FLAGS.containsKey(code)) return;
            byte[] png = httpGet("https://flagcdn.com/w40/" + code.toLowerCase(java.util.Locale.ROOT) + ".png");
            if (png.length > 64000) return;
            final BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image == null) return;
            MinecraftClient.getInstance().submit(() -> {
                try {
                    Identifier texture = FLAGS.get(code);
                    if (texture == null) {
                        texture = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("bat-server-flag-" + code.toLowerCase(java.util.Locale.ROOT), new NativeImageBackedTexture(image));
                        FLAGS.put(code, texture);
                        BatClientMod.LOGGER.info("[server-location] flag ready: {}", code);
                    }
                    Identifier ready = texture;
                    CACHE.replaceAll((host, info) -> info.code().equals(code) ? new Info(info.country(), code, info.city(), info.isp(), ready, false) : info);
                } catch (RuntimeException e) {
                    BatClientMod.LOGGER.warn("Could not prepare server flag: {}", e.toString());
                }
            });
        } catch (Exception ignored) {
            CACHE.compute(address, (host, info) -> info != null && !info.loading() ? info : new Info("Location unavailable", "", "", "", null, false));
        }
    }
}
