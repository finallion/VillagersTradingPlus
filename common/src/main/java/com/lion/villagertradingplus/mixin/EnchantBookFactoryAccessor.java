package com.lion.villagertradingplus.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.village.TradeOffers$EnchantBookFactory")
public interface EnchantBookFactoryAccessor {

    @Accessor("experience")
    int villagertradingplus$experience();

    @Accessor("possibleEnchantments")
    TagKey<Enchantment> villagertradingplus$possibleEnchantments();

    @Accessor("minLevel")
    int villagertradingplus$minLevel();

    @Accessor("maxLevel")
    int villagertradingplus$maxLevel();
}
