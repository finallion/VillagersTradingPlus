package com.lion.villagertradingplus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Exposes `AbstractContainerScreen.skipNextRelease`. A release outside the GUI drops the cursor stack.
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {

    @Accessor("skipNextRelease")
    void villagertradingplus$setCancelNextRelease(boolean cancelNextRelease);
}
