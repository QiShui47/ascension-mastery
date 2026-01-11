package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {

    @Inject(method = "getDroppedStacks", at = @At("RETURN"))
    private void modifyDrops(BlockState state, LootContextParameterSet.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        // 1. 获取上下文中的实体 (也就是谁挖的)
        Entity entity = builder.getOptional(LootContextParameters.THIS_ENTITY);

        if (entity instanceof ServerPlayerEntity player) {
            // 2. 检查目标方块 (花岗岩、安山岩、闪长岩)
            if (state.isOf(Blocks.GRANITE) || state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.DIORITE)) {

                // 3. 检查技能
                if (PacketUtils.isSkillActive(player, "hephaestus_favor")) {
                    int level = PacketUtils.getSkillLevel(player, "hephaestus_favor");
                    List<ItemStack> drops = cir.getReturnValue();

                    // 计算概率：Lv1=35%, Lv2/3=70%
                    float quartzChance = (level >= 2) ? 0.7f : 0.35f;
                    Random random = player.getWorld().getRandom();

                    if (random.nextFloat() < quartzChance) {
                        // === 触发石英掉落 ===
                        // 移除原来的岩石掉落 (通常 drops 里只有一个物品，就是岩石本身)
                        drops.clear();
                        drops.add(new ItemStack(Items.QUARTZ));
                    }

                    // Lv3 额外掉落粗铁 (10%) - 独立判定，不影响石英
                    if (level >= 3 && random.nextFloat() < 0.1f) {
                        drops.add(new ItemStack(Items.RAW_IRON));
                    }
                }
            }
        }
    }
}