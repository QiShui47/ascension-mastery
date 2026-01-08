package com.qishui48.ascension.command;

import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.skill.UnlockCriterion;
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
                                        context.getSource().sendFeedback(() -> Text.of("§a[OP] 已将所有技能等级设为: " + level), false);
                                    } else {
                                        // === 单个处理 ===
                                        if (SkillRegistry.get(id) == null) {
                                            context.getSource().sendError(Text.of("§c未知技能 ID: " + id));
                                            return 0;
                                        }
                                        PacketUtils.setSkillLevel(player, id, level);
                                        context.getSource().sendFeedback(() -> Text.of("§a[OP] 已将 " + id + " 等级设为: " + level), false);
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
    }
    // 辅助方法
    private static int modifyCriteria(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, boolean grant, int targetLevel) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String skillId = StringArgumentType.getString(context, "skill_id");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        for (Skill skill : SkillRegistry.getAll()) {
            if (!skillId.equals("all") && !skill.id.equals(skillId)) continue;

            // 如果 targetLevel 是 -1，遍历所有等级；否则只处理指定等级
            // 注意：criteriaMap 的 key 是等级 (1, 2, 3...)
            for (int lvl = 1; lvl <= skill.maxLevel; lvl++) {
                if (targetLevel != -1 && targetLevel != lvl) continue;

                for (UnlockCriterion c : skill.getCriteria(lvl)) {
                    int targetValue = grant ? c.getThreshold() : 0;
                    player.getStatHandler().setStat(player, c.getStat(), targetValue);
                }
            }
        }

        // === 核心修复：强制发包同步 ===
        PacketUtils.syncSkillData(player);

        context.getSource().sendFeedback(() -> Text.of(grant ? "§a已达成解锁条件 (UI已刷新)" : "§c已重置解锁条件 (UI已刷新)"), true);
        return 1;
    }
}