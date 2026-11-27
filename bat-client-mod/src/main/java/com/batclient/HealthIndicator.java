package com.batclient;

import com.batclient.hud.HealthValues;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.text.Text;
import java.util.Locale;

/** Vanilla heart sprites and billboard labels, using only client-known health. */
public final class HealthIndicator {
    private HealthIndicator() { }
    private static final Identifier EMPTY=Identifier.ofVanilla("hud/heart/container"), FULL=Identifier.ofVanilla("hud/heart/full"), HALF=Identifier.ofVanilla("hud/heart/half"), GOLD=Identifier.ofVanilla("hud/heart/absorbing_full"), GOLD_HALF=Identifier.ofVanilla("hud/heart/absorbing_half");
    public static JsonObject settings(){return HudConfig.module(HudConfig.snapshot(),"healthindicator");}
    public static String label(float health,float maximum,float absorption,JsonObject mod) {
        String value=HudConfig.str(mod,"style","Heart value").equals("Heart row")?HealthValues.row(health,maximum):HealthValues.hearts(health,maximum,HudConfig.bool(mod,"showMax",true));
        if(HudConfig.bool(mod,"absorption",true)&&HealthValues.absorption(absorption)>0)value+=" +"+String.format(Locale.ROOT,"%.1f",HealthValues.absorption(absorption)/2)+" ♥";
        return value;
    }
    public static void render(LivingEntity entity,boolean normalLabel,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        JsonObject mod=settings();if(!HudConfig.enabled(mod))return;
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||client.world==null||client.currentScreen!=null||!MinecraftClient.isHudEnabled()||!entity.isAlive()||entity.isInvisibleTo(client.player)||entity.isSpectator())return;
        boolean player=entity instanceof PlayerEntity,own=entity==client.player;
        if(player?!HudConfig.bool(mod,"players",true):!HudConfig.bool(mod,"mobs",true))return;
        if(own&&(!HudConfig.bool(mod,"showOwn",false)||client.options.getPerspective().isFirstPerson()))return;
        // Preserve native team/name visibility for other players.
        if(player&&!own&&!normalLabel)return;
        float range=Math.max(4,Math.min(64,HudConfig.setNumber(mod,"range",24)));
        if(client.player.squaredDistanceTo(entity)>range*range)return;
        var point=entity.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG,0,entity.getYaw(delta));
        if(point==null)return;
        double scoreboardLift=player&&!own&&client.player.squaredDistanceTo(entity)<100&&entity.getWorld().getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME)!=null?.25875:0;
        float scale=.025f*Math.max(.5f,Math.min(2,HudConfig.setNumber(mod,"scale",1)));
        matrices.push();
        try {
            matrices.translate(point.x,point.y+.5+scoreboardLift,point.z);matrices.multiply(client.getEntityRenderDispatcher().getRotation());matrices.scale(scale,-scale,scale);
            int color=HudRenderer.parseColor(HudConfig.str(mod,"color","#FFFFFFFF"),0xFFFFFFFF);
            if(HudConfig.bool(mod,"lowWarning",true)&&HealthValues.ratio(entity.getHealth(),entity.getMaxHealth())<=.25f)color=0xFFFF5555;
            int background=HudConfig.bool(mod,"background",true)?0x55000000:0;
            boolean ownNamePresent=own&&NativeNametags.enabled()&&HudConfig.bool(NativeNametags.settings(),"showOwn",true);
            if((!player&&!normalLabel&&HudConfig.bool(mod,"mobNames",true))||(own&&!ownNamePresent))draw(entity.getDisplayName(),0,0xFFFFFFFF,mod,background,matrices,vertices,light);
            int count=heartCount(entity.getMaxHealth(),entity.getAbsorptionAmount(),mod);
            int rows=(count+9)/10;
            int top=-3-rows*10-(HudConfig.bool(mod,"showValue",false)?11:0);
            drawHearts(entity.getHealth(),entity.getMaxHealth(),entity.getAbsorptionAmount(),mod,top,matrices,vertices);
            if(HudConfig.bool(mod,"showValue",false))draw(Text.literal(label(entity.getHealth(),entity.getMaxHealth(),entity.getAbsorptionAmount(),mod)),-12,color,mod,background,matrices,vertices,light);
        }finally{matrices.pop();}
    }
    private static void draw(Text text,int y,int color,JsonObject mod,int background,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        var renderer=MinecraftClient.getInstance().textRenderer;
        float x=-renderer.getWidth(text)/2f;
        // Background belongs only to the first pass. TextRenderer draws a shadow's
        // background again at z=.04, which can cover its foreground at z=.03.
        renderer.draw(text,x,y,(color&0xFFFFFF)|0x40000000,false,matrices.peek().getPositionMatrix(),vertices,
                HudConfig.bool(mod,"occluded",true)?TextRenderer.TextLayerType.SEE_THROUGH:TextRenderer.TextLayerType.NORMAL,
                background,LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.push();
        try {
            matrices.translate(0,0,.04f);
            renderer.draw(text,x,y,color,HudConfig.bool(mod,"shadow",true),matrices.peek().getPositionMatrix(),vertices,TextRenderer.TextLayerType.NORMAL,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }finally{matrices.pop();}
    }
    private static int heartCount(float maximum,float absorption,JsonObject mod){return HealthValues.slots(maximum)+(HudConfig.bool(mod,"absorption",true)?Math.min(10,(int)Math.ceil(HealthValues.absorption(absorption)/2)):0);}
    public static int previewWidth(JsonObject mod){return Math.max(81,HudConfig.bool(mod,"showValue",false)?MinecraftClient.getInstance().textRenderer.getWidth(label(14,20,2,mod)):0);}
    public static int previewHeight(JsonObject mod){return 33+(HudConfig.bool(mod,"showValue",false)?11:0);}
    private static Identifier filling(float health,float maximum,int slot,boolean gold){int fill=HealthValues.fill(health,maximum,slot);return fill==0?null:gold?(fill==1?GOLD_HALF:GOLD):(fill==1?HALF:FULL);}
    private static void drawHearts(float health,float maximum,float absorption,JsonObject mod,int top,MatrixStack matrices,VertexConsumerProvider vertices){
        int base=HealthValues.slots(maximum),count=heartCount(maximum,absorption,mod);
        for(int i=0;i<count;i++){
            int columns=Math.min(10,count-(i/10)*10);float x=(i%10)*8-(columns*8+1)/2f,y=top+(i/10)*10;
            Identifier fill=i<base?filling(health,maximum,i,false):filling(absorption,Math.max(2,(float)Math.ceil(absorption/2)*2),i-base,true);
            if(HudConfig.bool(mod,"occluded",true)){quad(EMPTY,x,y,.05f,0x40FFFFFF,true,matrices,vertices);if(fill!=null)quad(fill,x,y,.06f,0x40FFFFFF,true,matrices,vertices);}
            quad(EMPTY,x,y,.05f,0xFFFFFFFF,false,matrices,vertices);if(fill!=null)quad(fill,x,y,.06f,0xFFFFFFFF,false,matrices,vertices);
        }
    }
    private static void quad(Identifier id,float x,float y,float z,int color,boolean through,MatrixStack matrices,VertexConsumerProvider vertices){
        var sprite=MinecraftClient.getInstance().getGuiAtlasManager().getSprite(id);
        var buffer=vertices.getBuffer(through?RenderLayer.getTextSeeThrough(sprite.getAtlasId()):RenderLayer.getText(sprite.getAtlasId()));
        var matrix=matrices.peek().getPositionMatrix();int light=LightmapTextureManager.MAX_LIGHT_COORDINATE;
        buffer.vertex(matrix,x,y,z).color(color).texture(sprite.getMinU(),sprite.getMinV()).light(light);
        buffer.vertex(matrix,x,y+9,z).color(color).texture(sprite.getMinU(),sprite.getMaxV()).light(light);
        buffer.vertex(matrix,x+9,y+9,z).color(color).texture(sprite.getMaxU(),sprite.getMaxV()).light(light);
        buffer.vertex(matrix,x+9,y,z).color(color).texture(sprite.getMaxU(),sprite.getMinV()).light(light);
    }
    public static void drawPreview(DrawContext ctx,JsonObject mod){
        int width=previewWidth(mod),count=heartCount(20,2,mod);
        for(int i=0;i<count;i++){
            int columns=Math.min(10,count-(i/10)*10),x=(width-(columns*8+1))/2+(i%10)*8,y=(i/10)*10;
            ctx.drawGuiTexture(EMPTY,x,y,9,9);Identifier fill=i<10?filling(14,20,i,false):GOLD;
            if(fill!=null)ctx.drawGuiTexture(fill,x,y,9,9);
        }
        var renderer=MinecraftClient.getInstance().textRenderer;int y=22;
        if(HudConfig.bool(mod,"showValue",false)){String value=label(14,20,2,mod);ctx.drawText(renderer,value,(width-renderer.getWidth(value))/2,y,0xFFFFFFFF,HudConfig.bool(mod,"shadow",true));y+=11;}
        ctx.drawText(renderer,"Player",(width-renderer.getWidth("Player"))/2,y,0xFFFFFFFF,HudConfig.bool(mod,"shadow",true));
    }
}
