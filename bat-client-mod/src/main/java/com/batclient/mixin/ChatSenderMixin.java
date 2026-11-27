package com.batclient.mixin;
import com.batclient.NativeChat;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MessageHandler.class)
public class ChatSenderMixin {
    @Inject(method="onChatMessage",at=@At("HEAD"))
    private void bat$sender(SignedMessage message,GameProfile sender,MessageType.Parameters params,CallbackInfo ci) {
        NativeChat.remember(message.signature(),sender.getId());
    }
}
