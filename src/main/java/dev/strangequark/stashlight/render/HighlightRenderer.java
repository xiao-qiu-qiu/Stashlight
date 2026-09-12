package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.model.HighlightPos;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public final class HighlightRenderer {

    private HighlightRenderer() {
    }

    public static void render(LevelRenderContext context) {
        VertexConsumer vc = context.bufferSource().getBuffer(HighlightRenderLayer.XRAY_LAYER);
        Camera camera = context.gameRenderer().getMainCamera();
        Vec3 cam = camera.position();

        HighlightManager.removeExpired();

        PoseStack matrices = context.poseStack();
        for (HighlightPos highlight : HighlightManager.getActiveHighlights()) {
            long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();

            // Tracer stays visible for the whole highlight lifetime so the heading
            // is not lost during the wireframe blink-off.
            HighlightGeometry.drawTracer(matrices, vc, camera, highlight.pos());

            if (!HighlightEffect.shouldRender(elapsed)) continue;

            matrices.pushPose();
            matrices.translate(
                    highlight.pos().getX() - cam.x,
                    highlight.pos().getY() - cam.y,
                    highlight.pos().getZ() - cam.z
            );
            HighlightGeometry.drawWireframeBox(matrices, vc, cam, highlight.pos());
            matrices.popPose();
        }
    }
}
