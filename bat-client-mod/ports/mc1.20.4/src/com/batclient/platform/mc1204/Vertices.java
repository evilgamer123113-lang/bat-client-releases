package com.batclient.platform.mc1204;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Version bridge: 1.20.4 takes position/normal matrices separately. No copies. */
public final class Vertices {
    private Vertices() {}
    public static VertexConsumer vertex(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z){return v.vertex(m.getPositionMatrix(),x,y,z);}
    public static VertexConsumer vertex(VertexConsumer v,Matrix4f m,float x,float y,float z){return v.vertex(m,x,y,z);}
    public static VertexConsumer normal(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z){return v.normal(m.getNormalMatrix(),x,y,z);}
    public static VertexConsumer normal(VertexConsumer v,Matrix3f m,float x,float y,float z){return v.normal(m,x,y,z);}
}
