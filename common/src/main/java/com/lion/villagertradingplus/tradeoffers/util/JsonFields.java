package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class JsonFields {

    private JsonFields() {}

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

    public static JsonObject asObject(JsonElement element, String owner) {
        if (element == null || !element.isJsonObject()) {
            throw new TradeParseException(owner + " must contain objects, got: " + element);
        }

        return element.getAsJsonObject();
    }

    private static JsonElement require(JsonObject json, String owner, String field) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull()) {
            throw new TradeParseException(owner + " needs a \"" + field + "\" field: " + json);
        }

        return value;
    }

    private static TradeParseException wrongType(String owner, String field, String expected, JsonElement actual) {
        return new TradeParseException(owner + " needs \"" + field + "\" to be " + expected + ", got: " + actual);
    }
}
