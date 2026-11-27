package com.batclient.hud.layout;

/**
 * Pure coordinate math. Translates anchor-relative component state into an
 * absolute AABB in canvas/screen space. No rendering APIs, no Minecraft types
 * — unit-testable in isolation.
 *
 * Anchor map (Ax, Ay):
 *   TOP_LEFT      -> (0, 0)          TOP_CENTER    -> (W/2, 0)      TOP_RIGHT     -> (W, 0)
 *   CENTER_LEFT   -> (0, H/2)        CENTER        -> (W/2, H/2)    CENTER_RIGHT  -> (W, H/2)
 *   BOTTOM_LEFT   -> (0, H)          BOTTOM_CENTER -> (W/2, H)      BOTTOM_RIGHT  -> (W, H)
 *
 * Absolute position:
 *   absX = Ax + offsetX
 *   absY = Ay + offsetY
 *   scaledW = baseModuleWidth  * scale
 *   scaledH = baseModuleHeight * scale
 */
public final class HudCoordinateResolver {

	private HudCoordinateResolver() {
	}

	/** Anchor origin in screen space. */
	public static float anchorX(AnchorPoint a, float screenW) {
		return a.anchorX(screenW);
	}

	public static float anchorY(AnchorPoint a, float screenH) {
		return a.anchorY(screenH);
	}

	/** Top-left of the scaled module AABB. */
	public static float absX(HudComponentState state, float screenW) {
		return anchorX(state.anchor, screenW) + state.offsetX;
	}

	public static float absY(HudComponentState state, float screenH) {
		return anchorY(state.anchor, screenH) + state.offsetY;
	}

	/**
	 * Full AABB for a component given its measured (unscaled) content size.
	 * contentW/H are the module's natural pixel size at scale=1.
	 */
	public static AbsoluteBounds computeAbsoluteBounds(
			HudComponentState state,
			float contentW,
			float contentH,
			float screenW,
			float screenH) {
		if (state == null) {
			return new AbsoluteBounds(0, 0, 0, 0);
		}
		float sx = absX(state, screenW);
		float sy = absY(state, screenH);
		float sw = contentW * state.scale;
		float sh = contentH * state.scale;
		return new AbsoluteBounds(sx, sy, sx + sw, sy + sh);
	}

	/** Convenience overload using the state's cached base size. */
	public static AbsoluteBounds bounds(HudComponentState state, float screenW, float screenH) {
		if (state == null) return new AbsoluteBounds(0, 0, 0, 0);
		return computeAbsoluteBounds(state, state.baseWidth, state.baseHeight, screenW, screenH);
	}

	/**
	 * Inverse: given a desired absolute top-left, rewrite offsetX/Y so the
	 * component lands there under the current anchor + screen size.
	 */
	public static void setAbsoluteTopLeft(HudComponentState state, float absX, float absY,
			float screenW, float screenH) {
		state.offsetX = absX - anchorX(state.anchor, screenW);
		state.offsetY = absY - anchorY(state.anchor, screenH);
	}

	/**
	 * Re-anchor while keeping the absolute top-left fixed (Feather-style
	 * "pin to edge" without visual jump).
	 */
	public static void reanchorPreservingPosition(HudComponentState state, AnchorPoint next,
			float screenW, float screenH, AbsoluteBounds current) {
		if (state == null || next == null || current == null) return;
		state.anchor = next;
		setAbsoluteTopLeft(state, current.minX, current.minY, screenW, screenH);
	}

	/**
	 * Clamp an absolute top-left so the scaled box stays fully on screen.
	 * Returns the clamped top-left (does not mutate state).
	 */
	public static float[] clampTopLeftToScreen(float absX, float absY,
			float scaledW, float scaledH, float screenW, float screenH) {
		float x = absX;
		float y = absY;
		if (scaledW <= screenW) {
			x = Math.max(0f, Math.min(screenW - scaledW, x));
		} else {
			x = Math.min(0f, Math.max(screenW - scaledW, x));
		}
		if (scaledH <= screenH) {
			y = Math.max(0f, Math.min(screenH - scaledH, y));
		} else {
			y = Math.min(0f, Math.max(screenH - scaledH, y));
		}
		return new float[] { x, y };
	}
}
