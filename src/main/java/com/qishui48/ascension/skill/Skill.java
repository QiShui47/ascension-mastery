package com.qishui48.ascension.skill;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

public class Skill {
    public final String id;
    public final Item iconItem;
    public final String parentId;
    public final int tier;
    public final int maxLevel;
    public final boolean isHidden; // 新增：是否是隐藏技能
    public final List<String> mutexSkills = new ArrayList<>();  // 互斥技能 ID 列表
    private final int[] costs;
    // key = 目标等级 (例如 1 代表解锁条件, 2 代表升到 2 级的条件)
    //private final Map<Integer, List<UnlockCriterion>> criteriaMap = new HashMap<>();
    private final Map<Integer, List<List<UnlockCriterion>>> criteriaMap = new HashMap<>();

    // 额外父节点 ID
    public List<String> visualParents = new ArrayList<>();

    public int subTreeWidth = 0;
    public int x;
    public int y;
    // 普通构造函数 (非隐藏)
    public Skill(String id, Item iconItem, int tier, String parentId, int maxLevel, int... costs) {
        this(id, iconItem, tier, parentId, maxLevel, false, costs);
    }

    // 全参构造函数
    public Skill(String id, Item iconItem, int tier, String parentId, int maxLevel, boolean isHidden, int... costs) {
        this.id = id;
        this.iconItem = iconItem;
        this.parentId = parentId;
        this.tier = tier;
        this.maxLevel = maxLevel;
        this.isHidden = isHidden;

        this.costs = new int[maxLevel];
        for (int i = 0; i < maxLevel; i++) {
            if (i < costs.length) {
                this.costs[i] = costs[i];
            } else {
                this.costs[i] = (costs.length > 0) ? costs[costs.length - 1] : 1;
            }
        }
    }

    // === 链式配置方法 ===
    // 添加互斥技能
    public Skill setMutex(String... skillIds) {
        this.mutexSkills.addAll(Arrays.asList(skillIds));
        return this;
    }
    // 链式调用添加视觉父节点
    public Skill withVisualParent(String parentId) {
        if (parentId != null && !parentId.isEmpty()) {
            this.visualParents.add(parentId);
        }
        return this;
    }

    public int getCost(int targetLevel) {
        int index = targetLevel - 1;
        if (index >= 0 && index < costs.length) {
            return costs[index];
        }
        return 999;
    }

    public Text getDescription(int level) {
        String baseKey = "skill.ascension." + this.id + ".desc";
        if (level <= 1) return Text.translatable(baseKey);
        return Text.translatable(baseKey + "." + level);
    }

    // 添加解锁条件 (默认添加到 1 级，兼容旧代码)
    public Skill addCriterion(UnlockCriterion criterion) {
        return addUpgradeCriterion(1, criterion);
    }

    // 新方法: 添加一组必须同时满足的条件 (AND)
    public Skill addCriteriaGroup(UnlockCriterion... criteria) {
        return addUpgradeCriteriaGroup(1, criteria);
    }

    // 指定等级: 单个条件
    public Skill addUpgradeCriterion(int targetLevel, UnlockCriterion criterion) {
        return addUpgradeCriteriaGroup(targetLevel, criterion);
    }

    // 指定等级: 添加条件组 (Varargs)
    public Skill addUpgradeCriteriaGroup(int targetLevel, UnlockCriterion... criteria) {
        List<UnlockCriterion> group = new ArrayList<>(Arrays.asList(criteria));
        criteriaMap.computeIfAbsent(targetLevel, k -> new ArrayList<>()).add(group);
        return this;
    }

    // 检查条件 (OR / AND 逻辑)
    // 如果没有配置条件，默认返回 true
    // === 判定逻辑 ===
    public boolean checkCriteria(PlayerEntity player, int targetLevel) {
        List<List<UnlockCriterion>> groups = criteriaMap.get(targetLevel);
        if (groups == null || groups.isEmpty()) return true;

        // 外层循环是 OR (只要有一组满足即可)
        for (List<UnlockCriterion> group : groups) {
            boolean groupMet = true;
            // 内层循环是 AND (组内所有条件必须都满足)
            for (UnlockCriterion c : group) {
                if (!c.test(player)) {
                    groupMet = false;
                    break;
                }
            }
            if (groupMet) return true; // 找到一组满足的，通过
        }
        return false; // 所有组都失败
    }

    // === 获取扁平化列表 (用于 PacketUtils 索引) ===
    // 我们需要把所有条件展平，以便 PacketUtils 生成进度数组
    public List<UnlockCriterion> getCriteria(int targetLevel) {
        List<List<UnlockCriterion>> groups = criteriaMap.get(targetLevel);
        if (groups == null) return new ArrayList<>();
        return groups.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    // === 获取分组后的条件列表 (用于 UI 显示 AND/OR 结构) ===
    public List<List<UnlockCriterion>> getCriteriaGroups(int targetLevel) {
        // 如果没有定义，返回空列表
        return criteriaMap.getOrDefault(targetLevel, new ArrayList<>());
    }

    // 兼容旧 UI 逻辑，默认获取 1 级的
    public List<UnlockCriterion> getCriteria() {
        return getCriteria(1);
    }

    public ItemStack getIcon() { return new ItemStack(iconItem); }
    public Text getName() { return Text.translatable("skill.ascension." + this.id); }
}