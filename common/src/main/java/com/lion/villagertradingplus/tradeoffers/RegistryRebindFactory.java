package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

/// The codec compares holders by identity. A foreign holder drops the player.
public final class RegistryRebindFactory implements TradeFactory {

    private final TradeFactory delegate;

    private RegistryRebindFactory(TradeFactory delegate) {
        this.delegate = delegate;
    }

    public static TradeFactory wrap(TradeFactory delegate) {
        return delegate == null ? null : new RegistryRebindFactory(delegate);
    }

    public TradeFactory delegate() {
        return this.delegate;
    }

    @Override
    public MerchantOffer create(net.minecraft.server.level.ServerLevel world, Entity entity, RandomSource random) {
        MerchantOffer offer = this.delegate.create(world, entity, random);

        if (offer == null) {
            return null;
        }

        Registry<Enchantment> live = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack sell = offer.getResult();

        if (!rebind(sell, DataComponents.ENCHANTMENTS, live)
                & !rebind(sell, DataComponents.STORED_ENCHANTMENTS, live)) {
            return offer;
        }

        return new MerchantOffer(
                offer.getItemCostA(),
                offer.getItemCostB(),
                sell,
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand()
        );
    }

    private static boolean rebind(
            ItemStack stack,
            DataComponentType<ItemEnchantments> type,
            Registry<Enchantment> live
    ) {
        ItemEnchantments current = stack.get(type);

        if (current == null || current.isEmpty()) {
            return false;
        }

        ItemEnchantments.Mutable builder =
                new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        boolean changed = false;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : current.entrySet()) {
            Holder<Enchantment> bound = entry.getKey();
            Holder<Enchantment> fresh = resolve(bound, live);

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
            stack.set(type, builder.toImmutable());
        }

        return changed;
    }

    private static Holder<Enchantment> resolve(Holder<Enchantment> entry, Registry<Enchantment> live) {
        Optional<ResourceKey<Enchantment>> key = entry.unwrapKey();

        if (key.isEmpty()) {
            return entry;
        }

        return live.get(key.get()).map(reference -> (Holder<Enchantment>) reference).orElse(null);
    }
}
