package dev.strangequark.stashlight.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.strangequark.stashlight.Stashlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalInt;

/** Independent, unlit overlay pass, following Meteor Client's MeshRenderer approach. */
public final class HighlightRenderLayer {
    public static final RenderPipeline XRAY_PIPELINE = pipeline("xray", VertexFormat.Mode.QUADS);
    public static final RenderPipeline LINE_PIPELINE = pipeline("xray_debug_lines", VertexFormat.Mode.DEBUG_LINES);

    private static ByteBufferBuilder vertices;
    private static DynamicUniformStorage<Transform> transforms;

    private HighlightRenderLayer() {
    }

    private static RenderPipeline pipeline(String name, VertexFormat.Mode mode) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(Stashlight.MOD_ID, name))
                .withVertexShader(Identifier.fromNamespaceAndPath(Stashlight.MOD_ID, "core/highlight"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Stashlight.MOD_ID, "core/highlight"))
                .withUniform("HighlightTransform", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build());
    }

    static GpuBufferSlice beginFrame(Matrix4f viewProjection) {
        if (transforms == null) {
            transforms = new DynamicUniformStorage<>("Stashlight transforms",
                    new Std140SizeCalculator().putMat4f().get(), 2);
        }
        transforms.endFrame();
        return transforms.writeUniform(new Transform(new Matrix4f(viewProjection)));
    }

    static BufferBuilder begin(VertexFormat.Mode mode) {
        if (vertices == null) vertices = new ByteBufferBuilder(4096);
        return new BufferBuilder(vertices, mode, DefaultVertexFormat.POSITION_COLOR);
    }

    static void draw(BufferBuilder builder, RenderPipeline pipeline, GpuBufferSlice transform) {
        try (MeshData mesh = builder.build()) {
            if (mesh == null) return;
            var state = mesh.drawState();
            var vertexBuffer = state.format().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            var indices = RenderSystem.getSequentialBuffer(state.mode());
            var indexBuffer = indices.getBuffer(state.indexCount());

            // Explicitly use the main target, never the shared world buffers or
            // output overrides belonging to terrain/translucency/shadow passes.
            var target = Minecraft.getInstance().getMainRenderTarget();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "Stashlight highlights", target.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setUniform("HighlightTransform", transform);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(0, 0, state.indexCount(), 1);
            }
        } finally {
            vertices.clear();
        }
    }

    public static void close() {
        if (vertices != null) {
            vertices.close();
            vertices = null;
        }
        if (transforms != null) {
            transforms.close();
            transforms = null;
        }
    }

    private record Transform(Matrix4f viewProjection) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putMat4f(viewProjection);
        }
    }
}
