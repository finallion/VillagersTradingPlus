package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/// Reads required datapack fields and fails with a TradeParseException that names the field.
/// A TradeParseException means the datapack is wrong. It is logged with its message and costs one trade.
/// Any other exception reads as a defect in this mod and is logged with a stack trace.
/// Optional fields use the read helpers with a default on JsonTradeOffer.
public final class JsonFields {

    private JsonFields() {
    }

    /// The owner names what is parsed, for example a weather condition. It goes into the error message.
    public static String requireString(JsonObject json, String owner, String field) {
        JsonElement value = require(json, owner, field);
        if (!value.isJsonPrimitive()) {
            throw wrongType(owner, field, "a string", value);
        }
        return value.getAsString();
    }

    public static JsonObject requireObject(JsonObject json, String owner, String field) {
        JsonElement value = require(json, owner, field);
        if (!value.isJsonObject()) {
            throw wrongType(owner, field, "an object", value);
        }
        return value.getAsJsonObject();
    }

    public static JsonArray requireArray(JsonObject json, String owner, String field) {
        JsonElement value = require(json, owner, field);
        if (!value.isJsonArray()) {
            throw wrongType(owner, field, "an array", value);
        }
        return value.getAsJsonArray();
    }

    public static int requireInt(JsonObject json, String owner, String field) {
        JsonElement value = require(json, owner, field);
        if (!value.isJsonPrimitive()) {
            throw wrongType(owner, field, "a number", value);
        }
        return value.getAsInt();
    }

    /// For elements taken out of an array, where there is no field name to report.
    public static JsonObject asObject(JsonElement element, String owner) {
        if (element == null || !element.isJsonObject()) {
            throw new TradeParseException(owner + " must contain objects, got: " + element);
        }
        return element.getAsJsonObject();
    }

    private static JsonElement require(JsonObject json, String owner, String field) {
        JsonElement value = json.get(field);
        // A JSON null is reported as missing, which is the more useful message.
        if (value == null || value.isJsonNull()) {
            throw new TradeParseException(owner + " needs a \"" + field + "\" field: " + json);
        }
        return value;
    }

    private static TradeParseException wrongType(String owner, String field, String expected, JsonElement actual) {
        return new TradeParseException(owner + " needs \"" + field + "\" to be " + expected + ", got: " + actual);
    }
}
