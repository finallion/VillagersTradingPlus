package com.lion.villagertradingplus.tradeoffers.conditions;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import com.lion.villagertradingplus.tradeoffers.util.VanillaCodecs;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.context.ContextParameter;

public final class MerchantPredicate {

    public static final String FIELD = "merchant_predicate";

    private MerchantPredicate() {}

    public static ParsedConditions parse(JsonElement json) {
        LootCondition predicate = VanillaCodecs.decode(LootCondition.CODEC, json, FIELD);
        Set<ContextParameter<?>> missing = predicate.getAllowedParameters()
                .stream()
                .filter(parameter -> !LootContextTypes.ADVANCEMENT_ENTITY.getAllowed().contains(parameter))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            throw new TradeParseException("invalid \"" + FIELD + "\": a merchant does not provide "
                    + missing.stream().map(parameter -> parameter.getId().toString()).collect(Collectors.joining(", ")));
        }

        AtomicBoolean failed = new AtomicBoolean();
        TradeCondition condition = merchant -> test(predicate, merchant, failed);
        ParsedConditions.Entry entry = new ParsedConditions.Entry(
                Text.translatable("condition.villagertradingplus.generic", FIELD),
                condition
        );
        return new ParsedConditions(List.of(entry), false);
    }

    private static boolean test(LootCondition predicate, Entity merchant, AtomicBoolean failed) {
        if (!(merchant.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        try {
            return predicate.test(context(world, merchant));
        } catch (RuntimeException e) {
            if (failed.compareAndSet(false, true)) {
                VillagerTradingPlus.LOGGER.error("The \"{}\" of a trade failed and counts as false", FIELD, e);
            }

            return false;
        }
    }

    /// Trades have no loot context here, so ADVANCEMENT_ENTITY carries the merchant and origin.
    private static LootContext context(ServerWorld world, Entity merchant) {
        LootWorldContext parameters = new LootWorldContext.Builder(world)
                .add(LootContextParameters.THIS_ENTITY, merchant)
                .add(LootContextParameters.ORIGIN, merchant.getPos())
                .build(LootContextTypes.ADVANCEMENT_ENTITY);
        return new LootContext.Builder(parameters)
                .build(Optional.empty());
    }
}
