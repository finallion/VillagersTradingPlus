package com.lion.villagertradingplus;

import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.client.TradeControlsClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public class VillagerTradingPlusClient {

	@Environment(EnvType.CLIENT)
	public static void init() {
		TradeCatalogClientState.register();
		TradeControlsClientState.register();
	}
}
