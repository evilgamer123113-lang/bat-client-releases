package com.batclient.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BossBarHud.class)
public interface BossBarHudAccessor {
    @Accessor("bossBars") Map<UUID, ClientBossBar> bat$getBars();
    @Invoker("renderBossBar") void bat$drawBar(DrawContext context, int x, int y, BossBar bar);
}
