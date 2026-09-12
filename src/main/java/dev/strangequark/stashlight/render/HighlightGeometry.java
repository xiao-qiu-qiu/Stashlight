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
    private static final float TRACER_NEAR = 0.25f;
    private static final float TRACER_HALF = 0.005f;


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
     * Thin world-stable tube from a near-plane clip of (camera → block centre)
     * to the block centre. Start is along the line itself, pushed in front of
     * the camera so it is not clipped and does not swing with look direction.
     */
    static void drawTracer(PoseStack matrices, VertexConsumer vc, Camera camera, BlockPos pos) {
        Vec3 cam = camera.position();
        float ex = (float) (pos.getX() + 0.5 - cam.x);
        float ey = (float) (pos.getY() + 0.5 - cam.y);
        float ez = (float) (pos.getZ() + 0.5 - cam.z);
        float len = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (len < 1.0e-3f) return;

        Vector3fc fwd = camera.forwardVector();
        float viewZ = ex * fwd.x() + ey * fwd.y() + ez * fwd.z();
        if (viewZ < 0.05f) return;

        float startDist = Math.max(TRACER_NEAR, TRACER_NEAR * len / viewZ);
        if (startDist >= len) return;

        float inv = 1f / len;
        float dx = ex * inv;
        float dy = ey * inv;
        float dz = ez * inv;
        float sx = dx * startDist;
        float sy = dy * startDist;
        float sz = dz * startDist;

        float ux = 0f, uy = 1f, uz = 0f;
        if (Math.abs(dy) > 0.9f) {
            ux = 1f;
            uy = 0f;
        }
        float px = dy * uz - dz * uy;
        float py = dz * ux - dx * uz;
        float pz = dx * uy - dy * ux;
        float plen = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1.0e-4f) return;
        px /= plen;
        py /= plen;
        pz /= plen;

        float qx = py * dz - pz * dy;
        float qy = pz * dx - px * dz;
        float qz = px * dy - py * dx;

        Matrix4f mat = matrices.last().pose();
        drawTube(vc, mat, sx, sy, sz, ex, ey, ez, px, py, pz, qx, qy, qz);
    }

    private static void drawTube(VertexConsumer vc, Matrix4f mat,
                                 float sx, float sy, float sz,
                                 float ex, float ey, float ez,
                                 float px, float py, float pz,
                                 float qx, float qy, float qz) {
        float h = TRACER_HALF;
        float s00x = sx - px * h - qx * h, s00y = sy - py * h - qy * h, s00z = sz - pz * h - qz * h;
        float s10x = sx + px * h - qx * h, s10y = sy + py * h - qy * h, s10z = sz + pz * h - qz * h;
        float s11x = sx + px * h + qx * h, s11y = sy + py * h + qy * h, s11z = sz + pz * h + qz * h;
        float s01x = sx - px * h + qx * h, s01y = sy - py * h + qy * h, s01z = sz - pz * h + qz * h;
        float e00x = ex - px * h - qx * h, e00y = ey - py * h - qy * h, e00z = ez - pz * h - qz * h;
        float e10x = ex + px * h - qx * h, e10y = ey + py * h - qy * h, e10z = ez + pz * h - qz * h;
        float e11x = ex + px * h + qx * h, e11y = ey + py * h + qy * h, e11z = ez + pz * h + qz * h;
        float e01x = ex - px * h + qx * h, e01y = ey - py * h + qy * h, e01z = ez - pz * h + qz * h;

        quad(vc, mat, s00x, s00y, s00z, s10x, s10y, s10z, e10x, e10y, e10z, e00x, e00y, e00z);
        quad(vc, mat, s10x, s10y, s10z, s11x, s11y, s11z, e11x, e11y, e11z, e10x, e10y, e10z);
        quad(vc, mat, s11x, s11y, s11z, s01x, s01y, s01z, e01x, e01y, e01z, e11x, e11y, e11z);
        quad(vc, mat, s01x, s01y, s01z, s00x, s00y, s00z, e00x, e00y, e00z, e01x, e01y, e01z);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        tracerVertex(vc, mat, x1, y1, z1);
        tracerVertex(vc, mat, x2, y2, z2);
        tracerVertex(vc, mat, x3, y3, z3);
        tracerVertex(vc, mat, x4, y4, z4);
        tracerVertex(vc, mat, x1, y1, z1);
        tracerVertex(vc, mat, x4, y4, z4);
        tracerVertex(vc, mat, x3, y3, z3);
        tracerVertex(vc, mat, x2, y2, z2);
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
