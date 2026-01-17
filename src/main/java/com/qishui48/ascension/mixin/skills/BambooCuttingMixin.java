package com.qishui48.ascension.mixin.skills;

import com.qishui48.ascension.mixin.mechanics.LivingEntityAccessor;
import com.qishui48.ascension.network.ModMessages; // 确保导入 ModMessages
import com.qishui48.ascension.util.PacketUtils;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs; // 确保导入
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking; // 确保导入
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class BambooCuttingMixin {

    @Inject(method = "attack", at = @At("TAIL"))
    private void onAttack(Entity target, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        // 1. 确保在服务端运行
        if (player.getWorld().isClient) return;

        // 2. [关键修复] 显式转换类型，定义 serverPlayer 变量
        if (!(player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        // 3. 目标必须是活物
        if (!(target instanceof LivingEntity)) return;

        // 4. 检查技能激活 (使用 serverPlayer)
        if (!PacketUtils.isSkillActive(serverPlayer, "bamboo_cutting")) return;

        // 5. 检查武器：剑或斧
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof SwordItem) && !(stack.getItem() instanceof AxeItem)) return;

        // 6. 概率计算
        int level = PacketUtils.getSkillLevel(serverPlayer, "bamboo_cutting");
        float chance = 0.0f;
        switch (level) {
            case 1 -> chance = 0.10f;
            case 2 -> chance = 0.20f;
            case 3 -> chance = 0.27f;
            case 4 -> chance = 0.33f;
        }

        if (player.getRandom().nextFloat() < chance) {
            // A. 服务端逻辑：重置实际攻击计时器
            ((LivingEntityAccessor) player).setLastAttackedTicks(player.age - 100);

            // B. 客户端同步：发送数据包 (这里现在可以安全地使用 serverPlayer 了)
            ServerPlayNetworking.send(
                    serverPlayer,
                    ModMessages.BAMBOO_CUTTING_SYNC_ID,
                    PacketByteBufs.empty()
            );

            // 服务端播放声音给周围人听（可选）
            // player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            //         SoundEvents.BLOCK_BAMBOO_BREAK, SoundCategory.PLAYERS, 1.0f, 2.0f);
        }
    }
}