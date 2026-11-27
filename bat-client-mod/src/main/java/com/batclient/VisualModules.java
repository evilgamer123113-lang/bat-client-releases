package com.batclient;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

/** Small native render controls. No gameplay state, velocity or collision changes. */
public final class VisualModules {
    public static ShaderProgram glintShader;
    private VisualModules() { }
    public static JsonObject settings(String id){return HudConfig.module(HudConfig.snapshot(),id);}
    public static boolean enabled(String id){return HudConfig.enabled(settings(id));}
    public static float number(JsonObject mod,String key,float fallback,float min,float max){float v=HudConfig.setNumber(mod,key,fallback);return Float.isFinite(v)?Math.clamp(v,min,max):fallback;}
    public static float hurtAngle(float angle){var mod=settings("hurtcamera");return HudConfig.enabled(mod)?angle*number(mod,"intensity",0,0,100)/100:angle;}
    public static float itemHeight(ItemEntity entity,float original){
        var mod=settings("itemphysics");if(!HudConfig.enabled(mod)||entity.isTouchingWater()&&HudConfig.bool(mod,"water",true))return original;
        return entity.getStack().getItem() instanceof net.minecraft.item.BlockItem?.13f:.045f;
    }
    public static void rotateItem(MatrixStack matrices,Quaternionf original,ItemEntity entity,float delta){
        var mod=settings("itemphysics");
        if(!HudConfig.enabled(mod)||entity.isTouchingWater()&&HudConfig.bool(mod,"water",true)){matrices.multiply(original);return;}
        float seed=(entity.getId()*137.508f)%360;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(seed));
        float spin=(entity.getItemAge()+(MinecraftClient.getInstance().isPaused()?0:delta))*number(mod,"speed",1,0,3)*18;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.isOnGround()?90:spin%360));
    }
    public static ShaderProgram glint(ShaderProgram vanilla){
        var mod=settings("glintcolor");if(!HudConfig.enabled(mod)||glintShader==null)return vanilla;
        int color=HudRenderer.parseColor(HudConfig.str(mod,"color","#8B5CFFFF"),0xFF8B5CFF);
        glintShader.getUniformOrDefault("BatTint").set(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f*number(mod,"strength",1,0,2));
        return glintShader;
    }
}
