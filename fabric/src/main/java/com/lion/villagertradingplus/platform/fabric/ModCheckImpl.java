package com.lion.villagertradingplus.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

public class ModCheckImpl {

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
