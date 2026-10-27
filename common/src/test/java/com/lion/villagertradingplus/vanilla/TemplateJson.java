package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

public final class TemplateJson {

    private TemplateJson() {}

    public static String render(JsonElement element) {
        StringBuilder out = new StringBuilder();
        write(out, element, 0);
        out.append('\n');
        return out.toString();
    }

    private static void write(StringBuilder out, JsonElement element, int depth) {
        if (isFlat(element)) {
            out.append(element);
            return;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            out.append("[\n");

            for (int i = 0; i < array.size(); i++) {
                indent(out, depth + 1);
                write(out, array.get(i), depth + 1);
                out.append(i + 1 < array.size() ? ",\n" : "\n");
            }

            indent(out, depth);
            out.append(']');
            return;
        }

        JsonObject object = element.getAsJsonObject();

        if (isInline(object)) {
            writeInline(out, object);
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

    private static boolean isFlat(JsonElement element) {
        return element.isJsonPrimitive() || element.isJsonNull();
    }

    private static boolean isInline(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!isFlat(entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    private static void writeInline(StringBuilder out, JsonObject object) {
        out.append("{ ");
        int index = 0;

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            out.append('"').append(entry.getKey()).append("\": ").append(entry.getValue());
            out.append(++index < object.size() ? ", " : " ");
        }

        out.append('}');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }
}
