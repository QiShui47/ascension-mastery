package com.qishui48.ascension.network;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillActionHandler;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModMessages {
    public static final Identifier UNLOCK_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_unlock_skill");
    public static final Identifier JUMP_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_rocket_boost");
    public static final Identifier CHARGED_JUMP_ID = new Identifier(Ascension.MOD_ID, "request_charged_jump");
    public static final Identifier SYNC_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_sync");
    public static final Identifier TOGGLE_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_toggle_skill");
    public static final Identifier SHOW_NOTIFICATION_ID = new Identifier(Ascension.MOD_ID, "show_notification");

    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UNLOCK_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            String skillId = buf.readString();

            server.execute(() -> {
                Skill skill = SkillRegistry.get(skillId);
                if (skill == null) return;

                int currentLevel = PacketUtils.getSkillLevel(player, skillId);

                // 检查满级
                if (currentLevel >= skill.maxLevel) {
                    player.sendMessage(Text.literal("§c该技能已达最高等级！"), true);
                    return;
                }

                // 检查前置
                if (currentLevel == 0 && skill.parentId != null && !PacketUtils.isSkillUnlocked(player, skill.parentId)) {
                    Skill parent = SkillRegistry.get(skill.parentId);
                    player.sendMessage(Text.literal("§c前置未解锁！你需要先学习: ").append(parent.getName()), true);
                    return;
                }

                // === 修复：检查当前等级对应的升级条件 ===
                // 0 -> 1级: 检查 level 1 的条件
                // 1 -> 2级: 检查 level 2 的条件
                if (!skill.checkCriteria(player, currentLevel + 1)) {
                    player.sendMessage(Text.literal("§c未满足解锁/升级条件！"), true);
                    return;
                }

                // 计算消耗 (已修复：直接使用 Skill 里的配置，不再额外乘倍率)
                int actualCost = skill.getCost(currentLevel + 1);

                IEntityDataSaver dataSaver = (IEntityDataSaver) player;
                int currentPoints = dataSaver.getPersistentData().getInt("my_global_skills");

                if (currentPoints < actualCost) {
                    player.sendMessage(Text.of("§c技能点不足！需要: " + actualCost + " 点"), true);
                    return;
                }

                // 执行交易
                dataSaver.getPersistentData().putInt("my_global_skills", currentPoints - actualCost);
                PacketUtils.unlockSkill(player, skillId);
                SkillEffectHandler.refreshAttributes(player);
                SkillEffectHandler.onSkillUnlocked(player, skillId);

                player.sendMessage(Text.literal("§a成功升级: ").append(skill.getName())
                        .append(Text.literal(" (Lv." + (currentLevel + 1) + ")")), true);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JUMP_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (PacketUtils.isSkillUnlocked(player, "rocket_boost")) {
                    SkillActionHandler.executeBoost(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CHARGED_JUMP_ID, (server, player, handler, buf, responseSender) -> {
            float powerRatio = buf.readFloat();
            server.execute(() -> {
                if (PacketUtils.isSkillUnlocked(player, "charged_jump"))
                    SkillActionHandler.executeChargedJump(player, powerRatio);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SYNC_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                PacketUtils.syncSkillData(player);
            });
        });

        // 新增：处理中键切换
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            String skillId = buf.readString();
            server.execute(() -> {
                // 只有已解锁的技能才能切换
                if (PacketUtils.isSkillUnlocked(player, skillId)) {
                    PacketUtils.toggleSkill(player, skillId);
                }
            });
        });
    }
}