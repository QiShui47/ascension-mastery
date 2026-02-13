package com.qishui48.ascension.util;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.skill.*;
import com.qishui48.ascension.network.ModMessages;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Formatting;

import java.util.List;

public class PacketUtils {

    public static void syncSkillData(ServerPlayerEntity player) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtCompound syncNbt = new NbtCompound();

        if (nbt.contains("skill_points")) syncNbt.put("skill_points", nbt.get("skill_points"));
        if (nbt.contains("skill_levels")) syncNbt.put("skill_levels", nbt.get("skill_levels"));
        if (nbt.contains("revealed_skills")) syncNbt.put("revealed_skills", nbt.get("revealed_skills"));
        if (nbt.contains("disabled_skills")) syncNbt.put("disabled_skills", nbt.get("disabled_skills"));
        if (nbt.contains("reset_count")) syncNbt.putInt("reset_count", nbt.getInt("reset_count"));
        if (nbt.contains("last_reset_time")) syncNbt.putLong("last_reset_time", nbt.getLong("last_reset_time"));
        // 同步主动技能槽和施法材料
        if (nbt.contains("active_skill_slots")) syncNbt.put("active_skill_slots", nbt.get("active_skill_slots"));
        if (nbt.contains("casting_materials")) syncNbt.put("casting_materials", nbt.get("casting_materials"));
        if (nbt.contains("selected_active_slot")) syncNbt.putInt("selected_active_slot", nbt.getInt("selected_active_slot"));

        if (nbt.contains("invincible_damage_end")) syncNbt.putLong("invincible_damage_end", nbt.getLong("invincible_damage_end"));
        if (nbt.contains("invincible_damage_total")) syncNbt.putInt("invincible_damage_total", nbt.getInt("invincible_damage_total"));
        if (nbt.contains("invincible_status_end")) syncNbt.putLong("invincible_status_end", nbt.getLong("invincible_status_end"));
        if (nbt.contains("invincible_status_total")) syncNbt.putInt("invincible_status_total", nbt.getInt("invincible_status_total"));

        if (nbt.contains("radiant_damage_end")) {
            syncNbt.putLong("radiant_damage_end", nbt.getLong("radiant_damage_end"));
        }
        if (nbt.contains("radiant_light_end")) {
            syncNbt.putLong("radiant_light_end", nbt.getLong("radiant_light_end"));
        }

        if (nbt.contains("blink_recall_deadline")) syncNbt.putLong("blink_recall_deadline", nbt.getLong("blink_recall_deadline"));
        if (nbt.contains("blink_recall_x")) syncNbt.putDouble("blink_recall_x", nbt.getDouble("blink_recall_x"));
        if (nbt.contains("blink_recall_y")) syncNbt.putDouble("blink_recall_y", nbt.getDouble("blink_recall_y"));
        if (nbt.contains("blink_recall_z")) syncNbt.putDouble("blink_recall_z", nbt.getDouble("blink_recall_z"));
        if (nbt.contains("blink_recall_dim")) syncNbt.putString("blink_recall_dim", nbt.getString("blink_recall_dim"));
        // 岿然不动的时间键
        if (nbt.contains("steadfast_start_time")) syncNbt.putLong("steadfast_start_time", nbt.getLong("steadfast_start_time"));


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

    // === 判断技能是否激活 (核心判断方法) ===
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

    public static void setSkillCooldown(ServerPlayerEntity player, String skillId, int cooldownTicks) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();

        if (!nbt.contains("active_skill_slots", 9)) return;
        net.minecraft.nbt.NbtList activeSlots = nbt.getList("active_skill_slots", 10);

        boolean found = false;
        long currentTime = player.getWorld().getTime();
        long endTime = currentTime + cooldownTicks;

        for (int i = 0; i < activeSlots.size(); i++) {
            NbtCompound slotNbt = activeSlots.getCompound(i);
            if (slotNbt.getString("id").equals(skillId)) {
                // 存储结束时间戳
                slotNbt.putLong("cooldown_end", endTime);
                // 记录总时间，用于计算进度条
                slotNbt.putInt("cooldown_total", cooldownTicks);
                found = true;
                break;
            }
        }

