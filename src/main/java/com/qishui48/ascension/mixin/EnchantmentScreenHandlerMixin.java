package com.qishui48.ascension.mixin;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentScreenHandler.class)
public class EnchantmentScreenHandlerMixin {

    @Shadow @Final private int[] enchantmentPower; // 附魔等级数组
    @Shadow @Final private ScreenHandlerContext context;

    @Inject(method = "onButtonClick", at = @At("HEAD"))
    private void onEnchant(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // id 是按钮索引 (0, 1, 2)
            // 检查该选项需要的等级是否 >= 30
            // 注意：enchantmentPower[id] 存的是需求等级
            if (id >= 0 && id < 3 && this.enchantmentPower[id] >= 30) {
                // 这里只是点击按钮，后续原版逻辑会检查经验够不够。
                // 为了严谨，我们应该在扣除经验后触发，但 onButtonClick 返回 true 就会执行后续逻辑。
                // 只要玩家敢点 30 级附魔（并且点成功了），就算达成。
                // 简单的判定：如果玩家经验等级 >= 30 (或创造模式)，且点了这个按钮。
                if (player.experienceLevel >= 30 || player.isCreative()) {
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.ENCHANT_WITH_LEVEL_30), 1);
                }
            }
        }
    }
}