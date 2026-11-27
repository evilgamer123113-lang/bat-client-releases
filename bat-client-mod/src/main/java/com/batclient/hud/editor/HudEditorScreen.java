package com.batclient.hud.editor;

import com.batclient.BatClientMod;
import com.batclient.HudConfig;
import com.batclient.hud.HudLayoutEngine;
import com.batclient.hud.layout.AbsoluteBounds;
import com.batclient.hud.layout.AnchorPoint;
import com.batclient.hud.layout.HudComponentState;
import com.batclient.hud.layout.HudCoordinateResolver;
import com.batclient.hud.snap.SnapResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline B — interactive HUD editor Screen.
 *
 * Overrides mouse capture, ungrabs the cursor, intercepts click/drag/release,
 * and draws per-component bounding frames:
 *   gray = default, light blue = hovered, bright green = dragged.
 * Alignment guides (screen center + inter-module edges) render as dotted lines.
 *
 * Toggle: ClientMods.hudEditorKey (default Right-Shift) or the web menu's
 * "Move on HUD" action. Escape / same key closes and persists.
 */
public final class HudEditorScreen extends Screen {

	private final HudEditorController controller;
	private final List<HudComponentState> states;
	private final Map<String, float[]> measureCache = new HashMap<>();
	private float sw;
	private float sh;

	public HudEditorScreen() {
		super(Text.translatable("batclient.hud.editor"));
		sw = 0;
		sh = 0;
		states = HudLayoutEngine.loadStates(1, 1); // sizes refreshed in init
		controller = new HudEditorController(new HudEditorController.LayoutSource() {
			@Override
			public float screenW() {
				return sw;
			}

			@Override
			public float screenH() {
				return sh;
			}

			@Override
			public List<HudComponentState> modules() {
				return states;
			}

			@Override
			public float contentW(HudComponentState m) {
				return measure(m)[0];
			}

			@Override
			public float contentH(HudComponentState m) {
				return measure(m)[1];
			}

			@Override
			public java.util.function.Consumer<HudComponentState> onCommit() {
				return st -> HudLayoutEngine.persist(st, sw, sh);
			}
		});
	}

