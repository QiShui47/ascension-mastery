package com.qishui48.ascension.client;

import com.qishui48.ascension.util.IEntityDataSaver;

public class SkillClient {
    // === 辅助方法：客户端获取技能等级 ===
    // 返回 int 等级，0 表示未解锁
    private int getSkillLevel(String skillId) {
        // 1. 获取当前混入的玩家对象 (ClientPlayerEntity)
        net.minecraft.client.network.ClientPlayerEntity player = (net.minecraft.client.network.ClientPlayerEntity) (Object) this;

        // 2. 强转接口获取 NBT
        IEntityDataSaver data = (IEntityDataSaver) player;
        net.minecraft.nbt.NbtCompound nbt = data.getPersistentData();

        // 3. 读取 skill_levels
        if (nbt.contains("skill_levels")) {
            return nbt.getCompound("skill_levels").getInt(skillId);
        }

        return 0;
    }
}
