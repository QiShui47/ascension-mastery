package com.qishui48.ascension.util;

import net.minecraft.util.math.Vec3d;

public class MotionJumpUtils {

    /**
     * 计算蓄力跳跃的最终速度向量
     * @param pitch 玩家的俯仰角
     * @param yaw 玩家的偏航角
     * @param powerRatio 蓄力比率 (0.0 ~ 1.0)
     * @return 计算后的速度向量 (包含方向和力度)
     */
    public static Vec3d calculateChargedJumpVector(float pitch, float yaw, float powerRatio, float multiplier) {
        float calculatedPitch;

        // === 黄金弹道算法 ===
        // 规则：
        // 1. 视线 > -30度 (平视/看地): 强制修正为 -45度 (1:1 完美抛物线)
        // 2. 视线 < -30度 (看天): 在 -45度 到 -90度 之间线性过渡
        if (pitch > -0f) {
            calculatedPitch = -55.0f;
        } else {
            // 线性插值: 从 -30度 到 -90度，映射到 -45度 到 -90度
            calculatedPitch = -55.0f + (pitch + 0f) * 0.45f;
        }

        // 1. 获取单位方向向量
        Vec3d launchDir = Vec3d.fromPolar(calculatedPitch, yaw).normalize();

        // 2. 计算力度 (基础 0.8 + 蓄力满 3.0 = 最大 3.8)
        double totalForce = 0.5 + (powerRatio * multiplier);

        // 3. 返回最终速度向量
        return launchDir.multiply(totalForce);
    }
}