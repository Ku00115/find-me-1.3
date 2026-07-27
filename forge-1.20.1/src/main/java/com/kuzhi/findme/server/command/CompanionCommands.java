package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.OpenFindMeManageScreenPacket;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.profile.CompanionBindingProfileService;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/** Minimal command surface for reload, UI entry points, and administrator testing. */
public final class CompanionCommands {
    private CompanionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("findme");
        root.then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> PackAnimationPresetService.reload(ctx.getSource())));
        root.then(Commands.literal("editor")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> openEditor(player(ctx.getSource()))));
        root.then(Commands.literal("manage")
                .executes(ctx -> openManage(player(ctx.getSource()))));
        root.then(Commands.literal("tame")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> forceTameLookedAt(player(ctx.getSource())))
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(ctx -> forceTame(player(ctx.getSource()), EntityArgument.getEntity(ctx, "target")))));
        dispatcher.register(root);
    }

    private static int openEditor(ServerPlayer player) {
        if (!FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return 0;
        }
        PackPresetCommandHandler.openEditor(player);
        return 1;
    }

    private static int openManage(ServerPlayer player) {
        if (!FindMeModuleService.enabled(FindMeModule.MANAGEMENT) && !player.hasPermissions(2)) {
            FindMeModuleService.require(player, FindMeModule.MANAGEMENT);
            return 0;
        }
        FindMeModuleService.sync(player);
        ModNetwork.sendToPlayer(player, new OpenFindMeManageScreenPacket());
        return 1;
    }

    private static int forceTame(ServerPlayer player, Entity target) {
        LivingEntity living = livingTarget(target);
        if (living == null || living instanceof Player
                || !CompanionBindingProfileService.forceTame(player, living)) {
            CompanionMessageService.tell(player, "message.find_me.force_tame_invalid",
                    net.minecraft.ChatFormatting.RED);
            return 0;
        }
        CompanionMessageService.tell(player, "message.find_me.force_tame_success",
                net.minecraft.ChatFormatting.GREEN, living.getDisplayName());
        return 1;
    }

    private static int forceTameLookedAt(ServerPlayer player) {
        LivingEntity target = lookedAtLiving(player, 24.0);
        if (target == null) {
            CompanionMessageService.tell(player, "message.find_me.force_tame_no_target",
                    net.minecraft.ChatFormatting.RED);
            return 0;
        }
        return forceTame(player, target);
    }

    private static LivingEntity lookedAtLiving(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(range));
        HitResult blockHit = player.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double nearestDistance = blockHit.getType() == HitResult.Type.MISS ? range * range
                : eye.distanceToSqr(blockHit.getLocation());
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        LivingEntity nearest = null;
        for (Entity candidate : player.serverLevel().getEntities(player, search,
                entity -> entity.isAlive() && entity.isPickable())) {
            LivingEntity living = livingTarget(candidate);
            if (living == null || living instanceof Player) continue;
            AABB hitBox = candidate.getBoundingBox().inflate(Math.max(0.3, candidate.getPickRadius()));
            Optional<Vec3> hit = hitBox.clip(eye, end);
            if (hit.isEmpty()) continue;
            double distance = eye.distanceToSqr(hit.get());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = living;
            }
        }
        return nearest;
    }

    private static LivingEntity livingTarget(Entity target) {
        if (target instanceof LivingEntity living) return living;
        if (target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity living) return living;
        return null;
    }

    private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }
}
