package com.batclient;

import com.batclient.platform.ClientHookServices;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import java.util.Optional;

/** Read-only previews from item data already supplied by Minecraft. */
public final class ItemPreviews {
    private ItemPreviews() {}
    public record Contents(DefaultedList<ItemStack> items,String title,int color) implements TooltipData {}
    public record Comparison(ItemStack hovered,ItemStack equipped,EquipmentSlot slot) implements TooltipData {}
    public static Optional<TooltipData> contents(ItemStack stack) {
        var mod=VisualModules.settings("shulkerpreview");
        if(!HudConfig.enabled(mod)||HudConfig.bool(mod,"shift",true)&&!Screen.hasShiftDown()
            ||!(stack.getItem() instanceof BlockItem block)||!(block.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock))return Optional.empty();
        var container=stack.get(DataComponentTypes.CONTAINER);
        var items=DefaultedList.ofSize(27,ItemStack.EMPTY);if(container!=null)container.copyTo(items);
        var dye=((net.minecraft.block.ShulkerBoxBlock)block.getBlock()).getColor();
        return Optional.of(new Contents(items,stack.getName().getString(),dye==null?0xFF9973BC:0xFF000000|dye.getEntityColor()));
    }
    public static Optional<TooltipData> comparison(ItemStack stack){
        var mod=VisualModules.settings("equipmentcomparison");var player=MinecraftClient.getInstance().player;
        if(!HudConfig.enabled(mod)||player==null||HudConfig.bool(mod,"shift",true)&&!Screen.hasShiftDown())return Optional.empty();
        EquipmentSlot slot;if(stack.getItem() instanceof ArmorItem a)slot=a.getSlotType();else if(stack.getItem() instanceof SwordItem||stack.getItem() instanceof MiningToolItem||stack.getItem() instanceof TridentItem)slot=EquipmentSlot.MAINHAND;else return Optional.empty();
        var equipped=player.getEquippedStack(slot);return ItemStack.areEqual(stack,equipped)?Optional.empty():Optional.of(new Comparison(stack,equipped,slot));
    }
    public static void init(){
        ClientHookServices.get().tooltipComponent(data -> data instanceof Contents c?new Grid(c):data instanceof Comparison c?new CompareGrid(c):null);
        ClientHookServices.get().itemTooltip((stack,lines)->{
            var mod=VisualModules.settings("equipmentcomparison");var player=MinecraftClient.getInstance().player;
            if(!HudConfig.enabled(mod)||player==null||HudConfig.bool(mod,"shift",true)&&!Screen.hasShiftDown())return;
            if(stack.getItem() instanceof ArmorItem armor){
                var equipped=player.getEquippedStack(armor.getSlotType());if(ItemStack.areEqual(stack,equipped))return;
                lines.add(Text.literal("Compared with equipped armor").formatted(Formatting.GRAY));
                lines.addAll(compare(stack,equipped,armor.getSlotType(),HudConfig.bool(mod,"durability",true)));
            }else if(stack.getItem() instanceof SwordItem||stack.getItem() instanceof MiningToolItem||stack.getItem() instanceof TridentItem){
                var equipped=player.getMainHandStack();if(ItemStack.areEqual(stack,equipped))return;
                lines.add(Text.literal("Compared with main hand").formatted(Formatting.GRAY));
                lines.addAll(compare(stack,equipped,EquipmentSlot.MAINHAND,HudConfig.bool(mod,"durability",true)));
            }
        });
    }
    public static java.util.List<Text> compare(ItemStack stack,ItemStack equipped,EquipmentSlot slot,boolean durability){
        var lines=new java.util.ArrayList<Text>();
        if(slot==EquipmentSlot.MAINHAND){
            delta(lines,"Attack damage",(float)(stat(stack,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE,1)-stat(equipped,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE,1)));
            delta(lines,"Attack speed",(float)(stat(stack,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED,4)-stat(equipped,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED,4)));
        }else{
            delta(lines,"Armor",(float)(stat(stack,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR,0)-stat(equipped,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR,0)));
            delta(lines,"Toughness",(float)(stat(stack,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS,0)-stat(equipped,slot,net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS,0)));
        }
        if(durability&&stack.isDamageable())delta(lines,"Durability",stack.getMaxDamage()-stack.getDamage()-(equipped.isDamageable()?equipped.getMaxDamage()-equipped.getDamage():0));
        lines.add(Text.literal("Base item stats; enchantments excluded").formatted(Formatting.DARK_GRAY));return lines;
    }
    private static double stat(ItemStack stack,EquipmentSlot slot,net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,double base){
        double[] values={0,0,1};stack.applyAttributeModifiers(slot,(a,m)->{if(a.equals(attribute))switch(m.operation()){case ADD_VALUE->values[0]+=m.value();case ADD_MULTIPLIED_BASE->values[1]+=m.value();case ADD_MULTIPLIED_TOTAL->values[2]*=1+m.value();}});
        double added=base+values[0];return (added+added*values[1])*values[2];
    }
    private static void delta(java.util.List<Text> lines,String name,float value){lines.add(Text.literal(String.format(java.util.Locale.ROOT,"%s: %+.1f",name,value)).formatted(value>0?Formatting.GREEN:value<0?Formatting.RED:Formatting.GRAY));}
    private record Grid(Contents contents) implements TooltipComponent {
        public int getHeight(){return contents.items().stream().allMatch(ItemStack::isEmpty)?32:74;}
        public int getWidth(TextRenderer font){return 164;}
        public void drawItems(TextRenderer font,int x,int y,DrawContext ctx){
            int header=0xFF000000|((contents.color()&0xFEFEFE)>>>1);
            ctx.fill(x,y,x+164,y+14,header);ctx.drawText(font,font.trimToWidth(contents.title(),156),x+4,y+3,0xFFFFFFFF,true);
            if(contents.items().stream().allMatch(ItemStack::isEmpty)){ctx.drawText(font,"Empty box",x+4,y+20,0xFFAAAAAA,false);return;}
            for(int i=0;i<27;i++){int sx=x+(i%9)*18,sy=y+18+(i/9)*18;ctx.fill(sx,sy,sx+17,sy+17,0x55333333);var item=contents.items().get(i);if(!item.isEmpty()){ctx.drawItem(item,sx+1,sy+1);ctx.drawItemInSlot(font,item,sx+1,sy+1);}}
            if(inspectMouseX>=x&&inspectMouseX<x+162&&inspectMouseY>=y+18&&inspectMouseY<y+72){int index=((inspectMouseY-y-18)/18)*9+(inspectMouseX-x)/18;var item=contents.items().get(index);if(!item.isEmpty())ctx.drawTooltip(font,item.getName(),inspectMouseX,inspectMouseY);}
        }
    }
    private record CompareGrid(Comparison data) implements TooltipComponent {
        public int getHeight(){return 69;}
        public int getWidth(TextRenderer font){return 238;}
        public void drawItems(TextRenderer font,int x,int y,DrawContext ctx){
            ctx.fill(x,y,x+238,y+65,0x55333333);ctx.fill(x+118,y,x+119,y+65,0xFF777777);
            ItemStack[] items={data.hovered(),data.equipped()};
            for(int i=0;i<2;i++){int left=x+i*120;ctx.drawItem(items[i],left+3,y+3);ctx.drawText(font,i==0?"Hovered":"Equipped",left+23,y+3,0xFFAAAAAA,false);ctx.drawText(font,font.trimToWidth(items[i].isEmpty()?"Empty":items[i].getName().getString(),112),left+3,y+22,-1,false);
                var attribute=data.slot()==EquipmentSlot.MAINHAND?net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE:net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR;
                var secondary=data.slot()==EquipmentSlot.MAINHAND?net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED:net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS;
                ctx.drawText(font,String.format(java.util.Locale.ROOT,"%s: %.1f",data.slot()==EquipmentSlot.MAINHAND?"Damage":"Armor",stat(items[i],data.slot(),attribute,data.slot()==EquipmentSlot.MAINHAND?1:0)),left+3,y+36,-1,false);
                ctx.drawText(font,String.format(java.util.Locale.ROOT,"%s: %.1f",data.slot()==EquipmentSlot.MAINHAND?"Speed":"Toughness",stat(items[i],data.slot(),secondary,data.slot()==EquipmentSlot.MAINHAND?4:0)),left+3,y+49,-1,false);
            }
        }
    }
    private static net.minecraft.client.gui.screen.Screen inspectScreen;
    private static ItemStack pinned=ItemStack.EMPTY;
    private static int pinX,pinY,inspectMouseX=-1,inspectMouseY=-1;
    /** Shift pins a shulker tooltip so the pointer can inspect individual item names. */
    public static boolean inspect(DrawContext ctx,net.minecraft.screen.slot.Slot slot,int mouseX,int mouseY){
        var c=MinecraftClient.getInstance();if(inspectScreen!=c.currentScreen){pinned=ItemStack.EMPTY;inspectScreen=c.currentScreen;}
        if(!VisualModules.enabled("shulkerpreview")||!Screen.hasShiftDown()){pinned=ItemStack.EMPTY;inspectMouseX=inspectMouseY=-1;return false;}
        if(slot!=null&&slot.hasStack()&&slot.getStack().getItem() instanceof BlockItem b&&b.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock){if(pinned.isEmpty()||!ItemStack.areEqual(pinned,slot.getStack())){pinned=slot.getStack().copy();pinX=mouseX;pinY=mouseY;}}
        if(pinned.isEmpty())return false;inspectMouseX=mouseX;inspectMouseY=mouseY;ctx.drawItemTooltip(c.textRenderer,pinned,pinX,pinY);return true;
    }
}
