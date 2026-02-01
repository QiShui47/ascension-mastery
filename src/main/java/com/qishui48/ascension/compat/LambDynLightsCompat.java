package com.qishui48.ascension.compat;

import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 反射版兼容类 - 适配 LambDynamicLights (Retrofit API)
 * [修复版] 处理了 isWaterSensitive 返回类型错误导致的崩溃
 */
public class LambDynLightsCompat {

    private static final String API_PACKAGE = "dev.lambdaurora.lambdynlights.api";

    public static void register() {
        try {
            System.out.println("[Ascension] 正在尝试通过反射注入动态光源...");

            Class<?> handlersClass = Class.forName(API_PACKAGE + ".DynamicLightHandlers");
            Class<?> handlerInterface = Class.forName(API_PACKAGE + ".DynamicLightHandler");
            Method registerMethod = handlersClass.getMethod("registerDynamicLightHandler", EntityType.class, handlerInterface);

            Object proxyInstance = Proxy.newProxyInstance(
                    LambDynLightsCompat.class.getClassLoader(),
                    new Class<?>[]{handlerInterface},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();

                            // 1. 核心方法: getLuminance (返回 int)
                            if (name.equals("getLuminance")) {
                                if (args.length > 0 && args[0] instanceof LivingEntity entity) {
                                    return getPlayerLuminance(entity);
                                }
                                return 0;
                            }

                            // 2. [修复崩端] 怕水判定: isWaterSensitive (返回 boolean)
                            // 必须拦截这个方法并返回 boolean，否则返回 0 会导致 ClassCastException
                            if (name.equals("isWaterSensitive")) {
                                return false; // 我们的光是圣光，不怕水
                            }

                            // 3. 基础 Object 方法 (防止意外调用 toString 等报错)
                            if (name.equals("toString")) return "AscensionLightHandler";
                            if (name.equals("hashCode")) return 42;
                            if (name.equals("equals")) return args.length > 0 && args[0] == proxy;

                            // 默认返回 0 (针对返回 int 的方法)
                            return 0;
                        }
                    }
            );

            registerMethod.invoke(null, EntityType.PLAYER, proxyInstance);
            System.out.println("[Ascension] 成功！已通过 Retrofit API 连接到 LambDynamicLights。");

        } catch (Exception e) {
            System.err.println("[Ascension] 注入动态光源失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int getPlayerLuminance(LivingEntity entity) {
        if (!(entity instanceof IEntityDataSaver dataSaver)) return 0;

        NbtCompound nbt = dataSaver.getPersistentData();
        long now = entity.getWorld().getTime();

        // 检查主要效果
        if (nbt.contains("radiant_damage_end") && now < nbt.getLong("radiant_damage_end")) {
            return 15;
        }
        // 检查次要效果
        if (nbt.contains("radiant_light_end") && now < nbt.getLong("radiant_light_end")) {
            return 15;
        }
        return 0;
    }
}