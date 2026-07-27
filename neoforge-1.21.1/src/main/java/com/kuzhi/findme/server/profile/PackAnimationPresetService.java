package com.kuzhi.findme.server.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionRescueMotion;
import com.kuzhi.findme.common.PackAnimationPresetCategory;
import com.kuzhi.findme.common.PackEntityCategoryOverride;
import com.kuzhi.findme.common.PackEntityMovementOverride;
import com.kuzhi.findme.common.PackEntityBindingRequirement;
import com.kuzhi.findme.common.BindingAnimationPolicy;
import com.kuzhi.findme.common.PackEntityPresetField;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.PackAnimationPresetListPacket;
import com.kuzhi.findme.network.PackAnimationPresetModePacket;
import com.kuzhi.findme.server.vehicle.SableVehicleCompatibility;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.fml.loading.FMLPaths;

public final class PackAnimationPresetService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Preset> PRESETS = new HashMap<>();
    private static final Set<java.util.UUID> EDITORS = new HashSet<>();
    private static boolean loaded;

    private PackAnimationPresetService() {
    }

    public static void openEditor(ServerPlayer player) {
        load();
        EDITORS.add(player.getUUID());
        ModNetwork.sendToPlayer(player, new PackAnimationPresetModePacket(true, true));
        sync(player);
        player.displayClientMessage(Component.translatable("message.find_me.pack_edit_open").withStyle(ChatFormatting.AQUA), false);
    }

    public static void closeEditor(ServerPlayer player) {
        load();
        save();
        EDITORS.remove(player.getUUID());
        ModNetwork.sendToPlayer(player, new PackAnimationPresetModePacket(false, false));
        player.displayClientMessage(Component.translatable("message.find_me.pack_edit_closed", path().toString()).withStyle(ChatFormatting.GREEN), false);
    }

    public static void reload(ServerPlayer player) {
        loaded = false;
        load();
        sync(player);
        player.displayClientMessage(Component.translatable("message.find_me.pack_edit_reloaded").withStyle(ChatFormatting.GREEN), false);
    }

    public static int reload(CommandSourceStack source) {
        loaded = false;
        load();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (EDITORS.contains(player.getUUID())) {
                sync(player);
            }
        }
        source.sendSuccess(() -> Component.translatable("message.find_me.pack_edit_reloaded"), true);
        return 1;
    }

    public static void exportPath(ServerPlayer player) {
        load();
        save();
        player.displayClientMessage(Component.translatable("message.find_me.pack_edit_exported", path().toString()).withStyle(ChatFormatting.GREEN), false);
    }

    public static boolean isEditor(ServerPlayer player) {
        return player != null && EDITORS.contains(player.getUUID());
    }

    public static void forgetEditor(ServerPlayer player) {
        if (player != null) EDITORS.remove(player.getUUID());
    }

    public static void setStyles(ServerPlayer player, List<String> entityTypes, CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        if (!isEditor(player) || entityTypes == null || purpose == null || style == null) {
            return;
        }
        load();
        for (String entityType : entityTypes) {
            String key = normalize(entityType);
            if (!key.isBlank()) {
                Preset preset = preset(key);
                applyLegacyStyle(preset, purpose, style);
            }
        }
        save();
        syncChanged(player, entityTypes);
    }

    public static void setAnimationStyles(ServerPlayer player, List<String> entityTypes,
                                          CompanionAnimationPurpose purpose, CompanionAnimationStyle style) {
        if (!isEditor(player) || entityTypes == null || purpose == null || style == null) {
            return;
        }
        load();
        for (String entityType : entityTypes) {
            String key = normalize(entityType);
            if (!key.isBlank()) {
                preset(key).animations.put(purpose, style);
            }
        }
        save();
        syncChanged(player, entityTypes);
    }

    public static void setField(ServerPlayer player, List<String> entityTypes, PackEntityPresetField field, String value) {
        if (!isEditor(player) || entityTypes == null || field == null) {
            return;
        }
        load();
        for (String entityType : entityTypes) {
            String key = normalize(entityType);
            if (key.isBlank()) {
                continue;
            }
            if (field == PackEntityPresetField.RESET) {
                PRESETS.remove(key);
                continue;
            }
            Preset preset = preset(key);
            switch (field) {
                case CATEGORY -> parseEnum(PackEntityCategoryOverride.class, value).ifPresent(parsed -> preset.category = parsed);
                case MOVEMENT -> parseEnum(PackEntityMovementOverride.class, value).ifPresent(parsed -> preset.movement = parsed);
                case BINDING_REQUIREMENT -> parseEnum(PackEntityBindingRequirement.class, value)
                        .ifPresent(parsed -> preset.bindingRequirement = parsed);
                case BINDING_ANIMATION -> parseEnum(BindingAnimationPolicy.class, value)
                        .ifPresent(parsed -> preset.bindingAnimationPolicy = parsed);
                case RESCUE_MOTION -> parseEnum(CompanionRescueMotion.class, value).ifPresent(parsed -> preset.rescueMotion = parsed);
                case BOUNDS_SCALE -> preset.boundsScale = parseScale(value, preset.boundsScale);
                case CIRCLE_SCALE -> preset.circleScale = parseScale(value, preset.circleScale);
                case ARRIVAL_SOUND -> {
                    String sound = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                    if (sound.isBlank() || validSoundId(sound)) {
                        preset.arrivalSound = sound;
                    }
                }
                case ARRIVAL_VOLUME -> preset.arrivalVolume = parseAudioScale(value, preset.arrivalVolume);
                case ARRIVAL_PITCH -> preset.arrivalPitch = parseAudioScale(value, preset.arrivalPitch);
                case PREVIEW_NBT -> normalizePreviewNbt(key, value).ifPresent(parsed -> preset.previewNbt = parsed);
                case RESET -> {
                }
            }
        }
        save();
        if (field == PackEntityPresetField.RESET) {
            sync(player);
        } else {
            syncChanged(player, entityTypes);
        }
    }

    public static CompanionEffectStyle style(String entityType, CompanionEffectPurpose purpose) {
        load();
        Preset preset = PRESETS.get(normalize(entityType));
        if (preset == null) {
            return CompanionEffectStyle.DEFAULT;
        }
        return preset.styles.getOrDefault(purpose, CompanionEffectStyle.DEFAULT).visualStyle();
    }

    public static CompanionAnimationStyle animationStyle(String entityType, CompanionAnimationPurpose purpose) {
        load();
        Preset preset = PRESETS.get(normalize(entityType));
        if (preset == null) {
            return CompanionAnimationStyle.STANDARD;
        }
        return preset.animations.getOrDefault(purpose, CompanionAnimationStyle.STANDARD);
    }

    public static Optional<PackEntityCategoryOverride> categoryOverride(String entityType) {
        load();
        Preset preset = PRESETS.get(normalize(entityType));
        if (preset == null || preset.category == PackEntityCategoryOverride.AUTO) {
            return Optional.empty();
        }
        return Optional.of(preset.category);
    }

    public static PackAnimationPresetCategory effectiveCategory(String entityType) {
        String key = normalize(entityType);
        Optional<PackEntityCategoryOverride> override = categoryOverride(key);
        if (override.isPresent()) {
            return switch (override.get()) {
                case MOUNT -> PackAnimationPresetCategory.MOUNT;
                case COMPANION -> PackAnimationPresetCategory.COMPANION;
                case VEHICLE -> PackAnimationPresetCategory.VEHICLE;
                case DISABLED -> PackAnimationPresetCategory.DISABLED;
                case AUTO -> PackAnimationPresetCategory.COMPANION;
            };
        }
        if (movementOverride(key).isPresent()) return PackAnimationPresetCategory.MOUNT;
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return autoCategory(key, BuiltInRegistries.ENTITY_TYPE.get(id));
        }
        return isLikelyVehicleKey(key) ? PackAnimationPresetCategory.VEHICLE : PackAnimationPresetCategory.COMPANION;
    }

    public static Optional<CompanionMoveType> movementOverride(String entityType) {
        load();
        Preset preset = PRESETS.get(normalize(entityType));
        if (preset == null || preset.movement == PackEntityMovementOverride.AUTO) {
            return Optional.empty();
        }
        return switch (preset.movement) {
            case WALK -> Optional.of(CompanionMoveType.WALK);
            case FLY -> Optional.of(CompanionMoveType.FLY);
            case SWIM -> Optional.of(CompanionMoveType.SWIM);
            case AUTO -> Optional.empty();
        };
    }

    public static double boundsScale(String entityType) {
        load();
        Preset preset = PRESETS.get(normalize(entityType));
        return preset == null ? 1.0 : clampScale(preset.boundsScale);
    }

    public static double circleScale(String entityType) {
        load();
        Preset preset = PRESETS.get(normalize(entityType));
        return preset == null ? 1.0 : clampScale(preset.circleScale);
    }

    public static PackEntityBindingRequirement bindingRequirement(String entityType) {
        load();
        return PRESETS.getOrDefault(normalize(entityType), Preset.EMPTY).bindingRequirement;
    }

    public static BindingAnimationPolicy bindingAnimationPolicy(String entityType) {
        load();
        return PRESETS.getOrDefault(normalize(entityType), Preset.EMPTY).bindingAnimationPolicy;
    }

    public static CompanionRescueMotion rescueMotion(String entityType) {
        load();
        return PRESETS.getOrDefault(normalize(entityType), Preset.EMPTY).rescueMotion;
    }

    public static Optional<ResourceLocation> arrivalSound(String entityType) {
        load();
        String value = PRESETS.getOrDefault(normalize(entityType), Preset.EMPTY).arrivalSound;
        if (value.isBlank()) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public static float arrivalVolume(String entityType) {
        load();
        return (float) clampAudio(PRESETS.getOrDefault(normalize(entityType), Preset.EMPTY).arrivalVolume);
    }

    public static float arrivalPitch(String entityType) {
        load();
        return (float) clampAudio(PRESETS.getOrDefault(normalize(entityType), Preset.EMPTY).arrivalPitch);
    }

    public static void sync(ServerPlayer player) {
        if (player != null) {
            ModNetwork.sendToPlayer(player, new PackAnimationPresetListPacket(true, entries()));
        }
    }

    private static void syncChanged(ServerPlayer player, List<String> entityTypes) {
        if (player == null || entityTypes == null || entityTypes.isEmpty()) return;
        Set<String> requested = entityTypes.stream().map(PackAnimationPresetService::normalize).collect(java.util.stream.Collectors.toSet());
        List<PackAnimationPresetListPacket.Entry> changed = entries().stream()
                .filter(entry -> requested.contains(normalize(entry.entityType())))
                .toList();
        ModNetwork.sendToPlayer(player, new PackAnimationPresetListPacket(false, changed));
    }

    private static List<PackAnimationPresetListPacket.Entry> entries() {
        load();
        ArrayList<PackAnimationPresetListPacket.Entry> entries = new ArrayList<>();
        HashSet<String> added = new HashSet<>();
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            String key = id.toString();
            if (!shouldShow(key, type)) {
                continue;
            }
            entries.add(entry(key, type.getDescription().getString(), category(key, type)));
            added.add(key);
        }
        if (added.add(SableVehicleCompatibility.TYPE)) {
            entries.add(entry(SableVehicleCompatibility.TYPE, "Sable / Simulated Vehicle", PackAnimationPresetCategory.VEHICLE));
        }
        entries.sort(Comparator.comparing(PackAnimationPresetListPacket.Entry::category).thenComparing(PackAnimationPresetListPacket.Entry::entityType));
        return entries;
    }

    private static PackAnimationPresetListPacket.Entry entry(String key, String name, PackAnimationPresetCategory category) {
        Preset preset = PRESETS.getOrDefault(normalize(key), Preset.EMPTY);
        return new PackAnimationPresetListPacket.Entry(key, name, category, preset.category, preset.movement,
                preset.bindingRequirement, preset.bindingAnimationPolicy, preset.rescueMotion,
                animationStyle(key, CompanionAnimationPurpose.SUMMON), animationStyle(key, CompanionAnimationPurpose.RESCUE),
                animationStyle(key, CompanionAnimationPurpose.STORAGE), animationStyle(key, CompanionAnimationPurpose.SWITCH),
                (float)clampScale(preset.boundsScale), (float)clampScale(preset.circleScale), preset.arrivalSound,
                (float)clampAudio(preset.arrivalVolume), (float)clampAudio(preset.arrivalPitch),
                style(key, CompanionEffectPurpose.SUMMON), style(key, CompanionEffectPurpose.RESCUE), style(key, CompanionEffectPurpose.STORAGE),
                preset.previewNbt);
    }

    private static boolean shouldShow(String key, EntityType<?> type) {
        String normalized = normalize(key);
        if (PRESETS.containsKey(normalized)) {
            return true;
        }
        if (isLikelyVehicleKey(normalized)) {
            return true;
        }
        MobCategory category = type.getCategory();
        return category != MobCategory.MISC;
    }

    private static PackAnimationPresetCategory category(String key, EntityType<?> type) {
        String normalized = normalize(key);
        Optional<PackEntityCategoryOverride> override = categoryOverride(normalized);
        if (override.isPresent()) {
            return switch (override.get()) {
                case MOUNT -> PackAnimationPresetCategory.MOUNT;
                case COMPANION -> PackAnimationPresetCategory.COMPANION;
                case VEHICLE -> PackAnimationPresetCategory.VEHICLE;
                case DISABLED -> PackAnimationPresetCategory.DISABLED;
                case AUTO -> autoCategory(normalized, type);
            };
        }
        if (movementOverride(normalized).isPresent()) {
            return PackAnimationPresetCategory.MOUNT;
        }
        return autoCategory(normalized, type);
    }

    private static PackAnimationPresetCategory autoCategory(String key, EntityType<?> type) {
        if (isLikelyVehicleKey(key) || type.getCategory() == MobCategory.MISC && !key.startsWith("minecraft:")) {
            return PackAnimationPresetCategory.VEHICLE;
        }
        if (CompanionEntityClassifier.isLikelyMountKey(key)) {
            return PackAnimationPresetCategory.MOUNT;
        }
        return PackAnimationPresetCategory.COMPANION;
    }

    private static boolean isLikelyVehicleKey(String key) {
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        String namespace = key.contains(":") ? key.substring(0, key.indexOf(':')) : "";
        return key.equals(SableVehicleCompatibility.TYPE)
                || namespace.equals("immersive_aircraft")
                || namespace.equals("aeronautics")
                || namespace.equals("simulated")
                || namespace.equals("automobility")
                || path.contains("vehicle")
                || path.contains("aircraft")
                || path.contains("airship")
                || path.contains("balloon")
                || path.contains("boat")
                || path.contains("ship")
                || path.contains("cart")
                || path.contains("minecart")
                || path.contains("carriage")
                || path.contains("contraption");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        PRESETS.clear();
        readLegacyAnimationFile();
        readFile(path());
    }

    private static void readFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int format = root.has("format") ? root.get("format").getAsInt() : 1;
            JsonArray entries = root.has("entries") ? root.getAsJsonArray("entries") : new JsonArray();
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String entityType = normalize(object.has("entity") ? object.get("entity").getAsString() : "");
                if (entityType.isBlank()) {
                    continue;
                }
                Preset preset = preset(entityType);
                if (format >= 4) {
                    readStyle(object, preset.styles, "summonEffect", CompanionEffectPurpose.SUMMON);
                    readStyle(object, preset.styles, "rescueEffect", CompanionEffectPurpose.RESCUE);
                    readStyle(object, preset.styles, "storageEffect", CompanionEffectPurpose.STORAGE);
                    readAnimation(object, preset.animations, "summonAnimation", CompanionAnimationPurpose.SUMMON);
                    readAnimation(object, preset.animations, "rescueAnimation", CompanionAnimationPurpose.RESCUE);
                    readAnimation(object, preset.animations, "storageAnimation", CompanionAnimationPurpose.STORAGE);
                    readAnimation(object, preset.animations, "switchAnimation", CompanionAnimationPurpose.SWITCH);
                } else {
                    readLegacyStyle(object, preset, "summon", CompanionEffectPurpose.SUMMON);
                    readLegacyStyle(object, preset, "rescue", CompanionEffectPurpose.RESCUE);
                    readLegacyStyle(object, preset, "storage", CompanionEffectPurpose.STORAGE);
                }
                readEnum(object, "category", PackEntityCategoryOverride.class).ifPresent(value -> preset.category = value);
                readEnum(object, "movement", PackEntityMovementOverride.class).ifPresent(value -> preset.movement = value);
                readEnum(object, "bindingRequirement", PackEntityBindingRequirement.class)
                        .ifPresent(value -> preset.bindingRequirement = value);
                readEnum(object, "bindingAnimation", BindingAnimationPolicy.class)
                        .ifPresent(value -> preset.bindingAnimationPolicy = value);
                readEnum(object, "rescueMotion", CompanionRescueMotion.class).ifPresent(value -> preset.rescueMotion = value);
                preset.boundsScale = readScale(object, "boundsScale", preset.boundsScale);
                preset.circleScale = readScale(object, "circleScale", preset.circleScale);
                if (object.has("arrivalSound")) {
                    String sound = object.get("arrivalSound").getAsString().trim().toLowerCase(Locale.ROOT);
                    if (sound.isBlank() || validSoundId(sound)) {
                        preset.arrivalSound = sound;
                    }
                }
                preset.arrivalVolume = readAudioScale(object, "arrivalVolume", preset.arrivalVolume);
                preset.arrivalPitch = readAudioScale(object, "arrivalPitch", preset.arrivalPitch);
                if (object.has("previewNbt")) {
                    normalizePreviewNbt(entityType, object.get("previewNbt").getAsString()).ifPresent(value -> preset.previewNbt = value);
                }
            }
        } catch (RuntimeException | IOException ignored) {
        }
    }

    private static void readLegacyAnimationFile() {
        Path oldPath = FMLPaths.CONFIGDIR.get().resolve("find_me").resolve("animation_presets.json");
        if (oldPath.equals(path()) || !Files.isRegularFile(oldPath)) {
            return;
        }
        readFile(oldPath);
    }

    private static void readStyle(JsonObject object, EnumMap<CompanionEffectPurpose, CompanionEffectStyle> map, String key, CompanionEffectPurpose purpose) {
        readEnum(object, key, CompanionEffectStyle.class).ifPresent(style -> map.put(purpose, style.visualStyle()));
    }

    private static void readAnimation(JsonObject object,
                                      EnumMap<CompanionAnimationPurpose, CompanionAnimationStyle> map,
                                      String key, CompanionAnimationPurpose purpose) {
        readEnum(object, key, CompanionAnimationStyle.class).ifPresent(style -> map.put(purpose, style));
    }

    private static void readLegacyStyle(JsonObject object, Preset preset, String key, CompanionEffectPurpose purpose) {
        readEnum(object, key, CompanionEffectStyle.class).ifPresent(style -> {
            applyLegacyStyle(preset, purpose, style);
            if (!style.isLegacyAnimationValue()) {
                preset.animations.put(animationPurpose(purpose),
                        style == CompanionEffectStyle.NONE || style == CompanionEffectStyle.ENDER
                                || style == CompanionEffectStyle.VELOCITY_BURST
                                ? CompanionAnimationStyle.NONE : CompanionAnimationStyle.STANDARD);
            }
        });
    }

    private static void applyLegacyStyle(Preset preset, CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        if (style == CompanionEffectStyle.GROUND_EMERGE) {
            preset.animations.put(animationPurpose(purpose), CompanionAnimationStyle.GROUND_EMERGE);
            preset.styles.put(purpose, CompanionEffectStyle.MAGIC_CIRCLE);
        } else if (style == CompanionEffectStyle.GROUND_SINK) {
            preset.animations.put(animationPurpose(purpose), CompanionAnimationStyle.GROUND_SINK);
            preset.styles.put(purpose, CompanionEffectStyle.MAGIC_CIRCLE);
        } else {
            preset.styles.put(purpose, style.visualStyle());
        }
    }

    private static CompanionAnimationPurpose animationPurpose(CompanionEffectPurpose purpose) {
        return switch (purpose) {
            case SUMMON -> CompanionAnimationPurpose.SUMMON;
            case RESCUE -> CompanionAnimationPurpose.RESCUE;
            case STORAGE -> CompanionAnimationPurpose.STORAGE;
        };
    }

    private static <T extends Enum<T>> Optional<T> readEnum(JsonObject object, String key, Class<T> type) {
        if (!object.has(key)) {
            return Optional.empty();
        }
        return parseEnum(type, object.get(key).getAsString());
    }

    private static <T extends Enum<T>> Optional<T> parseEnum(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static double readScale(JsonObject object, String key, double fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return clampScale(object.get(key).getAsDouble());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double parseScale(String value, double fallback) {
        try {
            return clampScale(Double.parseDouble(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double readAudioScale(JsonObject object, String key, double fallback) {
        if (!object.has(key)) return fallback;
        try { return clampAudio(object.get(key).getAsDouble()); } catch (RuntimeException ignored) { return fallback; }
    }

    private static double parseAudioScale(String value, double fallback) {
        try { return clampAudio(Double.parseDouble(value)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static double clampScale(double value) {
        return Double.isFinite(value) ? Mth.clamp(value, 0.25, 4.0) : 1.0;
    }

    private static double clampAudio(double value) {
        return Double.isFinite(value) ? Mth.clamp(value, 0.0, 2.0) : 1.0;
    }

    private static boolean validSoundId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }

    private static Optional<String> normalizePreviewNbt(String entityType, String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) return Optional.of("");
        if (raw.length() > 32767) return Optional.empty();
        try {
            CompoundTag tag = TagParser.parseTag(raw);
            String declaredType = tag.getString("id");
            if (!declaredType.isBlank() && !normalize(declaredType).equals(normalize(entityType))) {
                return Optional.empty();
            }
            tag.putString("id", normalize(entityType));
            tag.remove("UUID");
            tag.remove("Pos");
            tag.remove("Motion");
            tag.remove("Rotation");
            tag.remove("Passengers");
            tag.remove("Leash");
            tag.remove("Vehicle");
            return Optional.of(tag.toString());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 7);
        JsonArray entries = new JsonArray();
        PRESETS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Preset preset = entry.getValue();
            JsonObject object = new JsonObject();
            object.addProperty("entity", entry.getKey());
            if (preset.category != PackEntityCategoryOverride.AUTO) object.addProperty("category", preset.category.name().toLowerCase(Locale.ROOT));
            if (preset.movement != PackEntityMovementOverride.AUTO) object.addProperty("movement", preset.movement.name().toLowerCase(Locale.ROOT));
            if (preset.bindingRequirement != PackEntityBindingRequirement.AUTO) {
                object.addProperty("bindingRequirement", preset.bindingRequirement.name().toLowerCase(Locale.ROOT));
            }
            if (preset.bindingAnimationPolicy != BindingAnimationPolicy.INHERIT) {
                object.addProperty("bindingAnimation", preset.bindingAnimationPolicy.name().toLowerCase(Locale.ROOT));
            }
            if (preset.rescueMotion != CompanionRescueMotion.STANDARD) object.addProperty("rescueMotion", preset.rescueMotion.name().toLowerCase(Locale.ROOT));
            if (Math.abs(clampScale(preset.boundsScale) - 1.0) > 0.0001) object.addProperty("boundsScale", clampScale(preset.boundsScale));
            if (Math.abs(clampScale(preset.circleScale) - 1.0) > 0.0001) object.addProperty("circleScale", clampScale(preset.circleScale));
            if (!preset.arrivalSound.isBlank()) object.addProperty("arrivalSound", preset.arrivalSound);
            if (Math.abs(clampAudio(preset.arrivalVolume) - 1.0) > 0.0001) object.addProperty("arrivalVolume", clampAudio(preset.arrivalVolume));
            if (Math.abs(clampAudio(preset.arrivalPitch) - 1.0) > 0.0001) object.addProperty("arrivalPitch", clampAudio(preset.arrivalPitch));
            if (!preset.previewNbt.isBlank()) object.addProperty("previewNbt", preset.previewNbt);
            writeStyle(object, preset.styles, "summonEffect", CompanionEffectPurpose.SUMMON);
            writeStyle(object, preset.styles, "rescueEffect", CompanionEffectPurpose.RESCUE);
            writeStyle(object, preset.styles, "storageEffect", CompanionEffectPurpose.STORAGE);
            writeAnimation(object, preset.animations, "summonAnimation", CompanionAnimationPurpose.SUMMON);
            writeAnimation(object, preset.animations, "rescueAnimation", CompanionAnimationPurpose.RESCUE);
            writeAnimation(object, preset.animations, "storageAnimation", CompanionAnimationPurpose.STORAGE);
            writeAnimation(object, preset.animations, "switchAnimation", CompanionAnimationPurpose.SWITCH);
            entries.add(object);
        });
        root.add("entries", entries);
        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static void writeStyle(JsonObject object, EnumMap<CompanionEffectPurpose, CompanionEffectStyle> map, String key, CompanionEffectPurpose purpose) {
        if (map.containsKey(purpose)) {
            object.addProperty(key, map.get(purpose).name().toLowerCase(Locale.ROOT));
        }
    }

    private static void writeAnimation(JsonObject object,
                                       EnumMap<CompanionAnimationPurpose, CompanionAnimationStyle> map,
                                       String key, CompanionAnimationPurpose purpose) {
        if (map.containsKey(purpose)) {
            object.addProperty(key, map.get(purpose).name().toLowerCase(Locale.ROOT));
        }
    }

    private static Preset preset(String entityType) {
        return PRESETS.computeIfAbsent(normalize(entityType), ignored -> new Preset());
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("find_me").resolve("entity_presets.json");
    }

    private static String normalize(String entityType) {
        return entityType == null ? "" : entityType.toLowerCase(Locale.ROOT).trim();
    }

    private static final class Preset {
        private static final Preset EMPTY = new Preset();
        private final EnumMap<CompanionAnimationPurpose, CompanionAnimationStyle> animations = new EnumMap<>(CompanionAnimationPurpose.class);
        private final EnumMap<CompanionEffectPurpose, CompanionEffectStyle> styles = new EnumMap<>(CompanionEffectPurpose.class);
        private PackEntityCategoryOverride category = PackEntityCategoryOverride.AUTO;
        private PackEntityMovementOverride movement = PackEntityMovementOverride.AUTO;
        private PackEntityBindingRequirement bindingRequirement = PackEntityBindingRequirement.AUTO;
        private BindingAnimationPolicy bindingAnimationPolicy = BindingAnimationPolicy.INHERIT;
        private CompanionRescueMotion rescueMotion = CompanionRescueMotion.STANDARD;
        private double boundsScale = 1.0;
        private double circleScale = 1.0;
        private String arrivalSound = "";
        private double arrivalVolume = 1.0;
        private double arrivalPitch = 1.0;
        private String previewNbt = "";
    }
}
