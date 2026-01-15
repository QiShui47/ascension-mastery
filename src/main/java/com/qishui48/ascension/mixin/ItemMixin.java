package com.qishui48.ascension.mixin;

import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// [修复] 将目标改为 Item.class，这样才能覆盖 SwordItem
@Mixin(Item.class)
public class ItemMixin implements Equipment {

    @Override
    public EquipmentSlot getSlotType() {
        // 1. 御剑飞行：如果是剑，允许装备在脚部
        if ((Object) this instanceof SwordItem) {
            return EquipmentSlot.FEET;
        }

        // 2. 缸中之脑：如果是方块物品，且是玻璃，允许装备在头部
        // [关键] 必须先判断是否为 BlockItem，否则剑走到这里会报错
        if ((Object) this instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof AbstractGlassBlock) {
                return EquipmentSlot.HEAD;
            }
        }

        // 默认逻辑
        return EquipmentSlot.MAINHAND;
    }

    @Override
    public SoundEvent getEquipSound() {
        // 剑的装备音效
        if ((Object) this instanceof SwordItem) {
            return SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
        }
        // 玻璃的装备音效
        if ((Object) this instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof AbstractGlassBlock) {
                return SoundEvents.BLOCK_GLASS_PLACE;
            }
        }
        return SoundEvents.ITEM_ARMOR_EQUIP_GENERIC;
    }

    // 右键快速装备逻辑
    // 允许玩家手持剑右键直接装备到脚上 (仅当脚上没东西时)
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void use(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = user.getStackInHand(hand);
        // 检测：如果是剑
        if (stack.getItem() instanceof SwordItem) {
            EquipmentSlot slot = EquipmentSlot.FEET;
            ItemStack equippedStack = user.getEquippedStack(slot);
            // 检测：靴子槽必须为空 (防止误操作把好装备顶掉了)
            if (equippedStack.isEmpty()) {
                // 执行装备逻辑
                ItemStack copy = stack.copy();
                copy.setCount(1); // 只装备一把
                user.equipStack(slot, copy);
                // 扣除手上的物品 (生存模式)
                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                // 播放音效 (调用上面定义的 getEquipSound)
                user.playSound(this.getEquipSound(), 1.0F, 1.0F);
                // 返回成功 (阻止后续逻辑，比如剑的格挡或挥舞)
                cir.setReturnValue(TypedActionResult.success(stack, world.isClient()));
            }
        }
    }
}