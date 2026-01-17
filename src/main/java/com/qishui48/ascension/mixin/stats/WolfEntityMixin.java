package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WolfEntity.class)
public abstract class WolfEntityMixin extends TameableEntity {

    protected WolfEntityMixin(net.minecraft.entity.EntityType<? extends TameableEntity> entityType, net.minecraft.world.World world) {
        super(entityType, world);
    }

    @Inject(method = "interactMob", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/WolfEntity;setOwner(Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void onTame(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!this.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // 增加驯服统计
            serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TAME_DOG_COUNT), 1);
        }
    }
}