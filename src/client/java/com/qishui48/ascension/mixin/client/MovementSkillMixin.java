package com.qishui48.ascension.mixin.client;

import com.qishui48.ascension.network.ModMessages;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.MotionJumpUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MovementSkillMixin {

    private int chargeTicks = 0;
    private int boostCooldown = 0;
    private boolean wasCharging = false;
    private boolean jumpKeyReleasedInAir = false;

    // === 新增：跳跃计数器 ===
    // 0 = 在地面, 1 = 第一段(原版跳), 2 = 第二段, 3 = 第三段...
    private int currentJumpCount = 0;

    private static final int MAX_CHARGE = 30;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (boostCooldown > 0) boostCooldown--;

        // ==================================================
        // 1. 状态重置 (落地重置)
        // ==================================================
        if (player.isOnGround()) {
            jumpKeyReleasedInAir = false;
            currentJumpCount = 0; // 落地归零
        } else {
            // 空中松手检测
            if (!player.input.jumping) {
                jumpKeyReleasedInAir = true;
            }
        }

        // ==================================================
        // 2. 蓄力跳 (Charged Jump) - 保持不变
        // ==================================================
        // 注意：这里调用 getSkillLevel，如果返回 > 0 则为真
        boolean canCharge = player.isOnGround() && player.isSneaking() && getSkillLevel(player, "charged_jump") > 0;

        if (canCharge && player.input.jumping) {
            wasCharging = true;
            if (chargeTicks < MAX_CHARGE) chargeTicks++;
        }
        else if (wasCharging) {
            if (!player.input.jumping && chargeTicks > 5) {
                float powerRatio = (float) chargeTicks / MAX_CHARGE;
                sendPacket(ModMessages.CHARGED_JUMP_ID, powerRatio);

                int level = getSkillLevel(player,"charged_jump");
                Vec3d velocity = MotionJumpUtils.calculateChargedJumpVector(player.getPitch(), player.getYaw(), powerRatio, 2.6f * level);
                player.addVelocity(velocity.x, velocity.y, velocity.z);
                player.fallDistance = 0;
                boostCooldown = 10;
                jumpKeyReleasedInAir = false;
            }
            chargeTicks = 0;
            wasCharging = false;
        }
        else {
            wasCharging = false;
            chargeTicks = 0;
        }

        // ==================================================
        // 3. 多段跳 (Multi-Jump / Rocket Boost) - 核心修改
        // ==================================================
        if (!player.isOnGround() && player.input.jumping && boostCooldown == 0 && jumpKeyReleasedInAir) {

            if (isSkillActive(player, "rocket_boost")) {
                // 获取技能等级
                int level = getSkillLevel(player, "rocket_boost");

                if (level > 0) {
                    // 计算允许的最大跳跃次数
                    // Lv1 = 二段跳 (空中跳1次) -> 原版跳(1) + 额外(1) = 2
                    // Lv2 = 三段跳 (空中跳2次) -> 原版跳(1) + 额外(2) = 3
                    int maxJumps = 1 + level;

                    // 如果还没用完次数
                    // 注意：currentJumpCount 在离地瞬间其实至少是 1 (因为原版跳跃已经算一次了)
                    // 如果是走下悬崖的，currentJumpCount 是 0，所以也能触发
                    if (currentJumpCount < maxJumps) {

                        sendPacket(ModMessages.JUMP_REQUEST_ID, 0);

                        boostCooldown = 4; // 稍微加点冷却防止按太快连发
                        jumpKeyReleasedInAir = false; // 必须松手才能跳下一段

                        // 计数 +1 (如果是走下悬崖第一次跳，变为1；如果是二段跳，变为2)
                        if (currentJumpCount == 0) currentJumpCount = 1;
                        currentJumpCount++;
                    }
                }
            }
        }
    }

    private void sendPacket(net.minecraft.util.Identifier id, float value) {
        var buf = PacketByteBufs.create();
        if (value != 0) buf.writeFloat(value);
        ClientPlayNetworking.send(id, buf);
    }

    // === 适配新的 NBT 数据结构 ===
    private int getSkillLevel(ClientPlayerEntity player, String id) {
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        // 现在要读 "skill_levels" (int) 而不是 "unlocked_skills" (boolean)
        if (nbt.contains("skill_levels")) {
            return nbt.getCompound("skill_levels").getInt(id);
        }
        return 0;
    }

    // 判断技能是否有效（解锁 且 未禁用）
    private boolean isSkillActive(ClientPlayerEntity player, String id) {
        // 1. 获取数据
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();

        // 2. 检查是否解锁 (等级 > 0)
        boolean unlocked = nbt.contains("skill_levels") && nbt.getCompound("skill_levels").getInt(id) > 0;
        if (!unlocked) return false;

        // 3. 检查是否被禁用 (核心逻辑)
        if (nbt.contains("disabled_skills") && nbt.getCompound("disabled_skills").getBoolean(id)) {
            return false; // 虽然解锁了，但被玩家停用了
        }

        return true;
    }
}