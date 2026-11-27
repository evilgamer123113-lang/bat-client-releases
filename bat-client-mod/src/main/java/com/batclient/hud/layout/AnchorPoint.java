package com.batclient.hud.layout;

/**
 * Anchor origin for a HUD component. The resolver maps the anchor onto the
 * live screen edges/center, then applies the component's pixel offsets.
 */
public enum AnchorPoint {
	TOP_LEFT(0f, 0f),
	TOP_CENTER(0.5f, 0f),
	TOP_RIGHT(1f, 0f),
	CENTER_LEFT(0f, 0.5f),
	CENTER(0.5f, 0.5f),
	CENTER_RIGHT(1f, 0.5f),
	BOTTOM_LEFT(0f, 1f),
	BOTTOM_CENTER(0.5f, 1f),
	BOTTOM_RIGHT(1f, 1f);

	/** Horizontal anchor fraction of screen width (0 = left, 1 = right). */
	public final float nx;
	/** Vertical anchor fraction of screen height (0 = top, 1 = bottom). */
	public final float ny;

	AnchorPoint(float nx, float ny) {
		this.nx = nx;
		this.ny = ny;
	}

	/**
	 * Absolute anchor point in canvas/screen space.
	 * TOP_LEFT -> (0,0); CENTER -> (W/2,H/2); BOTTOM_RIGHT -> (W,H); etc.
	 */
	public float anchorX(float screenW) {
		return nx * screenW;
	}

	public float anchorY(float screenH) {
		return ny * screenH;
	}

	public static AnchorPoint parse(String name, AnchorPoint def) {
		if (name == null) return def;
		try {
			return valueOf(name);
		} catch (IllegalArgumentException ex) {
			return def;
		}
	}
}
