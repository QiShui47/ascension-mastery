package com.qishui48.ascension.mixin.mechanics;

import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EntityAttributes.class)
public class EntityAttributesMixin {
    // 拦截 ClampedEntityAttribute 的构造函数调用
    @ModifyArgs(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/attribute/ClampedEntityAttribute;<init>(Ljava/lang/String;DDD)V"))
    private static void modifyArmorCap(Args args) {
        String translationKey = args.get(0);
        // 检查是否是护甲属性
        if ("attribute.name.generic.armor".equals(translationKey)) {
            // 参数顺序: name(0), default(1), min(2), max(3)
            // 将最大值从 30.0 修改
            args.set(3, 100.0);
        }
    }
}