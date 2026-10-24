package com.lion.villagertradingplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.village.TradeOffers$SellMapFactory")
public interface SellMapFactoryAccessor {

    @Accessor("price")
    int villagertradingplus$price();

    @Accessor("nameKey")
    String villagertradingplus$nameKey();

    @Accessor("maxUses")
    int villagertradingplus$maxUses();

    @Accessor("experience")
    int villagertradingplus$experience();
}
