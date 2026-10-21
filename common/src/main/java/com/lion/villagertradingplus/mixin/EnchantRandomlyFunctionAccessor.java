package com.lion.villagertradingplus.mixin;

import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantRandomlyFunction.class)
public interface EnchantRandomlyFunctionAccessor {

    @Accessor("options")
    Optional<HolderSet<Enchantment>> villagertradingplus$options();

    @Accessor("includeAdditionalCostComponent")
    boolean villagertradingplus$includeAdditionalCostComponent();
}
