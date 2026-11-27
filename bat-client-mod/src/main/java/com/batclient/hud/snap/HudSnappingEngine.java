package com.batclient.hud.snap;

import com.batclient.hud.layout.AbsoluteBounds;
import com.batclient.hud.layout.HudComponentState;
import com.batclient.hud.layout.HudCoordinateResolver;
import java.util.List;

/** Nearest edge/centre alignment in scaled GUI pixels. Screen wins exact ties. */
public final class HudSnappingEngine {
    public static final float SNAP_THRESHOLD = 5.0f;
    private HudSnappingEngine() { }

    private static final class Axis {
        float distance = SNAP_THRESHOLD + 1, position, guide;
        boolean screenCenter;
        AbsoluteBounds target;
        void offer(float raw, float offset, float line, boolean center, AbsoluteBounds bounds) {
            float delta = Math.abs(raw + offset - line);
            if (delta <= SNAP_THRESHOLD && delta < distance) {
                distance = delta; position = line - offset; guide = line;
                screenCenter = center; target = bounds;
            }
        }
    }

    public static SnapResult snap(HudComponentState dragged, float rawX, float rawY,
            float screenW, float screenH, List<HudComponentState> others,
            List<AbsoluteBounds> otherBounds) {
        SnapResult result = new SnapResult(); result.reset(rawX, rawY);
        if (dragged == null) return result;
        float w = dragged.scaledWidth(), h = dragged.scaledHeight();
        Axis x = new Axis(), y = new Axis();
        x.offer(rawX, w/2, screenW/2, true, null);
        y.offer(rawY, h/2, screenH/2, true, null);
        x.offer(rawX, 0, 0, false, null); x.offer(rawX, w, screenW, false, null);
        y.offer(rawY, 0, 0, false, null); y.offer(rawY, h, screenH, false, null);
        if (others != null && otherBounds != null) {
            for (int i=0; i<Math.min(others.size(),otherBounds.size()); i++) {
                HudComponentState other = others.get(i); AbsoluteBounds b = otherBounds.get(i);
                if (other == null || !other.enabled || b == null || other.id == null || other.id.equals(dragged.id)) continue;
                // Same edges, opposite edges, then centres. Each axis chooses the closest.
                x.offer(rawX,0,b.minX,false,b); x.offer(rawX,w,b.maxX,false,b);
                x.offer(rawX,0,b.maxX,false,b); x.offer(rawX,w,b.minX,false,b);
                x.offer(rawX,w/2,b.centerX(),false,b);
                y.offer(rawY,0,b.minY,false,b); y.offer(rawY,h,b.maxY,false,b);
                y.offer(rawY,0,b.maxY,false,b); y.offer(rawY,h,b.minY,false,b);
                y.offer(rawY,h/2,b.centerY(),false,b);
            }
        }
        if (x.distance <= SNAP_THRESHOLD) {
            result.finalAbsX=x.position; result.snappedHorizontal=true;
            result.drawVerticalCenterLine=x.screenCenter;
            result.hasVerticalGuide=!x.screenCenter; result.verticalGuideX=x.guide;
            result.guideTarget=x.target;
        }
        if (y.distance <= SNAP_THRESHOLD) {
            result.finalAbsY=y.position; result.snappedVertical=true;
            result.drawHorizontalCenterLine=y.screenCenter;
            result.hasHorizontalGuide=!y.screenCenter; result.horizontalGuideY=y.guide;
            if (result.guideTarget==null) result.guideTarget=y.target;
        }
        return result;
    }

    public static void applyToState(HudComponentState state, SnapResult snap, float screenW, float screenH) {
        if (state != null && snap != null)
            HudCoordinateResolver.setAbsoluteTopLeft(state,snap.finalAbsX,snap.finalAbsY,screenW,screenH);
    }
}
