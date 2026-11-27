package com.batclient.hud.editor;

import com.batclient.hud.layout.AbsoluteBounds;
import com.batclient.hud.layout.AnchorPoint;
import com.batclient.hud.layout.HudComponentState;
import com.batclient.hud.layout.HudCoordinateResolver;
import com.batclient.hud.snap.HudSnappingEngine;
import com.batclient.hud.snap.SnapResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pipeline B input controller: hover, pick, drag, snap, release.
 *
 * Decoupled from Minecraft Screen/input classes — the host (HudEditorScreen
 * or a web bridge) feeds mouse coordinates and receives:
 *   - the id of the hovered module (or null)
 *   - the id of the actively dragged module (or null)
 *   - the current SnapResult (for guide-line drawing)
 *   - a persist callback after each commit (release / snap apply)
 *
 * Hit resolution on overlap: smallest AABB area wins; tie-break by highest
 * list index (later modules paint on top).
 */
public final class HudEditorController {

	/** Visual state of a component's selection frame. */
	public enum FrameState {
		DEFAULT,   // gray
		HOVERED,   // light blue
		DRAGGED    // bright green
	}

	public interface LayoutSource {
		float screenW();
		float screenH();
		List<HudComponentState> modules();
		/** Measured unscaled content size; may be 0 until first measure. */
		float contentW(HudComponentState m);
		float contentH(HudComponentState m);
		/** Called after offsets change so the owner can persist. */
		Consumer<HudComponentState> onCommit();
	}

	private final LayoutSource src;
	private HudDragSession drag;
	private String hoveredId;
	private final SnapResult lastSnap = new SnapResult();
	private AbsoluteBounds[] boundsCache;
	private boolean snappingEnabled = true;
	public void setSnappingEnabled(boolean enabled) { snappingEnabled = enabled; }

	public HudEditorController(LayoutSource src) {
		this.src = src;
	}

	public String hoveredId() {
		return hoveredId;
	}

	public String draggedId() {
		return drag != null && drag.active ? drag.moduleId : null;
	}

	public boolean isDragging() {
		return drag != null && drag.active;
	}

	public SnapResult lastSnap() {
		return lastSnap;
	}

	public FrameState frameStateFor(String id) {
		if (id == null) return FrameState.DEFAULT;
		if (id.equals(draggedId())) return FrameState.DRAGGED;
		if (id.equals(hoveredId)) return FrameState.HOVERED;
		return FrameState.DEFAULT;
	}

	/** Recompute per-module bounds (call once per frame before hit tests). */
	public void updateBounds() {
		List<HudComponentState> mods = src.modules();
		if (boundsCache == null || boundsCache.length != mods.size()) {
			boundsCache = new AbsoluteBounds[mods.size()];
		}
		float sw = src.screenW();
		float sh = src.screenH();
		for (int i = 0; i < mods.size(); i++) {
			HudComponentState m = mods.get(i);
			m.baseWidth = src.contentW(m);
			m.baseHeight = src.contentH(m);
			boundsCache[i] = HudCoordinateResolver.bounds(m, sw, sh);
		}
	}

	public AbsoluteBounds boundsAt(int index) {
		if (boundsCache == null || index < 0 || index >= boundsCache.length) return null;
		return boundsCache[index];
	}

	/**
	 * Pick the top-most smallest-area module under the cursor.
	 * Overlap rule: min area, then max index.
	 */
	public String pick(float mx, float my) {
		List<HudComponentState> mods = src.modules();
		updateBounds();
		String best = null;
		float bestArea = Float.MAX_VALUE;
		int bestIdx = -1;
		for (int i = 0; i < mods.size(); i++) {
			HudComponentState m = mods.get(i);
			if (m == null || !m.enabled) continue;
			AbsoluteBounds b = boundsCache[i];
			if (b == null || !b.contains(mx, my)) continue;
			float area = b.area();
			if (area < bestArea - 0.001f || (Math.abs(area - bestArea) <= 0.001f && i > bestIdx)) {
				bestArea = area;
				best = m.id;
				bestIdx = i;
			}
		}
		return best;
	}

	/** Pointer-move: update hover; if dragging, run snap + apply offsets. */
	public void onMouseMove(float mx, float my) {
		if (drag != null && drag.active) {
			updateDrag(mx, my);
			return;
		}
		hoveredId = pick(mx, my);
	}

	/**
	 * Pointer-down. Starts a drag if a module is under the cursor.
	 * grabOffset stores the layout vector difference so the box does not
	 * teleport to the cursor origin.
	 */
	public boolean onMouseDown(float mx, float my) {
		updateBounds();
		String id = pick(mx, my);
		if (id == null) return false;
		HudComponentState st = find(id);
		if (st == null || st.locked) return false;

		float ax = HudCoordinateResolver.anchorX(st.anchor, src.screenW());
		float ay = HudCoordinateResolver.anchorY(st.anchor, src.screenH());
		// grabOffsetX = mouse - offsetX  (per spec: dragStartX = mouseX - state.offsetX)
		// When converting mouse -> abs we use anchorOrigin + (mouse - grabOffset).
		// Equivalent: at grab time absTopLeft = anchor + offset, so
		//   rawAbs(grabMouse) = anchor + (grabMouse - (grabMouse - offset)) = anchor + offset. Good.
		float grabOffX = mx - st.offsetX;
		float grabOffY = my - st.offsetY;
		drag = new HudDragSession(id, mx, my, grabOffX, grabOffY, ax, ay);
		hoveredId = id;
		return true;
	}

