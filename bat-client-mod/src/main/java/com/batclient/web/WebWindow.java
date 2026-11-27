package com.batclient.web;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nilsen84.ultralight.KeyEvent;
import io.github.nilsen84.ultralight.UltralightRenderer;
import io.github.nilsen84.ultralight.UltralightView;
import io.github.nilsen84.ultralight.lwjgl.LwjglKeyMapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.EnumSet;

/**
 * Wraps a single Ultralight view: owns the GL texture, forwards all game input to
 * the view, copies the finished surface pixels into a texture and draws it as a
 * full-screen BGRA8 premultiplied quad via batclient:bgra_position_tex.
 */
public class WebWindow implements AutoCloseable {
    private static final java.util.Set<WebWindow> LIVE = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private boolean closed;
    public static void closeAll() { for (WebWindow window : java.util.List.copyOf(LIVE)) window.close(); }
	private static final Logger LOGGER = LoggerFactory.getLogger("batclient-web");

	private final UltralightView view;
	private int texture;
	private int lastWidth = -1;
	private int lastHeight = -1;
	private int pbo;
	private ByteBuffer staging;
	private int stagingCap;
	private volatile boolean debugLogged;
	private volatile boolean painted;
	private int uploadDiag;

	private double lastMouseX;
	private double lastMouseY;
	private float xScale = 1.0f;
	private float yScale = 1.0f;

	public WebWindow(UltralightRenderer renderer, int width, int height) {
		this.lastWidth = Math.max(1, width);
		this.lastHeight = Math.max(1, height);
		this.view = renderer.createView(this.lastWidth, this.lastHeight);
		this.texture = GL11.glGenTextures();
		this.view.focus();
		resizeTexture(this.lastWidth, this.lastHeight);
		LIVE.add(this);
	}

	public UltralightView view() {
		if(closed)throw new IllegalStateException("Native web view is closed");
		return view;
	}

	public boolean isClosed(){return closed;}

	public void loadUrl(String url) {
		if(closed)return;
		LOGGER.info("Loading {}", url);
		view.loadUrl(url);
	}

	public void windowContentScaleCallback(float xScale, float yScale) {
		this.xScale = xScale;
		this.yScale = yScale;
	}

	/**
	 * Render the Ultralight frame (must be called on the render thread, every frame)
	 * and upload the surface into the GL texture.
	 */
	public boolean needsResize() {
		var window = MinecraftClient.getInstance().getWindow();
		return window.getFramebufferWidth() != lastWidth || window.getFramebufferHeight() != lastHeight;
	}

	/** Resize before Ultralight paints, rather than discarding its freshly painted frame. */
	public void prepareFrame() {
		if(closed)return;
		int width = MinecraftClient.getInstance().getWindow().getFramebufferWidth();
		int height = MinecraftClient.getInstance().getWindow().getFramebufferHeight();

		if (width != lastWidth || height != lastHeight) {
			lastWidth = width;
			lastHeight = height;
			view.resize(width, height);
			resizeTexture(width, height);
		}
	}

