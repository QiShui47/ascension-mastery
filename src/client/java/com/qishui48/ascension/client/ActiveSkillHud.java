package com.qishui48.ascension.client;

import com.qishui48.ascension.skill.ActiveSkill;
import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.RotationAxis;
import java.util.HashMap;
import java.util.Map;

public class ActiveSkillHud implements HudRenderCallback {
    private static final int SLOT_SPACING = 30;

    // === 状态记录 ===
    // 记录每个槽位的上一次充能数: Map<SlotIndex, ChargeCount>
    private static final Map<Integer, Integer> lastCharges = new HashMap<>();
    // 记录动画开始时间: Map<SlotIndex, StartTime>
    private static final Map<Integer, Long> animationStartTimes = new HashMap<>();

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        IEntityDataSaver data = (IEntityDataSaver) client.player;
        NbtCompound nbt = data.getPersistentData();

        NbtList activeSlots = nbt.getList("active_skill_slots", NbtElement.COMPOUND_TYPE);
        int selectedSlot = nbt.getInt("selected_active_slot");

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int startX = (screenWidth / 2) + 100;
        int startY = screenHeight - 36;

        long currentTime = client.world.getTime();

        for (int i = 0; i < 5; i++) {
            int x = startX + (i * SLOT_SPACING);
            int y = startY;

            // 1. 绘制菱形底座
            context.getMatrices().push();
            context.getMatrices().translate(x + 13, y + 13, 0);
            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
            context.getMatrices().translate(-13, -13, 0);

            context.fill(0, 0, 26, 26, 0x80000000);
            int borderColor = (i == selectedSlot) ? 0xFFFFFFFF : 0xFF555555;
            context.drawBorder(0, 0, 26, 26, borderColor);

            context.getMatrices().pop();

            // 2. 绘制内容
            if (i < activeSlots.size()) {
                NbtCompound slotNbt = activeSlots.getCompound(i);
                String skillId = slotNbt.getString("id");

                if (!skillId.isEmpty()) {
                    Skill skill = SkillRegistry.get(skillId);
                    if (skill != null) {
                        // === 图标放大 ===
                        context.getMatrices().push();
                        // 移动到图标中心 (x+5 是左上角，图标 16x16，中心在 x+13, y+13)
                        context.getMatrices().translate(x + 13, y + 13, 0);
                        context.getMatrices().scale(1.3f, 1.3f, 1f); // 放大 1.3 倍
                        context.getMatrices().translate(-8, -8, 0); // 移回图标左上角绘制点 (-16/2)

                        context.drawItem(skill.getIcon(), 0, 0); // 在 (0,0) 绘制，受矩阵影响
                        context.getMatrices().pop();

                        // === 冷却遮罩动画 ===
                        long endTime = slotNbt.getLong("cooldown_end");
                        int totalTime = slotNbt.getInt("cooldown_total");

                        // 懒加载恢复检测 (客户端显示用)
                        int charges = slotNbt.getInt("charges");
                        if (currentTime >= endTime && charges == 0) {
                            // 视觉上视为已恢复
                            charges = (skill instanceof ActiveSkill) ? ((ActiveSkill)skill).getMaxCharges(1) : 1;
                        }

                        if (currentTime < endTime && totalTime > 0) {
                            float progress = (float)(endTime - currentTime) / totalTime;

                            int cx = x + 13;
                            int cy = y + 13;
                            int halfDiag = 19;
                            int maskHeight = (int)(38 * progress);
                            int waterLevelY = (cy + 19) - maskHeight;

                            // 开启 Scissor 裁剪
                            context.enableScissor(cx - halfDiag, waterLevelY, cx + halfDiag, cy + 19);

                            context.getMatrices().push();
                            context.getMatrices().translate(cx, cy, 10);
                            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
                            context.getMatrices().translate(-13, -13, 0);

                            context.fill(0, 0, 26, 26, 0x60FFFFFF);

                            context.getMatrices().pop();
                            context.disableScissor();
                        }

                        // === 充能完成动画逻辑 ===
                        int currentCharges = slotNbt.getInt("charges");
                        // 获取上一次记录的充能
                        int lastCharge = lastCharges.getOrDefault(i, currentCharges);

                        // 检测：充能是否增加了？
                        if (currentCharges > lastCharge) {
                            // 触发动画：记录当前时间 (System.currentTimeMillis() 用于动画更精准)
                            animationStartTimes.put(i, System.currentTimeMillis());
                        }
                        // 更新记录
                        lastCharges.put(i, currentCharges);

                        // 渲染动画
                        if (animationStartTimes.containsKey(i)) {
                            long startTime = animationStartTimes.get(i);
                            long animElapsed = System.currentTimeMillis() - startTime;
                            long animDuration = 500; // 动画持续 500ms

                            if (animElapsed < animDuration) {
                                // 计算进度 0.0 -> 1.0
                                float animProgress = (float) animElapsed / animDuration;

                                // 效果：一个大的菱形框，逐渐缩小到正常大小，并淡出
                                // 初始 scale 2.0 -> 1.0
                                float scale = 2.0f - (1.0f * animProgress);
                                // 透明度 1.0 -> 0.0
                                int alpha = (int) ((1.0f - animProgress) * 255);
                                int color = (alpha << 24) | 0xFFFFFF; // 白色

                                context.getMatrices().push();
                                context.getMatrices().translate(x + 13, y + 13, 0); // 中心
                                context.getMatrices().scale(scale, scale, 1f); // 缩放
                                context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
                                context.getMatrices().translate(-13, -13, 0); // 回左上角

                                // 绘制边框
                                context.drawBorder(0, 0, 26, 26, color);

                                context.getMatrices().pop();
                            } else {
                                // 动画结束，清理
                                animationStartTimes.remove(i);
                            }
                        }

                        // === 绘制充能次数 (图标右侧) ===
                        // 颜色：有充能白色，0充能红色
                        int color = (charges > 0) ? 0xFFFFFFFF : 0xFFFF5555;
                        String countText = String.valueOf(charges);

                        context.getMatrices().push();
                        context.getMatrices().translate(0, 0, 20); // 最顶层
                        // 绘制位置：菱形右侧外部 x + 24, 垂直居中 y + 8
                        context.drawText(client.textRenderer, countText, x + 20, y + 18, color, true);
                        context.getMatrices().pop();
                    }
                }
            }

            // 4. 数字角标
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 20);
            String keyNum = String.valueOf(i + 1);
            context.drawText(client.textRenderer, keyNum, x + 13 - (client.textRenderer.getWidth(keyNum) / 2), y + 24, 0xFFAAAAAA, true);
            context.getMatrices().pop();
        }
    }
}