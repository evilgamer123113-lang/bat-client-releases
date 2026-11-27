package com.batclient;

import com.batclient.hud.ChatFlow;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/** Native visual chat only. Canonical signed history is owned entirely by vanilla. */
public final class NativeChat {
    private static final Map<MessageSignatureData,UUID> SENDERS=new LinkedHashMap<>();
    private static final Pattern LEGACY=Pattern.compile("^(?:<([A-Za-z0-9_]{1,16})>|([A-Za-z0-9_]{1,16}):) .+");
    private final Map<OrderedText,Line> rendered=new IdentityHashMap<>();
    private Group last;
    private JsonObject heads,animations,repeats;
    private boolean refreshing;
    private String configuration="";
    private boolean dirty;
    private JsonObject lastSnapshot;
    private static final class Group {
        final Text text; final UUID sender; final Object indicator; final long born;
        int count=1,tick;
        Group(ChatHudLine line,UUID sender,boolean refresh) {
            text=line.content();this.sender=sender;indicator=line.indicator();tick=line.creationTick();
            born=refresh?0:System.nanoTime()/1_000_000L;
        }
    }
    private record Line(Group group,boolean first,boolean head) { }
    public static void remember(MessageSignatureData signature,UUID sender) {
        if(signature==null || sender==null)return;
        SENDERS.put(signature,sender);
        while(SENDERS.size()>256)SENDERS.remove(SENDERS.keySet().iterator().next());
    }
    public boolean configure() {
        JsonObject cfg=HudConfig.snapshot();
        if(cfg==lastSnapshot)return dirty;
        lastSnapshot=cfg;
        heads=HudConfig.module(cfg,"chatheads");animations=HudConfig.module(cfg,"chatanimations");repeats=HudConfig.module(cfg,"chatrepeats");
        String next=String.valueOf(heads)+animations+repeats;
        dirty|=!configuration.equals(next);configuration=next;return dirty;
    }
    public void beginRefresh() { refreshing=true;dirty=false;last=null;rendered.clear(); }
    public void endRefresh() { refreshing=false; }
    public void clear() { last=null;rendered.clear();SENDERS.clear();refreshing=false; }
    private UUID sender(MinecraftClient c,ChatHudLine line) {
        UUID known=line.signature()==null?null:SENDERS.get(line.signature());
        if(known!=null)return known;
        if(c.getNetworkHandler()==null)return null;
        var match=LEGACY.matcher(line.content().getString());
        if(!match.matches())return null;
        String name=match.group(1)!=null?match.group(1):match.group(2);
        for(PlayerListEntry player:c.getNetworkHandler().getPlayerList())if(player.getProfile().getName().equals(name))return player.getProfile().getId();
        return null;
    }
    public boolean append(MinecraftClient c,ChatHudLine message,List<ChatHudLine.Visible> visible,int width,boolean focused,int scrolled,IntConsumer scroll) {
        UUID sender=sender(c,message);
        boolean merge=last!=null && Objects.equals(last.indicator,message.indicator()) &&
            ChatFlow.repeats(message.content(),last.text,sender,last.sender,message.creationTick(),last.tick,HudConfig.setNumber(repeats,"window",10));
        if(merge) {
            Group prior=last;
            visible.removeIf(line->{Line metadata=rendered.get(line.content());return metadata!=null&&metadata.group==prior;});
            Group next=new Group(message,sender,refreshing);next.count=Math.min(9999,prior.count+1);last=next;
        }else last=new Group(message,sender,refreshing);
        boolean head=sender!=null;
        Text text=message.content();
        if(sender!=null)
            text=text.copy().append(Text.literal(" ["+last.count+"x]").formatted(Formatting.GRAY));
        if(head)text=Text.literal("   ").append(text); // 12 native pixels reserved for the 8px face + gap.
        var icon=message.getIcon();
        int available=width-(icon==null?0:icon.width+6);
        List<OrderedText> lines=ChatMessages.breakRenderedChatMessageLines(text,Math.max(1,available),c.textRenderer);
        boolean unread=false;
        for(int i=0;i<lines.size();i++) {
            if(focused&&scrolled>0){unread=true;scroll.accept(1);}
            OrderedText content=lines.get(i);
            visible.add(0,new ChatHudLine.Visible(message.creationTick(),content,message.indicator(),i==lines.size()-1));
            rendered.put(content,new Line(last,i==0,head));
        }
        while(visible.size()>100)visible.remove(visible.size()-1);
        Set<OrderedText> active=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var line:visible)active.add(line.content());
        rendered.keySet().retainAll(active);
        return unread;
    }
    public int draw(DrawContext ctx,TextRenderer font,OrderedText text,int x,int y,int color,boolean focused,boolean scrolled) {
        Line line=rendered.get(text);
        if(line==null)return ctx.drawTextWithShadow(font,text,x,y,color);
        float progress=1;
        if(!focused&&!scrolled)
            progress=ChatFlow.progress(System.nanoTime()/1_000_000L-line.group.born,HudConfig.setNumber(animations,"duration",200));
        float alpha=progress;
        if(focused)alpha*=ChatOpeningAnimation.progress();
        if(alpha<.02f)return 0;
        int originalAlpha=color>>>24;
        int animatedColor=(color&0xFFFFFF)|(Math.max(4,(int)(originalAlpha*alpha))<<24);
        float distance=HudConfig.setNumber(animations,"slide",8);
        if(!Float.isFinite(distance))distance=8;
        float offset=(1-progress)*Math.clamp(distance,0,16);
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(offset,0,0);
            if(line.head&&line.first) {
                MinecraftClient c=MinecraftClient.getInstance();
                PlayerListEntry entry=c.getNetworkHandler()==null?null:c.getNetworkHandler().getPlayerListEntry(line.group.sender);
                if(entry!=null) {
                    RenderSystem.setShaderColor(1,1,1,originalAlpha/255f*alpha);
                    try { PlayerSkinDrawer.draw(ctx,entry.getSkinTextures().texture(),x,y,8,HudConfig.bool(heads,"hat",true),false); }
                    finally { RenderSystem.setShaderColor(1,1,1,1); }
                }
            }
            return ctx.drawTextWithShadow(font,text,x,y,animatedColor);
        }finally{ctx.getMatrices().pop();}
    }
    public String copyText(OrderedText content){
        Line line=rendered.get(content);
        return line==null?null:Formatting.strip(line.group.text.getString());
    }
}
