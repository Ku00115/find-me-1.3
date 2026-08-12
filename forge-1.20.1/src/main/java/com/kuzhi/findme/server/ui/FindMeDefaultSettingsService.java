package com.kuzhi.findme.server.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.FindMeUiSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

/** Global defaults for new player records. Existing players retain their own settings. */
public final class FindMeDefaultSettingsService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path UI_DEFAULTS = FMLPaths.CONFIGDIR.get().resolve("find_me/ui_defaults.json");

    private FindMeDefaultSettingsService() {
    }

    public static FindMeUiSettings loadUiDefaults() {
        if (!Files.isRegularFile(UI_DEFAULTS)) return FindMeUiSettings.defaults();
        try {
            JsonObject json = GSON.fromJson(Files.readString(UI_DEFAULTS, StandardCharsets.UTF_8), JsonObject.class);
            if (json == null) return FindMeUiSettings.defaults();
            CompoundTag tag = new CompoundTag();
            copyBoolean(json, tag, "showCustomNames");
            copyBoolean(json, tag, "showOriginalNames");
            copyBoolean(json, tag, "showHealth");
            copyBoolean(json, tag, "rotateModels");
            copyBoolean(json, tag, "reduceBackgroundAnimation");
            copyInt(json, tag, "dragHoldMillis");
            copyBoolean(json, tag, "operationSounds");
            copyBoolean(json, tag, "controlHints");
            copyBoolean(json, tag, "autoJoinTeams");
            copyBoolean(json, tag, "autoCreateTeams");
            copyInt(json, tag, "defaultTeamIndex");
            copyBoolean(json, tag, "allowNameColors");
            copyInt(json, tag, "nameMaxLength");
            copyString(json, tag, "wheelStyle");
            copyString(json, tag, "textMode");
            copyBoolean(json, tag, "uiAnimations");
            copyString(json, tag, "fontFamily");
            copyString(json, tag, "fontSize");
            copyString(json, tag, "ridingCameraMode");
            copyString(json, tag, "bindingAnimationPolicy");
            copyBoolean(json, tag, "preferNativeMountInteraction");
            copyBoolean(json, tag, "autoPromoteRiddenCompanions");
            copyBoolean(json, tag, "mountSummonAnimations");
            copyBoolean(json, tag, "companionSummonAnimations");
            copyBoolean(json, tag, "hideRiddenMountWhenLookingDown");
            copyBoolean(json, tag, "friendlyFireProtection");
            copyBoolean(json, tag, "autoStoreOnBinding");
            copyBoolean(json, tag, "autoOrganizeTeams");
            return FindMeUiSettings.load(tag);
        } catch (RuntimeException | IOException exception) {
            FindMeMod.LOGGER.warn("Could not read FindMe UI defaults from {}", UI_DEFAULTS, exception);
            return FindMeUiSettings.defaults();
        }
    }

    public static void export(MinecraftServer server, FindMeUiSettings settings) throws IOException {
        Files.createDirectories(UI_DEFAULTS.getParent());
        writeAtomic(UI_DEFAULTS, GSON.toJson(toJson(settings.normalized())));

        Path source = server.getWorldPath(LevelResource.ROOT).normalize()
                .resolve("serverconfig").resolve("find_me-server.toml");
        if (!Files.isRegularFile(source)) {
            source = FMLPaths.CONFIGDIR.get().resolve("find_me-server.toml");
        }
        Path destination = FMLPaths.GAMEDIR.get().resolve("defaultconfigs/find_me-server.toml");
        if (!Files.isRegularFile(source)) {
            throw new IOException("Current world server config was not found: " + source);
        }
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        moveAtomic(temporary, destination);
    }

    private static JsonObject toJson(FindMeUiSettings value) {
        JsonObject json = new JsonObject();
        json.addProperty("showCustomNames", value.showCustomNames());
        json.addProperty("showOriginalNames", value.showOriginalNames());
        json.addProperty("showHealth", value.showHealth());
        json.addProperty("rotateModels", value.rotateModels());
        json.addProperty("reduceBackgroundAnimation", value.reduceBackgroundAnimation());
        json.addProperty("dragHoldMillis", value.dragHoldMillis());
        json.addProperty("operationSounds", value.operationSounds());
        json.addProperty("controlHints", value.controlHints());
        json.addProperty("autoJoinTeams", value.autoJoinTeams());
        json.addProperty("autoCreateTeams", value.autoCreateTeams());
        json.addProperty("defaultTeamIndex", value.defaultTeamIndex());
        json.addProperty("allowNameColors", value.allowNameColors());
        json.addProperty("nameMaxLength", value.nameMaxLength());
        json.addProperty("wheelStyle", value.wheelStyle().name());
        json.addProperty("textMode", value.textMode().name());
        json.addProperty("uiAnimations", value.uiAnimations());
        json.addProperty("fontFamily", value.fontFamily().name());
        json.addProperty("fontSize", value.fontSize().name());
        json.addProperty("ridingCameraMode", value.ridingCameraMode().name());
        json.addProperty("bindingAnimationPolicy", value.bindingAnimationPolicy().name());
        json.addProperty("preferNativeMountInteraction", value.preferNativeMountInteraction());
        json.addProperty("autoPromoteRiddenCompanions", value.autoPromoteRiddenCompanions());
        json.addProperty("mountSummonAnimations", value.mountSummonAnimations());
        json.addProperty("companionSummonAnimations", value.companionSummonAnimations());
        json.addProperty("hideRiddenMountWhenLookingDown", value.hideRiddenMountWhenLookingDown());
        json.addProperty("friendlyFireProtection", value.friendlyFireProtection());
        json.addProperty("autoStoreOnBinding", value.autoStoreOnBinding());
        json.addProperty("autoOrganizeTeams", value.autoOrganizeTeams());
        return json;
    }

    private static void writeAtomic(Path destination, String text) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        moveAtomic(temporary, destination);
    }

    private static void moveAtomic(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyBoolean(JsonObject json, CompoundTag tag, String key) { if (json.has(key)) tag.putBoolean(key, json.get(key).getAsBoolean()); }
    private static void copyInt(JsonObject json, CompoundTag tag, String key) { if (json.has(key)) tag.putInt(key, json.get(key).getAsInt()); }
    private static void copyString(JsonObject json, CompoundTag tag, String key) { if (json.has(key)) tag.putString(key, json.get(key).getAsString()); }
}
