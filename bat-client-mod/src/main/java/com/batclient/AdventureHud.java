package com.batclient;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Live, native HUD data. No changes to food, arrows or mount movement. */
public final class AdventureHud {
    private AdventureHud() { }
    public static ItemStack food(MinecraftClient c,JsonObject mod,boolean preview){
        if(preview)return new ItemStack(Items.COOKED_BEEF);
        if(c.player==null)return ItemStack.EMPTY;
        var main=c.player.getMainHandStack();var off=c.player.getOffHandStack();
        return main.contains(DataComponentTypes.FOOD)?main:HudConfig.bool(mod,"offhand",true)&&off.contains(DataComponentTypes.FOOD)?off:ItemStack.EMPTY;
    }
    public static float charge(MinecraftClient c,boolean preview){
        if(preview)return .75f;
        if(c.player==null||!c.player.isUsingItem()||!(c.player.getActiveItem().getItem() instanceof BowItem))return -1;
        return BowItem.getPullProgress(c.player.getItemUseTime());
    }
    public static List<String> lines(MinecraftClient c,String id,JsonObject mod,boolean preview){
        var lines=new ArrayList<String>();
        switch(id){
            case "foodpreview" -> {
                var stack=food(c,mod,preview);var food=stack.get(DataComponentTypes.FOOD);if(food==null)return lines;
                int hunger=preview?12:c.player.getHungerManager().getFoodLevel();
                int restored=Math.min(food.nutrition(),20-hunger);
                lines.add(stack.getName().getString());
                lines.add("Hunger: +"+food.nutrition()+" ("+restored+" usable)");
                if(HudConfig.bool(mod,"saturation",true))lines.add(String.format(Locale.ROOT,"Saturation: +%.1f",food.saturation()));
            }
            case "bowcharge" -> {float charge=charge(c,preview);if(charge<0)return lines;lines.add(charge>=1&&HudConfig.bool(mod,"ready",true)?"Bow: Ready!":"Bow: "+Math.round(charge*100)+"%");if(HudConfig.bool(mod,"drawTime",false))lines.add(String.format(Locale.ROOT,"Draw: %.2fs",preview?.75:c.player.getItemUseTime()/20.0));}
            case "mounthud" -> {
                var horse=c.player!=null&&c.player.getVehicle() instanceof AbstractHorseEntity h?h:null;
                if(!preview&&horse==null)return lines;
                lines.add(preview?"Horse":horse.getName().getString());
                if(HudConfig.bool(mod,"health",true))lines.add(String.format(Locale.ROOT,"Health: %.1f / %.1f",preview?24f:horse.getHealth(),preview?30f:horse.getMaxHealth()));
                if(HudConfig.bool(mod,"speed",true)){
                    double speed=preview?7.5:horse.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)*42.157787;
                    lines.add(String.format(Locale.ROOT,"Max speed: ~%.2f blocks/s",speed));
                }
                if(HudConfig.bool(mod,"currentSpeed",true))lines.add(String.format(Locale.ROOT,"Current: %.2f blocks/s",preview?4.2:Math.hypot(horse.getX()-horse.prevX,horse.getZ()-horse.prevZ)*20));
                if(HudConfig.bool(mod,"jump",true)){
                    double j=preview?.7:horse.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH);
                    // Standard full-charge, level-ground horse jump estimate.
                    double blocks=-.1817584952*j*j*j+3.689713992*j*j+2.128599134*j-.343930367;
                    lines.add(!preview&&horse instanceof net.minecraft.entity.passive.CamelEntity?"Jump: Camel dash":String.format(Locale.ROOT,"Jump: ~%.2f blocks",Math.max(0,blocks)));
                }
            }
            case "deathcoords" -> {
                var death=DeathCoordinates.last(c,preview);if(death==null)return lines;lines.add("Last death");lines.add(death.x()+", "+death.y()+", "+death.z());if(HudConfig.bool(mod,"dimension",true))lines.add(death.dimension().replace("minecraft:",""));
                if(HudConfig.bool(mod,"distance",true)){
                    if(preview)lines.add("120m · NE");
                    else if(c.player!=null&&c.world!=null&&death.dimension().equals(c.world.getRegistryKey().getValue().toString())){
                        double dx=death.x()+.5-c.player.getX(),dz=death.z()+.5-c.player.getZ();String[] directions={"N","NE","E","SE","S","SW","W","NW"};int bearing=Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(dx,-dz))/45),8);
                        lines.add(Math.round(c.player.getPos().distanceTo(new net.minecraft.util.math.Vec3d(death.x()+.5,death.y(),death.z()+.5)))+"m · "+directions[bearing]);
                    }else lines.add("Different dimension");
                }
            }
        }
        return lines;
    }
    private static int barWidth(JsonObject mod){return (int)VisualModules.number(mod,"barWidth",110,90,180);}
    private static boolean bar(String id,JsonObject mod){return id.equals("bowcharge")||id.equals("foodpreview")&&HudConfig.bool(mod,"hungerBar",true)||id.equals("mounthud")&&HudConfig.bool(mod,"healthBar",true);}
    public static int width(MinecraftClient c,String id,JsonObject mod,boolean preview){int width=lines(c,id,mod,preview).stream().mapToInt(c.textRenderer::getWidth).max().orElse(0);if(HudConfig.bool(mod,"icons",true))width+=22;return bar(id,mod)?Math.max(id.equals("foodpreview")?90:barWidth(mod),width):width;}
    public static int height(MinecraftClient c,String id,JsonObject mod,boolean preview){var lines=lines(c,id,mod,preview);return lines.isEmpty()?0:Math.max(HudConfig.bool(mod,"icons",true)?16:0,lines.size()*11)+(bar(id,mod)?13:0);}
    public static void draw(DrawContext ctx,MinecraftClient c,String id,JsonObject mod,boolean preview){
        var lines=lines(c,id,mod,preview);int color=HudRenderer.parseColor(HudConfig.str(mod,"color","#FFFFFFFF"),0xFFFFFFFF);
        if(lines.isEmpty())return;
        int textX=HudConfig.bool(mod,"icons",true)?22:0;
        if(textX>0){var icon=switch(id){case "foodpreview"->food(c,mod,preview);case "bowcharge"->new ItemStack(Items.BOW);case "mounthud"->new ItemStack(Items.SADDLE);default->new ItemStack(Items.RECOVERY_COMPASS);};ctx.drawItem(icon,0,0);}
        for(int i=0;i<lines.size();i++)ctx.drawText(c.textRenderer,lines.get(i),textX,i*11,color,HudConfig.bool(mod,"shadow",true));
        int barY=Math.max(textX>0?16:0,lines.size()*11)+3,barWidth=barWidth(mod);
        if(id.equals("bowcharge")&&!lines.isEmpty()){
            float charge=charge(c,preview);ctx.fill(0,barY,barWidth,barY+5,0x88333333);
            int fill=charge>=1?0xFF55FF55:HudRenderer.parseColor(HudConfig.str(mod,"chargeColor","#FFCC55FF"),0xFFFFCC55);
            ctx.fill(0,barY,Math.round(barWidth*charge),barY+5,fill);
            for(int n=1;n<4;n++)ctx.fill(n*barWidth/4,barY,n*barWidth/4+1,barY+5,0x66000000);
        }
        if(id.equals("mounthud")&&HudConfig.bool(mod,"healthBar",true)){
            var horse=c.player!=null&&c.player.getVehicle() instanceof AbstractHorseEntity h?h:null;float ratio=preview?.8f:horse==null?0:horse.getHealth()/Math.max(1,horse.getMaxHealth());
            ctx.fill(0,barY,barWidth,barY+5,0x88333333);ctx.fill(0,barY,Math.round(barWidth*Math.clamp(ratio,0,1)),barY+5,ratio<.25?0xFFFF5555:0xFF55DD77);
        }
        if(id.equals("foodpreview")&&HudConfig.bool(mod,"hungerBar",true)){
            int hunger=preview?12:c.player.getHungerManager().getFoodLevel();var component=food(c,mod,preview).get(DataComponentTypes.FOOD);int after=Math.min(20,hunger+(component==null?0:component.nutrition()));
            for(int i=0;i<10;i++){
                int before=Math.clamp(hunger-i*2,0,2),later=Math.clamp(after-i*2,0,2);
                ctx.drawGuiTexture(net.minecraft.util.Identifier.ofVanilla("hud/food_empty"),i*9,barY,9,9);
                if(later>before){com.mojang.blaze3d.systems.RenderSystem.setShaderColor(.45f,1,.45f,.85f);ctx.drawGuiTexture(net.minecraft.util.Identifier.ofVanilla(later==1?"hud/food_half":"hud/food_full"),i*9,barY,9,9);com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);}
                if(before>0)ctx.drawGuiTexture(net.minecraft.util.Identifier.ofVanilla(before==1?"hud/food_half":"hud/food_full"),i*9,barY,9,9);
            }
        }
    }
}
