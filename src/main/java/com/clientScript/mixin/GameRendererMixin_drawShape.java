package com.clientScript.mixin;

import org.objectweb.asm.Opcodes;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;

import com.clientScript.utils.ShapesRenderer.ShapesRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin_drawShape {
	@Shadow
	@Final
	private Camera camera;

	private ShapesRenderer shapesRenderer = null;
	
	@Inject(
		method = "<init>",
		at = @At("RETURN")
	)
	private void addRenderers(MinecraftClient client, ResourceManager resourceManager, BufferBuilderStorage bufferBuilders, CallbackInfo ci) {
		this.shapesRenderer = new ShapesRenderer();
	}

	@Inject(
		method = "renderWorld",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z",
			opcode = Opcodes.GETFIELD,
			ordinal = 0
		)
	)
	private void renderShapes(float tickDelta, long finishTimeNano, MatrixStack matrices, CallbackInfo ci) {
		if (this.shapesRenderer != null) {
			this.shapesRenderer.render(matrices, this.camera, tickDelta);
		}
	}
}
