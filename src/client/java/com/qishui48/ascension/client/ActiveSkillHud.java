package com.qishui48.ascension.client;

import com.qishui48.ascension.client.render.SkillSlotRenderer;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public class ActiveSkillHud implements HudRenderCallback {
    private static final int SLOT_SPACING = 30;

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

        for (int i = 0; i < 5; i++) {
            int x = startX + (i * SLOT_SPACING);
            int y = startY;
            int centerX = x + 13;
            int centerY = y + 13;

            NbtCompound slotNbt = (i < activeSlots.size()) ? activeSlots.getCompound(i) : new NbtCompound();

            // 使用工具类渲染
            SkillSlotRenderer.render(context, centerX, centerY, i, selectedSlot, slotNbt, nbt, tickDelta);
        }
    }
}