package com.kuzhi.findme.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Durable, mod-neutral spell slot payload for one FindMe companion.
 * Optional addons own spell interpretation; FindMe owns persistence and item return.
 */
public record CompanionSpellBinding(ResourceLocation providerId, ResourceLocation spellId,
                                    ResourceLocation iconResource, CompanionSpellRole role,
                                    String spellName, int spellLevel, CompoundTag itemTag) {
    private static final ResourceLocation UNKNOWN_PROVIDER = ResourceLocation.fromNamespaceAndPath("find_me", "unknown");
    private static final ResourceLocation UNKNOWN_SPELL = ResourceLocation.fromNamespaceAndPath("find_me", "unknown_spell");
    private static final ResourceLocation UNKNOWN_ICON = ResourceLocation.fromNamespaceAndPath(
            "find_me", "textures/particle/contract_glyph.png");

    public CompanionSpellBinding {
        providerId = providerId == null ? UNKNOWN_PROVIDER : providerId;
        spellId = spellId == null ? UNKNOWN_SPELL : spellId;
        iconResource = iconResource == null ? UNKNOWN_ICON : iconResource;
        role = role == null ? CompanionSpellRole.UTILITY : role;
        spellName = spellName == null ? "" : spellName;
        spellLevel = Math.max(0, spellLevel);
        itemTag = itemTag == null ? new CompoundTag() : itemTag.copy();
        if (!itemTag.isEmpty()) {
            itemTag.putByte("Count", (byte)1);
            itemTag.putInt("count", 1);
        }
    }

    @Override
    public CompoundTag itemTag() {
        return this.itemTag.copy();
    }

    public String displayName() {
        return this.spellName.isBlank() ? this.spellId.toString() : this.spellName;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("providerId", this.providerId.toString());
        tag.putString("spellId", this.spellId.toString());
        tag.putString("icon", this.iconResource.toString());
        tag.putString("role", this.role.name());
        tag.putString("spellName", this.spellName);
        tag.putInt("spellLevel", this.spellLevel);
        tag.put("item", this.itemTag.copy());
        return tag;
    }

    public static CompanionSpellBinding load(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        ResourceLocation providerId = parse(tag.getString("providerId"), UNKNOWN_PROVIDER);
        ResourceLocation spellId = parse(tag.getString("spellId"), UNKNOWN_SPELL);
        ResourceLocation icon = parse(tag.getString("icon"), UNKNOWN_ICON);
        CompanionSpellRole role;
        try {
            role = CompanionSpellRole.valueOf(tag.getString("role"));
        } catch (IllegalArgumentException exception) {
            role = CompanionSpellRole.UTILITY;
        }
        return new CompanionSpellBinding(providerId, spellId, icon, role, tag.getString("spellName"),
                tag.getInt("spellLevel"), tag.getCompound("item"));
    }

    private static ResourceLocation parse(String value, ResourceLocation fallback) {
        ResourceLocation parsed = ResourceLocation.tryParse(value == null ? "" : value);
        return parsed == null ? fallback : parsed;
    }
}
