package com.batclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Networking runs off the render/tick threads; rendering reads only a cached snapshot. */
public final class ClientPresence {
    private static final boolean TEST_NO_PRESENCE=Boolean.getBoolean("batclient.test.noPresence");
    private static final String API = "https://batclient.xyz/skinapi/api/client-presence";
    private static final String SESSION = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Bat-client-presence"); thread.setDaemon(true); return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile Map<UUID, Long> active = Map.of();
    private static volatile long generation;
    private static UUID local;
    private static long nextPoll;
    private static boolean lastShare = true;

    public static boolean isBatPlayer(UUID uuid) {
        return active.getOrDefault(uuid, 0L) > System.nanoTime();
    }

    public static void tick(MinecraftClient client) {
        if(TEST_NO_PRESENCE)return;
        UUID current = client.world != null && client.player != null ? client.player.getUuid() : null;
        if (!java.util.Objects.equals(current, local)) {
            UUID previous = local; local = current; generation++; nextPoll = 0; active = Map.of();
            if (previous != null) WORKER.execute(() -> leave(previous));
        }
        if (current == null) return;
        JsonObject module = HudConfig.module(HudConfig.snapshot(), "nametags");
        boolean share = HudConfig.bool(module, "share", true);
        if (share != lastShare) { lastShare = share; nextPoll = 0; }
        long now = System.nanoTime();
        if (now < nextPoll || !BUSY.compareAndSet(false, true)) return;
        nextPoll = now + 15_000_000_000L;
        JsonObject request = identity(current);
        request.addProperty("share", share);
        JsonArray peers = new JsonArray();
        client.world.getPlayers().stream().limit(128).forEach(player -> peers.add(player.getUuid().toString()));
        request.add("peers", peers);
        long version = generation;
        WORKER.execute(() -> {
            try {
                JsonObject response = post(API, request);
                Map<UUID, Long> found = new HashMap<>();
                long received = System.nanoTime();
                for (var entry : response.getAsJsonObject("active").entrySet()) {
                    String key = entry.getKey();
                    UUID id = UUID.fromString(key.substring(0, 8) + "-" + key.substring(8, 12) + "-" + key.substring(12, 16) + "-" + key.substring(16, 20) + "-" + key.substring(20));
                    long ttl = Math.max(0, Math.min(60000, entry.getValue().getAsLong()));
                    found.put(id, received + ttl * 1_000_000L);
                }
                if (version == generation) active = Map.copyOf(found);
            } catch (Exception ignored) {
                // An unavailable registry must never interrupt gameplay. Cached leases expire.
            } finally { BUSY.set(false); }
        });
    }

    private static JsonObject identity(UUID uuid) {
        JsonObject request = new JsonObject();
        request.addProperty("uuid", uuid.toString()); request.addProperty("session", SESSION);
        return request;
    }

    private static JsonObject post(String url, JsonObject request) throws Exception {
        HttpRequest message = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(request.toString())).build();
        var response = HTTP.send(message, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("presence unavailable");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void leave(UUID uuid) {
        try { post(API + "/leave", identity(uuid)); } catch (Exception ignored) { }
    }

    public static void stop() {
        UUID previous = local; local = null; generation++; active = Map.of();
        if (previous != null) WORKER.execute(() -> leave(previous));
        WORKER.shutdown();
    }
}
