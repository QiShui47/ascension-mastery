package com.qishui48.ascension.skill;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;

import java.util.*;
import java.util.stream.Collectors;

public class SkillRegistry {
    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    public static void registerAll() {
        SKILLS.clear();

        // Root
        register(new Skill("root", Items.BOOK, 0, null, 1, 1));

        // Tier 1
        register(new Skill("combat", Items.IRON_SWORD, 1, "root", 1, 1));
        register(new Skill("survival",  Items.APPLE, 1, "root", 1, 1));
        register(new Skill("mining",  Items.IRON_PICKAXE, 1, "root", 1, 1));

        // Tier 2 - Survival Branch
        register(new Skill("health_boost", Items.GOLDEN_APPLE, 2, "survival", 5, 2, 5, 10, 15, 20)
                .addCriterion(new UnlockCriterion(Stats.USED, Items.APPLE, 1, "criterion.ascension.eat_apple"))
                .addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.COW, 1, "criterion.ascension.kill_cow")));

        register(new Skill("adrenaline_burst", Items.GLISTERING_MELON_SLICE, 2, "survival", 2, 2));
        register(new Skill("swift_move", Items.LEATHER_BOOTS, 2, "survival", 3, 1, 2, 3));

        // Tier 2 - Combat Branch
        register(new Skill("fire_res", Items.LAVA_BUCKET, 2, "combat", 1, 3));

        Skill rocketBoost = new Skill("rocket_boost", Items.FIREWORK_ROCKET, 2, "combat", 2, 5);
        rocketBoost.addCriterion(new UnlockCriterion(Stats.CUSTOM, Stats.JUMP, 100, "criterion.ascension.jump"));
        rocketBoost.addCriterion(new UnlockCriterion(Stats.KILLED, EntityType.PHANTOM, 1, "criterion.ascension.kill_phantom"));
        // 添加 Lv2 升级条件
        rocketBoost.addUpgradeCriterion(2, new UnlockCriterion(Stats.CUSTOM, Stats.AVIATE_ONE_CM, 10000, "criterion.ascension.fly_elytra").setDisplayDivisor(100.0));
        register(rocketBoost);

        register(new Skill("battle_instinct", Items.GOLDEN_SWORD, 2, "combat", 3, 1));
        register(new Skill("pocket_furnace", Items.FURNACE, 2, "mining", 2, 2, 5));

        // Tier 3
        register(new Skill("charged_jump", Items.FIREWORK_STAR, 3, "rocket_boost", 2, 2, 5));
        register(new Skill("hunger_tolerance", Items.COOKED_BEEF, 3, "survival", 1, 4));

        // Tier 4
        register(new Skill("life_steal",  Items.DIAMOND_SWORD, 4, "combat", 1, 2));
        register(new Skill("hunger_constitution", Items.ROTTEN_FLESH, 4, "hunger_tolerance", 2, 3));

        // === 新增：隐藏技能 - 饥饿爆发 (Hunger Burst) ===
        // 父节点: hunger_constitution, MaxLevel: 1, IsHidden: true
        // 解锁条件由 PlayerEntityMixin 中的逻辑触发 (Reveal)，这里主要配置元数据
        register(new Skill("hunger_burst", Items.RABBIT_FOOT, 5, "hunger_constitution", 1, true, 1));

        calculateLayout();
    }

    private static void register(Skill skill) {
        SKILLS.put(skill.id, skill);
    }

    private static final int NODE_WIDTH = 40;
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

    public static Skill get(String id) { return SKILLS.get(id); }
    public static Collection<Skill> getAll() { return SKILLS.values(); }
    public static Set<String> getIds() { return SKILLS.keySet(); }
}