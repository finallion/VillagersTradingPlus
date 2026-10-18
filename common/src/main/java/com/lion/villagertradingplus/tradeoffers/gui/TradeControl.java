package com.lion.villagertradingplus.tradeoffers.gui;

/// Mixin interface on merchant entities. The trade actions call it directly.
public interface TradeControl {

    void villagertradingplus$rerollAll();

    default void villagertradingplus$rerollLevel(int level) {
        villagertradingplus$rerollAll();
    }

    default boolean villagertradingplus$supportsLevels() {
        return false;
    }

    default void villagertradingplus$setLevel(int level) {
    }
}
