package com.qishui48.ascension.mixin.client;

import com.qishui48.ascension.AscensionClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityHunterVisionMixin {

    // 强制赋予客户端实体发光状态
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void forceHunterVisionGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld().isClient && AscensionClient.hunterVisionTargets.containsKey(self.getId())) {
            cir.setReturnValue(true);
        }
    }

    // 覆盖发光的颜色
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void overrideHunterVisionColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld().isClient && AscensionClient.hunterVisionTargets.containsKey(self.getId())) {
            cir.setReturnValue(AscensionClient.hunterVisionTargets.get(self.getId()));
        }
    }
}