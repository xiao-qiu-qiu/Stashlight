package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.model.HighlightPos;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
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

        VertexConsumer vc = context.bufferSource().getBuffer(HighlightRenderLayer.XRAY_LAYER);
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
            Vector3fc fwd = camera.forwardVector();
            Vec3 look = new Vec3(fwd.x(), fwd.y(), fwd.z());
            Vec3 blockOrigin = new Vec3(
                    highlight.pos().getX() - cam.x,
                    highlight.pos().getY() - cam.y,
                    highlight.pos().getZ() - cam.z
            );
            HighlightGeometry.drawTracer(
                    matrices,
                    vc,
                    look.scale(0.2).subtract(blockOrigin),
                    new Vec3(0.5, 0.5, 0.5)
            );
            HighlightGeometry.drawWireframeBox(matrices, vc, cam, highlight.pos());
            matrices.popPose();
        }
    }
}
