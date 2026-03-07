package com.qishui48.ascension.util;

import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

public class SkillHungerManager {

    public static void tick(ServerPlayerEntity player) {
        // 创造模式和旁观模式不生效
        if (player.isCreative() || player.isSpectator()) return;

        if (player.age % 100 == 0) {
            int coolingSkills = countCoolingSkills(player);
            if (coolingSkills > 0) {
                // 每 100 tick，每个冷却中的技能增加 2.0 消耗度 (对应 1/4 个鸡腿)
                float exhaustion = coolingSkills * 2.0f;
                player.getHungerManager().addExhaustion(exhaustion);
            }
        }
    }

    private static int countCoolingSkills(ServerPlayerEntity player) {
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        if (!nbt.contains("active_skill_slots", 9)) return 0;

        int count = 0;
        long now = player.getWorld().getTime();
        NbtList slots = nbt.getList("active_skill_slots", 10);

        for (int i = 0; i < slots.size(); i++) {
            NbtCompound slot = slots.getCompound(i);
            long cdEnd = slot.getLong("cooldown_end");
            long scdEnd = slot.getLong("secondary_cooldown_end");

            // 只要主要或次要冷却有任意一个没结束，就算作在冷却中
            if (now < cdEnd || now < scdEnd) {
                count++;
            }
        }
        return count;
    }
}