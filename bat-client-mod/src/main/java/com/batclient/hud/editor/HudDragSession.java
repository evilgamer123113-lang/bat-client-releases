package com.batclient.hud.editor;

/**
 * Active mouse-drag session for one HUD component.
 *
 * Stores the layout-vector difference (cursor - offset) so the AABB does not
 * snap its origin to the raw cursor pixel on grab. Offsets are in ANCHOR space
 * (same as HudComponentState.offsetX/Y), not absolute screen space — convert
 * with the anchor origin before comparing to mouse pixels.
 */
public final class HudDragSession {
	public final String moduleId;

	/** Cursor position at pointer-down. */
	public final float grabMouseX;
	public final float grabMouseY;

	/**
	 * Layout vector difference: grabMouseX - offsetX (anchor-relative).
	 * During move: newOffsetX = currentMouseX - grabOffsetX
	 */
	public final float grabOffsetX;
	public final float grabOffsetY;

	/** Anchor origin captured at grab time (screen may resize mid-drag). */
	public final float anchorOriginX;
	public final float anchorOriginY;

	public boolean active = true;

	public HudDragSession(String moduleId,
			float grabMouseX, float grabMouseY,
			float grabOffsetX, float grabOffsetY,
			float anchorOriginX, float anchorOriginY) {
		this.moduleId = moduleId;
		this.grabMouseX = grabMouseX;
		this.grabMouseY = grabMouseY;
		this.grabOffsetX = grabOffsetX;
		this.grabOffsetY = grabOffsetY;
		this.anchorOriginX = anchorOriginX;
		this.anchorOriginY = anchorOriginY;
	}

	/**
	 * New unsnapped absolute top-left for the current cursor position.
	 * abs = anchorOrigin + (mouse - grabOffset)
	 */
	public float rawAbsX(float mouseX) {
		return anchorOriginX + (mouseX - grabOffsetX);
	}

	public float rawAbsY(float mouseY) {
		return anchorOriginY + (mouseY - grabOffsetY);
	}
}
