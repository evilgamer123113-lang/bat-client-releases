package com.batclient.hud;

import com.batclient.HudConfig;
import com.batclient.hud.layout.AbsoluteBounds;
import com.batclient.hud.layout.AnchorPoint;
import com.batclient.hud.layout.HudComponentState;
import com.batclient.hud.layout.HudCoordinateResolver;
import com.batclient.hud.layout.HudLayoutConfig;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between the persistent HudConfig JSON (web editor + disk) and the
 * typed layout model used by the resolver / editor / renderer.
 *
 * Backward compatibility: legacy configs store x/y as PERCENT of screen.
 * On load those are converted to TOP_LEFT pixel offsets using the current
 * scaled screen so existing user layouts do not jump. New saves write the
 * anchor schema; the legacy x/y keys are kept in sync for older readers.
 */
public final class HudLayoutEngine {

	private static final String[] DEFAULT_IDS = {
			"fps", "cps", "coords", "keystrokes", "potions", "ping",
			"armor", "durability", "crosshair", "target", "togglesprint", "scoreboard", "bossbar", "blockindicator", "iteminfo", "compass", "clock", "speedometer", "resourcepacks", "combo", "reach", "inventory", "totems", "arrows", "itemtracker", "minimap", "foodpreview", "bowcharge", "deathcoords", "mounthud", "elytrahud", "shieldindicator"
	};
	private static final boolean[] DEF_EN = {
			true, false, true, false, false, true,
			false, false, false, false, false, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false
	};
	private static final float[] DEF_X = { 0, 0, 89, 7, 0, 93, 94, 82, 49.5f, 35, 0, 90, 39, 62, 40, 40, 88, 2, 2, 2, 2, 40, 2, 2, 75, 80, 60, 53, 2, 70, 70, 70 };
	private static final float[] DEF_Y = { 1, 9, 18, 78, 44, 1, 82, 76, 48, 72, 18, 44, 1, 35, 70, 3, 10, 25, 32, 38, 42, 60, 48, 54, 60, 5, 75, 53, 65, 70, 45, 60 };

	private HudLayoutEngine() {
	}

    private static long renderRevision=-1;
    private static float renderWidth,renderHeight;
    private static List<HudComponentState> renderStates;
    /** Only the renderer uses this cache; editor interaction keeps its own mutable states. */
    public static List<HudComponentState> loadRenderStates(float width,float height) {
        long revision=HudConfig.revision();
        if(renderStates==null||revision!=renderRevision||width!=renderWidth||height!=renderHeight){
            renderStates=loadStates(width,height);renderRevision=revision;renderWidth=width;renderHeight=height;
        }
        return renderStates;
    }

	/**
	 * Build the typed module list from the live HudConfig snapshot.
	 * Applies percent->pixel migration for legacy entries on first touch.
	 */
	public static List<HudComponentState> loadStates(float screenW, float screenH) {
		JsonObject cfg = HudConfig.snapshot();
		List<HudComponentState> out = new ArrayList<>();
		for (int i = 0; i < DEFAULT_IDS.length; i++) {
			String id = DEFAULT_IDS[i];
			if (id.equals("crosshair")) continue; // Fixed-centre reticle, not a movable HUD rectangle.
			JsonObject mod = HudConfig.module(cfg, id);
			HudComponentState st = new HudComponentState(id);
			if (mod != null) {
				st.enabled = HudConfig.enabled(mod);
                st.locked = mod.has("locked") && mod.get("locked").getAsBoolean();
				st.scale = HudConfig.scale(mod);
				if (mod.has("anchor") || mod.has("offset_x") || mod.has("offset_y")) {
					// modern schema
					st.anchor = AnchorPoint.parse(
							mod.has("anchor") && mod.get("anchor").isJsonPrimitive()
									? mod.get("anchor").getAsString() : "TOP_LEFT", AnchorPoint.TOP_LEFT);
					st.offsetX = num(mod, "offset_x", 0f);
					st.offsetY = num(mod, "offset_y", 0f);
				} else {
					// legacy percent x/y -> TOP_LEFT pixels
					st.anchor = AnchorPoint.TOP_LEFT;
					st.offsetX = HudConfig.posX(mod) * screenW / 100f;
					st.offsetY = HudConfig.posY(mod) * screenH / 100f;
				}
			} else {
				st.enabled = DEF_EN[i];
				st.scale = 1f;
				st.anchor = AnchorPoint.TOP_LEFT;
				st.offsetX = DEF_X[i] * screenW / 100f;
				st.offsetY = DEF_Y[i] * screenH / 100f;
			}
			out.add(st);
		}
		return out;
	}

	/** Write one component back into HudConfig (anchor schema + legacy x/y). */
	public static void persist(HudComponentState st, float screenW, float screenH) {
		if (st == null || st.id == null) return;
		JsonObject cfg = HudConfig.snapshot();
		JsonObject mods;
		if (!cfg.has("modules") || !cfg.get("modules").isJsonObject()) {
			mods = new JsonObject();
			cfg.add("modules", mods);
		} else {
			mods = cfg.getAsJsonObject("modules");
		}
		JsonObject m;
		if (mods.has(st.id) && mods.get(st.id).isJsonObject()) {
			m = mods.getAsJsonObject(st.id);
		} else {
			m = new JsonObject();
			mods.add(st.id, m);
		}
		m.addProperty("en", st.enabled);
        m.addProperty("locked", st.locked);
		m.addProperty("scale", st.scale);
		m.addProperty("anchor", st.anchor.name());
		m.addProperty("offset_x", st.offsetX);
		m.addProperty("offset_y", st.offsetY);
		// legacy percent mirror for older readers / web widgets
		m.addProperty("x", screenW > 0 ? HudCoordinateResolver.absX(st, screenW) * 100f / screenW : 0f);
		m.addProperty("y", screenH > 0 ? HudCoordinateResolver.absY(st, screenH) * 100f / screenH : 0f);
		HudConfig.update(cfg.toString());
	}

	/** Bounds for every enabled module (for editor guides / hit overlay). */
	public static List<AbsoluteBounds> boundsFor(List<HudComponentState> states,
			java.util.function.Function<HudComponentState, float[]> measure,
			float screenW, float screenH) {
		List<AbsoluteBounds> out = new ArrayList<>(states.size());
		for (HudComponentState st : states) {
			float[] wh = measure.apply(st);
			st.baseWidth = wh != null && wh.length >= 1 ? wh[0] : 0f;
			st.baseHeight = wh != null && wh.length >= 2 ? wh[1] : 0f;
			out.add(HudCoordinateResolver.bounds(st, screenW, screenH));
		}
		return out;
	}

	private static float num(JsonObject o, String k, float def) {
		if (o.has(k) && o.get(k).isJsonPrimitive()) {
			try {
				return o.get(k).getAsFloat();
			} catch (Throwable ignored) {
			}
		}
		return def;
	}
}
