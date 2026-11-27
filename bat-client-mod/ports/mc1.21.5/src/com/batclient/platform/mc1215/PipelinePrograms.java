package com.batclient.platform.mc1215;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.*;
import net.minecraft.util.Identifier;

/** Compile through the game's actual GPU device and resource-backed shader sources. */
public final class PipelinePrograms {
    private PipelinePrograms(){}
    private static final RenderPipeline GLINT=createGlint();
    private static RenderPipeline createGlint(){
        var source=RenderPipelines.GLINT;
        var builder=RenderPipeline.builder().withLocation(Identifier.of("batclient","pipeline/tinted_glint"))
            .withVertexShader(Identifier.of("batclient","core/tinted_glint"))
            .withFragmentShader(Identifier.of("batclient","core/tinted_glint"))
            .withVertexFormat(source.getVertexFormat(),source.getVertexFormatMode())
            .withDepthTestFunction(source.getDepthTestFunction()).withDepthWrite(source.isWriteDepth())
            .withColorWrite(source.isWriteColor(),source.isWriteAlpha()).withCull(source.isCull());
        source.getSamplers().forEach(builder::withSampler);
        source.getUniforms().forEach(u->builder.withUniform(u.name(),u.type()));
        source.getBlendFunction().ifPresent(builder::withBlend);
        return builder.withUniform("BatTint",UniformType.VEC4).build();
    }
    public static ShaderProgram program(Identifier id){
        var pipeline=id.getPath().equals("bgra_position_tex")?GpuWebQuad.PIPELINE:GLINT;
        var compiled=RenderSystem.getDevice().precompilePipeline(pipeline);
        if(!compiled.isValid()||!(compiled instanceof CompiledShaderPipeline nativeProgram))
            throw new IllegalStateException("Could not compile Bat pipeline: "+id);
        return nativeProgram.program();
    }
}
