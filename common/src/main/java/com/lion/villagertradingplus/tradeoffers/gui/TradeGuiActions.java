package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpansion;
import com.lion.villagertradingplus.tradeoffers.TradePools;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.inventory.MerchantMenu;

/// Button ids: 100+level sets the level, 110+level re-rolls a tier, 120 re-rolls all tiers.
/// 130+level sends the catalogue of a tier.
public final class TradeGuiActions {

    private static final int ACTION_COOLDOWN_TICKS = 10;
    private static final int CATALOG_COOLDOWN_TICKS = 20;
    private static final Map<ServerPlayer, Integer> LAST_ACTION_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> LAST_CATALOG_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, List<TradeCatalogPayload>> LAST_CATALOG = new WeakHashMap<>();

    private TradeGuiActions() {}

    public static boolean handle(ServerPlayer player, AbstractVillager merchant, int id) {
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

    private static boolean mayControlTrades(ServerPlayer player) {
        int required = Mth.clamp(VillagerTradingPlus.CONFIG.reroll_permission_level, 0, 4);
        return required == 0
                || player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(required)));
    }

    private static boolean isCoolingDown(ServerPlayer player, Map<ServerPlayer, Integer> lastTicks, int cooldownTicks) {
        int now = player.level().getServer().getTickCount();
        Integer previous = lastTicks.get(player);

        if (previous != null && now >= previous && now - previous < cooldownTicks) {
            return true;
        }

        lastTicks.put(player, now);
        return false;
    }

    private static void refresh(ServerPlayer player, AbstractVillager merchant) {
        if (player.containerMenu instanceof MerchantMenu handler) {
            int level = merchant instanceof Villager villager ? villager.getVillagerData().level() : 1;
            player.sendMerchantOffers(
                    handler.containerId,
                    merchant.getOffers(),
                    level,
                    merchant.getVillagerXp(),
                    merchant.showProgressBar(),
                    merchant.canRestock()
            );
        }
    }

    private static void sendCatalog(ServerPlayer player, AbstractVillager merchant, int requestedLevel) {
        int maxLevel = maxLevel(merchant);
        int level = Mth.clamp(requestedLevel, 1, maxLevel);

        CatalogBuilder builder = buildCatalog(merchant, level);
        List<TradeCatalogPayload> slices = TradeCatalogPacket.write(
                player.level().registryAccess(),
                level,
                maxLevel,
                builder.entries(),
                builder.skipped()
        );
        LAST_CATALOG.put(player, slices);
        slices.forEach(slice -> NetworkHelper.sendToPlayer(player, slice));
    }

    private static void resendLastCatalog(ServerPlayer player) {
        List<TradeCatalogPayload> last = LAST_CATALOG.get(player);

        if (last != null) {
            last.forEach(slice -> NetworkHelper.sendToPlayer(player, slice));
        }
    }

    public static int maxLevel(AbstractVillager merchant) {
        return merchant instanceof Villager ? 5 : 2;
    }

    public static CatalogBuilder buildCatalog(AbstractVillager merchant, int level) {
        if (!(merchant.level() instanceof ServerLevel serverLevel)) {
            return new CatalogBuilder(0, 0);
        }

        List<TradeFactory> pool = poolFor(serverLevel, merchant, level);
        CatalogBuilder builder = new CatalogBuilder(pool.size(), picksPerLevel(serverLevel, merchant, level));
        for (TradeFactory factory : pool) {
            CatalogExpansion.expandTrade(factory, merchant, builder);
        }
        return builder;
    }

    private static int picksPerLevel(ServerLevel serverLevel, AbstractVillager merchant, int level) {
        if (merchant instanceof Villager villager) {
            return TradePools.villagerPicks(serverLevel, merchant, villager.getVillagerData().profession(), level);
        }

        return TradePools.wanderingPicks(serverLevel, merchant, level);
    }

    private static List<TradeFactory> poolFor(ServerLevel serverLevel, AbstractVillager merchant, int level) {
        if (merchant instanceof Villager villager) {
            return TradePools.forVillager(serverLevel, villager.getVillagerData().profession(), level);
        }

        return TradePools.forWanderingTrader(serverLevel, level);
    }
}
