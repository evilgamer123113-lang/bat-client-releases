package com.batclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** Approximate IP hosting location, cached for a day; all DNS/HTTP runs off-thread. */
public final class ServerLocation {
    public record Info(String country, String code, String city, String isp, Identifier flag, boolean loading) { }
    private static final ConcurrentHashMap<String, Info> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Identifier> FLAGS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Bat-server-location"); thread.setDaemon(true); return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    public static Info get(String address) {
        String key = ServerPreferences.key(address);
        if (CACHE.size() > 512 && !CACHE.containsKey(key)) return new Info("Unavailable", "", "", "", null, false);
        return CACHE.computeIfAbsent(key, value -> {
            WORKER.execute(() -> lookup(value));
            return new Info("Looking up location", "", "", "", null, true);
        });
    }
    private static String str(JsonObject object, String key) { return object.has(key) ? object.get(key).getAsString() : ""; }
    private static void lookup(String address) {
        try {
            JsonObject saved = ServerPreferences.get(address);
            JsonObject geo;
            if (saved.has("geo") && saved.has("geoTime") && System.currentTimeMillis() - saved.get("geoTime").getAsLong() < 86400000) {
                geo = saved.getAsJsonObject("geo");
            } else {
                var resolved = AllowedAddressResolver.DEFAULT.resolve(ServerAddress.parse(address)).orElseThrow();
                var ip = resolved.getInetSocketAddress().getAddress();
                if (ip.isSiteLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isAnyLocalAddress()
                        || ip.isMulticastAddress() || (ip.getAddress().length == 16 && (ip.getAddress()[0] & 0xfe) == 0xfc)) {
                    CACHE.put(address, new Info("Local network", "LAN", "", "", null, false)); return;
                }
                var response = HTTP.send(HttpRequest.newBuilder(URI.create("https://ipwho.is/" + ip.getHostAddress()))
                        .timeout(Duration.ofSeconds(4)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("location unavailable");
                geo = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!geo.has("success") || !geo.get("success").getAsBoolean()) throw new IllegalStateException("location unavailable");
                JsonObject patch = new JsonObject(); patch.add("geo", geo); patch.addProperty("geoTime", System.currentTimeMillis());
                ServerPreferences.put(address, patch);
            }
            String country = str(geo, "country"), code = str(geo, "country_code"), city = str(geo, "city");
            String isp = geo.has("connection") ? str(geo.getAsJsonObject("connection"), "isp") : "";
            CACHE.put(address, new Info(country, code, city, isp, FLAGS.get(code), false));
            if (!code.matches("[A-Z]{2}") || FLAGS.containsKey(code)) return;
            var response = HTTP.send(HttpRequest.newBuilder(URI.create("https://flagcdn.com/w40/" + code.toLowerCase(java.util.Locale.ROOT) + ".png"))
                    .timeout(Duration.ofSeconds(4)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > 64000) return;
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(response.body()));
            MinecraftClient.getInstance().execute(() -> {
                try {
                    Identifier texture = FLAGS.get(code);
                    if (texture == null) {
                        texture = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("bat-server-flag-" + code.toLowerCase(java.util.Locale.ROOT), new NativeImageBackedTexture(image));
                        FLAGS.put(code, texture);
                        BatClientMod.LOGGER.info("[server-location] flag ready: {}", code);
                    } else image.close();
                    Identifier ready = texture;
                    CACHE.replaceAll((host, info) -> info.code().equals(code) ? new Info(info.country(), code, info.city(), info.isp(), ready, false) : info);
                } catch (RuntimeException e) {
                    image.close(); BatClientMod.LOGGER.warn("Could not prepare server flag: {}", e.toString());
                }
            });
        } catch (Exception ignored) {
            CACHE.compute(address, (host, info) -> info != null && !info.loading() ? info : new Info("Location unavailable", "", "", "", null, false));
        }
    }
}
