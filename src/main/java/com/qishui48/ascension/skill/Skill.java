package com.qishui48.ascension.skill;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Skill {
    public final String id;
    public final Item iconItem;
    public final String parentId;
    public final int tier;
    public final int maxLevel;
    public final boolean isHidden; // 新增：是否是隐藏技能

    private final int[] costs;

    // key = 目标等级 (例如 1 代表解锁条件, 2 代表升到 2 级的条件)
    private final Map<Integer, List<UnlockCriterion>> criteriaMap = new HashMap<>();

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

    // 添加特定等级的升级条件
    public Skill addUpgradeCriterion(int targetLevel, UnlockCriterion criterion) {
        criteriaMap.computeIfAbsent(targetLevel, k -> new ArrayList<>()).add(criterion);
        return this;
    }

    // 检查条件 (OR 逻辑：只要有一个满足就算通过)
    // 如果没有配置条件，默认返回 true
    public boolean checkCriteria(PlayerEntity player, int targetLevel) {
        List<UnlockCriterion> list = criteriaMap.get(targetLevel);
        if (list == null || list.isEmpty()) return true;

        for (UnlockCriterion c : list) {
            if (c.test(player)) return true;
        }
        return false;
    }

    public List<UnlockCriterion> getCriteria(int targetLevel) {
        return criteriaMap.getOrDefault(targetLevel, new ArrayList<>());
    }

    // 兼容旧 UI 逻辑，默认获取 1 级的
    public List<UnlockCriterion> getCriteria() {
        return getCriteria(1);
    }

    public ItemStack getIcon() { return new ItemStack(iconItem); }
    public Text getName() { return Text.translatable("skill.ascension." + this.id); }
}