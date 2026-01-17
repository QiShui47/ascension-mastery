package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {

    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void onAnvilUse(PlayerEntity player, ItemStack output, CallbackInfo ci) {
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 检查输出物是不是剑
            if (output.getItem() instanceof SwordItem) {
                // 读取 NBT
                var nbt = output.getOrCreateNbt();
                int count = nbt.getInt("AscensionAnvilCount");

                // 增加计数
                count++;
                nbt.putInt("AscensionAnvilCount", count);

                // 如果达到 3 次，触发统计
                if (count >= 3) {
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.ENCHANT_SWORD_THREE_TIMES), 1);
                }
            }
        }
    }
}