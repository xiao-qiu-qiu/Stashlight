package dev.strangequark.stashlight.compat;

import dev.strangequark.stashlight.Stashlight;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Optional integration: no Litematica or MaLiLib classes are linked at startup. */
public final class LitematicaCompat {
    private static final String GUI = "fi.dy.masa.litematica.gui.GuiMaterialList";
    private static Result lastScreenMaterials;
    private static boolean loggedFailure;

    private LitematicaCompat() {
    }

    public enum Status {
        READY(""),
        NOT_INSTALLED("render.stashlight.materials.notInstalled"),
        NO_LIST("render.stashlight.materials.noList"),
        EMPTY("render.stashlight.materials.empty"),
        INCOMPATIBLE("render.stashlight.materials.incompatible");

        private final String translationKey;

        Status(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record Result(Status status, List<ItemStack> stacks) {
    }

    public static void observeScreen(Screen screen) {
        if (!FabricLoader.getInstance().isModLoaded("litematica") || !isMaterialScreen(screen)) return;
        // Capture on close, after the user's final search, ignore and availability
        // filters. Keep no screen/world reference, and include off-screen rows.
        ScreenEvents.remove(screen).register(closed -> lastScreenMaterials = readScreen(closed));
    }

    public static Result readCurrentMaterials() {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return empty(Status.NOT_INSTALLED);
        if (lastScreenMaterials != null) return lastScreenMaterials;
        try {
            Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object list = dataManager.getMethod("getMaterialList").invoke(null);
            if (list == null) return empty(Status.NO_LIST);
            Object hud = list.getClass().getMethod("getHudRenderer").invoke(list);
            if (!Boolean.TRUE.equals(hud.getClass().getMethod("getShouldRenderCustom").invoke(hud))) {
                return empty(Status.NO_LIST);
            }
            // The HUD uses missing materials, including its ignore/availability
            // filtering, rather than the full schematic's total requirements.
            Object entries = list.getClass().getMethod("getMaterialsMissingOnly", boolean.class).invoke(list, true);
            return readEntries(entries);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return failed(e);
        }
    }

    private static Result readScreen(Screen screen) {
        try {
            Method accessor = Class.forName("fi.dy.masa.malilib.gui.GuiListBase")
                    .getDeclaredMethod("getListWidget");
            accessor.setAccessible(true);
            Object widget = accessor.invoke(screen);
            return readEntries(widget.getClass().getMethod("getCurrentEntries").invoke(widget));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Do not silently substitute the unfiltered schematic on API drift.
            return failed(e);
        }
    }

    private static Result readEntries(Object value) throws ReflectiveOperationException {
        if (!(value instanceof Collection<?> entries)) throw new IllegalStateException("Unexpected material list type");
        Method getStack = Class.forName("fi.dy.masa.litematica.materials.MaterialListEntry").getMethod("getStack");
        List<ItemStack> stacks = new ArrayList<>();
        for (Object entry : entries) {
            Object valueStack = getStack.invoke(entry);
            if (!(valueStack instanceof ItemStack stack)) throw new IllegalStateException("Unexpected material stack type");
            if (!stack.isEmpty()) stacks.add(stack.copy());
        }
        return new Result(stacks.isEmpty() ? Status.EMPTY : Status.READY, List.copyOf(stacks));
    }

    private static boolean isMaterialScreen(Screen screen) {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            if (GUI.equals(type.getName())) return true;
        }
        return false;
    }

    private static Result failed(Throwable error) {
        if (!loggedFailure) {
            Stashlight.LOGGER.warn("Could not read the current Litematica material list", error);
            loggedFailure = true;
        }
        return empty(Status.INCOMPATIBLE);
    }

    private static Result empty(Status status) {
        return new Result(status, List.of());
    }

    public static void clear() {
        lastScreenMaterials = null;
        loggedFailure = false;
    }
}
