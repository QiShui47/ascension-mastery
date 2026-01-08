package com.qishui48.ascension.util;

public interface IModProjectile {
    // 歼星炮相关
    void setDeathStar(boolean isDeathStar);
    boolean isDeathStar();

    // 生命之力相关
    void setLifeForce(boolean isLifeForce);
    boolean isLifeForce();

    // 随身水桶相关
    void setPocketBucket(boolean isPocketBucket);
    boolean isPocketBucket();

    void setPocketGuard(boolean v);
    boolean isPocketGuard(); // 护卫

    void setPocketCat(boolean v);
    boolean isPocketCat();     // 猫猫
}