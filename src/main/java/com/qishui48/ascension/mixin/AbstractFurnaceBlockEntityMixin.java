package com.qishui48.ascension.mixin;

import com.qishui48.ascension.Ascension;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin extends BlockEntity {

    public AbstractFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private static void onTick(World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        if (world.isClient) return;

        // 1. 检查是否是烟熏炉
        if (blockEntity instanceof SmokerBlockEntity) {

            // === [修复] 使用 Accessor 获取数据 ===
            // 将 blockEntity 强转为我们定义的接口，从而访问受保护的字段
            AbstractFurnaceBlockEntityAccessor accessor = (AbstractFurnaceBlockEntityAccessor) blockEntity;
            int cookTime = accessor.getCookTime();
            int totalTime = accessor.getCookTimeTotal();

            // 2. 检查是否“即将完成” (当前进度 >= 总进度 - 1)
            // 加上 cookTime > 0 是为了防止未工作时误判
            if (cookTime > 0 && cookTime >= totalTime - 1) {

                // 3. 寻找最近的玩家 (8格内)
                PlayerEntity player = world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 8.0, false);

                if (player instanceof ServerPlayerEntity serverPlayer) {
                    // 4. 增加统计
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.COOK_IN_SMOKER), 1);
                }
            }
        }
    }
}