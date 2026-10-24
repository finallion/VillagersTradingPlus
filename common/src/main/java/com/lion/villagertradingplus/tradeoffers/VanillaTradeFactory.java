package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

public final class VanillaTradeFactory implements TradeFactory {

    private static final Field GIVEN_ITEM_MODIFIERS = findField(VillagerTrade.class, "givenItemModifiers");
    private static final Field MERCHANT_PREDICATE = findField(VillagerTrade.class, "merchantPredicate");
    private static final Field WANTS = findField(VillagerTrade.class, "wants");
    private static final Field ADDITIONAL_WANTS = findField(VillagerTrade.class, "additionalWants");
    private static final Field GIVES = findField(VillagerTrade.class, "gives");
    private static final Field MAX_USES = findField(VillagerTrade.class, "maxUses");
    private static final Field XP = findField(VillagerTrade.class, "xp");
    private static final Field REPUTATION_DISCOUNT = findField(VillagerTrade.class, "reputationDiscount");

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

    public VillagerTrade trade() {
        return trade.value();
    }

    @Nullable
    public EnchantRandomlyFunction enchantRandomlyFunction() {
        if (!(read(GIVEN_ITEM_MODIFIERS, trade.value()) instanceof List<?> functions)) {
            return null;
        }

        for (Object function : functions) {
            if (function instanceof EnchantRandomlyFunction enchantRandomly) {
                return enchantRandomly;
            }
        }

        return null;
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

    /// Tests the merchant predicate like the vanilla offer does. Throws when the test fails to run.
    public boolean offeredTo(ServerLevel level, Entity trader, RandomSource random) {
        if (!(read(MERCHANT_PREDICATE, trade.value()) instanceof Optional<?> predicate) || predicate.isEmpty()) {
            return true;
        }

        Object condition = predicate.get() instanceof Holder<?> holder ? holder.value() : predicate.get();

        if (!(condition instanceof LootItemCondition lootCondition)) {
            throw new IllegalStateException("Unknown merchant predicate");
        }

        return lootCondition.test(context(level, trader, random, Optional.empty()));
    }

    /// Builds the row from the trade data only, so no structure search runs.
    @Nullable
    public MerchantOffer staticMapOffer(ServerLevel level, Entity trader, RandomSource random) {
        try {
            return buildStaticMapOffer(level, trader, random);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private MerchantOffer buildStaticMapOffer(ServerLevel level, Entity trader, RandomSource random) {
        VillagerTrade value = trade.value();
        Object wants = read(WANTS, value);
        Object gives = read(GIVES, value);
        Object maxUses = read(MAX_USES, value);
        Object xp = read(XP, value);
        Object discount = read(REPUTATION_DISCOUNT, value);

        if (!(wants instanceof TradeCost wantsCost)
                || !(gives instanceof ItemStackTemplate template)
                || !(maxUses instanceof NumberProvider maxUsesProvider)
                || !(xp instanceof NumberProvider xpProvider)
                || !(discount instanceof NumberProvider discountProvider)) {
            return null;
        }

        LootContext context = dataContext(level, trader, random);
        ItemStack result = template.create();

        if (result.is(Items.MAP)) {
            result = result.transmuteCopy(Items.FILLED_MAP);
        }

        result = applyNames(result, context);

        return new MerchantOffer(
                wantsCost.toItemCost(context, 0),
                additionalCost(context),
                result,
                Math.max(maxUsesProvider.getInt(context), 1),
                Math.max(xpProvider.getInt(context), 0),
                Math.max(discountProvider.getFloat(context), 0.0F)
        );
    }

    private ItemStack applyNames(ItemStack stack, LootContext context) {
        if (!(read(GIVEN_ITEM_MODIFIERS, trade.value()) instanceof List<?> functions)) {
            return stack;
        }

        ItemStack named = stack;

        for (Object function : functions) {
            if (function instanceof SetNameFunction setName) {
                named = setName.apply(named, context);
            }
        }

        return named;
    }

    private Optional<ItemCost> additionalCost(LootContext context) {
        if (read(ADDITIONAL_WANTS, trade.value()) instanceof Optional<?> cost
                && cost.isPresent()
                && cost.get() instanceof TradeCost additional) {
            return Optional.of(additional.toItemCost(context, 0));
        }

        return Optional.empty();
    }

    private static LootContext dataContext(ServerLevel level, Entity trader, RandomSource random) {
        ContextKeySet keys = new ContextKeySet.Builder()
                .required(LootContextParams.THIS_ENTITY)
                .required(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED)
                .build();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, trader)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(keys);
        return new LootContext.Builder(params).withOptionalRandomSource(random).create(Optional.empty());
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
