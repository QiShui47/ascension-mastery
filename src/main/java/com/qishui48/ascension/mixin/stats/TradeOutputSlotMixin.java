package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(TradeOutputSlot.class)
public class TradeOutputSlotMixin {

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTrade(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 检查是否是附魔书
            if (stack.getItem() == Items.ENCHANTED_BOOK) {
                // 获取书上的附魔
                Map<Enchantment, Integer> enchants = EnchantmentHelper.get(stack);

                IEntityDataSaver data = (IEntityDataSaver) serverPlayer;
                var nbt = data.getPersistentData();
                NbtList tradedBooks;
                if (nbt.contains("traded_enchanted_books", NbtElement.LIST_TYPE)) {
                    tradedBooks = nbt.getList("traded_enchanted_books", NbtElement.STRING_TYPE);
                } else {
                    tradedBooks = new NbtList();
                }

                // 遍历这本书上的所有附魔，只要有一个是新的，就算数
                boolean isNew = false;
                for (Enchantment e : enchants.keySet()) {
                    String enchId = Registries.ENCHANTMENT.getId(e).toString();

                    boolean found = false;
                    for (NbtElement el : tradedBooks) {
                        if (el.asString().equals(enchId)) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        tradedBooks.add(NbtString.of(enchId));
                        isNew = true;
                    }
                }

                if (isNew) {
                    nbt.put("traded_enchanted_books", tradedBooks);
                    // 更新统计数据
                    serverPlayer.getStatHandler().setStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRADE_DIFFERENT_ENCHANTED_BOOKS), tradedBooks.size());
                }
            }
        }
    }
}