package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 目标是酿造台 GUI 的药水槽位内部类
@Mixin(targets = "net.minecraft.screen.BrewingStandScreenHandler$PotionSlot")
public class BrewingStandSlotMixin {

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTakePotion(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // 检查是否是抗火药水
            // 注意：抗火药水包括普通(3:00)和延长版(8:00)，PotionUtil.getPotion 会识别基础类型
            if (PotionUtil.getPotion(stack) == Potions.FIRE_RESISTANCE ||
                    PotionUtil.getPotion(stack) == Potions.LONG_FIRE_RESISTANCE) {

                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.BREW_FIRE_RES_POTION), 1);
            }
            // === 缸中之脑：水肺药水 ===
            if (PotionUtil.getPotion(stack) == Potions.WATER_BREATHING ||
                    PotionUtil.getPotion(stack) == Potions.LONG_WATER_BREATHING) {
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.BREW_WATER_BREATHING), 1);
            }
        }
    }
}