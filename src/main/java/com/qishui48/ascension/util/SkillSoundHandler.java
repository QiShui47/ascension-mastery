package com.qishui48.ascension.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public class SkillSoundHandler {

    public enum SoundType {
        ACTIVATE,   // 技能启动 (普通)
        FAIL,       // 失败/无材料
        BUFF,       // 获得增益 (清脆)
        DEFENSE,    // 防御/格挡 (金属声)
        OFFENSE,    // 攻击/雷电
        TELEPORT,   // 传送
        COOLDOWN    // 冷却结束/充能完毕
    }

    public static void playSkillSound(ServerPlayerEntity player, SoundType type) {
        switch (type) {
            case ACTIVATE:
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                break;
            case FAIL:
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1.0f, 1.2f);
                break;
            case BUFF:
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.2f);
                break;
            case DEFENSE:
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                break;
            case OFFENSE:
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.5f, 2.0f);
                break;
            case TELEPORT:
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                break;
            case COOLDOWN:
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.2f, 2.0f);
                break;
        }
    }
}