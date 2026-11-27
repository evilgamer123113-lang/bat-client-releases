package com.batclient.platform.mc1201;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Uses the player's 1.20.1 icons texture, including active resource-pack overrides. */
public final class GuiSprites {
    private static final Identifier ICONS=new Identifier("minecraft","textures/gui/icons.png");
    private GuiSprites(){}
    public record Region(int x,int y) {
        public Identifier getAtlasId(){return ICONS;}
        public float getMinU(){return x/256f;}
        public float getMaxU(){return (x+9)/256f;}
        public float getMinV(){return y/256f;}
        public float getMaxV(){return (y+9)/256f;}
    }
    public static Region region(Identifier id){
        if(!id.getNamespace().equals("minecraft"))throw new IllegalArgumentException("Not a native HUD icon: "+id);
        return switch(id.getPath()){
            case "hud/heart/container"->new Region(16,0);
            case "hud/heart/full"->new Region(52,0);
            case "hud/heart/half"->new Region(61,0);
            case "hud/heart/absorbing_full"->new Region(160,0);
            case "hud/heart/absorbing_half"->new Region(169,0);
            case "hud/food_empty"->new Region(16,27);
            case "hud/food_full"->new Region(52,27);
            case "hud/food_half"->new Region(61,27);
            default->throw new IllegalArgumentException("Unknown native HUD icon: "+id);
        };
    }
    public static void draw(DrawContext context,Identifier id,int x,int y,int width,int height){
        Region r=region(id);context.drawTexture(ICONS,x,y,r.x(),r.y(),width,height);
    }
}
