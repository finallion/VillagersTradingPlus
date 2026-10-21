package com.lion.villagertradingplus;

import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.client.TradeControlsClientState;

public class VillagerTradingPlusClient {

	public static void init() {
		TradeCatalogClientState.register();
		TradeControlsClientState.register();
	}
}
