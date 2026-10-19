package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

import java.util.Optional;

/// An enchantment entry encodes by identity in the receiving connection's registry, so a stale entry is unencodable.
/// Only the sell stack is rebound, because the buy side is a ComponentMapPredicate that cannot be rebuilt losslessly.
public final class RegistryRebindFactory implements TradeOffers.Factory {

    private final TradeOffers.Factory delegate;

    private RegistryRebindFactory(TradeOffers.Factory delegate) {
        this.delegate = delegate;
    }

    public static TradeOffers.Factory wrap(TradeOffers.Factory delegate) {
        return delegate == null ? null : new RegistryRebindFactory(delegate);
    }

    public TradeOffers.Factory delegate() {
        return this.delegate;
    }

    @Override
    public TradeOffer create(Entity entity, Random random) {
        TradeOffer offer = this.delegate.create(entity, random);

        if (offer == null) {
            return null;
        }

        Registry<Enchantment> live = entity.getWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        ItemStack sell = offer.getSellItem();

        if (!rebind(sell, DataComponentTypes.ENCHANTMENTS, live)
                & !rebind(sell, DataComponentTypes.STORED_ENCHANTMENTS, live)) {
            return offer;
        }

        return new TradeOffer(
                offer.getFirstBuyItem(),
                offer.getSecondBuyItem(),
                sell,
                offer.getUses(),
                offer.getMaxUses(),
                offer.getMerchantExperience(),
                offer.getPriceMultiplier(),
                offer.getDemandBonus()
        );
    }

    private static boolean rebind(
            ItemStack stack,
            ComponentType<ItemEnchantmentsComponent> type,
            Registry<Enchantment> live
    ) {
        ItemEnchantmentsComponent current = stack.get(type);

        if (current == null || current.isEmpty()) {
            return false;
        }

        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        boolean changed = false;

        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : current.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> bound = entry.getKey();
            RegistryEntry<Enchantment> fresh = resolve(bound, live);

            if (fresh == null) {
                changed = true;
                continue;
            }

            if (fresh != bound) {
                changed = true;
            }

            builder.set(fresh, entry.getIntValue());
        }

        if (changed) {
            stack.set(type, builder.build());
        }

        return changed;
    }

    private static RegistryEntry<Enchantment> resolve(RegistryEntry<Enchantment> entry, Registry<Enchantment> live) {
        Optional<RegistryKey<Enchantment>> key = entry.getKey();

        if (key.isEmpty()) {
            return entry;
        }

        return live.getOptional(key.get()).map(reference -> (RegistryEntry<Enchantment>) reference).orElse(null);
    }
}
