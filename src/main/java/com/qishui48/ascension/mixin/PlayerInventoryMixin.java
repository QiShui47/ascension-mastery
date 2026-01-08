package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow @Final public PlayerEntity player;

    // 监听：自动塞入 (捡起掉落物、Shift点击)
    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void onInsertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        checkAndAward(stack);
    }

    // 监听：指定槽位塞入 (Shift点击的部分逻辑)
    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void onInsertStackSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        checkAndAward(stack);
    }

    // === 新增：监听手动放置 (鼠标拖拽放置) ===
    @Inject(method = "setStack", at = @At("HEAD"))
    private void onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
        checkAndAward(stack);
    }

    // 统一处理逻辑
    @Unique
    private void checkAndAward(ItemStack stack) {
        // 1. 基本检查
        if (stack.isEmpty()) return;
        if (player.getWorld().isClient) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        // 2. 获取物品 ID
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();

        // 3. 读取数据
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();

        NbtList collectedList;
        if (nbt.contains("collected_items", NbtElement.LIST_TYPE)) {
            collectedList = nbt.getList("collected_items", NbtElement.STRING_TYPE);
        } else {
            collectedList = new NbtList();
        }

        // 4. 查重
        for (NbtElement element : collectedList) {
            if (element.asString().equals(itemId)) {
                return; // 已收集过
            }
        }

        // 5. === 是新物品！===

        // A. 记录
        collectedList.add(NbtString.of(itemId));
        nbt.put("collected_items", collectedList);

        // B. === 稀有度奖励计算 ===
        Rarity rarity = stack.getRarity();
        int pointsAwarded = 1;
        Formatting color = Formatting.WHITE;
        String rarityName = "";

        switch (rarity) {
            case COMMON:
                pointsAwarded = 1;
                color = Formatting.WHITE;
                break;
            case UNCOMMON: // 黄色物品 (如附魔瓶)
                pointsAwarded = 2;
                color = Formatting.YELLOW;
                rarityName = " [罕见]";
                break;
            case RARE: // 青色物品 (如信标)
                pointsAwarded = 3;
                color = Formatting.AQUA;
                rarityName = " [珍贵]";
                break;
            case EPIC: // 紫色物品 (如神级物品)
                pointsAwarded =4;
                color = Formatting.LIGHT_PURPLE;
                rarityName = " [史诗]";
                break;
        }

        int currentPoints = nbt.getInt("my_global_skills");
        nbt.putInt("my_global_skills", currentPoints + pointsAwarded);

        // C. 同步与反馈
        PacketUtils.syncSkillData(serverPlayer);

        // 音效：越稀有声音越响
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 0.7f + (pointsAwarded * 0.2f));

        // 文字提示
        Text msg = Text.literal("§6[新发现] ")
                .append(Text.literal(stack.getItem().getName().getString()).formatted(color))
                .append(Text.literal(rarityName).formatted(color))
                .append(Text.literal(" §a+" + pointsAwarded + " 技能点").formatted(Formatting.BOLD));
        PacketUtils.sendNotification(serverPlayer, msg);
    }
}