package com.lion.villagertradingplus.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class ModCheck {

    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new AssertionError();
    }
}
