package com.qishui48.ascension.mixin.client;

import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientReachMixin {

    @Shadow private MinecraftClient client;

    // 1.20.1 中 getReachDistance 是控制方块交互距离的关键
    @Inject(method = "getReachDistance", at = @At("RETURN"), cancellable = true)
    private void modifyReachDistance(CallbackInfoReturnable<Float> cir) {
        if (this.client.player != null) {
            IEntityDataSaver data = (IEntityDataSaver) this.client.player;
            NbtCompound nbt = data.getPersistentData();

            if (nbt.contains("skill_levels")) {
                int level = nbt.getCompound("skill_levels").getInt("telekinesis");
                // 还要检查是否被禁用
                boolean disabled = nbt.contains("disabled_skills") && nbt.getCompound("disabled_skills").getBoolean("telekinesis");

                if (level > 0 && !disabled) {
                    float baseReach = cir.getReturnValue();
                    float bonus = (level >= 2) ? 2.0f : 1.0f;
                    cir.setReturnValue(baseReach + bonus);
                }
            }
        }
    }

    // 注意：攻击距离 (Attack Range) 通常在 GameRenderer.updateTargetedEntity 中硬编码为 3.0。
    // 如果要增加攻击距离，需要更复杂的 Mixin。这里主要满足 "与方块交互" 的需求。
}