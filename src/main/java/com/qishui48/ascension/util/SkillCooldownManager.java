package com.qishui48.ascension.util;

import com.qishui48.ascension.skill.ActiveSkill;
import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

public class SkillCooldownManager {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickPlayer(player);
            }
        });
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        // 每秒运行一次逻辑即可 (20 ticks)，节省性能
        if (player.age % 5 != 0) return;

        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        if (!nbt.contains("active_skill_slots", 9)) return;

        NbtList activeSlots = nbt.getList("active_skill_slots", 10);
        boolean dirty = false;
        long now = player.getWorld().getTime();

        for (int i = 0; i < activeSlots.size(); i++) {
            NbtCompound slotNbt = activeSlots.getCompound(i);
            String skillId = slotNbt.getString("id");
            if (skillId.isEmpty()) continue;

            Skill rawSkill = SkillRegistry.get(skillId);
            if (!(rawSkill instanceof ActiveSkill activeSkill)) continue;

            int currentCharges = slotNbt.getInt("charges");
            int level = PacketUtils.getSkillLevel(player, skillId);
            int maxCharges = activeSkill.getMaxCharges(level);
            long cooldownEnd = slotNbt.getLong("cooldown_end");

            // 恢复充能逻辑
            // 只有当充能不满时才检查恢复
            if (currentCharges < maxCharges) {
                // 如果当前没有在冷却 (cooldown_end == 0)，或者冷却时间已到
                if (cooldownEnd <= now) {
                    // 1. 恢复 1 点充能
                    currentCharges++;
                    slotNbt.putInt("charges", currentCharges);
                    dirty = true;

                    // 2. 检查是否还有剩余的空位需要继续冷却
                    if (currentCharges < maxCharges) {
                        int nextCooldown = 0;

                        // 2a. 检查冷却队列 (优先使用队列中的特定冷却时间)
                        if (slotNbt.contains("cooldown_queue", NbtElement.LIST_TYPE)) {
                            NbtList queue = slotNbt.getList("cooldown_queue", NbtElement.INT_TYPE);
                            if (!queue.isEmpty()) {
                                nextCooldown = queue.getInt(0); // 取出队首
                                queue.remove(0); // 移除
                                if (queue.isEmpty()) {
                                    slotNbt.remove("cooldown_queue"); // 空了就删掉key
                                }
                            }
                        }

                        // 2b. 如果队列没有，则使用默认的主冷却时间 (保底逻辑)
                        if (nextCooldown <= 0) {
                            nextCooldown = activeSkill.getPrimaryCooldown(level);
                        }

                        // 3. 开始下一轮冷却
                        slotNbt.putLong("cooldown_end", now + nextCooldown);
                        slotNbt.putInt("cooldown_total", nextCooldown);
                    } else {
                        // 满了，停止冷却
                        slotNbt.putLong("cooldown_end", 0);
                        slotNbt.putInt("cooldown_total", 0);
                        // 也可以顺手清空残留的队列 (理论上不该有残留)
                        slotNbt.remove("cooldown_queue");
                    }
                }
            }
        }

        if (dirty) {
            nbt.put("active_skill_slots", activeSlots);
            // 这里为了减少发包，可以做一个 debounce，或者仅在改变时发包
            PacketUtils.syncSkillData(player);
        }
    }
}