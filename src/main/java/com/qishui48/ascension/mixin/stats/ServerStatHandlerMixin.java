package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.skill.UnlockCriterion;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatHandler.class)
public class ServerStatHandlerMixin {

    @Inject(method = "setStat", at = @At("HEAD"))
    private void onSetStat(PlayerEntity player, Stat<?> stat, int value, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        // 获取旧值 (Mixin 在 HEAD，此时值还没变)
        int oldValue = ((ServerStatHandler) (Object) this).getStat(stat);
        int newValue = value;

        // 遍历所有技能，检查是否有条件被达成
        // (性能提示：如果技能很多，建议建立 Stat -> List<Skill> 的缓存映射)
        for (Skill skill : SkillRegistry.getAll()) {
            // 获取玩家当前该技能等级
            int currentLevel = PacketUtils.getSkillLevel(serverPlayer, skill.id);
            // 我们关心的目标等级是下一级
            int targetLevel = currentLevel + 1;

            // 如果已经满级，就不检查了
            if (targetLevel > skill.maxLevel) continue;

            for (UnlockCriterion criterion : skill.getCriteria(targetLevel)) {
                // 如果这个条件关心的统计数据就是当前变化的 stat
                if (criterion.getStat().equals(stat)) {
                    int threshold = criterion.getThreshold();

                    // 核心判定：旧值不达标，新值达标 -> 刚刚完成！
                    if (oldValue < threshold && newValue >= threshold) {

                        // 发送提示
                        Text msg = Text.translatable("notification.ascension.header.criteria").formatted(Formatting.AQUA)
                                .append(" ")
                                .append(Text.literal("✔ ").formatted(Formatting.GREEN))
                                .append(criterion.getDescription()) // "击杀 10 只僵尸"
                                .append(" ")
                                .append(Text.translatable("notification.ascension.for_skill", skill.getName()).formatted(Formatting.GRAY));

                        PacketUtils.sendNotification(serverPlayer, msg);
                        // 播放一个清脆的提示音
                        serverPlayer.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 2.0f);
                    }
                }
            }
        }
    }
}