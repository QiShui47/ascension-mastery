package com.qishui48.ascension.enchantment;

import com.qishui48.ascension.Ascension;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;

// 这是一个基类，所有特殊的弓附魔都继承它
public class SpecialBowEnchantment extends Enchantment {

    public SpecialBowEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    // 核心：互斥逻辑
    @Override
    public boolean canAccept(Enchantment other) {
        // 如果“另一个附魔”也是我们要互斥的这几个之一，返回 false
        if (other instanceof SpecialBowEnchantment) {
            return false;
        }
        // 还要互斥之前写好的歼星炮、生命之力、水桶 (如果你把它们也改成了这个基类最好，如果没有，需要手动列出)
        // 假设你把之前的附魔变量都存在 Ascension 里了
        if (other == Ascension.DEATH_STAR_CANNON ||
                other == Ascension.POCKET_BUCKET ||
                other == Ascension.LIFE_FORCE) {
            return false;
        }

        return super.canAccept(other);
    }
}