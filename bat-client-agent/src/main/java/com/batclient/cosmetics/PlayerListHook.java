package com.batclient.cosmetics;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Injected at the start of PlayerListEntry.<init>(GameProfile, boolean) — the
 * exact moment the client builds its tab-list entry for a remote player, long
 * before that player is rendered. If the GameProfile lacks a "textures" property
 * (vanilla offline servers always strip it), we ask the LOCAL yggdrasil skin server
 * for the profile of that UUID. The local server proxies real Bat Client users from
 * the hosted skin API and rewrites their texture URLs to 127.0.0.1 (an allowed
 * skin domain), so the game's PlayerSkinProvider picks the skin up on first render.
 *
 * Fetches run in the background with a bounded 250 ms constructor wait.
 * Legacy mutable profiles can receive a late injection. Authlib 7+ uses
 * immutable records/PropertyMaps, so a ready result becomes a replacement
 * constructor argument; late results are cached for the next tab entry.
 * Existing server/premium textures are never replaced.
 */
public class PlayerListHook {

    public static volatile String apiRoot = "";

    private static final long CACHE_TTL_MS = 5 * 60_000L;
    @SuppressWarnings("unused")
    private static final long WAIT_NS = 250_000_000L;
    /** uuid(lowercase, no dashes) -> {timestampMillis, 1=hasTextures, 0=known miss} */
    private static final Map<String, long[]> CACHE = new ConcurrentHashMap<String, long[]>();
    private static final Map<String, String[]> CACHED_VALUES = new ConcurrentHashMap<String, String[]>();
    private static final Map<String, Object> IN_FLIGHT = new ConcurrentHashMap<String, Object>();
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bat-playerlist-skin");
        t.setDaemon(true);
        return t;
    });

    private PlayerListHook() {
    }

    /** Called from the ASM-injected preamble; returns the profile the entry stores. */
    public static Object prepareProfile(Object profile) {
        String root = apiRoot;
        if (profile == null || root == null || root.isEmpty()) return profile;
        try {
            Object props = access(profile, "getProperties", "properties");
            if (props == null) return profile;
            // PropertyMap (authlib) extends ForwardingMultimap, NOT java.util.Map.
            // Use reflection so we work regardless of the actual collection type.
            Method containsKey = props.getClass().getMethod("containsKey", Object.class);
            if (Boolean.TRUE.equals(containsKey.invoke(props, "textures"))) {
                AgentMain.log("[playerhook] " + safeName(profile) + " already has textures, skip");
                return profile;
            }

            Object id = access(profile, "getId", "id");
            if (id == null) return profile;
            String uuid = id.toString().replace("-", "").toLowerCase();
            AgentMain.log("[playerhook] " + safeName(profile) + " (" + uuid + ") missing textures -> fetch");

            long now = System.currentTimeMillis();
            long[] cached = CACHE.get(uuid);
            if (cached != null && now - cached[0] < CACHE_TTL_MS) {
                return cached[1] == 1 ? applyProfile(profile, props, CACHED_VALUES.get(uuid)) : profile;
            }

            if (IN_FLIGHT.putIfAbsent(uuid, props) != null) return profile;

            CountDownLatch done = new CountDownLatch(1);
            POOL.execute(() -> {
                try {
                    String[] v = fetchProfile(root, uuid);
                    AgentMain.log("[playerhook] fetch " + uuid + " -> " + (v != null ? "HIT" : "miss"));
                    CACHE.put(uuid, new long[]{System.currentTimeMillis(), v != null ? 1 : 0});
                    if (v != null) {
                        CACHED_VALUES.put(uuid, v);
                        try { profile.getClass().getMethod("getProperties"); inject(props, v); } catch (NoSuchMethodException modern) { /* immutable; applied on constructor return or next entry */ }
                        AgentMain.log("[playerhook] cached textures for " + uuid + " (" + (v[1].length() > 0 ? "signed" : "unsigned") + ")");
                    }
                } finally {
                    IN_FLIGHT.remove(uuid);
                    done.countDown();
                }
            });
            try {
                done.await(250, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return applyProfile(profile, props, CACHED_VALUES.get(uuid));
        } catch (Throwable t) {
            if (REPORTED.add(profile.getClass())) AgentMain.log("[playerhook] unsupported profile: " + t);
            return profile;
        }
    }

    private static final java.util.Set<Class<?>> REPORTED = java.util.Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    private static Object access(Object value, String legacy, String modern) throws Exception {
        Method getter;
        try { getter = value.getClass().getMethod(legacy); }
        catch (NoSuchMethodException missing) { getter = value.getClass().getMethod(modern); }
        return getter.invoke(value);
    }

    /** Authlib 7 profiles and their PropertyMaps are immutable: return a new
     * profile before PlayerListEntry stores it. Never mutate EMPTY or premium textures. */
    private static Object applyProfile(Object profile, Object props, String[] values) throws Exception {
        if (values == null || Boolean.TRUE.equals(props.getClass().getMethod("containsKey",Object.class).invoke(props,"textures"))) return profile;
        try { profile.getClass().getMethod("getProperties"); inject(props, values); return profile; }
        catch (NoSuchMethodException modern) {
            ClassLoader loader=props.getClass().getClassLoader();
            Class<?> multimap=Class.forName("com.google.common.collect.Multimap",false,loader);
            Class<?> hash=Class.forName("com.google.common.collect.HashMultimap",false,loader);
            Object copy=hash.getMethod("create",multimap).invoke(null,props);
            Class<?> property=Class.forName("com.mojang.authlib.properties.Property",false,loader);
            Object texture=property.getConstructor(String.class,String.class,String.class).newInstance("textures",values[0],values[1]);
            multimap.getMethod("put",Object.class,Object.class).invoke(copy,"textures",texture);
            Object replacement=props.getClass().getConstructor(multimap).newInstance(copy);
            return profile.getClass().getConstructor(java.util.UUID.class,String.class,props.getClass()).newInstance(access(profile,"getId","id"),access(profile,"getName","name"),replacement);
        }
    }

    private static String safeName(Object profile) {
        try {
            Object n = access(profile, "getName", "name");
            return n == null ? "?" : n.toString();
        } catch (Throwable t) {
            return "?";
        }
    }

    /** Inject the textures property (value + signature) into the profile's PropertyMap. */
    private static void inject(Object props, String[] v) {
        try {
            if (v == null) return;
            // authlib lives in the game's classloader (Fabric isolates it), so load
            // Property from the same loader that loaded the PropertyMap instance.
            ClassLoader cl = props.getClass().getClassLoader();
            Class<?> propCls = Class.forName("com.mojang.authlib.properties.Property", false, cl);
            Object property = propCls.getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", v[0], v[1]);
            // PropertyMap is a Multimap: put(key, value) via reflection.
            Method put = props.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(props, "textures", property);
        } catch (Throwable ignored) {
        }
    }

    /** GET {apiRoot}/sessionserver/session/minecraft/profile/{uuid}; return textures value/signature or null. */
    private static String[] fetchProfile(String root, String uuid) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(root + "/sessionserver/session/minecraft/profile/" + uuid).openConnection();
            conn.setConnectTimeout(300);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            int code = conn.getResponseCode();
            if (code != 200) return null;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            String json = new String(out.toByteArray(), "UTF-8");
            return parseTextures(json);
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Minimal JSON scan for the textures property: {"name":"textures","value":"...","signature":"..."}. */
    private static String[] parseTextures(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"name\":\"textures\"");
        if (idx < 0) return null;
        String value = readQuoted(json, idx, "value");
        String signature = readQuoted(json, idx, "signature");
        if (value == null) return null;
        return new String[]{value, signature == null ? "" : signature};
    }

    private static String readQuoted(String s, int from, String key) {
        int k = s.indexOf("\"" + key + "\"", from);
        if (k < 0) return null;
        int c = s.indexOf(':', k);
        if (c < 0) return null;
        int q = s.indexOf('"', c);
        if (q < 0) return null;
        int end = s.indexOf('"', q + 1);
        if (end < 0) return null;
        return s.substring(q + 1, end);
    }
}