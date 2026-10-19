package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.level.storage.loot.LootContext;

public final class TradePools {

    private static final int VANILLA_OFFERS_PER_LEVEL = 2;
    private static final int VANILLA_WANDERING_COMMON_OFFERS = 5;

    private static volatile TradeSnapshot villagers = TradeSnapshot.EMPTY;

    private TradePools() {
    }

    public static void publish(TradeSnapshot snapshot) {
        villagers = snapshot;
    }

    public static TradeSnapshot villagerSnapshot() {
        return villagers;
    }

    public static boolean hasVillagerData(Holder<VillagerProfession> profession) {
        return profession.unwrapKey().map(villagers::defines).orElse(false);
    }

    /// True when the VTP pool draws the offers. Otherwise vanilla draws them unchanged.
    public static boolean drawsFromPool(Holder<VillagerProfession> profession) {
        return hasVillagerData(profession)
                || VillagerTradingPlus.CONFIG.trade_offers_per_level != VANILLA_OFFERS_PER_LEVEL;
    }

    public static boolean drawsWanderingFromPool() {
        return WanderingTraderTradeLoader.snapshot().hasTrades()
                || VillagerTradingPlus.CONFIG.trade_offers_wandering_trader != VANILLA_WANDERING_COMMON_OFFERS;
    }

    public static List<TradeFactory> forVillager(ServerLevel level, Holder<VillagerProfession> profession, int tier) {
        TradeSnapshot snapshot = villagers;
        Optional<ResourceKey<VillagerProfession>> key = profession.unwrapKey();
        List<TradeFactory> pool = new ArrayList<>();

        if (replacesVanillaSet(snapshot, key)) {
            pool.addAll(snapshot.defaultsAt(key.get(), tier));
        } else {
            pool.addAll(vanillaPool(level.registryAccess(), profession.value().getTrades(tier)));
            key.ifPresent(k -> pool.addAll(snapshot.defaultsAt(k, tier)));
        }
        key.ifPresent(k -> pool.addAll(snapshot.additionsAt(k, tier)));
        return pool;
    }

    public static int draw(ServerLevel level, Entity trader, List<TradeFactory> pool, int amount, MerchantOffers target) {
        RandomSource random = trader.getRandom();
        List<TradeFactory> remaining = new ArrayList<>(pool);
        int added = 0;
        while (added < amount && !remaining.isEmpty()) {
            TradeFactory factory = remaining.remove(random.nextInt(remaining.size()));
            MerchantOffer offer;

            try {
                offer = factory.create(level, trader, random);
            } catch (RuntimeException e) {
                VillagerTradingPlus.LOGGER.error("A trade failed to create an offer and was skipped.", e);
                continue;
            }

            if (offer != null) {
                target.add(offer);
                added++;
            }
        }
        return added;
    }

    public static int addVillagerOffers(ServerLevel level, Entity trader, Holder<VillagerProfession> profession, int tier, MerchantOffers target) {
        int amount = villagerPicks(level, trader, profession, tier);
        return draw(level, trader, forVillager(level, profession, tier), amount, target);
    }

    public static int villagerPicks(ServerLevel level, Entity trader, Holder<VillagerProfession> profession, int tier) {
        ResourceKey<TradeSet> set = profession.value().getTrades(tier);
        if (!drawsFromPool(profession)) {
            return vanillaAmount(level, trader, set, VANILLA_OFFERS_PER_LEVEL);
        }
        int configured = VillagerTradingPlus.CONFIG.trade_offers_per_level;
        if (replacesVanillaSet(villagers, profession.unwrapKey()) || !hasTradeSet(level, set)) {
            return configured;
        }
        return Math.max(0, vanillaAmount(level, trader, set, VANILLA_OFFERS_PER_LEVEL) + configured - VANILLA_OFFERS_PER_LEVEL);
    }

    private static boolean replacesVanillaSet(TradeSnapshot snapshot, Optional<ResourceKey<VillagerProfession>> key) {
        return key.isPresent() && replacesVanillaSet(snapshot, key.get());
    }

