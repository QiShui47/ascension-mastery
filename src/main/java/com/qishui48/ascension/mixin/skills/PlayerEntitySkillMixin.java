package com.qishui48.ascension.mixin.skills;

import com.qishui48.ascension.mixin.mechanics.LivingEntityAccessor;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.util.DistanceUtils;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Optional;

import static com.qishui48.ascension.skill.SkillEffectHandler.applySugarMasterEffect;

@Mixin(PlayerEntity.class)
public class PlayerEntitySkillMixin {
    // 口袋熔炉 //
    @Unique private int furnaceTimer = 0;
    @Unique private ItemStack lastSmeltingStack = ItemStack.EMPTY;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickPocketFurnace(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            int level = PacketUtils.getSkillLevel(serverPlayer, "pocket_furnace");
            if (level <= 0) return;
            // === [修复] 使用 isSkillActive 替代 getSkillLevel > 0 ===
            // 这样当玩家中键停用技能时，逻辑会立即停止
            if (!PacketUtils.isSkillActive(serverPlayer, "pocket_furnace")) {
                // 如果当前正在熔炼中（Timer > 0），说明技能刚被关掉，需要重置状态
                if (furnaceTimer > 0) {
                    ItemStack currentStack = player.getInventory().getStack(8);
                    resetFurnace(serverPlayer, currentStack);
                }
                return;
            }
            ItemStack currentStack = player.getInventory().getStack(8);

            if (currentStack.isEmpty() || !ItemStack.canCombine(currentStack, lastSmeltingStack)) {
                resetFurnace(serverPlayer, currentStack);
                lastSmeltingStack = currentStack.isEmpty() ? ItemStack.EMPTY : currentStack.copy();
            }

            if (currentStack.isEmpty()) return;

            World world = player.getWorld();
            SimpleInventory testInv = new SimpleInventory(currentStack);
            Optional<SmeltingRecipe> recipe = world.getRecipeManager()
                    .getFirstMatch(RecipeType.SMELTING, testInv, world);

            if (recipe.isPresent()) {
                int totalTime = (level >= 2) ? 200 : 400;

                if (furnaceTimer == 0 || !player.getItemCooldownManager().isCoolingDown(currentStack.getItem())) {
                    player.getItemCooldownManager().set(currentStack.getItem(), totalTime - furnaceTimer);
                }
                furnaceTimer++;

                if (furnaceTimer >= totalTime) {
                    ItemStack result = recipe.get().getOutput(world.getRegistryManager()).copy();
                    if (player.getInventory().insertStack(result)) {
                        currentStack.decrement(1);
                        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                                net.minecraft.sound.SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE,
                                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.0f);
                        furnaceTimer = 0;
                        player.getItemCooldownManager().set(currentStack.getItem(), 0);
                    } else {
                        furnaceTimer = totalTime;
                    }
                }
            } else {
                resetFurnace(serverPlayer, currentStack);
            }
        }
    }
    // 辅助方法
    @Unique
    private void resetFurnace(ServerPlayerEntity player, ItemStack currentStack) {
        furnaceTimer = 0;
        lastSmeltingStack = currentStack.copy();
        if (!currentStack.isEmpty()) {
            player.getItemCooldownManager().set(currentStack.getItem(), 0);
        }
    }

    // 火锅食客 & 糖分主理人（南瓜派部分） //
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void onEatFood(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (!world.isClient && (Object) this instanceof ServerPlayerEntity serverPlayer) {

            // === 火锅食客 (Hotpot Diner) ===
            if (PacketUtils.isSkillActive(serverPlayer, "hotpot_diner")) {
                // 检查是否是熟肉 (列举常见熟肉)
                Item item = stack.getItem();
                boolean isCookedMeat = (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP ||
                        item == Items.COOKED_MUTTON || item == Items.COOKED_CHICKEN ||
                        item == Items.COOKED_RABBIT);

                if (isCookedMeat) {
                    int level = PacketUtils.getSkillLevel(serverPlayer, "hotpot_diner");
                    // 概率: Lv1=10%, Lv2=20%, Lv3=30%
                    float chance = level * 0.1f;

                    if (serverPlayer.getRandom().nextFloat() < chance) {
                        // 恢复 3 颗心 (6点)
                        serverPlayer.heal(6.0f);
                        // 播放奖励音效
                        world.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f); // 嘶啦声
                    }
                }
            }

            // === 糖分主理人逻辑（南瓜派部分） ===
            if (PacketUtils.isSkillActive(serverPlayer, "sugar_master")) {
                Item item = stack.getItem();
                if (item == Items.PUMPKIN_PIE) {
                    applySugarMasterEffect(serverPlayer, item.getFoodComponent());
                }
            }
        }
    }

    // 森林主宰 & 矿工狂热 //
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void modifyMiningSpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        float speed = cir.getReturnValue();
        // === 双端通用的技能检查 ===
        // 我们不能使用 PacketUtils，因为它需要 ServerPlayerEntity。
        // 我们直接通过接口读取 NBT，这在 ClientPlayerEntity 和 ServerPlayerEntity 上都有效。
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        // 辅助变量：安全获取技能等级 (如果未解锁则为 0)
        int lumberjackLevel = 0;
        int minerFrenzyLevel = 0;
        if (nbt.contains("skill_levels")) {
            NbtCompound levels = nbt.getCompound("skill_levels");
            NbtCompound disabled = nbt.contains("disabled_skills") ? nbt.getCompound("disabled_skills") : new NbtCompound();
            // 获取等级，同时检查是否被禁用
            if (!disabled.getBoolean("lumberjack")) {
                lumberjackLevel = levels.getInt("lumberjack");
            }
            if (!disabled.getBoolean("miner_frenzy")) {
                minerFrenzyLevel = levels.getInt("miner_frenzy");
            }
        }
        // A. 森林主宰 (Lumberjack)
        // 条件：目标是原木或树叶
        if (block.isIn(BlockTags.LOGS) || block.isIn(BlockTags.LEAVES)) {
            if (lumberjackLevel > 0) {
                // 提升倍率：Lv1=1.2x, Lv2=1.4x, Lv3=1.6x
                float multiplier = 1.0f + (lumberjackLevel * 0.2f) + (lumberjackLevel == 3 ? 0.2f : 0);
                speed *= multiplier;
            }
        }
        // B. 矿工狂热 (Miner's Frenzy)
        if (player.getMainHandStack().getItem() instanceof PickaxeItem) {
            String id = net.minecraft.registry.Registries.BLOCK.getId(block.getBlock()).getPath();
            // 简单的关键词匹配
            boolean isStoneType = id.contains("stone") || id.contains("deepslate") || id.contains("diorite") ||
                    id.contains("andesite") || id.contains("granite");
            if (isStoneType && minerFrenzyLevel > 0) {
                // 提升倍率
                float multiplier = 1.0f + (minerFrenzyLevel * 0.9f);
                speed *= multiplier;
            }
        }
        cir.setReturnValue(speed);
    }

    // 缸中之脑 //
    @Unique private int glassWaterBreathingTicks = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickBrainInAJar(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        // 只在服务端处理 Buff 给予 (客户端会自动同步 Buff 状态)
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            if (PacketUtils.isSkillActive(serverPlayer, "brain_in_a_jar")) {
                ItemStack headStack = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
                boolean isGlass = !headStack.isEmpty() && headStack.getItem() instanceof net.minecraft.item.BlockItem bi &&
                        bi.getBlock() instanceof net.minecraft.block.AbstractGlassBlock;
                if (isGlass) {
                    // 1. 如果头在空气中 (不在水里)
                    if (!player.isSubmergedIn(FluidTags.WATER)) {
                        // 获取最大时间
                        int level = PacketUtils.getSkillLevel(serverPlayer, "brain_in_a_jar");
                        int duration = 12 * 20;
                        if (level >= 2) duration = 24 * 20;
                        if (level >= 3) duration = 36 * 20;
                        if (level >= 4) duration = 48 * 20;
                        if (level >= 5) duration = 60 * 20;
                        // 2. 补充 Buff (如果当前没有，或者持续时间不满，就覆盖)
                        // 使用 ambient=false, showParticles=false, showIcon=true
                        // 这样每 tick 刷新，玩家看到的就是满的时间
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, duration, 0, false, false, true));
                    }
                    // 3. 如果头在水里 -> 什么都不做，让 Buff 自然倒计时
                }
            }
        }
    }

    // 忧郁人格 //
    @Unique private int melancholicTimer = 0;
    @Unique private int melancholicBuffRetention = 0; // 抗性 buff 的残留时间

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickMelancholic(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            if (PacketUtils.isSkillActive(serverPlayer, "melancholic_personality")) {
                int level = PacketUtils.getSkillLevel(serverPlayer, "melancholic_personality");
                World world = player.getWorld();
                BlockPos pos = player.getBlockPos();
                // 1. 环境判定：(下雨 或 雷暴) 且 头顶能看到天
                boolean isWeatherBad = world.isRaining() || world.isThundering();
                // isSkyVisible 判定位置是否能直视天空 (透明方块也算遮挡，如果需要穿过玻璃判定更复杂，这里用原版逻辑)
                boolean isExposed = world.isSkyVisible(pos);
                // 生物群系也得下雨才行 (沙漠里下雨天是不下雨的)
                boolean biomeRains = world.getBiome(pos).value().getPrecipitation(pos) != net.minecraft.world.biome.Biome.Precipitation.NONE;
                boolean conditionMet = isWeatherBad && isExposed && biomeRains;
                // === 效果 1: 缓慢恢复 (Lv1+) ===
                if (conditionMet) {
                    melancholicTimer++;
                    // 每 4秒 (80 tick) 回半颗心
                    if (melancholicTimer % 80 == 0) {
                        if (player.getHealth() < player.getMaxHealth()) {
                            player.heal(1.0f);
                        }
                    }
                    // 每 6秒 (120 tick) 回半个鸡腿
                    if (melancholicTimer % 120 == 0) {
                        player.getHungerManager().add(1, 0.0f);
                    }
                    // 满足条件时，重置残留时间 (12秒 = 240 tick)
                    if (level >= 2) {
                        melancholicBuffRetention = 240;
                    }
                } else {
                    // 不满足条件，计时器不重置，但也不增加，或者你可以选择归零
                    // 这里归零比较合理，断了就要重新蓄力
                    melancholicTimer = 0;
                }
                // === 效果 2: 抗性提升 (Lv2+) ===
                if (level >= 2) {
                    if (melancholicBuffRetention > 0) {
                        // 给予抗性 I (showParticles=false)
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 0, false, false, true));
                        melancholicBuffRetention--;
                    }
                }
            }
        }
    }

    // 人力发电机 //
    @Unique private double dynamoLastX, dynamoLastY, dynamoLastZ;
    @Unique private float dynamoDistAccumulator = 0;
    @Unique private boolean dynamoInit = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHumanDynamo(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getWorld().isClient || player.age % 20 != 0 || !(player instanceof ServerPlayerEntity serverPlayer)) return;

        if (!PacketUtils.isSkillActive(serverPlayer, "human_dynamo")) return;

        if (!dynamoInit) {
            dynamoLastX = player.getX(); dynamoLastY = player.getY(); dynamoLastZ = player.getZ();
            dynamoInit = true;
        }

        // 使用 DistanceUtils 检查移动和计算距离
        if (DistanceUtils.hasMoved(player.getX(), player.getY(), player.getZ(), dynamoLastX, dynamoLastY, dynamoLastZ)) {

            // 直接获取 cm
            int distCm = DistanceUtils.getDistanceCm(player.getX(), player.getY(), player.getZ(), dynamoLastX, dynamoLastY, dynamoLastZ);
            dynamoDistAccumulator += distCm;

            if (dynamoDistAccumulator >= 10000) { // 100米
                int level = PacketUtils.getSkillLevel(serverPlayer, "human_dynamo");
                int xp = 0;
                if (level == 1) xp = 5;
                else if (level == 2) xp = 8;
                else if (level >= 3) xp = 11;
                player.addExperience(xp);
                player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 2.0f);
                dynamoDistAccumulator -= 10000;
            }
        }

        dynamoLastX = player.getX(); dynamoLastY = player.getY(); dynamoLastZ = player.getZ();
    }

    // 火焰抵抗 //
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickFireEffect(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            //禁用时不生效
            if (!PacketUtils.isSkillActive(serverPlayer, "fire_resistance")) return;
            // 火焰抵抗 Lv2 (燃烧时间减半)
            if (player.isOnFire() && PacketUtils.getSkillLevel(serverPlayer, "fire_resistance") >= 2) {
                int ticks = player.getFireTicks();
                if (ticks > 0) player.setFireTicks(ticks - 1);
            }
            // 热能引擎
            if (player.age % 10 == 0) {
                SkillEffectHandler.updateThermalDynamo(serverPlayer);
            }
        }
    }

    // 饥饿体质 & 饥饿爆发解锁逻辑 //
    @Unique private int lastFoodLevel = 20;
    // 饥饿爆发专用变量
    @Unique private double hbLastX, hbLastY, hbLastZ;
    @Unique private boolean hbInit = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHungerSkills(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getWorld().isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return;

        // 1. 饥饿体质 (Hunger Constitution)
        int currentFood = player.getHungerManager().getFoodLevel();
        if (currentFood < this.lastFoodLevel) {
            int level = PacketUtils.getSkillLevel(serverPlayer, "hunger_constitution");
            if (level > 0) {
                // 给几秒的伤害吸收
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, level - 1));
            }
        }
        this.lastFoodLevel = currentFood;

        // 2. 饥饿爆发 (Hunger Burst) - 效果实现
        if (currentFood == 0 && PacketUtils.isSkillActive(serverPlayer, "hunger_burst")) {
            // [优化] 直接检测速度 (Velocity) 来判断是否移动
            // 如果玩家当前这一刻有速度 (cm/tick > 0)，就视为在移动
            // 这比维护一套 XYZ 坐标要省事得多
            if (DistanceUtils.getSpeedCm(player) > 0) {
                // 给予短暂 Buff
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 2, true, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20, 2, true, false));
            }
        }
    }

    // 御剑飞行 //
    @Unique private int swordFlightHoverTimer = 0;
    @Unique private boolean isSwordFlying = false; // 用于同步给客户端渲染

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickSwordFlight(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        // 0. 基础检查
        if (player.isSpectator() || !player.isAlive()) {
            this.isSwordFlying = false;
            return;
        }

        // 1. 检查装备
        ItemStack feetStack = player.getEquippedStack(EquipmentSlot.FEET);
        if (!(feetStack.getItem() instanceof SwordItem)) {
            this.isSwordFlying = false;
            return;
        }

        // 2. 检查技能
        boolean hasSkill = false;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            hasSkill = PacketUtils.isSkillActive(serverPlayer, "sword_flight");
        } else {
            hasSkill = true;
        }

        if (!hasSkill) {
            this.isSwordFlying = false;
            return;
        }

        // === 起飞许可 ===
        // 只有在当前并未飞行（准备起飞）时，才检查空间
        // 一旦起飞成功 (isSwordFlying = true)，之后就不再检查，防止飞行中途经过低矮处坠机
        if (!this.isSwordFlying) {
            // 检测点：眼睛高度 + 0.6 (头部空间)
            if (!player.getWorld().isClient && !player.getWorld().getBlockState(BlockPos.ofFloored(player.getX(), player.getEyeY() + 0.6, player.getZ())).isAir()) {
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    unequipSword(serverPlayer, feetStack);
                    player.sendMessage(Text.translatable("message.ascension.flight_no_space").formatted(Formatting.RED), true);
                }
                // 标记为 false，阻止后续飞行逻辑
                this.isSwordFlying = false;
                return;
            }
        }

        this.isSwordFlying = true;

        // === 3. 物理飞行逻辑 ===
        player.setNoGravity(true);
        player.fallDistance = 0;

        Vec3d lookDir = player.getRotationVector();
        int level = 1;
        if (player instanceof ServerPlayerEntity sp) {
            level = PacketUtils.getSkillLevel(sp, "sword_flight");
        }

        // 数值设置
        double baseMaxSpeed = (level >= 2) ? 4.5 : 3.0;
        double materialMultiplier = getMaterialSpeedMultiplier(feetStack);
        double finalMaxSpeed = baseMaxSpeed * materialMultiplier;
        double horizontalInertia = 0.962;
        double verticalInertia = 0.975;

        double targetY = 0;
        boolean isJumping = ((LivingEntityAccessor) player).isJumping();

        if (isJumping) targetY += 1.1;
        if (player.isSneaking()) targetY -= 1.2;

        boolean isPressingForward = player.forwardSpeed > 0.01f;

        // 计算推力
        double sprintAcceleration = finalMaxSpeed * (1 - horizontalInertia) * 0.77;
        double verticalSprintAcceleration = finalMaxSpeed * (1 - verticalInertia) * 0.4;
        double walkAcceleration = sprintAcceleration * 0.05;

        if (player.isSprinting()) {
            player.addVelocity(lookDir.x * sprintAcceleration, targetY * verticalSprintAcceleration, lookDir.z * sprintAcceleration);
        } else if (isPressingForward) {
            player.addVelocity(lookDir.x * walkAcceleration, targetY * 0.05, lookDir.z * walkAcceleration);
        } else {
            player.addVelocity(0, targetY * 0.05, 0);
        }

        // === 磁悬浮底盘逻辑 ===
        // 向下发射射线检测地面距离
        Vec3d rayStart = player.getPos();
        Vec3d rayEnd = rayStart.add(0, -1.2, 0); // 检测脚下 1.2 米

        BlockHitResult hitResult = player.getWorld().raycast(new RaycastContext(
                rayStart, rayEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            double distanceToGround = hitResult.getPos().distanceTo(rayStart);
            double hoverHeight = 0.5; // 目标悬浮高度

            if (distanceToGround < hoverHeight) {
                // 如果低于悬浮高度，施加一个向上的"弹簧力"
                // 距离越近，推力越大，模拟磁斥力
                double pushForce = (hoverHeight - distanceToGround) * 0.18;
                // 只施加向上推力，不改变水平速度
                player.addVelocity(0, pushForce, 0);
            }
        }

        // 速度限制
        Vec3d newVel = player.getVelocity();
        double horizontalSpeed = Math.sqrt(newVel.x * newVel.x + newVel.z * newVel.z);
        double currentMaxSpeedLimit = player.isSprinting() ? finalMaxSpeed : (finalMaxSpeed * 0.3);

        if (horizontalSpeed > currentMaxSpeedLimit) {
            double scale = currentMaxSpeedLimit / horizontalSpeed;
            newVel = new Vec3d(newVel.x * scale, newVel.y, newVel.z * scale);
        }
        player.setVelocity(newVel.multiply(horizontalInertia));

        // === 4. 服务端逻辑：破坏与碰撞 ===
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // A. 饱食度
            boolean isMoving = horizontalSpeed > 0.05 || Math.abs(newVel.y) > 0.05;
            if (isMoving && player.isSprinting()) {
                player.addExhaustion(0.03F);
            }

            // A. 耐久消耗
            if (isMoving) {
                double moveDivisor = (level >= 2) ? 2.0 : 1.0;
                if (player.getRandom().nextDouble() < (horizontalSpeed / moveDivisor) * 0.5) {
                    damageSword(serverPlayer, feetStack, 1);
                }
            } else {
                swordFlightHoverTimer++;
                int hoverThreshold = (level >= 2) ? 300 : 200;
                if (swordFlightHoverTimer >= hoverThreshold) {
                    swordFlightHoverTimer = 0;
                    damageSword(serverPlayer, feetStack, 1);
                }
            }

            // B. 实体碰撞 (撞击敌人)
            Box killBox = player.getBoundingBox().expand(0.5, 0.2, 0.5).offset(0, -0.5, 0);
            List<Entity> targets = player.getWorld().getOtherEntities(player, killBox);
            for (Entity target : targets) {
                if (target instanceof LivingEntity livingTarget) {
                    float damage = (float) (horizontalSpeed * 20.0f);
                    if (horizontalSpeed >= finalMaxSpeed * 0.9) damage *= 1.4f;
                    if (damage < 2.0f) damage = 2.0f;

                    livingTarget.damage(player.getDamageSources().playerAttack(player), damage);
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 1.0f, 2.0f);

                    unequipSword(serverPlayer, feetStack);
                    return;
                }
            }

            // C. 方块破坏 (割草模式)
            // 只有速度足够快时才触发
            if (horizontalSpeed > 0.1) {
                // 1. 计算剑尖位置 (速度方向前方 0.8 米)
                Vec3d velocityDir = newVel.normalize();
                Vec3d swordTipPos = player.getPos().add(velocityDir.multiply(0.8));

                // 检查脚部高度的方块
                BlockPos targetPos = BlockPos.ofFloored(swordTipPos.x, swordTipPos.y + 0.1, swordTipPos.z);
                BlockState state = player.getWorld().getBlockState(targetPos);

                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    // 2. 获取硬度
                    float hardness = state.getHardness(player.getWorld(), targetPos);

                    if (hardness < 0) {
                        // 基岩/不可破坏：急停
                        player.setVelocity(0, 0, 0);
                    } else if (hardness <= 0.5f) {
                        // === 软方块 (草/泥土/树叶) -> 破坏 ===
                        if (player.getWorld().breakBlock(targetPos, true, player)) {
                            damageSword(serverPlayer, feetStack, 1);
                            // 轻微减速 (阻力感)
                            player.setVelocity(newVel.multiply(0.98));
                        }
                    } else {
                        // === 硬方块 -> 撞击判定 ===
                        if (hardness >= 3.0f) {
                            // 极硬 (矿石/黑曜石)：撞停 + 大量扣耐久 + 音效
                            player.setVelocity(0, 0, 0);
                            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.5f);
                            damageSword(serverPlayer, feetStack, 5);
                        } else {
                            // 中等硬度 (木头/石头)：不破坏 + 大幅减速
                            player.setVelocity(newVel.multiply(0.2));
                            damageSword(serverPlayer, feetStack, 2);
                        }
                    }
                }
            }
        }
    }
    // 辅助方法：安全卸下剑
    @Unique
    private void unequipSword(ServerPlayerEntity player, ItemStack swordStack) {
        ItemStack swordToReturn = swordStack.copy();
        player.getEquippedStack(EquipmentSlot.FEET).setCount(0);
        // 尝试塞回背包，塞不下就扔地上
        if (!player.getInventory().insertStack(swordToReturn)) {
            player.dropItem(swordToReturn, false);
        }
        this.isSwordFlying = false;
        player.setNoGravity(false);
    }
    // 辅助：处理耐久并防止 index out of bounds
    @Unique
    private void damageSword(ServerPlayerEntity player, ItemStack stack, int amount) {
        if (player.isCreative()) return;

        // 这里的 callback 需要正确处理装备槽位破坏
        stack.damage(amount, player, (p) -> p.sendEquipmentBreakStatus(EquipmentSlot.FEET));

        if (stack.isEmpty()) {
            player.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
    }
    // 注入：重置重力
    // 当不再御剑时，确保重力恢复。
    @Inject(method = "tick", at = @At("TAIL"))
    private void restoreGravity(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        ItemStack feetStack = player.getEquippedStack(EquipmentSlot.FEET);
        boolean valid = (feetStack.getItem() instanceof SwordItem);
        // 如果条件不满足，强制恢复重力 (防止 Bug 导致无限浮空)
        if (!valid || !this.isSwordFlying) {
            if (player.hasNoGravity() && !player.isSpectator()) {
                player.setNoGravity(false);
            }
        }
    }
    // 辅助方法：根据材质获取速度加成
    @Unique
    private double getMaterialSpeedMultiplier(ItemStack stack) {
        if (!(stack.getItem() instanceof SwordItem sword)) return 1.0;
        // 获取材质对象
        net.minecraft.item.ToolMaterial material = sword.getMaterial();
        // 注意：这是原版材质的判断方式
        if (material == net.minecraft.item.ToolMaterials.WOOD) return 1.0;       // +0%
        if (material == net.minecraft.item.ToolMaterials.STONE) return 1.1;      // +10%
        if (material == net.minecraft.item.ToolMaterials.IRON) return 1.15;      // +15%
        if (material == net.minecraft.item.ToolMaterials.DIAMOND) return 1.2;    // +20%
        if (material == net.minecraft.item.ToolMaterials.GOLD) return 1.22;      // +22%
        if (material == net.minecraft.item.ToolMaterials.NETHERITE) return 1.25; // +25%
        return 1.0; // 默认
    }
}
