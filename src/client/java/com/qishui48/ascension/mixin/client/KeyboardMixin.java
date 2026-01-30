package com.qishui48.ascension.mixin.client;

import com.qishui48.ascension.AscensionClient;
import com.qishui48.ascension.network.ModMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    public void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // 只处理按下事件 (action == 1)
        if (action == GLFW.GLFW_PRESS && MinecraftClient.getInstance().player != null) {

            // 检查 Alt 键是否被按下 (AscensionClient.altKey 必须已注册)
            if (AscensionClient.altKey.isPressed()) {

                // 检查数字键 1-5 (GLFW_KEY_1 ~ GLFW_KEY_5)
                // 注意：这里假设用户使用的是标准数字键，而不是小键盘
                int slotIndex = -1;
                if (key == GLFW.GLFW_KEY_1) slotIndex = 0;
                else if (key == GLFW.GLFW_KEY_2) slotIndex = 1;
                else if (key == GLFW.GLFW_KEY_3) slotIndex = 2;
                else if (key == GLFW.GLFW_KEY_4) slotIndex = 3;
                else if (key == GLFW.GLFW_KEY_5) slotIndex = 4;

                if (slotIndex != -1) {
                    // 发送切换包
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(slotIndex);
                    ClientPlayNetworking.send(ModMessages.SWITCH_SLOT_ID, buf);

                    // 播放音效
                    // MinecraftClient.getInstance().getSoundManager().play(...)

                    // === [关键] 阻止原版处理该按键 ===
                    // 这会防止物品栏切换
                    ci.cancel();
                }
            }
        }
    }
}