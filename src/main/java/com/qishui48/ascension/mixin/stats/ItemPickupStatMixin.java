package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.entity.player.PlayerInventory.class)
public abstract class ItemPickupStatMixin {
    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void onObtainItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isOf(Items.SPECTRAL_ARROW)) {
            PlayerEntity player = ((net.minecraft.entity.player.PlayerInventory)(Object)this).player;
            if (!player.getWorld().isClient) {
                player.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.OBTAIN_SPECTRAL_ARROW), 1);
            }
        }
    }
}