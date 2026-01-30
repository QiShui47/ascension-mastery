package com.qishui48.ascension.mixin.client;

import com.qishui48.ascension.AscensionClient;
import com.qishui48.ascension.network.ModMessages;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) return;

        // 检查 Alt 键
        if (AscensionClient.altKey.isPressed()) {
            // 获取当前已解锁的槽位数
            IEntityDataSaver data = (IEntityDataSaver) client.player;
            int masteryLevel = 0;
            if (data.getPersistentData().contains("skill_levels")) {
                masteryLevel = data.getPersistentData().getCompound("skill_levels").getInt("mastery");
            }
            int maxSlots = 2 + masteryLevel;

            // 获取当前选中的槽位
            int currentSlot = data.getPersistentData().getInt("selected_active_slot");

            // 计算新槽位
            if (vertical > 0) { // 向上滚
                currentSlot--;
                if (currentSlot < 0) currentSlot = maxSlots - 1;
            } else if (vertical < 0) { // 向下滚
                currentSlot++;
                if (currentSlot >= maxSlots) currentSlot = 0;
            }

            // 发包
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(currentSlot);
            ClientPlayNetworking.send(ModMessages.SWITCH_SLOT_ID, buf);

            // 阻止原版快捷栏滚动
            ci.cancel();
        }
    }
}