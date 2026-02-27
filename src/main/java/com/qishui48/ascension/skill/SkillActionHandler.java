package com.qishui48.ascension.skill;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.MotionJumpUtils;
import com.qishui48.ascension.util.PacketUtils;
import com.qishui48.ascension.util.SkillSoundHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.*;

public class SkillActionHandler {

    // 空中推进
    public static void executeBoost(ServerPlayerEntity player) {
        // 检查游戏模式
        // 如果是创造模式或者旁观模式，直接退出，不执行二段跳逻辑
        // 注意：在 Fabric/Yarn 中，获取游戏模式通常通过 interactionManager
        if (player.interactionManager.getGameMode() == GameMode.CREATIVE || player.isSpectator()) {
            return;
        }
        // 1. 施加力
        var rotation = player.getRotationVector();
        player.addVelocity(rotation.x * 0.2f, 0.8f, rotation.z * 0.2f);
        player.velocityModified = true;
        // 2. 记录摔落缓冲 (存一个具体的数值，而不是 boolean)
        // 假设这次跳跃能跳 5 格高，我们就豁免 6 格的摔落伤害
        setFallDistanceCushion(player, 6.0f);
        // 3. 特效
        player.getWorld().playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 0.8f);
        ((ServerWorld)player.getWorld()).spawnParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY(), player.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
        // 4. 消耗饥饿度
        player.getHungerManager().addExhaustion(2.0f);
    }

    // 火箭跳(蓄力跳)
    public static void executeChargedJump(ServerPlayerEntity player, float powerRatio) {
        // 1. 使用工具类计算物理向量 (核心！)
        int level = PacketUtils.getSkillLevel(player, "charged_jump");
        Vec3d velocity = MotionJumpUtils.calculateChargedJumpVector(player.getPitch(), player.getYaw(), powerRatio, 2.6f * level);

        // 2. 应用速度
        player.addVelocity(velocity.x, velocity.y, velocity.z);

        player.velocityModified = true;

        // 3. 给予巨额摔落缓冲 (根据力度动态计算，保证安全)
        // 比如 velocity.y 是 2.0，缓冲就是 20.0，足够抵消落地伤害
        setFallDistanceCushion(player, (float) (Math.abs(velocity.y) * 15.0f));

        // 4. 音效与特效
        player.getWorld().playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 2.0f - powerRatio);

        spawnParticles(player, 20);
    }

    // === 雷霆万钧 ===
    public static boolean executeThunderClap(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        if (isSecondary) {
            PacketUtils.consumeSkillCharge(player, skill, true);
            World world = player.getWorld();
            net.minecraft.entity.LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ());
                world.spawnEntity(lightning);
            }
            return true;
        }

        // === 使用提取的方法 ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);

        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }

        consumeMaterial(player, usedIngredient); // 消耗材料
        PacketUtils.consumeSkillCharge(player, skill, false); //消耗充能并添加冷却事件进入队列

        int lightningCount = 1 + (level - 1) * 2;
        // 使用 usedIngredient 判断加成
        if (usedIngredient != null && usedIngredient.isPriority) {
            lightningCount += usedIngredient.bonusEffect;
        }

        HitResult hit = player.raycast(48.0, 0.0f, false);
        Vec3d targetPos = hit.getPos();
        World world = player.getWorld();

        for (int i = 0; i < lightningCount; i++) {
            final int delayMs = i * 200;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    player.getServer().execute(() -> {
                        if (player.isRemoved()) return;
                        double offsetX = (world.random.nextDouble() - 0.5) * 4.0;
                        double offsetZ = (world.random.nextDouble() - 0.5) * 4.0;
                        net.minecraft.entity.LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                        if (lightning != null) {
                            lightning.refreshPositionAfterTeleport(targetPos.x + offsetX, targetPos.y, targetPos.z + offsetZ);
                            world.spawnEntity(lightning);
                        }
                    });
                }
            }, delayMs);
        }
        return true;
    }

    // 设置摔落缓冲值的辅助方法
    public static void setFallDistanceCushion(ServerPlayerEntity player, float cushion) {
        ((IEntityDataSaver) player).getPersistentData().putFloat("fall_cushion", cushion);
    }

    private static void spawnParticles(ServerPlayerEntity player, int count) {
        ((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY(), player.getZ(), count, 0.2, 0.2, 0.2, 0.1);
    }

    // 闪现 (Blink)
    public static boolean executeBlink(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        World world = player.getWorld();
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();

        // 获取当前槽位的充能数，用于判断
        int currentCharges = 0;
        if (nbt.contains("active_skill_slots", 9)) {
            NbtList slots = nbt.getList("active_skill_slots", 10);
            for(int i=0; i<slots.size(); i++) {
                if(slots.getCompound(i).getString("id").equals(skill.id)) {
                    currentCharges = slots.getCompound(i).getInt("charges");
                    break;
                }
            }
        }

        if (isSecondary) {
            long now = world.getTime();

            // 检查是否处于 "回溯窗口期"
            if (nbt.contains("blink_recall_deadline")) {
                ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
                if (usedIngredient == null && !player.isCreative()) {
                    player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                    return false;
                }
                consumeMaterial(player, usedIngredient);

                // === 触发回溯 (第二次按键) ===
                double x = nbt.getDouble("blink_recall_x");
                double y = nbt.getDouble("blink_recall_y");
                double z = nbt.getDouble("blink_recall_z");
                String dim = nbt.getString("blink_recall_dim");

                if (world.getRegistryKey().getValue().toString().equals(dim)) {
                    performTeleport(player, x, y, z);
                    // 回溯成功后清空速度矢量
                    player.setVelocity(0, 0, 0);
                    player.velocityModified = true;
                    // 重置掉落距离以防摔死
                    player.fallDistance = 0;
                    player.sendMessage(Text.translatable("message.ascension.blink_recall").formatted(Formatting.LIGHT_PURPLE), true);
                } else {
                    player.sendMessage(Text.translatable("message.ascension.blink_dim_mismatch").formatted(Formatting.RED), true);
                }

                // 清除回溯数据
                nbt.remove("blink_recall_deadline");
                nbt.remove("blink_recall_x");
                nbt.remove("blink_recall_y");
                nbt.remove("blink_recall_z");
                nbt.remove("blink_recall_dim");
                // 立即清除 UI 上的持续时间条
                updateSkillSlotBus(player, skill.id, 0, 0);
                // 此时才真正消耗充能，并进入次要冷却
                PacketUtils.consumeSkillCharge(player, skill, true);

            } else {
                // === 标记位置 (第一次按键) ===
                // [检查] 如果充能数 < 1 (虽然没到消耗阶段，但必须有1个充能打底)，不允许使用
                if (currentCharges < 1) {
                    // 实际上 activeSkill.execute 外部可能已经防住了 0 充能，
                    // 但这里是双重保险
                    return false;
                }

                // 记录坐标
                nbt.putDouble("blink_recall_x", player.getX());
                nbt.putDouble("blink_recall_y", player.getY());
                nbt.putDouble("blink_recall_z", player.getZ());
                nbt.putString("blink_recall_dim", world.getRegistryKey().getValue().toString());

                // 设置8秒窗口
                int windowTicks = 160;
                long deadline = now + windowTicks;
                nbt.putLong("blink_recall_deadline", deadline);

                // 显示耐久条
                updateSkillSlotBus(player, skill.id, windowTicks, deadline);

                player.playSound(SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 0.5f, 1.5f);
                player.sendMessage(Text.translatable("message.ascension.blink_mark").formatted(Formatting.LIGHT_PURPLE), true);

                // [注意] 此处不调用 consumeSkillCharge，不消耗充能，也不进冷却
                // 仅仅是开启了窗口期
            }
            PacketUtils.syncSkillData(player);
            return true;
        }

        // [关键逻辑] 如果正在回溯窗口期 (blink_recall_deadline 存在)
        if (nbt.contains("blink_recall_deadline")) {
            // 此时必须保证充能 > 1，因为要留 1 个给次要效果结算
            if (currentCharges <= 1) {
                //player.sendMessage(Text.of("§c能量不足以维持回溯锚点！"), true);
                return false;
            }
        }

        // === 使用提取的方法 ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient); // 消耗
        PacketUtils.consumeSkillCharge(player, skill, false);

        double range = (level >= 3) ? 18.0 : 12.0;
        // 判断是否使用了强化材料 (末影珍珠)
        if (usedIngredient != null && usedIngredient.item == Items.ENDER_PEARL) {
            range *= 2.0;
        }

        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVector();
        Vec3d end = start.add(look.multiply(range));

        BlockHitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d targetVec = hit.getPos();

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            BlockPos safePos = findSafeSpotUpwards(world, hitPos, (int)range);
            if (safePos != null) {
                targetVec = Vec3d.ofBottomCenter(safePos);
            } else {
                targetVec = hit.getPos().subtract(look.multiply(0.5));
            }
        } else {
            BlockPos checkPos = BlockPos.ofFloored(targetVec);
            if (!world.getBlockState(checkPos).getCollisionShape(world, checkPos).isEmpty()) {
                BlockPos safePos = findSafeSpotUpwards(world, checkPos, 5);
                if (safePos != null) targetVec = Vec3d.ofBottomCenter(safePos);
            }
        }

        performTeleport(player, targetVec.x, targetVec.y, targetVec.z);

        // 视觉冲刺效果
        player.setVelocity(look.multiply(0.5));
        player.velocityModified = true;

        // 移除这里的 setSkillCooldown，让 ModMessages 统一处理
        return true;
    }

    // 辅助：执行传送与特效
    private static void performTeleport(ServerPlayerEntity player, double x, double y, double z) {
        Vec3d start = player.getPos();
        // 播放传送前音效
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        // 传送
        player.teleport(player.getServerWorld(), x, y, z, player.getYaw(), player.getPitch());
        // 播放传送后音效
        player.getWorld().playSound(null, x, y, z, net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        // 生成粒子 (服务器端生成，所有客户端可见)
        // 在起点和终点之间画线
        int particleCount = 20;
        Vec3d diff = new Vec3d(x, y, z).subtract(start);
        for (int i = 0; i <= particleCount; i++) {
            double delta = (double) i / particleCount;
            double px = start.x + diff.x * delta;
            double py = start.y + diff.y * delta + 0.5; // 稍微抬高
            double pz = start.z + diff.z * delta;
            ((ServerWorld)player.getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    // 辅助：向上寻找安全点
    private static BlockPos findSafeSpotUpwards(World world, BlockPos start, int maxSteps) {
        for (int i = 1; i <= maxSteps; i++) {
            BlockPos pos = start.up(i);
            if (canTeleportTo(world, pos)) {
                return pos;
            }
        }
        return null; // 没找到
    }

    // 辅助：判断位置是否可以站人 (脚下实心，身体和头空气)
    private static boolean canTeleportTo(World world, BlockPos pos) {
        // 简单判定：该位置空气，上方空气，下方是非空气
        boolean air1 = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
        boolean air2 = world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty();
        // 下方可以是任何方块，甚至是流体，只要不是虚空就行。或者严格点要求下方有碰撞箱。
        // 这里为了体验流畅，暂不强制下方必须有方块（允许空中闪现），只要求不卡住
        //return air1 && air2;

        // 如果你要求“必须落在方块上”，解开下面注释：
         boolean ground = !world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty();
         return air1 && air2 && ground;
    }

    // 不败金身
    public static boolean executeInvincibleBody(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        // 1. 消耗材料 (逻辑不变)
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);
        PacketUtils.consumeSkillCharge(player, skill, isSecondary);

        // 2. 计算时间
        int baseDuration = 80;
        int bonus = (usedIngredient != null && usedIngredient.isPriority) ? usedIngredient.bonusEffect : 0;
        int totalDuration = baseDuration + bonus;
        long now = player.getWorld().getTime();
        long endTime = now + totalDuration;

        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();

        // === 核心修改：写入槽位 NBT (通用总线) ===
        // 找到这个技能在哪个槽位，并写入 effect_total 和 effect_end
        updateSkillSlotBus(player, skill.id, totalDuration, endTime);

        if (isSecondary) {
            // 逻辑判定依然使用 Player Root NBT (因为 Mixin 读取这里最快，不用遍历槽位)
            nbt.putLong("invincible_status_end", endTime);
            // 统一音效调用
            SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.BUFF);
            player.sendMessage(Text.translatable("message.ascension.invincible_status_active").formatted(Formatting.GOLD), true);
        } else {
            nbt.putLong("invincible_damage_end", endTime);
            SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.DEFENSE);
            player.sendMessage(Text.translatable("message.ascension.invincible_damage_active").formatted(Formatting.GOLD), true);
        }

        PacketUtils.syncSkillData(player);
        return true;
    }

    // 辅助方法，更新槽位总线数据
    public static void updateSkillSlotBus(ServerPlayerEntity player, String skillId, int totalDuration, long endTime) {
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        if (!nbt.contains("active_skill_slots", 9)) return;

        net.minecraft.nbt.NbtList activeSlots = nbt.getList("active_skill_slots", 10);
        for (int i = 0; i < activeSlots.size(); i++) {
            NbtCompound slotNbt = activeSlots.getCompound(i);
            if (slotNbt.getString("id").equals(skillId)) {
                // 写入通用视觉键值对
                if (totalDuration != -1) slotNbt.putInt("effect_total", totalDuration);// 支持只更新 end
                slotNbt.putLong("effect_end", endTime);
                // 必须重新 set 一下以确保 NBT 标记为脏 (虽然 modify 引用通常有效，但 set 更保险)
                activeSlots.set(i, slotNbt);
                break;
            }
        }
        nbt.put("active_skill_slots", activeSlots);
    }

    // === 龙焰 (Dragon Breath) ===
    public static boolean executeDragonBreath(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        World world = player.getWorld();

        // === 次要效果：弹幕轰炸 (消耗 16 铜锭 + 16 火焰弹) ===
        if (isSecondary) {
            // 1. 检查材料 (特殊逻辑：同时需要两种)
            if (!player.isCreative()) {
                boolean hasCopper = checkMaterialCount(player, Items.COPPER_INGOT, 16);
                boolean hasCharge = checkMaterialCount(player, Items.FIRE_CHARGE, 16);

                if (!hasCopper || !hasCharge) {
                    player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                    return false;
                }

                // 消耗材料
                consumeMaterialCount(player, Items.COPPER_INGOT, 16);
                consumeMaterialCount(player, Items.FIRE_CHARGE, 16);
            }

            // 2. 修改冷却
            PacketUtils.consumeSkillCharge(player, skill, true);

            // 3. 连续发射逻辑
            int count = (level == 1) ? 9 : (level == 2) ? 13 : 16;

            // 使用 Timer 制造连发效果
            for (int i = 0; i < count; i++) {
                final int delay = i * 250; // 每 0.25秒一发
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        player.getServer().execute(() -> {
                            if (player.isRemoved()) return;
                            spawnDragonFireball(player, true, 0); // 精准发射
                        });
                    }
                }, delay);
            }
            return true;
        }

        // === 主要效果：散射 ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);
        PacketUtils.consumeSkillCharge(player, skill, false);

        // 计算数量
        int baseCount = (level == 1) ? 1 : (level == 2) ? 4 : 7;
        if (usedIngredient != null && usedIngredient.isPriority) {
            baseCount *= 2; // 恶魂之泪翻倍
        }

        // 发射
        for (int i = 0; i < baseCount; i++) {
            boolean isAccurate = (i == 0); // 第一发精准
            spawnDragonFireball(player, isAccurate, i * 0.15); // 后续增加偏移
        }

        // 播放龙息音效
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);

        return true;
    }

    // 辅助：生成并发射龙焰弹
    private static void spawnDragonFireball(ServerPlayerEntity player, boolean precise, double spreadBase) {
        World world = player.getWorld();
        Vec3d look = player.getRotationVector();
        //look = new Vec3d(look.x, 0, look.z).normalize();

        if (!precise) {
            double spreadX = world.random.nextGaussian() * spreadBase;
            double spreadZ = world.random.nextGaussian() * spreadBase;

            // 直接叠加到 Look 向量的 X 和 Z 分量上
            // 这样会使弹道在水平方向上产生随机偏转，而 Y 轴(垂直)方向保持相对稳定
            look = look.add(spreadX, 0, spreadZ).normalize();
        }

        // 恶魂吐的是 FireballEntity (爆炸)，烈焰人是 SmallFireballEntity (燃烧)
        Vec3d spawnPos = player.getEyePos().add(look.multiply(4.0));
        net.minecraft.entity.projectile.FireballEntity fireball = new net.minecraft.entity.projectile.FireballEntity(world, player, look.x, look.y, look.z, 1);
        // 设置位置到计算好的安全点
        fireball.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);

        // 设置速度 (保持水平)
        fireball.setVelocity(look.x * 2.0, look.y * 2.0, look.z * 2.0);
        world.spawnEntity(fireball);
    }

    // 辅助：检查特定物品数量
    private static boolean checkMaterialCount(ServerPlayerEntity player, net.minecraft.item.Item item, int required) {
        if (player.isCreative()) return true; // 创造模式直接通过
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtList list = data.getPersistentData().getList("casting_materials", NbtElement.COMPOUND_TYPE);
        int total = 0;
        for (NbtElement e : list) {
            ItemStack s = ItemStack.fromNbt((NbtCompound) e);
            if (s.isOf(item)) total += s.getCount();
        }
        return total >= required;
    }

    // 辅助：消耗特定物品数量 (跨格子)
    private static void consumeMaterialCount(ServerPlayerEntity player, net.minecraft.item.Item item, int amount) {
        if (player.isCreative()) return; // 创造模式不消耗
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtList list = data.getPersistentData().getList("casting_materials", NbtElement.COMPOUND_TYPE);
        int remaining = amount;

        for (int i = 0; i < list.size(); i++) {
            if (remaining <= 0) break;
            NbtCompound c = list.getCompound(i);
            ItemStack s = ItemStack.fromNbt(c);
            if (s.isOf(item)) {
                int take = Math.min(s.getCount(), remaining);
                s.decrement(take);
                remaining -= take;
                if (s.isEmpty()) list.set(i, new NbtCompound());
                else {
                    NbtCompound newC = new NbtCompound();
                    s.writeNbt(newC);
                    list.set(i, newC);
                }
            }
        }
        data.getPersistentData().put("casting_materials", list);
        PacketUtils.syncSkillData(player);
        player.currentScreenHandler.syncState();
    }

    // 光耀化身 (Radiant Avatar)
    public static boolean executeRadiantAvatar(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        // 次要效果：仅发光
        if (isSecondary) {
            // 1. 检查材料 (1根烈焰棒)
            if (!player.isCreative()) {
                if (!checkMaterialCount(player, Items.BLAZE_ROD, 1)) {
                    player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                    return false;
                }
                consumeMaterialCount(player, Items.BLAZE_ROD, 1);
            }

            // 2. 持续 45秒 (900 ticks)
            long duration = 900;
            long endTime = player.getWorld().getTime() + duration;

            // 写入 NBT (使用专用 Key)
            IEntityDataSaver data = (IEntityDataSaver) player;
            data.getPersistentData().putLong("radiant_light_end", endTime);

            // 更新槽位显示
            updateSkillSlotBus(player, skill.id, (int)duration, endTime);

            // CD: 45s
            PacketUtils.consumeSkillCharge(player, skill, true);

            SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.BUFF);
            player.sendMessage(Text.translatable("message.ascension.radiant_light_active").formatted(Formatting.YELLOW), true);
            PacketUtils.syncSkillData(player);
            return true;
        }

        // 主要效果：亡灵杀手光环
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        // 1. 消耗材料 (3根烈焰棒)
        if (!player.isCreative()) {
            if (!checkMaterialCount(player, Items.BLAZE_ROD, 3)) {
                player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                return false;
            }
            consumeMaterialCount(player, Items.BLAZE_ROD, 3);
        }

        // 2. 持续 12秒 (240 ticks)
        int duration = 240;
        long endTime = player.getWorld().getTime() + duration;

        IEntityDataSaver data = (IEntityDataSaver) player;
        // 设置光环结束时间
        data.getPersistentData().putLong("radiant_damage_end", endTime);

        // 更新槽位显示
        updateSkillSlotBus(player, skill.id, duration, endTime);

        // CD: 45s
        PacketUtils.consumeSkillCharge(player, skill, false);

        SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.ACTIVATE);
        player.sendMessage(Text.translatable("message.ascension.radiant_damage_active").formatted(Formatting.GOLD), true);
        PacketUtils.syncSkillData(player);

        return true;
    }

    // === 斗转星移 ===
    public static boolean executeStarShift(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        // === 次要效果：万象天引·止 (停止弹射物) ===
        if (isSecondary) {
            ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
            if (usedIngredient == null && !player.isCreative()) {
                player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                return false;
            }
            consumeMaterial(player, usedIngredient);
            // 1. 获取范围内的弹射物 (16格)
            World world = player.getWorld();
            double range = 16.0;
            List<ProjectileEntity> projectiles = world.getEntitiesByClass(
                    net.minecraft.entity.projectile.ProjectileEntity.class,
                    player.getBoundingBox().expand(range),
                    p -> true // 所有弹射物
            );

            if (projectiles.isEmpty()) {
                player.sendMessage(Text.translatable("message.ascension.no_projectiles").formatted(Formatting.YELLOW), true);
                return false;
            }

            // 2. 冻结它们
            for (net.minecraft.entity.projectile.ProjectileEntity p : projectiles) {
                p.setVelocity(0, 0, 0);
                p.setNoGravity(true);
                p.velocityModified = true; // 强制更新客户端
            }

            // 3. 播放音效
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.5f);

            // 4. 延迟销毁 (4秒后)
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    player.getServer().execute(() -> {
                        for (net.minecraft.entity.projectile.ProjectileEntity p : projectiles) {
                            if (p.isAlive()) {
                                p.discard(); // 销毁
                                // 播放一点特效
                                ((ServerWorld) world).spawnParticles(ParticleTypes.POOF, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
                            }
                        }
                    });
                }
            }, 4000); // 4000ms = 4s

            // 设定次要技能冷却 (例如 30秒)
            PacketUtils.consumeSkillCharge(player, skill, true);
            return true;
        }

        // === 主要效果：斗转星移 (时间加速) ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);

        // [特殊修改] 斗转星移的主要效果冷却时间受施法材料影响
        // ActiveSkill 注册的只是默认值，我们需要根据材料动态调整
        // 方案：调用 consumeSkillCharge 之前先手动修改 ActiveSkill 对象（不推荐，会污染全局），
        // 或者 PacketUtils 增加 override 参数？
        // 鉴于 consumeSkillCharge 是通用的，我们可以先调用它，然后再手动“修正”最后一次冷却时间。
        PacketUtils.consumeSkillCharge(player, skill, false); // 先按默认扣除
        // 修正逻辑：如果用了钻石，减少冷却
        if (usedIngredient != null && usedIngredient.isPriority) {
            // 这里我们需要重新设置一下刚刚那个槽位的冷却时间
            // 因为 consumeSkillCharge 已经把冷却加上去了（可能是进队列，可能是设为 cooldown_end）
            // 这是一个 Edge Case，为了代码整洁，建议在 ActiveSkill 里就把逻辑写好，但目前我们只能 patch。
            // 简单处理：直接覆盖 setSkillCooldown
            PacketUtils.setSkillCooldown(player, skill.id, 30 * 20);
        }

        // 2. 激活全局加速状态
        // 我们在 SkillEffectHandler 里处理具体的 tick 逻辑
        SkillEffectHandler.activateTimeAcceleration(player.getServer().getOverworld(), 30 * 20); // 持续 30秒

        // 3. 视觉与听觉反馈
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.5f);
        player.sendMessage(Text.translatable("message.ascension.star_shift_active").formatted(Formatting.AQUA), true);

        // 同步状态用于 UI 显示 (复用 invicible_status_end 等通用槽位显示逻辑，或者新增一个)
        // 这里简单复用 updateSkillSlotBus 来显示持续时间
        long now = player.getWorld().getTime();
        updateSkillSlotBus(player, skill.id, 30 * 20, now + 30 * 20);
        PacketUtils.syncSkillData(player);

        return true;
    }

    // === 怨灵之怒 (Wraith's Wrath) ===
    public static boolean executeWraithWrath(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        // 预先检查：是否使用了瞬间伤害药箭进行增幅
        // 我们需要模拟 consumeMaterial 的查找逻辑来检查药水类型
        boolean isBoosted = false;

        // 只有当背包里确实有材料时才检查（这里不负责报错，报错交给后面的 findMaterial/consumeMaterial）
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtList materialList = dataSaver.getPersistentData().getList("casting_materials", 10); // 10 = Compound

        for (int i = 0; i < materialList.size(); i++) {
            ItemStack stack = ItemStack.fromNbt(materialList.getCompound(i));
            // 怨灵之怒消耗的是 TIPPED_ARROW (根据 SkillRegistry 注册信息)
            if (stack.isOf(Items.TIPPED_ARROW) && stack.getCount() >= 1) {
                Potion potion = PotionUtil.getPotion(stack);
                if (potion == Potions.HARMING || potion == Potions.STRONG_HARMING) {
                    isBoosted = true;
                }
                break; // consumeMaterial 总是消耗找到的第一个符合条件的物品，所以我们检查第一个即可
            }
        }

        // === 次要效果：怨灵之视 (单体伤害) ===
        if (isSecondary) {
            ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
            if (usedIngredient == null && !player.isCreative()) {
                player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                return false;
            }
            consumeMaterial(player, usedIngredient);
            double range = 36.0;
            Vec3d start = player.getEyePos();
            Vec3d look = player.getRotationVector();
            Vec3d end = start.add(look.multiply(range));

            // 射线检测实体
            Box box = player.getBoundingBox().expand(range);
            EntityHitResult hit = ProjectileUtil.raycast(player, start, end, box,
                    e -> !e.isSpectator() && e.isAlive() && e != player, range * range);

            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                // 造成 4 点魔法伤害
                float damage = isBoosted ? 8.0f : 4.0f;
                target.damage(player.getDamageSources().magic(), damage);

                // 特效
                ((ServerWorld)player.getWorld()).spawnParticles(ParticleTypes.SCULK_SOUL,
                        target.getX(), target.getBodyY(0.5), target.getZ(),
                        5, 0.1, 0.1, 0.1, 0.05);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_VEX_HURT, SoundCategory.PLAYERS, 1.0f, 1.5f);

                if (isBoosted) {
                    // 手动消耗一根药箭以获得增幅
                    consumeMaterialCount(player, Items.TIPPED_ARROW, 1);
                    //player.sendMessage(Text.of("§5[怨灵] §d药箭增幅生效！"), true);
                }

                // 次要冷却
                PacketUtils.consumeSkillCharge(player, skill, true);
                return true;
            } else {
                player.sendMessage(Text.translatable("message.ascension.wraith_no_target").formatted(Formatting.RED), true);
                return false;
            }
        }

        // === 主要效果：亡灵天灾 (范围AOE) ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);

        // 1. 设置蓄力状态
        // 蓄力逻辑改为使用耐久条
        long chargeTime = 80; // 4s
        long now = player.getWorld().getTime();
        long endTime = now + chargeTime;

        // 1. 设置 NBT 标记用于 ServerTick 判断
        NbtCompound nbt = ((IEntityDataSaver) player).getPersistentData();
        nbt.putLong("wraith_charging_end", endTime);
        if (isBoosted) {
            nbt.putBoolean("wraith_damage_boost", true);
            //player.sendMessage(Text.of("§5[怨灵] §d死灵能量已通过药箭增强..."), true);
        }

        // 2. 使用 updateSkillSlotBus 来显示绿色耐久条倒计时
        // 这样玩家就能看到图标下方有一个进度条在缩减
        updateSkillSlotBus(player, skill.id, (int)chargeTime, endTime);

        // 3. 播放开始音效
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_AGITATED, SoundCategory.PLAYERS, 1.0f, 0.5f);
        player.sendMessage(Text.translatable("message.ascension.wraith_charging").formatted(Formatting.DARK_PURPLE), true);

        return true;
    }

    // 岿然不动 (Steadfast)
    public static boolean executeSteadfast(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        if (isSecondary) {
            ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
            if (usedIngredient == null && !player.isCreative()) {
                player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                return false;
            }
            consumeMaterial(player, usedIngredient);
            // === 次要效果：生命提升 (2颗心) ===
            // 使用 true (secondary) 消耗充能
            PacketUtils.consumeSkillCharge(player, skill, true);
            // 给予 45秒 (900 ticks) 的伤害吸收效果，强度 0 (4点 = 2颗心)
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 900, 0));
            // 音效
            //SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.HEAL);
            //player.sendMessage(Text.translatable("message.ascension.steadfast_secondary").formatted(Formatting.GREEN), true);
            return true;
        }

        // === 主要效果：护甲衰减 ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);

        // 判定冷却：金锭减半
        int cooldown = 1800; // 90s (default)
        if (usedIngredient != null && usedIngredient.isPriority) {
            cooldown = 900; // 45s (half)
        }

        // 使用自定义冷却消耗充能
        PacketUtils.consumeSkillCharge(player, skill, false, cooldown);

        // 初始化护甲衰减逻辑
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        long now = player.getWorld().getTime();

        // 记录开始时间
        nbt.putLong("steadfast_start_time", now);

        // 持续时间: Lv1/2 = 15s (300 ticks), Lv3 = 30s (600 ticks)
        // 初始护甲 30，衰减速度 2/s 或 1/s
        int duration = (level >= 3) ? 600 : 300;
        long endTime = now + duration;

        // 更新耐久条显示
        updateSkillSlotBus(player, skill.id, duration, endTime);

        // 音效与特效
        SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.DEFENSE);
        player.sendMessage(Text.translatable("message.ascension.steadfast_active").formatted(Formatting.GOLD), true);

        // 立即触发一次属性更新，以便加上护甲
        SkillEffectHandler.updateSteadfastArmor(player);

        return true;
    }

    // 通用辅助方法：查找材料
    // 返回找到的第一个匹配的技能材料配置 (包含加成信息)，如果没找到返回 null
    private static ActiveSkill.CastIngredient findMaterial(ServerPlayerEntity player, ActiveSkill skill) {
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtList materialList = nbt.getList("casting_materials", NbtElement.COMPOUND_TYPE);

        // 遍历背包的 5 个格子 (从上到下优先级)
        for (int i = 0; i < materialList.size(); i++) {
            NbtCompound itemNbt = materialList.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(itemNbt);

            if (stack.isEmpty()) continue;

            // 遍历技能要求的所有材料选项
            for (ActiveSkill.CastIngredient ingredient : skill.ingredients) {
                if (stack.isOf(ingredient.item) && stack.getCount() >= ingredient.count) {
                    return ingredient; // 找到匹配，直接返回配置
                }
            }
        }
        return null;
    }

    // 酿造鸡尾酒 (Cocktail Brewing)
    public static boolean executeCocktailBrewing(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        if (isSecondary) {
            // === 次要效果：延长所有状态 ===
            ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
            if (usedIngredient == null && !player.isCreative()) {
                player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                return false;
            }
            consumeMaterial(player, usedIngredient);
            PacketUtils.consumeSkillCharge(player, skill, true);

            // 逻辑：总延长时间 30秒 (600 ticks)
            var effects = player.getStatusEffects();
            if (!effects.isEmpty()) {
                int totalBonus = 600;
                int bonusPerEffect = totalBonus / effects.size();

                // 由于 StatusEffectInstance 是不可变的，必须复制并重新添加
                // 必须先收集起来再修改，避免并发修改异常
                List<StatusEffectInstance> toUpdate = new ArrayList<>(effects);

                for (StatusEffectInstance instance : toUpdate) {
                    StatusEffectInstance newInstance = new StatusEffectInstance(
                            instance.getEffectType(),
                            instance.getDuration() + bonusPerEffect,
                            instance.getAmplifier(),
                            instance.isAmbient(),
                            instance.shouldShowParticles(),
                            instance.shouldShowIcon()
                    );
                    player.addStatusEffect(newInstance); // 覆盖旧的
                }
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 1.0f, 1.2f);
                player.sendMessage(Text.translatable("message.ascension.cocktail_extended").formatted(Formatting.GREEN), true);
            }
            return true;
        }

        // === 主要效果：随机鸡尾酒 ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);
        PacketUtils.consumeSkillCharge(player, skill, false);

        // 12秒 = 240 ticks
        int duration = 240;
        boolean isMaxLevel = (level >= 4);

        // 获取随机效果
        StatusEffect effect = getRandomEffect(player.getWorld().random, 0.75f); // 75% 正面
        boolean isBeneficial = effect.isBeneficial(); // 注意：低版本没有这个方法，未来适配时可以用 getCategory()

        player.addStatusEffect(new StatusEffectInstance(effect, duration, 0));
        Text name = effect.getName();
        player.sendMessage(Text.translatable("message.ascension.cocktail_drunk", name).formatted(isBeneficial ? Formatting.GREEN : Formatting.RED), true);

        // 满级特效：如果是负面，额外给一个正面
        if (isMaxLevel && !isBeneficial) {
            StatusEffect bonusEffect = getRandomEffect(player.getWorld().random, 1.0f); // 100% 正面
            player.addStatusEffect(new StatusEffectInstance(bonusEffect, duration, 0));
            player.sendMessage(Text.translatable("message.ascension.cocktail_bonus", bonusEffect.getName()).formatted(Formatting.GOLD), true);
        }

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_HONEY_BOTTLE_DRINK, SoundCategory.PLAYERS, 1.0f, 1.0f);

        return true;
    }

    // 辅助：获取随机效果
    private static StatusEffect getRandomEffect(net.minecraft.util.math.random.Random random, float positiveChance) {
        // 收集所有非瞬间效果
        List<StatusEffect> beneficial = new ArrayList<>();
        List<StatusEffect> harmful = new ArrayList<>();

        for (StatusEffect e : net.minecraft.registry.Registries.STATUS_EFFECT) {
            if (e.isInstant()) continue;
            // 判断有益/有害
            if (e.getCategory() == net.minecraft.entity.effect.StatusEffectCategory.BENEFICIAL) beneficial.add(e);
            else if (e.getCategory() == net.minecraft.entity.effect.StatusEffectCategory.HARMFUL) harmful.add(e);
        }

        if (random.nextFloat() < positiveChance) {
            return beneficial.get(random.nextInt(beneficial.size()));
        } else {
            return harmful.get(random.nextInt(harmful.size()));
        }
    }

    // 虚空之触 (Void Touch)
    public static boolean executeVoidTouch(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        World world = player.getWorld();

        // 1. 检查材料
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);

        // 判断是否使用了末影之眼 (冷却减半)
        boolean halfCooldown = (usedIngredient != null && usedIngredient.isPriority);

        // 射线检测 (48米)
        double range = 48.0;
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVector();
        Vec3d end = start.add(look.multiply(range));
        BlockHitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));

        if (hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.translatable("message.ascension.void_touch_fail").formatted(Formatting.GRAY), true);
            return false; // 未命中不消耗充能？通常技能如果空放建议返还，这里简单起见视作失败
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);

        // === 次要效果：远程放置 ===
        if (isSecondary) {
            // 计算放置位置
            BlockPos placePos = pos.offset(hit.getSide());
            ItemStack handStack = player.getMainHandStack();

            if (handStack.isEmpty() || !(handStack.getItem() instanceof BlockItem)) {
                //player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
                return false;
            }

            // 尝试放置
            ItemPlacementContext context = new ItemPlacementContext(player, Hand.MAIN_HAND, handStack, hit);
            ActionResult result = ((BlockItem) handStack.getItem()).place(context);

            if (result.isAccepted()) {
                // 计算冷却: 12s
                int cooldown = 12 * 20;
                if (halfCooldown) cooldown /= 2;

                PacketUtils.consumeSkillCharge(player, skill, true, cooldown);

                // 特效
                ((ServerWorld) world).spawnParticles(ParticleTypes.PORTAL, placePos.getX()+0.5, placePos.getY()+0.5, placePos.getZ()+0.5, 10, 0.2, 0.2, 0.2, 0.1);
                world.playSound(null, placePos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.5f, 1.2f);
                return true;
            } else {
                return false;
            }
        }

        // === 主要效果：远程破坏 ===

        // 硬度检查 (-1 为基岩等不可破坏方块)
        float hardness = state.getHardness(world, pos);
        if (hardness < 0) {
            player.sendMessage(Text.translatable("message.ascension.void_touch_fail").formatted(Formatting.RED), true);
            return false;
        }

        // 1. 获取掉落物 (模拟主手工具挖掘)
        ItemStack toolStack = player.getMainHandStack();
        LootContextParameterSet.Builder builder = (new LootContextParameterSet.Builder((ServerWorld)world))
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .add(LootContextParameters.TOOL, toolStack) // 传入工具以应用精准采集/时运/工具匹配逻辑
                .add(LootContextParameters.THIS_ENTITY, player)
                .add(LootContextParameters.BLOCK_STATE, state);

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity != null) builder.add(LootContextParameters.BLOCK_ENTITY, blockEntity);

        List<ItemStack> drops = state.getDroppedStacks(builder);

        // 2. 放入背包或丢弃
        for (ItemStack drop : drops) {
            if (!player.getInventory().insertStack(drop)) {
                player.dropItem(drop, false); // 背包满则丢出
            }
        }

        // 3. 销毁方块
        world.removeBlock(pos, false); // false = 不产生掉落物 (因为已经手动给了)
        world.emitGameEvent(player, GameEvent.BLOCK_DESTROY, pos);

        // 4. 扣除工具耐久
        if (!toolStack.isEmpty() && toolStack.isDamageable()) {
            toolStack.damage(1, player, (p) -> p.sendToolBreakStatus(Hand.MAIN_HAND));
        }

        // 5. 计算冷却：(12 + 硬度) 秒
        int cooldownTicks = (int) ((12.0f + hardness) * 20.0f);
        if (halfCooldown) cooldownTicks /= 2;

        // 消耗充能并应用动态冷却
        PacketUtils.consumeSkillCharge(player, skill, false, cooldownTicks);

        // 6. 特效
        ((ServerWorld) world).spawnParticles(ParticleTypes.DRAGON_BREATH, pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, 15, 0.4, 0.4, 0.4, 0.05);
        world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f); // 破碎声

        return true;
    }

    // 鳍化 (Finification)
    public static boolean executeFinification(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        // 检查并消耗材料
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }
        consumeMaterial(player, usedIngredient);

        // 消耗充能
        PacketUtils.consumeSkillCharge(player, skill, isSecondary);

        // 强化判定：是否使用了河豚
        boolean isBoosted = (usedIngredient != null && usedIngredient.isPriority);

        if (isSecondary) {
            // === 次要效果：回复氧气 ===
            int currentAir = player.getAir();
            int maxAir = player.getMaxAir();
            // 基础回复 2格 (2 bubbles = 60 ticks of air), 强化回复 4格 (120 ticks)
            int restoreAmount = isBoosted ? 120 : 60;

            player.setAir(Math.min(maxAir, currentAir + restoreAmount));

            // 音效与气泡特效
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1.0f, 1.5f);
            ((ServerWorld)player.getWorld()).spawnParticles(ParticleTypes.BUBBLE,
                    player.getX(), player.getEyeY(), player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);

            return true;
        }

        // === 主要效果：开启游泳加速状态 ===
        long duration = isBoosted ? 800 : 400; // 40s 或 20s
        long now = player.getWorld().getTime();
        long endTime = now + duration;

        // 设置状态 NBT
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();

        nbt.putLong("finification_end", endTime);
        nbt.putInt("finification_level", level); // 记录释放时的等级以便 Tick 处理

        // 更新 UI
        updateSkillSlotBus(player, skill.id, (int)duration, endTime);

        // 给予初始速度爆发感
        if (player.isTouchingWater()) {
            player.setVelocity(player.getVelocity().multiply(1.5));
            player.velocityModified = true;
        }

        SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.ACTIVATE);
        player.sendMessage(Text.translatable("message.ascension.finification_active").formatted(Formatting.AQUA), true);
        PacketUtils.syncSkillData(player);

        return true;
    }

    // === 用于存放正在执行的裁决技能实例 ===
    public static final List<GiantSwordStrike> activeStrikes = new ArrayList<>();
    public static final Map<UUID, SwordVortex> activeVortices = new java.util.HashMap<>();

    // 每一 Tick 调用以演算技能实体
    public static void tickSkills(ServerPlayerEntity player) {
        //  垃圾回收：每秒(20 ticks)清理一次意外遗留的裁决之剑模型
        if (player.age % 20 == 0) {
            net.minecraft.util.math.Box box = player.getBoundingBox().expand(64.0);
            List<net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity> displays = player.getWorld().getEntitiesByClass(
                    net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity.class, box,
                    e -> e.getCommandTags().contains("ascension_judgment") // 只找我们打过标签的实体
            );

            for (var display : displays) {
                boolean isManaged = false;
                for (SwordVortex v : activeVortices.values()) {
                    if (v.swords.contains(display)) { isManaged = true; break; }
                }
                if (!isManaged) {
                    for (GiantSwordStrike s : activeStrikes) {
                        if (s.display == display) { isManaged = true; break; }
                    }
                }
                // 如果发现这个实体既不在活跃的风暴里，也不在活跃的天降巨剑里，说明是残留的，删掉它！
                if (!isManaged) {
                    display.discard();
                }
            }
        }
        // 1. 演算该玩家拥有的剑刃风暴
        SwordVortex vortex = activeVortices.get(player.getUuid());
        if (vortex != null) {
            if (!vortex.tick(player)) {
                activeVortices.remove(player.getUuid());
            }
        }

        // 2. 演算该玩家召唤的天降巨剑
        java.util.Iterator<GiantSwordStrike> it = activeStrikes.iterator();
        while (it.hasNext()) {
            GiantSwordStrike strike = it.next();
            if (strike.owner.getUuid().equals(player.getUuid())) {
                if (!strike.tick()) {
                    it.remove();
                }
            }
        }
    }

    // === 裁决之剑 (Sword of Judgment) ===
    public static boolean executeSwordOfJudgment(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        World world = player.getWorld();

        // 检查材料
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }

        if (isSecondary) {
            // === 次要效果：剑刃漩涡 ===
            consumeMaterial(player, usedIngredient);
            PacketUtils.consumeSkillCharge(player, skill, true);

            net.minecraft.item.Item[] tiers = { Items.WOODEN_SWORD, Items.STONE_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD };
            int bestTier = 0;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                for (int t = 0; t < tiers.length; t++) {
                    if (stack.isOf(tiers[t]) && t > bestTier) {
                        bestTier = t;
                    }
                }
            }

            SwordVortex vortex = new SwordVortex();
            vortex.owner = player;
            vortex.bestTier = bestTier;
            int count = Math.max(40, Math.min(60, (int)(world.random.nextGaussian() * 5 + 50)));
            vortex.remainingSwords = count;

            for (int i = 0; i < count; i++) {
                net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity sword = net.minecraft.entity.EntityType.ITEM_DISPLAY.create(world);
                if (sword != null) {
                    int randomTier = world.random.nextInt(bestTier + 1);
                    sword.setPosition(player.getX(), player.getY() + 1, player.getZ());

                    // 【关键修复1】必须先生成实体，后注入 NBT 才能强行同步给客户端
                    world.spawnEntity(sword);
                    vortex.swords.add(sword);

                    sword.addCommandTag("ascension_judgment");
                    NbtCompound nbt = new NbtCompound();
                    sword.writeNbt(nbt);

                    NbtCompound itemNbt = new NbtCompound();
                    new ItemStack(tiers[randomTier]).writeNbt(itemNbt);
                    nbt.put("item", itemNbt);
                    nbt.putString("item_display", "fixed");

                    // 补全所有变换数据，缺一不可
                    NbtCompound transformNbt = new NbtCompound();

                    net.minecraft.nbt.NbtList scaleList = new net.minecraft.nbt.NbtList();
                    scaleList.add(net.minecraft.nbt.NbtFloat.of(0.6f)); // 剑刃风暴尺寸
                    scaleList.add(net.minecraft.nbt.NbtFloat.of(0.6f));
                    scaleList.add(net.minecraft.nbt.NbtFloat.of(0.6f));
                    transformNbt.put("scale", scaleList);

                    // 剑刃风暴旋转：逆时针转 -45度（水平向右）
                    net.minecraft.nbt.NbtList rotList = new net.minecraft.nbt.NbtList();
                    rotList.add(net.minecraft.nbt.NbtFloat.of(0f));
                    rotList.add(net.minecraft.nbt.NbtFloat.of(0f));
                    rotList.add(net.minecraft.nbt.NbtFloat.of(-0.38268f)); // Z
                    rotList.add(net.minecraft.nbt.NbtFloat.of(0.92388f));  // W
                    transformNbt.put("left_rotation", rotList);

                    // 补充默认的平移和右旋转防覆盖
                    net.minecraft.nbt.NbtList transList = new net.minecraft.nbt.NbtList();
                    transList.add(net.minecraft.nbt.NbtFloat.of(0f)); transList.add(net.minecraft.nbt.NbtFloat.of(0f)); transList.add(net.minecraft.nbt.NbtFloat.of(0f));
                    transformNbt.put("translation", transList);
                    net.minecraft.nbt.NbtList rightRotList = new net.minecraft.nbt.NbtList();
                    rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f)); rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f)); rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f)); rightRotList.add(net.minecraft.nbt.NbtFloat.of(1f));
                    transformNbt.put("right_rotation", rightRotList);

                    nbt.put("transformation", transformNbt);
                    nbt.putInt("interpolation_duration", 0); // 必须为0以瞬间生效
                    sword.readNbt(nbt);
                }
            }
            activeVortices.put(player.getUuid(), vortex);

            updateSkillSlotBus(player, skill.id, 300, world.getTime() + 300);
            PacketUtils.syncSkillData(player);
            SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.ACTIVATE);
            return true;

        } else {
            // === 主要效果：天降巨剑 ===
            HitResult hit = player.raycast(100.0, 0.0f, false);
            BlockPos targetPos = BlockPos.ofFloored(hit.getPos());

            if (!world.isSkyVisible(targetPos.up())) {
                player.sendMessage(Text.translatable("message.ascension.sword_of_judgment_blocked").formatted(Formatting.RED), true);
                return false;
            }

            consumeMaterial(player, usedIngredient);
            PacketUtils.consumeSkillCharge(player, skill, false);

            GiantSwordStrike strike = new GiantSwordStrike();
            strike.owner = player;
            strike.target = targetPos;
            strike.currentY = 319.0f;
            strike.isTrident = world.isThundering();

            net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity display = net.minecraft.entity.EntityType.ITEM_DISPLAY.create(world);
            if (display != null) {
                ItemStack dropItem = new ItemStack(strike.isTrident ? Items.TRIDENT : Items.DIAMOND_SWORD);
                if (strike.isTrident) {
                    net.minecraft.enchantment.EnchantmentHelper.enchant(world.random, dropItem, 30, true);
                }
                strike.dropItem = dropItem;

                display.setPosition(targetPos.getX() + 0.5, strike.currentY, targetPos.getZ() + 0.5);

                // 【关键修复2】先生成实体，后写入 NBT
                world.spawnEntity(display);
                strike.display = display;
                activeStrikes.add(strike);

                display.addCommandTag("ascension_judgment");
                NbtCompound nbt = new NbtCompound();
                display.writeNbt(nbt);

                NbtCompound itemNbt = new NbtCompound();
                dropItem.writeNbt(itemNbt);
                nbt.put("item", itemNbt);
                nbt.putString("item_display", "fixed");

                NbtCompound transformNbt = new NbtCompound();

                net.minecraft.nbt.NbtList scaleList = new net.minecraft.nbt.NbtList();
                scaleList.add(net.minecraft.nbt.NbtFloat.of(40.0f));
                scaleList.add(net.minecraft.nbt.NbtFloat.of(40.0f));
                scaleList.add(net.minecraft.nbt.NbtFloat.of(40.0f));
                transformNbt.put("scale", scaleList);

                // 天降巨剑旋转：逆时针转 135度（竖直向下）
                net.minecraft.nbt.NbtList rotList = new net.minecraft.nbt.NbtList();
                rotList.add(net.minecraft.nbt.NbtFloat.of(0f));
                rotList.add(net.minecraft.nbt.NbtFloat.of(0f));
                rotList.add(net.minecraft.nbt.NbtFloat.of(-0.92388f));  // Z
                rotList.add(net.minecraft.nbt.NbtFloat.of(0.38268f));  // W
                transformNbt.put("left_rotation", rotList);

                // 补充默认的平移和右旋转防覆盖
                net.minecraft.nbt.NbtList transList = new net.minecraft.nbt.NbtList();
                transList.add(net.minecraft.nbt.NbtFloat.of(0f)); transList.add(net.minecraft.nbt.NbtFloat.of(0f)); transList.add(net.minecraft.nbt.NbtFloat.of(0f));
                transformNbt.put("translation", transList);
                net.minecraft.nbt.NbtList rightRotList = new net.minecraft.nbt.NbtList();
                rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f)); rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f)); rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f)); rightRotList.add(net.minecraft.nbt.NbtFloat.of(1f));
                transformNbt.put("right_rotation", rightRotList);

                nbt.put("transformation", transformNbt);
                nbt.putInt("interpolation_duration", 0);
                display.readNbt(nbt);
            }
            return true;
        }
    }

    // === 天降巨剑演算类 ===
    public static class GiantSwordStrike {
        ServerPlayerEntity owner;
        BlockPos target;
        net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity display;
        ItemStack dropItem;
        float currentY;
        float velocity = 0.5f;
        boolean isTrident;
        int phase = 0; // 0=下落, 1=缩小
        int shrinkTicks = 0;

        public boolean tick() {
            World world = owner.getWorld();
            if (display == null || display.isRemoved()) return false;

            if (phase == 0) {
                velocity += 0.2f;
                currentY -= velocity;

                // [修改] 目标落地高度：地面高度 + 巨剑当前尺寸的一半 (40倍 / 2 = 20格) 因为旋转所以需要×根号二
                float groundY = target.getY() + 28.0f;

                if (currentY <= groundY) {
                    currentY = groundY;
                    display.requestTeleport(target.getX() + 0.5, currentY, target.getZ() + 0.5);

                    float fallDist = 319.0f - target.getY();
                    float dmg = Math.min(40.0f, (fallDist - 2.0f) * 2.0f);
                    if (dmg < 0) dmg = 0;

                    Box box = new Box(target).expand(3.0);
                    List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != owner);
                    for (LivingEntity e : targets) {
                        e.damage(world.getDamageSources().fallingAnvil(display), dmg);
                    }

                    world.playSound(null, target, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0f, 0.5f);
                    if (isTrident) {
                        world.playSound(null, target, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2.0f, 1.0f);
                        ((ServerWorld) world).spawnParticles(ParticleTypes.FLASH, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 1, 0, 0, 0, 0);
                    }
                    ((ServerWorld) world).spawnParticles(ParticleTypes.EXPLOSION, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 10, 2, 0, 2, 0.1);

                    phase = 1;
                    shrinkTicks = 100;
                } else {
                    display.requestTeleport(target.getX() + 0.5, currentY, target.getZ() + 0.5);
                }
            } else if (phase == 1) {
                shrinkTicks--;

                float scale = (shrinkTicks / 100.0f) * 40.0f;
                if (scale < 1.0f) scale = 1.0f;

                // [修改] 随着巨剑缩小，动态下调它的高度，让剑尖始终贴在地面！
                display.requestTeleport(target.getX() + 0.5, target.getY() + (scale / 1.414f), target.getZ() + 0.5);

                NbtCompound nbt = new NbtCompound();
                display.writeNbt(nbt);

                NbtCompound transformNbt = new NbtCompound();

                net.minecraft.nbt.NbtList scaleList = new net.minecraft.nbt.NbtList();
                scaleList.add(net.minecraft.nbt.NbtFloat.of(scale));
                scaleList.add(net.minecraft.nbt.NbtFloat.of(scale));
                scaleList.add(net.minecraft.nbt.NbtFloat.of(scale));
                transformNbt.put("scale", scaleList);

                // [修改] 缩小过程中的旋转也必须同步修改为 225 度
                org.joml.Quaternionf q = new org.joml.Quaternionf().rotationXYZ(
                        0f, 0f, (float) Math.toRadians(225)
                );
                net.minecraft.nbt.NbtList rotList = new net.minecraft.nbt.NbtList();
                rotList.add(net.minecraft.nbt.NbtFloat.of(q.x()));
                rotList.add(net.minecraft.nbt.NbtFloat.of(q.y()));
                rotList.add(net.minecraft.nbt.NbtFloat.of(q.z()));
                rotList.add(net.minecraft.nbt.NbtFloat.of(q.w()));
                transformNbt.put("left_rotation", rotList);

                net.minecraft.nbt.NbtList transList = new net.minecraft.nbt.NbtList();
                transList.add(net.minecraft.nbt.NbtFloat.of(0f));
                transList.add(net.minecraft.nbt.NbtFloat.of(0f));
                transList.add(net.minecraft.nbt.NbtFloat.of(0f));
                transformNbt.put("translation", transList);
                net.minecraft.nbt.NbtList rightRotList = new net.minecraft.nbt.NbtList();
                rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f));
                rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f));
                rightRotList.add(net.minecraft.nbt.NbtFloat.of(0f));
                rightRotList.add(net.minecraft.nbt.NbtFloat.of(1f));
                transformNbt.put("right_rotation", rightRotList);

                nbt.put("transformation", transformNbt);

                nbt.putInt("interpolation_duration", 10);
                nbt.putInt("start_interpolation", 0);
                nbt.putInt("teleport_duration", 10);

                display.readNbt(nbt);

                if (shrinkTicks <= 0) {
                    display.discard();
                    net.minecraft.entity.ItemEntity drop = new net.minecraft.entity.ItemEntity(world, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, this.dropItem);
                    drop.setToDefaultPickupDelay();
                    world.spawnEntity(drop);
                    return false;
                }
            }
            return true;
        }
    }

    // === 剑刃漩涡演算类 ===
    public static class SwordVortex {
        ServerPlayerEntity owner;
        List<net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity> swords = new ArrayList<>();
        int remainingSwords;
        int bestTier;
        int ticksLeft = 300; // 15秒 (300 ticks)

        public boolean tick(ServerPlayerEntity player) {
            ticksLeft--;
            if (ticksLeft <= 0 || remainingSwords <= 0 || !player.isAlive()) {
                swords.forEach(net.minecraft.entity.Entity::discard);
                return false;
            }

            // 丢弃多余的剑模型
            while (swords.size() > remainingSwords) {
                swords.get(swords.size() - 1).discard();
                swords.remove(swords.size() - 1);
            }

            double cx = player.getX();
            double cy = player.getY() + 1.0;
            double cz = player.getZ();

            // 更新剑的位置与旋转
            for (int i = 0; i < swords.size(); i++) {
                net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity sword = swords.get(i);
                double angle = (player.age * 12.0) + (360.0 / swords.size()) * i;
                double rad = Math.toRadians(angle);
                double x = cx + Math.cos(rad) * 5.0;
                double z = cz + Math.sin(rad) * 5.0;
                double y = cy + Math.sin(player.age * 0.15 + i) * 1.5 + 1.5f; // 上下浮动

                sword.requestTeleport(x, y, z);
                sword.setYaw((float) angle + 90f); // 剑尖顺着旋转方向
                sword.setPitch(0f);
            }

            // 碰撞检测
            World world = player.getWorld();
            Box box = player.getBoundingBox().expand(5.5);
            List<LivingEntity> enemies = world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

            int[] damages = { 4, 5, 4, 6, 7, 8 }; // 各材质基础伤害

            for (LivingEntity enemy : enemies) {
                if (enemy.timeUntilRegen <= 10 && enemy.distanceTo(player) <= 3.5) {
                    // 随机抽取一把剑的伤害
                    int randomTier = world.random.nextInt(bestTier + 1);
                    float dmg = damages[randomTier] / 1.5f;

                    enemy.damage(world.getDamageSources().magic(), dmg);
                    Vec3d kb = enemy.getPos().subtract(player.getPos()).normalize().multiply(0.5);
                    enemy.addVelocity(kb.x, 0.2, kb.z);
                    enemy.velocityModified = true;

                    remainingSwords -= world.random.nextBetween(1, 3);
                    world.playSound(null, enemy.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.2f);

                    if (remainingSwords <= 0) break;
                }
            }
            return true;
        }
    }

    // 猎手视觉
    public static boolean executeHunterVision(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        int level = PacketUtils.getSkillLevel(player, skill.id);
        if (level <= 0) return false;

        World world = player.getWorld();

        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);
        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }

        boolean isBoosted = (usedIngredient != null && usedIngredient.isPriority);

        if (isSecondary) {
            // === 次要效果：致盲发光生物 ===
            consumeMaterial(player, usedIngredient);
            PacketUtils.consumeSkillCharge(player, skill, true);

            // 寻找25格内具有发光效果的生物
            List<LivingEntity> targets = world.getEntitiesByClass(
                    LivingEntity.class,
                    player.getBoundingBox().expand(25.0),
                    e -> e.isAlive() && e != player && e.hasStatusEffect(StatusEffects.GLOWING)
            );

            for (LivingEntity target : targets) {
                // 持续时间：4-6秒 (80-120 ticks)，强化增加 50% (120-180 ticks)
                int minTicks = isBoosted ? 120 : 80;
                int maxTicks = isBoosted ? 180 : 120;
                int duration = world.random.nextBetween(minTicks, maxTicks);

                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0));
                target.setAttacker(null); // 清除受击源，打断逃跑行为

                // 清除仇恨与逃跑AI
                if (target instanceof net.minecraft.entity.mob.MobEntity mob) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
            }

            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return true;
        }

        // === 主要效果：开启猎手视觉 ===
        consumeMaterial(player, usedIngredient);
        PacketUtils.consumeSkillCharge(player, skill, false);

        long duration = isBoosted ? 360 : 240; // 12秒 或 18秒 (强化+50%)
        long now = world.getTime();
        long endTime = now + duration;

        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        nbt.putLong("hunter_vision_end", endTime);
        nbt.putInt("hunter_vision_level", level);

        updateSkillSlotBus(player, skill.id, (int)duration, endTime);
        PacketUtils.syncSkillData(player);

        SkillSoundHandler.playSkillSound(player, SkillSoundHandler.SoundType.ACTIVATE);
        player.sendMessage(Text.translatable("message.ascension.hunter_vision_active").formatted(Formatting.GREEN), true);
        return true;
    }

    // === 通用辅助方法：消耗材料 ===
    private static void consumeMaterial(ServerPlayerEntity player, ActiveSkill.CastIngredient ingredient) {
        if (player.isCreative()) return; // 创造模式不消耗

        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();
        NbtList materialList = nbt.getList("casting_materials", NbtElement.COMPOUND_TYPE);

        // 再次查找并扣除 (为了代码解耦，这里重新遍历一次，由于只有5格，性能损耗可忽略)
        for (int i = 0; i < materialList.size(); i++) {
            NbtCompound itemNbt = materialList.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(itemNbt);

            if (stack.isOf(ingredient.item) && stack.getCount() >= ingredient.count) {
                stack.decrement(ingredient.count);

                // 更新 NBT
                if (stack.isEmpty()) {
                    materialList.set(i, new NbtCompound());
                } else {
                    NbtCompound newItemNbt = new NbtCompound();
                    stack.writeNbt(newItemNbt);
                    materialList.set(i, newItemNbt);
                }

                nbt.put("casting_materials", materialList);
                PacketUtils.syncSkillData(player);
                player.currentScreenHandler.syncState(); // 强制同步防止丢物品
                return; // 只消耗一份
            }
        }
    }
}