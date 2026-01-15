package com.qishui48.ascension.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    // 自动生成一个 getter 方法来获取 jumping 字段
    @Accessor("jumping")
    boolean isJumping();
}