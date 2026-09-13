package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.model.HighlightPos;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;

public final class HighlightRenderer {
    private HighlightRenderer() {
    }

    public static void render(LevelRenderContext context) {
        HighlightManager.removeExpired();
        List<HighlightPos> active = HighlightManager.getActiveHighlights();
        if (active.isEmpty()) return;

        VertexConsumer lineVc = context.bufferSource().getBuffer(HighlightRenderLayer.LINE_LAYER);
        Camera camera = context.gameRenderer().getMainCamera();
        Vec3 cam = camera.position();
        PoseStack matrices = context.poseStack();

        for (HighlightPos highlight : active) {
            long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();
            if (!highlight.persistent() && !HighlightEffect.shouldRender(elapsed)) continue;

            matrices.pushPose();
            matrices.translate(
                    highlight.pos().getX() - cam.x,
                    highlight.pos().getY() - cam.y,
                    highlight.pos().getZ() - cam.z
            );
            Vec3 blockOrigin = new Vec3(highlight.pos().getX(), highlight.pos().getY(), highlight.pos().getZ());
            HighlightGeometry.drawTracer(
                    matrices,
                    lineVc,
                    cam.subtract(blockOrigin),
                    new Vec3(0.5, 0.5, 0.5)
            );
            drawStorageBox(matrices, lineVc, highlight.pos());
            matrices.popPose();
        }
    }

    private static void drawStorageBox(PoseStack matrices, VertexConsumer vc, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        double minX = 0, minZ = 0, maxX = 1, maxZ = 1;
        if (mc.level != null) {
            var state = mc.level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                Direction dir = ChestBlock.getConnectedDirection(state);
                if (dir.getAxis() == Direction.Axis.X) {
                    if (dir == Direction.WEST) minX = -1; else maxX = 2;
                } else {
                    if (dir == Direction.NORTH) minZ = -1; else maxZ = 2;
                }
            }
        }
        HighlightGeometry.drawAabb(matrices, vc, minX, 0, minZ, maxX, 1, maxZ);
    }
}
