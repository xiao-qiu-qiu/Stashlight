package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.model.HighlightPos;
import dev.strangequark.stashlight.util.Util;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Set;

/**
 * Box + tracer rendering follows Meteor Client StorageESP (Box mode + tracers).
 * Double chests are two inset boxes with the shared face excluded.
 * Tracers start at the inverted NDC origin (crosshair) like Meteor's RenderUtils.center.
 */
public final class HighlightRenderer {

    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Vector4f CENTER = new Vector4f();

    private HighlightRenderer() {
    }

    public static void render(LevelRenderContext context) {
        HighlightManager.removeExpired();
        List<HighlightPos> active = HighlightManager.getActiveHighlights();
        if (active.isEmpty()) return;

        Camera camera = context.gameRenderer().getMainCamera();
        Vec3 cam = camera.position();
        PoseStack matrices = context.poseStack();
        Level level = Minecraft.getInstance().level;

        Vec3 tracerStart = screenCenterOffset(camera);

        VertexConsumer lines = context.bufferSource().getBuffer(HighlightRenderLayer.LINE_LAYER);
        for (HighlightPos highlight : active) {
            Vec3 end = tracerEnd(level, highlight.pos(), cam);
            HighlightGeometry.line(
                    lines, matrices.last().pose(),
                    (float) tracerStart.x, (float) tracerStart.y, (float) tracerStart.z,
                    (float) end.x, (float) end.y, (float) end.z,
                    HighlightGeometry.LINE_R, HighlightGeometry.LINE_G, HighlightGeometry.LINE_B, HighlightGeometry.LINE_A
            );

            long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();
            if (!highlight.persistent() && !HighlightEffect.shouldRender(elapsed)) continue;
            drawStorageBoxes(matrices, lines, null, level, cam, highlight.pos(), true, false);
        }

        VertexConsumer quads = context.bufferSource().getBuffer(HighlightRenderLayer.XRAY_LAYER);
        for (HighlightPos highlight : active) {
            long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();
            if (!highlight.persistent() && !HighlightEffect.shouldRender(elapsed)) continue;
            drawStorageBoxes(matrices, null, quads, level, cam, highlight.pos(), false, true);
        }
    }

    /**
     * Meteor RenderUtils.updateScreenCenter: invert P*V at NDC (0,0,0) to get the
     * world-space point behind the crosshair, then store it camera-relative.
     */
    private static Vec3 screenCenterOffset(Camera camera) {
        camera.getViewRotationProjectionMatrix(VIEW_PROJ);
        CENTER.set(0f, 0f, 0f, 1f);
        new Matrix4f(VIEW_PROJ).invert().transform(CENTER);
        if (CENTER.w() != 0f) CENTER.div(CENTER.w());
        return new Vec3(CENTER.x(), CENTER.y(), CENTER.z());
    }

    private static Vec3 tracerEnd(Level level, BlockPos canonical, Vec3 cam) {
        double x = 0, y = 0, z = 0;
        int n = 0;
        Set<BlockPos> halves = level == null ? Set.of(canonical) : Util.resolveContainerPositions(level, canonical);
        for (BlockPos half : halves) {
            x += half.getX() + 0.5;
            y += half.getY() + 0.5;
            z += half.getZ() + 0.5;
            n++;
        }
        if (n == 0) {
            return new Vec3(canonical.getX() + 0.5 - cam.x, canonical.getY() + 0.5 - cam.y, canonical.getZ() + 0.5 - cam.z);
        }
        return new Vec3(x / n - cam.x, y / n - cam.y, z / n - cam.z);
    }

    private static void drawStorageBoxes(PoseStack matrices, VertexConsumer lines, VertexConsumer quads,
                                         Level level, Vec3 cam, BlockPos canonical,
                                         boolean drawLines, boolean drawSides) {
        Set<BlockPos> halves = level == null ? Set.of(canonical) : Util.resolveContainerPositions(level, canonical);
        for (BlockPos half : halves) {
            matrices.pushPose();
            matrices.translate(half.getX() - cam.x, half.getY() - cam.y, half.getZ() - cam.z);

            float x1 = 0f, y1 = 0f, z1 = 0f;
            float x2 = 1f, y2 = 1f, z2 = 1f;
            int excludeDir = 0;

            if (level != null) {
                BlockState state = level.getBlockState(half);
                if (state.getBlock() instanceof ChestBlock) {
                    if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        excludeDir = HighlightGeometry.dirBit(ChestBlock.getConnectedDirection(state));
                    }
                    float a = 1f / 16f;
                    if (HighlightGeometry.isNot(excludeDir, HighlightGeometry.dirBit(Direction.WEST))) x1 += a;
                    if (HighlightGeometry.isNot(excludeDir, HighlightGeometry.dirBit(Direction.NORTH))) z1 += a;
                    if (HighlightGeometry.isNot(excludeDir, HighlightGeometry.dirBit(Direction.EAST))) x2 -= a;
                    y2 -= a * 2f;
                    if (HighlightGeometry.isNot(excludeDir, HighlightGeometry.dirBit(Direction.SOUTH))) z2 -= a;
                }
            }

            if (drawLines && lines != null) {
                HighlightGeometry.boxLines(matrices, lines, x1, y1, z1, x2, y2, z2, excludeDir);
            }
            if (drawSides && quads != null) {
                HighlightGeometry.boxSides(matrices, quads, x1, y1, z1, x2, y2, z2, excludeDir);
            }
            matrices.popPose();
        }
    }
}
