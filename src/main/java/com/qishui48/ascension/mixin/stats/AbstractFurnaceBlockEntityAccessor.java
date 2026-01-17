package com.qishui48.ascension.mixin.stats;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 这个接口的作用就是把 protected 字段暴露成 public 方法
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
    @Accessor("cookTime")
    int getCookTime();

    @Accessor("cookTimeTotal")
    int getCookTimeTotal();
}