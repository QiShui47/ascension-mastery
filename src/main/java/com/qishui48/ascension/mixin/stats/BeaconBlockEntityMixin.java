package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public class BeaconBlockEntityMixin {

    // 目标方法是静态的，所以我们也必须用静态方法注入
    // 参数列表必须与目标方法一致：World, BlockPos, int(level), StatusEffect(primary), StatusEffect(secondary)
    @Inject(method = "applyPlayerEffects", at = @At("HEAD"))
    private static void onApplyEffects(World world, BlockPos pos, int level, StatusEffect primaryEffect, StatusEffect secondaryEffect, CallbackInfo ci) {
        if (world != null && !world.isClient) {
            // 检查：信标已激活 (level > 0) 且位于末地
            if (level > 0 && world.getRegistryKey() == World.END) {
                // 获取范围内的玩家 (信标范围逻辑：等级 * 10 + 10，Y轴延伸)
                // 直接使用传入的 pos 和 level 参数
                double range = level * 10 + 10;
                Box box = (new Box(pos)).expand(range).stretch(0.0D, (double)world.getHeight(), 0.0D);

                List<ServerPlayerEntity> players = world.getNonSpectatingEntities(ServerPlayerEntity.class, box);

                for (ServerPlayerEntity player : players) {
                    // 给予统计数据
                    player.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.ACTIVATE_BEACON_IN_END), 1);
                }
            }
        }
    }
}