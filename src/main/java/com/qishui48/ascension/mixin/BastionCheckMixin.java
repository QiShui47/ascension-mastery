package com.qishui48.ascension.mixin;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class BastionCheckMixin {

    // 手动定义 TagKey
    @Unique
    private static final TagKey<Structure> BASTION_TAG = TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "bastion_remnant"));

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (player.age % 100 != 0) return;
        if (player.getWorld().getRegistryKey() != World.NETHER) return;

        ServerWorld world = player.getServerWorld();
        BlockPos pos = player.getBlockPos();

        // === [修复] 正确的 Tag 检查逻辑 ===
        // 1. 获取结构注册表
        var structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        // 2. 获取该 Tag 下的所有结构列表
        var entryList = structureRegistry.getEntryList(BASTION_TAG);

        if (entryList.isPresent()) {
            boolean inBastion = false;

            // 3. 遍历 Tag 下的每一种结构类型 (因为 Bastion 可能有多种变种)
            for (RegistryEntry<Structure> entry : entryList.get()) {
                // 检查当前位置是否存在该具体结构
                if (world.getStructureAccessor().getStructureAt(pos, entry.value()).hasChildren()) {
                    inBastion = true;
                    break;
                }
            }

            if (inBastion) {
                IEntityDataSaver dataSaver = (IEntityDataSaver) player;
                NbtCompound nbt = dataSaver.getPersistentData();
                NbtCompound visited = nbt.contains("visited_bastions") ? nbt.getCompound("visited_bastions") : new NbtCompound();

                long chunkId = new ChunkPos(pos).toLong();
                String idStr = String.valueOf(chunkId);

                if (!visited.contains(idStr)) {
                    visited.putBoolean(idStr, true);
                    nbt.put("visited_bastions", visited);

                    // 增加原版统计数据 (这样就能在 ESC 统计面板看到了，Skill 系统也能直接读取)
                    player.increaseStat(Stats.CUSTOM.getOrCreateStat(Ascension.EXPLORE_BASTION), 1);
                }
            }
        }
    }
}