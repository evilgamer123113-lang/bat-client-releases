package com.batclient.hud.layout;

/**
 * Mutable per-module layout state (JSON-serializable schema).
 *
 * Schema shape (see HudLayoutConfig):
 * { "id": "fps", "enabled": true, "scale": 1.0,
 *   "anchor": "TOP_LEFT", "offset_x": 15.0, "offset_y": 15.0 }
 *
 * Offsets are signed pixels relative to the anchor origin. Floating-point is
 * intentional so fine scales and sub-pixel snaps survive serialization.
 */
public final class HudComponentState {
	public String id;
	public boolean enabled = true;
    public boolean locked;
	public float scale = 1.0f;
	public AnchorPoint anchor = AnchorPoint.TOP_LEFT;
	public float offsetX;
	public float offsetY;

	/** Cached measured content size (unscaled). Filled by the layout engine. */
	public transient float baseWidth;
	public transient float baseHeight;

	public HudComponentState() {
	}

	public HudComponentState(String id) {
		this.id = id;
	}

	public HudComponentState copy() {
		HudComponentState c = new HudComponentState(id);
		c.enabled = enabled;
        c.locked = locked;
		c.scale = scale;
		c.anchor = anchor;
		c.offsetX = offsetX;
		c.offsetY = offsetY;
		c.baseWidth = baseWidth;
		c.baseHeight = baseHeight;
		return c;
	}

	public float scaledWidth() {
		return baseWidth * scale;
	}

	public float scaledHeight() {
		return baseHeight * scale;
	}
}
