package com.qishui48.ascension.block.entity;

import com.qishui48.ascension.Ascension;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TemporaryGlowingBlockEntity extends BlockEntity {

    // 初始寿命
    private int ticksLeft = 6;

    public TemporaryGlowingBlockEntity(BlockPos pos, BlockState state) {
        super(Ascension.TEMPORARY_GLOWING_BLOCK_ENTITY, pos, state);
    }

    // 刷新
    public void refresh() {
        this.ticksLeft = 6; // 重置回满血状态
    }

    public static void tick(World world, BlockPos pos, BlockState state, TemporaryGlowingBlockEntity blockEntity) {
        if (world.isClient) return;

        blockEntity.ticksLeft--;

        // 寿命耗尽，自我销毁 (变回空气)
        if (blockEntity.ticksLeft <= 0) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            // 通常不需要手动 markRemoved，setBlockState 会处理
        }
    }
}