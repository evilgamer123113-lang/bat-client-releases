package com.batclient.platform;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/** Callback-scoped vanilla selection, not a custom raycast or generated chunk request. */
public interface BlockTarget {
    BlockState blockState();
    BlockPos blockPos();
    Entity entity();
}
