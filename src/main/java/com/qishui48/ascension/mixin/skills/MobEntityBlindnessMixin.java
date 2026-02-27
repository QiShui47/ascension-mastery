package com.qishui48.ascension.mixin.skills;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public class MobEntityBlindnessMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void onSetTarget(LivingEntity target, CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        // 如果该生物正在失明，且试图锁定一个新的目标，直接拦截取消！
        if (target != null && self.hasStatusEffect(StatusEffects.BLINDNESS)) {
            ci.cancel();
        }
    }
}