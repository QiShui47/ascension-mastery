package com.qishui48.ascension.event;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ModEvents {
    public static void register() {
        // 维度首次踏足奖励
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            String dimId = destination.getRegistryKey().getValue().toString();

            IEntityDataSaver dataSaver = (IEntityDataSaver) player;
            NbtCompound nbt = dataSaver.getPersistentData();

            NbtList visitedDims;
            if (nbt.contains("visited_dimensions", NbtElement.LIST_TYPE)) {
                visitedDims = nbt.getList("visited_dimensions", NbtElement.STRING_TYPE);
            } else {
                visitedDims = new NbtList();
            }

            for (NbtElement element : visitedDims) {
                if (element.asString().equals(dimId)) return;
            }

            visitedDims.add(NbtString.of(dimId));
            nbt.put("visited_dimensions", visitedDims);

            int points = 20;
            if (dimId.equals("minecraft:the_end")) points = 50;

            int currentPoints = nbt.getInt("my_global_skills");
            nbt.putInt("my_global_skills", currentPoints + points);
            PacketUtils.syncSkillData(player);

            Text msg = Text.literal("§d[位面旅行] §f抵达 ")
                    .append(Text.literal(dimId).formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.literal(" §a+" + points + " 技能点").formatted(Formatting.BOLD));
            PacketUtils.sendNotification(player, msg);
        });
    }
}