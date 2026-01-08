package com.qishui48.ascension.skill;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;

public class SkillEffectHandler {

    private static final UUID HEALTH_BOOST_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("b2c3d4e5-0000-0000-0000-000000000002");
    // 新增：移动速度 UUID
    private static final UUID MOVEMENT_SPEED_ID = UUID.fromString("c3d4e5f6-0000-0000-0000-000000000003");

    private static final double HP_BONUS = 4.0;

    public static void refreshAttributes(ServerPlayerEntity player) {
        // 1. 生命值
        var healthAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.removeModifier(HEALTH_BOOST_ID);
            if (PacketUtils.isSkillActive(player, "health_boost")) {
                int level = PacketUtils.getSkillLevel(player, "health_boost");
                healthAttribute.addPersistentModifier(new EntityAttributeModifier(
                        HEALTH_BOOST_ID, "Skill Health Boost", HP_BONUS * level, EntityAttributeModifier.Operation.ADDITION));
            }
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }

        // 2. 攻击速度 (战斗本能)
        var attackSpeedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.removeModifier(ATTACK_SPEED_ID);
            if (PacketUtils.isSkillActive(player, "battle_instinct")) {
                int level = PacketUtils.getSkillLevel(player, "battle_instinct");
                double boost = level * 0.30;
                attackSpeedAttr.addPersistentModifier(new EntityAttributeModifier(
                        ATTACK_SPEED_ID, "Skill Attack Speed", boost, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        // 3. === 新增：移动速度 (迅猛攻势) ===
        var moveSpeedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (moveSpeedAttr != null) {
            moveSpeedAttr.removeModifier(MOVEMENT_SPEED_ID);
            if (PacketUtils.isSkillActive(player, "swift_move")) {
                int level = PacketUtils.getSkillLevel(player, "swift_move");
                double boost = 0.0;
                if (level == 1) boost = 0.13;
                else if (level == 2) boost = 0.25;
                else if (level >= 3) boost = 0.4;

                moveSpeedAttr.addPersistentModifier(new EntityAttributeModifier(
                        MOVEMENT_SPEED_ID, "Skill Move Speed", boost, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    public static void onSkillUnlocked(ServerPlayerEntity player, String skillId) {
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 2.0f);

        if (skillId.equals("health_boost")) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 2));
            player.playSound(SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 0.8f);
        }
    }
}