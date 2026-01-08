package com.qishui48.ascension;

import com.qishui48.ascension.client.NotificationHud;
import com.qishui48.ascension.network.ModMessages;
import com.qishui48.ascension.screen.SkillTreeScreen;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AscensionClient implements ClientModInitializer {

	public static KeyBinding openGuiKey;

	@Override
	public void onInitializeClient() {
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ascension.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				"category.ascension.general"
		));

		// 1. 注册 HUD 渲染
		HudRenderCallback.EVENT.register(new NotificationHud());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			NotificationHud.tick();
			while (openGuiKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new SkillTreeScreen());
				}
			}
		});

		registerS2CPackets();
	}

	private void registerS2CPackets() {
		ClientPlayNetworking.registerGlobalReceiver(Ascension.PACKET_ID, (client, handler, buf, responseSender) -> {
			NbtCompound incomingNbt = buf.readNbt();

			client.execute(() -> {
				if (client.player != null) {
					IEntityDataSaver dataSaver = (IEntityDataSaver) client.player;
					NbtCompound localNbt = dataSaver.getPersistentData();

					if (incomingNbt.contains("my_global_skills")) {
						localNbt.putInt("my_global_skills", incomingNbt.getInt("my_global_skills"));
					}
					if (incomingNbt.contains("skill_levels")) {
						localNbt.put("skill_levels", incomingNbt.getCompound("skill_levels"));
					} else {
						localNbt.remove("skill_levels");
					}
					if (incomingNbt.contains("criteria_cache")) {
						localNbt.put("criteria_cache", incomingNbt.getCompound("criteria_cache"));
					}
					if (incomingNbt.contains("criteria_progress")) {
						localNbt.put("criteria_progress", incomingNbt.getCompound("criteria_progress"));
					}
					// === 同步已发现的隐藏技能 ===
					if (incomingNbt.contains("revealed_skills")) {
						localNbt.put("revealed_skills", incomingNbt.getCompound("revealed_skills"));
					} else {
						localNbt.remove("revealed_skills"); // 服务端清空了，本地也要清空
					}
					if (incomingNbt.contains("disabled_skills")) {
						localNbt.put("disabled_skills", incomingNbt.getCompound("disabled_skills"));
					} else {
						localNbt.remove("disabled_skills");
					}

					if (localNbt.contains("unlocked_skills")) {
						localNbt.remove("unlocked_skills");
					}
				}
			});
		});

		// 新增：接收通知包
		ClientPlayNetworking.registerGlobalReceiver(ModMessages.SHOW_NOTIFICATION_ID, (client, handler, buf, responseSender) -> {
			// 读取文字 JSON 字符串 或 直接读 Text
			// 推荐直接读 Text (Fabric API支持) 或者读 JSON 字符串
			// 这里假设我们发的是 JSON 字符串，兼容性好
			String json = buf.readString();
			Text text = Text.Serializer.fromJson(json);

			client.execute(() -> {
				NotificationHud.addMessage(text);
			});
		});
	}
}