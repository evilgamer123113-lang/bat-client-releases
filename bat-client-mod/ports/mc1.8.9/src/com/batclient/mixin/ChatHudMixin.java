package com.batclient.mixin;

import com.batclient.VisualModules;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    private static final DateTimeFormatter BAT$FMT = DateTimeFormatter.ofPattern("HH:mm");

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text bat$timestamp(Text message) {
        if (message != null && VisualModules.enabled("chattimestamps")) {
            LiteralText prefix = new LiteralText("\u00A77[" + LocalTime.now().format(BAT$FMT) + "] \u00A7r");
            prefix.append(message);
            return prefix;
        }
        return message;
    }
}