	/** Pointer-up: finalize drag, persist, clear session. */
	public void onMouseUp(float mx, float my) {
		if (drag == null || !drag.active) return;
		// final snap apply already happened in updateDrag
		HudComponentState st = find(drag.moduleId);
		drag.active = false;
		drag = null;
		hoveredId = pick(mx, my);
		if (st != null && src.onCommit() != null) {
			src.onCommit().accept(st);
		}
	}

	/** Escape / cancel: restore is owner's job; just drop the session. */
	public void cancelDrag() {
		drag = null;
	}

	// ---- internals ----

	private void updateDrag(float mx, float my) {
		HudComponentState st = find(drag.moduleId);
		if (st == null) {
			drag.active = false;
			drag = null;
			return;
		}
		float rawX = drag.rawAbsX(mx);
		float rawY = drag.rawAbsY(my);

		// Snap BEFORE writing offsets back into memory (spec §6).
		List<HudComponentState> mods = src.modules();
		List<HudComponentState> others = new ArrayList<>(mods.size());
		List<AbsoluteBounds> otherBounds = new ArrayList<>(mods.size());
		float sw = src.screenW();
		float sh = src.screenH();
		for (int i = 0; i < mods.size(); i++) {
			HudComponentState o = mods.get(i);
			if (o == null) continue;
			if (o.id != null && o.id.equals(st.id)) continue;
			if (!o.enabled) continue;
			others.add(o);
			AbsoluteBounds b = (boundsCache != null && i < boundsCache.length)
					? boundsCache[i]
					: HudCoordinateResolver.bounds(o, sw, sh);
			otherBounds.add(b);
		}

		SnapResult snap;
		if (snappingEnabled) snap = HudSnappingEngine.snap(st, rawX, rawY, sw, sh, others, otherBounds);
		else { snap = new SnapResult(); snap.reset(rawX, rawY); }
		// Keep guide flags; copy numbers into lastSnap
		lastSnap.reset(snap.finalAbsX, snap.finalAbsY);
		lastSnap.finalAbsX = snap.finalAbsX;
		lastSnap.finalAbsY = snap.finalAbsY;
		lastSnap.snappedHorizontal = snap.snappedHorizontal;
		lastSnap.snappedVertical = snap.snappedVertical;
		lastSnap.drawVerticalCenterLine = snap.drawVerticalCenterLine;
		lastSnap.drawHorizontalCenterLine = snap.drawHorizontalCenterLine;
		lastSnap.hasVerticalGuide = snap.hasVerticalGuide;
		lastSnap.hasHorizontalGuide = snap.hasHorizontalGuide;
		lastSnap.verticalGuideX = snap.verticalGuideX;
		lastSnap.horizontalGuideY = snap.horizontalGuideY;
		lastSnap.guideTarget = snap.guideTarget;

		// Optional on-screen clamp (keeps widgets grabbable)
		float[] clamped = HudCoordinateResolver.clampTopLeftToScreen(
				snap.finalAbsX, snap.finalAbsY,
				st.scaledWidth(), st.scaledHeight(), sw, sh);
		snap.finalAbsX = clamped[0];
		snap.finalAbsY = clamped[1];
		lastSnap.finalAbsX = clamped[0];
		lastSnap.finalAbsY = clamped[1];

		// state.offsetX = finalAbsX - Ax;  state.offsetY = finalAbsY - Ay
		HudSnappingEngine.applyToState(st, snap, sw, sh);
		// refresh bounds so subsequent picks/guides see the new position
		updateBounds();
	}

	private HudComponentState find(String id) {
		if (id == null) return null;
		for (HudComponentState m : src.modules()) {
			if (id.equals(m.id)) return m;
		}
		return null;
	}

	/** Sorted helper for debug UI: top-most first (highest index, then area). */
	public List<String> pickOrder(float mx, float my) {
		List<HudComponentState> mods = src.modules();
		updateBounds();
		List<String> hits = new ArrayList<>();
		for (int i = 0; i < mods.size(); i++) {
			HudComponentState m = mods.get(i);
			if (m == null || !m.enabled) continue;
			AbsoluteBounds b = boundsCache[i];
			if (b != null && b.contains(mx, my)) hits.add(m.id);
		}
		hits.sort(Comparator.comparingDouble((String id) -> {
			int idx = indexOf(id);
			return idx < 0 ? 0 : idx;
		}).reversed());
		return hits;
	}

	private int indexOf(String id) {
		List<HudComponentState> mods = src.modules();
		for (int i = 0; i < mods.size(); i++) {
			if (mods.get(i) != null && id.equals(mods.get(i).id)) return i;
		}
		return -1;
	}

	/** Color for the selection frame given current FrameState. */
	public static int frameColor(FrameState s) {
		return switch (s) {
			case HOVERED -> 0xB3FFFFFF; // light blue
			case DRAGGED -> 0xE6FFFFFF; // bright green
			default -> 0x80808080;      // gray
		};
	}
}
