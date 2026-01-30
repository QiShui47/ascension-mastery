package com.qishui48.ascension.mixin.mechanics;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Item.class)
public abstract class ItemMixin {
    @Unique
    public SoundEvent getAscensionEquipSound(Item item) {
        if (item instanceof SwordItem) return SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractGlassBlock) {
            return SoundEvents.BLOCK_GLASS_PLACE;
        }
        return SoundEvents.ITEM_ARMOR_EQUIP_GENERIC;
    }

    // Use 方法负责“特权装备”
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void use(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = user.getStackInHand(hand);

        // === 1. 御剑飞行 ===
        if (stack.getItem() instanceof SwordItem) {
            // 只有服务端才检查技能并执行装备
            if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
                // A. 没技能 -> 啥也不做，返回让原版逻辑处理（原版剑右键无事发生）
                if (!PacketUtils.isSkillActive(serverPlayer, "sword_flight")) {
                    return;
                }

                // B. 有技能 -> 强制装备
                EquipmentSlot slot = EquipmentSlot.FEET;
                ItemStack equippedStack = user.getEquippedStack(slot);
                if (equippedStack.isEmpty()) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    // 强制装备（绕过 UI 限制）
                    user.equipStack(slot, copy);
                    if (!user.getAbilities().creativeMode) stack.decrement(1);

                    // 播放音效
                    user.playSound(this.getAscensionEquipSound(stack.getItem()), 1.0F, 1.0F);

                    // 标记成功
                    cir.setReturnValue(TypedActionResult.success(stack, world.isClient()));
                }
            } else if (world.isClient) {
                // 客户端预判：为了手感，最好也检查一下（如果有同步 NBT）
                // 简单起见，允许客户端先尝试发起包，由服务端裁决
            }
        }

        // === 2. 缸中之脑 ===
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractGlassBlock) {
            if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
                if (!PacketUtils.isSkillActive(serverPlayer, "brain_in_a_jar")) {
                    return; // 没技能 -> 执行原版逻辑（放置方块）
                }

                EquipmentSlot slot = EquipmentSlot.HEAD;
                if (user.getEquippedStack(slot).isEmpty()) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    user.equipStack(slot, copy);
                    if (!user.getAbilities().creativeMode) stack.decrement(1);
                    user.playSound(this.getAscensionEquipSound(stack.getItem()), 1.0F, 1.0F);
                    cir.setReturnValue(TypedActionResult.success(stack, world.isClient()));
                }
            }
        }
    }
}