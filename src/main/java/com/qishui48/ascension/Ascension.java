package com.qishui48.ascension;

import com.qishui48.ascension.block.TemporaryGlowingBlock;
import com.qishui48.ascension.block.entity.TemporaryGlowingBlockEntity;
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
import com.qishui48.ascension.util.SkillCooldownManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
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
	// 注册隐形发光方块
	public static Block TEMPORARY_GLOWING_BLOCK;
	public static BlockEntityType<TemporaryGlowingBlockEntity> TEMPORARY_GLOWING_BLOCK_ENTITY;

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
	public static final Identifier BOAT_ON_ICE = new Identifier(MOD_ID, "boat_on_ice");//冰上划船
	public static final Identifier BREW_WATER_BREATHING = new Identifier(MOD_ID, "brew_water_breathing");//炼制水肺药水
	public static final Identifier CRAFT_WATER_BREATHING_ARROW = new Identifier(MOD_ID, "craft_water_breathing_arrow");//做水肺箭
	public static final Identifier MOVE_UNDERWATER = new Identifier(MOD_ID, "move_underwater");//水下移动
	public static final Identifier KILL_CHARGED_CREEPER = new Identifier(MOD_ID, "kill_charged_creeper");//击杀闪电苦力怕
	public static final Identifier ASCEND_HEIGHT = new Identifier(MOD_ID, "ascend_height"); // 上升高度
	public static final Identifier KILL_ZOMBIE_AIR = new Identifier(MOD_ID, "kill_zombie_air"); // 空中杀僵尸
	public static final Identifier SURVIVE_ELYTRA_CRASH = new Identifier(MOD_ID, "survive_elytra_crash"); //坠机生还
	public static final Identifier ENCHANT_SWORD_THREE_TIMES = new Identifier(MOD_ID, "enchant_sword_three_times"); //强化剑3次
	public static final Identifier ENCHANT_WITH_LEVEL_30 = new Identifier(MOD_ID, "enchant_with_level_30"); //附30级的魔
	public static final Identifier TRADE_DIFFERENT_ENCHANTED_BOOKS = new Identifier(MOD_ID, "trade_different_enchanted_books"); //交易不同附魔书
	public static final Identifier PLACE_BLOCK_COUNT = new Identifier(MOD_ID, "place_block_count"); //放置方块
	public static final Identifier USE_ENDER_PEARL_COUNT = new Identifier(MOD_ID, "use_ender_pearl_count"); //使用末影珍珠
	public static final Identifier FIND_MUSHROOM_FIELDS = new Identifier(MOD_ID, "find_mushroom_fields"); //找到蘑菇岛
	public static final Identifier TAME_DOG_COUNT = new Identifier(MOD_ID, "tame_dog_count"); // 驯服狗狗
	public static final Identifier DOG_KILL_MOB_COUNT = new Identifier(MOD_ID, "dog_kill_mob_count"); //狗狗出击
	public static final Identifier EXPLORE_BASTION = new Identifier(MOD_ID, "explore_bastion"); // 探索不同的堡垒遗迹
	public static final Identifier SURVIVE_EXPLOSION = new Identifier(MOD_ID, "survive_explosion"); //在爆炸中幸存
	public static final Identifier KILL_GHAST_WITH_REFLECTION = new Identifier(MOD_ID, "kill_ghast_with_reflection"); //用火球反击恶魂
	public static final Identifier CRAFT_CLOCK_END = new Identifier(MOD_ID, "craft_clock_end"); // 在末地合成时钟
	public static final Identifier BREW_POTION_TYPE_COUNT = new Identifier(MOD_ID, "brew_potion_type_count"); // 炼制不同药水
	public static final Identifier ACTIVATE_BEACON_IN_END = new Identifier(MOD_ID, "activate_beacon_in_end"); // 在末地激活信标
	public static final Identifier FISH_PUFFERFISH_COUNT = new Identifier(MOD_ID, "fish_pufferfish_count"); // 钓河豚
	public static final Identifier FISH_RESPIRATION_BOOK_COUNT = new Identifier(MOD_ID, "fish_respiration_book_count"); // 钓水下呼吸

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
		Registry.register(Registries.CUSTOM_STAT, BOAT_ON_ICE, BOAT_ON_ICE);
		Registry.register(Registries.CUSTOM_STAT, BREW_WATER_BREATHING, BREW_WATER_BREATHING);
		Registry.register(Registries.CUSTOM_STAT, CRAFT_WATER_BREATHING_ARROW, CRAFT_WATER_BREATHING_ARROW);
		Registry.register(Registries.CUSTOM_STAT, MOVE_UNDERWATER, MOVE_UNDERWATER);
		Registry.register(Registries.CUSTOM_STAT, KILL_CHARGED_CREEPER, KILL_CHARGED_CREEPER);
		Registry.register(Registries.CUSTOM_STAT, ASCEND_HEIGHT, ASCEND_HEIGHT);
		Registry.register(Registries.CUSTOM_STAT, KILL_ZOMBIE_AIR, KILL_ZOMBIE_AIR);
		Registry.register(Registries.CUSTOM_STAT, SURVIVE_ELYTRA_CRASH, SURVIVE_ELYTRA_CRASH);
		Registry.register(Registries.CUSTOM_STAT, ENCHANT_SWORD_THREE_TIMES, ENCHANT_SWORD_THREE_TIMES);
		Registry.register(Registries.CUSTOM_STAT, ENCHANT_WITH_LEVEL_30, ENCHANT_WITH_LEVEL_30);
		Registry.register(Registries.CUSTOM_STAT, TRADE_DIFFERENT_ENCHANTED_BOOKS, TRADE_DIFFERENT_ENCHANTED_BOOKS);
		Registry.register(Registries.CUSTOM_STAT, PLACE_BLOCK_COUNT, PLACE_BLOCK_COUNT);
		Registry.register(Registries.CUSTOM_STAT, USE_ENDER_PEARL_COUNT, USE_ENDER_PEARL_COUNT);
		Registry.register(Registries.CUSTOM_STAT, FIND_MUSHROOM_FIELDS, FIND_MUSHROOM_FIELDS);
		Registry.register(Registries.CUSTOM_STAT, TAME_DOG_COUNT, TAME_DOG_COUNT);
		Registry.register(Registries.CUSTOM_STAT, DOG_KILL_MOB_COUNT, DOG_KILL_MOB_COUNT);
		Registry.register(Registries.CUSTOM_STAT, EXPLORE_BASTION, EXPLORE_BASTION);
		Registry.register(Registries.CUSTOM_STAT, SURVIVE_EXPLOSION, SURVIVE_EXPLOSION);
		Registry.register(Registries.CUSTOM_STAT, KILL_GHAST_WITH_REFLECTION, KILL_GHAST_WITH_REFLECTION);
		Registry.register(Registries.CUSTOM_STAT, CRAFT_CLOCK_END, CRAFT_CLOCK_END);
		Registry.register(Registries.CUSTOM_STAT, BREW_POTION_TYPE_COUNT, BREW_POTION_TYPE_COUNT);
		Registry.register(Registries.CUSTOM_STAT, ACTIVATE_BEACON_IN_END, ACTIVATE_BEACON_IN_END);
		Registry.register(Registries.CUSTOM_STAT, FISH_PUFFERFISH_COUNT, FISH_PUFFERFISH_COUNT);
		Registry.register(Registries.CUSTOM_STAT, FISH_RESPIRATION_BOOK_COUNT, FISH_RESPIRATION_BOOK_COUNT);
		SkillRegistry.registerAll();

		TEMPORARY_GLOWING_BLOCK = Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "temporary_light"),
				new TemporaryGlowingBlock(AbstractBlock.Settings.copy(Blocks.AIR).luminance(state -> 15).noCollision().dropsNothing()));

		// 2. 注册 BlockEntity
		TEMPORARY_GLOWING_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(MOD_ID, "temporary_light_entity"),
				BlockEntityType.Builder.create(TemporaryGlowingBlockEntity::new, TEMPORARY_GLOWING_BLOCK).build(null));

		// 踏足新维度的事件
		ModEvents.register();

		// 注册网络包
		ModMessages.registerC2SPackets();

		// 注册冷却与充能逻辑
		SkillCooldownManager.register();

		// 注册斗转星移-时间加速事件
		ServerTickEvents.END_SERVER_TICK.register(SkillEffectHandler::onServerTick);

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
		LOGGER.info("Ascension Mastery Initialized!");
	}
}