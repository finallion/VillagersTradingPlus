package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import java.util.List;

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

    default List<TradeFactory> villagertradingplus$catalogPool(int level) {
        return null;
    }

    default int villagertradingplus$catalogMaxLevel() {
        return 0;
    }

    default int villagertradingplus$catalogPicks(int level) {
        return 0;
    }
}
