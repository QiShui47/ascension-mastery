package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.IModProjectile;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin implements IModProjectile {

    @Unique private boolean isDeathStarArrow = false;
    @Unique private boolean isLifeForceArrow = false;
    @Unique private boolean isPocketBucketArrow = false;
    @Unique private boolean isPocketGuardArrow = false; // 随身护卫
    @Unique private boolean isPocketCatArrow = false;   // 随身猫猫

    // === 接口实现 ===
    @Override public void setDeathStar(boolean v) { this.isDeathStarArrow = v; }
    @Override public boolean isDeathStar() { return this.isDeathStarArrow; }

    @Override public void setLifeForce(boolean v) { this.isLifeForceArrow = v; }
    @Override public boolean isLifeForce() { return this.isLifeForceArrow; }

    @Override public void setPocketBucket(boolean v) { this.isPocketBucketArrow = v; }
    @Override public boolean isPocketBucket() { return this.isPocketBucketArrow; }

    @Override public void setPocketGuard(boolean v) { this.isPocketGuardArrow = v; }
    @Override public boolean isPocketGuard() { return this.isPocketGuardArrow; }

    @Override public void setPocketCat(boolean v) { this.isPocketCatArrow = v; }
    @Override public boolean isPocketCat() { return this.isPocketCatArrow; }

    // === NBT 数据保存 ===
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("IsDeathStar", isDeathStarArrow);
        nbt.putBoolean("IsLifeForce", isLifeForceArrow);
        nbt.putBoolean("IsPocketBucket", isPocketBucketArrow);
        nbt.putBoolean("IsPocketGuard", isPocketGuardArrow);
        nbt.putBoolean("IsPocketCat", isPocketCatArrow);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readNbt(NbtCompound nbt, CallbackInfo ci) {
        this.isDeathStarArrow = nbt.getBoolean("IsDeathStar");
        this.isLifeForceArrow = nbt.getBoolean("IsLifeForce");
        this.isPocketBucketArrow = nbt.getBoolean("IsPocketBucket");
        this.isPocketGuardArrow = nbt.getBoolean("IsPocketGuard");
        this.isPocketCatArrow = nbt.getBoolean("IsPocketCat");
    }

    // === 自动销毁 (防止无限飞行) ===
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
        if (this.isDeathStarArrow && arrow.age > 100) {
            arrow.discard();
        }
    }

    // === 击中方块的核心逻辑 ===
    @Inject(method = "onBlockHit", at = @At("HEAD"))
    private void onHitBlock(BlockHitResult blockHitResult, CallbackInfo ci) {
        PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
        World world = arrow.getWorld();

        // 在这里统一定义 pos，这样下面所有的 if 都能用
        BlockPos pos = blockHitResult.getBlockPos();

        if (!world.isClient) {

            // 1. 歼星炮
            if (this.isDeathStarArrow) {
                Vec3d velocity = arrow.getVelocity();
                if (velocity.lengthSquared() > 1.0E-7D) {
                    velocity = velocity.normalize();
                } else {
                    velocity = new Vec3d(0, -1, 0);
                }
                Vec3d startVec = blockHitResult.getPos();

                for (int i = 0; i < 50; i++) {
                    Vec3d targetVec = startVec.add(velocity.multiply(i));
                    BlockPos centerPos = BlockPos.ofFloored(targetVec);
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -1; z <= 1; z++) {
                                BlockPos breakPos = centerPos.add(x, y, z);
                                BlockState state = world.getBlockState(breakPos);
                                if (!state.isAir() && !state.isOf(Blocks.BEDROCK)) {
                                    world.setBlockState(breakPos, Blocks.AIR.getDefaultState());
                                }
                            }
                        }
                    }
                }
                arrow.discard();
            }

            // 2. 生命之力
            if (this.isLifeForceArrow) {
                BlockState state = world.getBlockState(pos);
                if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
                    world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState());
                    arrow.discard();
                }
            }

            // 3. 随身水桶
            if (this.isPocketBucketArrow) {
                BlockPos placePos = pos.offset(blockHitResult.getSide());
                BlockState targetState = world.getBlockState(placePos);
                if (targetState.isAir() || !targetState.isSolidBlock(world, placePos)) {
                    world.setBlockState(placePos, Blocks.WATER.getDefaultState());
                    arrow.discard();
                }
            }

            // 4. 随身护卫 (Iron Golem)
            if (this.isPocketGuardArrow) {
                // 计算生成位置（在击中面的外侧）
                BlockPos placePos = pos.offset(blockHitResult.getSide());

                IronGolemEntity golem = EntityType.IRON_GOLEM.create(world);
                if (golem != null) {
                    golem.refreshPositionAndAngles(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5, 0, 0);
                    golem.setPlayerCreated(true);
                    world.spawnEntity(golem);
                    arrow.discard();
                }
            }

            // 5. 随身猫猫 (Cats)
            if (this.isPocketCatArrow) {
                BlockPos placePos = pos.offset(blockHitResult.getSide());

                // 在这里单独获取 owner 并检查类型
                Entity rawOwner = arrow.getOwner();

                // 只有当主人是玩家时才生成（为了防止奇怪的Bug）
                if (rawOwner instanceof ServerPlayerEntity playerOwner) {
                    int catCount = 2 + world.random.nextInt(2); // 2-3只
                    for (int i = 0; i < catCount; i++) {
                        CatEntity cat = EntityType.CAT.create(world);
                        if (cat != null) {
                            cat.refreshPositionAndAngles(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5, world.random.nextFloat() * 360, 0);

                            // 驯服逻辑
                            cat.setTamed(true);
                            cat.setOwner(playerOwner); // 使用刚刚转换好的 playerOwner

                            // 随机花色
                            cat.initialize(world.getServer().getWorld(world.getRegistryKey()), world.getLocalDifficulty(placePos), net.minecraft.entity.SpawnReason.TRIGGERED, null, null);

                            world.spawnEntity(cat);
                        }
                    }
                    arrow.discard();
                }
            }
        }
    }
}