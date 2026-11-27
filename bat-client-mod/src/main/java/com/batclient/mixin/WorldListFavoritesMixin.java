package com.batclient.mixin;

import com.batclient.WorldFavorites;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

@Mixin(WorldListWidget.class)
public abstract class WorldListFavoritesMixin {
    @Inject(method = "getRowWidth", at = @At("RETURN"), cancellable = true)
    private void bat$favoriteGutter(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + 20);
    }
    @ModifyVariable(method = "showSummaries", at = @At("HEAD"), argsOnly = true)
    private List<LevelSummary> bat$favoritesFirst(List<LevelSummary> summaries) {
        // Stable sort retains Minecraft's last-played order within each group.
        return summaries.stream().sorted(Comparator.comparing(level -> !WorldFavorites.contains(level.getName()))).toList();
    }
}
