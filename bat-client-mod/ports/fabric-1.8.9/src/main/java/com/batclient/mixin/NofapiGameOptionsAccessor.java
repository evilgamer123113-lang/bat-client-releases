package com.batclient.mixin;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameOptions.class)
public interface NofapiGameOptionsAccessor {
    @Accessor("allKeys")
    KeyBinding[] bat$getKeysAll();

    @Accessor("allKeys")
    @Mutable
    void bat$setKeysAll(KeyBinding[] keys);
}
