package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class ItemPickupStatMixin {

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void onObtainItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        PlayerEntity player = ((PlayerInventory)(Object)this).player;

        // 确保逻辑只在服务端执行，并且 player 是 ServerPlayerEntity 的实例
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 1. 原有的光灵箭统计逻辑
            if (stack.isOf(Items.SPECTRAL_ARROW)) {
                serverPlayer.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.OBTAIN_SPECTRAL_ARROW), 1);
            }

            // === 2. 新增：统计收集的不同物品种类 ===
            IEntityDataSaver dataSaver = (IEntityDataSaver) serverPlayer;
            NbtCompound nbt = dataSaver.getPersistentData();

            // 获取或初始化记录物品种类的哈希表
            NbtCompound collectedRegistry;
            if (nbt.contains("collected_items_registry")) {
                collectedRegistry = nbt.getCompound("collected_items_registry");
            } else {
                collectedRegistry = new NbtCompound();
            }

            // 获取物品的标准注册名 (例如 "minecraft:apple")
            // 注意: 如果你的游戏版本低于 1.19.3，请将 Registries.ITEM 改为 net.minecraft.util.registry.Registry.ITEM
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            // 如果这个物品是第一次被收集
            if (!collectedRegistry.contains(itemId)) {
                // 标记该物品已收集
                collectedRegistry.putBoolean(itemId, true);
                nbt.put("collected_items_registry", collectedRegistry);

                // 更新收集到的不同物品的总数
                int uniqueCount = collectedRegistry.getSize();
                nbt.putInt("collected_unique_items", uniqueCount);

                // 同步数据到客户端，刷新技能树 UI 的解锁条件进度
                PacketUtils.syncSkillData(serverPlayer);
            }
        }
    }
}