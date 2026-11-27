package com.batclient.mixin;

import com.batclient.ClientMods;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseZoomMixin {
    @ModifyArgs(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void bat$zoomSensitivity(Args args) {
        double scale = ClientMods.zoomMouseScale();
        if(com.batclient.Freelook.mouse((double)args.get(0)*scale,(double)args.get(1)*scale)){args.set(0,0d);args.set(1,0d);return;}
        if(scale==1)return; // Preserve vanilla arguments exactly outside Zoom/Freelook.
        args.set(0, (double) args.get(0) * scale);
        args.set(1, (double) args.get(1) * scale);
    }
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void bat$scrollZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ClientMods.scrollZoom(window, vertical)) ci.cancel();
    }
}
