package com.batclient.web;

import io.github.nilsen84.ultralight.Ultralight;
import io.github.nilsen84.ultralight.UltralightLoader;
import io.github.nilsen84.ultralight.UltralightRenderer;
import java.io.File;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton owning the Ultralight renderer + render loop lifecycle.
 * Call {@link #updateAndRender()} once per frame on the render thread.
 */
public final class WebController {
	public static final WebController INSTANCE = new WebController();
	private static final Logger LOGGER = LoggerFactory.getLogger("batclient-web");

	private UltralightRenderer renderer;
	/** One blank 1x1 view for the renderer's lifetime, never a page/overlay/GL texture.
	 * This binding has no renderer-dispose API. Releasing its last view before JVM
	 * teardown fast-fails in native code, even in a standalone reproducer. Keep
	 * one engine-owned view until natural renderer/process teardown; ordinary
	 * menu views still close normally, so their memory cannot accumulate. */
	private io.github.nilsen84.ultralight.UltralightView rendererLifetimeView;
	private WebWindow window;
	private boolean nativesReady;
	private boolean nativesAttempted;
	private boolean rendererAttempted;
    private boolean stopping;
    public synchronized void shutdown() {
        LOGGER.info("[shutdown] Closing native web views on {}",Thread.currentThread().getName());
        stopping = true;
        pendingScripts.clear();
        WebWindow.closeAll();
        window = null;
        // rendererLifetimeView belongs to the native renderer's lifetime. Closing
        // it here recreates the upstream last-view teardown failure.
        LOGGER.info("[shutdown] Native web views closed");
    }

	private WebController() {
	}

	/**
	 * The ultralight-bindings loader extracts the DLLs into a NEW temp dir named
	 * {@code ultralight-internal-natives-*} on every JVM run and never cleans them
	 * up. Sweep leftover dirs from previous runs BEFORE loading so a player's
	 * machine never accumulates a pile of ~45MB native dirs.
	 */
	public static void cleanStaleNatives() {
		String tmp = System.getProperty("java.io.tmpdir");
		if (tmp == null || tmp.isEmpty()) {
			return;
		}
		File base = new File(tmp);
		File[] dirs = base.listFiles((d, name) -> name.startsWith("ultralight-internal-natives-"));
		if (dirs == null || dirs.length == 0) {
			return;
		}
		for (File dir : dirs) {
			try {
				if (deleteRecursively(dir)) {
					LOGGER.info("Cleaned stale Ultralight natives dir: {}", dir);
				}
			} catch (Throwable t) {
				LOGGER.warn("Could not clean stale Ultralight natives dir {}: {}", dir, t.toString());
			}
		}
	}

	/** Expected on a background thread: extracts + loads the native DLLs (pure JVM/native, no GL). */
	public synchronized void loadNatives() {
		if (renderer != null || nativesReady || nativesAttempted) {
			return;
		}
		nativesAttempted = true;
		try {
			cleanStaleNatives();
			UltralightLoader.INSTANCE.load();
			nativesReady = true;
			LOGGER.info("Ultralight natives loaded");
		} catch (Throwable t) {
			LOGGER.error("Failed to load Ultralight natives", t);
		}
	}

	/** Best-effort recursive delete; false if anything could not be removed. */
	private static boolean deleteRecursively(File root) {
		File[] children = root.listFiles();
		if (children != null) {
			for (File child : children) {
				if (!deleteRecursively(child)) {
					return false;
				}
			}
		}
		return !root.exists() || root.delete();
	}

	/**
	 * Must run on the client/render thread (Ultralight views are thread-affine to the
	 * thread that creates the renderer). Cheap no-op callable every tick once ready.
	 */
	public synchronized void ensureRenderer() {
		if (stopping) return;
		if (renderer != null || !nativesReady || rendererAttempted) {
			return;
		}
		rendererAttempted = true;
		try {
			UltralightRenderer created = Ultralight.INSTANCE.createRenderer(
					new BatWebFileSystem(),
					new BatWebLogger(),
					new BatWebClipboard()
			);
			rendererLifetimeView = created.createView(1, 1);
			renderer = created;
			LOGGER.info("Ultralight renderer created");
		} catch (Throwable t) {
			LOGGER.error("Failed to create Ultralight renderer", t);
		}
	}

	public boolean isReady() {
		return renderer != null;
	}

	public UltralightRenderer getRenderer() {
		return renderer;
	}

	public synchronized WebScreen createScreen() {
		return createScreen(null);
	}

	public synchronized WebScreen createScreen(WebScreen.Mode mode) {
		if (!isReady()) {
			return null;
		}
		// Close the previous window ONLY when no warm cache references it — the
		// cached pause/overlay windows survive so the next ESC (pause reuse) or
		// the in-game HUD (HudRenderer) can re-adopt them instead of reloading
		// their page. Caches are invalidated by world teardown, not here.
		if (window != null && !com.batclient.BatClientMod.isCachedWindow(window)) {
			try {
				window.close();
			} catch (Throwable ignored) {
			}
			window = null;
		}
		WebScreen screen = new WebScreen(mode);
		screen.initView();
		window = screen.window();
		return screen;
	}

	/** Re-attach a WebScreen's revived window (after removed()/init() rebuild) so
	 *  updateAndRender() keeps feeding it. */
	public synchronized void adoptWindow(WebWindow w) {
		this.window = w;
		uiFrameClock.invalidate();
	}

	private final UiFrameClock uiFrameClock = new UiFrameClock();

	/** Clicks and typing need immediate feedback, even between scheduled UI frames. */
	public void requestUiFrame() {
		uiFrameClock.invalidate();
	}

	/** Queue a JS snippet to be evaluated on the render thread inside updateAndRender().
	 *  Ultralight views are thread-affine — background threads (e.g. the resource-pack
	 *  downloader) must never touch the view directly, only through this queue. */
	public void queueScript(String script) {
		pendingScripts.add(script);
	}

	private final java.util.List<String> pendingScripts =
			java.util.Collections.synchronizedList(new java.util.ArrayList<>());

	public synchronized WebWindow currentWindow() {
		return window;
	}

	public void updateAndRender() {
        if (stopping) return;
		if (renderer == null) {
			return;
		}
		long now = System.nanoTime();
		// While an interactive web screen (Mod Menu / Pause / Main Menu) is open,
		// pump Ultralight on every render frame so scrolling, hover, and animations
		// match the game's display refresh rate with zero frame-clock beat stutter.
		boolean interactiveScreen = com.batclient.BatClientMod.getActiveScreen() != null;
		boolean urgent = interactiveScreen || window == null || !window.hasPainted() || window.view().isLoading()
				|| window.needsResize() || !pendingScripts.isEmpty();
		if (!uiFrameClock.shouldRender(now, urgent)) return;
		if (window != null) window.prepareFrame();
		// Deliver scripts queued by background threads first (they call back into
		// the page, e.g. resource-pack install results) while the view is valid.
		if (!pendingScripts.isEmpty()) {
			String[] batch;
			synchronized (pendingScripts) {
				batch = pendingScripts.toArray(new String[0]);
				pendingScripts.clear();
			}
			if (window != null) {
				for (String js : batch) {
					try {
						window.view().evaluateScript(js);
					} catch (Throwable t) {
						LOGGER.warn("queueScript failed: {}", t.toString());
					}
				}
			}
		}
		renderer.update();
		renderer.refreshDisplay();
		renderer.render();

		if (window != null) {
			window.renderAndUpload();
		}
	}

	public synchronized void closeWindow() {
		if (window != null) {
			try {
				window.close();
			} catch (Throwable ignored) {
			}
			window = null;
		}
	}

	/** @return preferred view size in physical framebuffer pixels. */
	public static int framebufferWidth() {
		return MinecraftClient.getInstance().getWindow().getFramebufferWidth();
	}

	public static int framebufferHeight() {
		return MinecraftClient.getInstance().getWindow().getFramebufferHeight();
	}
}
