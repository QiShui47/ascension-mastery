package com.qishui48.ascension.skill;

import com.qishui48.ascension.Ascension;
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

        // ==========================================================
        // === Survival Branch (生存系) ===
        // ==========================================================

        // 生命提升 (Tier 2)
        register(new Skill("health_boost", Items.GOLDEN_APPLE, 2, "survival", 5, 5, 15, 25, 35, 45)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.APPLE, 1, "criterion.ascension.eat_apple"))
                .addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.COW, 1, "criterion.ascension.kill_cow")));

        // 肾上腺素 (Tier 2)
        register(new Skill("adrenaline_burst", Items.GLISTERING_MELON_SLICE, 2, "survival", 2, 5));

        // 亢奋 (Tier 3)
        register(new Skill("excitement", Items.SUGAR, 3, "adrenaline_burst", 2, 10, 15)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.BREAD, 16, "criterion.ascension.eat_bread"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.USED, Items.POISONOUS_POTATO, 1, "criterion.ascension.eat_poison_potato")));

        // 脚底抹油 (Tier 2)
        register(new Skill("swift_move", Items.LEATHER_BOOTS, 2, "survival", 3, 5, 7, 10));

        // 火锅食客 (Tier 2)
        register(new Skill("hotpot_diner", Items.COOKED_BEEF, 2, "survival", 3, 5, 7, 10)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.COOK_IN_SMOKER, 16, "criterion.ascension.cook_smoker"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.TRAVEL_NETHER, 100000, "criterion.ascension.travel_nether").setDisplayDivisor(100.0)));

        // 饥饿耐受 (Tier 3)
        register(new Skill("hunger_tolerance", Items.COOKED_CHICKEN, 3, "hotpot_diner", 1, 15));

        // 饥饿体质 (Tier 4)
        register(new Skill("hunger_constitution", Items.ROTTEN_FLESH, 4, "hunger_tolerance", 2, 15));

        // 饥饿爆发 (Tier 5, 隐藏技能)
        register(new Skill("hunger_burst", Items.RABBIT_FOOT, 5, "hunger_constitution", 1, true, 20));


        // ==========================================================
        // === Combat Branch (战斗系) ===
        // ==========================================================

        // --- 火焰系分支 (重构) ---

        // 1. 火焰抵抗 (Fire Resistance) - Tier 2 (前置)
        register(new Skill("fire_resistance", Items.MAGMA_CREAM, 2, "combat", 2, 15, 25)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.BREW_FIRE_RES_POTION, 1, "criterion.ascension.brew_fire_res")));

        // 2. 左分支：火焰感染 (Fire Infection) - Tier 3
        register(new Skill("fire_infection", Items.BLAZE_POWDER, 3, "fire_resistance", 1, 15)
                .setMutex("fire_res") // 与火焰免疫互斥
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.KILL_BURNING_SKELETON, 1, "criterion.ascension.kill_burning_skeleton")));

        // 3. 右分支：火焰免疫 (Fire Immunity) - Tier 3 (沿用旧ID fire_res)
        register(new Skill("fire_res", Items.LAVA_BUCKET, 3, "fire_resistance", 1, 45) // 从 Tier 2 移动到 Tier 3
                .setMutex("fire_infection") // 与火焰感染互斥
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.DAMAGE_TAKEN, 2000, "criterion.ascension.take_fire_damage").setDisplayDivisor(10.0)));

        // 4. 汇聚节点：热能引擎 (Thermal Dynamo) - Tier 4
        // 主父节点设为 "fire_infection" (用于确定它画在左边分支的下方)
        // 额外父节点设为 "fire_res" (右边也会画一条线连过来)
        register(new Skill("thermal_dynamo", Items.CAMPFIRE, 4, "fire_infection", 1, 15)
                .withVisualParent("fire_res")
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.SWIM_IN_LAVA, 20000, "criterion.ascension.swim_lava").setDisplayDivisor(100.0)));


        // --- 机动系分支 ---

        // 空中推进 (Tier 2)
        register(new Skill("rocket_boost", Items.FIREWORK_ROCKET, 2, "combat", 2, 5,20)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.JUMP, 100, "criterion.ascension.jump"))
                .addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.PHANTOM, 1, "criterion.ascension.kill_phantom"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Stats.AVIATE_ONE_CM, 10000, "criterion.ascension.fly_elytra").setDisplayDivisor(100.0)));

        // 蓄力跳 (Tier 3)
        register(new Skill("charged_jump", Items.FIREWORK_STAR, 3, "rocket_boost", 2, 20, 10));

        // 战斗本能 (Tier 2)
        register(new Skill("battle_instinct", Items.GOLDEN_SWORD, 2, "combat", 3, 5,10,15));


        // ==========================================================
        // === Mining Branch (挖掘系) ===
        // ==========================================================
        register(new Skill("pocket_furnace", Items.FURNACE, 2, "mining", 2, 15,20));

        // 赫菲斯托斯眷顾 (Hephaestus's Favor)
        register(new Skill("hephaestus_favor", Items.QUARTZ, 3, "miner_frenzy", 3, 15, 15, 20)
                .addCriterion(new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.DEEPSLATE, 64, "criterion.ascension.mine_deepslate"))
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.CUSTOM, Ascension.WALK_ON_BEDROCK, 10000, "criterion.ascension.walk_bedrock").setDisplayDivisor(100.0)));

        // 淘金 (Gold Panning)
        Skill goldPanning = new Skill("gold_panning", Items.GOLD_NUGGET, 2, "mining", 8, 5, 10, 10, 10, 10, 10, 10, 10);
        goldPanning.addCriterion(new UnlockCriterion(Stats.CRAFTED, Items.GLASS, 64, "criterion.ascension.smelt_glass"));
        for (int i = 2; i <= 8; i++) {
            // 公式：(Level - 1) * 2
            int targetCount = (i - 1) * 2;
            goldPanning.addUpgradeCriterion(i, new UnlockCriterion(Stats.CUSTOM, Ascension.COLLECT_STAINED_GLASS, targetCount, "criterion.ascension.collect_stained_glass"));
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
                // Lv2 条件：探索矿井
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.EXPLORE_MINESHAFT, 1, "criterion.ascension.find_mineshaft"))
                // Lv3 条件：挖掘紫水晶簇
                .addUpgradeCriterion(3, new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.AMETHYST_CLUSTER, 1, "criterion.ascension.mine_amethyst")));

        // 学术派矿工 (Academic Miner)
        Skill academicMiner = new Skill("academic_miner", Items.IRON_PICKAXE, 3, "miner_frenzy", 2, 5, 10);
        academicMiner.addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.MINECART_ONE_CM, 10000, "criterion.ascension.minecart_travel").setDisplayDivisor(100.0));
        academicMiner.addUpgradeCriterion(2, new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.EMERALD_ORE, 1, "criterion.ascension.mine_emerald_ore"));
        academicMiner.addUpgradeCriterion(2, new UnlockCriterion(Stats.MINED, net.minecraft.block.Blocks.DEEPSLATE_EMERALD_ORE, 1, "criterion.ascension.mine_emerald_ore"));
        register(academicMiner);

        // 计算布局
        calculateLayout();
    }

    private static void register(Skill skill) { SKILLS.put(skill.id, skill); }
    public static Skill get(String id) { return SKILLS.get(id); }
    public static Collection<Skill> getAll() { return SKILLS.values(); }
    public static Set<String> getIds() { return SKILLS.keySet(); }

    private static final int NODE_WIDTH = 60;
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