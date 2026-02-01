package com.qishui48.ascension.command;

import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.skill.UnlockCriterion;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SkillOpCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment environment) {

        // 新指令: /skillcondition <grant|revoke> <all|id>
        // 用于快速满足/重置技能的解锁条件 (通过修改统计数据实现)
        dispatcher.register(CommandManager.literal("skillcondition")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("grant")
                        .then(CommandManager.argument("skill_id", StringArgumentType.string())
                                .suggests((ctx, b) -> CommandSource.suggestMatching(SkillRegistry.getIds(), b))
                                .executes(context -> modifyCriteria(context, true, -1)) // -1 表示所有等级
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(1)) // 可选参数 level
                                        .executes(context -> modifyCriteria(context, true, IntegerArgumentType.getInteger(context, "level"))))))
                .then(CommandManager.literal("revoke")
                        .then(CommandManager.argument("skill_id", StringArgumentType.string())
                                .suggests((ctx, b) -> CommandSource.suggestMatching(SkillRegistry.getIds(), b))
                                .executes(context -> modifyCriteria(context, false, -1))
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(1))
                                        .executes(context -> modifyCriteria(context, false, IntegerArgumentType.getInteger(context, "level"))))))
                .then(CommandManager.literal("hide")//在UI中隐藏
                        .then(CommandManager.argument("skill_id", StringArgumentType.string())
                                .suggests((ctx, b) -> CommandSource.suggestMatching(SkillRegistry.getIds(), b))
                                .executes(ctx -> {
                                    PacketUtils.hideSkill(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "skill_id"));
                                    return 1;
                                })))
                .then(CommandManager.literal("reveal")//在UI中隐藏
                        .then(CommandManager.argument("skill_id", StringArgumentType.string())
                                .suggests((ctx, b) -> CommandSource.suggestMatching(SkillRegistry.getIds(), b))
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "skill_id");
                                    PacketUtils.revealHiddenSkill(context.getSource().getPlayerOrThrow(), id);
                                    return 1;
                                })))
        );
        // /skillsetlevel <id|all> <level>
        dispatcher.register(CommandManager.literal("skillsetlevel")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("skill_id", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            // 自动补全建议：所有技能ID + "all"
                            CommandSource.suggestMatching(SkillRegistry.getIds(), builder);
                            CommandSource.suggestMatching(new String[]{"all"}, builder);
                            return builder.buildFuture();
                        })
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 10))
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "skill_id");
                                    int level = IntegerArgumentType.getInteger(context, "level");
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

                                    if ("all".equals(id)) {
                                        // === 批量处理 ===
                                        for (String skillId : SkillRegistry.getIds()) {
                                            PacketUtils.setSkillLevel(player, skillId, level);
                                            // 注意：setSkillLevel 内部应该已经包含了 nbt.put
                                        }
                                        context.getSource().sendFeedback(() -> Text.translatable("command.ascension.set_level.all", level).formatted(Formatting.GREEN), false);
                                    } else {
                                        // === 单个处理 ===
                                        if (SkillRegistry.get(id) == null) {
                                            context.getSource().sendError(Text.translatable("command.ascension.error.unknown_skill", id));
                                            return 0;
                                        }
                                        PacketUtils.setSkillLevel(player, id, level);
                                        context.getSource().sendFeedback(() -> Text.translatable("command.ascension.set_level.single", id, level).formatted(Formatting.GREEN), false);
                                    }

                                    // === 核心修复：强制刷新 ===
                                    // 1. 发包同步给客户端 (修复 UI 不亮的问题)
                                    PacketUtils.syncSkillData(player);
                                    // 2. 刷新属性 (修复血量/攻速不变化的问题)
                                    SkillEffectHandler.refreshAttributes(player);

                                    return 1;
                                })
                        )
                ));

        // /setactiveskillcd <槽位> <ticks>
        dispatcher.register(CommandManager.literal("setactiveskillcd")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(0, 4))
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    int slot = IntegerArgumentType.getInteger(context, "slot");
                                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                    ServerPlayerEntity player = context.getSource().getPlayer();

                                    // 调用 PacketUtils 里的逻辑
                                    IEntityDataSaver data = (IEntityDataSaver) player;
                                    net.minecraft.nbt.NbtList activeSlots = data.getPersistentData().getList("active_skill_slots", 10);

                                    if (slot < activeSlots.size()) {
                                        net.minecraft.nbt.NbtCompound slotNbt = activeSlots.getCompound(slot);
                                        long now = player.getWorld().getTime();
                                        slotNbt.putLong("cooldown_end", now + ticks);

                                        if (slotNbt.getInt("cooldown_total") < ticks) {
                                            slotNbt.putInt("cooldown_total", ticks);
                                        }

                                        data.getPersistentData().put("active_skill_slots", activeSlots);
                                        com.qishui48.ascension.util.PacketUtils.syncSkillData(player);

                                        context.getSource().sendFeedback(() -> Text.literal("Cooldown set."), true);
                                    }
                                    PacketUtils.syncSkillData(player);
                                    return 1;
                                })
                        )
                )
        );
    }
    // 辅助方法
    private static int modifyCriteria(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, boolean grant, int targetLevel) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String skillId = StringArgumentType.getString(context, "skill_id");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        for (Skill skill : SkillRegistry.getAll()) {
            if (!skillId.equals("all") && !skill.id.equals(skillId)) continue;

            // 如果 targetLevel 是 -1，遍历所有等级；否则只处理指定等级
            for (int lvl = 1; lvl <= skill.maxLevel; lvl++) {
                if (targetLevel != -1 && targetLevel != lvl) continue;

                for (UnlockCriterion c : skill.getCriteria(lvl)) {
                    int targetValue = grant ? c.getThreshold() : 0;
                    // 1. 如果是原版 Stat 条件，走原版逻辑
                    if (c.getStat() != null) {
                        player.getStatHandler().setStat(player, c.getStat(), targetValue);
                    }
                    // 2. 如果是 NBT 条件，走 NBT 修改逻辑
                    else if (c.getNbtKey() != null) {
                        // 使用 PacketUtils.setData 安全修改 NBT
                        PacketUtils.setData(player, c.getNbtKey(), targetValue);
                    }
                }
            }
            // === 撤销条件时同时锁定技能 ===
            // 如果执行的是 revoke 操作 (!grant)，则强制将技能等级设为 0
            if (!grant) {
                // 这里不需要 isSkillActive 判断，直接重置最安全
                PacketUtils.setSkillLevel(player, skill.id, 0);
            }
        }
        // 强制发包同步
        PacketUtils.syncSkillData(player);
        // 刷新属性 (防止技能被锁定后属性还在)
        SkillEffectHandler.refreshAttributes(player);

        context.getSource().sendFeedback(() -> Text.of(grant ? "§a已达成解锁条件 (UI已刷新)" : "§c已重置解锁条件并锁定技能 (UI已刷新)"), true);
        return 1;
    }
}