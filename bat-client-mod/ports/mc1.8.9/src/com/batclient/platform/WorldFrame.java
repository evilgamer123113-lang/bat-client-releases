package com.batclient.platform;

import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/** Render-thread view for 1.8 / 1.8.9, valid only during the callback. */
public interface WorldFrame {
    MatrixStack matrixStack();
    Vec3d cameraPos();
    ClientWorld world();
    float tickDelta();
}
