package com.batclient;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import java.util.*;
public final class TravelHud {
    private TravelHud(){}
    private static Object player,world;
    private static int flightTicks,readyTicks;
    private static float lastCooldown;
    public static void tick(MinecraftClient c){
        if(player!=c.player||world!=c.world){player=c.player;world=c.world;flightTicks=readyTicks=0;lastCooldown=0;}
        if(c.player==null)return;
        flightTicks=c.player.isFallFlying()?flightTicks+1:0;
        float cooldown=c.player.getItemCooldownManager().getCooldownProgress(Items.SHIELD,0);
        if(lastCooldown>0&&cooldown==0)readyTicks=16;else if(readyTicks>0)readyTicks--;
        lastCooldown=cooldown;
    }
    public static List<String> lines(MinecraftClient c,String id,JsonObject mod,boolean preview){
        var out=new ArrayList<String>();if(!preview&&c.player==null)return out;
        if(id.equals("elytrahud")){
            var chest=preview?new ItemStack(Items.ELYTRA):c.player.getEquippedStack(EquipmentSlot.CHEST);
            if(!preview&&(!chest.isOf(Items.ELYTRA)||!c.player.isFallFlying()&&!HudConfig.bool(mod,"equipped",false)))return out;
            out.add(preview||c.player.isFallFlying()?"Elytra · Gliding":"Elytra · Equipped");
            if(HudConfig.bool(mod,"speed",true))out.add(String.format(Locale.ROOT,"Speed: %.1f blocks/s",preview?28.4:c.player.getVelocity().length()*20));
            if(HudConfig.bool(mod,"altitude",true))out.add("Altitude: "+(preview?128:c.player.getBlockY()));
            int remaining=chest.getMaxDamage()-chest.getDamage();out.add("Durability: "+remaining+" / "+chest.getMaxDamage());
            if(HudConfig.bool(mod,"flightTime",true)){int seconds=preview?125:flightTicks/20;out.add(String.format(Locale.ROOT,"Flight: %d:%02d",seconds/60,seconds%60));}
            if(HudConfig.bool(mod,"rockets",true)){int count=preview?32:0;if(!preview){for(var s:c.player.getInventory().main)if(s.isOf(Items.FIREWORK_ROCKET))count+=s.getCount();for(var s:c.player.getInventory().offHand)if(s.isOf(Items.FIREWORK_ROCKET))count+=s.getCount();}out.add("Rockets: "+count+(count<=VisualModules.number(mod,"rocketWarning",8,0,64)?" (LOW)":""));}
        }else{
            if(!preview&&!c.player.getMainHandStack().isOf(Items.SHIELD)&&!c.player.getOffHandStack().isOf(Items.SHIELD))return out;
            float cooldown=preview?.5f:c.player.getItemCooldownManager().getCooldownProgress(Items.SHIELD,0);
            out.add("Shield: "+(cooldown>0?"Disabled":!preview&&c.player.isBlocking()?"Blocking":!preview&&c.player.isUsingItem()&&c.player.getActiveItem().isOf(Items.SHIELD)?"Raising":"Ready"));
            if(cooldown>0)out.add("Cooldown: "+Math.round(cooldown*100)+"%");
        }return out;
    }
    public static int width(MinecraftClient c,String id,JsonObject mod,boolean preview){return Math.max(100,lines(c,id,mod,preview).stream().mapToInt(c.textRenderer::getWidth).max().orElse(0)+22);}
    public static int height(MinecraftClient c,String id,JsonObject mod,boolean preview){var l=lines(c,id,mod,preview);return l.isEmpty()?0:Math.max(22,l.size()*11)+(id.equals("elytrahud")&&HudConfig.bool(mod,"durabilityBar",true)?9:0);}
    public static void draw(DrawContext ctx,MinecraftClient c,String id,JsonObject mod,boolean preview){var lines=lines(c,id,mod,preview);if(lines.isEmpty())return;ctx.drawItem(new ItemStack(id.equals("elytrahud")?Items.ELYTRA:Items.SHIELD),0,0);int color=HudRenderer.parseColor(HudConfig.str(mod,"color","#FFFFFFFF"),-1);if(id.equals("elytrahud")&&!preview){var s=c.player.getEquippedStack(EquipmentSlot.CHEST);if(s.getMaxDamage()-s.getDamage()<=VisualModules.number(mod,"warning",30,1,100))color=0xFFFF5555;}if(id.equals("shieldindicator")&&!preview&&readyTicks>0&&HudConfig.bool(mod,"readyPulse",true))color=0xFF55FF88;
        for(int i=0;i<lines.size();i++)ctx.drawText(c.textRenderer,lines.get(i),22,i*11,lines.get(i).endsWith("(LOW)")?0xFFFFAA44:color,HudConfig.bool(mod,"shadow",true));
        if(id.equals("elytrahud")&&HudConfig.bool(mod,"durabilityBar",true)){var stack=preview?new ItemStack(Items.ELYTRA):c.player.getEquippedStack(EquipmentSlot.CHEST);float ratio=(stack.getMaxDamage()-stack.getDamage())/(float)Math.max(1,stack.getMaxDamage());int y=lines.size()*11+3,w=width(c,id,mod,preview);ctx.fill(0,y,w,y+5,0x88444444);ctx.fill(0,y,Math.round(w*ratio),y+5,ratio<.15?0xFFFF5555:0xFF55DD88);}
        if(id.equals("shieldindicator")&&HudConfig.bool(mod,"cooldownRing",true)){
            float cooldown=preview?.5f:c.player.getItemCooldownManager().getCooldownProgress(Items.SHIELD,0);
            for(int i=0;i<24;i++){double a=i*Math.PI/12-Math.PI/2;int x=8+(int)Math.round(Math.cos(a)*10),y=10+(int)Math.round(Math.sin(a)*10);ctx.fill(x,y,x+2,y+2,i<Math.round((1-cooldown)*24)?color:0x88555555);}
            if(!preview&&readyTicks>0&&HudConfig.bool(mod,"readyPulse",true))ctx.drawBorder(-3,-1,24,24,((readyTicks*10)<<24)|0x55FF88);
        }
}
}
