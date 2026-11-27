package com.batclient;

import com.google.gson.JsonObject;
import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/**
 * Tick-driven client modules that have no on-screen element of their own:
 * zoom (hold C — smooth FOV zoom), fullbright (night vision while enabled),
 * togglesprint (auto-sprint while walking). Also owns the CPS counters that
 * the keystrokes + cps HUD modules read.
 *
 * Enabled flags come from the same config/batclient-hud.json the web mod
 * menu edits (HudConfig), so toggles there take effect immediately in-game.
 */
public final class ClientMods {
	private static KeyBinding zoomKey;
	private static KeyBinding perspectiveKey;
	private static double zoomFactor = 1;
	private static long lastZoomFrame;
	private static boolean zoomActive;
	private static double wheelZoomLevel = 4;
	/** Reduces particle spawns: refreshed once per tick, read per spawn (see dropParticle). */
	private static volatile int particlePct = 50;
	private static final ArrayDeque<Long> lmbClicks = new ArrayDeque<>();
	private static final ArrayDeque<Long> rmbClicks = new ArrayDeque<>();
	private static boolean lmbPrev;
	private static boolean rmbPrev;
	private static boolean brightApplied;

	private ClientMods() {
	}

	public static void init() {
		zoomKey = ClientHookServices.get().registerKeyBinding(new KeyBinding(
				"key.batclient.zoom",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_C,
				"category.batclient.title"
		));
		perspectiveKey = ClientHookServices.get().registerKeyBinding(new KeyBinding(
				"key.batclient.perspective",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				"category.batclient.title"
		));
		BatKeybinds.register("zoom", zoomKey);
		BatKeybinds.register("perspective", perspectiveKey);
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			zoomFactor = 1; lastZoomFrame = 0;
			zoomActive = false;
			brightApplied = false;
			lmbPrev = false;
			rmbPrev = false;
			lmbClicks.clear();
			rmbClicks.clear();
			return;
		}
		JsonObject cfg = HudConfig.snapshot();
		particlePct = particlePctFrom(cfg);
		trackClicks(client);
		
