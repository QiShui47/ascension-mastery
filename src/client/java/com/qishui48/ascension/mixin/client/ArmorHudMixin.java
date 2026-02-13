package com.qishui48.ascension.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ArmorHudMixin {

    @Shadow protected abstract PlayerEntity getCameraPlayer();
    @Shadow private int renderHealthValue;
    @Shadow private int scaledHeight;
    @Shadow private int scaledWidth;

    @Unique
    private static final Identifier GUI_ICONS_TEXTURE = new Identifier("minecraft", "textures/gui/icons.png");

    @Inject(method = "renderStatusBars", at = @At("RETURN"))
    private void renderExtraArmor(DrawContext context, CallbackInfo ci) {
        PlayerEntity player = this.getCameraPlayer();
        if (player == null) return;

        int currentArmor = player.getArmor();
        if (currentArmor <= 20) return;

        // 1. 开启混合模式，防止颜色渲染异常
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 2. 重新计算 Y 坐标
        int startX = this.scaledWidth / 2 - 91;
        int bottomY = this.scaledHeight - 39;
        float maxHealth = (float)player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
        int currentHealth = MathHelper.ceil(player.getHealth());
        float layoutHealth = Math.max(maxHealth, Math.max(this.renderHealthValue, currentHealth));
        int absorption = MathHelper.ceil(player.getAbsorptionAmount());
        int healthRows = MathHelper.ceil((layoutHealth + (float)absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int y = bottomY - (healthRows - 1) * rowHeight - 10;

        // 3. 绘制逻辑
        int remainingArmor = currentArmor - 20;

        // 颜色循环：青 -> 红 -> 金 -> 紫
        int[] colors = {
                0xFF55FFFF, // Aqua
                0xFFFF5555, // Red
                0xFFFFAA00, // Gold
                0xFFAA00AA  // Dark Purple
        };
        int layerIndex = 0;

        // 循环绘制每一层，直到剩余护甲归零
        while (remainingArmor > 0) {
            // 设置当前层颜色
            int color = colors[layerIndex % colors.length];
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            // 这一层最多消耗 20 点护甲 (10 个图标)
            int layerArmor = Math.min(remainingArmor, 20);

            for (int n = 0; n < 10; ++n) {
                if (layerArmor <= 0) break;

                int drawX = startX + n * 8;

                if (layerArmor == 1) {
                    // === 半甲绘制 (左侧实心当前色，右侧透出底层色) ===
                    // 1. 先用满不透明度的白色画一个"满格"作为遮罩? 不，我们利用裁剪绘制。
                    // 只要只画左边 5px，右边透明，就会自然露底。

                    RenderSystem.setShaderColor(r, g, b, 1.0f);
                    // 绘制 u=34 (满甲图标) 但宽度只取 5
                    context.drawTexture(GUI_ICONS_TEXTURE, drawX, y, 34, 9, 5, 9, 256, 256);

                    layerArmor -= 1;
                } else if (layerArmor >= 2) {
                    // === 满甲绘制 ===
                    RenderSystem.setShaderColor(r, g, b, 1.0f);
                    context.drawTexture(GUI_ICONS_TEXTURE, drawX, y, 34, 9, 9, 9, 256, 256);
                    layerArmor -= 2;
                }
            }

            remainingArmor -= 20;
            layerIndex++;
        }

        // 4. 重置状态
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}