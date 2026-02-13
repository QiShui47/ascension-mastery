package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {

    @Shadow public abstract PlayerEntity getPlayerOwner();

    // 改用 Redirect 拦截 spawnEntity 调用，这比 Inject+LocalCapture 稳定得多
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean checkCatch(World world, Entity entity) {
        // 只有当生成的是物品实体时才检查（防止检查到经验球等其他东西）
        if (!world.isClient && entity instanceof ItemEntity) {
            ItemStack stack = ((ItemEntity) entity).getStack();
            PlayerEntity player = this.getPlayerOwner();

            if (player != null) {
                // 1. 检查河豚
                if (stack.isOf(Items.PUFFERFISH)) {
                    player.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.FISH_PUFFERFISH_COUNT), 1);
                }

                // 2. 检查水下呼吸附魔书
                if (stack.isOf(Items.ENCHANTED_BOOK)) {
                    int respLevel = EnchantmentHelper.getLevel(Enchantments.RESPIRATION, stack);
                    if (respLevel > 0) {
                        player.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.FISH_RESPIRATION_BOOK_COUNT), 1);
                    }
                }
            }
        }

        // 关键：必须调用原始的 spawnEntity 方法，否则钓上来的鱼不会生成在世界上！
        return world.spawnEntity(entity);
    }
}