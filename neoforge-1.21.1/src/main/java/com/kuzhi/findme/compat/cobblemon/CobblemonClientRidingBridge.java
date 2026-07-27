package com.kuzhi.findme.compat.cobblemon;

import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.ActiveRidingContext;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.server.pokemon.update.ServerboundUpdateRidingStatePacket;
import com.cobblemon.mod.common.net.messages.server.riding.ServerboundUpdateDriverInputPacket;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientExternalRideHandoffState;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Keeps Cobblemon's local riding controller in step when FindMe swaps the
 * server-side PokemonEntity underneath a player who is already riding.
 *
 * This is deliberately not keyboard simulation. It reapplies the real input
 * currently held by the local player after the local controller is replaced.
 */
public final class CobblemonClientRidingBridge {
    private static final int DETACHED_HANDOFF_GRACE_TICKS = 12;
    private static PokemonEntity previousVehicle;
    private static RidingStyle previousStyle;
    private static RidingStyle requestedStyle;
    private static Vec3 previousRideVelocity = Vec3.ZERO;
    private static DetachedRideState detachedRideState;
    private static PokemonEntity diagnosticVehicle;
    private static int diagnosticTick = -1;
    private static int switchSequence;

    private CobblemonClientRidingBridge() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            reset();
            return;
        }

        PokemonEntity current = player.getVehicle() instanceof PokemonEntity pokemon ? pokemon : null;
        ClientExternalRideHandoffState.Snapshot externalHandoff = current == null
                ? null
                : ClientExternalRideHandoffState.consume(current.getPokemon().getUuid());
        if (current != previousVehicle) {
            if (current != null) {
                switchSequence++;
                PokemonEntity source = previousVehicle;
                RidingStyle sourceStyle = previousStyle;
                Vec3 sourceVelocity = previousRideVelocity;
                float sourceYRot = source == null ? current.getYRot() : source.getYRot();
                float sourceXRot = source == null ? current.getXRot() : source.getXRot();
                RidingStyle explicitTargetStyle = null;
                String sourcePath = "direct";
                if (externalHandoff != null) {
                    source = null;
                    sourceStyle = ridingStyle(externalHandoff.sourceMoveType());
                    sourceVelocity = externalHandoff.velocity();
                    sourceYRot = externalHandoff.yRot();
                    sourceXRot = externalHandoff.xRot();
                    explicitTargetStyle = ridingStyle(externalHandoff.targetMoveType());
                    sourcePath = "external";
                } else if (source == null && detachedRideState != null) {
                    source = detachedRideState.vehicle();
                    sourceStyle = detachedRideState.style();
                    sourceVelocity = detachedRideState.velocity();
                    sourceYRot = detachedRideState.yRot();
                    sourceXRot = detachedRideState.xRot();
                    sourcePath = "detached";
                }
                boolean hasSource = sourceStyle != null
                        && (source != null || externalHandoff != null || detachedRideState != null);
                requestedStyle = explicitTargetStyle != null
                        ? explicitTargetStyle
                        : preferredStyle(player, current);
                if (hasSource) {
                    detachedRideState = new DetachedRideState(
                            source, sourceStyle, sourceVelocity, sourceYRot, sourceXRot, 0);
                    if (handoff(player, source, current, sourceStyle, sourceVelocity, requestedStyle, sourcePath,
                            sourceYRot, sourceXRot)) {
                        requestedStyle = null;
                        detachedRideState = null;
                    }
                } else {
                    detachedRideState = null;
                }
                diagnosticVehicle = current;
                diagnosticTick = 0;
            } else {
                if (previousVehicle != null) {
                    detachedRideState = new DetachedRideState(
                            previousVehicle, previousStyle, previousRideVelocity,
                            previousVehicle.getYRot(), previousVehicle.getXRot(), 0);
                    log("detached-source entity={} style={} velocity={}", describe(previousVehicle),
                            previousStyle, previousRideVelocity);
                }
                diagnosticVehicle = null;
                diagnosticTick = -1;
            }
            previousVehicle = current;
        } else if (current != null && externalHandoff != null) {
            switchSequence++;
            RidingStyle sourceStyle = ridingStyle(externalHandoff.sourceMoveType());
            requestedStyle = ridingStyle(externalHandoff.targetMoveType());
            if (requestedStyle == null) {
                requestedStyle = preferredStyle(player, current);
            }
            detachedRideState = new DetachedRideState(null, sourceStyle, externalHandoff.velocity(),
                    externalHandoff.yRot(), externalHandoff.xRot(), 0);
            if (handoff(player, null, current, sourceStyle, externalHandoff.velocity(), requestedStyle, "external-late",
                    externalHandoff.yRot(), externalHandoff.xRot())) {
                requestedStyle = null;
                detachedRideState = null;
            }
            diagnosticVehicle = current;
            diagnosticTick = 0;
        }

        if (current != null) {
            if (requestedStyle != null || current.getRidingController().getContext() == null) {
                boolean initialized;
                if (detachedRideState != null && detachedRideState.style() != null) {
                    initialized = handoff(player, detachedRideState.vehicle(), current,
                            detachedRideState.style(), detachedRideState.velocity(), requestedStyle, "retry",
                            detachedRideState.yRot(), detachedRideState.xRot());
                } else {
                    initialized = ensureBehaviour(player, current, requestedStyle, Vec3.ZERO);
                }
                if (initialized || diagnosticTick < 0) {
                    requestedStyle = null;
                    detachedRideState = null;
                }
            }
            captureCurrentState(current);
        } else if (detachedRideState != null) {
            detachedRideState = detachedRideState.nextTick();
            if (detachedRideState.age() > DETACHED_HANDOFF_GRACE_TICKS) {
                log("detached-source expired entity={} style={} velocity={}",
                        describe(detachedRideState.vehicle()), detachedRideState.style(), detachedRideState.velocity());
                detachedRideState = null;
            }
        }
        tickDiagnostics(player, current);
    }

    private static boolean handoff(LocalPlayer player, PokemonEntity oldPokemon, PokemonEntity newPokemon,
                                   RidingStyle sourceStyle, Vec3 velocity, RidingStyle targetStyle, String sourcePath,
                                   float sourceYRot, float sourceXRot) {
        boolean inheritedVelocity = sourceStyle != null && sourceStyle == targetStyle;
        Vec3 handoffVelocity = inheritedVelocity ? boundedVelocity(targetStyle, velocity) : Vec3.ZERO;
        if (!ensureBehaviour(player, newPokemon, targetStyle, handoffVelocity)) {
            return false;
        }
        if (inheritedVelocity) {
            newPokemon.setYRot(sourceYRot);
            newPokemon.setXRot(sourceXRot);
            newPokemon.yBodyRot = sourceYRot;
            newPokemon.yHeadRot = sourceYRot;
        }
        stabilizeVisualHandoff(player, newPokemon);
        sendRealInput(player);
        ActiveRidingContext context = newPokemon.getRidingController().getContext();
        log("switch={} sourcePath={} old={} oldStyle={} new={} requestedStyle={} activeStyle={} inheritedVelocity={} newDelta={} input={}",
                switchSequence, sourcePath, describe(oldPokemon), sourceStyle, describe(newPokemon), targetStyle,
                context == null ? "null" : context.getStyle(), inheritedVelocity,
                newPokemon.getDeltaMovement(), input(player));
        return true;
    }

    private static void stabilizeVisualHandoff(LocalPlayer player, PokemonEntity newPokemon) {
        player.xo = player.getX();
        player.yo = player.getY();
        player.zo = player.getZ();
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        newPokemon.xo = newPokemon.getX();
        newPokemon.yo = newPokemon.getY();
        newPokemon.zo = newPokemon.getZ();
        newPokemon.yRotO = newPokemon.getYRot();
        newPokemon.xRotO = newPokemon.getXRot();
    }

    private static void captureCurrentState(PokemonEntity pokemon) {
        ActiveRidingContext context = pokemon.getRidingController().getContext();
        if (context != null) {
            previousStyle = context.getStyle();
        } else if (requestedStyle != null) {
            previousStyle = requestedStyle;
        } else {
            previousStyle = inferStyle(pokemon);
        }
        if (context != null && context.getState().getRideVelocity().get() != null) {
            previousRideVelocity = context.getState().getRideVelocity().get();
        } else {
            previousRideVelocity = pokemon.getDeltaMovement();
        }
    }

    private static boolean ensureBehaviour(LocalPlayer player, PokemonEntity pokemon, RidingStyle requested, Vec3 inheritedVelocity) {
        Map<RidingStyle, RidingBehaviourSettings> behaviours = pokemon.getRidingController().getBehaviours();
        if (behaviours == null || behaviours.isEmpty()) {
            return false;
        }
        RidingStyle style = requested != null && behaviours.containsKey(requested) ? requested : inferStyle(player, pokemon, behaviours);
        RidingBehaviourSettings settings = behaviours.get(style);
        if (settings == null) {
            return false;
        }
        pokemon.getRidingController().changeBehaviour(settings.getKey());
        ActiveRidingContext context = pokemon.getRidingController().getContext();
        if (context == null || context.getStyle() != style) {
            return false;
        }
        Vec3 boundedVelocity = boundedVelocity(style, inheritedVelocity);
        if (boundedVelocity.lengthSqr() > 1.0E-7) {
            context.getState().getRideVelocity().set(boundedVelocity, true);
            pokemon.setDeltaMovement(boundedVelocity);
            pokemon.hurtMarked = true;
        }
        new ServerboundUpdateRidingStatePacket(pokemon.getId(), settings.getKey(), context.getState(), null).sendToServer();
        sendRealInput(player);
        log("controller-init entity={} requestedStyle={} activeStyle={} behaviours={} inheritedVelocity={}",
                describe(pokemon), requested, style, behaviours.keySet(), boundedVelocity);
        return true;
    }

    private static RidingStyle preferredStyle(LocalPlayer player, PokemonEntity pokemon) {
        Map<RidingStyle, RidingBehaviourSettings> behaviours = pokemon.getRidingController().getBehaviours();
        if (behaviours == null || behaviours.isEmpty()) {
            return null;
        }
        return inferStyle(player, pokemon, behaviours);
    }

    private static RidingStyle ridingStyle(CompanionMoveType moveType) {
        if (moveType == null) {
            return null;
        }
        return switch (moveType) {
            case FLY -> RidingStyle.AIR;
            case SWIM -> RidingStyle.LIQUID;
            case WALK -> RidingStyle.LAND;
            case COMPANION -> null;
        };
    }

    private static Vec3 boundedVelocity(RidingStyle style, Vec3 velocity) {
        if (style == null || velocity == null) {
            return Vec3.ZERO;
        }
        Vec3 adjusted = style == RidingStyle.LAND
                ? new Vec3(velocity.x, 0.0, velocity.z)
                : velocity;
        double maximum = switch (style) {
            case LAND -> 1.5;
            case LIQUID -> 2.0;
            case AIR -> 4.0;
        };
        double lengthSqr = adjusted.lengthSqr();
        return lengthSqr > maximum * maximum
                ? adjusted.scale(maximum / Math.sqrt(lengthSqr))
                : adjusted;
    }

    private static RidingStyle inferStyle(PokemonEntity pokemon) {
        Map<RidingStyle, RidingBehaviourSettings> behaviours = pokemon.getRidingController().getBehaviours();
        return inferStyle(null, pokemon, behaviours);
    }

    private static RidingStyle inferStyle(LocalPlayer player, PokemonEntity pokemon,
                                          Map<RidingStyle, RidingBehaviourSettings> behaviours) {
        if (behaviours == null || behaviours.isEmpty()) {
            return null;
        }
        if (player != null && (player.isInWater() || pokemon.isInWater()) && behaviours.containsKey(RidingStyle.LIQUID)) {
            return RidingStyle.LIQUID;
        }
        if (behaviours.containsKey(RidingStyle.AIR)) {
            return RidingStyle.AIR;
        }
        if (behaviours.containsKey(RidingStyle.LAND)) {
            return RidingStyle.LAND;
        }
        if (behaviours.containsKey(RidingStyle.LIQUID)) {
            return RidingStyle.LIQUID;
        }
        return behaviours.keySet().iterator().next();
    }

    private static void sendRealInput(LocalPlayer player) {
        float vertical = player.input.jumping ? 1.0f : player.input.shiftKeyDown ? -1.0f : 0.0f;
        new ServerboundUpdateDriverInputPacket(new Vector3f(player.xxa, vertical, player.zza)).sendToServer();
    }

    private static void tickDiagnostics(LocalPlayer player, PokemonEntity current) {
        if (diagnosticVehicle == null || current != diagnosticVehicle || diagnosticTick < 0) {
            return;
        }
        if (diagnosticTick <= 2) {
            sendRealInput(player);
        }
        if (diagnosticTick == 0 || diagnosticTick == 1 || diagnosticTick == 2 || diagnosticTick == 5
                || diagnosticTick == 10 || diagnosticTick == 20 || diagnosticTick == 40) {
            ActiveRidingContext context = current.getRidingController().getContext();
            log("switch={} tick={} entity={} behaviour={} style={} rideVelocity={} delta={} input={}",
                    switchSequence, diagnosticTick, describe(current),
                    context == null ? "null" : context.getBehaviour(),
                    context == null ? "null" : context.getStyle(),
                    context == null ? Vec3.ZERO : context.getState().getRideVelocity().get(),
                    current.getDeltaMovement(), input(player));
        }
        if (diagnosticTick >= 40) {
            diagnosticVehicle = null;
            diagnosticTick = -1;
        } else {
            diagnosticTick++;
        }
    }

    private static String input(LocalPlayer player) {
        return "x=" + player.xxa + ",z=" + player.zza + ",jump=" + player.input.jumping
                + ",shift=" + player.input.shiftKeyDown;
    }

    private static String describe(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemon)) {
            return "null";
        }
        return pokemon.getId() + "/" + pokemon.getPokemon().getUuid() + "/" + pokemon.getPokemon().getSpecies().getResourceIdentifier();
    }

    private static void log(String message, Object... args) {
        FindMeMod.LOGGER.info("[FindMe cobblemon-client] " + message, args);
    }

    private static void reset() {
        previousVehicle = null;
        previousStyle = null;
        requestedStyle = null;
        previousRideVelocity = Vec3.ZERO;
        detachedRideState = null;
        diagnosticVehicle = null;
        diagnosticTick = -1;
    }

    private record DetachedRideState(PokemonEntity vehicle, RidingStyle style, Vec3 velocity,
                                     float yRot, float xRot, int age) {
        private DetachedRideState nextTick() {
            return new DetachedRideState(this.vehicle, this.style, this.velocity,
                    this.yRot, this.xRot, this.age + 1);
        }
    }
}
