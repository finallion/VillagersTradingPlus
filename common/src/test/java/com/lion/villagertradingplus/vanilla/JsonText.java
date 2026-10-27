package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

final class JsonText {

    private static final int INLINE_LIMIT = 110;

    private JsonText() {}

    static String pretty(JsonElement element) {
        StringBuilder out = new StringBuilder();
        write(out, element, 0);
        return out.append('\n').toString();
    }

    private static void write(StringBuilder out, JsonElement element, int depth) {
        if (element.isJsonObject()) {
            writeObject(out, element.getAsJsonObject(), depth);
        } else if (element.isJsonArray()) {
            writeArray(out, element.getAsJsonArray(), depth);
        } else {
            out.append(element);
        }
    }

    private static void writeObject(StringBuilder out, JsonObject object, int depth) {
        if (object.size() == 0) {
            out.append("{}");
            return;
        }

        String inline = inline(object);

        if (inline != null && inline.length() <= INLINE_LIMIT) {
            out.append(inline);
            return;
        }

        out.append("{\n");
        int index = 0;

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            indent(out, depth + 1);
            out.append('"').append(entry.getKey()).append("\": ");
            write(out, entry.getValue(), depth + 1);
            out.append(++index < object.size() ? ",\n" : "\n");
        }

        indent(out, depth);
        out.append('}');
    }

    private static void writeArray(StringBuilder out, JsonArray array, int depth) {
        if (array.size() == 0) {
            out.append("[]");
            return;
        }

        out.append("[\n");

        for (int i = 0; i < array.size(); i++) {
            indent(out, depth + 1);
            write(out, array.get(i), depth + 1);
            out.append(i + 1 < array.size() ? ",\n" : "\n");
        }

        indent(out, depth);
        out.append(']');
    }

    private static String inline(JsonObject object) {
        StringBuilder out = new StringBuilder("{ ");
        int index = 0;

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                return null;
            }

            out.append('"').append(entry.getKey()).append("\": ").append(entry.getValue());
            out.append(++index < object.size() ? ", " : " ");
        }

        return out.append('}').toString();
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }
}
