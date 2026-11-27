package com.batclient.platform.mc1204;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;

/** Version bridge: 1.19.2 takes net.minecraft.util.math matrices. No copies. */
public final class Vertices {
    private Vertices() {}
    public static VertexConsumer vertex(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z){return v.vertex(m.getPositionMatrix(),x,y,z);}
    public static VertexConsumer vertex(VertexConsumer v,Matrix4f m,float x,float y,float z){return v.vertex(m,x,y,z);}
    public static VertexConsumer normal(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z){return v.normal(m.getNormalMatrix(),x,y,z);}
    public static VertexConsumer normal(VertexConsumer v,Matrix3f m,float x,float y,float z){return v.normal(m,x,y,z);}
}
