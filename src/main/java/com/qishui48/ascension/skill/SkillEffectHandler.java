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
    // 移动速度 UUID
    private static final UUID MOVEMENT_SPEED_ID = UUID.fromString("c3d4e5f6-0000-0000-0000-000000000003");
    // 热能引擎 UUID
    private static final UUID THERMAL_SPEED_ID = UUID.fromString("d4e5f6a1-0000-0000-0000-000000000004");
    private static final UUID THERMAL_DAMAGE_ID = UUID.fromString("e5f6a1b2-0000-0000-0000-000000000005");

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

        // 调用下面的专用方法来更新热能引擎状态
        updateThermalDynamo(player);
    }

    // === 独立出来的热能引擎更新逻辑 ===
    // 因为玩家的着火状态每 tick 都在变，我们需要高频调用这个方法
    public static void updateThermalDynamo(ServerPlayerEntity player) {
        var moveSpeedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        var attackDamageAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        if (moveSpeedAttr == null || attackDamageAttr == null) return;

        // 1. 【核心】先移除，确保不叠加，也不残留
        moveSpeedAttr.removeModifier(THERMAL_SPEED_ID);
        attackDamageAttr.removeModifier(THERMAL_DAMAGE_ID);

        // 2. 判定条件：技能激活 且 正在着火
        if (PacketUtils.isSkillActive(player, "thermal_dynamo") && player.isOnFire()) {

            // 3. 添加临时属性 (Add Temporary Modifier)
            // 速度 +30% (Multiply Total)
            moveSpeedAttr.addTemporaryModifier(new EntityAttributeModifier(
                    THERMAL_SPEED_ID, "Thermal Speed", 0.30, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));

            // 攻击力 +20% (Multiply Total)
            attackDamageAttr.addTemporaryModifier(new EntityAttributeModifier(
                    THERMAL_DAMAGE_ID, "Thermal Damage", 0.20, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // === [新增] 糖分主理人：应用效果 ===
    public static void applySugarMasterEffect(net.minecraft.server.network.ServerPlayerEntity player, net.minecraft.item.FoodComponent food) {
        if (food == null) return;

        int level = PacketUtils.getSkillLevel(player, "sugar_master");
        if (level <= 0) return; // 没技能就不处理

        // 1. 计算原本会获得的饱和度
        // 原版公式: nutrition * saturationModifier * 2.0
        float baseSat = food.getHunger() * food.getSaturationModifier() * 2.0f;
        // 2. 计算额外饱和度 (每级 +1.5)
        float extraSat = level * 1.5f;
        // 3. 计算逻辑
        int currentFood = player.getHungerManager().getFoodLevel();
        float currentSat = player.getHungerManager().getSaturationLevel();
        // 吃完后的饱食度 (上限20)
        int newFood = Math.min(currentFood + food.getHunger(), 20);
        // 理论上的总饱和度
        float projectedSat = currentSat + baseSat + extraSat;
        // 实际能存下的饱和度 (原版规则：饱和度不能超过当前饱食度)
        float cappedSat = Math.min(projectedSat, newFood);
        // 溢出部分
        float overflow = projectedSat - newFood;
        // Level 3 特权：溢出转化生命
        if (level >= 3 && overflow > 0) {
            // 每 3.0 饱和度 -> 恢复 0.5 心 (1.0 HP)
            int healTicks = (int) (overflow / 3.0f);
            if (healTicks > 0) {
                player.heal(healTicks * 1.0f);
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 2.0f);
            }
        }

        final float finalSaturation = cappedSat;
        player.getServer().execute(() -> {
            if (player.isAlive()) {
                player.getHungerManager().setSaturationLevel(finalSaturation);
            }
        });
    }

    public static void onSkillUnlocked(ServerPlayerEntity player, String skillId) {
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 2.0f);

        if (skillId.equals("health_boost")) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 2));
            player.playSound(SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 0.8f);
        }
    }
}