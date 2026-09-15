package dev.strangequark.stashlight.compat;

import dev.strangequark.stashlight.render.HighlightManager;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** A click captures the current material list for the entire gathering session. */
public final class MaterialSelection {
    private static Set<Item> materials = Set.of();
    private static String dimension;

    private MaterialSelection() {
    }

    public static void highlightContainers(ContainerRepository repository) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        var result = LitematicaCompat.readCurrentMaterials();
        if (result.status() != LitematicaCompat.Status.READY) {
            client.player.sendOverlayMessage(Component.translatable(result.status().translationKey())
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        Set<Item> selected = new HashSet<>();
        result.stacks().forEach(stack -> selected.add(stack.getItem()));
        HighlightManager.clearAll();
        materials = Set.copyOf(selected);
        dimension = Util.getDimensionName(client.level);

        // Material gathering searches every cached container in this dimension,
        // independently of the text, radius and small-container search filters.
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (var item : repository.getSearchIndex()) {
            if (dimension.equals(item.dimension()) && matches(item.stack())) {
                positions.add(item.pos());
            }
        }
        HighlightManager.highlightPersistent(positions);
        client.player.sendOverlayMessage(Component.translatable(
                "render.stashlight.materials.marked", materials.size(), positions.size())
                .withStyle(positions.isEmpty() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        client.setScreen(null);
    }

    public static boolean isActive() {
        var level = Minecraft.getInstance().level;
        return !materials.isEmpty() && level != null && Util.getDimensionName(level).equals(dimension);
    }

    public static void tick() {
        if (!materials.isEmpty() && !isActive()) HighlightManager.clearAll();
    }

    public static boolean matches(ItemStack stack) {
        if (materials.isEmpty() || stack.isEmpty()) return false;
        // Litematica's building materials identify item types; custom names and
        // other incidental components should not hide otherwise usable blocks.
        if (materials.contains(stack.getItem())) return true;
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItemCopyStream().anyMatch(MaterialSelection::matches)) return true;
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle != null && bundle.itemCopyStream().anyMatch(MaterialSelection::matches);
    }

    public static void clear() {
        materials = Set.of();
        dimension = null;
    }
}
