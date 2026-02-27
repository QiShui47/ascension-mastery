package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slot.class)
public abstract class SmithingSlotMixin {

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTakeSmithingResult(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        Slot slot = (Slot) (Object) this;
        if (!player.getWorld().isClient && stack.isOf(Items.NETHERITE_SWORD)) {
            // 确保不是从玩家自己的背包格子中拿起的，而是从工作台/锻造台的输出槽
            if (!(slot.inventory instanceof net.minecraft.entity.player.PlayerInventory)) {
                player.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.SMITH_NETHERITE_SWORD), 1);
            }
        }
    }
}