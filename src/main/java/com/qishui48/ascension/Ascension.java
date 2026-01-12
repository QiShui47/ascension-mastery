package com.qishui48.ascension;

import com.qishui48.ascension.command.SetSkillCommand;
import com.qishui48.ascension.command.SkillOpCommands;
import com.qishui48.ascension.enchantment.LifeStealEnchantment;
import com.qishui48.ascension.enchantment.SpecialBowEnchantment;
import com.qishui48.ascension.enchantment.SpeedStealEnchantment;
import com.qishui48.ascension.event.ModEvents;
import com.qishui48.ascension.network.ModMessages;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.util.PacketUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents; // 监听连接事件
import com.qishui48.ascension.util.IEntityDataSaver;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class Ascension implements ModInitializer {
	public static final String MOD_ID = "ascension";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 声明变量
	public static Enchantment HEALTH_STEAL;
	public static Enchantment SPEED_STEAL;
	public static Enchantment LIFE_FORCE;
	public static Enchantment POCKET_BUCKET;
	public static Enchantment DEATH_STAR_CANNON;
	public static SpecialBowEnchantment POCKET_GUARD;
	public static SpecialBowEnchantment POCKET_CAT;
	public static final Identifier PACKET_ID = new Identifier(MOD_ID, "sync_skill_data");
	public static final Identifier UNLOCK_REQUEST_ID = new Identifier(MOD_ID, "request_unlock_skill");

	// === 自定义统计数据 ID ===
	public static final Identifier BREW_FIRE_RES_POTION = new Identifier(MOD_ID, "brew_fire_res_potion"); //炼制抗火药水
	public static final Identifier SWIM_IN_LAVA = new Identifier(MOD_ID, "swim_in_lava"); //在岩浆中游泳
	public static final Identifier KILL_BURNING_SKELETON = new Identifier(MOD_ID, "kill_burning_skeleton"); //击杀着火小白
	public static final Identifier COLLECT_LOG_VARIANTS = new Identifier(MOD_ID, "collect_log_variants"); // 收集原木种类数
	public static final Identifier EXPLORE_MINESHAFT = new Identifier(MOD_ID, "explore_mineshaft");       // 探索废弃矿井
	public static final Identifier WALK_ON_BEDROCK = new Identifier(MOD_ID, "walk_on_bedrock"); //在基岩上行走
	public static final Identifier COLLECT_STAINED_GLASS = new Identifier(MOD_ID, "collect_stained_glass"); // 用于追踪染色玻璃数量
	public static final Identifier COOK_IN_SMOKER = new Identifier(MOD_ID, "cook_in_smoker"); // 烟熏炉烹饪
	public static final Identifier COLLECT_CROP_VARIANTS = new Identifier(MOD_ID, "collect_crop_variants"); // 农作物种类
	public static final Identifier COLLECT_HONEY = new Identifier(MOD_ID, "collect_honey");                 // 蜂蜜瓶
	public static final Identifier TRAVEL_OVERWORLD = new Identifier(MOD_ID, "travel_overworld");//主世界旅行
	public static final Identifier TRAVEL_NETHER = new Identifier(MOD_ID, "travel_nether");//下界旅行
	public static final Identifier TRAVEL_END = new Identifier(MOD_ID, "travel_end");//末地旅行

	@Override
	public void onInitialize() {
		// 最先初始化技能表
		//SkillRegistry.registerAll();
		// 实例化
		HEALTH_STEAL = new LifeStealEnchantment();
		SPEED_STEAL = new SpeedStealEnchantment();
		LIFE_FORCE = new Enchantment(Enchantment.Rarity.COMMON, EnchantmentTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND}) {
			@Override
			public int getMaxLevel() { return 1; }
		};
		POCKET_BUCKET = new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND}) {
			@Override
			public int getMaxLevel() { return 1; }
		};
		POCKET_GUARD = new SpecialBowEnchantment(); // 使用互斥基类
		POCKET_CAT = new SpecialBowEnchantment();
		DEATH_STAR_CANNON = new Enchantment(Enchantment.Rarity.RARE, EnchantmentTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND}) {
			@Override
			public int getMaxLevel() { return 1; }
		};

		// 注册
		Registry.register(Registries.ENCHANTMENT, new Identifier(MOD_ID, "life_steal"), HEALTH_STEAL);
		Registry.register(Registries.ENCHANTMENT, new Identifier(MOD_ID, "speed_steal"), SPEED_STEAL);
		Registry.register(Registries.ENCHANTMENT, new Identifier(MOD_ID, "death_star_cannon"), DEATH_STAR_CANNON);
		Registry.register(Registries.ENCHANTMENT, new Identifier(MOD_ID, "life_force"), LIFE_FORCE);
		Registry.register(Registries.ENCHANTMENT, new Identifier(MOD_ID, "pocket_bucket"), POCKET_BUCKET);
		Registry.register(Registries.ENCHANTMENT, new Identifier(MOD_ID, "pocket_guard"), POCKET_GUARD);
		Registry.register(Registries.ENCHANTMENT, new Identifier(MOD_ID, "pocket_cat"), POCKET_CAT);

		CommandRegistrationCallback.EVENT.register(SetSkillCommand::register);
		CommandRegistrationCallback.EVENT.register(SkillOpCommands::register);

		// 注册自定义统计
		Registry.register(Registries.CUSTOM_STAT, BREW_FIRE_RES_POTION, BREW_FIRE_RES_POTION);
		Registry.register(Registries.CUSTOM_STAT, SWIM_IN_LAVA, SWIM_IN_LAVA);
		Registry.register(Registries.CUSTOM_STAT, KILL_BURNING_SKELETON, KILL_BURNING_SKELETON);
		Registry.register(Registries.CUSTOM_STAT, COLLECT_LOG_VARIANTS, COLLECT_LOG_VARIANTS);
		Registry.register(Registries.CUSTOM_STAT, EXPLORE_MINESHAFT, EXPLORE_MINESHAFT);
		Registry.register(Registries.CUSTOM_STAT, WALK_ON_BEDROCK, WALK_ON_BEDROCK);
		Registry.register(Registries.CUSTOM_STAT, COLLECT_STAINED_GLASS, COLLECT_STAINED_GLASS);
		Registry.register(Registries.CUSTOM_STAT, COOK_IN_SMOKER, COOK_IN_SMOKER);
		Registry.register(Registries.CUSTOM_STAT, COLLECT_CROP_VARIANTS, COLLECT_CROP_VARIANTS);
		Registry.register(Registries.CUSTOM_STAT, COLLECT_HONEY, COLLECT_HONEY);
		Registry.register(Registries.CUSTOM_STAT, TRAVEL_OVERWORLD, TRAVEL_OVERWORLD);
		Registry.register(Registries.CUSTOM_STAT, TRAVEL_NETHER, TRAVEL_NETHER);
		Registry.register(Registries.CUSTOM_STAT, TRAVEL_END, TRAVEL_END);
		SkillRegistry.registerAll();

		//踏足新维度的事件
		ModEvents.register();

		// 注册网络包
		ModMessages.registerC2SPackets();

		// 注册登录同步事件 - 玩家加入服务器时
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			// 先进行平衡性检查
			PacketUtils.checkSkillPointBalance(handler.getPlayer());
			// 刷新各种数据
			PacketUtils.syncSkillData(handler.getPlayer());
			SkillEffectHandler.refreshAttributes(handler.getPlayer());
		});
		// 玩家重生
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PacketUtils.syncSkillData(newPlayer);
			// 新增：刷新属性
			SkillEffectHandler.refreshAttributes(newPlayer);
		});
		// === 死亡数据保留===
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			// 无论是因为死亡(alive=false)还是哪怕从末地回来(alive=true)，都要搬运数据
			IEntityDataSaver oldData = (IEntityDataSaver) oldPlayer;
			IEntityDataSaver newData = (IEntityDataSaver) newPlayer;

			// 把旧数据的 NBT 完整拷贝给新数据
			newData.getPersistentData().copyFrom(oldData.getPersistentData());
		});
		LOGGER.info("Template Mod Initialized!");
	}
}