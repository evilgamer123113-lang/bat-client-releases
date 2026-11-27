package com.batclient.platform;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

/** Render-thread view, valid only during the callback. Game-version adapter: 1.21.1. */
public interface WorldFrame {
    MatrixStack matrixStack();
    Camera camera();
    VertexConsumerProvider consumers();
    Frustum frustum();
    ClientWorld world();
    Matrix4f projectionMatrix();
    float tickDelta();
}
