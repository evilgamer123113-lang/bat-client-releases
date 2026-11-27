package com.batclient.hud.layout;

/**
 * Axis-aligned bounding box in absolute screen/canvas pixels (float precision).
 * Returned by {@link HudCoordinateResolver#computeAbsoluteBounds}.
 */
public final class AbsoluteBounds {
	public final float minX;
	public final float minY;
	public final float maxX;
	public final float maxY;

	public AbsoluteBounds(float minX, float minY, float maxX, float maxY) {
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
	}

	public float width() {
		return maxX - minX;
	}

	public float height() {
		return maxY - minY;
	}

	public float centerX() {
		return (minX + maxX) * 0.5f;
	}

	public float centerY() {
		return (minY + maxY) * 0.5f;
	}

	public float area() {
		return Math.max(0f, width()) * Math.max(0f, height());
	}

	public boolean contains(float px, float py) {
		return px >= minX && px <= maxX && py >= minY && py <= maxY;
	}

	public boolean overlaps(AbsoluteBounds o) {
		return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY;
	}

	@Override
	public String toString() {
		return String.format("Bounds[%.2f,%.2f -> %.2f,%.2f]", minX, minY, maxX, maxY);
	}
}
