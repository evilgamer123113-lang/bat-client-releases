package com.batclient.mixin;

import com.batclient.platform.fabric.WorldFrameAccess;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldFrameCaptureMixin implements WorldFrameAccess {
    @Unique private Matrix4f bat$projection;
    @Unique private Frustum bat$frustum;

    @Inject(method = "render", at = @At("HEAD"))
    private void bat$captureProjection(ObjectAllocator allocator, RenderTickCounter ticks, boolean outline,
            Camera camera, Matrix4f position, Matrix4f basicProjection, Matrix4f projection,
            GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        bat$projection = projection;
        bat$frustum = null;
    }

    @Inject(method = "renderMain", at = @At("HEAD"))
    private void bat$captureFrustum(FrameGraphBuilder graph, Frustum frustum, Matrix4f position,
            GpuBufferSlice fog, boolean outline, net.minecraft.client.render.state.WorldRenderState state,
            RenderTickCounter ticks, net.minecraft.util.profiler.Profiler profiler, CallbackInfo ci) {
        bat$frustum = frustum;
    }

    @Override public Matrix4f bat$projection() { return bat$projection; }
    @Override public Frustum bat$frustum() { return bat$frustum; }
}
