package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class HighlightGeometry {

    private static final float THICKNESS = 0.02f;
    private static final float MAX_THICKNESS = 0.7f;
    private static final float R = 1f, G = 1f, B = 1f, A = 1f;


    static void drawWireframeBox(PoseStack matrices, VertexConsumer vc, Vec3 cam, BlockPos pos) {
        double dist = pos.distToCenterSqr(cam.x, cam.y, cam.z);
        float t = (float) Math.min(THICKNESS + Math.sqrt(dist) * 0.002, MAX_THICKNESS);

        float min = -t;
        float max = 1f + t;

        // Vertical pillars
        drawBox(matrices, vc, min, min, min, min + t, max, min + t);
        drawBox(matrices, vc, max - t, min, min, max, max, min + t);
        drawBox(matrices, vc, min, min, max - t, min + t, max, max);
        drawBox(matrices, vc, max - t, min, max - t, max, max, max);

        // Bottom edges
        drawBox(matrices, vc, min + t, min, min, max - t, min + t, min + t);
        drawBox(matrices, vc, min + t, min, max - t, max - t, min + t, max);
        drawBox(matrices, vc, min, min, min + t, min + t, min + t, max - t);
        drawBox(matrices, vc, max - t, min, min + t, max, min + t, max - t);

        // Top edges
        drawBox(matrices, vc, min + t, max - t, min, max - t, max, min + t);
        drawBox(matrices, vc, min + t, max - t, max - t, max - t, max, max);
        drawBox(matrices, vc, min, max - t, min + t, min + t, max, max - t);
        drawBox(matrices, vc, max - t, max - t, min + t, max, max, max - t);
    }

    static void drawTracer(PoseStack matrices, VertexConsumer vc, Vec3 start, Vec3 end) {
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 0.01) return;
        Matrix4f mat = matrices.last().pose();
        vertex(vc, mat, start, 1f, 0.82f, 0.3f, 1f);
        vertex(vc, mat, end, 1f, 0.82f, 0.3f, 1f);
    }

    static void drawStorageBox(PoseStack matrices, VertexConsumer vc, BlockPos pos) {
        Matrix4f mat = matrices.last().pose();
        float min = 1f / 16f;
        float max = 1f - min;
        line(vc, mat, 0.5f, 0.0f, min, 0.5f, 1.0f - 2f * min, min);
        line(vc, mat, min, 0.0f, 0.5f, max, 0.0f, 0.5f);
        line(vc, mat, min, 1.0f - 2f * min, 0.5f, max, 1.0f - 2f * min, 0.5f);
        line(vc, mat, min, 0.0f, min, max, 0.0f, min);
        line(vc, mat, min, 1.0f - 2f * min, min, max, 1.0f - 2f * min, min);
        line(vc, mat, min, 0.0f, max, max, 0.0f, max);
        line(vc, mat, min, 1.0f - 2f * min, max, max, 1.0f - 2f * min, max);
        line(vc, mat, min, 0.0f, min, min, 1.0f - 2f * min, min);
        line(vc, mat, max, 0.0f, min, max, 1.0f - 2f * min, min);
        line(vc, mat, min, 0.0f, max, min, 1.0f - 2f * min, max);
        line(vc, mat, max, 0.0f, max, max, 1.0f - 2f * min, max);
    }

    private static void line(VertexConsumer vc, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2) {
        vertex(vc, mat, new Vec3(x1, y1, z1), 1f, 1f, 1f, 1f);
        vertex(vc, mat, new Vec3(x2, y2, z2), 1f, 1f, 1f, 1f);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    private static void drawBox(PoseStack matrices, VertexConsumer vc,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2) {
        Matrix4f mat = matrices.last().pose();

        // Top
        vertex(vc, mat, x1, y2, z1);
        vertex(vc, mat, x1, y2, z2);
        vertex(vc, mat, x2, y2, z2);
        vertex(vc, mat, x2, y2, z1);

        // Bottom
        vertex(vc, mat, x1, y1, z2);
        vertex(vc, mat, x1, y1, z1);
        vertex(vc, mat, x2, y1, z1);
        vertex(vc, mat, x2, y1, z2);

        // Front
        vertex(vc, mat, x1, y1, z1);
        vertex(vc, mat, x1, y2, z1);
        vertex(vc, mat, x2, y2, z1);
        vertex(vc, mat, x2, y1, z1);

        // Back
        vertex(vc, mat, x2, y1, z2);
        vertex(vc, mat, x2, y2, z2);
        vertex(vc, mat, x1, y2, z2);
        vertex(vc, mat, x1, y1, z2);

        // Left
        vertex(vc, mat, x1, y1, z2);
        vertex(vc, mat, x1, y2, z2);
        vertex(vc, mat, x1, y2, z1);
        vertex(vc, mat, x1, y1, z1);

        // Right
        vertex(vc, mat, x2, y1, z1);
        vertex(vc, mat, x2, y2, z1);
        vertex(vc, mat, x2, y2, z2);
        vertex(vc, mat, x2, y1, z2);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z) {
        vc.addVertex(mat, x, y, z).setColor(R, G, B, A).setNormal(0f, 1f, 0f);
    }
}
