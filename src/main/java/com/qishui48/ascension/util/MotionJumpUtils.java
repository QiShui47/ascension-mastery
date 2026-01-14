package com.qishui48.ascension.util;

import net.minecraft.util.math.Vec3d;

public class MotionJumpUtils {

    /**
     * 计算蓄力跳跃的最终速度向量
     * @param pitch 玩家的俯仰角 (-90 看天, 90 看地)
     * @param yaw 玩家的偏航角
     * @param powerRatio 蓄力比率 (0.0 ~ 1.0)
     * @param multiplier 技能等级带来的倍率
     */
    public static Vec3d calculateChargedJumpVector(float pitch, float yaw, float powerRatio, float multiplier) {
        float calculatedPitch;

        // === 黄金弹道算法 (修正版) ===
        // 规则：
        // 1. 视线 > 0度 (低头/平视): 强制修正为 -55度 (最佳抛物线仰角)
        // 2. 视线 < 0度 (抬头): 在 -55度 到 -90度 之间线性过渡
        if (pitch > 0f) {
            calculatedPitch = -55.0f;
        } else {
            // 线性插值优化:
            // 当 pitch = 0 时，结果 -55
            // 当 pitch = -90 时，我们希望结果也是 -90 (垂直向上)
            // 公式: -55 + (pitch / 90) * (90 - 55)
            // 简化后: -55 + (pitch * 0.388)
            calculatedPitch = -55.0f + (pitch * 0.39f);
        }

        // 1. 获取基础方向向量 (单位向量)
        Vec3d launchDir = Vec3d.fromPolar(calculatedPitch, yaw).normalize();

        // 2. 计算总力度 (基础 0.5 + 蓄力加成)
        double totalForce = 0.5 + (powerRatio * multiplier);

        // 3. === 核心修改：垂直助推与水平抑制 ===
        // 分离向量
        double x = launchDir.x * totalForce;
        double z = launchDir.z * totalForce;
        double y = launchDir.y * totalForce;

        // 3.1 水平抑制：让跳跃更"高"而不是更"远"
        // 削弱 X 和 Z 轴的速度 (例如只保留 80%)
        x *= 0.8;
        z *= 0.8;

        // 3.2 垂直助推：额外增加 Y 轴速度
        // 基础给一点，蓄力满再多给一点 (例如额外 +0.3 ~ 0.8)
        double extraY = 0.3 + (powerRatio * 0.5);
        y += extraY;

        // 4. 返回合成后的向量
        return new Vec3d(x, y, z);
    }
}