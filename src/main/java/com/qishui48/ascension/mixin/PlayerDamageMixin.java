package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 1. 技能：火焰免疫 (Fire Res)
            if (PacketUtils.isSkillUnlocked(serverPlayer, "fire_res")) {
                if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) {
                    player.extinguish();
                    cir.setReturnValue(false);
                    return;
                }
            }

            // 2. 技能：肾上腺素爆发 (Adrenaline Rush)
            // 判定条件：满血 且 受到伤害 >= 4 (2颗心)
            if (player.getHealth() >= player.getMaxHealth() && amount >= 4.0f) {

                int level = PacketUtils.getSkillLevel(serverPlayer, "adrenaline_burst");

                if (level > 0) {
                    // === 关键：推迟到下一 Tick 执行 ===
                    // 这样就能保证 "先扣血结算，然后再加盾"
                    // 否则如果在 HEAD 加盾，盾会被本次伤害直接打掉
                    serverPlayer.getServer().execute(() -> {
                        if (serverPlayer.isAlive()) {
                            int absorptionAmplifier = level - 1; // 0级=I, 1级=II...
                            int speedAmplifier = (level >= 3) ? 1 : 0; // 3级才有速度II

                            // 给予 Buff
                            serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, absorptionAmplifier));
                            serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, speedAmplifier));

                            // 反馈音效
                            serverPlayer.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.5f);
                        }
                    });
                }
            }
        }
    }
}