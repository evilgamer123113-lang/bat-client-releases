package com.batclient.web;

import com.batclient.BatClientMod;
import com.batclient.HudConfig;
import io.github.nilsen84.ultralight.UltralightLoadListener;
import io.github.nilsen84.ultralight.UltralightViewListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A Screen hosting the Ultralight view. Opened via the keybinding (Right Shift),
 * via the ESC pause takeover, or as the main-menu takeover.
 * Everything we need for v1: init, per-frame render, input forwarding.
 */
public class WebScreen extends Screen {
	/** Which page this screen hosts and how it behaves.
	 *  MENU    = title-screen takeover (index.html, ESC does nothing)
	 *  OVERLAY = Right Shift in-game menu (overlay.html, world keeps running)
	 *  PAUSE   = ESC pause screen (pause.html, world pauses like vanilla) */
	public enum Mode { MENU, OVERLAY, PAUSE }

	private final Mode mode;
	private WebWindow window;
	private boolean hudCovered;
	private boolean zoomBindingCapture;
	private String bindingCaptureAction;
	private com.google.gson.JsonObject hudPreview;

	protected WebScreen() {
		this(null);
	}

	WebScreen(Mode forced) {
		super(Text.literal("Bat Client"));
		MinecraftClient client = MinecraftClient.getInstance();
		this.mode = forced != null ? forced : (client.world != null ? Mode.OVERLAY : Mode.MENU);
	}

	public Mode mode() {
		return mode;
	}

	/** Creates the view sized to the current framebuffer. Separated from ctor so initView is explicit. */
	void initView() {
		if (window != null) {
			window.close();
		}
		int w = WebController.framebufferWidth();
		int h = WebController.framebufferHeight();
		window = new WebWindow(WebController.INSTANCE.getRenderer(), w, h);

		// Physical-pixel ratio = framebuffer / scaled GUI size.
		float scale = (float) ((double) w / Math.max(1, MinecraftClient.getInstance().getWindow().getScaledWidth()));
		window.windowContentScaleCallback(scale, scale);

		// The JS bridge MUST be attached after the window object exists — calling
		// bindFunction before loadUrl silently no-ops (window.batAction stays
		// undefined, so every button click does nothing, which is what the
		// 'bridge probe: undefined' line proved). onWindowObjectReady fires exactly
		// when the first JS context is created, before any page script runs.
		window.view().setLoadListener(new UltralightLoadListener() {
			@Override
			public void onWindowObjectReady(String url) {
				bindBridge();
			}
		});
		// Route the page's console messages into the log so JS errors/breadcrumbs
		// ("[bat] click ...") are visible next to the Java-side logs.
		window.view().setViewListener(new UltralightViewListener() {
			@Override
			public void onAddConsoleMessage(MessageLevel level, String message, int lineNumber, int columnNumber, String sourceId) {
				BatClientMod.LOGGER.info("[web:js] {}:{} {}", level, lineNumber, message);
			}
		});

		String page = switch (mode) {
			case PAUSE -> "pause.html";
			case OVERLAY -> "overlay.html";
			case MENU -> "index.html";
		};
		String url = WebAssetManager.webRoot().resolve(page).toUri().toString();
		window.loadUrl(url);
	}

	@Override
	protected void init() {
		// Revive after removed(): pause -> Options/Advancements/... -> Done returns
		// to THIS instance. A kept window (PAUSE / in-world OVERLAY) needs no
		// rebuild, but activeScreen and the controller reference were cleared
		// when the screen was removed — re-register them on EVERY activation.
		if (window == null) {
			initView();
		}
		WebController.INSTANCE.adoptWindow(window);
		BatClientMod.setActiveScreen(this);
		// Cached pages may have been edited through the pause/menu page.
		if (window != null) {
			try {
				window.view().evaluateScript("nativeHud=typeof window.batUsesNativeHud==='function'&&window.batUsesNativeHud()==='true';document.body.classList.toggle('native-hud',nativeHud);typeof cfgLoad==='function'&&(cfgLoad(),renderHudWidgets());typeof batMenuEnter==='function'&&batMenuEnter()");
			} catch (Throwable ignored) {
			}
		}
		if (mode == Mode.OVERLAY && window != null) {
			// Opening the Bat overlay = HUD editor on (the load-time auto-on in
			// app.js covers the very first open; this covers cached reopens).
			try {
				window.view().evaluateScript(
						"typeof toggleHudEditor==='function'&&toggleHudEditor(true)");
			} catch (Throwable ignored) {
			}
		}
	}

