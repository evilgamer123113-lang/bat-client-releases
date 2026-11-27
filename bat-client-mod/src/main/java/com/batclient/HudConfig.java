package com.batclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.batclient.platform.Platforms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent module/HUD configuration shared between the web UI
 * (batGetHudConfig / batSaveHudConfig bindings in WebScreen) and the in-game
 * HUD renderer (HudRenderer) + client mods (ClientMods).
 *
 * Shape:
 * { "modules": { "<id>": {
 *     "en": true, "x": 2.0, "y": 4.0, "scale": 1.0,
 *     "set": { "shadow": true, "color": "White", ... }
 * } } }
 * x/y are percentages of the scaled screen (same coordinate space the web
 * HUD editor uses), scale is the module zoom factor (0.5 - 3.0).
 *
 * Single in-memory copy: the web bridge updates it on every save so the
 * renderer never touches the file per frame.
 */
public final class HudConfig {
	private static final Path FILE = Platforms.get().getConfigDir()
			.resolve("batclient-hud.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static JsonObject memory;
	private static volatile boolean dirtyDisk;
    private static long revision;
    private static void setMemory(JsonObject next) {memory=next;revision++;}
    public static synchronized long revision() {ensureLoaded();return revision;}

	private HudConfig() {
	}

	public static synchronized JsonObject snapshot() {
		ensureLoaded();
		return memory;
	}

	/** JSON string for batGetHudConfig(). */
	public static synchronized String json() {
		ensureLoaded();
		return memory.toString();
	}

	/** Called from the web bridge (batSaveHudConfig) on every change. */
	public static synchronized void preview(String json) {
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (parsed.isJsonObject() && parsed.getAsJsonObject().has("modules")) setMemory(parsed.getAsJsonObject());
		} catch (RuntimeException ignored) { }
	}