	@Override
	protected void init() {
		super.init();
		sw = width;
		sh = height;
		// Re-resolve percent migration with real screen size once.
		float oldSw = sw;
		float oldSh = sh;
		List<HudComponentState> fresh = HudLayoutEngine.loadStates(sw, sh);
		states.clear();
		states.addAll(fresh);
		controller.updateBounds();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Dim world slightly so frames read clearly (no full GUI background).
		ctx.fill(0, 0, width, height, 0x40000000);

		controller.updateBounds();
		controller.setSnappingEnabled(!hasShiftDown());
		controller.onMouseMove(mouseX, mouseY);

		TextRenderer fr = client != null ? client.textRenderer : null;
		for (HudComponentState st : states) {
			if (!st.enabled) continue;
			AbsoluteBounds b = HudCoordinateResolver.bounds(st, sw, sh);
			drawModulePreview(ctx, st, b);
			int color = HudEditorController.frameColor(controller.frameStateFor(st.id));
			drawFrame(ctx, b, color);
		}

		// Guides
		SnapResult snap = controller.lastSnap();
		if (controller.isDragging()) {
			if (snap.drawVerticalCenterLine) {
				drawDottedV(ctx, (int) (sw * 0.5f), 0, height, 0xB3FFFFFF);
			}
			if (snap.drawHorizontalCenterLine) {
				drawDottedH(ctx, (int) (sh * 0.5f), 0, width, 0xB3FFFFFF);
			}
			if (snap.hasVerticalGuide) {
				drawDottedV(ctx, (int) snap.verticalGuideX, 0, height, 0xE6FFFFFF);
			}
			if (snap.hasHorizontalGuide) {
				drawDottedH(ctx, (int) snap.horizontalGuideY, 0, width, 0xE6FFFFFF);
			}
		}

		// Hint line
		if (fr != null) {
			String hint = "HUD Editor  |  Shift: free move  |  R-Shift / Esc: close";
			ctx.drawText(fr, hint, 8, 8, 0xFFFFFFFF, true);
			String hovered = controller.hoveredId();
			if (hovered != null) {
				ctx.drawText(fr, "hover: " + hovered, 8, 18, 0xFFFFFFFF, true);
			}
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawModulePreview(DrawContext ctx, HudComponentState st, AbsoluteBounds b) {
		// Lightweight ghost fill so the editor is usable before full draw parity.
		int fill = controller.frameStateFor(st.id) == HudEditorController.FrameState.DRAGGED
				? 0x552FD06F : 0x5508080B;
		int x0 = MathHelper.floor(b.minX);
		int y0 = MathHelper.floor(b.minY);
		int x1 = MathHelper.ceil(b.maxX);
		int y1 = MathHelper.ceil(b.maxY);
		if (x1 > x0 && y1 > y0) {
			ctx.fill(x0, y0, x1, y1, fill);
		}
		if (client != null) {
			// Scale label under the box
			String label = st.id + "  " + String.format(java.util.Locale.ROOT, "%.1fx", st.scale);
			ctx.drawText(client.textRenderer, label, x0, y1 + 2, 0xFFCCCCCC, false);
		}
	}

	private void drawFrame(DrawContext ctx, AbsoluteBounds b, int color) {
		int x0 = MathHelper.floor(b.minX) - 1;
		int y0 = MathHelper.floor(b.minY) - 1;
		int x1 = MathHelper.ceil(b.maxX) + 1;
		int y1 = MathHelper.ceil(b.maxY) + 1;
		// 1px hollow rect
		ctx.drawBorder(x0, y0, x1 - x0, y1 - y0, color);
	}

	private void drawDottedV(DrawContext ctx, int x, int y0, int y1, int color) {
		for (int y = y0; y < y1; y += 4) {
			ctx.fill(x, y, x + 1, Math.min(y + 2, y1), color);
		}
	}

	private void drawDottedH(DrawContext ctx, int y, int x0, int x1, int color) {
		for (int x = x0; x < x1; x += 4) {
			ctx.fill(x, y, Math.min(x + 2, x1), y + 1, color);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (controller.onMouseDown((float) mouseX, (float) mouseY)) {
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			controller.onMouseUp((float) mouseX, (float) mouseY);
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
			double deltaX, double deltaY) {
		if (button == 0 && controller.isDragging()) {
			controller.setSnappingEnabled(!hasShiftDown());
			controller.onMouseMove((float) mouseX, (float) mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		// Scroll scales the hovered/dragged module (0.5 – 3.0).
		String id = controller.draggedId() != null ? controller.draggedId() : controller.hoveredId();
		if (id != null) {
			for (HudComponentState st : states) {
				if (id.equals(st.id)) {
                    if(st.locked)return true;
					float step = (float) (vertical > 0 ? 0.1 : -0.1);
					st.scale = MathHelper.clamp(st.scale + step, 0.5f, 3.0f);
					controller.updateBounds();
					HudLayoutEngine.persist(st, sw, sh);
					return true;
				}
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void close() {
		controller.cancelDrag();
		// Persist everything on close.
		for (HudComponentState st : states) {
			HudLayoutEngine.persist(st, sw, sh);
		}
		if (client != null) {
			client.setScreen(null);
		}
	}

	private float[] measure(HudComponentState st) {
		float[] cached = measureCache.get(st.id);
		if (cached != null) return cached;
		TextRenderer fr = client != null ? client.textRenderer : null;
		float w = 40f;
		float h = 12f;
		if (fr != null) {
			switch (st.id) {
				case "fps" -> { w = fr.getWidth("144 FPS"); h = fr.fontHeight; }
				case "coords" -> { w = fr.getWidth("X: -125  Y: 64  Z: -340"); h = fr.fontHeight; }
				case "ping" -> { w = fr.getWidth("888 ms"); h = fr.fontHeight; }
				case "cps" -> { w = fr.getWidth("LMB 5 CPS   RMB 2 CPS"); h = fr.fontHeight; }
				case "togglesprint" -> { w = fr.getWidth("Sprinting (Toggled)"); h = fr.fontHeight; }
				case "potions" -> { w = fr.getWidth("Speed II  1:24"); h = fr.fontHeight * 2f + 1f; }
				case "armor" -> { w = 50f; h = 16f * 3f; }
				case "durability" -> { w = 90f; h = 16f + fr.fontHeight + 2f; }
				case "target" -> { w = fr.getWidth("Zombie"); h = fr.fontHeight * 2f + 1f; }
				case "crosshair" -> { w = 22f; h = 22f; }
				case "keystrokes" -> { w = 74f; h = 50f; }
				default -> { }
			}
		}
		float[] r = { w, h };
		measureCache.put(st.id, r);
		return r;
	}

	/** Open helper used by ClientMods keybind / web bridge. */
	public static void open(MinecraftClient client) {
		if (client == null) return;
		client.setScreen(new HudEditorScreen());
	}
}
