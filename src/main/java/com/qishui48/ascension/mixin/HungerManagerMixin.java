package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {

    // === 临时变量：存储当前正在更新的玩家 ===
    @Unique
    private PlayerEntity tempPlayer;

    /**
     * 第一步：在 update 方法的最开始，捕获 player 参数。
     * 这样我们在后面的修改中就能知道是谁在回血了。
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void capturePlayer(PlayerEntity player, CallbackInfo ci) {
        this.tempPlayer = player;
    }

    /**
     * 第二步：修改回血阈值常量。
     * 原版逻辑：if (foodLevel >= 18) 才回血。
     * 我们改为：如果有技能，只要 (foodLevel >= 1) 就回血。
     */
    @ModifyConstant(method = "update", constant = @Constant(intValue = 18))
    private int modifyRegenThreshold(int originalValue) {
        // 1. 检查刚刚捕获的玩家
        if (this.tempPlayer instanceof ServerPlayerEntity serverPlayer) {
            // 2. 检查是否有"饥饿耐受"技能
            if (PacketUtils.isSkillUnlocked(serverPlayer, "hunger_tolerance")) {
                // 3. 返回 1 (意味着只要饱食度大于等于 1 就能回血)
                return 1;
            }
        }
        // 没有技能则保持原版 18
        return originalValue;
    }
}