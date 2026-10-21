package com.lion.villagertradingplus.mixin;

import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.VillagerTrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VillagerTrade.class)
public interface VillagerTradeAccessor {

    @Accessor("doubleTradePriceEnchantments")
    Optional<HolderSet<Enchantment>> villagertradingplus$doubleTradePriceEnchantments();
}
