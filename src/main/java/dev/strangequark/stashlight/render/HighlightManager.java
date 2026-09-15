package dev.strangequark.stashlight.render;

import dev.strangequark.stashlight.compat.MaterialSelection;
import dev.strangequark.stashlight.model.HighlightPos;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class HighlightManager {
    private static final List<HighlightPos> highlights = new ArrayList<>();

    private HighlightManager() {
    }

    public static boolean tryHighlight(IndexedItem item) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;

        String currentDim = Util.getDimensionName(client.level);
        if (!currentDim.equals(item.dimension())) {
            notifyWrongDimension(client.player);
            client.setScreen(null);
            return false;
        }

        if (isPersistent(item.pos())) return true;

        highlights.add(new HighlightPos(item.pos(), System.currentTimeMillis(), false));
        return true;
    }

    public static int highlightPersistent(Collection<BlockPos> positions) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (BlockPos pos : positions) {
            highlights.removeIf(h -> h.pos().equals(pos));
            highlights.add(new HighlightPos(pos, now, true));
            count++;
        }
        return count;
    }

    public static void removeContainer(Level level, BlockPos pos) {
        removeAt(Util.getCanonicalPos(level, pos));
        for (BlockPos half : Util.resolveContainerPositions(level, pos)) {
            removeAt(half);
        }
    }

    public static void removeAt(BlockPos pos) {
        highlights.removeIf(h -> h.pos().equals(pos));
    }

    public static void clearAll() {
        highlights.clear();
        MaterialSelection.clear();
    }

    public static void removeExpired() {
        long now = System.currentTimeMillis();
        highlights.removeIf(h -> !h.persistent() && HighlightEffect.isExpired(now - h.startTimeMillis()));
    }

    public static List<HighlightPos> getActiveHighlights() {
        long now = System.currentTimeMillis();
        List<HighlightPos> active = new ArrayList<>();
        for (HighlightPos h : highlights) {
            if (h.persistent() || !HighlightEffect.isExpired(now - h.startTimeMillis())) {
                active.add(h);
            }
        }
        return active;
    }

    private static boolean isPersistent(BlockPos pos) {
        for (HighlightPos h : highlights) {
            if (h.persistent() && h.pos().equals(pos)) return true;
        }
        return false;
    }

    private static void notifyWrongDimension(@NotNull Player player) {
        player.sendOverlayMessage(Component.translatable("render.stashlight.highlight.incorrectDimension").withStyle(ChatFormatting.RED));
    }
}
