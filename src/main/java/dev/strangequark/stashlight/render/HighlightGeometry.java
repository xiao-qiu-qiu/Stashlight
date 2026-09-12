package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

public final class HighlightGeometry {

    private static final float THICKNESS = 0.02f;
    private static final float MAX_THICKNESS = 0.7f;
    private static final float R = 1f, G = 1f, B = 1f, A = 1f;

    /** Gold tracer: distinct from the white wireframe, still readable on most backgrounds. */
    private static final float TR = 1f, TG = 0.82f, TB = 0.18f, TA = 0.95f;
    private static final float TRACER_NEAR = 0.2f;
    private static final float TRACER_HALF = 0.004f;


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

    /**
     * Uniform-width ribbon from just in front of the camera (the crosshair)
     * to the block centre. Two crossed quads so the line stays visible from the side.
     */
    static void drawTracer(PoseStack matrices, VertexConsumer vc, Camera camera, BlockPos pos) {
        Vec3 cam = camera.position();
        Vector3fc fwd = camera.forwardVector();
        Vector3fc up = camera.upVector();

        float sx = fwd.x() * TRACER_NEAR;
        float sy = fwd.y() * TRACER_NEAR;
        float sz = fwd.z() * TRACER_NEAR;

        float ex = (float) (pos.getX() + 0.5 - cam.x);
        float ey = (float) (pos.getY() + 0.5 - cam.y);
        float ez = (float) (pos.getZ() + 0.5 - cam.z);

        float dx = ex - sx;
        float dy = ey - sy;
        float dz = ez - sz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-3f) return;

        float px = dy * up.z() - dz * up.y();
        float py = dz * up.x() - dx * up.z();
        float pz = dx * up.y() - dy * up.x();
        float plen = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1.0e-4f) {
            Vector3fc left = camera.leftVector();
            px = left.x();
            py = left.y();
            pz = left.z();
            plen = 1f;
        }
        px /= plen;
        py /= plen;
        pz /= plen;

        Matrix4f mat = matrices.last().pose();
        drawRibbon(vc, mat, sx, sy, sz, ex, ey, ez, px, py, pz);
        drawRibbon(vc, mat, sx, sy, sz, ex, ey, ez, up.x(), up.y(), up.z());
    }

    private static void drawRibbon(VertexConsumer vc, Matrix4f mat,
                                   float sx, float sy, float sz,
                                   float ex, float ey, float ez,
                                   float nx, float ny, float nz) {
        float s0x = sx - nx * TRACER_HALF, s0y = sy - ny * TRACER_HALF, s0z = sz - nz * TRACER_HALF;
        float s1x = sx + nx * TRACER_HALF, s1y = sy + ny * TRACER_HALF, s1z = sz + nz * TRACER_HALF;
        float e0x = ex - nx * TRACER_HALF, e0y = ey - ny * TRACER_HALF, e0z = ez - nz * TRACER_HALF;
        float e1x = ex + nx * TRACER_HALF, e1y = ey + ny * TRACER_HALF, e1z = ez + nz * TRACER_HALF;

        tracerVertex(vc, mat, s0x, s0y, s0z);
        tracerVertex(vc, mat, s1x, s1y, s1z);
        tracerVertex(vc, mat, e1x, e1y, e1z);
        tracerVertex(vc, mat, e0x, e0y, e0z);

        tracerVertex(vc, mat, s0x, s0y, s0z);
        tracerVertex(vc, mat, e0x, e0y, e0z);
        tracerVertex(vc, mat, e1x, e1y, e1z);
        tracerVertex(vc, mat, s1x, s1y, s1z);
    }

    private static void tracerVertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z) {
        vc.addVertex(mat, x, y, z).setColor(TR, TG, TB, TA).setNormal(0f, 1f, 0f);
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
