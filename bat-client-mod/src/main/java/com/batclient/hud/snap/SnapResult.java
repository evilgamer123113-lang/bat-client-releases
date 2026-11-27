package com.batclient.hud.snap;

import com.batclient.hud.layout.AbsoluteBounds;

/**
 * Result of one snapping pass over a dragged component.
 *
 * finalAbsX/Y are the snapped top-left of the dragged box. The guide flags
 * tell the editor which dotted alignment lines to project this frame.
 */
public final class SnapResult {
	public float finalAbsX;
	public float finalAbsY;

	public boolean snappedHorizontal;   // snapped in X (vertical guide line)
	public boolean snappedVertical;     // snapped in Y (horizontal guide line)
	public boolean drawVerticalCenterLine;
	public boolean drawHorizontalCenterLine;

	/** Optional guide line positions (absolute px) for inter-module snaps. */
	public boolean hasVerticalGuide;
	public boolean hasHorizontalGuide;
	public float verticalGuideX;
	public float horizontalGuideY;

	/** Bounds of the target that produced the edge snap (null if only screen-center). */
	public AbsoluteBounds guideTarget;

	public void reset(float rawAbsX, float rawAbsY) {
		finalAbsX = rawAbsX;
		finalAbsY = rawAbsY;
		snappedHorizontal = false;
		snappedVertical = false;
		drawVerticalCenterLine = false;
		drawHorizontalCenterLine = false;
		hasVerticalGuide = false;
		hasHorizontalGuide = false;
		verticalGuideX = 0f;
		horizontalGuideY = 0f;
		guideTarget = null;
	}
}
