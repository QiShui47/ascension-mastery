package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(AbstractBlock.class)
public class BlockDropsStatMixin {

    @Inject(method = "getDroppedStacks", at = @At("RETURN"))
    private void onDropsStat(BlockState state, LootContextParameterSet.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        Entity entity = builder.getOptional(LootContextParameters.THIS_ENTITY);

        if (entity instanceof ServerPlayerEntity player) {
            // === 糖分主理人：作物收集统计 ===
            if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                // 仅统计特定作物
                boolean isValidCrop = blockId.equals("minecraft:wheat") ||
                        blockId.equals("minecraft:carrots") ||
                        blockId.equals("minecraft:potatoes") ||
                        blockId.equals("minecraft:beetroots");

                if (isValidCrop) {
                    IEntityDataSaver dataSaver = (IEntityDataSaver) player;
                    NbtCompound nbt = dataSaver.getPersistentData();
                    NbtList collectedCrops = nbt.contains("collected_crop_types", NbtElement.LIST_TYPE)
                            ? nbt.getList("collected_crop_types", NbtElement.STRING_TYPE)
                            : new NbtList();

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
                        player.getStatHandler().setStat(player, Stats.CUSTOM.getOrCreateStat(Ascension.COLLECT_CROP_VARIANTS), collectedCrops.size());
                    }
                }
            }
        }
    }
}