	/**
	 * Exposes Java functions to the web UI via Ultralight's JS bridge.
	 * The page calls <code>batAction("singleplayer")</code> from its navigation,
	 * and <code>batGetPlayer()</code> to read the current session username.
	 */
	private void bindBridge() {
		if (window == null) {
			return;
		}
		window.view().bindFunction("batAction", args -> {
			String action = (args != null && args.length > 0) ? args[0] : "";
			handleAction(action);
			return null;
		});
		window.view().bindFunction("batGetPlayer", args -> MinecraftClient.getInstance().getSession().getUsername());
		window.view().bindFunction("batPlayerHead", args -> com.batclient.accounts.AccountHeads.current(MinecraftClient.getInstance()));
		window.view().bindFunction("batAccounts", args -> ((com.batclient.ClientAccountAccess)MinecraftClient.getInstance()).bat$accounts(
				args!=null && args.length>0?args[0]:"list",args!=null && args.length>1?args[1]:""));
		// Pause screen: JSON array of the latest screenshot file names (newest first).
		window.view().bindFunction("batScreenshots", args -> {
			StringBuilder sb = new StringBuilder("[");
			try {
				File[] files = shotsDir().toFile().listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
				if (files != null) {
					java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
					int n = Math.min(files.length, 64);
					for (int i = 0; i < n; i++) {
						if (i > 0) sb.append(',');
						String nm = files[i].getName().replace("\\", "\\\\").replace("\"", "\\\"");
						sb.append('"').append(nm).append('"');
					}
				}
			} catch (Throwable ignored) {
			}
			return sb.append(']').toString();
		});
		window.view().bindFunction("batIsSingleplayer", args ->
				String.valueOf(MinecraftClient.getInstance().isInSingleplayer()));
		// Feather-style HUD modules: the web editor reads/writes the SAME config
		// file the in-game HudRenderer/ClientMods consume (config/batclient-hud.json).
		window.view().bindFunction("batUsesNativeHud", args -> "true");
		window.view().bindFunction("batSetHudPreview", args -> {
			hudPreview = null;
			if (args != null && args.length > 0 && args[0] != null && args[0].length() < 2048) {
				try {
					com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(args[0]);
					if (parsed.isJsonObject()) {
						com.google.gson.JsonObject obj = parsed.getAsJsonObject();
						if (obj.has("id") && obj.has("left") && obj.has("top") && obj.has("right") && obj.has("bottom")) hudPreview = obj;
					}
				} catch (RuntimeException ignored) { }
			}
			return null;
		});
		window.view().bindFunction("batReturnTrail",args->com.batclient.ReturnTrail.action(args!=null&&args.length>0?args[0]:"status"));
        window.view().bindFunction("batWaypoints", args -> com.batclient.Waypoints.action(args!=null&&args.length>0?args[0]:"list",args!=null&&args.length>1?args[1]:""));
        window.view().bindFunction("batGetModuleConflicts", args -> com.batclient.ModuleConflicts.json());
        window.view().bindFunction("batGetHudConfig", args -> HudConfig.json());
		window.view().bindFunction("batProfiles", args -> {
			String error = HudConfig.profileAction(args != null && args.length > 0 ? args[0] : "list", args != null && args.length > 1 ? args[1] : "");
			com.batclient.BatKeybinds.suppressHeld(MinecraftClient.getInstance());
			com.google.gson.JsonObject result = new com.google.gson.JsonObject();
			result.addProperty("error", error); result.addProperty("config", HudConfig.json());
			return result.toString();
		});
        window.view().bindFunction("batServerProfiles", args -> com.batclient.ServerProfiles.action(args!=null&&args.length>0?args[0]:"list",args!=null&&args.length>1?args[1]:""));
		window.view().bindFunction("batGetKeybinds", args -> com.batclient.BatKeybinds.describe(MinecraftClient.getInstance()));
		window.view().bindFunction("batSetKeybind", args -> args != null && args.length >= 2 && com.batclient.BatKeybinds.save(args[0], args[1]) ? HudConfig.json() : "");
		window.view().bindFunction("batSaveHudModule", args -> {
			if (args == null || args.length < 2 || args[1] == null || args[1].length() >= 1_000_000) return "";
			return HudConfig.updateModule(args[0], args[1], true) ? HudConfig.json() : "";
		});
		window.view().bindFunction("batPreviewHudModule", args -> {
			if (args != null && args.length >= 2 && args[1] != null && args[1].length() < 1_000_000) HudConfig.updateModule(args[0], args[1], false);
			return null;
		});
		window.view().bindFunction("batPreviewHudConfig", args -> {
			if (args != null && args.length > 0 && args[0] != null && args[0].length() < 1_000_000) HudConfig.preview(args[0]);
			return null;
		});
		window.view().bindFunction("batSaveHudConfig", args -> {
			String json = (args != null && args.length > 0) ? args[0] : null;
			if (json != null && json.length() < 1_000_000) {
				HudConfig.update(json);
			}
			return null;
		});
	}

