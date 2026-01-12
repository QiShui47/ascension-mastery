package com.qishui48.ascension.skill;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class UnlockCriterion {
    private final String translationKey; // 存翻译键，而不是硬编码文本
    private final Stat<?> stat;
    private final int threshold;
    private double displayDivisor = 1.0;

    public <T> UnlockCriterion(StatType<T> type, T target, int threshold, String translationKey) {
        this.stat = type.getOrCreateStat(target);
        this.threshold = threshold;
        this.translationKey = translationKey;
    }

    // 链式调用设置除数
    public UnlockCriterion setDisplayDivisor(double divisor) {
        this.displayDivisor = divisor;
        return this;
    }

    public boolean test(PlayerEntity player) {
        // 仅服务端检查，客户端逻辑由 UI 独立处理或通过网络同步
        // (为了防止之前的引用报错，这里只保留最安全的检查)
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return serverPlayer.getStatHandler().getStat(this.stat) >= this.threshold;
        }
        return false;
    }

    public int getProgress(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return serverPlayer.getStatHandler().getStat(this.stat);
        }
        return 0;
    }

    // 获取翻译后的描述 (支持传入阈值作为参数，比如 "击杀 %d 只牛")
    public MutableText getDescription() {
        // === 修改：显示时除以系数 ===
        // 例如 threshold 10000, divisor 100.0 -> 显示 100
        int displayValue = (int) (threshold / displayDivisor);
        return Text.translatable(translationKey, displayValue);
    }

    // 解锁条件单位修正
    public double getDisplayDivisor() {
        return this.displayDivisor;
    }

    public Stat<?> getStat() { return stat; }
    public int getThreshold() { return threshold; }
}