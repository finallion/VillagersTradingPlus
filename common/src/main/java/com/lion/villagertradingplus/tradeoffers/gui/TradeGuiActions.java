package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpansion;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffers;

/// Server-side handler for the trade-screen control buttons, addressed by ScreenHandler.onButtonClick ids.
/// 100+level sets the villager level. 110+level re-rolls one tier. 120 re-rolls all tiers.
/// 130+level sends the catalogue for a tier. The reply needs its own packet.
public final class TradeGuiActions {

    private static final int ACTION_COOLDOWN_TICKS = 10;
    private static final int CATALOG_COOLDOWN_TICKS = 20;
    private static final Map<ServerPlayerEntity, Integer> LAST_ACTION_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Integer> LAST_CATALOG_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, List<TradeCatalogPayload>> LAST_CATALOG = new WeakHashMap<>();

    private TradeGuiActions() {
    }

    public static boolean handle(ServerPlayerEntity player, MerchantEntity merchant, int id) {
        VTPConfig cfg = VillagerTradingPlus.CONFIG;

        if (id >= 100 && id < 110) {
            if (!cfg.allow_set_villager_level || !(merchant instanceof TradeControl tc) || !tc.villagertradingplus$supportsLevels()) {
                return false;
            }

            if (!mayControlTrades(player)) {
                return false;
            }

            if (isCoolingDown(player, LAST_ACTION_TICK, ACTION_COOLDOWN_TICKS)) {
                return true;
            }

            tc.villagertradingplus$setLevel(id - 100);
            refresh(player, merchant);
            return true;
        }

        if (id >= 110 && id < 120) {
            if (!cfg.allow_trade_reroll || !(merchant instanceof TradeControl tc)) {
                return false;
            }
            if (!mayControlTrades(player)) {
                return false;
            }

            if (isCoolingDown(player, LAST_ACTION_TICK, ACTION_COOLDOWN_TICKS)) {
                return true;
            }

            tc.villagertradingplus$rerollLevel(id - 110);
            refresh(player, merchant);
            return true;
        }

        if (id == 120) {
            if (!cfg.allow_trade_reroll || !(merchant instanceof TradeControl tc)) {
                return false;
            }
            if (!mayControlTrades(player)) {
                return false;
            }

            if (isCoolingDown(player, LAST_ACTION_TICK, ACTION_COOLDOWN_TICKS)) {
                return true;
            }

            tc.villagertradingplus$rerollAll();
            refresh(player, merchant);
            return true;
        }

        if (id >= 130 && id < 140) {
            if (!cfg.allow_view_all_trades) {
                return false;
            }
            if (isCoolingDown(player, LAST_CATALOG_TICK, CATALOG_COOLDOWN_TICKS)) {
                resendLastCatalog(player);
                return true;
            }

            sendCatalog(player, merchant, id - 130);
            return true;
        }

        return false;
    }

    private static boolean mayControlTrades(ServerPlayerEntity player) {
        int required = MathHelper.clamp(VillagerTradingPlus.CONFIG.reroll_permission_level, 0, 4);
        return required == 0 || player.hasPermissionLevel(required);
    }

    private static boolean isCoolingDown(ServerPlayerEntity player, Map<ServerPlayerEntity, Integer> lastTicks, int cooldownTicks) {
        int now = player.getServer().getTicks();
        Integer previous = lastTicks.get(player);

        if (previous != null && now >= previous && now - previous < cooldownTicks) {
            return true;
        }

        lastTicks.put(player, now);
        return false;
    }

    private static void refresh(ServerPlayerEntity player, MerchantEntity merchant) {
        if (player.currentScreenHandler instanceof MerchantScreenHandler handler) {
            int level = merchant instanceof VillagerEntity villager ? villager.getVillagerData().getLevel() : 1;
            player.sendTradeOffers(handler.syncId, merchant.getOffers(), level, merchant.getExperience(),
                    merchant.isLeveledMerchant(), merchant.canRefreshTrades());
        }
    }

    private static void sendCatalog(ServerPlayerEntity player, MerchantEntity merchant, int requestedLevel) {
        int maxLevel = maxLevel(merchant);
        int level = MathHelper.clamp(requestedLevel, 1, maxLevel);

        CatalogBuilder builder = buildCatalog(merchant, level);
        // One catalogue goes out as several packets; they arrive in order and the client reassembles.
        List<TradeCatalogPayload> slices = TradeCatalogPacket.write(player.server.getRegistryManager(), level, maxLevel,
                        builder.entries(), builder.skipped());
        LAST_CATALOG.put(player, slices);
        slices.forEach(slice -> NetworkHelper.sendToPlayer(player, slice));
    }

    private static void resendLastCatalog(ServerPlayerEntity player) {
        List<TradeCatalogPayload> last = LAST_CATALOG.get(player);

        if (last != null) {
            last.forEach(slice -> NetworkHelper.sendToPlayer(player, slice));
        }
    }

    /// Villagers have five tiers. The wandering trader has two pools.
    public static int maxLevel(MerchantEntity merchant) {
        return merchant instanceof VillagerEntity ? 5 : 2;
    }

    /// Derives rows from the JSON definition, so a tier always yields the same rows.
    public static CatalogBuilder buildCatalog(MerchantEntity merchant, int level) {
        TradeOffers.Factory[] pool = poolFor(merchant, level);
        int poolSize = pool == null ? 0 : pool.length;

        CatalogBuilder builder = new CatalogBuilder(poolSize, picksPerLevel(merchant, level));
        if (pool == null) {
            return builder;
        }

        for (TradeOffers.Factory factory : pool) {
            CatalogExpansion.expandTrade(factory, merchant, builder);
        }
        return builder;
    }

    private static int picksPerLevel(MerchantEntity merchant, int level) {
        if (merchant instanceof VillagerEntity) {
            return VillagerTradingPlus.CONFIG.trade_offers_per_level;
        }
        // Wandering trader: the common pool contributes several offers, the rare pool exactly one.
        return level == 1 ? VillagerTradingPlus.CONFIG.trade_offers_wandering_trader : 1;
    }

    private static TradeOffers.Factory[] poolFor(MerchantEntity merchant, int level) {
        if (merchant instanceof VillagerEntity villager) {
            Int2ObjectMap<TradeOffers.Factory[]> map =
                    TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(villager.getVillagerData().getProfession());
            return map == null ? null : map.get(level);
        }
        // Wandering trader: level 1 = common, level 2 = rare.
        return TradeOffers.WANDERING_TRADER_TRADES.get(level);
    }
}
