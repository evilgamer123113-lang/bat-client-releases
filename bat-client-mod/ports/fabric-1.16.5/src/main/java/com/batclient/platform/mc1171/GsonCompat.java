package com.batclient.platform.mc1171;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Gson 2.8.0 compatibility helpers for Minecraft 1.17.1 / 1.16.5. */
public final class GsonCompat {
    private GsonCompat() {}

    public static JsonElement parse(String json) {
        return new JsonParser().parse(json);
    }

    public static JsonObject copy(JsonObject obj) {
        return obj == null ? null : new JsonParser().parse(obj.toString()).getAsJsonObject();
    }

    public static JsonArray copy(JsonArray arr) {
        return arr == null ? null : new JsonParser().parse(arr.toString()).getAsJsonArray();
    }

    public static JsonElement copy(JsonElement el) {
        return el == null ? null : new JsonParser().parse(el.toString());
    }

    public static Set<String> keys(JsonObject obj) {
        Set<String> set = new LinkedHashSet<>();
        if (obj != null) {
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                set.add(e.getKey());
            }
        }
        return set;
    }
}