	private static Path shotsDir() {
		return MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
	}

	private void handleAction(String action) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (action.startsWith("keybind-capture:")) {
			String id = action.substring("keybind-capture:".length());
			bindingCaptureAction = id.matches("[a-z0-9_]{1,40}") ? id : null;
			return;
		}
		if (action.equals("keybind-cancel")) { bindingCaptureAction = null; return; }
		BatClientMod.LOGGER.info("[bat] web action: {}", action);
		// NOTE: do NOT call WebController.closeWindow() here — this runs inside the
		// view's OWN JS callback (window.batAction). Destroying the view mid-callback
		// can abort the chain before the screen swap. Window teardown happens in
		// removed() on setScreen, and in ClientLifecycleEvents.CLIENT_STOPPING on quit.
		if (action.startsWith("openshot:")) {
			String name = action.substring("openshot:".length());
			if (name.matches("[A-Za-z0-9._-]+")) {
				Path file = shotsDir().resolve(name);
				client.execute(() -> Util.getOperatingSystem().open(file.toUri()));
			}
			return;
		}
		if (action.startsWith("installrespack:")) {
			String id = action.substring("installrespack:".length());
			if (id.matches("[A-Za-z0-9]{1,32}")) {
				startRespackInstall(client, id);
			}
			return;
		}
		switch (action) {
			case "zoom-bind" -> zoomBindingCapture = true;
			case "zoom-bind-cancel" -> zoomBindingCapture = false;
			case "singleplayer" -> client.execute(() -> {
			// If any worlds exist -> show the load list (SelectWorldScreen);
			// otherwise go straight to world creation.
			boolean anyWorlds = false;
			try {
				anyWorlds = client.getLevelStorage().getLevelList().iterator().hasNext();
			} catch (Exception ignored) {
				// failed to list saves: fall through to the load list
			}
			if (anyWorlds) {
				client.setScreen(new SelectWorldScreen(this));
			} else {
				CreateWorldScreen.create(client, this);
			}
		});
			case "multiplayer" -> client.execute(() -> client.setScreen(new MultiplayerScreen(this)));
			case "settings" -> client.execute(() -> {
				if (mode == Mode.PAUSE) {
					// From the pause screen: Done/ESC returns to the pause menu.
					client.setScreen(new OptionsScreen(this, client.options));
				} else {
					// In-game: parent = null so Done/ESC returns to the world — a
					// TitleScreen parent would dump a connected player on the title screen.
					client.setScreen(new OptionsScreen(client.world != null ? null : this, client.options));
				}
			});
			case "quit" -> client.execute(client::scheduleStop);
			case "website" -> client.execute(() ->
					Util.getOperatingSystem().open(URI.create("https://batclient.xyz")));
			case "resume" -> client.execute(() -> {
				// overlay's / pause's "back to game" button
				if (client.world != null) {
					client.setScreen(null);
				}
			});
			// ---- pause screen actions ----
			// Mirrors vanilla GameMenuScreen.disconnect(): world.disconnect(), then
			// MessageScreen(ProgressScreen) for the save, then — critically — the
			// TitleScreen. Bare client.disconnect() leaves ProgressScreen(true):
			// when the save finishes it does setScreen(null) and the window goes
			// black forever (nobody ever shows a title screen).
			case "savequit" -> client.execute(() -> {
				var world = client.world;
				if (world == null) return;
				boolean sp = client.isInSingleplayer();
				ServerInfo serverInfo = client.getCurrentServerEntry();
				world.disconnect();
				if (sp) {
					client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
				} else {
					client.disconnect();
				}
				TitleScreen titleScreen = new TitleScreen();
				if (sp) {
					client.setScreen(titleScreen);
				} else if (serverInfo != null && serverInfo.isRealm()) {
					client.setScreen(new RealmsMainScreen(titleScreen));
				} else {
					client.setScreen(new MultiplayerScreen(titleScreen));
				}
			});
			case "serverlist" -> client.execute(() -> client.setScreen(new MultiplayerScreen(this)));
			case "resourcepacks" -> client.execute(() -> client.setScreen(new PackScreen(
					client.getResourcePackManager(),
					manager -> {
						client.options.refreshResourcePacks(manager);
						client.setScreen(this);
					},
					client.getResourcePackDir(),
					Text.translatable("resourcePack.title"))));
			case "originalpause" -> client.execute(() -> {
				BatClientMod.setPreferOriginalPause(true);
				client.setScreen(new GameMenuScreen(true));
			});
			case "advancements" -> client.execute(() -> {
				if (client.player != null) {
					client.setScreen(new AdvancementsScreen(client.player.networkHandler.getAdvancementHandler(), this));
				}
			});
			case "statistics" -> client.execute(() -> {
				if (client.player != null) {
					client.setScreen(new StatsScreen(this, client.player.getStatHandler()));
				}
			});
			case "opentolan" -> client.execute(() -> {
				if (client.isInSingleplayer()) {
					client.setScreen(new OpenToLanScreen(this));
				}
			});
			case "openworlds" -> client.execute(() ->
					Util.getOperatingSystem().open(client.runDirectory.toPath().resolve("saves").toUri()));
			case "mcfolder" -> client.execute(() ->
					Util.getOperatingSystem().open(client.runDirectory.toPath().toUri()));
			case "openshotsfolder" -> client.execute(() ->
					Util.getOperatingSystem().open(shotsDir().toUri()));
			// HUD editor state: Java's flag tells the HudRenderer feed which
			// body mode to report (hud-edit vs hud-idle) every frame; the page
			// toggles the class itself for immediate feedback.
			case "hudpanel:on" -> hudCovered = true;
			case "hudpanel:off" -> hudCovered = false;
			case "hudedit:on" -> BatClientMod.setHudEditorActive(true);
			case "hudedit:off" -> BatClientMod.setHudEditorActive(false);
			case "hudeditor:open" -> client.execute(() -> {
				if (client.world == null) return;
				WebScreen editor = BatClientMod.cachedOverlay();
				if (editor == null || editor.window() == null) {
					editor = WebController.INSTANCE.createScreen(Mode.OVERLAY);
					if (editor == null) return;
					BatClientMod.setCachedOverlay(editor);
				}
				client.setScreen(editor);
				BatClientMod.setHudEditorActive(true);
			});
			default -> BatClientMod.LOGGER.info("Unhandled web action: {}", action);
		}
	}

	/**
	 * Resource-pack install (resource packs browser): resolve the project's latest
	 * version file on Modrinth, download the zip into the game's resourcepacks dir.
	 * Runs on a background thread; results are marshalled back to the page through
	 * WebController.queueScript (views are thread-affine — never touch them here).
	 */
	private static boolean jsonArrayContains(com.google.gson.JsonArray arr, String s) {
		if (arr == null || s == null) return false;
		for (int i = 0; i < arr.size(); i++) {
			if (arr.get(i).isJsonPrimitive() && s.equals(arr.get(i).getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static void startRespackInstall(MinecraftClient client, String projectId) {
		Thread worker = new Thread(() -> {
			try {
				java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
						.connectTimeout(java.time.Duration.ofSeconds(15))
						.build();
				java.net.http.HttpRequest jsonReq = java.net.http.HttpRequest.newBuilder(
						java.net.URI.create("https://api.modrinth.com/v2/project/" + projectId + "/version"))
						.header("User-Agent", "BatClient/1.0 (https://batclient.xyz)")
						.GET().build();
				String body = http.send(jsonReq, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
				com.google.gson.JsonArray versions = com.google.gson.JsonParser.parseString(body).getAsJsonArray();
				String fileUrl = null;
				String filename = null;
				String chosenVer = null;
				// getGameVersion() returns the launcher --version arg (e.g. "Fabric"
				// in dev runs) — VERSION_NAME is the actual MC release ("1.21.1").
				String mcVer = net.minecraft.SharedConstants.VERSION_NAME;
				if (mcVer == null || mcVer.isEmpty()) {
					mcVer = client.getGameVersion();
				}
				BatClientMod.LOGGER.info("[respacks] install {} for MC {}", projectId, mcVer);
				for (int pass = 0; pass < 2 && fileUrl == null; pass++) {
					for (com.google.gson.JsonElement ver : versions) {
						com.google.gson.JsonObject vo = ver.getAsJsonObject();
						com.google.gson.JsonArray gv = vo.getAsJsonArray("game_versions");
						if (pass == 0) {
							if (!jsonArrayContains(gv, mcVer)) continue;
						} else {
							// pass 2: only packs that declare no game versions at all
							if (gv != null && gv.size() > 0) continue;
						}
						com.google.gson.JsonArray files = vo.getAsJsonArray("files");
						if (files == null) continue;
						for (com.google.gson.JsonElement fe : files) {
							com.google.gson.JsonObject f = fe.getAsJsonObject();
							String fn = f.get("filename").getAsString();
							if (fn.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
								fileUrl = f.get("url").getAsString();
								filename = fn;
								if (vo.has("version_number")) {
									chosenVer = vo.get("version_number").getAsString();
								}
								break;
							}
						}
						if (fileUrl != null) break;
					}
				}
				if (fileUrl == null) {
					finishRespackInstall(false,
							"No version of this pack supports Minecraft " + mcVer + ".");
					return;
				}
				// never trust the remote file name with a path: strip separators,
				// drop Windows-illegal/control chars, reject empty/hidden names.
				filename = filename.replace('\\', '/');
				if (filename.indexOf('/') >= 0) {
					filename = filename.substring(filename.lastIndexOf('/') + 1);
				}
				filename = filename.replaceAll("[<>:\"|?*\\x00-\\x1F]", "").trim();
				while (filename.startsWith(".")) {
					filename = filename.substring(1);
				}
				while (filename.endsWith(".")) {
					filename = filename.substring(0, filename.length() - 1);
				}
				filename = filename.trim();
				if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") || filename.isEmpty()) {
					finishRespackInstall(false, "Modrinth sent an unusable file name.");
					return;
				}
				java.net.http.HttpRequest dl = java.net.http.HttpRequest.newBuilder(
						java.net.URI.create(fileUrl))
						.header("User-Agent", "BatClient/1.0 (https://batclient.xyz)")
						.GET().build();
				java.net.http.HttpResponse<byte[]> resp =
						http.send(dl, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
				if (resp.statusCode() != 200) {
					finishRespackInstall(false, "Download failed (HTTP " + resp.statusCode() + ").");
					return;
				}
				Path dir = client.getResourcePackDir().toAbsolutePath().normalize();
				Files.createDirectories(dir);
				Path out = dir.resolve(filename).normalize();
				if (!out.startsWith(dir)) {
					finishRespackInstall(false, "Blocked path outside resourcepacks.");
					return;
				}
				Files.write(out, resp.body());
				BatClientMod.LOGGER.info("[respacks] installed {} v{} ({} bytes) for {}",
						filename, chosenVer != null ? chosenVer : "?", resp.body().length, projectId);
				finishRespackInstall(true, filename);
			} catch (Exception e) {
				finishRespackInstall(false, e.getMessage() != null ? e.getMessage() : e.toString());
			}
		}, "bat-respack-install");
		worker.setDaemon(true);
		worker.start();
	}

	private static void finishRespackInstall(boolean ok, String message) {
		if (!ok) {
			BatClientMod.LOGGER.warn("[respacks] install failed: {}", message);
		}
		String js = "if (typeof window.__rpInstallDone === 'function') window.__rpInstallDone("
				+ (ok ? "true" : "false") + ", "
				+ new com.google.gson.Gson().toJson(message) + ");";
		WebController.INSTANCE.queueScript(js);
	}

	public WebWindow window() {
		return window;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Drive the Ultralight render loop on the frame thread, then draw the
		// surface. NEVER pump or upload while a loading overlay (SplashOverlay)
		// is drawing: the first GL upload during the splash races the native
		// first paint and crashes the NVIDIA/Kepler driver (nvoglv64 AV). The
		// backdrop fill below still runs, so the vanilla title screen behind
		// the splash can never flash through while the page loads.
		if (MinecraftClient.getInstance().getOverlay() == null) {
			if (mode != Mode.MENU && client != null && client.world != null) {
				applyBlur(delta);
			}
			if (mode == Mode.OVERLAY && !hudCovered && client.world != null) {
				com.batclient.HudRenderer.feedEditor(window, client);
				com.batclient.HudRenderer.drawNative(context, client, true);
			}
			WebController.INSTANCE.updateAndRender();
		}
		if (window == null) {
			return;
		}
		if (!window.hasPainted()) {
			// Solid backdrop until the first successful texture upload (the
			// texture is undefined before that — drawing it would show garbage).
			int w = this.width;
			int h = this.height;
			int color = (mode == Mode.MENU) ? 0xFF000000 : 0x8C000000;
			if (w > 0 && h > 0) {
				context.fill(0, 0, w, h, color);
			}
		} else {
			window.draw(context);
			if (hudPreview != null && client != null) {
				com.batclient.HudRenderer.drawSettingsPreview(context, client, hudPreview);
			}
		}
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (window != null) window.onCursorPos(mouseX, mouseY);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (window != null) window.onCursorPos(mouseX, mouseY);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (bindingCaptureAction != null) { finishBinding(net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(button).getTranslationKey()); return true; }
		if (zoomBindingCapture) {
			finishZoomBinding(net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(button).getTranslationKey());
			return true;
		}
		BatClientMod.LOGGER.info("[bat] click recv x={} y={} btn={}", mouseX, mouseY, button);
		if (window == null) return true;
		window.onMouseButton(button, GLFW.GLFW_PRESS, (int) mouseX, (int) mouseY);
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (window == null) return true;
		window.onMouseButton(button, GLFW.GLFW_RELEASE, (int) mouseX, (int) mouseY);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (window != null) window.onScroll(horizontalAmount, verticalAmount);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (window == null) return true;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && mode == Mode.MENU) {
			try { window.view().evaluateScript("closeAccounts()"); } catch (Throwable ignored) { }
		}
		if (bindingCaptureAction != null) { finishBinding(keyCode == GLFW.GLFW_KEY_ESCAPE ? null : net.minecraft.client.util.InputUtil.fromKeyCode(keyCode,scanCode).getTranslationKey()); return true; }
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && mode == Mode.OVERLAY) {
			zoomBindingCapture = false;
			try { window.view().evaluateScript("dismissGameOverlay()"); } catch (Throwable ignored) { }
			this.close();
			return true;
		}
		if (zoomBindingCapture) {
			finishZoomBinding(keyCode == GLFW.GLFW_KEY_ESCAPE ? null : net.minecraft.client.util.InputUtil.fromKeyCode(keyCode, scanCode).getTranslationKey());
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
			if (mode == Mode.PAUSE) {
				// ESC ladder lives in Java — the page's keydown handler never sees
				// ESC (Ultralight doesn't deliver it while the MC window has focus
				// handling), so query which layer is up and step back one level:
				// module settings -> back to grid (5); mod menu -> close menu (4);
				// screenshot viewer -> close viewer (3); screenshots layer ->
				// close layer (1); resource browser -> back to pause (2);
				// pause -> back to the world (0). Returns 1/2/3/4/5/0 (quote-stripped
				// because the bindings may serialize strings with quotes).
				String state = "0";
				try {
					String raw = window.view().evaluateScript(
							"(function(){try{var m=document.getElementById('mod-settings');"
									+ "if(m&&!m.classList.contains('hidden'))return 5;"
									+ "var p=document.getElementById('panel-layer');"
									+ "if(p&&!p.classList.contains('hidden'))return 4;"
									+ "var v=document.getElementById('shot-viewer');"
									+ "if(v&&!v.classList.contains('hidden'))return 3;"
									+ "var l=document.getElementById('shots-layer');"
									+ "if(l&&!l.classList.contains('hidden'))return 1;"
									+ "if(document.body.classList.contains('respacks-page'))return 2;"
									+ "return 0;}catch(e){return 0;}})()");
					if (raw != null) state = raw.replace("\"", "").trim();
				} catch (Throwable ignored) {
				}
				if ("5".equals(state)) {
					try {
						window.view().evaluateScript("closeModSettings()");
					} catch (Throwable ignored) {
					}
					return true;
				}
				if ("4".equals(state)) {
					try {
						window.view().evaluateScript("closePanel()");
					} catch (Throwable ignored) {
					}
					return true;
				}
				if ("3".equals(state)) {
					try {
						window.view().evaluateScript("closeShotView()");
					} catch (Throwable ignored) {
					}
					return true;
				}
				if ("1".equals(state)) {
					try {
						window.view().evaluateScript("closeShots()");
					} catch (Throwable ignored) {
					}
					return true;
				}
				if ("2".equals(state)) {
					window.loadUrl(WebAssetManager.webRoot().resolve("pause.html").toUri().toString());
					return true;
				}
				this.close();
				return true;
			}
			this.close();
			return true;
		}
		window.onKey(keyCode, scanCode, GLFW.GLFW_PRESS, modifiers);
		return true;
	}

	private void finishZoomBinding(String key) {
		zoomBindingCapture = false;
		if (window != null) window.view().evaluateScript("window.__batKeyCaptured&&window.__batKeyCaptured(" + new com.google.gson.Gson().toJson(key) + ");");
	}

	private void finishBinding(String key) {
		String action = bindingCaptureAction; bindingCaptureAction = null;
		if (window != null) window.view().evaluateScript("window.__batManagerKeyCaptured&&window.__batManagerKeyCaptured(" + new com.google.gson.Gson().toJson(action) + "," + new com.google.gson.Gson().toJson(key) + ");");
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (window == null) return true;
		window.onKey(keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
		return true;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (window != null) window.onCharMods(chr, modifiers);
		return true;
	}

	@Override
	public boolean shouldPause() {
		// Screen.shouldPause() defaults to true (singleplayer freezes on any screen);
		// the Right Shift overlay must keep the world running, the ESC pause must not.
		return mode == Mode.PAUSE;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		// On the main menu (no world) ESC must NOT close: Minecraft turns
		// setScreen(null) into a fresh TitleScreen, and our takeover tick would
		// immediately rebuild the whole web window (reload storm). Only allow
		// ESC when this is an in-game screen (overlay or pause).
		return mode != Mode.MENU;
	}

	/** PAUSE windows are reused on the next ESC; the OVERLAY window stays
	 *  alive while a world is loaded (W2: closing the overlay screen must not
	 *  destroy the in-game HUD — HudRenderer keeps drawing it). */
	private boolean keepWindowAlive() {
		if (mode == Mode.PAUSE) {
			return true;
		}
		return mode == Mode.OVERLAY && MinecraftClient.getInstance().world != null;
	}

	/** Close the window now and drop the reference (world-teardown cleanup).
	 *  Safe to call while another screen is current — the controller is only
	 *  cleared when it points at THIS window. */
	public void destroyWindow() {
		if (window == null) {
			return;
		}
		WebWindow w = window;
		window = null;
		if (WebController.INSTANCE.currentWindow() == w) {
			WebController.INSTANCE.closeWindow();
		} else {
			try {
				w.close();
			} catch (Throwable ignored) {
			}
		}
	}

	@Override
	public void close() {
		// super.close() -> client.setScreen(null) -> removed(), which keeps the
		// window alive when reuse is intended (PAUSE / in-world OVERLAY) and
		// tears it down otherwise. The block below is only a safety net for the
		// case where this screen was not the active one (removed() never fired).
		super.close();
		if (!keepWindowAlive()) {
			destroyWindow();
			BatClientMod.setActiveScreen(null);
		}
	}

	@Override
	public void removed() {
		if (mode == Mode.OVERLAY || mode == Mode.PAUSE) HudConfig.flush();
		super.removed();
		zoomBindingCapture = false;
		bindingCaptureAction = null;
		// Leaving any web screen ends editor mode; the HudRenderer feed then
		// switches the page to its plain HUD state (body.hud-idle).
		BatClientMod.setHudEditorActive(false);
		if (keepWindowAlive()) {
			// Immediately clean the page state so the one-frame gap between
			// removed() and the next 20Hz feed never shows stale editor chrome
			// (the "split second overlay + HUD editor" flash on pause resume).
			if (window != null && !window.isClosed()) {
				try {
					window.view().evaluateScript(
							"typeof toggleHudEditor==='function'&&toggleHudEditor(false);"
									+ "typeof closePanel==='function'&&closePanel(false)");
				} catch (Throwable ignored) {
				}
			}
			// Keep BOTH the view and this screen's window reference intact:
			// PAUSE re-adopts on the next ESC, OVERLAY keeps rendering as the
			// in-game HUD (HudRenderer pumps/draws it while no screen is up).
			// The view is torn down by WebController.createScreen() when the
			// cache no longer holds it, by world teardown, or at shutdown.
			BatClientMod.setActiveScreen(null);
			return;
		}
		destroyWindow();
		BatClientMod.setActiveScreen(null);
	}
}
