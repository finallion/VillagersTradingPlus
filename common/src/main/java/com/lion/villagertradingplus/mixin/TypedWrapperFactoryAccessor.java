package com.lion.villagertradingplus.mixin;

import java.util.Map;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.village.TradeOffers$TypedWrapperFactory")
public interface TypedWrapperFactoryAccessor {

    @Accessor("typeToFactory")
    Map<VillagerType, TradeOffers.Factory> villagertradingplus$typeToFactory();
}