        if (found) {
            nbt.put("active_skill_slots", activeSlots);
            syncSkillData(player);
        }
    }

    // 设置充能数
    public static void setSkillCharges(ServerPlayerEntity player, String skillId, int charges) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();

        if (!nbt.contains("active_skill_slots", 9)) return;
        net.minecraft.nbt.NbtList activeSlots = nbt.getList("active_skill_slots", 10);

        boolean found = false;
        for (int i = 0; i < activeSlots.size(); i++) {
            NbtCompound slotNbt = activeSlots.getCompound(i);
            if (slotNbt.getString("id").equals(skillId)) {
                slotNbt.putInt("charges", charges);
                found = true;
                break;
            }
        }
        if (found) {
            nbt.put("active_skill_slots", activeSlots);
            syncSkillData(player);
        }
    }

    // 发送自定义通知
    public static void sendNotification(ServerPlayerEntity player, Text message) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(Text.Serializer.toJson(message));
        ServerPlayNetworking.send(player, ModMessages.SHOW_NOTIFICATION_ID, buf);
    }

    // === 技能点平衡性检查 ===
    public static void checkSkillPointBalance(ServerPlayerEntity player) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();

        // 1. 计算当前所有已解锁技能的总价值 (Current Theoretical Cost)
        int currentTotalValue = 0;
        for (Skill skill : SkillRegistry.getAll()) {
            int level = getSkillLevel(player, skill.id);
            if (level > 0) {
                // 累加每一级的消耗
                for (int i = 1; i <= level; i++) {
                    currentTotalValue += skill.getCost(i);
                }
            }
        }
        // 2. 获取记录的“实际已花费点数”
        // 如果没有记录 (老存档)，则初始化为当前价值 (既往不咎)
        if (!nbt.contains("spent_skill_points")) {
            nbt.putInt("spent_skill_points", currentTotalValue);
            return; // 首次初始化，无需返还
        }
        int spentPoints = nbt.getInt("spent_skill_points");
        // 3. 比较
        if (spentPoints > currentTotalValue) {
            // 情况：贬值了，返还差价
            int refund = spentPoints - currentTotalValue;
            // 返还到可用点数
            int available = nbt.getInt("skill_points");
            nbt.putInt("skill_points", available + refund);
            // 更新已花费点数为当前价值
            nbt.putInt("spent_skill_points", currentTotalValue);
            // 提示玩家
            player.sendMessage(Text.translatable("message.ascension.refund_points", refund).formatted(Formatting.GOLD), false);
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
            // 同步
            syncSkillData(player);
        }
        // 情况：升值了 (spentPoints < currentTotalValue) -> 少不补，保留原 spentPoints
    }

    // === 解锁技能时记录花费 ===
    // 在 ModMessages.java 里调用 unlockSkill 时，也需要更新 spent_skill_points
    // 加一个 helper 方法给 ModMessages 用
    public static void recordSpending(ServerPlayerEntity player, int cost) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        int spent = nbt.getInt("spent_skill_points");
        nbt.putInt("spent_skill_points", spent + cost);
    }

    public static void resetSkills(ServerPlayerEntity player) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();

        // === 1. 冷却检查 (10天) ===
        long lastResetTime = nbt.contains("last_reset_time") ? nbt.getLong("last_reset_time") : 0;
        long currentTime = player.getWorld().getTime();
        long cooldownTicks = 240000L; // 10 天

        if (lastResetTime != 0 && (currentTime - lastResetTime) < cooldownTicks) {
            long daysLeft = (cooldownTicks - (currentTime - lastResetTime)) / 24000L;
            player.sendMessage(Text.translatable("message.ascension.reset_cooldown", daysLeft + 1).formatted(Formatting.RED), true);
            return;
        }

        // === 2. 费用计算 (基础 1395 + 递增) ===
        int resetCount = nbt.contains("reset_count") ? nbt.getInt("reset_count") : 0;
        int cost = 1395 * (1 + resetCount);

        // === 3. 经验检查与扣除 ===
        // 使用 totalExperience 来判断，确保准确
        if (player.totalExperience < cost) {
            player.sendMessage(Text.translatable("message.ascension.not_enough_xp", cost).formatted(Formatting.RED), true);
            return;
        }

        // 扣除经验 (直接增加负数经验值)
        player.addExperience(-cost);

        // === 4. 计算返还点数 (核心修复) ===
        int spent = 0;

        // 优先读取 spent_skill_points (这是最准确的记录)
        if (nbt.contains("spent_skill_points")) {
            spent = nbt.getInt("spent_skill_points");
        }
        // [保险逻辑] 如果是老存档，可能没有记录 spent_skill_points，此时我们手动计算一下当前技能的总价值
        else {
            for (Skill skill : SkillRegistry.getAll()) {
                int level = getSkillLevel(player, skill.id);
                for (int i = 1; i <= level; i++) {
                    spent += skill.getCost(i);
                }
            }
        }

        int currentPoints = nbt.contains("skill_points") ? nbt.getInt("skill_points") : 0;

        // 返还所有点数
        nbt.putInt("skill_points", currentPoints + spent);

        // [关键步骤] 必须将已花费记录清零！
        // 否则下次 checkSkillPointBalance 运行时，会误以为你有点数没被返还，再次给你加分。
        nbt.putInt("spent_skill_points", 0);

        // === 5. 清除技能与状态 ===
        nbt.remove("skill_levels");
        // 如果你想让玩家重新探索隐藏技能，可以取消注释下面这行：
        // nbt.remove("revealed_skills");

        // 移除所有手动停用记录
        nbt.remove("disabled_skills");

        // === 6. 更新记录 ===
        nbt.putInt("reset_count", resetCount + 1);
        nbt.putLong("last_reset_time", currentTime);

        // === 7. 刷新与同步 ===
        SkillEffectHandler.refreshAttributes(player); // 移除属性加成
        syncSkillData(player); // 刷新客户端 UI

        player.sendMessage(Text.translatable("message.ascension.reset_success", cost).formatted(Formatting.GREEN), true);
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // 通用方法：使用冷却遮罩来显示一个临时的倒计时/蓄力进度
    // 适用于不想使用下方耐久条，而是直接在图标上转圈圈的场景
    public static void startCooldownOverlayVisual(ServerPlayerEntity player, String skillId, int ticks) {
        // 其实底层还是设置冷却，但封装后语义更清晰
        setSkillCooldown(player, skillId, ticks);
    }

    // === 消耗技能充能并应用冷却队列 (使用默认冷却) ===
    public static void consumeSkillCharge(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        consumeSkillCharge(player, skill, isSecondary, -1);
    }

    // 核心方法：消耗技能充能并应用冷却队列
    public static void consumeSkillCharge(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary, int overrideCooldown) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        if (!nbt.contains("active_skill_slots", 9)) return;
        NbtList activeSlots = nbt.getList("active_skill_slots", 10);

        int level = getSkillLevel(player, skill.id);
        int cooldownTicks;
        if (overrideCooldown > 0) {
            cooldownTicks = overrideCooldown;
        } else {
            cooldownTicks = isSecondary ? skill.getSecondaryCooldown(level) : skill.getPrimaryCooldown(level);
        }

        boolean found = false;
        long currentTime = player.getWorld().getTime();

        for (int i = 0; i < activeSlots.size(); i++) {
            NbtCompound slotNbt = activeSlots.getCompound(i);
            if (slotNbt.getString("id").equals(skill.id)) {
                // 1. 扣除充能
                int currentCharges = slotNbt.getInt("charges");
                if (currentCharges > 0) {
                    currentCharges--;
                    slotNbt.putInt("charges", currentCharges);
                }

                // 2. 冷却逻辑
                long cooldownEnd = slotNbt.getLong("cooldown_end");

                // 如果当前已经在冷却中（cooldownEnd > currentTime），则将新的冷却时间加入队列
                if (cooldownEnd > currentTime) {
                    // 获取或创建队列
                    // NBT没有直接的IntList，我们用IntArray或List<Int>，这里用IntList方便追加
                    // 为了方便操作，我们用 nbt.getIntArray 的变体，但 NbtList 更灵活
                    NbtList queue;
                    if (slotNbt.contains("cooldown_queue", NbtElement.LIST_TYPE)) {
                        queue = slotNbt.getList("cooldown_queue", NbtElement.INT_TYPE);
                    } else {
                        queue = new NbtList();
                    }

                    // 将本次消耗对应的冷却时间加入队列
                    queue.add(net.minecraft.nbt.NbtInt.of(cooldownTicks));
                    slotNbt.put("cooldown_queue", queue);

                } else {
                    // 如果当前没有冷却，直接开始冷却
                    slotNbt.putLong("cooldown_end", currentTime + cooldownTicks);
                    slotNbt.putInt("cooldown_total", cooldownTicks);
                }

                found = true;
                break;
            }
        }

        if (found) {
            nbt.put("active_skill_slots", activeSlots);
            syncSkillData(player);
        }
    }

    /**
     * 获取存储在玩家身上的通用整型数据
     * @param player 玩家实体
     * @param key NBT键名 (例如 "zhu_rong_charges")
     * @return 存储的整数值，如果没有则返回 0
     */
    public static int getData(ServerPlayerEntity player, String key) {
        NbtCompound nbt = ((IEntityDataSaver) player).getPersistentData();
        return nbt.getInt(key);
    }

    /**
     * 设置存储在玩家身上的通用整型数据，并同步
     * @param player 玩家实体
     * @param key NBT键名
     * @param value 要设置的值
     */
    public static void setData(ServerPlayerEntity player, String key, int value) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();

        // 如果值为0，我们可以选择移除key以节省空间，或者存0
        // 这里为了简单直接存值
        nbt.putInt(key, value);

        // 任何数据变动后，都建议同步一次数据，防止客户端显示不同步
        // 如果数据仅服务端逻辑使用（如伤害计算），其实可以不立即同步
        // 但为了保险（比如以后要在UI显示充能层数），我们同步一下
        syncSkillData(player);
    }
    /**
     * 增加数据值 (快捷方法)
     * @param player 目标玩家
     * @param key 数据键名
     * @param amount 增加量
     */
    public static void addData(ServerPlayerEntity player, String key, int amount) {
        setData(player, key, getData(player, key) + amount);
    }
}