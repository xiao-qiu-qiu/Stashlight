package dev.strangequark.stashlight.persistence;

import dev.strangequark.stashlight.model.ContainerSnapshot;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.serializer.Serializer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone persistence regression checks run by the cacheRegressionTest Gradle task. */
public final class RegressionChecks {
    private static final BlockPos TEST_POS = new BlockPos(12, 64, -8);

    private RegressionChecks() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        bindFixtureItemComponents();

        Path tempRoot = Files.createTempDirectory("stashlight-cache-regression-");
        try {
            testNormalRoundTrip(tempRoot, lookup);
            testCorruptAndItemDecodeFailureProtectFile(tempRoot, lookup);
            testItemEncodeFailureProtectFile(tempRoot, lookup);
            testRepositoryRetriesFailedWrite(tempRoot, lookup);
            System.out.println("Cache persistence regression checks passed (4/4)");
        } finally {
            deleteTree(tempRoot);
        }
    }

    private static void testNormalRoundTrip(Path tempRoot, HolderLookup.Provider lookup) throws IOException {
        Path file = tempRoot.resolve("roundtrip.dat");
        Map<String, Map<BlockPos, ContainerSnapshot>> database = database(new ItemStack(Items.DIAMOND, 3));

        Serializer writer = new Serializer(file, lookup);
        check(writer.write(database), "normal cache write failed");

        Serializer reader = new Serializer(file, lookup);
        Map<String, Map<BlockPos, ContainerSnapshot>> loaded = reader.read();
        check(reader.loadSucceeded(), "normal cache read failed");
        ContainerSnapshot snapshot = loaded.get("overworld").get(TEST_POS);
        check(snapshot != null, "normal cache entry missing");
        check(snapshot.items().size() == 1, "normal cache item count changed");
        check(snapshot.items().get(0).getItem() == Items.DIAMOND, "normal cache item changed");
        check(snapshot.items().get(0).getCount() == 3, "normal cache item count changed");
        check(snapshot.timestamp() == 42L, "normal cache timestamp changed");
    }

    private static void testCorruptAndItemDecodeFailureProtectFile(
            Path tempRoot,
            HolderLookup.Provider lookup
    ) throws IOException {
        Path corruptFile = tempRoot.resolve("corrupt.dat");
        byte[] corruptBytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        Files.write(corruptFile, corruptBytes);

        Serializer corruptSerializer = new Serializer(corruptFile, lookup);
        corruptSerializer.read();
        check(!corruptSerializer.loadSucceeded(), "raw corruption was accepted");
        check(!corruptSerializer.write(database(new ItemStack(Items.DIAMOND))),
                "write succeeded after raw corruption");
        check(Arrays.equals(corruptBytes, Files.readAllBytes(corruptFile)),
                "raw corruption file was modified");

        Path itemFile = tempRoot.resolve("item-decode-failure.dat");
        Map<String, Map<BlockPos, ContainerSnapshot>> validDatabase =
                database(new ItemStack(Items.DIAMOND, 2));
        Serializer validWriter = new Serializer(itemFile, lookup);
        check(validWriter.write(validDatabase), "failed to create valid cache for decode test");

        CompoundTag root = NbtIo.readCompressed(itemFile, NbtAccounter.unlimitedHeap());
        CompoundTag dimension = root.getCompound("overworld").orElseThrow();
        CompoundTag snapshot = dimension.getCompound(Long.toString(TEST_POS.asLong())).orElseThrow();
        ListTag items = snapshot.getList("items").orElseThrow();
        items.set(0, new CompoundTag());
        NbtIo.writeCompressed(root, itemFile);
        byte[] invalidItemBytes = Files.readAllBytes(itemFile);

        Serializer itemFailureSerializer = new Serializer(itemFile, lookup);
        itemFailureSerializer.read();
        check(!itemFailureSerializer.loadSucceeded(), "item decode failure was accepted");
        check(!itemFailureSerializer.write(validDatabase),
                "write succeeded after item decode failure");
        check(Arrays.equals(invalidItemBytes, Files.readAllBytes(itemFile)),
                "item decode failure file was modified");
    }

    private static void testItemEncodeFailureProtectFile(Path tempRoot, HolderLookup.Provider lookup)
            throws IOException {
        Path file = tempRoot.resolve("item-encode-failure.dat");
        Serializer serializer = new Serializer(file, lookup);
        check(serializer.write(database(new ItemStack(Items.IRON_INGOT, 4))),
                "failed to create valid cache for encode test");
        byte[] originalBytes = Files.readAllBytes(file);

        Map<String, Map<BlockPos, ContainerSnapshot>> invalidDatabase =
                database(ItemStack.EMPTY);
        check(!serializer.write(invalidDatabase), "invalid item encoding unexpectedly succeeded");
        check(Arrays.equals(originalBytes, Files.readAllBytes(file)),
                "item encode failure replaced the original file");
    }

    private static void testRepositoryRetriesFailedWrite(Path tempRoot, HolderLookup.Provider lookup)
            throws Exception {
        FailingSerializer serializer = new FailingSerializer(tempRoot.resolve("retry.dat"), lookup);
        ContainerRepository repository = new ContainerRepository(serializer);
        try {
            repository.update("overworld", TEST_POS, "Chest", 27, List.of(new ItemStack(Items.CHEST)));

            repository.saveIfDirty();
            awaitSave(repository, serializer, 1);
            check(serializer.writeCount() == 1, "first retry test write did not run");

            repository.saveIfDirty();
            awaitSave(repository, serializer, 2);
            check(serializer.writeCount() == 2, "dirty state was cleared after failed write");
        } finally {
            repository.shutdown();
        }
    }

    private static Map<String, Map<BlockPos, ContainerSnapshot>> database(ItemStack stack) {
        Map<String, Map<BlockPos, ContainerSnapshot>> database = new HashMap<>();
        Map<BlockPos, ContainerSnapshot> dimension = new HashMap<>();
        dimension.put(TEST_POS, new ContainerSnapshot("Test Chest", 27, List.of(stack), 42L));
        database.put("overworld", dimension);
        return database;
    }

    private static void bindFixtureItemComponents() {
        // Minimal fixtures for exercising the real codecs without loading a world's data packs.
        Items.DIAMOND.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.CHEST.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    private static void awaitSave(ContainerRepository repository, FailingSerializer serializer, int writes)
            throws Exception {
        Field pendingField = ContainerRepository.class.getDeclaredField("isSavePending");
        pendingField.setAccessible(true);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (!pendingField.getBoolean(repository) && serializer.writeCount() >= writes) {
                return;
            }
            Thread.sleep(5L);
        }
        throw new AssertionError("timed out waiting for save attempt " + writes);
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FailingSerializer extends Serializer {
        private final AtomicInteger writes = new AtomicInteger();

        private FailingSerializer(Path file, HolderLookup.Provider lookup) {
            super(file, lookup);
        }

        @Override
        public Map<String, Map<BlockPos, ContainerSnapshot>> read() {
            return new HashMap<>();
        }

        @Override
        public boolean write(Map<String, Map<BlockPos, ContainerSnapshot>> database) {
            return writes.incrementAndGet() >= 2;
        }

        private int writeCount() {
            return writes.get();
        }
    }
}
