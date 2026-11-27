package com.batclient.hud.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Root JSON-serializable layout document.
 *
 * {
 *   "canvas": {
 *     "base_width": 1920, "base_height": 1080,
 *     "global_gui_scale": 1.0, "global_corner_radius": 4.0
 *   },
 *   "modules": [ HudComponentState, ... ]
 * }
 *
 * Pure data — no rendering, no IO. Persistence lives in HudConfig; this class
 * only owns the typed model + (de)serialization.
 */
public final class HudLayoutConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final float DEFAULT_BASE_W = 1920f;
	public static final float DEFAULT_BASE_H = 1080f;

	public Canvas canvas = new Canvas();
	public List<HudComponentState> modules = new ArrayList<>();

	public static final class Canvas {
		public float base_width = DEFAULT_BASE_W;
		public float base_height = DEFAULT_BASE_H;
		public float global_gui_scale = 1.0f;
		public float global_corner_radius = 4.0f;
	}

	public HudComponentState find(String id) {
		if (id == null) return null;
		for (HudComponentState m : modules) {
			if (id.equals(m.id)) return m;
		}
		return null;
	}

	public HudComponentState findOrCreate(String id) {
		HudComponentState m = find(id);
		if (m == null) {
			m = new HudComponentState(id);
			modules.add(m);
		}
		return m;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public static HudLayoutConfig fromJson(String json) {
		if (json == null || json.isEmpty()) return new HudLayoutConfig();
		try {
			HudLayoutConfig cfg = GSON.fromJson(json, HudLayoutConfig.class);
			if (cfg == null) return new HudLayoutConfig();
			if (cfg.canvas == null) cfg.canvas = new Canvas();
			if (cfg.modules == null) cfg.modules = new ArrayList<>();
			return cfg;
		} catch (Throwable t) {
			return new HudLayoutConfig();
		}
	}

	/**
	 * Parse the modern schema from a raw JsonObject. Tolerates a missing
	 * canvas block and a modules JSON array. Returns an empty config on junk.
	 */
	public static HudLayoutConfig fromJsonObject(JsonObject obj) {
		HudLayoutConfig cfg = new HudLayoutConfig();
		if (obj == null) return cfg;
		if (obj.has("canvas") && obj.get("canvas").isJsonObject()) {
			JsonObject c = obj.getAsJsonObject("canvas");
			cfg.canvas.base_width = num(c, "base_width", DEFAULT_BASE_W);
			cfg.canvas.base_height = num(c, "base_height", DEFAULT_BASE_H);
			cfg.canvas.global_gui_scale = num(c, "global_gui_scale", 1f);
			cfg.canvas.global_corner_radius = num(c, "global_corner_radius", 4f);
		}
		if (obj.has("modules") && obj.get("modules").isJsonArray()) {
			JsonArray arr = obj.getAsJsonArray("modules");
			for (JsonElement el : arr) {
				if (!el.isJsonObject()) continue;
				JsonObject m = el.getAsJsonObject();
				HudComponentState st = new HudComponentState();
				st.id = str(m, "id", null);
				st.enabled = bool(m, "enabled", true);
				st.scale = num(m, "scale", 1f);
				st.anchor = AnchorPoint.parse(str(m, "anchor", "TOP_LEFT"), AnchorPoint.TOP_LEFT);
				st.offsetX = num(m, "offset_x", 0f);
				st.offsetY = num(m, "offset_y", 0f);
				if (st.id != null && !st.id.isEmpty()) cfg.modules.add(st);
			}
		}
		return cfg;
	}

	/** Serialize to a plain JsonObject (inverse of fromJsonObject). */
	public JsonObject toJsonObject() {
		JsonObject root = new JsonObject();
		JsonObject c = new JsonObject();
		c.addProperty("base_width", canvas.base_width);
		c.addProperty("base_height", canvas.base_height);
		c.addProperty("global_gui_scale", canvas.global_gui_scale);
		c.addProperty("global_corner_radius", canvas.global_corner_radius);
		root.add("canvas", c);
		JsonArray arr = new JsonArray();
		for (HudComponentState m : modules) {
			JsonObject o = new JsonObject();
			o.addProperty("id", m.id);
			o.addProperty("enabled", m.enabled);
			o.addProperty("scale", m.scale);
			o.addProperty("anchor", m.anchor.name());
			o.addProperty("offset_x", m.offsetX);
			o.addProperty("offset_y", m.offsetY);
			arr.add(o);
		}
		root.add("modules", arr);
		return root;
	}

	private static float num(JsonObject o, String k, float def) {
		if (o != null && o.has(k) && o.get(k).isJsonPrimitive()) {
			try {
				return o.get(k).getAsFloat();
			} catch (Throwable ignored) {
			}
		}
		return def;
	}

	private static boolean bool(JsonObject o, String k, boolean def) {
		if (o != null && o.has(k) && o.get(k).isJsonPrimitive()) {
			try {
				return o.get(k).getAsBoolean();
			} catch (Throwable ignored) {
			}
		}
		return def;
	}

	private static String str(JsonObject o, String k, String def) {
		if (o != null && o.has(k) && o.get(k).isJsonPrimitive()) {
			try {
				return o.get(k).getAsString();
			} catch (Throwable ignored) {
			}
		}
		return def;
	}
}
