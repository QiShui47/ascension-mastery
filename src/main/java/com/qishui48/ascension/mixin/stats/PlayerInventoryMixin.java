package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
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

        // 森林主宰：追踪原木种类
        // 检查物品是否属于原木标签 (minecraft:logs)
        if (stack.isIn(ItemTags.LOGS)) {
            NbtList collectedLogs;
            if (nbt.contains("collected_log_types", NbtElement.LIST_TYPE)) {
                collectedLogs = nbt.getList("collected_log_types", NbtElement.STRING_TYPE);
            } else {
                collectedLogs = new NbtList();
            }

            // 查重：如果我们还没记录过这种原木
            if (!containsString(collectedLogs, itemId)) {
                collectedLogs.add(NbtString.of(itemId));
                nbt.put("collected_log_types", collectedLogs);

                // 增加统计数据 (触发技能解锁判断)
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.COLLECT_LOG_VARIANTS), 1);
            }
        }
        // 淘金：追踪染色玻璃种类
        // 检查是否是染色玻璃 (tag: c:glass_blocks 或者直接判断 instanceof StainedGlassBlock)
        // 简单判断：Item 名字包含 "stained_glass" 且不包含 "pane" (板)
        // 或者更严谨：Registry检查
        if (stack.getItem() instanceof net.minecraft.item.BlockItem blockItem &&
                blockItem.getBlock() instanceof net.minecraft.block.StainedGlassBlock) {

            NbtList collectedGlass;
            if (nbt.contains("collected_stained_glass_types", NbtElement.LIST_TYPE)) {
                collectedGlass = nbt.getList("collected_stained_glass_types", NbtElement.STRING_TYPE);
            } else {
                collectedGlass = new NbtList();
            }

            if (!containsString(collectedGlass, itemId)) {
                collectedGlass.add(NbtString.of(itemId));
                nbt.put("collected_stained_glass_types", collectedGlass);

                // 更新统计数据：设为当前收集的总数量
                int count = collectedGlass.size();
                serverPlayer.getStatHandler().setStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.COLLECT_STAINED_GLASS), count);
            }
        }
        // === 糖分主理人：蜂蜜瓶收集 ===
        if (stack.getItem() == Items.HONEY_BOTTLE) {
            // 直接增加统计数据，每获得一个就加 1
            // 这里 stack.getCount() 可能是一组，我们要全部加上
            serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.COLLECT_HONEY), stack.getCount());
        }

        // 5. === 是新物品！===

        // A. 记录
        collectedList.add(NbtString.of(itemId));
        nbt.put("collected_items", collectedList);

        // B. === 稀有度奖励计算 ===
        Rarity rarity = stack.getRarity();
        int pointsAwarded = 1;
        Formatting color = Formatting.WHITE;
        // 动态生成翻译键：rarity.ascension.common / rare / epic ...
        String rarityKey = "rarity.ascension." + rarity.name().toLowerCase();

        switch (rarity) {
            case COMMON:
                pointsAwarded = 1;
                color = Formatting.WHITE;
                break;
            case UNCOMMON: // 黄色物品 (如附魔瓶)
                pointsAwarded = 2;
                color = Formatting.YELLOW;
                break;
            case RARE: // 青色物品 (如信标)
                pointsAwarded = 3;
                color = Formatting.AQUA;
                break;
            case EPIC: // 紫色物品 (如神级物品)
                pointsAwarded =4;
                color = Formatting.LIGHT_PURPLE;
                break;
        }

        int currentPoints = nbt.getInt("skill_points");
        nbt.putInt("skill_points", currentPoints + pointsAwarded);

        // C. 同步与反馈
        PacketUtils.syncSkillData(serverPlayer);

        // 音效：越稀有声音越响
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 0.7f + (pointsAwarded * 0.2f));

        // 文字提示
        // [修改] 构建消息：括号现在在翻译键里
        Text msg = Text.translatable("notification.ascension.header.discovery").formatted(Formatting.GOLD)
                .append(" ")
                .append(Text.literal(stack.getItem().getName().getString()).formatted(color))
                .append(" ")
                .append(Text.translatable(rarityKey).formatted(color)) // 使用翻译后的稀有度
                .append(" ")
                .append(Text.translatable("notification.ascension.suffix.points", pointsAwarded).formatted(Formatting.BOLD, Formatting.GREEN));
        PacketUtils.sendNotification(serverPlayer, msg);
    }
    // 辅助方法：检查 List 是否包含字符串
    @Unique
    private boolean containsString(NbtList list, String str) {
        for (NbtElement e : list) {
            if (e.asString().equals(str)) return true;
        }
        return false;
    }
}