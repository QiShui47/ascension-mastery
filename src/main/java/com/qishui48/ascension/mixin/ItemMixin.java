package com.qishui48.ascension.mixin;

import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Equipment; // 1.20.1 新增的接口
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;

// 1. 目标改为 BlockItem
// 2. 实现 Equipment 接口
@Mixin(BlockItem.class)
public class ItemMixin implements Equipment {

    @Override
    public EquipmentSlot getSlotType() {
        // 强转 this 为 BlockItem 以获取方块类型
        if (((BlockItem) (Object) this).getBlock() instanceof AbstractGlassBlock) {
            // 如果是玻璃类方块，允许戴在头上
            return EquipmentSlot.HEAD;
        }
        // 其他方块物品默认只能拿在手上（不可装备）
        return EquipmentSlot.MAINHAND;
    }

    // 可选：自定义装备音效
    @Override
    public SoundEvent getEquipSound() {
        if (((BlockItem) (Object) this).getBlock() instanceof AbstractGlassBlock) {
            return SoundEvents.BLOCK_GLASS_PLACE; // 装备时播放放置玻璃的声音
        }
        return SoundEvents.ITEM_ARMOR_EQUIP_GENERIC;
    }
}