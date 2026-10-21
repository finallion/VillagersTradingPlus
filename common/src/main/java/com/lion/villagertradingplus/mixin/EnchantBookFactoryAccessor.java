package com.lion.villagertradingplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.village.TradeOffers$EnchantBookFactory")
public interface EnchantBookFactoryAccessor {

    @Accessor("experience")
    int villagertradingplus$experience();
}
