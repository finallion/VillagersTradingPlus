package com.lion.villagertradingplus.tradeoffers.util;

/// Thrown when a trade cannot produce a usable offer, for example an item id from a missing mod.
/// TradeOfferManager.deserializeTrade catches it, logs the reason and skips that trade.
public class TradeParseException extends RuntimeException {

    public TradeParseException(String message) {
        super(message);
    }
}
