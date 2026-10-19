package com.lion.villagertradingplus.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Exposes HandledScreen.cancelNextRelease.
/// Vanilla repeats its outside-click check on release, and a MerchantScreen mixin cannot reach that flag.
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("cancelNextRelease")
    void villagertradingplus$setCancelNextRelease(boolean cancelNextRelease);
}
