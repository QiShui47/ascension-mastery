package com.qishui48.ascension.mixin.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.SwordItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {

    // [新增] Shadow 引入外层模型的部件
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    public PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void onSetAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        // 检查：如果脚上装备了剑
        if (entity.getEquippedStack(EquipmentSlot.FEET).getItem() instanceof SwordItem) {

            // 1. 锁定内层 (肉体)
            this.leftLeg.pitch = 0.0F;
            this.leftLeg.yaw = 0.0F;
            this.leftLeg.roll = 0.0F;

            this.rightLeg.pitch = 0.0F;
            this.rightLeg.yaw = 0.0F;
            this.rightLeg.roll = 0.0F;

            // 2. [核心修复] 锁定外层 (衣服/裤子/袖子)
            // 手动把它们也归零，防止出现"幽灵裤管"
            this.leftPants.copyTransform(this.leftLeg);
            this.rightPants.copyTransform(this.rightLeg);

            // 身体外套
            this.jacket.copyTransform(this.body);

            // 3. 手臂与袖子处理
            if (entity.getMainHandStack().isEmpty()) {
                this.rightArm.roll = 0.1F;
                this.rightArm.pitch = 0.0F;
                // 同步袖子
                this.rightSleeve.copyTransform(this.rightArm);
            }
            if (entity.getOffHandStack().isEmpty()) {
                this.leftArm.roll = -0.1F;
                this.leftArm.pitch = 0.0F;
                // 同步袖子
                this.leftSleeve.copyTransform(this.leftArm);
            }
        }
    }
}