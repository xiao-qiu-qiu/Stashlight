package dev.strangequark.stashlight.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fabric screen events expose slots, but not their screen-space origin. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int stashlight$getLeftPos();

    @Accessor("topPos")
    int stashlight$getTopPos();
}
