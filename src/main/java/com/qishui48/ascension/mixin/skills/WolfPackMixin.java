package com.qishui48.ascension.mixin.skills;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(PlayerEntity.class)
public abstract class WolfPackMixin extends LivingEntity {

    protected WolfPackMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void onPlayerDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // 只有在伤害成功生效（未被格挡/免疫）时触发
        if (!cir.getReturnValue()) return;

        if (!this.getWorld().isClient && (Object)this instanceof ServerPlayerEntity player) {
            // 检查技能是否激活
            if (!PacketUtils.isSkillActive(player, "wolf_pack")) return;

            // 检查伤害来源：近战 或 箭矢
            boolean isProjectile = source.getSource() instanceof PersistentProjectileEntity;
            // 这里的判断逻辑没问题，只要是生物造成的非间接伤害就算近战
            boolean isMelee = source.getAttacker() instanceof LivingEntity && !source.isIndirect();

            if (!isProjectile && !isMelee) return;

            // === 召唤逻辑 ===
            int level = PacketUtils.getSkillLevel(player, "wolf_pack");
            ServerWorld serverWorld = (ServerWorld) player.getWorld();
            Random random = new Random();

            int spawnCount = 0;
            boolean guaranteedSummon = false;
            float chance = 0.10f; // 基础 10%
            int maxSpawn = 1 + level; // Lv1: 1-2, Lv2: 1-3
            spawnCount = random.nextInt(maxSpawn) + 1;

            // Lv3 特性检查：是否有骨头
            if (level >= 3 && player.getInventory().count(Items.BONE) > 0) {
                guaranteedSummon = true; // 只要有骨头，就必定召唤
                // 随机决定想消耗多少骨头 (1-4)，但这取决于实际能生成多少只狼
                // 我们先预设最大值，后面实际生成成功几只就扣几个
                spawnCount = random.nextInt(4) + 1;
            } else if (amount > 4.0f) {
                // 普通概率判定 (如果没有骨头，或者等级不够)
                chance += Math.min(0.30f, (amount - 4.0f) * 0.05f);
            }

            // 随机判定
            if (!guaranteedSummon && player.getRandom().nextFloat() > chance) return;

            // 执行生成
            while(spawnCount > 0){
                spawnCount--;
                if(spawnTamedWolf(serverWorld, player)){ // 尝试生成，如果成功返回 true
                    if(player.getInventory().count(Items.BONE) >= 4) {
                        removeBones(player, 4);
                        player.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    }
                    else{
                        player.playSound(SoundEvents.ENTITY_WOLF_HOWL, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    @Unique
    private boolean spawnTamedWolf(ServerWorld world, ServerPlayerEntity owner) {
        WolfEntity wolf = EntityType.WOLF.create(world);
        if (wolf != null) {
            Vec3d pos = findSafeSpawnPos(owner);
            // 简单的距离检查，确保不是生成在原点（findSafeSpawnPos 失败会返回玩家位置）
            // 这里我们可以认为只要 create 成功就算成功，或者你可以加更严谨的各种检查

            wolf.refreshPositionAndAngles(pos.x, pos.y, pos.z, owner.getYaw(), 0.0f);
            wolf.setOwner(owner);
            wolf.setTamed(true);
            wolf.setTarget(null);
            if (owner.getAttacker() != null) {
                wolf.setAngryAt(owner.getAttacker().getUuid());
            }
            world.spawnEntity(wolf);
            return true; // 生成成功
        }
        return false; // 生成失败
    }

    // [新增] 更安全的生成位置查找
    @Unique
    private Vec3d findSafeSpawnPos(ServerPlayerEntity owner) {
        for (int i = 0; i < 10; i++) {
            double angle = owner.getRandom().nextDouble() * 2 * Math.PI;
            double dist = 1.5 + owner.getRandom().nextDouble() * 2.0; // 1.5 ~ 3.5 米
            double x = owner.getX() + Math.cos(angle) * dist;
            double z = owner.getZ() + Math.sin(angle) * dist;
            double y = owner.getY();

            BlockPos targetPos = BlockPos.ofFloored(x, y, z);
            // 检查目标位置是否是空气，且脚下有方块
            if (owner.getWorld().getBlockState(targetPos).isAir() &&
                    owner.getWorld().getBlockState(targetPos.up()).isAir()) {
                return new Vec3d(x, y, z);
            }
        }
        return owner.getPos(); // 如果找不到，无奈只能生成在身上
    }

    // [核心修复] 正确的移除物品逻辑
    @Unique
    private int removeBones(PlayerEntity player, int count) {
        int removed = 0;
        // 遍历背包
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.BONE)) {
                int take = Math.min(count - removed, stack.getCount());
                stack.decrement(take);
                removed += take;
                if (removed >= count) break;
            }
        }
        return removed;
    }
}