package dev.strangequark.stashlight.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class HighlightRenderLayer {

    private static final DepthStencilState XRAY_DEPTH_STATE = new DepthStencilState(CompareOp.ALWAYS_PASS, false);
    private static final ColorTargetState TRANSLUCENT_COLOR = new ColorTargetState(BlendFunction.TRANSLUCENT);

    public static final RenderPipeline XRAY_PIPELINE =
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(Stashlight.MOD_ID, "xray"))
                            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                            .withDepthStencilState(XRAY_DEPTH_STATE)
                            .withColorTargetState(TRANSLUCENT_COLOR)
                            .withCull(false)
                            .build()
            );

    public static final RenderPipeline LINE_PIPELINE =
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(Stashlight.MOD_ID, "xray_debug_lines"))
                            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES)
                            .withDepthStencilState(XRAY_DEPTH_STATE)
                            .withColorTargetState(TRANSLUCENT_COLOR)
                            .withCull(false)
                            .build()
            );

    public static final RenderType XRAY_LAYER = RenderTypeInvoker.create(
            "chestfinder_xray",
            RenderSetup.builder(XRAY_PIPELINE).createRenderSetup()
    );

    public static final RenderType LINE_LAYER = RenderTypeInvoker.create(
            "stashlight_debug_lines",
            RenderSetup.builder(LINE_PIPELINE).createRenderSetup()
    );
}
