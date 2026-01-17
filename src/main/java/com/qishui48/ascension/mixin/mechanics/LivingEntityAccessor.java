package com.qishui48.ascension.mixin.mechanics;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    // 自动生成一个 getter 方法来获取 jumping 字段
    @Accessor("jumping")
    boolean isJumping();
    // 字段 lastAttackedTicks 实际上定义在 LivingEntity 中
    // 用于记录上一次攻击的时间点 (age)
    @Accessor("lastAttackedTicks")
    void setLastAttackedTicks(int ticks);

    // 如果需要获取，也可以加上
    @Accessor("lastAttackedTicks")
    int getLastAttackedTicks();
}