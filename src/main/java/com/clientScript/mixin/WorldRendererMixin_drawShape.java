package com.clientScript.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;

import com.clientScript.utils.ShapesRenderer.ShapesRenderer;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin_drawShape {	
	private ShapesRenderer shapesRenderer = null;
	
	@Inject(
		method = "<init>",
		at = @At("RETURN")
	)
	private void addRenderers(MinecraftClient client, BufferBuilderStorage bufferBuilders, CallbackInfo ci) {
		this.shapesRenderer = new ShapesRenderer();
	}
	
	@Inject(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/render/TexturedRenderLayers;getEntityTranslucentCull()Lnet/minecraft/client/render/RenderLayer;",
			shift = At.Shift.BEFORE
		)
	)
	private void renderShapes(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, CallbackInfo ci) {
		if (this.shapesRenderer != null) {
			this.shapesRenderer.render(matrices, camera, tickDelta);
		}
	}
}
