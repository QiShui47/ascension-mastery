package com.qishui48.ascension.mixin.mechanics;

import com.qishui48.ascension.util.ISacrificialState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMechanicMixin implements ISacrificialState {

    @Unique
    private boolean isSacrificialReady = false;

    @Override
    public void setSacrificialReady(boolean ready) {
        this.isSacrificialReady = ready;
    }

    @Override
    public boolean isSacrificialReady() {
        return this.isSacrificialReady;
    }
}