package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public class PlayerAttackMixin {

    // 修改攻击伤害 (amount)
    // 目标方法：attack(Entity target)
    // 注入点：在获取属性伤害之后，造成伤害之前。
    // 在 1.20.1 中，attack 方法里有一个 float f = (float)this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
    // 我们可以通过 ModifyVariable 拦截这个 f

    @ModifyVariable(method = "attack", at = @At(value = "STORE"), ordinal = 0)
    private float modifyAttackDamage(float damage) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // 检查状态
            if (((com.qishui48.ascension.util.ISacrificialState) player).isSacrificialReady()) {
                if (PacketUtils.isSkillActive(serverPlayer, "sacrificial_strike")) {
                    int level = PacketUtils.getSkillLevel(serverPlayer, "sacrificial_strike");

                    // 基础提升: Lv1 +20%, Lv2 +40%
                    float multiplier = 1.0f + (level * 0.2f);

                    // 距离地面加成
                    // 射线检测下方距离
                    float heightBonus = 0f;
                    double y = player.getY();
                    // 简单的向下寻找地面 (最多找 5米，节省性能)
                    double groundY = y;
                    for (int i = 0; i <= 5; i++) {
                        BlockPos p = BlockPos.ofFloored(player.getX(), y - i, player.getZ());
                        if (!player.getWorld().isAir(p)) {
                            // 找到了地面 (简化计算，取方块顶部)
                            groundY = p.getY() + 1.0;
                            break;
                        }
                    }

                    double dist = y - groundY; // 离地距离
                    // 只有在 0.3 到 1.5 之间有加成
                    if (dist > 0.3 && dist <= 1.5) {
                        // 线性插值: 0.3米时最大(20%), 1.5米时最小(0%)
                        // 公式: (1.5 - dist) / (1.5 - 0.3) * 0.2
                        double ratio = (1.5 - dist) / 1.2;
                        heightBonus = (float) (ratio * 0.2f);
                    } else if (dist <= 0.3) {
                        // 极低空，满额加成
                        heightBonus = 0.2f;
                    }

                    // 状态是一次性的，打完就没了
                    ((com.qishui48.ascension.util.ISacrificialState) player).setSacrificialReady(false);

                    return damage * (multiplier + heightBonus);
                }
            }
        }
        return damage;
    }

    @ModifyVariable(method = "attack", at = @At(value = "STORE"), ordinal = 0)
    private float modifyDamage(float damage) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // 检查充能
            int charges = PacketUtils.getData(serverPlayer, "zhu_rong_charges");
            if (charges > 0) {
                // 每一层 +2 伤害
                float bonus = 2.0f; // 描述说"下一次伤害附加2点"，是每消耗1层+2点？还是消耗1层获得所有加成？
                // 描述："每吃1个...下一次伤害额外附加2点...最多累计2/3/4/5次"
                // 通常理解：吃3个 -> 存3层 -> 下一刀消耗1层+2伤害？还是下一刀消耗所有层+6伤害？
                // 既然是"累计效果"，且"附加2点"，通常是一刀消耗1层比较平衡。
                // 但如果是"核弹流"，那就是一刀全消耗。
                // 让我们设定为：**每次攻击消耗 1 层，附加 2 点伤害**。（细水长流）
                // 如果你想改成全消耗，请告诉我。

                // [更新] 重新阅读："每吃1个...可使...附加2点...最多累计..."
                // 这听起来像是 Accumulate Damage。即吃3个，下一刀 +6。
                // 这样才符合"爆发"的定位。

                float totalBonus = charges * 2.0f;

                // 扣除充能 (一次性全部释放！)
                PacketUtils.setData(serverPlayer, "zhu_rong_charges", 0);

                // 播放音效
                serverPlayer.getWorld().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.ITEM_FIRECHARGE_USE, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

                return damage + totalBonus;
            }
        }
        return damage;
    }
}