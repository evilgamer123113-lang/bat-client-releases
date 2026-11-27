package com.batclient.platform.mc1215;

import com.mojang.blaze3d.buffers.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import java.nio.*;
import java.util.OptionalInt;

/** Owned 1.21.5 GPU quad; each native pass owns blend and depth state. */
public final class GpuWebQuad implements AutoCloseable {
    public static final RenderPipeline PIPELINE=RenderPipeline.builder()
        .withLocation(Identifier.of("batclient","pipeline/web"))
        .withVertexShader(Identifier.of("minecraft","core/position_tex"))
        .withFragmentShader(Identifier.of("batclient","core/bgra_position_tex"))
        .withVertexFormat(VertexFormats.POSITION_TEXTURE,VertexFormat.DrawMode.QUADS)
        .withSampler("Sampler0").withUniform("ModelViewMat",UniformType.MATRIX4X4)
        .withUniform("ProjMat",UniformType.MATRIX4X4).withUniform("ColorModulator",UniformType.VEC4)
        .withBlend(new BlendFunction(SourceFactor.ONE,DestFactor.ONE_MINUS_SRC_ALPHA))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build();
    private final ByteBuffer data=ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder());
    private GpuBuffer vertices;
    private boolean closed;
    public void draw(DrawContext context,GpuTexture texture,float left,float top,float width,float height,float u1,float v1,float u2,float v2){
        if(closed)throw new IllegalStateException("GPU web quad is closed");
        if(texture==null||texture.isClosed())return;
        var device=RenderSystem.getDevice();
        if(vertices==null)vertices=device.createBuffer(()->"Bat web quad",BufferType.VERTICES,BufferUsage.DYNAMIC_WRITE,80);
        data.clear();vertex(left,top,u1,v1);vertex(left,top+height,u1,v2);
        vertex(left+width,top+height,u2,v2);vertex(left+width,top,u2,v1);data.flip();
        var encoder=device.createCommandEncoder();encoder.writeToBuffer(vertices,data,0);
        var target=MinecraftClient.getInstance().getFramebuffer();
        var indices=RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        var indexBuffer=indices.getIndexBuffer(6);
        // The command encoder caches its active program and pipeline. Let the
        // next native pass transition state through that same encoder.
        try(var pass=encoder.createRenderPass(target.getColorAttachment(),OptionalInt.empty())){
            pass.setPipeline(PIPELINE);pass.bindSampler("Sampler0",texture);
            if(RenderSystem.SCISSOR_STATE.isEnabled())pass.enableScissor(RenderSystem.SCISSOR_STATE);
            pass.setUniform("ModelViewMat",new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()).mul(context.getMatrices().peek().getPositionMatrix()));
            pass.setUniform("ProjMat",RenderSystem.getProjectionMatrix());pass.setUniform("ColorModulator",RenderSystem.getShaderColor());
            pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(indexBuffer,indices.getIndexType());pass.drawIndexed(0,6);

        }
    }
    private void vertex(float x,float y,float u,float v){data.putFloat(x).putFloat(y).putFloat(0).putFloat(u).putFloat(v);}
    @Override public void close(){if(closed)return;closed=true;if(vertices!=null){vertices.close();vertices=null;}}
}
