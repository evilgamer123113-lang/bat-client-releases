package com.batclient;
import net.minecraft.client.render.*;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Bounded native line-layer cache; state restoration is owned by RenderLayer. */
public abstract class VisualLayers extends RenderLayer {
    private static final Map<Integer,RenderLayer> LINES_CACHE=new HashMap<>();
    private static final RenderLayer WAYPOINT_PLATE=createWaypointPlate(true),WAYPOINT_PLATE_DEPTH=createWaypointPlate(false);
    private static RenderLayer createWaypointPlate(boolean through){return of("bat_waypoint_plate_"+through,VertexFormats.POSITION_COLOR,VertexFormat.DrawMode.QUADS,4096,false,false,
            MultiPhaseParameters.builder().program(new ShaderProgram(GameRenderer::getPositionColorProgram)).transparency(TRANSLUCENT_TRANSPARENCY)
                    .depthTest(through?ALWAYS_DEPTH_TEST:LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).build(false));}
    public static RenderLayer waypointPlate(boolean through){return through?WAYPOINT_PLATE:WAYPOINT_PLATE_DEPTH;}
    private VisualLayers(){super("bat_unused",VertexFormats.LINES,VertexFormat.DrawMode.LINES,1536,false,false,()->{},()->{});}
    public static RenderLayer lines(float width){
        int key=Math.clamp(Math.round(width*2),2,8);
        return LINES_CACHE.computeIfAbsent(key,k->of("bat_lines_"+k,VertexFormats.LINES,VertexFormat.DrawMode.LINES,1536,false,false,
                MultiPhaseParameters.builder().program(LINES_PROGRAM).lineWidth(new LineWidth(OptionalDouble.of(k/2d))).layering(VIEW_OFFSET_Z_LAYERING)
                        .transparency(TRANSLUCENT_TRANSPARENCY).target(ITEM_ENTITY_TARGET).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).build(false)));
    }
}
