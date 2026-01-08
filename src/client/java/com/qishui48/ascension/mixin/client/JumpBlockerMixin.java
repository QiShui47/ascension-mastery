package com.qishui48.ascension.mixin.client;

import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 目标改为 PlayerEntity，因为 jump 方法定义在这里
@Mixin(PlayerEntity.class)
public class JumpBlockerMixin {

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        // 1. 我们只关心客户端玩家
        // 这里的 (Object)this 是 PlayerEntity，我们需要判断它是不是 ClientPlayerEntity
        if ((Object) this instanceof ClientPlayerEntity player) {

            // 2. 判定蓄力条件
            // 只要满足蓄力条件，就禁止执行 jump()
            boolean canCharge = player.isOnGround() && player.isSneaking() && isUnlocked(player, "charged_jump");

            if (canCharge) {
                // "取消" 此次起跳。
                // 结果：玩家 input.jumping 是 true，但物理上不会获得向上速度。
                // 玩家会被“钉”在地面上，允许蓄力逻辑继续执行。
                ci.cancel();
            }
        }
    }

    // 复制一份解锁判断逻辑
    private boolean isUnlocked(PlayerEntity player, String id) {
        if (!(player instanceof IEntityDataSaver)) return false;
        IEntityDataSaver data = (IEntityDataSaver) player;
        NbtCompound nbt = data.getPersistentData();
        return nbt.contains("unlocked_skills") && nbt.getCompound("unlocked_skills").getBoolean(id);
    }
}