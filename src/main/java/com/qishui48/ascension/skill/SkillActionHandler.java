package com.qishui48.ascension.skill;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.MotionJumpUtils;
import com.qishui48.ascension.util.PacketUtils;
import com.qishui48.ascension.util.SkillSoundHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
        return air1 && air2;

        // 如果你要求“必须落在方块上”，解开下面注释：
        // boolean ground = !world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty();
        // return air1 && air2 && ground;
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
                slotNbt.putInt("effect_total", totalDuration);
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

    // === 光耀化身 (Radiant Avatar) ===
    public static boolean executeRadiantAvatar(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary) {
        // === 次要效果：仅发光 ===
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

        // === 主要效果：亡灵杀手光环 ===
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
                    player.sendMessage(Text.of("§5[怨灵] §d药箭增幅生效！"), true);
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
            // === 次要效果：生命提升 (2颗心) ===
            // 使用 true (secondary) 消耗充能
            PacketUtils.consumeSkillCharge(player, skill, true);
            // 给予 45秒 (900 ticks) 的伤害吸收效果，强度 0 (4点 = 2颗心)
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 900, 4));
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