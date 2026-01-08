package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public class FallDamageMixin {

    // 核心修复：添加 ordinal = 0
    // argsOnly = true 表示只在参数里找
    // ordinal = 0 表示找“第1个”符合 float 类型的参数 (也就是 fallDistance)
    // 如果 ordinal = 1，就会修改 damageMultiplier
    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float reduceFallDistance(float fallDistance) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        // 只在服务端逻辑生效
        if (!player.getWorld().isClient) {
            IEntityDataSaver dataSaver = (IEntityDataSaver) player;

            // 检查是否有缓冲值
            if (dataSaver.getPersistentData().contains("fall_cushion")) {
                float cushion = dataSaver.getPersistentData().getFloat("fall_cushion");

                // 消耗掉缓冲 (落地一次后失效)
                dataSaver.getPersistentData().remove("fall_cushion");

                // === 完美的数学减法 ===
                // 如果缓冲是 6.0，摔落是 10.0，返回 4.0 (受 4 格伤害)
                // 如果缓冲是 6.0，摔落是 3.0，返回 0.0 (无伤)
                return Math.max(0, fallDistance - cushion);
            }
        }

        // 如果没有缓冲，原样返回，不干扰原版逻辑
        return fallDistance;
    }
}