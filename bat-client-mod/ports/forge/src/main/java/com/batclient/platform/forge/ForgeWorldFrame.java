package com.batclient.platform.forge;

import com.batclient.platform.WorldFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

/** Reused view; Forge supplies a model-view matrix instead of a Fabric context. */
final class ForgeWorldFrame implements WorldFrame {
    private final MatrixStack stack=new MatrixStack();
    private MatrixStack matrices;
    private Camera camera;
    private Frustum frustum;
    private VertexConsumerProvider buffers;
    private Matrix4f projection;
    private float delta;
    void stage(net.minecraftforge.client.event.RenderLevelStageEvent event){
        stack.loadIdentity();
        stack.peek().getPositionMatrix().set(event.getPoseStack());
        stack.peek().getNormalMatrix().set(event.getPoseStack()).invert().transpose();
        matrices=stack;camera=event.getCamera();frustum=event.getFrustum();
        projection=event.getProjectionMatrix();delta=event.getPartialTick();
        buffers=MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
    }
    void highlight(net.minecraftforge.client.event.RenderHighlightEvent.Block event){
        matrices=event.getPoseStack();camera=event.getCamera();delta=event.getPartialTick();
        buffers=event.getMultiBufferSource();frustum=null;
        // Block-outline renderers do not consume a projection matrix.
        projection=null;
    }
    @Override public MatrixStack matrixStack(){return matrices;}
    @Override public Camera camera(){return camera;}
    @Override public VertexConsumerProvider consumers(){return buffers;}
    @Override public Frustum frustum(){return frustum;}
    @Override public ClientWorld world(){return MinecraftClient.getInstance().world;}
    @Override public Matrix4f projectionMatrix(){return projection;}
    @Override public float tickDelta(){return delta;}
}
