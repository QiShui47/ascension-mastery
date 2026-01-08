package com.qishui48.ascension.util;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.skill.UnlockCriterion;
import com.qishui48.ascension.network.ModMessages;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;

import java.util.List;

public class PacketUtils {

    public static void syncSkillData(ServerPlayerEntity player) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtCompound syncNbt = new NbtCompound();

        if (nbt.contains("my_global_skills")) syncNbt.put("my_global_skills", nbt.get("my_global_skills"));
        if (nbt.contains("skill_levels")) syncNbt.put("skill_levels", nbt.get("skill_levels"));
        if (nbt.contains("revealed_skills")) syncNbt.put("revealed_skills", nbt.get("revealed_skills"));
        if (nbt.contains("disabled_skills")) syncNbt.put("disabled_skills", nbt.get("disabled_skills"));

        // === 服务端预计算条件状态与进度 ===
        NbtCompound criteriaCache = new NbtCompound();
        NbtCompound criteriaProgress = new NbtCompound(); // 新增：进度数据

        for (Skill skill : SkillRegistry.getAll()) {
            int currentLevel = getSkillLevel(player, skill.id);
            int targetLevel = currentLevel + 1;

            List<UnlockCriterion> criteriaList = skill.getCriteria(targetLevel);
            if (!criteriaList.isEmpty()) {
                // 1. 是否达成
                boolean met = skill.checkCriteria(player, targetLevel);
                criteriaCache.putBoolean(skill.id, met);

                // 2. 具体进度 (存为 int 数组: [进度1, 进度2...])
                int[] progresses = new int[criteriaList.size()];
                for (int i = 0; i < criteriaList.size(); i++) {
                    progresses[i] = criteriaList.get(i).getProgress(player);
                }
                criteriaProgress.putIntArray(skill.id, progresses);
            }
        }
        syncNbt.put("criteria_cache", criteriaCache);
        syncNbt.put("criteria_progress", criteriaProgress); // 发送进度

        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeNbt(syncNbt);
        ServerPlayNetworking.send(player, Ascension.PACKET_ID, buffer);
    }

    // 省略中间未变动的方法，请保留原有的这些方法
    public static int getSkillLevel(ServerPlayerEntity player, String skillId) {
        NbtCompound nbt = ((IEntityDataSaver) player).getPersistentData();
        if (nbt.contains("skill_levels")) {
            return nbt.getCompound("skill_levels").getInt(skillId);
        }
        return 0;
    }

    public static boolean isSkillUnlocked(ServerPlayerEntity player, String skillId) {
        return getSkillLevel(player, skillId) > 0;
    }

    public static void unlockSkill(ServerPlayerEntity player, String skillId) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtCompound levelList;
        if (nbt.contains("skill_levels")) {
            levelList = nbt.getCompound("skill_levels");
        } else {
            levelList = new NbtCompound();
        }
        int currentLevel = levelList.getInt(skillId);
        levelList.putInt(skillId, currentLevel + 1);
        nbt.put("skill_levels", levelList);
        syncSkillData(player);
    }

    public static void setSkillLevel(ServerPlayerEntity player, String skillId, int level) {
        // ... 保持原有逻辑 ...
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtCompound levelList;
        if (nbt.contains("skill_levels")) {
            levelList = nbt.getCompound("skill_levels");
        } else {
            levelList = new NbtCompound();
        }
        if (level <= 0) {
            levelList.remove(skillId);
        } else {
            levelList.putInt(skillId, level);
        }
        nbt.put("skill_levels", levelList);
        syncSkillData(player);
    }

    public static void hideSkill(ServerPlayerEntity player, String skillId) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        if (nbt.contains("revealed_skills")) {
            nbt.getCompound("revealed_skills").remove(skillId);
            syncSkillData(player);
        }
    }
    // === Reveal 指令支持 ===
    public static void revealHiddenSkill(ServerPlayerEntity player, String skillId) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtCompound revealedList;

        if (nbt.contains("revealed_skills")) {
            revealedList = nbt.getCompound("revealed_skills");
        } else {
            revealedList = new NbtCompound();
        }

        if (revealedList.getBoolean(skillId)) return;

        revealedList.putBoolean(skillId, true);
        nbt.put("revealed_skills", revealedList);

        player.sendMessage(Text.of("§6§l[奇迹] §f你发现了隐藏技能: §e" + SkillRegistry.get(skillId).getName().getString()), true);
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);

        syncSkillData(player);
    }
    // === 新增：切换技能启用状态 ===
    public static void toggleSkill(ServerPlayerEntity player, String skillId) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtCompound disabledSkills;

        if (nbt.contains("disabled_skills")) {
            disabledSkills = nbt.getCompound("disabled_skills");
        } else {
            disabledSkills = new NbtCompound();
        }

        // 切换逻辑：如果有就删(启用)，没有就加(停用)
        if (disabledSkills.contains(skillId)) {
            disabledSkills.remove(skillId);
            player.sendMessage(Text.of("§a[技能] 已启用: " + SkillRegistry.get(skillId).getName().getString()), true);
        } else {
            disabledSkills.putBoolean(skillId, true);
            player.sendMessage(Text.of("§c[技能] 已停用: " + SkillRegistry.get(skillId).getName().getString()), true);
        }

        nbt.put("disabled_skills", disabledSkills);

        // 立即刷新属性（因为如果是属性类技能，需要立刻生效）
        SkillEffectHandler.refreshAttributes(player);
        syncSkillData(player);
    }

    // === 新增：判断技能是否激活 (核心判断方法) ===
    // 所有的 Mixin 和 EffectHandler 以后都要用这个方法！
    // 逻辑：已解锁 AND 未停用
    public static boolean isSkillActive(ServerPlayerEntity player, String skillId) {
        if (!isSkillUnlocked(player, skillId)) return false;

        NbtCompound nbt = ((IEntityDataSaver) player).getPersistentData();
        if (nbt.contains("disabled_skills") && nbt.getCompound("disabled_skills").getBoolean(skillId)) {
            return false; // 虽然解锁了，但是被玩家手动停用了
        }
        return true;
    }

    // 发送自定义通知
    public static void sendNotification(ServerPlayerEntity player, Text message) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(Text.Serializer.toJson(message));
        ServerPlayNetworking.send(player, ModMessages.SHOW_NOTIFICATION_ID, buf);
    }
}