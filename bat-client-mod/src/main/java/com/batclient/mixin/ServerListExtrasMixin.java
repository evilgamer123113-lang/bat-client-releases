package com.batclient.mixin;

import com.batclient.ServerPreferences;
import com.batclient.ServerListExtras;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;

@Mixin(MultiplayerServerListWidget.class)
public abstract class ServerListExtrasMixin implements ServerListExtras {
    @Unique private String bat$query = "";
    @Invoker("updateEntries") protected abstract void bat$updateEntries();
    @Override public void bat$search(String search) { bat$query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim(); bat$refresh(); }
    @Override public void bat$refresh() {
        var list = (MultiplayerServerListWidget)(Object)this;
        var selected = list.getSelectedOrNull(); double scroll = list.getScrollAmount();
        bat$updateEntries();
        if (selected != null && list.children().contains(selected)) list.setSelected(selected);
        list.setScrollAmount(scroll);
    }
    @Redirect(method = "updateEntries", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void bat$filter(List<MultiplayerServerListWidget.ServerEntry> entries, Consumer<MultiplayerServerListWidget.ServerEntry> consumer) {
        // Vanilla can call updateEntries from its constructor, before Mixin field
        // initializers have executed. A null query must mean no filtering.
        String query = bat$query == null ? "" : bat$query;
        entries.stream().filter(entry -> query.isEmpty() || (entry.getServer().name + " " + entry.getServer().address).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(entry -> !ServerPreferences.flag(entry.getServer().address, "favorite"))).forEach(consumer);
    }
    @Inject(method = "getRowWidth", at = @At("RETURN"), cancellable = true)
    private void bat$gutter(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(cir.getReturnValue() + 20); }
}
