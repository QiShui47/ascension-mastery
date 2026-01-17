package com.qishui48.ascension.mixin.skills;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntitySkillMixin {

    // === 舍身一击逻辑 ===
    @Inject(method = "damage", at = @At("HEAD"))
    private void onDamageSacrificialSkill(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            if (((com.qishui48.ascension.util.ISacrificialState) player).isSacrificialReady()) {
                if (PacketUtils.isSkillActive(player, "sacrificial_strike")) {
                    ((com.qishui48.ascension.util.ISacrificialState) player).setSacrificialReady(false);
                    // 这里原文件似乎留了坑没填完，但逻辑位置是在这里的
                }
            }
        }
    }
}