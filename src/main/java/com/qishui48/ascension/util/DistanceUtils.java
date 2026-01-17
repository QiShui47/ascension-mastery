package com.qishui48.ascension.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class DistanceUtils {

    /**
     * 计算两点之间的 3D 距离 (单位: cm)
     */
    public static int getDistanceCm(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dX = x1 - x2;
        double dY = y1 - y2;
        double dZ = z1 - z2;
        // 距离 = sqrt(dx^2 + dy^2 + dz^2)
        // * 100 转为 cm
        return (int) (Math.sqrt(dX * dX + dY * dY + dZ * dZ) * 100.0);
    }

    /**
     * 计算两点之间的水平距离 (忽略 Y 轴，用于基岩行走等) (单位: cm)
     */
    public static int getHorizontalDistanceCm(double x1, double z1, double x2, double z2) {
        double dX = x1 - x2;
        double dZ = z1 - z2;
        return (int) (Math.sqrt(dX * dX + dZ * dZ) * 100.0);
    }

    /**
     * 根据玩家当前的瞬时速度计算移动距离 (单位: cm/tick)
     * 适用于检测“游泳”、“飞行”等难以记录上一帧坐标的情况
     */
    public static int getSpeedCm(PlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        // length() 返回的是米/tick
        return (int) (velocity.length() * 100.0);
    }

    /**
     * 简单的阈值检查，避免浮点数微小抖动
     */
    public static boolean hasMoved(double x1, double y1, double z1, double x2, double y2, double z2) {
        // 距离平方 > 0.0001 (相当于移动了 0.01格)
        double sqrDist = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2);
        return sqrDist > 0.0001;
    }
}