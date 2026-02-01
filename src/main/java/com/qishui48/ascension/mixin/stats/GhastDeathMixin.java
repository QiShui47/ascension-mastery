package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension; // 假设你需要用到某些常量
import net.minecraft.entity.LivingEntity; // 修改目标类
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1. 将目标改为 LivingEntity (因为 onDeath 定义在这里)
@Mixin(LivingEntity.class)
public class GhastDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onGhastDeath(DamageSource source, CallbackInfo ci) {
        // 2. 核心逻辑：先判断 "我" 是不是恶魂
        // Mixin 中 "this" 是 LivingEntity 类型，所以需要强转 (Object) 再 instanceof
        if (!((Object) this instanceof GhastEntity)) {
            return;
        }
    }
}