package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Storage-ESP style geometry, ported from Meteor Client's Renderer3D box/line helpers.
 */
public final class HighlightGeometry {

    static final float LINE_R = 1f, LINE_G = 160f / 255f, LINE_B = 0f, LINE_A = 1f;
    static final float SIDE_R = LINE_R, SIDE_G = LINE_G, SIDE_B = LINE_B, SIDE_A = 50f / 255f;

    private static final int UP = 1 << 1;
    private static final int DOWN = 1 << 2;
    private static final int NORTH = 1 << 3;
    private static final int SOUTH = 1 << 4;
    private static final int WEST = 1 << 5;
    private static final int EAST = 1 << 6;

    private HighlightGeometry() {
    }

    static int dirBit(Direction dir) {
        return switch (dir) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    static boolean isNot(int excludeDir, int face) {
        return (excludeDir & face) != face;
    }

    static void line(VertexConsumer vc, Matrix4f mat,
                     float x1, float y1, float z1,
                     float x2, float y2, float z2,
                     float r, float g, float b, float a) {
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
    }

    static void boxLines(PoseStack matrices, VertexConsumer vc,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         int excludeDir) {
        Matrix4f mat = matrices.last().pose();
        float r = LINE_R, g = LINE_G, b = LINE_B, a = LINE_A;

        if (excludeDir == 0) {
            line(vc, mat, x1, y1, z1, x1, y2, z1, r, g, b, a);
            line(vc, mat, x1, y1, z2, x1, y2, z2, r, g, b, a);
            line(vc, mat, x2, y1, z1, x2, y2, z1, r, g, b, a);
            line(vc, mat, x2, y1, z2, x2, y2, z2, r, g, b, a);

            line(vc, mat, x1, y1, z1, x1, y1, z2, r, g, b, a);
            line(vc, mat, x2, y1, z1, x2, y1, z2, r, g, b, a);
            line(vc, mat, x1, y1, z1, x2, y1, z1, r, g, b, a);
            line(vc, mat, x1, y1, z2, x2, y1, z2, r, g, b, a);

            line(vc, mat, x1, y2, z1, x1, y2, z2, r, g, b, a);
            line(vc, mat, x2, y2, z1, x2, y2, z2, r, g, b, a);
            line(vc, mat, x1, y2, z1, x2, y2, z1, r, g, b, a);
            line(vc, mat, x1, y2, z2, x2, y2, z2, r, g, b, a);
            return;
        }

        if (isNot(excludeDir, WEST) && isNot(excludeDir, NORTH)) line(vc, mat, x1, y1, z1, x1, y2, z1, r, g, b, a);
        if (isNot(excludeDir, WEST) && isNot(excludeDir, SOUTH)) line(vc, mat, x1, y1, z2, x1, y2, z2, r, g, b, a);
        if (isNot(excludeDir, EAST) && isNot(excludeDir, NORTH)) line(vc, mat, x2, y1, z1, x2, y2, z1, r, g, b, a);
        if (isNot(excludeDir, EAST) && isNot(excludeDir, SOUTH)) line(vc, mat, x2, y1, z2, x2, y2, z2, r, g, b, a);

        if (isNot(excludeDir, WEST) && isNot(excludeDir, DOWN)) line(vc, mat, x1, y1, z1, x1, y1, z2, r, g, b, a);
        if (isNot(excludeDir, EAST) && isNot(excludeDir, DOWN)) line(vc, mat, x2, y1, z1, x2, y1, z2, r, g, b, a);
        if (isNot(excludeDir, NORTH) && isNot(excludeDir, DOWN)) line(vc, mat, x1, y1, z1, x2, y1, z1, r, g, b, a);
        if (isNot(excludeDir, SOUTH) && isNot(excludeDir, DOWN)) line(vc, mat, x1, y1, z2, x2, y1, z2, r, g, b, a);

        if (isNot(excludeDir, WEST) && isNot(excludeDir, UP)) line(vc, mat, x1, y2, z1, x1, y2, z2, r, g, b, a);
        if (isNot(excludeDir, EAST) && isNot(excludeDir, UP)) line(vc, mat, x2, y2, z1, x2, y2, z2, r, g, b, a);
        if (isNot(excludeDir, NORTH) && isNot(excludeDir, UP)) line(vc, mat, x1, y2, z1, x2, y2, z1, r, g, b, a);
        if (isNot(excludeDir, SOUTH) && isNot(excludeDir, UP)) line(vc, mat, x1, y2, z2, x2, y2, z2, r, g, b, a);
    }

    static void boxSides(PoseStack matrices, VertexConsumer vc,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         int excludeDir) {
        Matrix4f mat = matrices.last().pose();
        if (excludeDir == 0 || isNot(excludeDir, WEST)) {
            quad(vc, mat, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        }
        if (excludeDir == 0 || isNot(excludeDir, EAST)) {
            quad(vc, mat, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
        }
        if (excludeDir == 0 || isNot(excludeDir, NORTH)) {
            quad(vc, mat, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        }
        if (excludeDir == 0 || isNot(excludeDir, SOUTH)) {
            quad(vc, mat, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        }
        if (excludeDir == 0 || isNot(excludeDir, DOWN)) {
            quad(vc, mat, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        }
        if (excludeDir == 0 || isNot(excludeDir, UP)) {
            quad(vc, mat, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        vc.addVertex(mat, x1, y1, z1).setColor(SIDE_R, SIDE_G, SIDE_B, SIDE_A);
        vc.addVertex(mat, x2, y2, z2).setColor(SIDE_R, SIDE_G, SIDE_B, SIDE_A);
        vc.addVertex(mat, x3, y3, z3).setColor(SIDE_R, SIDE_G, SIDE_B, SIDE_A);
        vc.addVertex(mat, x4, y4, z4).setColor(SIDE_R, SIDE_G, SIDE_B, SIDE_A);
    }
}
