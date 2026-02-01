package com.qishui48.ascension.skill;

import com.qishui48.ascension.Ascension;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import java.util.*;
import java.util.stream.Collectors;

public class SkillRegistry {
    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    public static void registerAll() {
        SKILLS.clear();

        // === Root (Tier 0) ===
        register(new Skill("root", Items.BOOK, 0, null, 1, 1));

        // === Tier 1 ===
        register(new Skill("combat", Items.IRON_SWORD, 1, "root", 1, 2));
        register(new Skill("survival",  Items.APPLE, 1, "root", 1, 2));
        register(new Skill("mining",  Items.WOODEN_PICKAXE, 1, "root", 1, 2));
        register(new Skill("miscellaneous",  Items.STICK, 1, "root", 1, 2));

        // ==========================================================
        // === Survival Branch (生存系) ===
        // ==========================================================

        // 龙舟划手 (Tier 2)
        register(new Skill("dragon_boat_rower", Items.OAK_BOAT, 2, "survival", 2, 10, 15)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.SWIM_ONE_CM, 10000, "criterion.ascension.move_underwater").setDisplayDivisor(100.0))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.BOAT_ON_ICE, 10000, "criterion.ascension.boat_on_ice").setDisplayDivisor(100.0)));

        // 缸中之脑 (Tier 3)
        register(new Skill("brain_in_a_jar", Items.GLASS, 3, "dragon_boat_rower", 5, 10, 10, 15, 15, 15)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.BREW_WATER_BREATHING, 1, "criterion.ascension.brew_water_breathing"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.CRAFT_WATER_BREATHING_ARROW, 1, "criterion.ascension.craft_tipped_arrow"))
                .addUpgradeCriterion(5, new UnlockCriterion(Stats.PICKED_UP, Items.TRIDENT, 1, "criterion.ascension.obtain_trident")));

        // 肾上腺素 (Tier 2)
        register(new Skill("adrenaline_burst", Items.GLISTERING_MELON_SLICE, 2, "survival", 2, 5));

        // 亢奋 (Tier 3)
        register(new Skill("excitement", Items.SUGAR, 3, "adrenaline_burst", 2, 10, 15)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.BREAD, 16, "criterion.ascension.eat_bread"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.USED, Items.POISONOUS_POTATO, 1, "criterion.ascension.eat_poison_potato")));

        // 生命提升 (Tier 4) - 保持 OR 关系
        register(new Skill("health_boost", Items.GOLDEN_APPLE, 4, "excitement", 5, 5, 15, 25, 35, 45)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.APPLE, 1, "criterion.ascension.eat_apple"))
                .addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.COW, 1, "criterion.ascension.kill_cow"))
                .addUpgradeCriterion(5, new UnlockCriterion(Stats.CUSTOM, Ascension.FIND_MUSHROOM_FIELDS, 1, "criterion.ascension.find_mushroom_island")));

        // 脚底抹油 (Tier 2)
        register(new Skill("swift_move", Items.LEATHER_BOOTS, 2, "survival", 3, 5, 7, 10));

        // 人力发电机 (Human Dynamo) - 链式重构
        register(new Skill("human_dynamo", Items.IRON_BOOTS, 3, "swift_move", 3, 3, 5, 8)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.TRAVEL_OVERWORLD, 200000, "criterion.ascension.travel_overworld").setDisplayDivisor(100.0))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.TRAVEL_NETHER, 200000, "criterion.ascension.travel_nether").setDisplayDivisor(100.0))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.TRAVEL_END, 200000, "criterion.ascension.travel_end").setDisplayDivisor(100.0)));

        // 御剑飞行 (Sword Flight)
        register(new Skill("sword_flight", Items.DIAMOND_SWORD, 4, "human_dynamo", 2, 20, 30)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.SURVIVE_ELYTRA_CRASH, 1, "criterion.ascension.survive_elytra_crash"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.ENCHANT_SWORD_THREE_TIMES, 1, "criterion.ascension.enchant_sword_three_times")));

        // 糖分主理人 (Sugar Master) - 应用 AND 逻辑
        register(new Skill("sugar_master", Items.CAKE, 2, "survival", 3, 5, 10, 15)
                .addCriteriaGroup( // 1级条件组 (AND)
                        new UnlockCriterion(Stats.CUSTOM, Ascension.COLLECT_CROP_VARIANTS, 4, "criterion.ascension.collect_crops"),
                        new UnlockCriterion(Stats.CRAFTED, Items.BREAD, 32, "criterion.ascension.craft_bread")
                )
                .addUpgradeCriteriaGroup(3, // 3级条件组 (AND)
                        new UnlockCriterion(Stats.CUSTOM, Ascension.COLLECT_HONEY, 8, "criterion.ascension.collect_honey"),
                        new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.PUMPKIN, 32, "criterion.ascension.harvest_pumpkin")
                ));

        // 火锅食客 (Tier 2)
        register(new Skill("hotpot_diner", Items.COOKED_BEEF, 2, "survival", 3, 5, 7, 10)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.COOK_IN_SMOKER, 16, "criterion.ascension.cook_smoker"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.TRAVEL_NETHER, 100000, "criterion.ascension.travel_nether").setDisplayDivisor(100.0)));

        // 饥饿耐受 (Tier 3)
        register(new Skill("hunger_tolerance", Items.COOKED_CHICKEN, 3, "hotpot_diner", 1, 15)
                .withVisualParent("sugar_master"));

        // 饥饿体质 (Tier 4)
        register(new Skill("hunger_constitution", Items.RABBIT_STEW, 4, "hunger_tolerance", 2, 15));

        // 饥饿爆发 (Tier 5, 隐藏技能)
        register(new Skill("hunger_burst", Items.RABBIT_FOOT, 5, "hunger_constitution", 1, true, 20));


        // ==========================================================
        // === Combat Branch (战斗系) ===
        // ==========================================================

        //祝融之力
        register(new Skill("zhu_rong", Items.MAGMA_CREAM, 4, "battle_instinct", 4, 15, 20, 25, 30)
                .addCriterion(new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.NETHER_WART, 64, "criterion.ascension.harvest_nether_wart"))
                .addUpgradeCriterion(4, new UnlockCriterion(Stats.CUSTOM, Ascension.EXPLORE_BASTION, 2, "criterion.ascension.explore_bastions")));

        // 火焰抵抗
        register(new Skill("fire_resistance", Items.MAGMA_CREAM, 2, "zhu_rong", 2, 15, 25)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.BREW_FIRE_RES_POTION, 1, "criterion.ascension.brew_fire_res")));

        // 火焰感染
        register(new Skill("fire_infection", Items.BLAZE_POWDER, 3, "fire_resistance", 1, 15)
                .setMutex("fire_res")
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.KILL_BURNING_SKELETON, 1, "criterion.ascension.kill_burning_skeleton")));

        // 火焰免疫
        register(new Skill("fire_res", Items.LAVA_BUCKET, 3, "fire_resistance", 1, 45)
                .setMutex("fire_infection")
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.DAMAGE_TAKEN, 2000, "criterion.ascension.take_fire_damage").setDisplayDivisor(10.0)));

        // 热能引擎
        register(new Skill("thermal_dynamo", Items.CAMPFIRE, 4, "fire_infection", 1, 15)
                .withVisualParent("fire_res")
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.SWIM_IN_LAVA, 20000, "criterion.ascension.swim_lava").setDisplayDivisor(100.0)));

        // 忧郁人格 (Melancholic Personality) - 父节点: combat (战斗入门)
        register(new Skill("melancholic_personality", Items.GHAST_TEAR, 2, "battle_instinct", 2, 10, 15)
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.KILL_CHARGED_CREEPER, 1, "criterion.ascension.kill_charged_creeper")));

        // 舍身一击 (Sacrificial Strike) - 父节点: rocket_boost (空中推进)
        // 注意：rocket_boost 是 Combat 分支下的
        register(new Skill("sacrificial_strike", Items.IRON_AXE, 3, "rocket_boost", 2, 5, 10)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.ASCEND_HEIGHT, 200000, "criterion.ascension.ascend_height").setDisplayDivisor(100.0))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.KILL_ZOMBIE_AIR, 50, "criterion.ascension.kill_zombie_air")));

        // === [新增] 华丽掉落 ===
        //register(new Skill("graceful_fall", Items.FEATHER, 2, "combat", 3, 5,5,10) // 假设花费为5
        //       .addUpgradeCriterion(3, new UnlockCriterion("stat.ascension.fall_damage", 200, "criterion.ascension.accumulate_fall_damage"))
        //);

        // 空中推进
        register(new Skill("rocket_boost", Items.FIREWORK_ROCKET, 2, "combat", 2, 5, 20)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.JUMP, 100, "criterion.ascension.jump"))
                .addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.PHANTOM, 1, "criterion.ascension.kill_phantom"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Stats.AVIATE_ONE_CM, 10000, "criterion.ascension.fly_elytra").setDisplayDivisor(100.0)));

        // 蓄力跳
        register(new Skill("charged_jump", Items.FIREWORK_STAR, 3, "rocket_boost", 2, 20, 10));

        // 战斗本能
        register(new Skill("battle_instinct", Items.GOLDEN_SWORD, 2, "combat", 3, 5, 10, 15));

        // 狼群领袖 (Wolf Pack Leader)
        register(new Skill("wolf_pack", Items.BONE, 3, "combat", 3, 10, 20, 30)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.TAME_DOG_COUNT, 2, "criterion.ascension.tame_2_dogs"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.DOG_KILL_MOB_COUNT, 1, "criterion.ascension.dog_kill_mob")));

        // 斩竹 (Bamboo Cutting)
        register(new Skill("bamboo_cutting", Items.BAMBOO, 4, "battle_instinct", 4, 10, 15, 20, 25)
                .addCriterion(new UnlockCriterion(Stats.MINED, Blocks.BAMBOO, 7, "criterion.ascension.mine_7_bamboo"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.MINED, Blocks.BAMBOO, 27, "criterion.ascension.mine_27_bamboo"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.MINED, Blocks.BAMBOO, 37, "criterion.ascension.mine_37_bamboo"))
                .addUpgradeCriterion(4, new UnlockCriterion(Stats.MINED, Blocks.BAMBOO, 47, "criterion.ascension.mine_47_bamboo")));

        // ==========================================================
        // === Mining Branch (挖掘系) ===
        // ==========================================================
        register(new Skill("pocket_furnace", Items.FURNACE, 2, "hephaestus_favor", 2, 15, 20)
                .withVisualParent("academic_miner")
        );
        // 赫菲斯托斯眷顾
        register(new Skill("hephaestus_favor", Items.QUARTZ, 3, "miner_frenzy", 3, 15, 15, 20)
                .addCriterion(new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.DEEPSLATE, 64, "criterion.ascension.mine_deepslate"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.WALK_ON_BEDROCK, 10000, "criterion.ascension.walk_bedrock").setDisplayDivisor(100.0)));

        // 淘金 (Gold Panning) - 链式重构
        // 使用先定义变量再处理循环的方式，保持代码可读性，这在链式编程中是可以接受的妥协
        Skill goldPanning = new Skill("gold_panning", Items.GOLD_NUGGET, 2, "mining", 8, 5, 10, 10, 10, 10, 10, 10, 10)
                .addCriterion(new UnlockCriterion(Stats.CRAFTED, Items.GLASS, 64, "criterion.ascension.smelt_glass"));
        for (int i = 2; i <= 8; i++) {
            goldPanning.addUpgradeCriterion(i, new UnlockCriterion(Stats.CUSTOM, Ascension.COLLECT_STAINED_GLASS, (i - 1) * 2, "criterion.ascension.collect_stained_glass"));
        }
        register(goldPanning);

        // 森林主宰
        register(new Skill("lumberjack", Items.IRON_AXE, 2, "mining", 3, 5, 10, 10)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.COLLECT_LOG_VARIANTS, 4, "criterion.ascension.collect_logs"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.COLLECT_LOG_VARIANTS, 6, "criterion.ascension.collect_logs"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.COLLECT_LOG_VARIANTS, 8, "criterion.ascension.collect_logs")));

        // 矿工狂热
        register(new Skill("miner_frenzy", Items.GOLDEN_PICKAXE, 2, "mining", 3, 5, 10, 10)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.STONE_BRICKS, 64, "criterion.ascension.use_stone_bricks"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.EXPLORE_MINESHAFT, 1, "criterion.ascension.find_mineshaft"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.AMETHYST_CLUSTER, 1, "criterion.ascension.mine_amethyst")));

        // 学术派矿工 - 链式重构
        register(new Skill("academic_miner", Items.IRON_PICKAXE, 3, "miner_frenzy", 2, 10, 15)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.MINECART_ONE_CM, 10000, "criterion.ascension.minecart_travel").setDisplayDivisor(100.0))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.EMERALD_ORE, 1, "criterion.ascension.mine_emerald_ore"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.DEEPSLATE_EMERALD_ORE, 1, "criterion.ascension.mine_deepslate_emerald_ore"))); // 这里两个是 OR 关系，如果你想变成 AND，改成 addUpgradeCriteriaGroup

        // === 黄金屋 (Golden House) ===
        register(new Skill("golden_house", Items.ENCHANTED_BOOK, 2, "miscellaneous", 3, 10, 20, 25)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.ENCHANT_WITH_LEVEL_30, 1, "criterion.ascension.enchant_lv30"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.TRADE_DIFFERENT_ENCHANTED_BOOKS, 4, "criterion.ascension.trade_4_books")));

        // === 念力 (Telekinesis) ===
        register(new Skill("telekinesis", Items.ENDER_EYE, 2, "mining", 2, 10, 15)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.PLACE_BLOCK_COUNT, 2000, "criterion.ascension.place_block_count"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.USE_ENDER_PEARL_COUNT, 64, "criterion.ascension.use_64_pearls")));

        // ==========================================================
        // === Active Skills (主动技能) ===
        // ==========================================================

        // 雷霆万钧 (Thunder Clap)
        ActiveSkill thunderClap = new ActiveSkill(
                "thunder_clap",
                Items.LIGHTNING_ROD,
                3,
                "melancholic_personality",
                3,
                new int[]{1, 2, 2}, // maxCharges
                new int[]{1200, 1200, 900}, // 冷却
                10, 10, 10 // costs
        );
        thunderClap.addIngredient(Items.COPPER_INGOT, 1, false, 0)
                .addIngredient(Items.DIAMOND, 1, true, 3);
        thunderClap.addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.KILL_CHARGED_CREEPER, 1, "criterion.ascension.kill_charged_creeper"));
        thunderClap.setBehavior(SkillActionHandler::executeThunderClap); // 保持行为绑定
        register(thunderClap);

        // 闪现 (Blink)
        ActiveSkill blink = new ActiveSkill(
                "blink",
                Items.ENDER_PEARL, // 图标
                2, // Tier
                "miscellaneous", // Parent
                4, // Max Level
                new int[]{1, 2, 2, 3}, // Charges: 1, 2, 2, 3
                new int[]{400, 400, 400, 400}, // Cooldown: 20s
                10, 10, 10, 10 // Costs
        );
        blink.addIngredient(Items.COPPER_INGOT, 1, false, 0)
                .addIngredient(Items.ENDER_PEARL, 1, true, 1); // bonusEffect=1 (flag for double range)
        blink.setBehavior(SkillActionHandler::executeBlink);
        register(blink);

        // 不败金身 (Invincible Body)
        ActiveSkill invincibleBody = new ActiveSkill(
                "invincible_body",
                Items.GOLDEN_CHESTPLATE,
                4, // Tier 4 (高级技能)
                "health_boost", // 父节点
                2, // Max Level
                new int[]{2, 2}, // Charges: 都是2次
                new int[]{1200, 900}, // Cooldown: Lv1=60s(1200t), Lv2=45s(900t)
                10, 10 // Costs
        );
        invincibleBody.addIngredient(Items.COPPER_INGOT, 1, false, 0)
                .addIngredient(Items.GOLD_INGOT, 1, true, 60);
        invincibleBody.addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.SURVIVE_EXPLOSION, 1, "criterion.ascension.survive_explosion"));
        invincibleBody.setBehavior(SkillActionHandler::executeInvincibleBody);
        register(invincibleBody);

        // 龙焰 (Dragon Flame)
        ActiveSkill dragonFlame = new ActiveSkill(
                "dragon_flame",
                Items.DRAGON_BREATH,
                3,
                "thermal_dynamo",
                3,
                new int[]{1, 1, 1}, // 最大充能 1 次
                new int[]{400, 400, 400}, // 基础冷却 20s
                10, 10, 10 // 消耗
        );
        dragonFlame.addIngredient(Items.COPPER_INGOT, 1, false, 0);
        dragonFlame.addIngredient(Items.GHAST_TEAR, 1, true, 1);
        dragonFlame.addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.KILL_GHAST_WITH_REFLECTION, 1, "criterion.ascension.kill_ghast_reflection"));
        dragonFlame.addUpgradeCriterion(3, new UnlockCriterion(Stats.PICKED_UP, Items.DRAGON_BREATH, 1, "criterion.ascension.pickup_dragon_breath"));
        dragonFlame.setBehavior(SkillActionHandler::executeDragonBreath);
        register(dragonFlame);

        // 光耀化身 (Radiant Avatar)
        ActiveSkill radiantAvatar = new ActiveSkill(
                "radiant_avatar",
                Items.NETHER_STAR, // 图标
                3, // Tier (雷霆万钧是3，这个也是3或4，父节点是 thunder_clap)
                "thunder_clap",
                2, // Max Level
                new int[]{1, 1}, // Charges
                new int[]{900, 900}, // Cooldown: 45s
                20, 30 // Costs
        );
        radiantAvatar.addIngredient(Items.BLAZE_ROD, 3, false, 0);
        radiantAvatar.addCriterion(new UnlockCriterion("undead_type_count", 8, "criterion.ascension.kill_undead_types"));
        radiantAvatar.addUpgradeCriterion(2, new UnlockCriterion(Stats.PICKED_UP, Items.NETHER_STAR, 1, "criterion.ascension.obtain_nether_star"));

        radiantAvatar.setBehavior(SkillActionHandler::executeRadiantAvatar);
        register(radiantAvatar);

        // 计算布局
        calculateLayout();
    }

    private static void register(Skill skill) { SKILLS.put(skill.id, skill); }
    public static Skill get(String id) { return SKILLS.get(id); }
    public static Collection<Skill> getAll() { return SKILLS.values(); }
    public static Set<String> getIds() { return SKILLS.keySet(); }

    private static final int NODE_WIDTH = 66;
    private static final int NODE_SPACING_Y = 50;

    private static void calculateLayout() {
        List<Skill> roots = SKILLS.values().stream()
                .filter(s -> s.parentId == null)
                .collect(Collectors.toList());

        for (Skill root : roots) {
            calculateWidth(root);
        }

        int currentX = 0;
        for (Skill root : roots) {
            assignPosition(root, currentX + (root.subTreeWidth / 2), 0);
            currentX += root.subTreeWidth + 10;
        }

        // 后处理 (Post-processing)，修正那些需要“居中”的节点
        adjustVisualPositions();
    }

    private static void calculateWidth(Skill node) {
        List<Skill> children = getChildren(node);
        if (children.isEmpty()) {
            node.subTreeWidth = NODE_WIDTH;
        } else {
            int sumWidth = 0;
            for (Skill child : children) {
                calculateWidth(child);
                sumWidth += child.subTreeWidth;
            }
            node.subTreeWidth = sumWidth;
        }
    }

    private static void assignPosition(Skill node, int x, int y) {
        node.x = x;
        node.y = y;
        List<Skill> children = getChildren(node);
        if (children.isEmpty()) return;

        int startX = x - (node.subTreeWidth / 2);
        int currentChildX = startX;
        for (Skill child : children) {
            int childCenterX = currentChildX + (child.subTreeWidth / 2);
            assignPosition(child, childCenterX, y + NODE_SPACING_Y);
            currentChildX += child.subTreeWidth;
        }
    }

    private static List<Skill> getChildren(Skill parent) {
        return SKILLS.values().stream()
                .filter(s -> Objects.equals(s.parentId, parent.id))
                .collect(Collectors.toList());
    }

    private static void adjustVisualPositions() {
        // 遍历所有技能，寻找有 visualParents 的技能
        for (Skill skill : SKILLS.values()) {
            if (!skill.visualParents.isEmpty()) {
                List<Skill> allParents = new ArrayList<>();

                // 加入原本的逻辑父节点
                if (skill.parentId != null) {
                    Skill logicalParent = get(skill.parentId);
                    if (logicalParent != null) allParents.add(logicalParent);
                }

                // 加入额外的视觉父节点
                for (String pid : skill.visualParents) {
                    Skill vp = get(pid);
                    if (vp != null) allParents.add(vp);
                }

                if (!allParents.isEmpty()) {
                    // 计算所有父节点的 X 坐标平均值
                    int totalX = 0;
                    for (Skill p : allParents) {
                        totalX += p.x;
                    }
                    int averageX = totalX / allParents.size();

                    // 强行修正当前技能的 X 坐标
                    // 注意：这里可能需要递归调整它的子节点，但为了简单，
                    // 我们假设汇聚节点通常是叶子节点或独立分支的开始。
                    // 如果需要连带移动子树，需要计算 deltaX 并应用到所有 children。
                    int deltaX = averageX - skill.x;
                    shiftSubTree(skill, deltaX);
                }
            }
        }
    }

    // 辅助方法：平移整个子树
    private static void shiftSubTree(Skill root, int deltaX) {
        root.x += deltaX;
        for (Skill child : getChildren(root)) {
            shiftSubTree(child, deltaX);
        }
    }
}