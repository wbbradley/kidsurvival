package com.kidsurvival.mixin;

import com.kidsurvival.KidSurvivalMod;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public class HungerManagerMixin {
    @Shadow
    private int foodLevel;

    @Shadow
    private float saturationLevel;

    @Shadow
    private float exhaustion;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdate(ServerPlayerEntity player, CallbackInfo ci) {
        if (KidSurvivalMod.kidModePlayers.contains(player.getUuid())) {
            this.foodLevel = 20;
            this.saturationLevel = 20.0f;
            this.exhaustion = 0.0f;
            ci.cancel();
        }
    }
}
