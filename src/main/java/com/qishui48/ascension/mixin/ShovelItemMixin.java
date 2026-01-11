package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShovelItem.class)
public class ShovelItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void onUseShovel(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (world.isClient) return;

        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);

        // 1. 检查目标是沙子
        if (state.isOf(Blocks.SAND)) {
            // 2. 检查技能
            if (PacketUtils.isSkillActive(serverPlayer, "gold_panning")) {
                int level = PacketUtils.getSkillLevel(serverPlayer, "gold_panning");
                ItemStack stack = context.getStack();

                // 3. 执行淘金逻辑
                // 消耗耐久 (会受到耐久附魔影响)
                stack.damage(4, serverPlayer, (p) -> p.sendToolBreakStatus(context.getHand()));
                // 播放音效
                world.playSound(null, pos, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);

                // === [修改开始] 计算概率 ===
                // 基础: 5% + (Level-1)*2%
                float baseChance = 0.05f + ((level - 1) * 0.02f);

                // 时运加成: 每级 +2%
                int fortuneLevel = EnchantmentHelper.getLevel(Enchantments.FORTUNE, stack);
                float fortuneBonus = fortuneLevel * 0.02f;

                // 材质加成 (用户需求: 木-石-铁-钻-金-下界合金, 每级+2%)
                float tierBonus = 0.0f;
                Item item = stack.getItem();
                if (item instanceof ToolItem toolItem) {
                    ToolMaterial material = toolItem.getMaterial();
                    // 在 1.20.1 Fabric 中，我们可以通过 ToolMaterials 枚举或直接判断物品来确定
                    if (material == ToolMaterials.WOOD) tierBonus = 0.0f;       // 默认
                    else if (material == ToolMaterials.STONE) tierBonus = 0.02f;
                    else if (material == ToolMaterials.IRON) tierBonus = 0.04f;
                    else if (material == ToolMaterials.DIAMOND) tierBonus = 0.06f;
                    else if (material == ToolMaterials.GOLD) tierBonus = 0.08f; // 金铲子虽然耐久低，但这里为了奖励给它高概率
                    else if (material == ToolMaterials.NETHERITE) tierBonus = 0.10f;
                }
                float totalChance = baseChance + fortuneBonus + tierBonus;

                if (world.random.nextFloat() < totalChance) {
                    // 移除沙子
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    // 掉落金锭
                    ItemEntity itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(Items.GOLD_INGOT));
                    world.spawnEntity(itemEntity);
                    // 额外提示音
                    world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
                }

                // 阻止原版逻辑 (例如削路径)
                cir.setReturnValue(ActionResult.SUCCESS);
            }
        }
    }
}