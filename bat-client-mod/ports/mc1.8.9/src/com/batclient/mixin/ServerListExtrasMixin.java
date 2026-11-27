package com.batclient.mixin;

import com.batclient.ServerListExtras;
import com.batclient.ServerPreferences;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ServerEntry;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Mixin(MultiplayerServerListWidget.class)
public abstract class ServerListExtrasMixin implements ServerListExtras {
    @Shadow
    @Final
    private List<ServerEntry> servers;

    @Unique
    private String bat$query = "";

    @Unique
    private List<ServerEntry> bat$allServers;

    @Unique
    private List<ServerEntry> bat$all() {
        if (bat$allServers == null) bat$allServers = new ArrayList<ServerEntry>();
        return bat$allServers;
    }

    @Override
    public void bat$search(String search) {
        bat$query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        bat$refresh();
    }

    @Override
    public void bat$refresh() {
        bat$applyFilter();
    }

    @Inject(method = "setServers(Lnet/minecraft/client/option/ServerList;)V", at = @At("TAIL"))
    private void bat$onSetServers(ServerList list, CallbackInfo ci) {
        List<ServerEntry> all = bat$all();
        all.clear();
        if (this.servers != null) all.addAll(this.servers);
        bat$applyFilter();
    }

    @Unique
    private void bat$applyFilter() {
        List<ServerEntry> all = bat$all();
        if (all.isEmpty() || this.servers == null) return;
        String query = bat$query == null ? "" : bat$query;
        List<ServerEntry> filtered = new ArrayList<ServerEntry>();
        for (ServerEntry entry : all) {
            if (query.isEmpty() || (entry.getServer().name + " " + entry.getServer().address).toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(entry);
            }
        }
        filtered.sort(Comparator.comparing(entry -> !ServerPreferences.flag(entry.getServer().address, "favorite")));
        this.servers.clear();
        this.servers.addAll(filtered);
    }

    @Inject(method = "getRowWidth()I", at = @At("RETURN"), cancellable = true)
    private void bat$gutter(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + 20);
    }
}
