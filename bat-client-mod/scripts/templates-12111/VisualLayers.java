package com.batclient;

import com.batclient.mixin.RenderLayerFactory;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

/** World overlays use the native render setup and vertex line widths. */
public final class VisualLayers {
    private static final RenderLayer PLATE = plate(false);
    private static final RenderLayer PLATE_THROUGH = plate(true);

    private VisualLayers() {}

    public static RenderLayer lines() {
        return RenderLayers.LINES_TRANSLUCENT;
    }

    public static RenderLayer waypointPlate(boolean through) {
        return through ? PLATE_THROUGH : PLATE;
    }

    private static RenderLayer plate(boolean through) {
        var name = through ? "waypoint_plate_through" : "waypoint_plate";
        var source = RenderPipelines.GUI;
        var builder = RenderPipeline.builder()
            .withLocation(Identifier.of("batclient", name))
            .withVertexShader(source.getVertexShader())
            .withFragmentShader(source.getFragmentShader())
            .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
            .withDepthTestFunction(through ? DepthTestFunction.NO_DEPTH_TEST : RenderPipelines.LINES.getDepthTestFunction())
            .withDepthWrite(false).withCull(false).withBlend(BlendFunction.TRANSLUCENT);
        source.getUniforms().forEach(uniform -> builder.withUniform(uniform.name(), uniform.type()));
        source.getSamplers().forEach(builder::withSampler);
        return RenderLayerFactory.bat$of(name,
            RenderSetup.builder(builder.build()).translucent().expectedBufferSize(4096).build());
    }
}
