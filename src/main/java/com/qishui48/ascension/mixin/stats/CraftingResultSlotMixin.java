package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public class CraftingResultSlotMixin extends Slot {

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onCrafted(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 检查是否是药水箭
            if (stack.getItem() == Items.TIPPED_ARROW) {
                // 检查药水类型是否为水肺
                if (PotionUtil.getPotion(stack) == Potions.WATER_BREATHING ||
                        PotionUtil.getPotion(stack) == Potions.LONG_WATER_BREATHING) {

                    // 增加统计 (注意 stack.getCount()，一次可能做 8 根)
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.CRAFT_WATER_BREATHING_ARROW), stack.getCount());
                }
            }
        }
    }
    @Shadow
    private PlayerEntity player;

    public CraftingResultSlotMixin(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void checkCraftingCondition(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getWorld().isClient) {
            // 检查物品是否为时钟
            if (stack.isOf(Items.CLOCK)) {
                // 检查维度是否为末地
                if (player.getWorld().getRegistryKey() == World.END) {
                    // 增加统计数据
                    player.increaseStat(Ascension.CRAFT_CLOCK_END, 1);
                }
            }
        }
    }
}