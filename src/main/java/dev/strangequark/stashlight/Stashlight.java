package dev.strangequark.stashlight;

import com.mojang.blaze3d.platform.InputConstants;
import dev.strangequark.stashlight.compat.LitematicaCompat;
import dev.strangequark.stashlight.compat.MaterialSelection;
import dev.strangequark.stashlight.render.HighlightManager;
import dev.strangequark.stashlight.render.HighlightRenderLayer;
import dev.strangequark.stashlight.render.MaterialSlotHighlight;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.screen.SearchScreen;
import dev.strangequark.stashlight.serializer.Serializer;
import dev.strangequark.stashlight.util.Util;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class Stashlight implements ClientModInitializer {
    public static final String MOD_ID = "stashlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Serializer serializer;
    private ContainerRepository repository;
    private static KeyMapping searchKey;
    public static final KeyMapping.Category STASHLIGHT = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "stashlight"));

    private int tickCounter = 0;

    @Nullable
    private BlockPos lastOpened;
    @Nullable
    private ClientLevel lastOpenedLevel;

    private final Map<Screen, ContainerContext> openContainers = new WeakHashMap<>();

    private record ContainerContext(ClientLevel level, ContainerRepository repository, BlockPos pos) {
    }

    @Override
    public void onInitializeClient() {
        Init.init();
        UseBlockCallback.EVENT.register(this::onBlockUsed);
        ClientPlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> HighlightRenderLayer.close());

        searchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stashlight.search_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                STASHLIGHT
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (searchKey.consumeClick()) {
                if (client.level == null || repository == null) continue;

                if (client.screen instanceof SearchScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new SearchScreen(repository));
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MaterialSelection.tick();
            if (client.level == null || repository == null) return;

            tickCounter++;

            if (tickCounter % 100 == 0) {
                repository.runCleanup(client.level);
            }

            if (tickCounter % 3000 == 0) {
                repository.saveIfDirty();
                tickCounter = 0;
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serializer = new Serializer(Init.getFileName(), handler.registryAccess());
            repository = new ContainerRepository(serializer);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (repository != null) {
                repository.shutdown();

            }
            HighlightManager.clearAll();
            LitematicaCompat.clear();
            lastOpened = null;
            lastOpenedLevel = null;
            openContainers.clear();
            serializer = null;
            repository = null;
        });
    }

    private InteractionResult onBlockUsed(Player player, Level level, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        // UseBlockCallback also runs on the integrated server. Only track local interactions.
        Minecraft client = Minecraft.getInstance();
        if (level != client.level || player != client.player) return InteractionResult.PASS;

        lastOpened = null;
        lastOpenedLevel = null;
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (Util.isValidSearchableContainer(state)) {
            lastOpened = pos.immutable();
            lastOpenedLevel = client.level;
            HighlightManager.removeContainer(level, pos);
        }
        return InteractionResult.PASS;
    }

    private void onBlockBreak(ClientLevel clientLevel, LocalPlayer localPlayer, BlockPos blockPos, BlockState blockState) {
        if (repository == null || !(blockState.getBlock() instanceof EntityBlock)) {
            return;
        }

        // Use the canonical resolution to find the correct database key to delete.
        BlockPos canonicalPos = Util.getCanonicalPos(clientLevel, blockPos);
        String dimension = Util.getDimensionName(clientLevel);
        repository.remove(dimension, canonicalPos);
        HighlightManager.removeContainer(clientLevel, blockPos);
    }


    private void onScreenInit(Minecraft client, Screen screen, int w, int h) {
        LitematicaCompat.observeScreen(screen);
        BlockPos pendingPos = lastOpened;
        ClientLevel pendingLevel = lastOpenedLevel;
        lastOpened = null;
        lastOpenedLevel = null;
        if (client.level == null || client.player == null || screen instanceof CreativeModeInventoryScreen) {
            return;
        }

        if (screen instanceof AbstractContainerScreen<?> handled) {
            var handler = handled.getMenu();
            // A failed chest interaction must never turn the player's inventory into a chest snapshot.
            if (handler == client.player.inventoryMenu) return;
            ScreenEvents.afterExtract(screen).register(MaterialSlotHighlight::extract);

            // Screen events reset on resize; retain the original association across reinitialization.
            if (!openContainers.containsKey(screen) && pendingPos != null
                    && pendingLevel == client.level && repository != null) {
                openContainers.put(screen, new ContainerContext(client.level, repository, pendingPos));
            }
            ContainerContext context = openContainers.get(screen);
            if (context == null) return;
            // Serialize on close to ensure the database reflects the final state of the inventory.
            ScreenEvents.remove(screen).register(closedScreen -> {
                if (openContainers.remove(closedScreen) != null) {
                    serializeContainer(client, handler, context);
                }
            });
        }
    }

    private void serializeContainer(Minecraft client, AbstractContainerMenu handler, ContainerContext context) {
        if (client.level != context.level() || repository != context.repository()) {
            return;
        }

        String dimension = Util.getDimensionName(client.level);
        Set<BlockPos> pair = Util.resolveContainerPositions(client.level, context.pos());
        BlockPos canonicalPos = Util.getCanonicalPos(client.level, pair.iterator().next());
        BlockState blockstate = client.level.getBlockState(canonicalPos);


        if (!Util.isValidSearchableContainer(blockstate)) {
            return;
        }

        var stacks = handler.getItems();
        int containerSize = stacks.size() - 36;
        if (containerSize <= 0) {
            return;
        }

        // HARD INVALIDATION — nuke positions data
        repository.remove(dimension, canonicalPos);
        for (BlockPos p : pair) {
            repository.remove(dimension, p);
        }

        // Single authoritative write
        repository.update(
                dimension,
                canonicalPos,
                blockstate.getBlock().getName().getString(),
                containerSize,
                stacks.subList(0, containerSize)
        );
    }
}
