// Pure-math smoke test for the HUD layout engine (no Minecraft).
// Run: java scripts/HudLayoutSmoke.java  (JDK 21+, single-file source)
import com.batclient.hud.layout.*;
import com.batclient.hud.snap.*;
import com.batclient.hud.editor.HudDragSession;

public class HudLayoutSmoke {
	public static void main(String[] args) {
		// --- Anchor math ---
		check("TL.ax", AnchorPoint.TOP_LEFT.anchorX(1920) == 0f);
		check("C.ax", AnchorPoint.CENTER.anchorX(1920) == 960f);
		check("BR.ax", AnchorPoint.BOTTOM_RIGHT.anchorX(1920) == 1920f);
		check("BR.ay", AnchorPoint.BOTTOM_RIGHT.anchorY(1080) == 1080f);

		// --- Bounds: TOP_LEFT offset 15,15 scale 1, content 100x20 ---
		HudComponentState st = new HudComponentState("fps");
		st.anchor = AnchorPoint.TOP_LEFT;
		st.offsetX = 15;
		st.offsetY = 15;
		st.scale = 1f;
		AbsoluteBounds b = HudCoordinateResolver.computeAbsoluteBounds(st, 100, 20, 1920, 1080);
		check("bounds.minX", b.minX == 15f);
		check("bounds.maxX", b.maxX == 115f);
		check("bounds.maxY", b.maxY == 35f);

		// --- BOTTOM_RIGHT: offset -20,-10, content 50x50 on 1920x1080 ---
		HudComponentState br = new HudComponentState("ping");
		br.anchor = AnchorPoint.BOTTOM_RIGHT;
		br.offsetX = -20;
		br.offsetY = -10;
		br.scale = 1f;
		AbsoluteBounds bb = HudCoordinateResolver.computeAbsoluteBounds(br, 50, 50, 1920, 1080);
		// Ax=1920, absX=1900, maxX=1950
		check("br.minX", bb.minX == 1900f);
		check("br.minY", bb.minY == 1070f);

		// --- CENTER anchor, scale 2 ---
		HudComponentState ctr = new HudComponentState("crosshair");
		ctr.anchor = AnchorPoint.CENTER;
		ctr.offsetX = -11;
		ctr.offsetY = -11;
		ctr.scale = 1f;
		AbsoluteBounds cb = HudCoordinateResolver.computeAbsoluteBounds(ctr, 22, 22, 1920, 1080);
		check("center.minX", cb.minX == 949f); // 960-11
		check("center.centerX", Math.abs(cb.centerX() - 960f) < 0.01f);

		// --- Snap: near horizontal center ---
		HudComponentState drag = new HudComponentState("fps");
		drag.anchor = AnchorPoint.TOP_LEFT;
		drag.offsetX = 0;
		drag.offsetY = 0;
		drag.scale = 1f;
		drag.baseWidth = 100;
		drag.baseHeight = 20;
		// raw position: center of module almost at screen center X (960)
		// modCenterX = rawX + 50; want |modCenterX - 960| <= 5 => rawX in [905, 915]
		SnapResult snap = HudSnappingEngine.snap(drag, 910, 100, 1920, 1080, null, null);
		check("snap.centerHit", snap.drawVerticalCenterLine);
		check("snap.centeredX", Math.abs(snap.finalAbsX - 910f) < 0.01f); // 960-50=910

		// --- Snap: far from center, no snap ---
		SnapResult far = HudSnappingEngine.snap(drag, 10, 10, 1920, 1080, null, null);
		check("snap.noCenter", !far.drawVerticalCenterLine && !far.snappedHorizontal);

		// --- Snap: edge align with another module ---
		HudComponentState other = new HudComponentState("coords");
		other.enabled = true;
		other.baseWidth = 80;
		other.baseHeight = 20;
		other.scale = 1f;
		// other at abs (100,200)-(180,220)
		AbsoluteBounds ob = new AbsoluteBounds(100, 200, 180, 220);
		java.util.List<HudComponentState> others = java.util.List.of(other);
		java.util.List<AbsoluteBounds> obs = java.util.List.of(ob);
		// drag left edge near other right edge (180): rawX in [175,185]
		SnapResult edge = HudSnappingEngine.snap(drag, 182, 50, 1920, 1080, others, obs);
		check("snap.edge", edge.snappedHorizontal);
		check("snap.edgeX", edge.finalAbsX == 180f);
		check("snap.guide", edge.hasVerticalGuide && edge.verticalGuideX == 180f);

		SnapResult same = HudSnappingEngine.snap(drag, 102, 198, 1920, 1080, others, obs);
		check("snap.sameEdges", same.finalAbsX == 100 && same.finalAbsY == 200);
		SnapResult screenEdge = HudSnappingEngine.snap(drag, 2, 30, 1920, 1080, others, obs);
		check("snap.screenEdge", screenEdge.finalAbsX == 0 && screenEdge.hasVerticalGuide && screenEdge.verticalGuideX == 0);
		AbsoluteBounds nearCenter = new AbsoluteBounds(912, 200, 992, 220);
		SnapResult nearest = HudSnappingEngine.snap(drag, 912.5f, 50, 1920, 1080, others, java.util.List.of(nearCenter));
		check("snap.nearestGuide", nearest.finalAbsX == 912 && nearest.hasVerticalGuide && !nearest.drawVerticalCenterLine && nearest.verticalGuideX == 912);

		// --- JSON round-trip ---
		HudLayoutConfig cfg = new HudLayoutConfig();
		cfg.canvas.base_width = 1920;
		HudComponentState m = cfg.findOrCreate("fps");
		m.enabled = true;
		m.scale = 1.5f;
		m.anchor = AnchorPoint.TOP_RIGHT;
		m.offsetX = -30;
		m.offsetY = 12;
		String json = cfg.toJson();
		HudLayoutConfig back = HudLayoutConfig.fromJson(json);
		HudComponentState m2 = back.find("fps");
		check("json.scale", m2 != null && m2.scale == 1.5f);
		check("json.anchor", m2 != null && m2.anchor == AnchorPoint.TOP_RIGHT);
		check("json.offset", m2 != null && m2.offsetX == -30f && m2.offsetY == 12f);

		// --- Drag session: no origin snap ---
		HudDragSession ds = new HudDragSession("fps", 500, 300, 485, 285, 0, 0);
		// grabOffset = mouse - offset = 500-15=485
		check("drag.rawX", ds.rawAbsX(500) == 15f); // mouse at grab -> original abs
		check("drag.moveX", ds.rawAbsX(520) == 35f); // +20 cursor -> +20 abs

		System.out.println("ALL_SMOKE_OK");
	}

	static void check(String name, boolean ok) {
		if (!ok) throw new AssertionError("FAIL: " + name);
		System.out.println("ok  " + name);
	}
}