	public void renderAndUpload() {
		if(closed)return;

		// Surface is BGRA8 premultiplied alpha.
		io.github.nilsen84.ultralight.UltralightSurface surface = view.surface();
		io.github.nilsen84.ultralight.IntRect dirty = surface.dirtyBounds();
		if (!dirty.isValid() || view.isLoading()) {
			return;
		}
		int sw = view.width();
		int sh = view.height();
		if (sw <= 0 || sh <= 0) {
			return;
		}

		int left = Math.max(0, dirty.getLeft());
		int top = Math.max(0, dirty.getTop());
		int w = Math.min(dirty.getWidth(), sw - left);
		int h = Math.min(dirty.getHeight(), sh - top);
		if (w <= 0 || h <= 0) {
			return;
		}

		int rowBytes = surface.rowBytes();

		// Copy the dirty region into our OWN padded staging buffer. Ultralight's
		// software compositor runs on other threads and this old NVIDIA/Kepler
		// driver keeps reading the source asynchronously (even past our exact
		// pointer). A staged copy means we never hand it memory that can be freed
		// or resized underneath; the +4096 padding absorbs any driver over-read.
		int dirtyBytes = w * 4 * h;
		if (stagingCap < dirtyBytes + 4096) {
			stagingCap = Math.max(dirtyBytes + 4096, stagingCap * 2);
			staging = ByteBuffer.allocateDirect(stagingCap).order(java.nio.ByteOrder.nativeOrder());
		}
		staging.clear();

		try (io.github.nilsen84.ultralight.UltralightBuffer buffer = surface.lockPixels()) {
			ByteBuffer pixels = buffer.asByteBuffer();
			int capacity = pixels.capacity();
			if (debugLogged == false && (debugLogged = true)) {
				// Alpha of surface pixel (0,0): 255 = opaque page (main menu),
				// 0 = transparent page (in-game overlay, world visible behind).
				int cornerAlpha = pixels.get(3) & 0xFF;
				LOGGER.warn("upload first-frame: view={}x{} alpha0={} dirty=({},{},{},{}) rowBytes={} loading={}",
						sw, sh, cornerAlpha, dirty.getLeft(), dirty.getTop(), dirty.getRight(), dirty.getButtom(),
						rowBytes, view.isLoading());
				// Decide if the script declarations clobbered the native bridge:
				// a native binding stringifies as "[native code]", a page function
				// as "function batAction(action) {...}".
				try {
					String probe = view.evaluateScript(
							"(function(){ try { var t = typeof window.batAction; " +
							"var s = t === 'function' ? String(window.batAction) : '-'; " +
							"return t + '|' + s.slice(0, 48) + '|' + (typeof window.batGetPlayer); " +
							"} catch (e) { return 'ERR:' + e; } })()");
					LOGGER.warn("bridge probe: {}", probe);
				} catch (Throwable t) {
					LOGGER.warn("bridge probe failed: {}", t.toString());
				}
			}
			if (left == 0 && w * 4 == rowBytes) {
				pixels.position(top * rowBytes);
				pixels.limit(top * rowBytes + dirtyBytes);
				staging.put(pixels);
			} else for (int y = top; y < top + h; y++) {
				pixels.limit(capacity);
				pixels.position(y * rowBytes + left * 4);
				pixels.limit((y * rowBytes + left * 4) + w * 4);
				staging.put(pixels);
			}
		}

		// Restore raw GL state so Minecraft's texture cache stays consistent.
		int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int previousPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
		int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
		int previousRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
		int previousSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
		int previousSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
		try {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		// Reset ALL pixel-store state: Minecraft's own texture uploads
		// (fonts/atlas) can leave GL_UNPACK_ROW_LENGTH / SKIP_* dirty, which
		// makes the driver compute a source size larger than our PBO
		// (=> GL_INVALID_OPERATION "Invalid PBO operation") and made client-
		// memory uploads over-read past the buffer (nvoglv64 AV).
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);

		// Upload through a pixel-pack buffer (GPU-side copy). The Kepler DMA
		// helper misfires on tight 4-byte-aligned client rows (read past the
		// allocation), but a PBO avoids client-memory DMA entirely. The PBO is
		// re-allocated every frame to exactly dirtyBytes so the driver never
		// sees an undersized/oversized buffer (GL_INVALID_OPERATION otherwise).
		GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
		staging.flip();
		GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, dirtyBytes, GL15.GL_STREAM_DRAW);
		boolean diag = uploadDiag < 5;
		int eBufData = diag ? GL11.glGetError() : 0;
		GL15.glBufferSubData(GL21.GL_PIXEL_UNPACK_BUFFER, 0, staging);
		int eSubData = diag ? GL11.glGetError() : 0;
		GL11.glTexSubImage2D(
				GL11.GL_TEXTURE_2D, 0,
				left, top, w, h,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
				0L
		);
		if (diag) {
			int eTex = GL11.glGetError();
			int rowLen = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
			int pboBind = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
			int pboSize = pboBind != 0 ? GL15.glGetBufferParameteri(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_BUFFER_SIZE) : -1;
			LOGGER.warn("upload diag #{}: bufData=0x{} subData=0x{} texSub=0x{} unpackRowLength={} pbo={} pboSize={} need={}",
					uploadDiag, Integer.toHexString(eBufData), Integer.toHexString(eSubData), Integer.toHexString(eTex),
					rowLen, pbo, pboSize, dirtyBytes);
			uploadDiag++;
		} else {
			while (GL11.glGetError() != 0) {
				// drain errors so the debug callback doesn't spam
			}
		}
		} finally {
			GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousPbo);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, previousRowLength);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, previousSkipPixels);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, previousSkipRows);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
			staging.clear();
		}

		surface.clearDirtyBounds();
		// First successful GL upload is done: the texture now carries real
		// pixels, so WebScreen may stop drawing the solid backdrop.
		painted = true;
	}

	public void draw(DrawContext context) {
		draw(context, 0, 0, MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
	}

	/** Draw a small web surface without stretching it across the whole screen. */
	public void draw(DrawContext context, float left, float top, float width, float height) {
		drawRegion(context, left, top, width, height, 0, 0, 1, 1);
	}

	public void drawRegion(DrawContext context, float left, float top, float width, float height, float u1, float v1, float u2, float v2) {
		if(closed)return;
		ShaderProgram shader = BatClientShaders.bgraPositionTextureShader;
		if (shader == null) {
			return;
		}
		ShaderProgram previousShader = RenderSystem.getShader();
		int previousShaderTexture = RenderSystem.getShaderTexture(0);
		boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
		int previousSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
		int previousDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		int previousSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
		int previousDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		try {

		// Alpha-blend the surface over the game. The in-game overlay page is
		// mostly transparent (alpha=0 pixels where it draws nothing), so the
		// quad must composite instead of overwrite. The Ultralight surface is
		// PREMULTIPLIED (verified: 50% red = R128/A128) -> blend ONE,
		// 1-srcAlpha. MC does not apply the "blend" section of core shader
		// JSON (ShaderProgram has no blend handling; vanilla position_tex.json
		// carries none), so the state is set explicitly here.
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShader(() -> shader);

		float x1 = left, y1 = top;
		float x2 = left + width, y2 = top + height;
		float z = 0;

		var matrix = context.getMatrices().peek().getPositionMatrix();
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
		builder.vertex(matrix, x1, y1, z).texture(u1, v1);
		builder.vertex(matrix, x1, y2, z).texture(u1, v2);
		builder.vertex(matrix, x2, y2, z).texture(u2, v2);
		builder.vertex(matrix, x2, y1, z).texture(u2, v1);
		BufferRenderer.drawWithGlobalProgram(builder.end());

		// Native rows and widgets continue drawing after the web decorations.
		} finally {
			RenderSystem.setShaderTexture(0, previousShaderTexture);
			RenderSystem.setShader(() -> previousShader);
			GlStateManager._blendFuncSeparate(previousSrcRgb, previousDstRgb, previousSrcAlpha, previousDstAlpha);
			if (!previousBlend) RenderSystem.disableBlend();
		}
	}

	// ---- input forwarding (UI coords -> framebuffer pixels) ----

	public void onCursorPos(double x, double y) {
		if(closed)return;
		double nextX = x * xScale;
		double nextY = y * yScale;
		if ((int) nextX != (int) this.lastMouseX || (int) nextY != (int) this.lastMouseY) {
			WebController.INSTANCE.requestUiFrame();
		}
		this.lastMouseX = nextX;
		this.lastMouseY = nextY;

		int button = pressedButton();
		if(button!=3)WebController.INSTANCE.requestUiFrame();
		view.fireMouseMoveEvent(button, (int) lastMouseX, (int) lastMouseY);
	}

	public void onMouseButton(int glfwButton, int action, int x, int y) {
		if(closed)return;
		WebController.INSTANCE.requestUiFrame();
		int button = switch (glfwButton) {
			case GLFW.GLFW_MOUSE_BUTTON_LEFT -> 0;
			case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> 1;
			case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> 2;
			default -> 3; // none
		};
		boolean down = action == GLFW.GLFW_PRESS;
		view.fireMouseButtonEvent(button, down, (int) (x * xScale), (int) (y * yScale));
	}

	public void onScroll(double horizontalAmount, double verticalAmount) {
		if(closed)return;
		WebController.INSTANCE.requestUiFrame();
		// Ultralight scroll is in pixels; scale wheel deltas like the reference (x50).
		int deltaY = (int) (verticalAmount * 50);
		if (deltaY != 0) {
			view.fireScrollEvent(deltaY);
		}
	}

	public void onCharMods(int codepoint, int mods) {
		if(closed)return;
		WebController.INSTANCE.requestUiFrame();
		String text = new String(Character.toChars(codepoint));
		view.fireKeyEvent(new KeyEvent(
				KeyEvent.Type.Char, modifiers(mods),
				0, 0, text, text, false, false, false
		));
	}

	public void onKey(int glfwKey, int scanCode, int action, int mods) {
		if(closed)return;
		WebController.INSTANCE.requestUiFrame();
		KeyEvent.Type type = switch (action) {
			case GLFW.GLFW_PRESS -> KeyEvent.Type.KeyDown;
			case GLFW.GLFW_RELEASE -> KeyEvent.Type.KeyUp;
			default -> null;
		};
		if (type == null) {
			return;
		}

		// Enter and Tab carry no char callback in MC, synthesize them like the reference.
		boolean synthesizeChar = type == KeyEvent.Type.KeyDown
				&& (glfwKey == GLFW.GLFW_KEY_ENTER || glfwKey == GLFW.GLFW_KEY_TAB);

		// Hardcode common editing VKs — LwjglKeyMapper misses Backspace/Delete
		// so the search field can't erase.
		int virtualKey = switch (glfwKey) {
			case GLFW.GLFW_KEY_BACKSPACE -> 8;
			case GLFW.GLFW_KEY_DELETE -> 46;
			case GLFW.GLFW_KEY_ENTER -> 13;
			case GLFW.GLFW_KEY_TAB -> 9;
			case GLFW.GLFW_KEY_ESCAPE -> 27;
			case GLFW.GLFW_KEY_LEFT -> 37;
			case GLFW.GLFW_KEY_RIGHT -> 39;
			case GLFW.GLFW_KEY_UP -> 38;
			case GLFW.GLFW_KEY_DOWN -> 40;
			case GLFW.GLFW_KEY_HOME -> 36;
			case GLFW.GLFW_KEY_END -> 35;
			case GLFW.GLFW_KEY_PAGE_UP -> 33;
			case GLFW.GLFW_KEY_PAGE_DOWN -> 34;
			default -> LwjglKeyMapper.INSTANCE.toUltralight(glfwKey).getCode();
		};
		view.fireKeyEvent(new KeyEvent(
				type, modifiers(mods),
				virtualKey, scanCode, "", "", false, false, false
		));

		if (synthesizeChar) {
			String text = glfwKey == GLFW.GLFW_KEY_ENTER ? "\n" : "\t";
			view.fireKeyEvent(new KeyEvent(
					KeyEvent.Type.Char, modifiers(mods),
					0, 0, text, text, false, false, false
			));
		}
	}

	private static EnumSet<KeyEvent.Modifier> modifiers(int mods) {
		EnumSet<KeyEvent.Modifier> set = EnumSet.noneOf(KeyEvent.Modifier.class);
		if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) set.add(KeyEvent.Modifier.Shift);
		if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) set.add(KeyEvent.Modifier.Ctrl);
		if ((mods & GLFW.GLFW_MOD_ALT) != 0) set.add(KeyEvent.Modifier.Alt);
		if ((mods & GLFW.GLFW_MOD_SUPER) != 0) set.add(KeyEvent.Modifier.Meta);
		return set;
	}

	/** Returns the currently-held mouse button for drag events (3 = none). */
	private int pressedButton() {
		long handle = MinecraftClient.getInstance().getWindow().getHandle();
		if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) return 0;
		if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) return 1;
		if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS) return 2;
		return 3;
	}

	private void resizeTexture(int width, int height) {
		int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int previousPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
		try {
		GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		} finally {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
			GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousPbo);
		}

		if (pbo == 0) {
			pbo = GL15.glGenBuffers();
		}
	}

	@Override
	public void close() {
        if (closed) return;
        closed = true;
        LIVE.remove(this);
		try {
			// The binding deletes listener wrappers before releasing the view.
            // Detach them first so native teardown cannot call dangling listeners.
            view.setLoadListener(null);
            view.setViewListener(null);
			view.close();
		} catch (Throwable ignored) {
		}
		if (pbo != 0) {
			GL15.glDeleteBuffers(pbo);
			pbo = 0;
		}
		if (texture != 0) {
			// Invalidate Minecraft's cached binding when GL recycles texture IDs.
			GlStateManager._deleteTexture(texture);
			texture = 0;
		}
	}

	public boolean hasPainted() {
		return painted;
	}
}
