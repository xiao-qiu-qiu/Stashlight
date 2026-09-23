package dev.strangequark.stashlight.serializer;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.model.ContainerSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Serializer {
    private final Path file;
    private final HolderLookup.Provider lookup;
    private boolean loadSucceeded = true;

    private static final String NBT_CONTAINER_NAME_KEY = "name";
    private static final String NBT_CONTAINER_CAPACITY_KEY = "capacity";
    private static final String NBT_STACK_LIST_KEY = "items";
    private static final String NBT_TIMESTAMP_KEY = "time";

    public Serializer(Path file, HolderLookup.Provider lookup) {
        this.file = file;
        this.lookup = lookup;
    }

    public boolean loadSucceeded() {
        return loadSucceeded;
    }

    public Map<String, Map<BlockPos, ContainerSnapshot>> read() {
        Map<String, Map<BlockPos, ContainerSnapshot>> database = new HashMap<>();
        loadSucceeded = true;
        if (file == null || !Files.exists(file)) return database;

        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

            int containers = 0;
            for (String dim : root.keySet()) {
                CompoundTag dimTag = root.getCompound(dim).orElseThrow();
                Map<BlockPos, ContainerSnapshot> posMap = new HashMap<>();
                for (String key : dimTag.keySet()) {
                    BlockPos pos = BlockPos.of(Long.parseLong(key));
                    CompoundTag snapNbt = dimTag.getCompound(key).orElseThrow();
                    posMap.put(pos, deserializeSnapshot(snapNbt));
                }
                containers += posMap.size();
                database.put(dim, posMap);
            }
            Stashlight.LOGGER.info("Loaded {} cached containers from {}", containers, file.getFileName());
        } catch (Exception e) {
            loadSucceeded = false;
            Stashlight.LOGGER.error("Load failed; existing cache file will not be overwritten", e);
            return new HashMap<>();
        }
        return database;
    }

    public boolean write(Map<String, Map<BlockPos, ContainerSnapshot>> database) {
        // Enforce this for every caller, including periodic saves and cleanup.
        if (file == null || !loadSucceeded) return false;

        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            CompoundTag root = new CompoundTag();
            database.forEach((dim, posMap) -> {
                CompoundTag dimTag = new CompoundTag();
                posMap.forEach((pos, snap) -> dimTag.put(String.valueOf(pos.asLong()), serializeSnapshot(snap)));
                root.put(dim, dimTag);
            });
            NbtIo.writeCompressed(root, tmp);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            Stashlight.LOGGER.error("Save failed", e);
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private CompoundTag serializeSnapshot(ContainerSnapshot snap) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(NBT_CONTAINER_NAME_KEY, snap.containerName());
        nbt.putInt(NBT_CONTAINER_CAPACITY_KEY, snap.containerCapacity());
        nbt.putLong(NBT_TIMESTAMP_KEY, snap.timestamp());

        ListTag itemList = new ListTag();
        for (ItemStack stack : snap.items()) {
            itemList.add(serializeStack(stack));
        }
        nbt.put(NBT_STACK_LIST_KEY, itemList);
        return nbt;
    }

    private ContainerSnapshot deserializeSnapshot(CompoundTag nbt) {
        String name = nbt.getString(NBT_CONTAINER_NAME_KEY).orElseThrow();
        int capacity = nbt.getInt(NBT_CONTAINER_CAPACITY_KEY).orElseThrow();
        long timestamp = nbt.getLong(NBT_TIMESTAMP_KEY).orElseThrow();

        List<ItemStack> items = new ArrayList<>();
        ListTag itemList = nbt.getList(NBT_STACK_LIST_KEY).orElseThrow();
        for (int i = 0; i < itemList.size(); i++) {
            items.add(deserializeStack(itemList.getCompound(i).orElseThrow()));
        }
        return new ContainerSnapshot(name, capacity, items, timestamp);
    }

    private Tag serializeStack(ItemStack stack) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, lookup);
        // Abort the save instead of replacing an unencodable item with an empty compound.
        return ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
    }

    private ItemStack deserializeStack(CompoundTag nbt) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, lookup);
        // Let read() protect the original file if even one item fails to decode.
        return ItemStack.CODEC.parse(ops, nbt).getOrThrow();
    }
}
