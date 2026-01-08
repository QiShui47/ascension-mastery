package com.qishui48.ascension.mixin;

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
            String typeName = "遗迹";

            if (lootId.contains("stronghold") || lootId.contains("temple")) {
                points = 7;
                typeName = "神殿/要塞";
            } else if (lootId.contains("end_city") || lootId.contains("ancient_city")) {
                points = 10;
                typeName = "史诗遗迹";
            }

            // 加分
            int currentPoints = nbt.getInt("my_global_skills");
            nbt.putInt("my_global_skills", currentPoints + points);
            PacketUtils.syncSkillData(serverPlayer);

            // 通知
            Text msg = Text.literal("§6[考古学家] §f探索 ")
                    .append(Text.literal(typeName).formatted(Formatting.AQUA))
                    .append(Text.literal(" §a+" + points + " 技能点").formatted(Formatting.BOLD));
            PacketUtils.sendNotification(serverPlayer, msg);

            serverPlayer.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.5f, 0.8f);
        }
    }
}