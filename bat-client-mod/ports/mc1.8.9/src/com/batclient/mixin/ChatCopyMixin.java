package com.batclient.mixin;

import com.batclient.ChatCopyAccess;
import com.batclient.web.BatWebClipboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatCopyMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bat$copy(int x, int y, int button, CallbackInfo ci) {
        if (button != 1 && button != 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        String text = ((ChatCopyAccess) client.inGameHud.getChatHud()).bat$copyAt(x, y);
        if (text == null) return;
        if (button == 0) {
            if (com.batclient.WaypointChat.click(text)) ci.cancel();
            return;
        }
        new BatWebClipboard().write(text);
        client.inGameHud.getChatHud().addMessage(new LiteralText("Copied message"));
        ci.cancel();
    }
}
