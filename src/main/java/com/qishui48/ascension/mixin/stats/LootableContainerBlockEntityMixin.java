package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootableContainerBlockEntity.class)
public abstract class LootableContainerBlockEntityMixin {

    @Shadow protected Identifier lootTableId;

    @Inject(method = "checkLootInteraction", at = @At("HEAD"))
    private void onOpenLootContainer(PlayerEntity player, CallbackInfo ci) {
        // 1. 只有在有 LootTable 且是服务端玩家打开时才触发
        if (this.lootTableId != null && player instanceof ServerPlayerEntity serverPlayer) {
            String lootId = this.lootTableId.toString();

            // 2. 读取数据
            IEntityDataSaver dataSaver = (IEntityDataSaver) serverPlayer;
            NbtCompound nbt = dataSaver.getPersistentData();

            NbtList lootedList;
            if (nbt.contains("looted_structures", NbtElement.LIST_TYPE)) {
                lootedList = nbt.getList("looted_structures", NbtElement.STRING_TYPE);
            } else {
                lootedList = new NbtList();
            }

            // 3. 查重 (同种 LootTable 只给一次)
            for (NbtElement element : lootedList) {
                if (element.asString().equals(lootId)) return;
            }

            // 4. === 新的遗迹类型！===
            lootedList.add(NbtString.of(lootId));
            nbt.put("looted_structures", lootedList);

            // 分级奖励
            int points = 5; // 默认
            String typeKey = "type.ascension.structure.common"; // 默认翻译键

            // 根据 lootId 字符串包含的关键词来判断
            if (lootId.contains("stronghold")) {
                points = 15;
                typeKey = "type.ascension.structure.stronghold";
            } else if (lootId.contains("ancient_city")) {
                points = 20;
                typeKey = "type.ascension.structure.ancient_city";
            } else if (lootId.contains("end_city")) {
                points = 20;
                typeKey = "type.ascension.structure.end_city";
            } else if (lootId.contains("fortress")) { // nether_bridge usually refers to fortress chests
                points = 15;
                typeKey = "type.ascension.structure.fortress";
            } else if (lootId.contains("bastion")) {
                points = 15;
                typeKey = "type.ascension.structure.bastion";
            } else if (lootId.contains("temple") || lootId.contains("pyramid") || lootId.contains("outpost")) {
                points = 10;
                typeKey = "type.ascension.structure.temple";
            } else if (lootId.contains("mineshaft")) {
                points = 5;
                typeKey = "type.ascension.structure.mineshaft";
            } else if (lootId.contains("dungeon") || lootId.contains("monster_room")) {
                points = 5;
                typeKey = "type.ascension.structure.dungeon";
            }

            // 加分
            int currentPoints = nbt.getInt("skill_points");
            nbt.putInt("skill_points", currentPoints + points);
            PacketUtils.syncSkillData(serverPlayer);

            // 通知
            Text msg = Text.translatable("notification.ascension.header.structure").formatted(Formatting.GOLD)
                    .append(" ")
                    .append(Text.translatable("notification.ascension.verb.explore").formatted(Formatting.WHITE))
                    .append(" ")
                    .append(Text.translatable(typeKey).formatted(Formatting.AQUA))
                    .append(" ")
                    .append(Text.translatable("notification.ascension.suffix.points", points).formatted(Formatting.BOLD, Formatting.GREEN));

            PacketUtils.sendNotification(serverPlayer, msg);

            serverPlayer.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.5f, 0.8f);
        }
    }
}