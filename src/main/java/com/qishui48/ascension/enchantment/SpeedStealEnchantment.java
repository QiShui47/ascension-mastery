package com.qishui48.ascension.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

public class SpeedStealEnchantment extends Enchantment {

    public SpeedStealEnchantment() {
        super(Rarity.RARE, EnchantmentTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public void onTargetDamaged(LivingEntity user, Entity target, int level) {
        if (target instanceof LivingEntity livingTarget) {
            // 计算持续时间 (刻): 比如 1级=3秒(60ticks), 2级=5秒(100ticks)... 你可以按需调整
            int duration = 60 + (level * 40);

            // 1. 偷取：给予自己速度
            // level 0 (效果等级1) = +20% 速度
            // level 1 (效果等级2) = +40% 速度
            // 我们用 level - 1 来对应附魔等级 I, II, III
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, level - 1));

            // 2. 剥夺：给予对方缓慢
            livingTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, level - 1));

            // 3. 视觉效果：生成粒子
            // 只有在服务端才能生成粒子传给客户端
            if (user.getWorld() instanceof ServerWorld serverWorld) {
                // 在目标身上生成“灵魂”粒子，表示速度被吸走了
                serverWorld.spawnParticles(ParticleTypes.SOUL,
                        livingTarget.getX(), livingTarget.getY() + 1, livingTarget.getZ(),
                        10, 0.5, 0.5, 0.5, 0.1);
            }
        }
        super.onTargetDamaged(user, target, level);
    }
}