	public static synchronized void update(String json) {
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) return;
			JsonObject obj = parsed.getAsJsonObject();
			if (!obj.has("modules") || !obj.get("modules").isJsonObject()) {
				obj.add("modules", new JsonObject());
			}
			if (obj.has("profiles")) syncActiveProfile(obj);
			setMemory(obj);
			dirtyDisk = true;
		} catch (Throwable t) {
			BatClientMod.LOGGER.warn("[hud] config save failed: {}", t.toString());
		}
	}

	/** Cached pages may edit different modules; never replace unrelated state. */
	public static synchronized boolean updateModule(String id, String json, boolean persist) {
		if (id == null || !id.matches("[a-z0-9_]{1,40}")) return false;
		try {
			ensureLoaded();
			JsonObject patch = JsonParser.parseString(json).getAsJsonObject();
			JsonObject next = memory.deepCopy();
			JsonObject modules = next.getAsJsonObject("modules");
			JsonObject existing = modules.has(id) && modules.get(id).isJsonObject() ? modules.getAsJsonObject(id)
					: patch.has("_initial") && patch.get("_initial").isJsonObject() ? patch.getAsJsonObject("_initial").deepCopy() : new JsonObject();
			if ((patch.has("x") || patch.has("y")) && !patch.has("anchor")) {
				existing.remove("anchor"); existing.remove("offset_x"); existing.remove("offset_y");
			}
			for (var field : patch.entrySet()) {
				if ("_initial".equals(field.getKey())) continue;
				if ("set".equals(field.getKey()) && field.getValue().isJsonObject()) {
					JsonObject settings = existing.has("set") && existing.get("set").isJsonObject() ? existing.getAsJsonObject("set") : new JsonObject();
					for (var setting : field.getValue().getAsJsonObject().entrySet()) settings.add(setting.getKey(), setting.getValue().deepCopy());
					existing.add("set", settings);
				} else existing.add(field.getKey(), field.getValue().deepCopy());
			}
			modules.add(id, existing);
			if (next.has("profiles")) syncActiveProfile(next);
			setMemory(next);
			if (persist) dirtyDisk = true;
			return true;
		} catch (Throwable t) {
			BatClientMod.LOGGER.warn("[hud] module {} save failed: {}", id, t.toString());
			return false;
		}
	}

	private static void write(JsonObject config) throws java.io.IOException {
		if (config.has("profiles")) syncActiveProfile(config);
		Files.createDirectories(FILE.getParent());
		Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
		Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
		com.batclient.platform.AtomicFiles.replace(temporary,FILE);
	}

	private static JsonObject profileState(JsonObject config) {
		JsonObject state = new JsonObject();
		for (String key : new String[]{"modules", "bindings"})
			state.add(key, config.has(key) && config.get(key).isJsonObject() ? config.getAsJsonObject(key).deepCopy() : new JsonObject());
		return state;
	}

	private static void syncActiveProfile(JsonObject config) {
		if (!config.get("profiles").isJsonObject()) return;
		String active = config.has("activeProfile") ? config.get("activeProfile").getAsString() : "Default";
		config.getAsJsonObject("profiles").add(active, profileState(config));
	}

	/** Named local profiles contain module settings/layouts and Bat keybinds only. */
    public static synchronized String serverBinding(String action,String server,String profile){
        ensureLoaded();if(server==null||server.isBlank())return "Join a server or world first.";
        JsonObject next=memory.deepCopy();JsonObject map=next.has("serverProfiles")&&next.get("serverProfiles").isJsonObject()?next.getAsJsonObject("serverProfiles"):new JsonObject();
        if(action.equals("bind")){
            if(!next.has("profiles")||!next.getAsJsonObject("profiles").has(profile))return "Profile not found.";
            if(!map.has(server)&&map.size()>=128)return "You can save up to 128 server assignments.";
            map.addProperty(server,profile);
        }else if(action.equals("clear"))map.remove(server);else return "Unknown server profile action.";
        next.add("serverProfiles",map);try{write(next);setMemory(next);return "";}catch(java.io.IOException e){return "Server assignment could not be saved.";}
    }
	public static synchronized String profileAction(String action, String name) {
		ensureLoaded();
		try {
			JsonObject next = memory.deepCopy();
			if (!next.has("profiles") || !next.get("profiles").isJsonObject()) next.add("profiles", new JsonObject());
			JsonObject profiles = next.getAsJsonObject("profiles");
			String active = next.has("activeProfile") ? next.get("activeProfile").getAsString() : "Default";
			if (!profiles.has(active)) profiles.add(active, profileState(next));
			next.addProperty("activeProfile", active);
			syncActiveProfile(next);
			name = name == null ? "" : name.trim();
			if (!"list".equals(action) && (name.isEmpty() || name.length() > 48 || name.chars().anyMatch(Character::isISOControl))) throw new IllegalArgumentException("Use a profile name of 1–48 characters.");
			switch (action) {
				case "list" -> { }
				case "create" -> {
					if (profiles.has(name)) throw new IllegalArgumentException("That profile already exists.");
					if (profiles.size() >= 16) throw new IllegalArgumentException("You can save up to 16 profiles.");
					profiles.add(name, profileState(next)); next.addProperty("activeProfile", name);
				}
				case "load" -> {
					if (!profiles.has(name) || !profiles.get(name).isJsonObject()) throw new IllegalArgumentException("Profile not found.");
					JsonObject saved = profiles.getAsJsonObject(name);
					for (String key : new String[]{"modules", "bindings"}) next.add(key, saved.has(key) && saved.get(key).isJsonObject() ? saved.get(key).deepCopy() : new JsonObject());
					next.addProperty("activeProfile", name);
					ServerProfiles.manual();
				}
				case "rename" -> {
					if (!name.equals(active) && profiles.has(name)) throw new IllegalArgumentException("That profile already exists.");
					JsonObject saved = profiles.getAsJsonObject(active); profiles.remove(active); profiles.add(name, saved); next.addProperty("activeProfile", name);
				}
				case "delete" -> {
					if (name.equals(active)) throw new IllegalArgumentException("Switch to another profile before deleting this one.");
					if (!profiles.has(name)) throw new IllegalArgumentException("Profile not found.");
					profiles.remove(name);
				}
				default -> throw new IllegalArgumentException("Unknown profile action.");
			}
            if(next.has("serverProfiles")&&next.get("serverProfiles").isJsonObject()){
                var map=next.getAsJsonObject("serverProfiles");for(String key:java.util.Set.copyOf(map.keySet())){
                    if(action.equals("rename")&&map.get(key).getAsString().equals(active))map.addProperty(key,name);
                    if(action.equals("delete")&&map.get(key).getAsString().equals(name))map.remove(key);
                }
            }
			write(next); setMemory(next);
			return "";
		} catch (Exception e) { return e.getMessage() == null ? "Profile could not be saved." : e.getMessage(); }
	}

	public static synchronized boolean updateBinding(String action, String key) {
		if (action == null || !action.matches("[a-z0-9_]{1,40}")) return false;
		ensureLoaded();
		JsonObject next = memory.deepCopy();
		JsonObject bindings = next.has("bindings") && next.get("bindings").isJsonObject() ? next.getAsJsonObject("bindings") : new JsonObject();
		if (key == null || key.isBlank()) bindings.remove(action); else bindings.addProperty(action, key);
		next.add("bindings", bindings);
		try { write(next); setMemory(next); return true; }
		catch (java.io.IOException e) { BatClientMod.LOGGER.warn("[hud] binding save failed: {}", e.toString()); return false; }
	}

	/** Persist pending drag/resize previews, including an interrupted pointer gesture. */
	public static synchronized void flush() {
		ensureLoaded();
		try {
			write(memory);
			dirtyDisk = false;
		} catch (java.io.IOException e) { BatClientMod.LOGGER.warn("[hud] config flush failed: {}", e.toString()); }
	}

	private static void ensureLoaded() {
		if (memory != null) return;
		try {
			if (Files.isRegularFile(FILE)) {
				JsonElement parsed = JsonParser.parseString(
						Files.readString(FILE, StandardCharsets.UTF_8));
				if (parsed.isJsonObject()) {
					setMemory(parsed.getAsJsonObject());
				}
			}
		} catch (Throwable t) {
			BatClientMod.LOGGER.warn("[hud] config load failed: {}", t.toString());
		}
		if (memory == null) setMemory(new JsonObject());
		if (!memory.has("modules") || !memory.get("modules").isJsonObject()) {
			memory.add("modules", new JsonObject());
		}
	}

	// ---- renderer helpers ----

	public static JsonObject module(JsonObject cfg, String id) {
		if (cfg == null || id == null) return null;
		if (!cfg.has("modules") || !cfg.get("modules").isJsonObject()) return null;
		JsonObject mods = cfg.getAsJsonObject("modules");
		if (!mods.has(id) || !mods.get(id).isJsonObject()) return null;
		return mods.getAsJsonObject(id);
	}

	public static boolean enabled(JsonObject mod) {
		return mod != null && mod.has("en") && mod.get("en").getAsBoolean();
	}

	public static float posX(JsonObject mod) {
		return number(mod, "x", 2f);
	}

	public static float posY(JsonObject mod) {
		return number(mod, "y", 4f);
	}

	public static float scale(JsonObject mod) {
		float s = number(mod, "scale", 1f);
		return s <= 0f ? 1f : s;
	}

	// ---- per-module "set" values ----

	public static String str(JsonObject mod, String key, String def) {
		if (mod != null && mod.has("set") && mod.get("set").isJsonObject()) {
			JsonObject set = mod.getAsJsonObject("set");
			if (set.has(key) && set.get(key).isJsonPrimitive()) {
				try {
					return set.get(key).getAsString();
				} catch (Throwable ignored) {
				}
			}
		}
		return def;
	}

	public static boolean bool(JsonObject mod, String key, boolean def) {
		if (mod != null && mod.has("set") && mod.get("set").isJsonObject()) {
			JsonObject set = mod.getAsJsonObject("set");
			if (set.has(key) && set.get(key).isJsonPrimitive()) {
				try {
					return set.get(key).getAsBoolean();
				} catch (Throwable ignored) {
				}
			}
		}
		return def;
	}

	public static float setNumber(JsonObject mod, String key, float def) {
		if (mod != null && mod.has("set") && mod.get("set").isJsonObject()) {
			JsonObject set = mod.getAsJsonObject("set");
			if (set.has(key) && set.get(key).isJsonPrimitive()) {
				try {
					return set.get(key).getAsFloat();
				} catch (Throwable ignored) {
				}
			}
		}
		return def;
	}

	private static float number(JsonObject mod, String key, float def) {
		if (mod != null && mod.has(key) && mod.get(key).isJsonPrimitive()) {
			try {
				return mod.get(key).getAsFloat();
			} catch (Throwable ignored) {
			}
		}
		return def;
	}
}
