package com.batclient;

import com.batclient.platform.WorldFrame;
import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.*;

/** Bounded camera-facing markers for saved positions only. No chunk loads, entities or network queries. */
public final class WaypointRenderer {
    private WaypointRenderer(){}
    public static void init(){ClientHookServices.get().afterEntities(WaypointRenderer::world);ClientHookServices.get().hud(WaypointRenderer::navigation);}
    public static int color(WaypointStore.Entry e){int rgb=e.chroma()?java.awt.Color.HSBtoRGB((System.currentTimeMillis()%10000)/10000f,.7f,1f)&0xFFFFFF:Integer.parseInt(e.color().substring(1),16);return (Math.round(e.opacity()*2.55f)<<24)|rgb;}
    public static String distance(double d){return d>=1000?String.format(Locale.ROOT,"%.1f km",d/1000):Math.round(d)+" m";}
    private static double distance(MinecraftClient c,WaypointStore.Entry e){return c.player.getPos().distanceTo(new Vec3d(e.x()+.5,e.y(),e.z()+.5));}
    private static void world(WorldFrame ctx){
        var c=MinecraftClient.getInstance();var mod=VisualModules.settings("waypoints");
        if(!HudConfig.enabled(mod)||c.player==null||c.world==null||!MinecraftClient.isHudEnabled()||c.currentScreen!=null||ctx.matrixStack()==null||ctx.consumers()==null)return;
        double range=VisualModules.number(mod,"range",10000,64,30000000);int limit=(int)VisualModules.number(mod,"markers",12,1,24);
        var values=Waypoints.current().stream().filter(e->e.visible()&&e.opacity()>0&&e.dimension().equals(Waypoints.dimension())&&distance(c,e)<=range).sorted(Comparator.<WaypointStore.Entry>comparingInt(e->e.tracked()?0:e.favorite()?1:2).thenComparingDouble(e->distance(c,e))).limit(64).toList();
        // World effects have their own budget. A beam can intersect the view
        // even when its ground-level label is behind/below the camera.
        int effects=0;double effectRange=c.options.getViewDistance().getValue()*16+64;
        for(var e:values){if(distance(c,e)>=effectRange)continue;
            if(HudConfig.bool(mod,"ring",true))groundRing(ctx,e);
            if(e.beam()&&HudConfig.bool(mod,"beam",false))beam(ctx,e,(int)VisualModules.number(mod,"beamHeight",128,16,256));
            if(++effects>=limit)break;
        }
        var occupied=new ArrayList<float[]>();var camera=ctx.camera().getPos();var inverse=new Quaternionf(ctx.camera().getRotation()).conjugate();int drawn=0;
        for(var e:values){
            Vec3d real=new Vec3d(e.x()+.5,e.y()+2.5,e.z()+.5),delta=real.subtract(camera);double actual=distance(c,e);if(delta.lengthSquared()<.04)continue;
            Vec3d offset=HudConfig.bool(mod,"throughWalls",true)?delta.normalize().multiply(Math.min(16,delta.length())):delta;Vector3f local=new Vector3f((float)offset.x,(float)offset.y,(float)offset.z).rotate(inverse);
            Vector4f clip=new Vector4f(local,1).mul(ctx.projectionMatrix());if(clip.w<=0)continue;
            float nx=clip.x/clip.w,ny=clip.y/clip.w;if(Math.abs(nx)>1.1||Math.abs(ny)>1.1)continue;
            String title=e.name()+" ["+Math.round(actual)+"m]";float scale=VisualModules.number(mod,"scale",1,.6f,1.8f);
            float plateWidth=c.textRenderer.getWidth(title);
            if(HudConfig.bool(mod,"coordinates",false))plateWidth=Math.max(plateWidth,c.textRenderer.getWidth(e.x()+", "+e.y()+", "+e.z()));
            int pad=(int)VisualModules.number(mod,"padding",4,0,10);
            float sx=(nx*.5f+.5f)*c.getWindow().getScaledWidth(),sy=(.5f-ny*.5f)*c.getWindow().getScaledHeight();float width=(plateWidth+pad*2)*scale,height=((HudConfig.bool(mod,"coordinates",false)?22:10)+pad*2)*scale;
            float displacement=0;boolean accepted=false;
            for(int step=0;step<5;step++){displacement=step==0?0:(step%2==1?-1:1)*((step+1)/2)*(height+3);float[] box={sx-width/2,sy+displacement-10,sx+width/2,sy+displacement+height};boolean overlap=false;for(var other:occupied)if(box[0]<other[2]&&box[2]>other[0]&&box[1]<other[3]&&box[3]>other[1]){overlap=true;break;}if(!overlap&&box[1]>12&&box[3]<c.getWindow().getScaledHeight()-12){occupied.add(box);accepted=true;break;}}
            if(!accepted)continue;
            var matrices=ctx.matrixStack();matrices.push();try{
                matrices.translate(offset.x,offset.y,offset.z);matrices.multiply(ctx.camera().getRotation());
                float pixel=2*Math.abs(local.z)/(c.getWindow().getScaledHeight()*ctx.projectionMatrix().m11())*scale;matrices.scale(pixel,-pixel,pixel);matrices.translate(0,displacement/scale,0);
                boolean through=HudConfig.bool(mod,"throughWalls",true),background=HudConfig.bool(mod,"background",true),shadow=HudConfig.bool(mod,"shadow",true);
                float l=-plateWidth/2-pad,r=plateWidth/2+pad,t=-pad,b=(HudConfig.bool(mod,"coordinates",false)?21:9)+pad;
                int tint=color(e);float radius=Math.min(3.5f,(b-t)/2);
                if(HudConfig.bool(mod,"glow",true)&&HudConfig.bool(mod,"border",true)){
                    rounded(ctx,l-2,t-2,r+2,b+2,radius+2,(Math.round(e.opacity()*.2f)<<24)|(tint&0xFFFFFF),2.5f,through);
                    rounded(ctx,l-1,t-1,r+1,b+1,radius+1,(Math.round(e.opacity()*.35f)<<24)|(tint&0xFFFFFF),1.7f,through);
                }
                if(background)rounded(ctx,l,t,r,b,radius,(Math.round(e.opacity()*1.15f)<<24),0,through);
                if(HudConfig.bool(mod,"border",true))rounded(ctx,l,t,r,b,radius,tint,.65f,through);
                // Flush the background first: a translucent plate drawn after a
                // see-through font layer would darken otherwise white glyphs.
                if(ctx.consumers() instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate buffers)buffers.draw(VisualLayers.waypointPlate(through));
                int alpha=tint&0xFF000000;
                line(ctx,title,alpha|0xFFFFFF,0,through,shadow);
                if(HudConfig.bool(mod,"coordinates",false))line(ctx,e.x()+", "+e.y()+", "+e.z(),alpha|0xDDDDDD,12,through,shadow);
            }finally{matrices.pop();}
            if(++drawn>=limit)break;
        }
    }
    private static void line(WorldFrame ctx,String value,int color,int y,boolean through,boolean shadow){
        var font=MinecraftClient.getInstance().textRenderer;float x=-font.getWidth(value)/2f;var matrices=ctx.matrixStack();
        font.draw(Text.literal(value),x,y,color,shadow,matrices.peek().getPositionMatrix(),ctx.consumers(),through?TextRenderer.TextLayerType.SEE_THROUGH:TextRenderer.TextLayerType.NORMAL,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }
    private static float[] cornerPoint(float l,float t,float r,float b,float radius,int index){
        int corner=(index/9)%4;float angle=(float)Math.toRadians(180+corner*90+(index%9)*90f/8);
        float cx=corner==0||corner==3?l+radius:r-radius,cy=corner<2?t+radius:b-radius;
        return new float[]{cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius};
    }
    private static void rounded(WorldFrame ctx,float l,float t,float r,float b,float radius,int color,float stroke,boolean through){
        var v=ctx.consumers().getBuffer(VisualLayers.waypointPlate(through));var m=ctx.matrixStack().peek();
        for(int i=0;i<36;i++){
            var a=cornerPoint(l,t,r,b,radius,i);var z=cornerPoint(l,t,r,b,radius,(i+1)%36);
            float[] ai=stroke>0?cornerPoint(l+stroke,t+stroke,r-stroke,b-stroke,Math.max(0,radius-stroke),i):new float[]{(l+r)/2,(t+b)/2};
            float[] zi=stroke>0?cornerPoint(l+stroke,t+stroke,r-stroke,b-stroke,Math.max(0,radius-stroke),(i+1)%36):ai;
            v.vertex(m,a[0],a[1],-.02f).color(color);v.vertex(m,z[0],z[1],-.02f).color(color);v.vertex(m,zi[0],zi[1],-.02f).color(color);v.vertex(m,ai[0],ai[1],-.02f).color(color);
        }
    }
    private static void groundRing(WorldFrame ctx,WaypointStore.Entry e){
        var camera=ctx.camera().getPos();var m=ctx.matrixStack().peek();var v=ctx.consumers().getBuffer(net.minecraft.client.render.RenderLayer.getDebugQuads());
        float x=(float)(e.x()+.5-camera.x),y=(float)(e.y()+.035-camera.y),z=(float)(e.z()+.5-camera.z);int tint=color(e);
        for(int layer=0;layer<3;layer++){
            float outer=.65f+layer*.045f,inner=.62f-layer*.015f;int shade=layer==0?tint:((layer==1?35:15)*(tint>>>24)/255<<24)|(tint&0xFFFFFF);
            for(int i=0;i<64;i++){double a=i*Math.PI/32,b=(i+1)*Math.PI/32;float ax=(float)Math.cos(a),az=(float)Math.sin(a),bx=(float)Math.cos(b),bz=(float)Math.sin(b);
                v.vertex(m,x+ax*outer,y,z+az*outer).color(shade);v.vertex(m,x+bx*outer,y,z+bz*outer).color(shade);v.vertex(m,x+bx*inner,y,z+bz*inner).color(shade);v.vertex(m,x+ax*inner,y,z+az*inner).color(shade);
            }
        }
    }
    private static void beam(WorldFrame ctx,WaypointStore.Entry e,int height){
        var pos=ctx.camera().getPos();float x=(float)(e.x()+.5-pos.x),y=(float)(e.y()-pos.y),z=(float)(e.z()+.5-pos.z),w=.16f,h=Math.min(height,ctx.world().getTopY()-e.y());
        if(h<=0)return;int tint=color(e),alpha=Math.min(120,tint>>>24);tint=(alpha<<24)|(tint&0xFFFFFF);
        var v=ctx.consumers().getBuffer(net.minecraft.client.render.RenderLayer.getDebugQuads());var m=ctx.matrixStack().peek();
        for(int side=0;side<4;side++){double a=side*Math.PI/2,b=(side+1)*Math.PI/2;float ax=x+(float)Math.cos(a)*w,az=z+(float)Math.sin(a)*w,bx=x+(float)Math.cos(b)*w,bz=z+(float)Math.sin(b)*w;
            v.vertex(m,ax,y,az).color(tint);v.vertex(m,bx,y,bz).color(tint);v.vertex(m,bx,y+h,bz).color(tint);v.vertex(m,ax,y+h,az).color(tint);}
    }
    public static void compass(DrawContext ctx,MinecraftClient c,double heading,boolean preview){
        var mod=VisualModules.settings("waypoints");if(preview||c.player==null||!HudConfig.enabled(mod)||!HudConfig.bool(mod,"compass",true))return;
        int count=0;for(var e:Waypoints.current())if(e.visible()&&e.opacity()>0&&e.dimension().equals(Waypoints.dimension())){
            double dx=e.x()+.5-c.player.getX(),dz=e.z()+.5-c.player.getZ(),angle=(Math.toDegrees(Math.atan2(dx,-dz))+360)%360;float offset=net.minecraft.util.math.MathHelper.wrapDegrees((float)(angle-heading))*.8f;
            if(Math.abs(offset)>72)continue;int x=Math.round(80+offset);ctx.fill(x-2,1,x+3,5,color(e));if(e.tracked())ctx.drawBorder(x-3,0,7,6,0xFFFFFFFF);if(++count>=24)break;
        }
    }
    private static void navigation(DrawContext ctx){
        var c=MinecraftClient.getInstance();var mod=VisualModules.settings("waypoints");if(!HudConfig.enabled(mod)||!HudConfig.bool(mod,"navigation",true)||c.player==null||c.world==null||c.currentScreen!=null||!MinecraftClient.isHudEnabled())return;
        var point=Waypoints.current().stream().filter(e->e.tracked()&&e.visible()&&e.opacity()>0).findFirst().orElse(null);if(point==null)return;
        String value;if(!point.dimension().equals(Waypoints.dimension()))value=point.name()+" · "+point.dimension().replace("minecraft:","");
        else{double dx=point.x()+.5-c.player.getX(),dz=point.z()+.5-c.player.getZ();double yaw=Math.toDegrees(Math.atan2(-dx,dz))-c.player.getYaw();yaw=((yaw+540)%360+360)%360-180;String direction=Math.abs(yaw)<15?"^":Math.abs(yaw)>160?"v":yaw>0?">":"<";value=direction+" "+point.name()+" · "+distance(distance(c,point));}
        int width=c.textRenderer.getWidth(value),x=(c.getWindow().getScaledWidth()-width)/2,y=Math.round(c.getWindow().getScaledHeight()*VisualModules.number(mod,"navigationY",14,3,90)/100);
        if(HudConfig.bool(mod,"background",true))HudRenderer.fillRounded(ctx,x-7,y-4,width+14,17,5,0x99000000);
        ctx.drawText(c.textRenderer,value,x,y,color(point),HudConfig.bool(mod,"shadow",true));
    }
    public static void minimap(DrawContext ctx,MinecraftClient c,int x,int z,int radius,int size){
        var mod=VisualModules.settings("waypoints");if(!HudConfig.enabled(mod)||!HudConfig.bool(mod,"minimap",true))return;
        for(var point:Waypoints.current())if(point.visible()&&point.opacity()>0&&point.dimension().equals(Waypoints.dimension())){
            int px=Math.round(size/2f+(point.x()+.5f-x)*size/(radius*2)),py=Math.round(size/2f+(point.z()+.5f-z)*size/(radius*2));if(px<4||py<4||px>size-5||py>size-5)continue;
            ctx.fill(px-3,py-3,px+4,py+4,0xFF101010);ctx.fill(px-2,py-2,px+3,py+3,color(point));if(point.tracked())ctx.drawBorder(px-4,py-4,9,9,0xFFFFFFFF);
        }
    }
}
