package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerDamageStatMixin {
    @Inject(method = "damage", at = @At("RETURN"))
    private void onDamageReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // 不败金身解锁：幸存爆炸
            // 判定：是爆炸伤害 + 玩家还活着
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION) && player.isAlive()) {
                // 增加统计数据
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.SURVIVE_EXPLOSION), 1);
            }
        }
    }
}
