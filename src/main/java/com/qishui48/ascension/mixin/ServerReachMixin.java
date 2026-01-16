package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerReachMixin {

    @Shadow public ServerPlayerEntity player;

    // 拦截最大交互距离常量 (原版通常是 64.0 = 8^2 或类似)
    // 在 1.20.1 ServerPlayNetworkHandler.onPlayerInteractBlock 中有一行：
    // if (d > 64.0) { ... }

    @ModifyConstant(method = "onPlayerInteractBlock", constant = @Constant(doubleValue = 64.0))
    private double modifyMaxBlockDistance(double original) {
        if (PacketUtils.isSkillActive(player, "telekinesis")) {
            int level = PacketUtils.getSkillLevel(player, "telekinesis");
            double extra = (level >= 2) ? 2.0 : 1.0;
            // 距离平方：(原版4.5 + extra)^2 约等于 (4.5+2)^2 = 42.25
            // 原版判定是 64.0 (即8格)，我们加了距离后可能超过 8 格吗？
            // 4.5 + 2 = 6.5，6.5^2 = 42.25，还没超。
            // 但如果是创造模式 (5.0 + 2 = 7.0)，7^2 = 49，也没超。
            // 所以，实际上只要我们不加得特别夸张 (> 3格)，原版反作弊可能不会触发。
            // 为了保险起见，我们把它改大一点。
            return 100.0; // 10格
        }
        return original;
    }
}