package com.qishui48.ascension.skill;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.mixin.stats.AbstractFurnaceBlockEntityAccessor;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SkillEffectHandler {

    private static final UUID HEALTH_BOOST_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("b2c3d4e5-0000-0000-0000-000000000002");
    // 移动速度 UUID
    private static final UUID MOVEMENT_SPEED_ID = UUID.fromString("c3d4e5f6-0000-0000-0000-000000000003");
    // 热能引擎 UUID
    private static final UUID THERMAL_SPEED_ID = UUID.fromString("d4e5f6a1-0000-0000-0000-000000000004");
    private static final UUID THERMAL_DAMAGE_ID = UUID.fromString("e5f6a1b2-0000-0000-0000-000000000005");
    private static final java.util.UUID GLASS_ARMOR_ID = java.util.UUID.fromString("f6a1b2c3-0000-0000-0000-000000000006");
    private static final UUID STEADFAST_ARMOR_ID = UUID.fromString("f7a1b2c3-0000-0000-0000-000000000007");

    private static final double HP_BONUS = 4.0;

    public static void refreshAttributes(ServerPlayerEntity player) {
        // 生命值
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

        // 攻击速度 (战斗本能)
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

        // 移动速度 (脚底抹油)
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

        // === 缸中之脑：护甲加成 ===
        var armorAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(GLASS_ARMOR_ID);

            if (PacketUtils.isSkillActive(player, "brain_in_a_jar")) {
                // 检查头上是否戴着玻璃
                net.minecraft.item.ItemStack headStack = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
                net.minecraft.item.Item item = headStack.getItem();
                boolean isGlass = (item instanceof net.minecraft.item.BlockItem bi) &&
                        (bi.getBlock() instanceof net.minecraft.block.AbstractGlassBlock);

                if (isGlass) {
                    int level = PacketUtils.getSkillLevel(player, "brain_in_a_jar");
                    double armorValue = (level >= 5) ? 2.0 : 1.0;

                    armorAttr.addPersistentModifier(new EntityAttributeModifier(
                            GLASS_ARMOR_ID, "Brain Jar Armor", armorValue, EntityAttributeModifier.Operation.ADDITION));
                }
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

    // 糖分主理人应用效果
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

    // 斗转星移效果 //
    // 静态变量记录剩余加速时间
    private static int timeAccelerationTicks = 0;
    private static int originalRandomTickSpeed = 3;
    private static boolean isAccelerating = false;

    // 激活方法
    public static void activateTimeAcceleration(ServerWorld world, int duration) {
        if (!isAccelerating) {
            originalRandomTickSpeed = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
            world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(30, world.getServer());
            isAccelerating = true;
        }
        timeAccelerationTicks = Math.max(timeAccelerationTicks, duration);
    }

    private static void tickStarShift(MinecraftServer server) {
        if (timeAccelerationTicks > 0) {
            timeAccelerationTicks--;

            // 1. 加速昼夜循环 (额外+9，凑齐10倍)
            for (ServerWorld world : server.getWorlds()) {
                world.setTimeOfDay(world.getTimeOfDay() + 9);
            }

            // 结束恢复
            if (timeAccelerationTicks <= 0) {
                isAccelerating = false;
                ServerWorld overworld = server.getOverworld();
                overworld.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(originalRandomTickSpeed, server);
            }
        }
    }

    public static void onServerTick(MinecraftServer server) {
        tickStarShift(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickWraithWrath(player);
            tickBlink(player);
            tickSteadfast(player);
        }
    }

    private static void tickWraithWrath(ServerPlayerEntity player) {
        NbtCompound nbt = ((IEntityDataSaver)player).getPersistentData();
        ActiveSkill skill = (ActiveSkill) SkillRegistry.get("wraith_wrath");
        if (nbt.contains("wraith_charging_end")) {
            long endTime = nbt.getLong("wraith_charging_end");
            long now = player.getWorld().getTime();
            if (now < endTime) {
                if (now % 5 == 0) ((ServerWorld)player.getWorld()).spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY()+1, player.getZ(), 2, 0.3, 0.5, 0.3, 0.05);
            } else {
                nbt.remove("wraith_charging_end");
                executeWraithDamage(player);
                PacketUtils.consumeSkillCharge(player, skill, false);
            }
        }
    }

    private static void executeWraithDamage(ServerPlayerEntity player) {
        NbtCompound nbt = ((IEntityDataSaver)player).getPersistentData();
        int level = PacketUtils.getSkillLevel(player, "wraith_wrath");
        float totalDamage = (level == 1 ? 30f : level == 2 ? 40f : 50f) * 2;
        double range = 36.0;

        //检查增幅标记
        if (nbt.getBoolean("wraith_damage_boost")) {
            totalDamage *= 2.0f; // 伤害翻倍
            nbt.remove("wraith_damage_boost"); // 清除标记
        }

        List<LivingEntity> targets = new ArrayList<>();
        // 获取玩家视线向量
        Vec3d lookVec = player.getRotationVector();
        Vec3d playerPos = player.getEyePos();

        player.getWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(range),
                e -> e != player && e.isAlive() && !e.isTeammate(player) && !e.hasStatusEffect(StatusEffects.INVISIBILITY)
        ).forEach(e -> {
            // 1. 距离检测
            if (e.distanceTo(player) > range) return;
            // 2. 障碍物检测 (Raycast)
            if (!player.canSee(e)) return;

            // 3. 视野角度检测
            // 计算 "玩家->怪物" 的方向向量
            Vec3d toTargetVec = e.getPos().add(0, e.getHeight()/2, 0).subtract(playerPos).normalize();
            // 计算点积。值 > 0 表示前方，> 0.5 表示前方60度以内。
            // 这里我们设置 0.1，保证只打得到的 "视野内" 的怪
            if (lookVec.dotProduct(toTargetVec) > 0.1) {
                targets.add(e);
            }
        });

        if (targets.isEmpty()) {
            player.sendMessage(Text.translatable("message.ascension.wraith_no_target"), true);
            return;
        }

        Random rand = player.getRandom();
        double totalWeight = 0;
        double[] weights = new double[targets.size()];
        for(int i=0; i<targets.size(); i++) { weights[i] = rand.nextDouble(); totalWeight += weights[i]; }

        for (int i=0; i<targets.size(); i++) {
            float dmg = (float)((weights[i]/totalWeight) * totalDamage);
            if(dmg < 1.0f) dmg = 1.0f;
            targets.get(i).damage(player.getDamageSources().magic(), dmg);
            ((ServerWorld)player.getWorld()).spawnParticles(ParticleTypes.SCULK_SOUL, targets.get(i).getX(), targets.get(i).getBodyY(0.5), targets.get(i).getZ(), 10, 0.2, 0.2, 0.2, 0.1);
        }
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    public static void onSkillUnlocked(ServerPlayerEntity player, String skillId) {
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 2.0f);

        if (skillId.equals("health_boost")) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 2));
            player.playSound(SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 0.8f);
        }
    }

    //  闪现回溯 Tick 逻辑
    private static void tickBlink(ServerPlayerEntity player) {
        NbtCompound nbt = ((IEntityDataSaver) player).getPersistentData();
        // 检查是否存在回溯标记
        if (nbt.contains("blink_recall_deadline")) {
            long deadline = nbt.getLong("blink_recall_deadline");
            // 如果超时
            if (player.getWorld().getTime() > deadline) {
                // 清除回溯点
                nbt.remove("blink_recall_deadline");
                nbt.remove("blink_recall_x");
                nbt.remove("blink_recall_y");
                nbt.remove("blink_recall_z");
                nbt.remove("blink_recall_dim");

                // 窗口期结束，触发次要冷却
                // 使用 true 表示次要效果 (Secondary)
                Skill rawSkill = SkillRegistry.get("blink");
                if (rawSkill instanceof ActiveSkill activeSkill) {
                    // 这会正确扣除充能，并根据是否已经在冷却来决定是立即冷却还是入队
                    PacketUtils.consumeSkillCharge(player, activeSkill, true);
                }

                // 同步数据以移除进度条
                PacketUtils.syncSkillData(player);
            }
        }
    }

    // 岿然不动逻辑
    private static void tickSteadfast(ServerPlayerEntity player) {
        // 每秒更新一次护甲值 (20 ticks)，过于频繁没有必要且消耗性能
        if (player.age % 20 == 0) {
            updateSteadfastArmor(player);
        }
    }

    public static void updateSteadfastArmor(ServerPlayerEntity player) {
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        var armorAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armorAttr == null) return;

        // 1. 清理旧修饰符 (无论是否结束，都要移除旧的以便添加新的数值)
        armorAttr.removeModifier(STEADFAST_ARMOR_ID);

        // 2. 检查是否处于激活状态
        if (nbt.contains("steadfast_start_time")) {
            long startTime = nbt.getLong("steadfast_start_time");
            long now = player.getWorld().getTime();

            // 计算经过秒数
            double secondsElapsed = (now - startTime) / 20.0;

            // 获取技能等级决定衰减速率
            int level = PacketUtils.getSkillLevel(player, "steadfast");
            double decayRate = (level >= 3) ? 1.0 : 2.0;

            // 计算当前护甲: 初始 30 - (时间 * 速率)
            double currentArmor = 30.0 - (secondsElapsed * decayRate);

            if (currentArmor > 0) {
                // 应用新护甲
                armorAttr.addTemporaryModifier(new EntityAttributeModifier(
                        STEADFAST_ARMOR_ID, "Steadfast Armor", currentArmor, EntityAttributeModifier.Operation.ADDITION));
            } else {
                // 衰减至 0，移除状态
                nbt.remove("steadfast_start_time");
                // 此时 removeModifier 已经在上面执行过了，所以属性被清除
            }
        }
    }

    public static void updateFinification(ServerPlayerEntity player) {
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();

        if (!nbt.contains("finification_end")) return;

        long endTime = nbt.getLong("finification_end");
        long now = player.getWorld().getTime();

        // 检查是否过期
        if (now >= endTime) {
            nbt.remove("finification_end");
            nbt.remove("finification_level");
            PacketUtils.syncSkillData(player); // 同步以移除 UI
            player.sendMessage(Text.translatable("message.ascension.finification_expired").formatted(Formatting.GRAY), true);
            return;
        }

        // 效果逻辑
        int level = nbt.getInt("finification_level");

        // 1. 基础游泳加速 (给予海豚恩惠效果)
        // 持续 2 tick 保证状态不断但离开后立即消失
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 2, level + 1, true, false, true));

        // 2. 二级效果：水下上升冲刺
        if (level >= 2 && player.isTouchingWater()) {
            // 判断是否在向上看且按住跳跃键(或单纯向上游)
            // 简单判定：Y轴速度大于 0 且 玩家看着上方 (Pitch < -10)
            boolean isMovingUp = player.getVelocity().y > 0.01;
            boolean isLookingUp = player.getPitch() < -10;

            if (isMovingUp && isLookingUp) {
                // 施加额外向上的推力
                Vec3d rotation = player.getRotationVector();
                // 只取 Y 轴分量增强，或者沿视线冲刺
                player.addVelocity(rotation.x * 0.05, 0.12, rotation.z * 0.05);
                player.velocityModified = true;

                // 产生气泡流
                ((ServerWorld)player.getWorld()).spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                        player.getX(), player.getY(), player.getZ(), 1, 0.2, 0.2, 0.2, 0.1);

                // === 双倍消耗惩罚 ===
                // 当前是第 T 刻，结束是 E。
                // 正常流逝：下一刻变为 T+1, 距离 E 缩短 1。
                // 双倍消耗：我们需要让距离 E 缩短 2。
                // 即：将 E 减去 1。
                // 这样 ActiveSkillHud 计算 (E - T) / Total 时，分子会减小得更快。
                long newEndTime = endTime - 1;
                nbt.putLong("finification_end", newEndTime);

                // 更新 UI 上的结束时间显示 (不需要每 tick 同步，可以每 5 tick 同步一次减少发包，或者为了流畅每 tick 同步)
                // 这里为了视觉流畅性，每 tick 修改 NBT 但不用每 tick 发包，
                // 但 ActiveSkillHud 是依赖 NBT 的，如果客户端不同步，UI 不会加速。
                // 权衡：每 4 tick (0.2s) 同步一次 NBT
                if (now % 4 == 0) {
                    // 还要更新槽位里的 effect_end 以便 UI 正确显示
                    // 这部分逻辑较繁琐，建议提取一个 helper
                    SkillActionHandler.updateSkillSlotBus(player, "finification", -1, newEndTime); // -1 ignore total
                    PacketUtils.syncSkillData(player);
                }
            }
        }
    }

    public static final Identifier S2C_HUNTER_VISION_ID = new Identifier(Ascension.MOD_ID, "s2c_hunter_vision");
    public static void updateHunterVision(ServerPlayerEntity player) {
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();

        if (!nbt.contains("hunter_vision_end")) return;

        long endTime = nbt.getLong("hunter_vision_end");
        long now = player.getWorld().getTime();

        if (now >= endTime) {
            nbt.remove("hunter_vision_end");
            nbt.remove("hunter_vision_level");
            // 发送空包以清除客户端残留的高亮
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, S2C_HUNTER_VISION_ID, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
            return;
        }

        // 每 5 tick 扫描一次，节省性能
        if (now % 5 == 0) {
            int level = nbt.getInt("hunter_vision_level");
            List<net.minecraft.entity.LivingEntity> entities = player.getWorld().getEntitiesByClass(
                    net.minecraft.entity.LivingEntity.class,
                    player.getBoundingBox().expand(25.0),
                    e -> e.isAlive() && e != player
            );

            net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            int count = 0;
            buf.writeInt(0); // 预留数量位置

            for (net.minecraft.entity.LivingEntity entity : entities) {
                int color = -1;

                // 判定敌我与等级
                if (entity instanceof net.minecraft.entity.mob.Monster) {
                    if (level >= 2) color = 0xFF0000; // 敌对：红色
                } else if (entity instanceof net.minecraft.entity.player.PlayerEntity) {
                    if (level >= 3) color = 0xFFFFFF; // 玩家：白色
                } else {
                    color = 0x00FF00; // 非敌对：绿色
                }

                if (color != -1) {
                    buf.writeInt(entity.getId());
                    buf.writeInt(color);
                    count++;
                }
            }

            // 回填真实数量
            int writerIndex = buf.writerIndex();
            buf.writerIndex(0);
            buf.writeInt(count);
            buf.writerIndex(writerIndex);

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, S2C_HUNTER_VISION_ID, buf);
        }
    }
}