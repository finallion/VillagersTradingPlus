package com.lion.villagertradingplus.tradeoffers.gui;

/// Duck interface implemented by merchant entities through a mixin.
/// The trade-screen controls use it to reset trades and set levels without a networking layer.
public interface TradeControl {

    void villagertradingplus$rerollAll();

    /// Re-rolls one tier and keeps the others. Traders without levels re-roll all tiers.
    default void villagertradingplus$rerollLevel(int level) {
        villagertradingplus$rerollAll();
    }

    default boolean villagertradingplus$supportsLevels() {
        return false;
    }

    /// No-op for merchants without levels.
    default void villagertradingplus$setLevel(int level) {
    }
}
