package com.lion.villagertradingplus.tradeoffers.conditions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TradeConditions {

    private static final Map<String, ConditionType> REGISTRY = new HashMap<>();
    private static final Map<String, GameRules.Key<GameRules.BooleanRule>> BOOLEAN_RULES = new HashMap<>();

    private static final int DESCRIBE_LIST_LIMIT = 3;

    private static final Set<String> WEATHER_STATES = Set.of("clear", "rain", "thunder");

    private static final List<String> WOOD_VARIANTS = List.of(
            "dark_oak",
            "pale_oak",
            "oak",
            "spruce",
            "birch",
            "jungle",
            "acacia",
            "mangrove",
            "cherry",
            "bamboo",
            "crimson",
            "warped"
    );

    private record ConditionType(Function<JsonObject, TradeCondition> parser, Function<JsonObject, Text> describer) {
    }

    static {
        GameRules.accept(new GameRules.Visitor() {
            @Override
            public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
            }

            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                BOOLEAN_RULES.put(key.getName(), key);
            }
        });
        registerBuiltins();
    }

    private TradeConditions() {}

    public static void register(String type, Function<JsonObject, TradeCondition> factory) {
        register(type, factory, json -> Text.translatable("condition.villagertradingplus.generic", type));
    }

    public static void register(
            String type,
            Function<JsonObject, TradeCondition> factory,
            Function<JsonObject, Text> describer
    ) {
        REGISTRY.put(type, new ConditionType(factory, describer));
    }

    public static ParsedConditions parse(JsonArray conditions) {
        return parse(conditions, false);
    }

    public static ParsedConditions parse(JsonArray conditions, boolean orLogic) {
        List<ParsedConditions.Entry> parsed = new ArrayList<>();
        for (JsonElement element : conditions) {
            JsonObject obj = JsonFields.asObject(element, "\"conditions\"");
            String type = stripNamespace(JsonFields.requireString(obj, "trade condition", "type"));
            ConditionType conditionType = REGISTRY.get(type);

            if (conditionType == null) {
                VillagerTradingPlus.LOGGER.error("Unknown trade condition type: " + type + " -- this trade will be withheld.");
                parsed.add(new ParsedConditions.Entry(
                        Text.translatable("condition.villagertradingplus.unknown", type),
                        villager -> false));
                continue;
            }

            TradeCondition condition = conditionType.parser().apply(obj);
            parsed.add(new ParsedConditions.Entry(describeSafely(conditionType, obj, type), condition));
        }

        return new ParsedConditions(List.copyOf(parsed), orLogic);
    }

    private static Text describeSafely(ConditionType type, JsonObject json, String typeName) {
        try {
            return type.describer().apply(json);
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.warn("Could not describe trade condition of type " + typeName, e);
            return Text.translatable("condition.villagertradingplus.generic", typeName);
        }
    }

    private static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon >= 0 ? type.substring(colon + 1) : type;
    }

    private static void registerBuiltins() {
        register("biome", TradeConditions::parseBiome, TradeConditions::describeBiome);
        register("dimension", TradeConditions::parseDimension, TradeConditions::describeDimension);
        register("weather", TradeConditions::parseWeather, TradeConditions::describeWeather);
        register("day_night", TradeConditions::parseDayNight, TradeConditions::describeDayNight);
        register("moon_phase", TradeConditions::parseMoonPhase, TradeConditions::describeMoonPhase);
        register("config_flag", TradeConditions::parseConfigFlag, TradeConditions::describeConfigFlag);
        register("gamerule", TradeConditions::parseGamerule, TradeConditions::describeGamerule);
        register("job_site_block", TradeConditions::parseJobSite, TradeConditions::describeJobSite);
    }

    private static TradeCondition parseBiome(JsonObject json) {
        if (json.has("tag")) {
            TagKey<net.minecraft.world.biome.Biome> tag = TagKey.of(
                    RegistryKeys.BIOME,
                    Identifier.tryParse(JsonFields.requireString(json, "\"biome\" condition", "tag"))
            );
            return villager -> villager.getWorld().getBiome(villager.getBlockPos()).isIn(tag);
        }

        Set<String> biomes = toStringSet(JsonFields.requireArray(json, "\"biome\" condition", "biomes"));
        return villager -> villager.getWorld().getBiome(villager.getBlockPos()).getKey()
                .map(key -> biomes.contains(key.getValue().toString()))
                .orElse(false);
    }

    private static TradeCondition parseDimension(JsonObject json) {
        String dimension = JsonFields.requireString(json, "\"dimension\" condition", "dimension");
        return villager -> villager.getWorld().getRegistryKey().getValue().toString().equals(dimension);
    }

    private static TradeCondition parseWeather(JsonObject json) {
        String state = JsonFields.requireString(json, "\"weather\" condition", "state");

        if (!WEATHER_STATES.contains(state)) {
            throw new TradeParseException("\"weather\" condition: unknown state '" + state + "' (use clear, rain or thunder)");
        }

        return villager -> {
            World world = villager.getWorld();
            return switch (state) {
                case "thunder" -> world.isThundering();
                case "rain" -> world.isRaining();
                default -> !world.isRaining();
            };
        };
    }

    private static TradeCondition parseDayNight(JsonObject json) {
        String time = JsonFields.requireString(json, "\"day_night\" condition", "time");

        if (!"day".equals(time) && !"night".equals(time)) {
            throw new TradeParseException("\"day_night\" condition: unknown time '" + time + "' (use day or night)");
        }

        boolean wantDay = "day".equals(time);
        return villager -> villager.getWorld().isDay() == wantDay;
    }

    /// The lunar day also covers daylight, so night is required by default.
    private static TradeCondition parseMoonPhase(JsonObject json) {
        Set<Integer> phases = new HashSet<>();
        for (JsonElement element : JsonFields.requireArray(json, "\"moon_phase\" condition", "phases")) {
            phases.add(element.getAsInt());
        }
        boolean requireNight = !json.has("require_night") || json.get("require_night").getAsBoolean();
        return villager -> phases.contains(villager.getWorld().getMoonPhase())
                && (!requireNight || !villager.getWorld().isDay());
    }

    private static TradeCondition parseConfigFlag(JsonObject json) {
        String fieldName = JsonFields.requireString(json, "\"config_flag\" condition", "field");
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        Field field = findBooleanConfigField(fieldName);
        return villager -> {
            try {
                return field.getBoolean(VillagerTradingPlus.CONFIG) == expected;
            } catch (IllegalAccessException e) {
                return false;
            }
        };
    }

    private static Field findBooleanConfigField(String fieldName) {
        try {
            Field field = VillagerTradingPlus.CONFIG.getClass().getField(fieldName);

            if (field.getType() != boolean.class) {
                throw new TradeParseException("\"config_flag\" condition: config field '" + fieldName + "' is not a boolean");
            }

            return field;
        } catch (NoSuchFieldException e) {
            throw new TradeParseException("\"config_flag\" condition: unknown config field '" + fieldName + "'");
        }
    }

    private static TradeCondition parseGamerule(JsonObject json) {
        String ruleName = JsonFields.requireString(json, "\"gamerule\" condition", "rule");
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        GameRules.Key<GameRules.BooleanRule> key = BOOLEAN_RULES.get(ruleName);

        if (key == null) {
            VillagerTradingPlus.LOGGER.error("gamerule condition references unknown boolean rule: " + ruleName);
            return villager -> false;
        }

        return villager -> villager.getWorld().getGameRules().getBoolean(key) == expected;
    }

    private static TradeCondition parseJobSite(JsonObject json) {
        if (json.has("wood_variant")) {
            String variant = JsonFields.requireString(json, "\"job_site_block\" condition", "wood_variant");
            boolean listed = WOOD_VARIANTS.contains(variant);
            return villager -> matchesJobSite(
                    villager,
                    id -> listed ? variant.equals(woodOf(id.getPath())) : id.getPath().contains(variant)
            );
        }

        Set<String> blocks = toStringSet(JsonFields.requireArray(json, "\"job_site_block\" condition", "blocks"));
        return villager -> matchesJobSite(villager, id -> blocks.contains(id.toString()));
    }

    /// The longest wood name inside the path, so `dark_oak` never counts as `oak`.
    private static String woodOf(String path) {
        String best = "";

        for (String wood : WOOD_VARIANTS) {
            if (path.contains(wood) && wood.length() > best.length()) {
                best = wood;
            }
        }

        return best;
    }

    private static Text describeBiome(JsonObject json) {
        if (json.has("tag")) {
            return Text.translatable("condition.villagertradingplus.biome_tag", prettify(json.get("tag").getAsString()));
        }

        return Text.translatable("condition.villagertradingplus.biome", joinArray(json.getAsJsonArray("biomes")));
    }

    private static Text describeDimension(JsonObject json) {
        return Text.translatable("condition.villagertradingplus.dimension", prettify(json.get("dimension").getAsString()));
    }

    private static Text describeWeather(JsonObject json) {
        String state = json.get("state").getAsString();
        String key = switch (state) {
            case "thunder", "rain" -> state;
            default -> "clear";
        };
        return Text.translatable(
                "condition.villagertradingplus.weather",
                Text.translatable("condition.villagertradingplus.weather." + key)
        );
    }

    private static Text describeDayNight(JsonObject json) {
        String time = "day".equals(json.get("time").getAsString()) ? "day" : "night";
        return Text.translatable(
                "condition.villagertradingplus.day_night",
                Text.translatable("condition.villagertradingplus.day_night." + time)
        );
    }

    private static Text describeMoonPhase(JsonObject json) {
        boolean requireNight = !json.has("require_night") || json.get("require_night").getAsBoolean();
        return Text.translatable(
                requireNight
                        ? "condition.villagertradingplus.moon_phase_night"
                        : "condition.villagertradingplus.moon_phase",
                joinArray(json.getAsJsonArray("phases"))
        );
    }

    private static Text describeConfigFlag(JsonObject json) {
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        return Text.translatable(
                "condition.villagertradingplus.config_flag",
                json.get("field").getAsString(),
                String.valueOf(expected)
        );
    }

    private static Text describeGamerule(JsonObject json) {
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        return Text.translatable(
                "condition.villagertradingplus.gamerule",
                json.get("rule").getAsString(),
                String.valueOf(expected)
        );
    }

    private static Text describeJobSite(JsonObject json) {
        if (json.has("wood_variant")) {
            return Text.translatable("condition.villagertradingplus.job_site_wood", json.get("wood_variant").getAsString());
        }

        return Text.translatable("condition.villagertradingplus.job_site_block", joinArray(json.getAsJsonArray("blocks")));
    }

    private static String joinArray(JsonArray array) {
        StringBuilder joined = new StringBuilder();
        int shown = Math.min(array.size(), DESCRIBE_LIST_LIMIT);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                joined.append(", ");
            }

            joined.append(prettify(array.get(i).getAsString()));
        }

        if (array.size() > shown) {
            joined.append(" +").append(array.size() - shown);
        }

        return joined.toString();
    }

    private static String prettify(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static boolean matchesJobSite(Entity entity, Predicate<Identifier> test) {
        if (!(entity instanceof VillagerEntity villager)) {
            return false;
        }

        Optional<GlobalPos> jobSite = villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);

        if (jobSite.isEmpty()) {
            return true;
        }

        GlobalPos pos = jobSite.get();
        World world = villager.getWorld();

        if (!pos.getDimension().equals(world.getRegistryKey()) || !world.isChunkLoaded(pos.getPos())) {
            return true;
        }

        return test.test(
                net.minecraft.registry.Registries.BLOCK.getId(
                        world.getBlockState(pos.getPos())
                                .getBlock()
                )
        );
    }

    private static Set<String> toStringSet(JsonArray array) {
        Set<String> set = new HashSet<>();
        for (JsonElement element : array) {
            set.add(element.getAsString());
        }
        return set;
    }
}
