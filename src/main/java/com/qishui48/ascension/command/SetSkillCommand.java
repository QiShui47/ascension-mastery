package com.qishui48.ascension.command;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SetSkillCommand {
    // 注册逻辑
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess, CommandManager.RegistrationEnvironment registrationEnvironment) {
        dispatcher.register(CommandManager.literal("setskills") // 指令名 /setskills
                .requires(source -> source.hasPermissionLevel(2)) // 需要 OP 权限 (作弊模式)
                .then(CommandManager.argument("amount", IntegerArgumentType.integer()) // 参数 1: 整数
                        .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))) // 执行逻辑
                ));
    }

    // 执行逻辑
    private static int run(ServerCommandSource source, int amount) {
        try {
            // 获取指令的使用者 (必须是玩家，不能是控制台)
            ServerPlayerEntity player = source.getPlayerOrThrow();

            // 修改 NBT
            IEntityDataSaver dataSaver = (IEntityDataSaver) player;
            dataSaver.getPersistentData().putInt("skill_points", amount);

            // 同步给客户端
            PacketUtils.syncSkillData(player);

            // 反馈
            source.sendFeedback(() -> Text.translatable("command.ascension.set_skills.success", amount).formatted(Formatting.GREEN), false);
            return 1; // 返回 1 表示执行成功
        } catch (Exception e) {
            return 0;
        }
    }
}