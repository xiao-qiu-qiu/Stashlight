package dev.strangequark.stashlight.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.strangequark.stashlight.render.HighlightRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    // Like Meteor's StorageESP: after LevelRenderer (including Iris compositing),
    // before the hand projection replaces the world projection. Fabric's level
    // events still run inside LevelRenderer's frame graph and are too early.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
            args = "ldc=hand"))
    private void stashlight$renderHighlights(DeltaTracker deltaTracker, CallbackInfo ci,
                                           @Local(name = "projectionMatrix") Matrix4f projection,
                                           @Local(name = "modelViewMatrix") Matrix4fc modelView) {
        HighlightRenderer.render(projection, modelView);
    }
}
