package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MobEntityDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity) (Object) this;

        if (!victim.getWorld().isClient) {
            Entity attacker = source.getAttacker();

            // 如果攻击者是狼
            if (attacker instanceof WolfEntity wolf && wolf.isTamed()) {
                LivingEntity owner = wolf.getOwner();

                // 且主人是玩家
                if (owner instanceof ServerPlayerEntity serverPlayer) {
                    // 增加统计
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.DOG_KILL_MOB_COUNT), 1);
                }
            }
        }
    }
}