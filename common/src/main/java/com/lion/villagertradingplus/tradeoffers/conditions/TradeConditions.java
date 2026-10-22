package com.lion.villagertradingplus.tradeoffers.conditions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRules;
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
import java.util.function.IntPredicate;

public final class TradeConditions {

    private static final Map<String, ConditionType> REGISTRY = new HashMap<>();

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

    private record ConditionType(
            Function<JsonObject, TradeCondition> parser,
            Function<JsonObject, Component> describer
    ) {
    }

    static {
        registerBuiltins();
    }

    private TradeConditions() {}

    public static void register(String type, Function<JsonObject, TradeCondition> factory) {
        register(type, factory, json -> Component.translatable("condition.villagertradingplus.generic", type));
    }

    public static void register(
            String type,
            Function<JsonObject, TradeCondition> factory,
            Function<JsonObject, Component> describer
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
                parsed.add(
                        new ParsedConditions.Entry(
                                Component.translatable("condition.villagertradingplus.unknown", type),
                                villager -> false
                        )
                );
                continue;
            }

            TradeCondition condition = conditionType.parser().apply(obj);
            parsed.add(new ParsedConditions.Entry(describeSafely(conditionType, obj, type), condition));
        }

        return new ParsedConditions(List.copyOf(parsed), orLogic);
    }

    private static Component describeSafely(ConditionType type, JsonObject json, String typeName) {
        try {
            return type.describer().apply(json);
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.warn("Could not describe trade condition of type " + typeName, e);
            return Component.translatable("condition.villagertradingplus.generic", typeName);
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
            TagKey<net.minecraft.world.level.biome.Biome> tag = TagKey.create(
                    Registries.BIOME,
                    Identifier.tryParse(JsonFields.requireString(json, "\"biome\" condition", "tag"))
            );
            return villager -> villager.level().getBiome(villager.blockPosition()).is(tag);
        }

        Set<String> biomes = toStringSet(JsonFields.requireArray(json, "\"biome\" condition", "biomes"));
        return villager -> villager.level().getBiome(villager.blockPosition()).unwrapKey()
                .map(key -> biomes.contains(key.identifier().toString()))
                .orElse(false);
    }

    private static TradeCondition parseDimension(JsonObject json) {
        String dimension = JsonFields.requireString(json, "\"dimension\" condition", "dimension");
        return villager -> villager.level().dimension().identifier().toString().equals(dimension);
    }

    private static TradeCondition parseWeather(JsonObject json) {
        String state = JsonFields.requireString(json, "\"weather\" condition", "state");

        if (!WEATHER_STATES.contains(state)) {
            throw new TradeParseException("\"weather\" condition: unknown state '" + state + "' (use clear, rain or thunder)");
        }

        return villager -> {
            Level world = villager.level();
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
        return villager -> villager.level().isBrightOutside() == wantDay;
    }

    /// The lunar day also covers daylight, so night is required by default.
    private static TradeCondition parseMoonPhase(JsonObject json) {
        Set<Integer> phases = new HashSet<>();
        for (JsonElement element : JsonFields.requireArray(json, "\"moon_phase\" condition", "phases")) {
            phases.add(element.getAsInt());
        }
        boolean requireNight = !json.has("require_night") || json.get("require_night").getAsBoolean();
        return villager -> phases.contains(
                villager.level().environmentAttributes() .getDimensionValue(EnvironmentAttributes.MOON_PHASE).index()
        )
                && (!requireNight || !villager.level().isBrightOutside());
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

    private record LegacyRule(String path, boolean inverted) {
        static LegacyRule of(String path) {
            return new LegacyRule(path, false);
        }

        static LegacyRule inverse(String path) {
            return new LegacyRule(path, true);
        }
    }

    private static final Map<String, LegacyRule> LEGACY_BOOLEAN_RULES = Map.ofEntries(
            Map.entry("announceAdvancements", LegacyRule.of("show_advancement_messages")),
            Map.entry("commandBlocksEnabled", LegacyRule.of("command_blocks_work")),
            Map.entry("enableCommandBlocks", LegacyRule.of("command_blocks_work")),
            Map.entry("disableElytraMovementCheck", LegacyRule.inverse("elytra_movement_check")),
            Map.entry("disablePlayerMovementCheck", LegacyRule.inverse("player_movement_check")),
            Map.entry("disableRaids", LegacyRule.inverse("raids")),
            Map.entry("doDaylightCycle", LegacyRule.of("advance_time")),
            Map.entry("doEntityDrops", LegacyRule.of("entity_drops")),
            Map.entry("doImmediateRespawn", LegacyRule.of("immediate_respawn")),
            Map.entry("doInsomnia", LegacyRule.of("spawn_phantoms")),
            Map.entry("doLimitedCrafting", LegacyRule.of("limited_crafting")),
            Map.entry("doMobLoot", LegacyRule.of("mob_drops")),
            Map.entry("doMobSpawning", LegacyRule.of("spawn_mobs")),
            Map.entry("doPatrolSpawning", LegacyRule.of("spawn_patrols")),
            Map.entry("doTileDrops", LegacyRule.of("block_drops")),
            Map.entry("doTraderSpawning", LegacyRule.of("spawn_wandering_traders")),
            Map.entry("doVinesSpread", LegacyRule.of("spread_vines")),
            Map.entry("doWardenSpawning", LegacyRule.of("spawn_wardens")),
            Map.entry("doWeatherCycle", LegacyRule.of("advance_weather")),
            Map.entry("naturalRegeneration", LegacyRule.of("natural_health_regeneration")),
            Map.entry("spawnerBlocksEnabled", LegacyRule.of("spawner_blocks_work"))
    );

    private static final String LEGACY_DO_FIRE_TICK = "doFireTick";
    private static final String LEGACY_FIRE_TICKS_AWAY_FROM_PLAYER = "allowFireTicksAwayFromPlayer";
    private static final String LEGACY_PASSENGERS_USE_PORTALS = "entitiesWithPassengersCanUsePortals";

    private static GameRule<Boolean> findBooleanRule(String name) {
        for (GameRule<?> rule : BuiltInRegistries.GAME_RULE) {
            if (rule.gameRuleType() != GameRuleType.BOOL) {
                continue;
            }

            String path = rule.getIdentifier().getPath();

            if (name.equals(rule.getIdentifier().toString()) || name.equals(path) || name.equalsIgnoreCase(path.replace("_", ""))) {
                @SuppressWarnings("unchecked")
                GameRule<Boolean> bool = (GameRule<Boolean>) rule;
                return bool;
            }
        }
        return null;
    }

    private static TradeCondition parseGamerule(JsonObject json) {
        String ruleName = JsonFields.requireString(json, "\"gamerule\" condition", "rule");
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        switch (ruleName) {
            case LEGACY_DO_FIRE_TICK:
                return fireSpreadCondition(radius -> radius != 0, expected);
            case LEGACY_FIRE_TICKS_AWAY_FROM_PLAYER:
                return fireSpreadCondition(radius -> radius == -1, expected);
            case LEGACY_PASSENGERS_USE_PORTALS:
                VillagerTradingPlus.LOGGER.warn("gamerule condition: " + ruleName + " no longer exists; the condition reads it as true");
                return villager -> expected;
            default:
                break;
        }
        LegacyRule legacy = LEGACY_BOOLEAN_RULES.get(ruleName);
        String lookup = legacy == null ? ruleName : legacy.path();
        boolean wanted = legacy != null && legacy.inverted() ? !expected : expected;
        GameRule<Boolean> key = findBooleanRule(lookup);

        if (key == null) {
            VillagerTradingPlus.LOGGER.error("gamerule condition references unknown boolean rule: " + ruleName);
            return villager -> false;
        }

        return villager -> villager.level() instanceof ServerLevel serverWorld
                && serverWorld.getGameRules().get(key) == wanted;
    }

    /// Fire rules use one int radius: 0 is off, -1 is everywhere, else near players only.
    private static TradeCondition fireSpreadCondition(IntPredicate test, boolean expected) {
        GameRule<Integer> radius = GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER;
        return villager -> villager.level() instanceof ServerLevel serverWorld
                && test.test(serverWorld.getGameRules().get(radius)) == expected;
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

    private static Component describeBiome(JsonObject json) {
        if (json.has("tag")) {
            return Component.translatable("condition.villagertradingplus.biome_tag", prettify(json.get("tag").getAsString()));
        }

        return Component.translatable("condition.villagertradingplus.biome", joinArray(json.getAsJsonArray("biomes")));
    }

    private static Component describeDimension(JsonObject json) {
        return Component.translatable("condition.villagertradingplus.dimension", prettify(json.get("dimension").getAsString()));
    }

    private static Component describeWeather(JsonObject json) {
        String state = json.get("state").getAsString();
        String key = switch (state) {
            case "thunder", "rain" -> state;
            default -> "clear";
        };
        return Component.translatable(
                "condition.villagertradingplus.weather",
                Component.translatable("condition.villagertradingplus.weather." + key)
        );
    }

    private static Component describeDayNight(JsonObject json) {
        String time = "day".equals(json.get("time").getAsString()) ? "day" : "night";
        return Component.translatable(
                "condition.villagertradingplus.day_night",
                Component.translatable("condition.villagertradingplus.day_night." + time)
        );
    }

    private static Component describeMoonPhase(JsonObject json) {
        boolean requireNight = !json.has("require_night") || json.get("require_night").getAsBoolean();
        return Component.translatable(
                requireNight
                        ? "condition.villagertradingplus.moon_phase_night"
                        : "condition.villagertradingplus.moon_phase",
                joinArray(json.getAsJsonArray("phases"))
        );
    }

    private static Component describeConfigFlag(JsonObject json) {
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        return Component.translatable(
                "condition.villagertradingplus.config_flag",
                json.get("field").getAsString(),
                String.valueOf(expected)
        );
    }

    private static Component describeGamerule(JsonObject json) {
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        return Component.translatable(
                "condition.villagertradingplus.gamerule",
                json.get("rule").getAsString(),
                String.valueOf(expected)
        );
    }

    private static Component describeJobSite(JsonObject json) {
        if (json.has("wood_variant")) {
            return Component.translatable("condition.villagertradingplus.job_site_wood", json.get("wood_variant").getAsString());
        }

        return Component.translatable("condition.villagertradingplus.job_site_block", joinArray(json.getAsJsonArray("blocks")));
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
        if (!(entity instanceof Villager villager)) {
            return false;
        }

        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);

        if (jobSite.isEmpty()) {
            return true;
        }

        GlobalPos pos = jobSite.get();
        Level world = villager.level();

        if (!pos.dimension().equals(world.dimension()) || !world.isLoaded(pos.pos())) {
            return true;
        }

        return test.test(
                BuiltInRegistries.BLOCK.getKey(
                        world.getBlockState(pos.pos())
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
