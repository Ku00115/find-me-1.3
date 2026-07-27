package com.kuzhi.findme.compat.cobblemon;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class CobblemonPreviewFactory {
    private CobblemonPreviewFactory() {
    }

    public static Entity create(Level level, CompoundTag entityTag, UUID expectedPokemonId) {
        if (level == null || entityTag == null || !entityTag.contains("Pokemon")) {
            return null;
        }
        Pokemon pokemon = new Pokemon().loadFromNBT(level.registryAccess(), entityTag.getCompound("Pokemon").copy());
        if (expectedPokemonId != null && !expectedPokemonId.equals(pokemon.getUuid())) {
            FindMeMod.LOGGER.warn("FindMe refused mismatched Cobblemon preview: expected={}, decoded={}, species={}",
                    expectedPokemonId, pokemon.getUuid(), pokemon.getSpecies().getResourceIdentifier());
            return null;
        }
        PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
        entity.moveTo(0.0, 0.0, 0.0, 0.0f, 0.0f);
        FindMeDebugLogger.info("preview", "FindMe created Cobblemon preview: pokemon={}, species={}",
                pokemon.getUuid(), pokemon.getSpecies().getResourceIdentifier());
        return entity;
    }

    public static boolean isPokemonPreview(Entity entity) {
        return entity instanceof PokemonEntity;
    }

    /** Advances only Cobblemon's local render delegate; the preview is never added to a level. */
    public static void tickPreview(Entity entity, float partialTicks, boolean advanceAge) {
        if (!(entity instanceof PokemonEntity pokemonEntity)
                || !(pokemonEntity.getDelegate() instanceof PokemonClientDelegate delegate)) {
            return;
        }
        if (advanceAge) {
            delegate.tick(pokemonEntity);
        }
        delegate.updatePartialTicks(partialTicks);
    }
}
