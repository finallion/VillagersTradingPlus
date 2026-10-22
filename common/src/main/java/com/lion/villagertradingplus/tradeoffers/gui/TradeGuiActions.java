package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpansion;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffers;

/// 100+level sets the villager level. 110+level re-rolls one tier. 120 re-rolls all tiers.
/// 130+level requests a catalogue. 140 requests the allowed controls.
public final class TradeGuiActions {

    private static final int ACTION_COOLDOWN_TICKS = 10;
    private static final int CATALOG_COOLDOWN_TICKS = 20;
    private static final Map<ServerPlayerEntity, Integer> LAST_ACTION_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Integer> LAST_CATALOG_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, PendingCatalog> PENDING_CATALOG = new WeakHashMap<>();

    private TradeGuiActions() {}

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

            if (!tc.villagertradingplus$rerollLevelChecked(id - 110)) {
                player.sendMessage(Text.translatable("message.villagertradingplus.reroll_level_unavailable"), true);
            }

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
            if (!cfg.allow_view_all_trades || !hasCatalog(merchant)) {
                return false;
            }

            if (isCoolingDown(player, LAST_CATALOG_TICK, CATALOG_COOLDOWN_TICKS)) {
                PENDING_CATALOG.put(player, new PendingCatalog(merchant.getUuid(), id - 130));
                return true;
            }

            PENDING_CATALOG.remove(player);
            sendCatalog(player, merchant, id - 130);
            return true;
        }

        if (id == 140) {
            sendControls(player, merchant);
            return true;
        }

        return false;
    }

    private static void sendControls(ServerPlayerEntity player, MerchantEntity merchant) {
        VTPConfig cfg = VillagerTradingPlus.CONFIG;
        boolean may = mayControlTrades(player);
        boolean control = merchant instanceof TradeControl;
        boolean levels = merchant instanceof TradeControl tc && tc.villagertradingplus$supportsLevels();

        NetworkHelper.sendToPlayer(
                player,
                new TradeControlsPayload(
                        cfg.allow_set_villager_level && levels && may,
                        cfg.allow_trade_reroll && control && may,
                        cfg.allow_view_all_trades && hasCatalog(merchant),
                        maxLevel(merchant)
                )
        );
    }

    private static boolean mayControlTrades(ServerPlayerEntity player) {
        int required = MathHelper.clamp(VillagerTradingPlus.CONFIG.reroll_permission_level, 0, 4);
        return required == 0 || player.hasPermissionLevel(required);
    }

    private static boolean isCoolingDown(
            ServerPlayerEntity player,
            Map<ServerPlayerEntity, Integer> lastTicks,
            int cooldownTicks
    ) {
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
            int level = merchant instanceof VillagerEntity villager ? villager.getVillagerData().level() : 1;
            player.sendTradeOffers(
                    handler.syncId,
                    merchant.getOffers(),
                    level,
                    merchant.getExperience(),
                    merchant.isLeveledMerchant(),
                    merchant.canRefreshTrades()
            );
        }
    }

    private static void sendCatalog(ServerPlayerEntity player, MerchantEntity merchant, int requestedLevel) {
        int maxLevel = maxLevel(merchant);
        int level = MathHelper.clamp(requestedLevel, 1, maxLevel);

        CatalogBuilder builder = buildCatalog(merchant, level);
        List<TradeCatalogPayload> slices = TradeCatalogPacket.write(
                player.getServer().getRegistryManager(),
                level,
                maxLevel,
                builder.entries(),
                builder.skipped()
        );
        slices.forEach(slice -> NetworkHelper.sendToPlayer(player, slice));
    }

    private record PendingCatalog(UUID merchant, int level) {}

    public static void servePending(ServerPlayerEntity player, MerchantEntity merchant) {
        PendingCatalog pending = PENDING_CATALOG.get(player);

        if (pending == null) {
            return;
        }

        if (!pending.merchant().equals(merchant.getUuid()) || !hasCatalog(merchant)) {
            PENDING_CATALOG.remove(player);
            return;
        }

        if (isCoolingDown(player, LAST_CATALOG_TICK, CATALOG_COOLDOWN_TICKS)) {
            return;
        }

        PENDING_CATALOG.remove(player);
        sendCatalog(player, merchant, pending.level());
    }

    public static void forgetPending(ServerPlayerEntity player) {
        PENDING_CATALOG.remove(player);
    }

    public static boolean hasCatalog(MerchantEntity merchant) {
        return merchant instanceof VillagerEntity || merchant instanceof TradeControl;
    }

    public static int maxLevel(MerchantEntity merchant) {
        if (merchant instanceof TradeControl control) {
            int maxLevel = control.villagertradingplus$catalogMaxLevel();

            if (maxLevel > 0) {
                return maxLevel;
            }
        }

        return merchant instanceof VillagerEntity ? 5 : 2;
    }

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
        if (merchant instanceof TradeControl control) {
            int picks = control.villagertradingplus$catalogPicks(level);

            if (picks > 0) {
                return picks;
            }
        }

        if (merchant instanceof VillagerEntity) {
            return VillagerTradingPlus.CONFIG.trade_offers_per_level;
        }

        return WanderingTraderTradeLoader.countForLevel(level);
    }

    private static TradeOffers.Factory[] poolFor(MerchantEntity merchant, int level) {
        if (merchant instanceof TradeControl control) {
            TradeOffers.Factory[] custom = control.villagertradingplus$catalogPool(level);

            if (custom != null) {
                return custom;
            }
        }

        if (merchant instanceof VillagerEntity villager) {
            Int2ObjectMap<TradeOffers.Factory[]> map =
                    villager.getVillagerData().profession().getKey()
                            .map(TradeOffers.PROFESSION_TO_LEVELED_TRADE::get).orElse(null);
            return map == null ? null : map.get(level);
        }

        return WanderingTraderTradeLoader.poolForLevel(level);
    }
}
