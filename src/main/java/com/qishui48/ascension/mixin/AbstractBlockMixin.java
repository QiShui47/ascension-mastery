package com.qishui48.ascension.mixin;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
            // === 糖分主理人：收获农作物追踪 ===
            // 检查是否是作物方块 且 已成熟
            if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                // 过滤：只记录我们关心的几种 (小麦、胡萝卜、马铃薯、甜菜根)
                boolean isValidCrop = blockId.equals("minecraft:wheat") ||
                        blockId.equals("minecraft:carrots") ||
                        blockId.equals("minecraft:potatoes") ||
                        blockId.equals("minecraft:beetroots");
                if (isValidCrop) {
                    IEntityDataSaver dataSaver = (IEntityDataSaver) player;
                    NbtCompound nbt = dataSaver.getPersistentData();

                    NbtList collectedCrops;
                    if (nbt.contains("collected_crop_types", NbtElement.LIST_TYPE)) {
                        collectedCrops = nbt.getList("collected_crop_types", NbtElement.STRING_TYPE);
                    } else {
                        collectedCrops = new NbtList();
                    }
                    // 查重
                    boolean found = false;
                    for (NbtElement e : collectedCrops) {
                        if (e.asString().equals(blockId)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        collectedCrops.add(NbtString.of(blockId));
                        nbt.put("collected_crop_types", collectedCrops);

                        // 更新统计：数量
                        player.getStatHandler().setStat(player, Stats.CUSTOM.getOrCreateStat(Ascension.COLLECT_CROP_VARIANTS), collectedCrops.size());
                    }
                }
            }
        }
    }

    @Inject(method = "onStacksDropped", at = @At("RETURN"))
    private void onStacksDroppedWithXp(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, ItemStack tool, boolean dropExperience, CallbackInfo ci) {
        // 1. 必须开启经验掉落 且 工具是镐子
        if (!dropExperience || !(tool.getItem() instanceof net.minecraft.item.PickaxeItem)) return;

        // 2. 必须是矿石 (检查 Tag)
        if (!state.isIn(net.minecraft.registry.tag.BlockTags.GOLD_ORES) ||
                !state.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES) ||
                !state.isIn(net.minecraft.registry.tag.BlockTags.DIAMOND_ORES) ||
                !state.isIn(net.minecraft.registry.tag.BlockTags.REDSTONE_ORES) ||
                !state.isIn(net.minecraft.registry.tag.BlockTags.LAPIS_ORES) ||
                !state.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES) ||
                !state.isIn(net.minecraft.registry.tag.BlockTags.EMERALD_ORES) ||
                !state.isIn(net.minecraft.registry.tag.BlockTags.COPPER_ORES)
        ) return;

        // 3. 寻找最近的玩家 (通常是挖掘者)
        // 注意：onStacksDropped 没有直接传入 Player 实体，我们需要在附近找一个
        // 范围设为 10 格应该足够
        ServerPlayerEntity player = (ServerPlayerEntity) world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false);
        if (player == null) return;

        // 4. 检查技能
        if (PacketUtils.isSkillActive(player, "academic_miner")) {
            int level = PacketUtils.getSkillLevel(player, "academic_miner");

            // === 计算经验上限 ===
            // 基础区间: Lv1 -> [1, 2], Lv2 -> [2, 3]
            // minXp: Lv1=1, Lv2=2
            // baseMaxXp: Lv1=2, Lv2=3
            int minXp = level;
            int baseMaxXp = level + 1;

            // 材质加成 (Wood=0, Stone/Gold=1, Iron=2, Diamond=3, Netherite=4)
            int tierBonus = 0;
            net.minecraft.item.ToolMaterial material = ((net.minecraft.item.ToolItem) tool.getItem()).getMaterial();
            if (material == net.minecraft.item.ToolMaterials.STONE || material == net.minecraft.item.ToolMaterials.GOLD) tierBonus = 1;
            else if (material == net.minecraft.item.ToolMaterials.IRON) tierBonus = 2;
            else if (material == net.minecraft.item.ToolMaterials.DIAMOND) tierBonus = 3;
            else if (material == net.minecraft.item.ToolMaterials.NETHERITE) tierBonus = 4;

            // 时运加成
            int fortuneLevel = net.minecraft.enchantment.EnchantmentHelper.getLevel(net.minecraft.enchantment.Enchantments.FORTUNE, tool);

            // 最终上限
            int finalMaxXp = baseMaxXp + tierBonus + fortuneLevel;

            // 生成随机经验值 (均匀分布)
            // random.nextInt(max - min + 1) + min
            int xpToDrop = minXp + world.random.nextInt(finalMaxXp - minXp + 1);

            // === 生成经验球 ===
            net.minecraft.entity.ExperienceOrbEntity.spawn(world, net.minecraft.util.math.Vec3d.ofCenter(pos), xpToDrop);
        }
    }
}