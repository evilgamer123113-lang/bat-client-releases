package com.batclient.platform.mc1214;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
/** Old shared drawing contract over 1.21.4's explicit render layers and vertex tint. */
public final class GuiCompat {
 private static final java.util.concurrent.atomic.AtomicLong IDS=new java.util.concurrent.atomic.AtomicLong();
 private GuiCompat(){}
 public static int tint(){float[] c=RenderSystem.getShaderColor();return (Math.clamp(Math.round(c[3]*255),0,255)<<24)|(Math.clamp(Math.round(c[0]*255),0,255)<<16)|(Math.clamp(Math.round(c[1]*255),0,255)<<8)|Math.clamp(Math.round(c[2]*255),0,255);}
 public static void drawTexture(DrawContext c,Identifier id,int x,int y,int u,int v,int w,int h,int tw,int th){c.drawTexture(RenderLayer::getGuiTextured,id,x,y,u,v,w,h,tw,th,tint());}
 public static void drawTexture(DrawContext c,Identifier id,int x,int y,int w,int h,float u,float v,int rw,int rh,int tw,int th){c.drawTexture(RenderLayer::getGuiTextured,id,x,y,u,v,w,h,rw,rh,tw,th,tint());}
 public static void drawTexture(DrawContext c,Identifier id,int x,int y,int w,int h,float u,float v,int rw,int rh,int tw,int th,int color){c.drawTexture(RenderLayer::getGuiTextured,id,x,y,u,v,w,h,rw,rh,tw,th,color);}
 public static void drawGuiTexture(DrawContext c,Identifier id,int x,int y,int w,int h){c.drawGuiTexture(RenderLayer::getGuiTextured,id,x,y,w,h,tint());}
 public static void drawSprite(DrawContext c,int x,int y,int z,int w,int h,Sprite s){c.getMatrices().push();try{c.getMatrices().translate(0,0,z);c.drawSpriteStretched(RenderLayer::getGuiTextured,s,x,y,w,h,tint());}finally{c.getMatrices().pop();}}
 public static Identifier registerTexture(TextureManager manager,String prefix,AbstractTexture texture){Identifier id=Identifier.of("batclient","dynamic/"+prefix.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]","_")+"_"+IDS.incrementAndGet());manager.registerTexture(id,texture);return id;}
 private static int swap(int c){return(c&0xff00ff00)|((c&255)<<16)|((c>>>16)&255);}
 public static void setAbgr(NativeImage image,int x,int y,int c){image.setColorArgb(x,y,swap(c));}
 public static int getAbgr(NativeImage image,int x,int y){return swap(image.getColorArgb(x,y));}
}
