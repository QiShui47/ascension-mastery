package com.qishui48.ascension.mixin.skills;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
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

    // 不败金身 - 次要效果（拦截状态添加）
    // 注入 addStatusEffect 方法
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void onAddStatusEffect(StatusEffectInstance effect, net.minecraft.entity.Entity source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // 只对服务端玩家生效
        if (!entity.getWorld().isClient && entity instanceof ServerPlayerEntity player) {
            IEntityDataSaver data = (IEntityDataSaver) player;

            // 检查不败金身状态 (次要效果)
            if (data.getPersistentData().contains("invincible_status_end")) {
                long endTime = data.getPersistentData().getLong("invincible_status_end");
                if (player.getWorld().getTime() < endTime) {
                    // 免疫所有新状态
                    cir.setReturnValue(false);
                }
            }
        }
    }
}