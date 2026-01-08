package com.qishui48.ascension.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;

public class LifeStealEnchantment extends Enchantment {

    // 1. 构造函数：配置这个 IP 核的基础参数
    public LifeStealEnchantment() {
        super(
                Rarity.RARE, // 稀有度 (决定了在附魔台出现的概率)
                EnchantmentTarget.WEAPON, // 目标类型 (只能附魔在武器上，不能附魔在盔甲上)
                new EquipmentSlot[]{EquipmentSlot.MAINHAND} // 生效槽位 (拿在主手时生效)
        );
    }

    // 2. 最小/最大等级 (Min/Max Level)
    // 就像配置 IP 核的位宽，这里我们设定最高只能附魔到 3 级
    @Override
    public int getMaxLevel() {
        return 3;
    }

    // 3. 核心逻辑：中断处理 (Callback)
    // 当带有这个附魔的物品攻击了别人，这个方法会被触发
    // user: 攻击者
    // target: 被攻击者
    // level: 附魔等级 (1, 2, 3...)
    @Override
    public void onTargetDamaged(LivingEntity user, Entity target, int level) {
        // 确保目标是活物 (不是矿车或者画)
        if (target instanceof LivingEntity) {
            // 公式：等级 * 1.0f (每级回 1 点血，即半颗心)
            // 3级附魔一次回 1.5 颗心
            float healAmount = level * 0.5f;

            // 执行回血
            user.heal(healAmount);

            // 还可以加个副作用，比如让对面虚弱 (Debuff)
            // ((LivingEntity) target).addStatusEffect(...);
        }

        // 别忘了调用父类，虽然父类目前啥也没干
        super.onTargetDamaged(user, target, level);
    }
}