		bright(client, enabledDef(cfg, "fullbright", false));
		sprint(client, enabledDef(cfg, "togglesprint", false) && HudConfig.bool(HudConfig.module(cfg,"togglesprint"),"autoSprint",true));
		perspective(client, enabledDef(cfg, "perspective", false));
	}

	/** Missing config entry = module default (all three client mods default ON). */
	private static boolean enabledDef(JsonObject cfg, String id, boolean def) {
		JsonObject m = HudConfig.module(cfg, id);
		return m == null ? def : HudConfig.enabled(m);
	}

	// ---- zoom ----

	/** Applied to the camera FOV, bypassing the Options slider's 30-degree minimum. */
	public static double zoomedFov(double vanillaFov) {
		MinecraftClient client = MinecraftClient.getInstance();
		JsonObject cfg = HudConfig.snapshot();
		JsonObject mod = HudConfig.module(cfg, "zoom");
		boolean held = client.world != null && client.player != null && client.currentScreen == null
				&& client.isWindowFocused() && enabledDef(cfg, "zoom", true) && zoomKeyHeld(client, mod);
		if (held && !zoomActive) wheelZoomLevel = HudConfig.setNumber(mod, "level", 4);
		zoomActive = held;
		double target = com.batclient.hud.ZoomInterpolation.target(held, wheelZoomLevel);
		long now = System.nanoTime();
		double elapsed = lastZoomFrame == 0 ? 1.0 / 60 : Math.min(.1, Math.max(0, (now - lastZoomFrame) / 1e9));
		lastZoomFrame = now;
		if (client.currentScreen != null || client.world == null || !enabledDef(cfg, "zoom", true) || !HudConfig.bool(mod, "smooth", true)) zoomFactor = target;
		else zoomFactor = com.batclient.hud.ZoomInterpolation.step(zoomFactor, target, elapsed, HudConfig.setNumber(mod, "speed", 18));
		return vanillaFov * zoomFactor;
	}

	private static boolean zoomKeyHeld(MinecraftClient client, JsonObject mod) {
		return BatKeybinds.held(client, "zoom");
	}

	/** Consume the wheel only while the zoom key is held, leaving normal hotbar scrolling intact. */
	public static boolean scrollZoom(long window, double vertical) {
		MinecraftClient client = MinecraftClient.getInstance();
		JsonObject cfg = HudConfig.snapshot(), mod = HudConfig.module(cfg, "zoom");
		if (window != client.getWindow().getHandle() || vertical == 0 || client.world == null || client.player == null
				|| client.currentScreen != null || !client.isWindowFocused() || !enabledDef(cfg, "zoom", true)
				|| !HudConfig.bool(mod, "scroll", true) || !zoomKeyHeld(client, mod)) return false;
		if (!zoomActive) wheelZoomLevel = HudConfig.setNumber(mod, "level", 4);
		zoomActive = true;
		wheelZoomLevel = com.batclient.hud.ZoomInterpolation.scrollLevel(wheelZoomLevel, vertical,
				HudConfig.setNumber(mod, "scrollSpeed", 1), HudConfig.setNumber(mod, "maxZoom", 20));
		return true;
	}

	public static double zoomMouseScale() {
		if (zoomFactor == 1) return 1; // Ordinary mouse motion needs no config/JSON lookup.
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.currentScreen != null || !enabledDef(HudConfig.snapshot(), "zoom", true)) return 1;
		JsonObject mod = HudConfig.module(HudConfig.snapshot(), "zoom");
		return com.batclient.hud.ZoomInterpolation.mouseScale(zoomFactor, HudConfig.setNumber(mod, "sensitivity", 75));
	}
	// ---- fullbright ----

	/** True while WE applied the fake night-vision (HudRenderer filters it from potions). */
	public static boolean isFakeNightVision() {
		return brightApplied;
	}

	private static void bright(MinecraftClient client, boolean enabled) {
		if (enabled) {
			StatusEffectInstance existing = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
			// Refresh before expiry (was 400 ticks = 20s, expired then re-added
			// next tick → visible flicker at night). Keep it permanently fresh.
			if (existing == null || existing.getDuration() < 200) {
				client.player.addStatusEffect(new StatusEffectInstance(
						StatusEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
				brightApplied = true;
			}
		} else if (brightApplied) {
			client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
			brightApplied = false;
		}
	}

	// ---- togglesprint ----

	private static void sprint(MinecraftClient client, boolean enabled) {
		if (!enabled || client.currentScreen != null) return;
		if (client.options.forwardKey.isPressed() && !client.options.sneakKey.isPressed()) {
			client.player.setSprinting(true);
		}
	}

	public static String movementLabel(MinecraftClient client, JsonObject mod, boolean preview) {
		if (client.player == null || preview) return "Sprinting (Toggled)";
		var player=client.player;
		boolean held=client.options.sprintKey.isPressed();
		boolean auto=HudConfig.bool(mod,"autoSprint",true) && !held;
		return new com.batclient.hud.MovementStatus(player.hasVehicle(),player.isFallFlying(),player.getAbilities().flying,
				player.isSwimming(),player.isSneaking(),player.isSprinting(),
				Math.abs(player.forwardSpeed)>.001f || Math.abs(player.sidewaysSpeed)>.001f,
				auto,held,player.getAbilities().getFlySpeed()/.05f).label(HudConfig.bool(mod,"flightSpeed",true));
	}

	// ---- perspective ----

	private static void perspective(MinecraftClient client, boolean enabled) {
		boolean pressed = BatKeybinds.pressed(client, "perspective");
		if (!enabled || client.currentScreen != null || perspectiveKey == null) return;
		if (pressed) {
			client.options.setPerspective(client.options.getPerspective().next());
		}
	}

	// ---- particle reduction (policy for ParticleThrottleMixin) ----

	private static int particlePctFrom(JsonObject cfg) {
		JsonObject m = HudConfig.module(cfg, "particlereduce");
		if (m == null || !HudConfig.enabled(m)) return 0;
		float amt = m == null ? 50f : HudConfig.setNumber(m, "amount", 50f);
		return Math.max(0, Math.min(100, Math.round(amt)));
	}

	/** Called per particle spawn (mixin) — reads the tick-cached percent only. */
	public static boolean dropParticle() {
		int pct = particlePct;
		if (pct <= 0) return false;
		if (pct >= 100) return true;
		return java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < pct;
	}

	// ---- CPS counters (keystrokes / cps HUD modules) ----

	private static void trackClicks(MinecraftClient client) {
		long now = System.currentTimeMillis();
		boolean a = client.currentScreen == null && client.options.attackKey.isPressed();
		if (a && !lmbPrev) lmbClicks.addLast(now);
		lmbPrev = a;
		boolean u = client.currentScreen == null && client.options.useKey.isPressed();
		if (u && !rmbPrev) rmbClicks.addLast(now);
		rmbPrev = u;
	}

	public static int lmbCps() {
		return cps(lmbClicks);
	}

	public static int rmbCps() {
		return cps(rmbClicks);
	}

	private static int cps(ArrayDeque<Long> q) {
		long cutoff = System.currentTimeMillis() - 1000L;
		while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
		return q.size();
	}
}
