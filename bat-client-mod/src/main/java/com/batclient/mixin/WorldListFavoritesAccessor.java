package com.batclient.mixin;

import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

@Mixin(WorldListWidget.class)
public interface WorldListFavoritesAccessor {
    @Accessor("search") String bat$getSearch();
    @Accessor("levels") List<LevelSummary> bat$getLevels();
    @Invoker("showSummaries") void bat$refresh(String search, List<LevelSummary> levels);
}
