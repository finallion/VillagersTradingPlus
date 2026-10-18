package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpansion;
import com.lion.villagertradingplus.tradeoffers.TradePools;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.inventory.MerchantMenu;

/// Button ids: 100+level sets the level, 110+level re-rolls a tier, 120 re-rolls all tiers.
/// 130+level sends the catalogue of a tier.
public final class TradeGuiActions {

    private TradeGuiActions() {
    }

    public static boolean handle(ServerPlayer player, AbstractVillager merchant, int id) {
        VTPConfig cfg = VillagerTradingPlus.CONFIG;

        if (id >= 100 && id < 110) {
            if (!cfg.allow_set_villager_level || !(merchant instanceof TradeControl tc) || !tc.villagertradingplus$supportsLevels()) {
                return false;
            }
            tc.villagertradingplus$setLevel(id - 100);
            refresh(player, merchant);
            return true;
        }

        if (id >= 110 && id < 120) {
            if (!cfg.allow_trade_reroll || !(merchant instanceof TradeControl tc)) {
                return false;
            }
            tc.villagertradingplus$rerollLevel(id - 110);
            refresh(player, merchant);
            return true;
        }

        if (id == 120) {
            if (!cfg.allow_trade_reroll || !(merchant instanceof TradeControl tc)) {
                return false;
            }
            tc.villagertradingplus$rerollAll();
            refresh(player, merchant);
            return true;
        }

        if (id >= 130 && id < 140) {
            if (!cfg.allow_view_all_trades) {
                return false;
            }
            sendCatalog(player, merchant, id - 130);
            return true;
        }

        return false;
    }

    private static void refresh(ServerPlayer player, AbstractVillager merchant) {
        if (player.containerMenu instanceof MerchantMenu handler) {
            int level = merchant instanceof Villager villager ? villager.getVillagerData().level() : 1;
            player.sendMerchantOffers(handler.containerId, merchant.getOffers(), level, merchant.getVillagerXp(),
                    merchant.showProgressBar(), merchant.canRestock());
        }
    }

    private static void sendCatalog(ServerPlayer player, AbstractVillager merchant, int requestedLevel) {
        int maxLevel = maxLevel(merchant);
        int level = Mth.clamp(requestedLevel, 1, maxLevel);

        CatalogBuilder builder = buildCatalog(merchant, level);
        // The client joins the packets of one catalogue in order.
        TradeCatalogPacket.write(player.level().registryAccess(), level, maxLevel,
                        builder.entries(), builder.skipped())
                .forEach(slice -> NetworkHelper.sendToPlayer(player, slice));
    }

    /// Villagers have five tiers. The wandering trader has two (common and uncommon).
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
