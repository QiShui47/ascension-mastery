package com.qishui48.ascension.mixin.skills;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class MagmaCreamMixin {

    // 1. 允许右键使用 (模拟食物)
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = user.getStackInHand(hand);
        if (stack.isOf(Items.MAGMA_CREAM)) {
            if (user instanceof ServerPlayerEntity serverPlayer && PacketUtils.isSkillActive(serverPlayer, "zhu_rong")) {
                // 开始“吃”的动作
                user.setCurrentHand(hand);
                cir.setReturnValue(TypedActionResult.consume(stack));
            }
        }
    }

    // 2. 设置使用动作 (EAT)
    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void onGetUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if (stack.isOf(Items.MAGMA_CREAM)) {
            // 这里我们无法直接获取 Player，所以只能泛泛地返回，或者稍微 Hack 一下。
            // 只要是岩浆膏，就允许 EAT 动作显示（反正没有 use 逻辑普通人也吃不了）
            cir.setReturnValue(UseAction.EAT);
        }
    }

    // 3. 设置食用时间 (32 tick)
    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void onGetMaxUseTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack.isOf(Items.MAGMA_CREAM)) {
            cir.setReturnValue(32);
        }
    }

    // 4. 吃完后的逻辑 (增加充能)
    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
    private void onFinishUsing(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (!world.isClient && stack.isOf(Items.MAGMA_CREAM) && user instanceof ServerPlayerEntity player) {
            if (PacketUtils.isSkillActive(player, "zhu_rong")) {
                // 增加层数
                int maxStacks = 1 + PacketUtils.getSkillLevel(player, "zhu_rong"); // Lv1:2, Lv2:3...
                // 修正：描述说 "最多累计 2/3/4/5 次" -> Lv1=2, Lv2=3...

                int current = PacketUtils.getData(player, "zhu_rong_charges");
                if (current < maxStacks + 1) { // Lv1 max=2
                    PacketUtils.setData(player, "zhu_rong_charges", current + 1);
                    player.sendMessage(net.minecraft.text.Text.of("§c[祝融] 火焰充能: " + (current + 1)), true);
                } else {
                    player.sendMessage(net.minecraft.text.Text.of("§c[祝融] 充能已满！"), true);
                    // 充能满了也消耗物品，防止玩家以为没吃进去？或者不消耗？这里设定为消耗
                }

                // 播放效果
                player.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);

                // 消耗物品
                if (!player.isCreative()) {
                    stack.decrement(1);
                }
                cir.setReturnValue(stack);
            }
        }
    }
}