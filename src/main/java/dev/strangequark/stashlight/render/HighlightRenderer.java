package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.model.HighlightPos;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

public final class HighlightRenderer {
    private static final int TRACER_COLOR = 0xFFFFD24D;
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Vector4f PROJECTED = new Vector4f();

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
            HighlightGeometry.drawWireframeBox(matrices, vc, cam, highlight.pos());
            matrices.popPose();
        }
    }

    public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;

        HighlightManager.removeExpired();
        List<HighlightPos> active = HighlightManager.getActiveHighlights();
        if (active.isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return;

        camera.getViewRotationProjectionMatrix(VIEW_PROJ);
        Vec3 cam = camera.position();
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        for (HighlightPos highlight : active) {
            int[] screen = project(highlight.pos(), cam, width, height);
            if (screen == null) continue;
            clipToGui(cx, cy, screen, width, height);
            drawLine(graphics, cx, cy, screen[0], screen[1]);
        }
    }

    private static int[] project(BlockPos pos, Vec3 cam, int width, int height) {
        PROJECTED.set(
                (float) (pos.getX() + 0.5 - cam.x),
                (float) (pos.getY() + 0.5 - cam.y),
                (float) (pos.getZ() + 0.5 - cam.z),
                1f
        );
        VIEW_PROJ.transform(PROJECTED);
        if (PROJECTED.w() <= 0.05f) return null;

        float ndcX = PROJECTED.x() / PROJECTED.w();
        float ndcY = PROJECTED.y() / PROJECTED.w();
        int x = Math.round((ndcX * 0.5f + 0.5f) * width);
        int y = Math.round((1f - (ndcY * 0.5f + 0.5f)) * height);
        return new int[]{x, y};
    }

    private static void clipToGui(int x0, int y0, int[] end, int width, int height) {
        int x1 = end[0];
        int y1 = end[1];
        if (x1 >= 0 && x1 < width && y1 >= 0 && y1 < height) return;

        double dx = x1 - x0;
        double dy = y1 - y0;
        double t = 1;
        if (dx != 0) {
            double tx = dx > 0 ? (width - 1.0 - x0) / dx : (0.0 - x0) / dx;
            if (tx >= 0) t = Math.min(t, tx);
        }
        if (dy != 0) {
            double ty = dy > 0 ? (height - 1.0 - y0) / dy : (0.0 - y0) / dy;
            if (ty >= 0) t = Math.min(t, ty);
        }
        end[0] = (int) Math.round(x0 + dx * t);
        end[1] = (int) Math.round(y0 + dy * t);
        end[0] = Math.max(0, Math.min(width - 1, end[0]));
        end[1] = Math.max(0, Math.min(height - 1, end[1]));
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, TRACER_COLOR);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err * 2;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
