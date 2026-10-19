package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public final class VanillaTradeFactory implements TradeFactory {

    private static final Field GIVEN_ITEM_MODIFIERS = findField(VillagerTrade.class, "givenItemModifiers");

    private final Holder<VillagerTrade> trade;
    private final Optional<Identifier> randomSequence;
    private final boolean rollsStructureSearch;

    public VanillaTradeFactory(Holder<VillagerTrade> trade, Optional<Identifier> randomSequence) {
        this.trade = trade;
        this.randomSequence = randomSequence;
        this.rollsStructureSearch = searchesStructures(read(GIVEN_ITEM_MODIFIERS, trade.value()));
    }

    public boolean rollsStructureSearch() {
        return rollsStructureSearch;
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VillagerTradingPlus.LOGGER.warn("Cannot read {}.{}; the catalog may roll map trades.", owner.getSimpleName(), name);
            return null;
        }
    }

    private static Object read(Field field, Object owner) {
        if (field == null) {
            return null;
        }

        try {
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean searchesStructures(Object modifiers) {
        if (!(modifiers instanceof List<?> functions)) {
            return false;
        }

        for (Object function : functions) {
            if (function instanceof ExplorationMapFunction || nestedSearchesStructures(function)) {
                return true;
            }
        }

        return false;
    }

    private static boolean nestedSearchesStructures(Object function) {
        if (!"SequenceFunction".equals(function.getClass().getSimpleName())) {
            return false;
        }

        return searchesStructures(read(findField(function.getClass(), "functions"), function));
    }


    @Override
    public MerchantOffer create(ServerLevel level, Entity trader, RandomSource random) {
        return trade.value().getOffer(context(level, trader, random, randomSequence));
    }

    public static LootContext context(
            ServerLevel level,
            Entity trader,
            RandomSource random,
            Optional<Identifier> randomSequence
    ) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, trader.position())
                .withParameter(LootContextParams.THIS_ENTITY, trader)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE);
        return new LootContext.Builder(params).withOptionalRandomSource(random).create(randomSequence);
    }
}
