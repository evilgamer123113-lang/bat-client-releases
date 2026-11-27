package com.batclient.mixin;
import com.batclient.ChatCopyAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatCopyMixin {
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void bat$copy(double x,double y,int button,CallbackInfoReturnable<Boolean> ci){
        if(button!=1&&button!=0)return;
        var client=MinecraftClient.getInstance();
        String text=((ChatCopyAccess)client.inGameHud.getChatHud()).bat$copyAt(x,y);
        if(text==null)return;
        if(button==0){if(com.batclient.WaypointChat.click(text))ci.setReturnValue(true);return;}
        client.keyboard.setClipboard(text);
        client.inGameHud.setOverlayMessage(Text.literal("Copied message"),false);
        ci.setReturnValue(true);
    }
}
