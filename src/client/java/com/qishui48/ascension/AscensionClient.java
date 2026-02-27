package com.qishui48.ascension;

import com.qishui48.ascension.client.ActiveSkillHud;
import com.qishui48.ascension.client.NotificationHud;
import com.qishui48.ascension.compat.LambDynLightsCompat;
import com.qishui48.ascension.mixin.mechanics.LivingEntityAccessor;
import com.qishui48.ascension.network.ModMessages;
import com.qishui48.ascension.screen.SkillTreeScreen;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import com.qishui48.ascension.client.render.SwordFlightFeatureRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class AscensionClient implements ClientModInitializer {

	public static KeyBinding openGuiKey;
	public static KeyBinding altKey; // 组合热键
	public static KeyBinding castPrimaryKey;   // 主动技能主要效果
	public static KeyBinding castSecondaryKey; // 主动技能次要效果

	// 防止连点
	private boolean wasAltPressed = false;
	private boolean wasAttackPressed = false;
	private boolean wasUsePressed = false;

	public static final java.util.Map<Integer, Integer> hunterVisionTargets = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public void onInitializeClient() {
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ascension.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				"category.ascension.general"
		));
		// 注册 Alt 组合键 (默认左 Alt)
		altKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ascension.combo_alt",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_ALT,
				"category.ascension.general"
		));
		// 注册新热键 (默认为 GLFW_KEY_UNKNOWN 即未绑定)
		castPrimaryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ascension.cast_primary",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				"category.ascension.general"
		));
		castSecondaryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ascension.cast_secondary",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				"category.ascension.general"
		));

		// 1. 注册 HUD 渲染
		HudRenderCallback.EVENT.register(new NotificationHud());
		HudRenderCallback.EVENT.register(new ActiveSkillHud()); // 注册技能槽 HUD

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer) {
				registrationHelper.register(new SwordFlightFeatureRenderer(
						(PlayerEntityRenderer) entityRenderer,
						net.minecraft.client.MinecraftClient.getInstance().getItemRenderer()
				));
			}
		});

		// 4. Client Tick (处理输入)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			NotificationHud.tick();

			if (client.player == null) return;

			// 打开 UI
			while (openGuiKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new SkillTreeScreen());
				}
			}

			// === ALT 组合键逻辑 ===
			if (altKey.isPressed()) {
				// A. 切换槽位 (Alt + 1~5)
				// 这里检测原版快捷键绑定
				for (int i = 0; i < 5; i++) {
					if (client.options.hotbarKeys[i].wasPressed()) {
						PacketByteBuf buf = PacketByteBufs.create();
						buf.writeInt(i);
						ClientPlayNetworking.send(ModMessages.SWITCH_SLOT_ID, buf);
						// 为了防止切换原版物品栏，可能需要额外逻辑，但 Fabric API 较难完美拦截。
						// 简单的做法是：玩家需接受 Alt+1 也会切物品栏。
						// 或者可以在这里设置 client.player.getInventory().selectedSlot = previous; (会抖动)
					}
				}

				// B. 释放技能 (Alt + 左键/右键)
				// 左键 (Primary)
				if (client.options.attackKey.isPressed()) {
					if (!wasAttackPressed) {
						PacketByteBuf buf = PacketByteBufs.create();
						buf.writeBoolean(false); // isSecondary = false
						ClientPlayNetworking.send(ModMessages.USE_ACTIVE_SKILL_ID, buf);
						wasAttackPressed = true;

						// 可以在这里取消原版攻击挖掘: client.options.attackKey.setPressed(false);
					}
				} else {
					wasAttackPressed = false;
				}

				// 右键 (Secondary)
				if (client.options.useKey.isPressed()) {
					if (!wasUsePressed) {
						PacketByteBuf buf = PacketByteBufs.create();
						buf.writeBoolean(true); // isSecondary = true
						ClientPlayNetworking.send(ModMessages.USE_ACTIVE_SKILL_ID, buf);
						wasUsePressed = true;
					}
				} else {
					wasUsePressed = false;
				}
			}
			// 独立热键逻辑 //
			// 主要效果
			while (castPrimaryKey.wasPressed()) {
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeBoolean(false); // isSecondary = false
				ClientPlayNetworking.send(ModMessages.USE_ACTIVE_SKILL_ID, buf);
			}

			// 次要效果
			while (castSecondaryKey.wasPressed()) {
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeBoolean(true); // isSecondary = true
				ClientPlayNetworking.send(ModMessages.USE_ACTIVE_SKILL_ID, buf);
			}
		});

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				new net.minecraft.util.Identifier("ascension", "s2c_hunter_vision"),
				(client, handler, buf, responseSender) -> {
					// 如果发来的是空包，表示技能结束
					if (buf.readableBytes() == 0) {
						client.execute(hunterVisionTargets::clear);
						return;
					}

					int count = buf.readInt();
					java.util.Map<Integer, Integer> newTargets = new java.util.HashMap<>();
					for (int i = 0; i < count; i++) {
						newTargets.put(buf.readInt(), buf.readInt());
					}

					client.execute(() -> {
						hunterVisionTargets.clear();
						hunterVisionTargets.putAll(newTargets);
					});
				});

		// === 软依赖加载 ===
		// 检查模组是否加载
		if (FabricLoader.getInstance().isModLoaded("lambdynlights")) {
			// 只有存在时，才去触碰 Compat 类
			LambDynLightsCompat.register();
		}

		registerS2CPackets();
	}

	private void registerS2CPackets() {
		ClientPlayNetworking.registerGlobalReceiver(Ascension.PACKET_ID, (client, handler, buf, responseSender) -> {
			NbtCompound incomingNbt = buf.readNbt();

			client.execute(() -> {
				if (client.player != null) {
					IEntityDataSaver dataSaver = (IEntityDataSaver) client.player;
					NbtCompound localNbt = dataSaver.getPersistentData();

					if (incomingNbt.contains("skill_points")) {
						localNbt.putInt("skill_points", incomingNbt.getInt("skill_points"));
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

					// === 接收主动技能与材料数据 ===
					if (incomingNbt.contains("active_skill_slots")) {
						localNbt.put("active_skill_slots", incomingNbt.getList("active_skill_slots", 10)); // 10=Compound
					}
					if (incomingNbt.contains("casting_materials")) {
						localNbt.put("casting_materials", incomingNbt.getList("casting_materials", 10));
					}
					if (incomingNbt.contains("selected_active_slot")) {
						localNbt.putInt("selected_active_slot", incomingNbt.getInt("selected_active_slot"));
					}

					// 接收光耀化身数据
					if (incomingNbt.contains("radiant_damage_end")) {
						localNbt.putLong("radiant_damage_end", incomingNbt.getLong("radiant_damage_end"));
					}
					// 接收次要效果数据
					if (incomingNbt.contains("radiant_light_end")) {
						localNbt.putLong("radiant_light_end", incomingNbt.getLong("radiant_light_end"));
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

		ClientPlayNetworking.registerGlobalReceiver(ModMessages.BAMBOO_CUTTING_SYNC_ID, (client, handler, buf, responseSender) -> {
			client.execute(() -> {
				if (client.player != null) {
					// 强制重置客户端的攻击冷却计时器
					// 需确保你已有 LivingEntityAccessor 且能在客户端访问（通常 Accessor 是通用的）
					((LivingEntityAccessor) client.player).setLastAttackedTicks(client.player.age - 100);

					// 播放音效（可选，如果你希望只在触发时听到特殊声音）
					// client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_BAMBOO_BREAK, 1.0f, 1.5f);
				}
			});
		});
	}
}