    /// Defaults replace the vanilla trade sets of `minecraft` professions only.
    public static boolean replacesVanillaSet(TradeSnapshot snapshot, ResourceKey<VillagerProfession> key) {
        return "minecraft".equals(key.identifier().getNamespace()) && snapshot.replacesVanilla(key);
    }

    private static boolean hasTradeSet(ServerLevel level, ResourceKey<TradeSet> setKey) {
        return setKey != null && level.registryAccess().lookupOrThrow(Registries.TRADE_SET).get(setKey).isPresent();
    }

    /// Fills a wandering trader in vanilla order: buying set, uncommon set, common set.
    public static void fillWanderingTrader(ServerLevel level, Entity trader, MerchantOffers target) {
        fillWanderingSet(level, trader, TradeSets.WANDERING_TRADER_BUYING, 0, target);
        fillWanderingSet(level, trader, TradeSets.WANDERING_TRADER_UNCOMMON, 2, target);
        fillWanderingSet(level, trader, TradeSets.WANDERING_TRADER_COMMON, 1, target);
    }

    /// The pool of a wandering trader tier: 1 is the common set, 2 the uncommon set.
    public static List<TradeFactory> forWanderingTrader(ServerLevel level, int tier) {
        return wanderingPool(level, tier == 2 ? TradeSets.WANDERING_TRADER_UNCOMMON : TradeSets.WANDERING_TRADER_COMMON, tier);
    }

    public static int wanderingPicks(ServerLevel level, Entity trader, int tier) {
        if (tier == 1) {
            return VillagerTradingPlus.CONFIG.trade_offers_wandering_trader;
        }
        return vanillaAmount(level, trader, TradeSets.WANDERING_TRADER_UNCOMMON, 2);
    }

    private static void fillWanderingSet(ServerLevel level, Entity trader, ResourceKey<TradeSet> setKey, int vtpTier, MerchantOffers target) {
        List<TradeFactory> pool = wanderingPool(level, setKey, vtpTier);
        int amount = vtpTier == 1
                ? VillagerTradingPlus.CONFIG.trade_offers_wandering_trader
                : vanillaAmount(level, trader, setKey, 2);
        draw(level, trader, pool, amount, target);
    }

    private static List<TradeFactory> wanderingPool(ServerLevel level, ResourceKey<TradeSet> setKey, int vtpTier) {
        WanderingTraderTradeLoader.Snapshot snapshot = WanderingTraderTradeLoader.snapshot();
        List<TradeFactory> custom = vtpTier == 0 ? List.of() : snapshot.tier(vtpTier);
        List<TradeFactory> pool = new ArrayList<>();
        if (!(snapshot.replaceVanilla() && !custom.isEmpty())) {
            pool.addAll(vanillaPool(level.registryAccess(), setKey));
        }
        pool.addAll(custom);
        return pool;
    }

    private static int vanillaAmount(ServerLevel level, Entity trader, ResourceKey<TradeSet> setKey, int fallback) {
        if (setKey == null) {
            return fallback;
        }
        Optional<Holder.Reference<TradeSet>> set = level.registryAccess().lookupOrThrow(Registries.TRADE_SET).get(setKey);
        if (set.isEmpty()) {
            return fallback;
        }
        TradeSet tradeSet = set.get().value();
        LootContext context = VanillaTradeFactory.context(level, trader, trader.getRandom(), tradeSet.randomSequence());
        return tradeSet.calculateNumberOfTrades(context);
    }

    public static List<TradeFactory> vanillaPool(RegistryAccess registries, ResourceKey<TradeSet> setKey) {
        if (setKey == null) {
            return List.of();
        }
        HolderLookup.RegistryLookup<TradeSet> sets = registries.lookupOrThrow(Registries.TRADE_SET);
        Optional<Holder.Reference<TradeSet>> set = sets.get(setKey);
        if (set.isEmpty()) {
            return List.of();
        }
        List<TradeFactory> pool = new ArrayList<>();
        Optional<Identifier> sequence = set.get().value().randomSequence();
        set.get().value().trades().forEach(trade -> pool.add(new VanillaTradeFactory(trade, sequence)));
        return pool;
    }
}
