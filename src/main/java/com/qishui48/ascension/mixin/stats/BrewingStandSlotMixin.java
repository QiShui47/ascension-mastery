package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.screen.slot.Slot;
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
        // 炼制特定药水检查
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
        // 炼制不同药水计数
        Slot slot = (Slot) (Object) this;
        // 检查这个 Slot 是否属于酿造台 (BrewingStandInventory)
        if (slot.inventory instanceof net.minecraft.inventory.Inventory &&
                slot.inventory.toString().contains("BrewingStand")) { // 简单的判断方式，或者 instanceof BrewingStandBlockEntity

            // 检查取出的物品是否是药水
            if (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) {
                Potion potion = PotionUtil.getPotion(stack);
                String potionId = net.minecraft.registry.Registries.POTION.getId(potion).toString();

                // 忽略水瓶和粗制药水
                if (potionId.equals("minecraft:water") || potionId.equals("minecraft:mundane") ||
                        potionId.equals("minecraft:thick") || potionId.equals("minecraft:awkward")) {
                    return;
                }

                // 检查玩家是否已经炼制过这种药
                IEntityDataSaver data = (IEntityDataSaver) player;
                NbtCompound nbt = data.getPersistentData();
                NbtList brewedList = nbt.getList("ascension_brewed_potions", NbtElement.STRING_TYPE);

                boolean alreadyBrewed = false;
                for (NbtElement e : brewedList) {
                    if (e.asString().equals(potionId)) {
                        alreadyBrewed = true;
                        break;
                    }
                }

                if (!alreadyBrewed) {
                    // 没炼制过：记录并增加统计
                    brewedList.add(NbtString.of(potionId));
                    nbt.put("ascension_brewed_potions", brewedList);

                    // 增加统计数据
                    player.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.BREW_POTION_TYPE_COUNT), 1);
                }
            }
        }
    }
}