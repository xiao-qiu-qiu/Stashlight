package dev.strangequark.stashlight.model;

import net.minecraft.core.BlockPos;

/**
 * Immutable data describing a block position being highlighted.
 * Persistent entries stay until the container is opened or the player clears them.
 */
public record HighlightPos(
        BlockPos pos,
        long startTimeMillis,
        boolean persistent
) {
}

