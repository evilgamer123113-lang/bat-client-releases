package com.batclient.platform.fabric;

import net.minecraft.client.render.Frustum;
import org.joml.Matrix4f;

public interface WorldFrameAccess {
    Matrix4f bat$projection();
    Frustum bat$frustum();
}
