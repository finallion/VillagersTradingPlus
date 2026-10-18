package com.lion.villagertradingplus;

import com.lion.villagertradingplus.config.OmegaConfig;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Entry point of the library. A consumer mod ships JSON under its own namespace.
/// The resource listeners load it on every reload. The format is in
/// `data/villagertradingplus/available_trade_examples`.
public class VillagerTradingPlus {
	public static final String MOD_ID = "villagertradingplus";
	public static final VTPConfig CONFIG = OmegaConfig.register(VTPConfig.class);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static VTPConfig getConfig() {
		return CONFIG;
	}

	public static void init() {
		NetworkHelper.init();
		TradeOfferManager.registerTradeOffers();
	}

	public static String createStringID(String name) {
		return MOD_ID + ":" + name;
	}
}
