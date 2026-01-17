package com.qishui48.ascension.mixin.skills;

import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ShovelItem;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.nbt.NbtCompound;

@Mixin(BoatEntity.class)
public abstract class BoatEntityMixin extends Entity {

    // === Shadow 所有的按键状态 ===
    @Shadow private boolean pressingLeft;
    @Shadow private boolean pressingRight;
    @Shadow private boolean pressingForward; // 必须加上这个！

    @Unique private int durabilityTimer = 0;

    public BoatEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        BoatEntity boat = (BoatEntity) (Object) this;
        Entity passenger = boat.getFirstPassenger();

        if (passenger instanceof PlayerEntity player) {

            // 技能检查
            if (isSkillActiveCommon(player, "dragon_boat_rower")) {
                int level = getSkillLevelCommon(player, "dragon_boat_rower");

                boolean holdingShovel = player.getMainHandStack().getItem() instanceof ShovelItem ||
                        player.getOffHandStack().getItem() instanceof ShovelItem;

                if (holdingShovel) {
                    Vec3d velocity = boat.getVelocity();
                    boolean isMoving = Math.abs(player.forwardSpeed) > 0.0f || Math.abs(player.sidewaysSpeed) > 0.0f;

                    // === 加入 pressingForward 判断 ===
                    // 只有当玩家按了 (左 OR 右 OR 前) 键时，才视为正在划船
                    boolean isPaddling = this.pressingForward;

                    // 只有在划船 且 有速度 且 未超速时，才执行加速
                    // 例如 12.0f 是速度平方，约等于 3.46 blocks/tick (非常快)
                    if (isPaddling && velocity.horizontalLengthSquared() > 0.0001f && velocity.horizontalLengthSquared() < 4.0f) {
                        float multiplier = (level >= 2) ? 1.08f : 1.05f;
                        boat.setVelocity(velocity.multiply(multiplier, 1.0, multiplier));
                    }
                    // 耐久消耗逻辑 (仅服务端)
                    if(isMoving) {
                        if (!this.getWorld().isClient) {
                            durabilityTimer++;
                            if (durabilityTimer >= 40) {
                                durabilityTimer = 0;
                                if (player.getMainHandStack().getItem() instanceof ShovelItem) {
                                    player.getMainHandStack().damage(1, player, p -> p.sendToolBreakStatus(player.getActiveHand()));
                                } else if (player.getOffHandStack().getItem() instanceof ShovelItem) {
                                    player.getOffHandStack().damage(1, player, p -> p.sendToolBreakStatus(player.getActiveHand()));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 辅助方法保持不变
    @Unique
    private boolean isSkillActiveCommon(PlayerEntity player, String skillId) {
        if (!(player instanceof IEntityDataSaver)) return false;
        NbtCompound nbt = ((IEntityDataSaver) player).getPersistentData();
        boolean unlocked = nbt.contains("skill_levels") && nbt.getCompound("skill_levels").getInt(skillId) > 0;
        if (!unlocked) return false;
        if (nbt.contains("disabled_skills") && nbt.getCompound("disabled_skills").getBoolean(skillId)) {
            return false;
        }
        return true;
    }

    @Unique
    private int getSkillLevelCommon(PlayerEntity player, String skillId) {
        if (!(player instanceof IEntityDataSaver)) return 0;
        NbtCompound nbt = ((IEntityDataSaver) player).getPersistentData();
        if (nbt.contains("skill_levels")) {
            return nbt.getCompound("skill_levels").getInt(skillId);
        }
        return 0;
    }
}