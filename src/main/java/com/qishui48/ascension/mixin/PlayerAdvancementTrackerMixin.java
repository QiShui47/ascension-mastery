package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {

    @Shadow private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At("TAIL"))
    private void onGrantCriterion(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        // 1. 只有当 grant 成功返回 true (表示进度有更新) 时才检查
        if (cir.getReturnValue()) {
            // 2. 检查该成就现在是否已经全部完成
            if (this.owner.getAdvancementTracker().getProgress(advancement).isDone()) {

                // 3. 必须是有显示信息的成就 (排除配方解锁等隐藏成就)
                if (advancement.getDisplay() == null) return;

                // 4. NBT 查重 (防止重置成就后刷分，或者成就系统重复触发)
                String advId = advancement.getId().toString();
                IEntityDataSaver dataSaver = (IEntityDataSaver) owner;
                NbtCompound nbt = dataSaver.getPersistentData();

                // 使用 boolean map 存储已领奖的成就
                NbtCompound rewardedAdvs;
                if (nbt.contains("rewarded_advancements")) {
                    rewardedAdvs = nbt.getCompound("rewarded_advancements");
                } else {
                    rewardedAdvs = new NbtCompound();
                }

                if (rewardedAdvs.contains(advId)) return; // 领过了

                // === 5. 发奖！===
                rewardedAdvs.putBoolean(advId, true);
                nbt.put("rewarded_advancements", rewardedAdvs);

                // 根据类型分级
                AdvancementFrame frame = advancement.getDisplay().getFrame();
                int points = 0;
                String typeName = "";

                switch (frame) {
                    case TASK:      points = 5;  typeName = "进度达成"; break;
                    case GOAL:      points = 15; typeName = "目标达成"; break;
                    case CHALLENGE: points = 45; typeName = "挑战完成"; break;
                }

                int currentPoints = nbt.getInt("my_global_skills");
                nbt.putInt("my_global_skills", currentPoints + points);
                PacketUtils.syncSkillData(owner);

                Text msg = Text.literal("§e[" + typeName + "] §f")
                        .append(advancement.getDisplay().getTitle().copy().formatted(Formatting.YELLOW))
                        .append(Text.literal(" §a+" + points + " 技能点").formatted(Formatting.BOLD));
                PacketUtils.sendNotification(owner, msg);
            }
        }
    }
}