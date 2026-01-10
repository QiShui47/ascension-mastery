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
        register(new Skill("combat", Items.IRON_SWORD, 1, "root", 1, 1));
        register(new Skill("survival",  Items.APPLE, 1, "root", 1, 1));
        register(new Skill("mining",  Items.IRON_PICKAXE, 1, "root", 1, 1));

        // ==========================================================
        // === Survival Branch (生存系) ===
        // ==========================================================

        // 生命提升 (Tier 2)
        register(new Skill("health_boost", Items.GOLDEN_APPLE, 2, "survival", 5, 2, 5, 10, 15, 20)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.APPLE, 1, "criterion.ascension.eat_apple"))
                .addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.COW, 1, "criterion.ascension.kill_cow")));

        // 肾上腺素 (Tier 2)
        register(new Skill("adrenaline_burst", Items.GLISTERING_MELON_SLICE, 2, "survival", 2, 2));

        // 亢奋 (Tier 3)
        register(new Skill("excitement", Items.SUGAR, 3, "adrenaline_burst", 2, 5, 5)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.BREAD, 16, "criterion.ascension.eat_bread"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.USED, Items.POISONOUS_POTATO, 1, "criterion.ascension.eat_poison_potato")));

        // 移动速度 (Tier 2)
        register(new Skill("swift_move", Items.LEATHER_BOOTS, 2, "survival", 3, 1, 2, 3));

        // 饥饿耐受 (Tier 3)
        register(new Skill("hunger_tolerance", Items.COOKED_BEEF, 3, "survival", 1, 4));

        // 饥饿体质 (Tier 4)
        register(new Skill("hunger_constitution", Items.ROTTEN_FLESH, 4, "hunger_tolerance", 2, 3));

        // 饥饿爆发 (Tier 5, 隐藏技能)
        register(new Skill("hunger_burst", Items.RABBIT_FOOT, 5, "hunger_constitution", 1, true, 1));


        // ==========================================================
        // === Combat Branch (战斗系) ===
        // ==========================================================

        // --- 火焰系分支 (重构) ---

        // 1. 火焰抵抗 (Fire Resistance) - Tier 2 (前置)
        register(new Skill("fire_resistance", Items.MAGMA_CREAM, 2, "combat", 2, 5, 5)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.BREW_FIRE_RES_POTION, 1, "criterion.ascension.brew_fire_res"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Ascension.SWIM_IN_LAVA, 10000, "criterion.ascension.swim_lava").setDisplayDivisor(100.0)));

        // 2. 左分支：火焰感染 (Fire Infection) - Tier 3
        register(new Skill("fire_infection", Items.BLAZE_POWDER, 3, "fire_resistance", 1, 5)
                .setMutex("fire_res") // 与火焰免疫互斥
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.KILL_BURNING_SKELETON, 1, "criterion.ascension.kill_burning_skeleton")));

        // 3. 右分支：火焰免疫 (Fire Immunity) - Tier 3 (沿用旧ID fire_res)
        register(new Skill("fire_res", Items.LAVA_BUCKET, 3, "fire_resistance", 1, 5) // 从 Tier 2 移动到 Tier 3
                .setMutex("fire_infection") // 与火焰感染互斥
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.DAMAGE_TAKEN, 2000, "criterion.ascension.take_fire_damage").setDisplayDivisor(10.0)));

        // 4. 汇聚节点：热能引擎 (Thermal Dynamo) - Tier 4
        // 主父节点设为 "fire_infection" (用于确定它画在左边分支的下方)
        // 额外父节点设为 "fire_res" (右边也会画一条线连过来)
        register(new Skill("thermal_dynamo", Items.CAMPFIRE, 4, "fire_infection", 1, 5)
                .addParent("fire_res")
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Ascension.SWIM_IN_LAVA, 5000, "criterion.ascension.swim_lava").setDisplayDivisor(100.0)));


        // --- 机动系分支 ---

        // 空中推进 (Tier 2)
        register(new Skill("rocket_boost", Items.FIREWORK_ROCKET, 2, "combat", 2, 5)
                .addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.JUMP, 100, "criterion.ascension.jump"))
                .addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.PHANTOM, 1, "criterion.ascension.kill_phantom"))
                .addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Stats.AVIATE_ONE_CM, 10000, "criterion.ascension.fly_elytra").setDisplayDivisor(100.0)));

        // 蓄力跳 (Tier 3)
        register(new Skill("charged_jump", Items.FIREWORK_STAR, 3, "rocket_boost", 2, 2, 5));

        // 战斗本能 (Tier 2)
        register(new Skill("battle_instinct", Items.GOLDEN_SWORD, 2, "combat", 3, 1));


        // ==========================================================
        // === Mining Branch (挖掘系) ===
        // ==========================================================
        register(new Skill("pocket_furnace", Items.FURNACE, 2, "mining", 2, 2, 5));


        // 计算布局
        calculateLayout();
    }

    // ... 下面的代码保持不变 (calculateLayout, register 等) ...
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
}