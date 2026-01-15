package com.qishui48.ascension.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.RotationAxis;

public class SwordFlightFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final ItemRenderer itemRenderer;

    public SwordFlightFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context, ItemRenderer itemRenderer) {
        super(context);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        ItemStack feetStack = player.getEquippedStack(EquipmentSlot.FEET);

        // 只渲染剑
        if (!feetStack.isEmpty() && feetStack.getItem() instanceof SwordItem) {
            matrices.push();

            // 1. 绑定到人体模型 (默认是原点，我们需要移动到脚下)
            // 简单的做法是手动移动坐标
            // 更好的做法是 parentModel.leftLeg.rotate(matrices)... 但我们希望剑是静止的

            // 2. 调整位置：脚底板中心
            matrices.translate(0.0D, 1.5D, 0.0D); // 这里的 1.5 大概是脚底相对于模型原点的位置 (上下颠倒?)
            // Minecraft 模型坐标系很怪，通常 Y 向下是正，0在头上。
            // 实际上 render 的时候 0 在脚底，往上是负。
            matrices.translate(0.0, 0.1, 0.0); // 稍微往下挪一点

            // 3. 放大模型
            float scale = 2.0f; // 放大两倍
            matrices.scale(scale, scale, scale);

            // 4. 旋转：让剑平躺
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(135)); // 调整剑尖朝前

            // 5. 渲染物品
            this.itemRenderer.renderItem(feetStack, ModelTransformationMode.FIXED, light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, player.getWorld(), 0);

            matrices.pop();
        }
    }
}