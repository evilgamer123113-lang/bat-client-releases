package com.batclient;

import com.batclient.web.BatClientShaders;
import com.batclient.web.WebController;
import com.batclient.web.WebScreen;
import com.batclient.web.WebWindow;



import com.batclient.platform.ClientHookServices;
import com.batclient.platform.ClientResourceServices;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatClientMod {
	public static final String MOD_ID = "batclient";
	public static final Logger LOGGER = LoggerFactory.getLogger("batclient");

	private static WebScreen activeScreen;
	private static KeyBinding openSettingsKey;
	/** One-shot: when the player picks "Original Pause Screen" on the Bat pause
	 *  menu, vanilla GameMenuScreen must NOT be swapped back for our page until
	 *  they leave the pause (currentScreen == null), which resets this flag. */
	private static boolean preferOriginalPause;
	private static WebScreen cachedPauseScreen;
	/** Warm in-game HUD window (overlay.html). Created on world join, kept
	 *  alive while the overlay screen is closed — HudRenderer pumps + draws
	 *  its texture as the live HUD (W2: the web widgets ARE the HUD). */
	private static WebScreen cachedOverlayScreen;
	/** True while the web HUD editor (hudedit:on) is active. Fed back to the
	 *  page every frame (hud-edit body class) so the editor chrome shows. */
	private static volatile boolean hudEditorActive;

	public static boolean isHudEditorActive() {
		return hudEditorActive;
	}

	public static void setHudEditorActive(boolean value) {
		hudEditorActive = value;
	}

	public static WebScreen getActiveScreen() {
		return activeScreen;
	}

	public static void clearCachedPause() {
		cachedPauseScreen = null;
	}

	public static void setCachedOverlay(WebScreen screen) {
		cachedOverlayScreen = screen;
	}

	public static WebScreen cachedOverlay() {
		return cachedOverlayScreen;
	}

	/** The overlay window currently hosting the in-game HUD (null pre-join). */
	public static WebWindow overlayWindow() {
		WebScreen s = cachedOverlayScreen;
		return s != null ? s.window() : null;
	}

	/** True when the window belongs to a warm cache (pause or overlay) and
	 *  must survive WebController.createScreen() for later reuse. */
	public static boolean isCachedWindow(WebWindow w) {
		if (w == null) return false;
		WebScreen p = cachedPauseScreen;
		if (p != null && p.window() == w) return true;
		WebScreen o = cachedOverlayScreen;
		return o != null && o.window() == w;
	}

	public static void setActiveScreen(WebScreen screen) {
		activeScreen = screen;
	}

	public static void setPreferOriginalPause(boolean value) {
		preferOriginalPause = value;
	}

	private static boolean resourcesInitialized;
	public static synchronized void initializeResources(){
		if(resourcesInitialized)return;
		com.batclient.platform.LauncherAccess.require();
		resourcesInitialized=true;
		ResourcePackHud.init();
		ClientResourceServices.get().shaders(BatClientShaders::registerAll);
		SkinLayers3D.init();
		ItemPreviews.init();
	}

	public void onInitializeClient() {
		LOGGER.info("Bat Client mod initializing (Ultralight/WebKit UI engine)");
		initializeResources();
        com.batclient.performance.BuiltinPerformance.init();
        Waypoints.init();
        Freelook.init();LootBeams.init();ReturnTrail.init();
        ClientHookServices.get().endTick(Freelook::tick);
        ClientHookServices.get().endTick(LootBeams::tick);
        ClientHookServices.get().endTick(ReturnTrail::tick);
        ClientHookServices.get().endTick(Waypoints::tick);
        ClientHookServices.get().endTick(TravelHud::tick);
        ClientHookServices.get().endTick(ServerProfiles::tick);
		WorldVisualModules.init();
		AdventureWorld.init();
		ClientHookServices.get().endTick(DeathCoordinates::tick);
		ClientHookServices.get().stopping(client -> SkinLayers3D.clear());
		ClientHookServices.get().endTick(ItemPickupTracker::tick);
		ClientHookServices.get().endTick(MinimapHud::tick);
		ClientHookServices.get().stopping(MinimapHud::close);

		// Preload the Ultralight engine in the background so the main menu
		// is ready to take over the vanilla title screen on launch.
		Thread loader = new Thread(WebController.INSTANCE::loadNatives, "batclient-ultralight-loader");
		loader.setDaemon(true);
		loader.start();

		// Shader that renders the BGRA8 premultiplied Ultralight surface as a textured quad
		// (Ultralight surfaces are BGRA in memory; the shader swaps bgr -> rgb).


		openSettingsKey = ClientHookServices.get().registerKeyBinding(new KeyBinding(
				"key.batclient.openSettings",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"category.batclient.title"
		));

		ClientHookServices.get().endTick(BatClientMod::tick);

		// Feather-style functional modules: in-game HUD renderer (reads
		// config/batclient-hud.json) + tick-driven client mods (zoom/CPS/...).
		HudRenderer.init();
		ClientMods.init();
		BatKeybinds.register("menu", openSettingsKey);

		// Instant pause swap at SCREEN-INIT time. The pause key is handled by
		// net.minecraft.client.Keyboard (a GLFW key callback), which can fire
		// OUTSIDE MinecraftClient.tick() — between END_CLIENT_TICK and the frame
		// render — so the tick-only takeover left ONE vanilla GameMenuScreen
		// frame visible when spamming ESC ("millisecond of the normal pause
		// menu"). BEFORE_INIT fires inside setScreen -> Screen.init no matter
		// when/where the GameMenuScreen was created, so it swaps to the web
		// pause before it can ever render. Reentrancy-safe: MinecraftClient
		//.setScreen does nothing after init() except skipGameRender=false and
		// updateWindowTitle (verified via javap on the mapped jar).
		ClientHookServices.get().beforeScreen((client, screen, scaledWidth, scaledHeight) -> {
			// Replace titles during setScreen/init, before a frame can render vanilla.
			if (screen instanceof TitleScreen && WebController.INSTANCE.isReady()) {
				showMainMenu(client);
				return;
			}
			if (!(screen instanceof GameMenuScreen) || screen instanceof OriginalPauseScreen) {
				return;
			}
			if (client.world == null || !WebController.INSTANCE.isReady()) {
				return;
			}
			// While OriginalPauseScreen is current it re-inits its vanilla
			// delegate — never wrap that GameMenuScreen again (recursion guard).
			if (client.currentScreen instanceof OriginalPauseScreen) {
				return;
			}
			if (!preferOriginalPause) {
				showPauseScreen(client);
			} else {
				client.setScreen(new OriginalPauseScreen((GameMenuScreen) screen));
			}
		});

		// Close the Ultralight view before WebKit tears down. Without this, the
		// native engine is destroyed in an inconsistent state at process exit
		// and the game fast-fails (0xC0000409) instead of exiting cleanly.
		// Also best-effort sweep stale native extraction dirs (the current run's
		// DLLs are still loaded here so its own dir is skipped; it gets cleaned
		// on the next launch).
		ClientHookServices.get().stopping(client -> {
			ResourcePackHud.close(client);
			com.batclient.web.MenuBranding.close();
			HudConfig.flush();
			ClientPresence.stop();
			WebController.INSTANCE.shutdown();
			WebController.cleanStaleNatives();
		});
		if (Boolean.getBoolean("batclient.test.multiplayer") && com.batclient.platform.Platforms.get().isDevelopmentEnvironment()) {
			ClientHookServices.get().started(client -> {
				var screen = new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(null);
				try {
					screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
					var list = screen.children().stream().filter(element -> element instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget)
							.map(element -> (net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget)element).findFirst().orElseThrow();
					long buttons = screen.children().stream().filter(element -> element instanceof net.minecraft.client.gui.widget.ButtonWidget).count();
					if (buttons < 7) throw new AssertionError("Multiplayer buttons missing: " + buttons);
					var server = new net.minecraft.client.network.ServerInfo("zz-bat-test", "127.0.0.1:25565", net.minecraft.client.network.ServerInfo.ServerType.OTHER);
					screen.getServerList().add(server, false); list.setServers(screen.getServerList());
					((ServerListExtras)list).bat$search("zz-bat-test");
					long matches = list.children().stream().filter(entry -> entry instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget.ServerEntry).count();
					if (matches != 1) throw new AssertionError("Multiplayer search failed: " + matches);
					LOGGER.info("[screen-smoke] MULTIPLAYER_CONTROLS_AND_SEARCH_OK buttons={} matches={}", buttons, matches);
				} catch (Throwable e) { LOGGER.error("[screen-smoke] Multiplayer test failed", e); }
				finally { screen.removed(); }
			});
		}
	}

	private static void tick(MinecraftClient client) {
		ExtraHud.tick(client);
		CombatHud.tick(client);
		BatKeybinds.tick(client);
		// Client modules (zoom/fullbright/togglesprint + CPS counters).
		ClientMods.tick(client);
		ClientPresence.tick(client);

		// Hotkey: open the overlay in-game, or close it if already open (never hijack keys while typing in chat/menus).
		if (BatKeybinds.pressed(client, "menu")
				&& (client.currentScreen == null || client.currentScreen instanceof WebScreen)) {
			toggle(client);
		}

		// Create the Ultralight renderer on the render thread once natives are in
		// (renderer/views are thread-affine; never touch them from the loader thread).
		WebController.INSTANCE.ensureRenderer();
		com.batclient.web.MenuRegressionChecks.tick(client);

		// Main menu takeover: whenever the vanilla title screen shows, replace it
		// with the Bat Client main menu (once the engine is up). Runs EVEN WHILE
		// SplashOverlay is drawing — the web screen's own render covers the
		// vanilla title with a solid backdrop so it can't flash through during
		// the splash fade-out, while Ultralight pumping/upload stays gated on
		// overlay==null inside WebScreen.render (the NVIDIA nvoglv64 crash was
		// the first GL upload racing the splash, not the screen swap).
		if (client.currentScreen instanceof TitleScreen
				&& WebController.INSTANCE.isReady()) {
			showMainMenu(client);
		}

		// ESC pause takeover: vanilla opens GameMenuScreen in a world — swap it for
		// the Bat pause page in the SAME tick (before render → no vanilla flash).
		// "Original Pause Screen" opts out: vanilla menu stays, but wrapped in
		// OriginalPauseScreen so it gains Feather's top-right "Bat Client Screen"
		// button (no mixins — the wrapper composes the vanilla screen).
		if (client.world != null
				&& client.currentScreen instanceof GameMenuScreen
				&& WebController.INSTANCE.isReady()) {
			if (!preferOriginalPause) {
				showPauseScreen(client);
			} else if (!(client.currentScreen instanceof OriginalPauseScreen)) {
				client.setScreen(new OriginalPauseScreen((GameMenuScreen) client.currentScreen));
			}
		}
		// Backing out to the world (or quitting it) resets the original-pause
		// preference so the next ESC opens the Bat pause screen again.
		if (preferOriginalPause && (client.currentScreen == null || client.world == null)) {
			preferOriginalPause = false;
		}
		// Warm pause view is only invalid when the world is gone (quit to
		// title / disconnect). Do NOT clear on currentScreen == null — that is
		// exactly the state right after closing the pause screen, and clearing
		// there made every ESC reload pause.html.
		//
		// Defer optional web prewarming until the first chunk-loading burst has settled.
		// Opening the menu explicitly still creates its view immediately.
		// Proactive: build the in-game HUD window once the player has been in a world briefly
		// (the page load takes a moment — the HUD must not wait for the first
		// Right Shift press).
		// Gameplay HUD is native. Build the web menu on explicit opening, then cache it.
		// Creating a hidden software web view during world entry competes with chunks.
		// World gone: destroy the warm overlay + pause windows (rebuilt on the
		// next join). Skip the one still current — its removed() tears it down
		// (or keeps it, in which case the nulled cache lets the next
		// createScreen() reclaim/close it).
		if (client.world == null) {
			WebScreen pause = cachedPauseScreen;
			cachedPauseScreen = null;
			if (pause != null && client.currentScreen != pause) {
				pause.destroyWindow();
			}
			WebScreen ov = cachedOverlayScreen;
			cachedOverlayScreen = null;
			if (ov != null && client.currentScreen != ov) {
				ov.destroyWindow();
			}
		}
	}

	public static void showPauseScreen(MinecraftClient client) {
		WebScreen screen = cachedPauseScreen;
		if (screen != null && screen.window() != null) {
			activeScreen = screen;
			WebController.INSTANCE.adoptWindow(screen.window());
			client.setScreen(screen);
			return;
		}
		screen = WebController.INSTANCE.createScreen(WebScreen.Mode.PAUSE);
		if (screen == null) {
			LOGGER.warn("Failed to create Bat Client pause screen, keeping vanilla pause");
			return;
		}
		cachedPauseScreen = screen;
		activeScreen = screen;
		client.setScreen(screen);
	}

	private static void showMainMenu(MinecraftClient client) {
		WebScreen screen = WebController.INSTANCE.createScreen();
		if (screen == null) {
			LOGGER.warn("Failed to create Bat Client main menu, keeping vanilla title");
			return;
		}
		activeScreen = screen;
		client.setScreen(screen);
	}

	public static void openWaypoints(MinecraftClient client) { toggle(client); }

	private static void toggle(MinecraftClient client) {
		if (activeScreen != null && client.currentScreen == activeScreen) {
			client.setScreen(null);
			return;
		}
		if (!WebController.INSTANCE.isReady()) {
			LOGGER.warn("Ultralight engine not ready, cannot open web UI");
			return;
		}
		if (client.world != null) {
			// In-game: reuse the warm HUD overlay window (page already loaded,
			// HUD widgets keep living in the same DOM while the menu is open).
			WebScreen screen = cachedOverlayScreen;
			if (screen == null || screen.window() == null) {
				screen = WebController.INSTANCE.createScreen(WebScreen.Mode.OVERLAY);
				if (screen == null) {
					LOGGER.warn("Failed to create Bat Client overlay");
					return;
				}
				cachedOverlayScreen = screen;
			}
			activeScreen = screen;
			WebController.INSTANCE.adoptWindow(screen.window());
			client.setScreen(screen);
			return;
		}
		activeScreen = WebController.INSTANCE.createScreen();
		if (activeScreen != null) {
			client.setScreen(activeScreen);
		}
	}
}
