package com.lion.villagertradingplus;

import com.lion.villagertradingplus.config.OmegaConfig;
import com.lion.villagertradingplus.config.VTPConfig;
import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerTradingPlus {
	public static final String MOD_ID = "villagertradingplus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final VTPConfig CONFIG = loadConfig();

	private static VTPConfig loadConfig() {
		VTPConfig config = OmegaConfig.register(VTPConfig.class);
		config.clampToValidRanges();
		return config;
	}

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
