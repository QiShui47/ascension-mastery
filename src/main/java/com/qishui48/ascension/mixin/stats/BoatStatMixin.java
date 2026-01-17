package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BoatEntity.class)
public abstract class BoatStatMixin extends Entity {
    public BoatStatMixin(EntityType<?> type, World world) { super(type, world); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onStatTick(CallbackInfo ci) {
        if (this.getWorld().isClient) return;

        BoatEntity boat = (BoatEntity) (Object) this;
        Entity passenger = boat.getFirstPassenger();

        if (passenger instanceof ServerPlayerEntity serverPlayer) {
            // === 冰上划船统计 ===
            if (this.horizontalCollision || Math.abs(this.getVelocity().x) > 0.01 || Math.abs(this.getVelocity().z) > 0.01) {
                BlockPos belowPos = this.getBlockPos().down();
                net.minecraft.block.BlockState state = this.getWorld().getBlockState(belowPos);

                if (state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE)) {
                    int distCm = (int) (this.getVelocity().horizontalLength() * 100);
                    if (distCm > 0) {
                        serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.BOAT_ON_ICE), distCm);
                    }
                }
            }
        }
    }
}