package com.qishui48.ascension.mixin.skills;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityBlindnessMixin {
    @Inject(method = "setAttacker", at = @At("HEAD"), cancellable = true)
    private void onSetAttacker(LivingEntity attacker, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 阻止失明的生物记住是谁打了它（切断逃跑 AI 寻路源）
        if (attacker != null && self.hasStatusEffect(StatusEffects.BLINDNESS)) {
            ci.cancel();
        }
    }
}