package com.qishui48.ascension.skill;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.MotionJumpUtils;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

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
            PacketUtils.setSkillCooldown(player, skill.id, 100);
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

        consumeMaterial(player, usedIngredient); // 消耗

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

        if (isSecondary) {
            for (int i = 0; i < 10; i++) {
                double dx = (world.random.nextDouble() - 0.5) * 9.0;
                double dy = (world.random.nextDouble() - 0.5) * 9.0;
                double dz = (world.random.nextDouble() - 0.5) * 9.0;
                BlockPos targetPos = BlockPos.ofFloored(player.getX() + dx, player.getY() + dy, player.getZ() + dz);

                if (canTeleportTo(world, targetPos)) {
                    performTeleport(player, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                    PacketUtils.setSkillCooldown(player, skill.id, 100);
                    return true;
                }
            }
            player.sendMessage(Text.translatable("message.ascension.blink_fail").formatted(Formatting.RED), true);
            return false;
        }

        // === 使用提取的方法 ===
        ActiveSkill.CastIngredient usedIngredient = findMaterial(player, skill);

        if (usedIngredient == null && !player.isCreative()) {
            player.sendMessage(Text.translatable("message.ascension.no_material").formatted(Formatting.RED), true);
            return false;
        }

        consumeMaterial(player, usedIngredient); // 消耗

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

    // === 通用辅助方法：查找材料 ===
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