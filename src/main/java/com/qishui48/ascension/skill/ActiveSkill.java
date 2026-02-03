package com.qishui48.ascension.skill;

import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class ActiveSkill extends Skill {
    private final int[] cooldowns;
    private final int[] secondaryCooldowns; // 次要技能冷却时间 (Ticks)
    private final int[] maxCharges;
    public final List<CastIngredient> ingredients = new ArrayList<>();

    // === [新增] 行为接口 ===
    private SkillBehavior behavior;
    @FunctionalInterface
    public interface SkillBehavior {
        boolean execute(ServerPlayerEntity player, ActiveSkill skill, boolean isSecondary);
    }

    // 构造函数
    // 注意参数顺序调整为：maxCharges, cooldowns, costs
    public ActiveSkill(String id, Item iconItem, int tier, String parentId, int maxLevel, int[] maxCharges, int[] cooldowns, int[] secondaryCooldowns, int... costs) {
        super(id, iconItem, tier, parentId, maxLevel, costs);
        this.cooldowns = cooldowns;
        this.secondaryCooldowns = secondaryCooldowns;
        this.maxCharges = maxCharges;
    }

    // [新增] 辅助构造函数（如果想用固定冷却）
    public ActiveSkill(String id, Item iconItem, int tier, String parentId, int maxLevel, int maxCharges, int cooldowns, int secondaryCooldowns, int... costs) {
        this(id, iconItem, tier, parentId, maxLevel, new int[]{maxCharges}, new int[]{cooldowns}, new int[]{secondaryCooldowns}, costs);
    }

    // 链式调用：添加施法材料
    public ActiveSkill addIngredient(Item item, int count, boolean isPriority, int bonusEffect) {
        this.ingredients.add(new CastIngredient(item, count, isPriority, bonusEffect));
        return this;
    }

    // 获取主要效果冷却时间
    public int getPrimaryCooldown(int level) {
        if (level <= 0) return 0;
        // 如果配置了足够的等级冷却，取对应值；否则取最后一个（满级）
        int index = Math.min(level - 1, cooldowns.length - 1);
        return cooldowns[index];
    }

    // 获取次要效果冷却时间
    public int getSecondaryCooldown(int level) {
        if (level <= 0) return 0;
        int index = Math.min(level - 1, secondaryCooldowns.length - 1);
        return secondaryCooldowns[index];
    }

    public int getMaxCharges(int level) {
        if (level <= 0) return 0;
        int index = Math.min(level - 1, maxCharges.length - 1);
        return this.maxCharges[index];
    }

    // === 设置行为 (链式调用) ===
    public ActiveSkill setBehavior(SkillBehavior behavior) {
        this.behavior = behavior;
        return this;
    }

    // === 执行行为 ===
    public boolean cast(ServerPlayerEntity player, boolean isSecondary) {
        if (this.behavior != null) {
            return this.behavior.execute(player, this, isSecondary);
        }
        return false; // 如果没有定义行为，视为失败
    }

    // 内部类：施法材料定义
    public static class CastIngredient {
        public final Item item;
        public final int count;
        public final boolean isPriority; // 是否为强化材料
        public final int bonusEffect;    // 强化数值

        public CastIngredient(Item item, int count, boolean isPriority, int bonusEffect) {
            this.item = item;
            this.count = count;
            this.isPriority = isPriority;
            this.bonusEffect = bonusEffect;
        }
    }
}