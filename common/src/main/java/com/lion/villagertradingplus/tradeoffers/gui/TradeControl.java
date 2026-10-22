package com.lion.villagertradingplus.tradeoffers.gui;

import net.minecraft.village.TradeOffers;

public interface TradeControl {

    void villagertradingplus$rerollAll();

    default void villagertradingplus$rerollLevel(int level) {
        villagertradingplus$rerollAll();
    }

    default boolean villagertradingplus$rerollLevelChecked(int level) {
        villagertradingplus$rerollLevel(level);
        return true;
    }

    default boolean villagertradingplus$supportsLevels() {
        return false;
    }

    default void villagertradingplus$setLevel(int level) {
    }

    default TradeOffers.Factory[] villagertradingplus$catalogPool(int level) {
        return null;
    }

    default int villagertradingplus$catalogMaxLevel() {
        return 0;
    }

    default int villagertradingplus$catalogPicks(int level) {
        return 0;
    }
}
