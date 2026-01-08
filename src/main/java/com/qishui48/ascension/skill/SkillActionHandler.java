package com.qishui48.ascension.skill;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.MotionJumpUtils;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class SkillActionHandler {

    // 处理推进跳跃 (Boost)
    public static void executeBoost(ServerPlayerEntity player) {
        // [新增] 检查游戏模式
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

    // === 火箭跳 (蓄力跳) ===
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

    // 设置摔落缓冲值的辅助方法
    public static void setFallDistanceCushion(ServerPlayerEntity player, float cushion) {
        ((IEntityDataSaver) player).getPersistentData().putFloat("fall_cushion", cushion);
    }

    private static void spawnParticles(ServerPlayerEntity player, int count) {
        ((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY(), player.getZ(), count, 0.2, 0.2, 0.2, 0.1);
    }
}