package dev.strangequark.stashlight.render;

import dev.strangequark.stashlight.compat.MaterialSelection;
import dev.strangequark.stashlight.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class MaterialSlotHighlight {
    private MaterialSlotHighlight() {
    }

    public static void extract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var client = Minecraft.getInstance();
        if (!MaterialSelection.isActive() || client.player == null
                || !(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        var origin = (ContainerScreenAccessor) containerScreen;
        for (var slot : containerScreen.getMenu().slots) {
            if (slot.container == client.player.getInventory() || !slot.isActive()
                    || !MaterialSelection.matches(slot.getItem())) continue;

            int x = origin.stashlight$getLeftPos() + slot.x;
            int y = origin.stashlight$getTopPos() + slot.y;
            graphics.fill(x, y, x + 16, y + 16, 0x403DE8C5);
            graphics.outline(x - 1, y - 1, 18, 18, 0xFF3DE8C5);
        }
    }
}
