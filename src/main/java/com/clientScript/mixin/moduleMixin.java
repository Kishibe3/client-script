package com.clientScript.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;

import com.clientScript.language.ScriptHost.ClientScriptHost;

@Mixin(MinecraftClient.class)
public class moduleMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void load(CallbackInfo info) {
        ClientScriptHost.globalHost.load();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo info) {
        ClientScriptHost.globalHost.tick();
    }
}
