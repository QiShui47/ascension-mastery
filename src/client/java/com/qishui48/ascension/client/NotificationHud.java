package com.qishui48.ascension.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class NotificationHud implements HudRenderCallback {

    // 内部类：存储单条消息
    private static class Notification {
        Text text;
        int displayTicks; // 显示时长
        int fadeOutTicks; // 淡出时长

        public Notification(Text text) {
            this.text = text;
            this.displayTicks = 120; // 6秒
            this.fadeOutTicks = 20;  // 1秒淡出
        }
    }

    private static final List<Notification> notifications = new ArrayList<>();
    private static final int MAX_NOTIFICATIONS = 5; // 最多显示几条

    // 添加新消息
    public static void addMessage(Text message) {
        notifications.add(new Notification(message));
        // 如果超过数量，移除最早的（下标0）
        if (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.remove(0);
        }
    }

    // 每一帧渲染
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (notifications.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return; // F1隐藏界面时也不显示

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        // 起始位置：屏幕右侧，垂直居中偏下
        int x = width - 5;
        int y = height / 2 + 20;

        // 倒序遍历（新消息在下面，旧消息在上面，或者反过来，看你喜好）
        // 这里我们采用：新消息在最底部，旧消息被顶上去
        // 所以我们从列表的最后一个开始画，坐标不断往上减

        // 为了线程安全，建议在 Render 线程操作，这里简单处理
        // 注意：集合修改必须小心并发，但在 Minecraft Client 主线程一般没问题
        for (int i = notifications.size() - 1; i >= 0; i--) {
            Notification note = notifications.get(i);

            // 计算透明度
            int alpha = 255;
            if (note.displayTicks < note.fadeOutTicks) {
                alpha = (int) ((float) note.displayTicks / note.fadeOutTicks * 255);
            }
            if (alpha < 5) continue; // 看不见了就不画

            // ARGB 颜色: (alpha << 24) | (R << 16) | (G << 8) | B
            int color = (alpha << 24) | 0xFFFFFF; // 白色文字带透明度

            // 测量文字宽度，为了右对齐
            int textWidth = client.textRenderer.getWidth(note.text);

            // 绘制文字 (右对齐)
            context.drawTextWithShadow(client.textRenderer, note.text, x - textWidth, y, color);

            // 向上移动一行
            y -= 12;
        }
    }

    // 需要在 ClientTick 更新计时器
    public static void tick() {
        Iterator<Notification> it = notifications.iterator();
        while (it.hasNext()) {
            Notification note = it.next();
            note.displayTicks--;
            if (note.displayTicks <= 0) {
                it.remove();
            }
        }
    }
}