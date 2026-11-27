package com.batclient.mixin;

import com.batclient.NativeChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements com.batclient.ChatCopyAccess {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;
    @Shadow private boolean hasUnreadNewMessages;
    @Shadow public abstract int getWidth();
    @Shadow public abstract double getChatScale();
    @Shadow public abstract boolean isChatFocused();
    @Shadow public abstract void scroll(int lines);
    @Shadow protected abstract void refresh();
    @Shadow private double toChatLineX(double x){throw new AssertionError();}
    @Shadow private double toChatLineY(double y){throw new AssertionError();}
    @Shadow private int getMessageLineIndex(double x,double y){throw new AssertionError();}
    @Unique private NativeChat bat$chat;
    @Unique private NativeChat bat$state() {
        if(bat$chat==null)bat$chat=new NativeChat();
        return bat$chat;
    }
    @Override public String bat$copyAt(double mouseX,double mouseY){
        int index=getMessageLineIndex(toChatLineX(mouseX),toChatLineY(mouseY-com.batclient.ChatOpeningAnimation.offset()));
        if(index<0||index>=visibleMessages.size())return null;
        return bat$state().copyText(visibleMessages.get(index).content());
    }
    @Inject(method="render",at=@At("HEAD"))
    private void bat$configuration(DrawContext ctx,int ticks,int mouseX,int mouseY,boolean focused,CallbackInfo ci) {
        if(bat$state().configure())refresh();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0,com.batclient.ChatOpeningAnimation.offset(),0);
    }
    @Inject(method="render",at=@At("RETURN"))
    private void bat$endOpening(DrawContext ctx,int ticks,int mouseX,int mouseY,boolean focused,CallbackInfo ci){ctx.getMatrices().pop();}
    @Inject(method="addVisibleMessage",at=@At("HEAD"),cancellable=true)
    private void bat$visible(ChatHudLine message,CallbackInfo ci) {
        bat$state().configure();
        hasUnreadNewMessages|=bat$state().append(client,message,visibleMessages,(int)Math.floor(getWidth()/getChatScale()),isChatFocused(),scrolledLines,this::scroll);
        ci.cancel();
    }
    @Inject(method="refresh",at=@At("HEAD"))
    private void bat$refreshBegin(CallbackInfo ci){bat$state().beginRefresh();}
    @Inject(method="refresh",at=@At("RETURN"))
    private void bat$refreshEnd(CallbackInfo ci){bat$state().endRefresh();}
    @Inject(method="clear",at=@At("HEAD"))
    private void bat$clear(boolean history,CallbackInfo ci){bat$state().clear();}
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
    private int bat$draw(DrawContext ctx,TextRenderer font,OrderedText text,int x,int y,int color) {
        return bat$state().draw(ctx,font,text,x,y,color,isChatFocused(),scrolledLines>0);
    }
}
