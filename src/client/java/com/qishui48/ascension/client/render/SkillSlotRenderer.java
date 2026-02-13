package com.qishui48.ascension.client.render;

import com.qishui48.ascension.skill.ActiveSkill;
import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.Map;

public class SkillSlotRenderer {
    private static final Map<Integer, Integer> lastCharges = new HashMap<>();
    private static final Map<Integer, Long> animationStartTimes = new HashMap<>();

    /**
     * 绘制单个技能槽
     * @param context 绘图上下文
     * @param centerX 槽位中心X坐标
     * @param centerY 槽位中心Y坐标
     * @param slotIndex 槽位索引 (0-4)
     * @param selectedSlotIndex 当前选中的槽位索引
     * @param slotNbt 槽位的NBT数据 (包含 charges, cooldown 等)
     * @param playerNbt 玩家的NBT数据 (包含 skill_levels 等)
     * @param tickDelta 渲染Tick差值
     */
    public static void render(DrawContext context, int centerX, int centerY, int slotIndex, int selectedSlotIndex, NbtCompound slotNbt, NbtCompound playerNbt, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isSelected = (slotIndex == selectedSlotIndex);
        long currentTime = client.world.getTime();

        // 颜色定义
        int mainBorderColor = isSelected ? 0xFFFFFFFF : 0xFF555555;
        // [新增需求] 选中时，充能条(冷却条)的小边框也变为白色，否则为灰色
        int barBorderColor = isSelected ? 0xFFFFFFFF : 0xFF555555;

        // === 1. 绘制底座 (旋转) ===
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));

        // 绘制半透明黑底和主边框
        context.fill(-13, -13, 13, 13, 0x80000000);
        context.drawBorder(-13, -13, 26, 26, mainBorderColor);

        String skillId = slotNbt.getString("id");
        if (!skillId.isEmpty()) {
            Skill skill = SkillRegistry.get(skillId);
            if (skill != null) {
                // === 2. 绘制图标 (抵消旋转以保持直立) ===
                context.getMatrices().push();
                context.getMatrices().multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(45));
                context.getMatrices().scale(1.3f, 1.3f, 1f);
                context.getMatrices().translate(-8.5f, -8.5f, 0);
                context.drawItem(skill.getIcon(), 0, 0);
                context.getMatrices().pop();

                // === 3. 冷却遮罩 (竖直水位效果) ===
                long endTime = slotNbt.getLong("cooldown_end");
                int totalTime = slotNbt.getInt("cooldown_total");
                int charges = slotNbt.getInt("charges");

                int level = 0;
                if (playerNbt.contains("skill_levels")) level = playerNbt.getCompound("skill_levels").getInt(skillId);
                if (level < 1) level = 1;
                int maxCharges = (skill instanceof ActiveSkill) ? ((ActiveSkill)skill).getMaxCharges(level) : 1;

                if (currentTime >= endTime && charges == 0) charges = maxCharges;

                if (currentTime < endTime && totalTime > 0) {
                    float progress = (float)(endTime - currentTime) / totalTime;

                    // 暂时弹出旋转矩阵以应用 Scissor
                    context.getMatrices().pop();

                    int halfVisualHeight = 19;
                    int maskFullHeight = 38;
                    int visibleHeight = (int)(maskFullHeight * progress);
                    int scissorY = (centerY - halfVisualHeight) + (maskFullHeight - visibleHeight);

                    context.enableScissor(centerX - halfVisualHeight, scissorY, centerX + halfVisualHeight, centerY + halfVisualHeight);

                    // 重新进入旋转矩阵
                    context.getMatrices().push();
                    context.getMatrices().translate(centerX, centerY, 0);
                    context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));

                    context.fill(-13, -13, 13, 13, 0x60FFFFFF); // 半透明白遮罩

                    context.getMatrices().pop(); // 弹出旋转
                    context.disableScissor();
                    context.getMatrices().push(); // 恢复外层旋转
                    context.getMatrices().translate(centerX, centerY, 0);
                    context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
                }

                // === 4. 充能完成动画 (外框缩放) ===
                int currentCharges = slotNbt.getInt("charges");
                int lastCharge = lastCharges.getOrDefault(slotIndex, currentCharges);
                if (currentCharges > lastCharge) animationStartTimes.put(slotIndex, System.currentTimeMillis());
                lastCharges.put(slotIndex, currentCharges);

                if (animationStartTimes.containsKey(slotIndex)) {
                    long startTime = animationStartTimes.get(slotIndex);
                    long animElapsed = System.currentTimeMillis() - startTime;
                    if (animElapsed < 500) {
                        float p = animElapsed / 500f;
                        float s = 1.0f + (0.5f * (1-p));
                        int alpha = (int)((1-p) * 255);
                        int col = (alpha << 24) | 0xFFFFFF;

                        context.getMatrices().push();
                        context.getMatrices().scale(s, s, 1f);
                        context.drawBorder(-13, -13, 26, 26, col);
                        context.getMatrices().pop();
                    } else {
                        animationStartTimes.remove(slotIndex);
                    }
                }

                // === 5. 充能条 (平行四边形风格) ===
                int reservedCharges = 0;
                // 使用 slotNbt 中的 effect_end 来判断通用预留 (修复了上个问题中的逻辑)
                if (skillId.equals("blink") && slotNbt.getLong("effect_end") > currentTime) reservedCharges = 1;
                else if (skillId.equals("wraith_wrath") && playerNbt.contains("wraith_charging_end")) reservedCharges = 1;

                int availableCharges = Math.max(0, charges - reservedCharges);

                // 尺寸定义
                int barLen = 7; int barThick = 3; int gap = 1; int border = 1; int edgeLen = 26;
                int countTopRight = Math.min(2, maxCharges);
                int countBotRight = Math.min(2, Math.max(0, maxCharges - 2));
                int countBotLeft = Math.max(0, maxCharges - 4);

                for (int c = 0; c < maxCharges; c++) {
                    float drawX = 0, drawY = 0, width = 0, height = 0;

                    if (c < 2) { // Top-Right
                        int totalLen = countTopRight * (barLen + gap) - gap;
                        int startOffset = (edgeLen - totalLen) / 2;
                        drawX = -13 + startOffset + c * (barLen + gap);
                        drawY = -13 - gap - barThick;
                        width = barLen; height = barThick;
                    } else if (c < 4) { // Bottom-Right
                        int totalLen = countBotRight * (barLen + gap) - gap;
                        int startOffset = (edgeLen - totalLen) / 2;
                        drawX = 13 + gap;
                        drawY = -13 + startOffset + (c - 2) * (barLen + gap);
                        width = barThick; height = barLen;
                    } else { // Bottom-Left
                        int totalLen = countBotLeft * (barLen + gap) - gap;
                        int startOffset = (edgeLen - totalLen) / 2;
                        drawX = 13 - startOffset - ((c - 4) + 1) * (barLen + gap) + gap;
                        drawY = 13 + gap;
                        width = barLen; height = barThick;
                    }

                    // 颜色逻辑
                    int innerColor = 0x00000000;
                    float fillProgress = 0.0f;
                    if (c < availableCharges) {
                        innerColor = 0xFF00AAFF; fillProgress = 1.0f;
                    } else if (c < availableCharges + reservedCharges) {
                        innerColor = 0xFFFF0000; fillProgress = 1.0f;
                    } else if (c == charges && currentTime < endTime) {
                        long timeLeft = endTime - currentTime;
                        if (timeLeft <= 5) { fillProgress = 1.0f - (timeLeft / 5.0f); innerColor = 0xFFFFFFFF; }
                    }

                    // 绘制边框 (使用 barBorderColor)
                    context.fill((int)drawX, (int)drawY, (int)(drawX+width), (int)(drawY+border), barBorderColor); // Top
                    context.fill((int)drawX, (int)(drawY+height-border), (int)(drawX+width), (int)(drawY+height), barBorderColor); // Bottom
                    context.fill((int)drawX, (int)drawY, (int)(drawX+border), (int)(drawY+height), barBorderColor); // Left
                    context.fill((int)(drawX+width-border), (int)drawY, (int)(drawX+width), (int)(drawY+height), barBorderColor); // Right

                    // 绘制内部
                    if (fillProgress > 0) {
                        context.fill((int)drawX + border, (int)drawY + border, (int)(drawX+width-border), (int)(drawY+height-border), innerColor);
                    }
                }

                // === 6. 持续时间条 (左上边长) ===
                if (slotNbt.contains("effect_end")) {
                    long end = slotNbt.getLong("effect_end");
                    int total = slotNbt.getInt("effect_total");
                    if (end > currentTime && total > 0) {
                        float progress = (float)(end - currentTime) / total;
                        int durThick = 3; int durLen = 14; int gapDur = 1;
                        int dX = -13 - gapDur - durThick; int dY = -13;

                        int barColor = 0xFFFFD700;
                        if (progress <= 0.25f) {
                            barColor = 0xFFFF0000;
                            if (currentTime % 8 < 4) barColor = 0x00000000;
                        }

                        if (barColor != 0) {
                            context.drawBorder(dX, dY, durThick, durLen + 2, 0xFFFFFFFF);
                            // 填充
                            int fillH = (int)(durLen * progress);
                            // 内部区域
                            int inX = dX + 1;
                            int inW = durThick - 2;
                            // 比如满的时候: y=-13, h=26.
                            // 少的时候: y=-13, h=5.
                            int fillStartY = (dY + durLen + 1) - fillH;
                            context.fill(inX, fillStartY, inX + inW, fillStartY + fillH, barColor); //y1为绘制终点(右上），y2为绘制起点
                        }
                    }
                }
            }
        }

        context.getMatrices().pop(); // 结束旋转

        // 7. 数字角标 (不旋转)
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 20);
        String keyNum = String.valueOf(slotIndex + 1);
        context.drawText(client.textRenderer, keyNum, centerX - (client.textRenderer.getWidth(keyNum) / 2), centerY + 14, 0xFFAAAAAA, true);
        context.getMatrices().pop();
    }
}