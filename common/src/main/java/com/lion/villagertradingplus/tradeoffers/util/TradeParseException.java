package com.lion.villagertradingplus.tradeoffers.util;

/// Thrown when a trade cannot produce a usable offer, for example an unknown item id.
/// `TradeOfferManager.deserializeTrade` logs it and skips that trade.
public class TradeParseException extends RuntimeException {

    public TradeParseException(String message) {
        super(message);
    }
}
