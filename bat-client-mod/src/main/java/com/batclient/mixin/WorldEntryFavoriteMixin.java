package com.batclient.mixin;

import com.batclient.WorldFavorites;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.text.Text;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldListWidget.WorldEntry.class)
public abstract class WorldEntryFavoriteMixin implements WorldFavorites.Entry {
    @Shadow @Final private LevelSummary level;
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private WorldListWidget field_19135;
    @Unique private int bat$starX, bat$starY;
    @Override public String bat$worldFolder() { return level.getName(); }

    @Inject(method = "render", at = @At("HEAD"))
    private void bat$star(DrawContext ctx, int index, int y, int x, int width, int height,
                          int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
        bat$starX = x; bat$starY = y + (height - 18) / 2;
        boolean over = mouseX >= x && mouseX < x + 18 && mouseY >= bat$starY && mouseY < bat$starY + 18;
        boolean favorite = WorldFavorites.contains(level.getName());
        com.batclient.web.MenuBranding.drawIcon(ctx, x + 1, bat$starY + 1, favorite ? 1 : 0);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(20, 0, 0);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void bat$finishRow(DrawContext ctx, int index, int y, int x, int width, int height,
                              int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
        ctx.getMatrices().pop();
        mouseX += 20;
        if (mouseX >= bat$starX && mouseX < bat$starX + 18 && mouseY >= bat$starY && mouseY < bat$starY + 18)
            ctx.drawTooltip(client.textRenderer, Text.literal(WorldFavorites.contains(level.getName()) ? "Remove favorite" : "Favorite world"), mouseX, mouseY);
    }

    @ModifyVariable(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelSummary;getDisplayName()Ljava/lang/String;"), argsOnly = true, ordinal = 5)
    private int bat$shiftIconHover(int mouseX) { return mouseX - 20; }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bat$toggle(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || x < bat$starX || x >= bat$starX + 18 || y < bat$starY || y >= bat$starY + 18) return;
        if (WorldFavorites.toggle(level.getName())) {
            // Rebuild after the click finishes; keep the selected world and scroll position.
            client.send(() -> {
                WorldListFavoritesAccessor list = (WorldListFavoritesAccessor) field_19135;
                if (list.bat$getLevels() == null) return;
                var selected = field_19135.getSelectedOrNull();
                String folder = selected instanceof WorldFavorites.Entry entry ? entry.bat$worldFolder() : null;
                double scroll = field_19135.getScrollAmount();
                list.bat$refresh(list.bat$getSearch(), list.bat$getLevels());
                if (folder != null) for (var entry : field_19135.children()) {
                    if (entry instanceof WorldListWidget.WorldEntry world && entry instanceof WorldFavorites.Entry favorite
                            && folder.equals(favorite.bat$worldFolder())) field_19135.setSelected(world);
                }
                field_19135.setScrollAmount(scroll);
            });
        }
        cir.setReturnValue(true);
    }

    // Vanilla measures the play-icon hitbox from the row's original left edge.
    @ModifyVariable(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelSummary;isSelectable()Z"), argsOnly = true, ordinal = 0)
    private double bat$shiftIconHitbox(double x) { return x - 20; }
}
