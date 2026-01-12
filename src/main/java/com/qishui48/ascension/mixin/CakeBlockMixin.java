package com.qishui48.ascension.mixin;

import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CakeBlock.class)
public class CakeBlockMixin {

    @Inject(method = "onUse", at = @At(value = "RETURN"))
    private void onEatCake(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        // 检查交互是否成功（成功意味着玩家吃了一口）
        if (cir.getReturnValue() == ActionResult.SUCCESS && !world.isClient) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                // 检查是否激活技能
                if (PacketUtils.isSkillActive(serverPlayer, "sugar_master")) {
                    // 蛋糕方块本身没有 FoodComponent，我们需要模拟一个
                    // 蛋糕每片回复 2 点饥饿值 (1格)，饱和度 0.4 + 1.5 x level
                    // 这里的 FoodComponent 我们需要手动构建或者引用 Items.CAKE 的（但 Items.CAKE 也没配置吃的数据因为它是方块物品）
                    FoodComponent cakeSlice = new FoodComponent.Builder().hunger(2).saturationModifier(0.4f).build();

                    SkillEffectHandler.applySugarMasterEffect(serverPlayer, cakeSlice);
                }
            }
        }
    }
}