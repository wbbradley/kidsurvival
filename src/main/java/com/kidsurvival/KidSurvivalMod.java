package com.kidsurvival;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class KidSurvivalMod implements ModInitializer {
    public static final Set<UUID> kidModePlayers = new HashSet<>();

    @Override
    public void onInitialize() {
        // Register /kid toggle command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("kid")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    UUID uuid = player.getUuid();

                    if (kidModePlayers.contains(uuid)) {
                        kidModePlayers.remove(uuid);
                        context.getSource().sendFeedback(
                            () -> Text.literal("Kid mode disabled"),
                            false
                        );
                    } else {
                        kidModePlayers.add(uuid);
                        // Immediately restore health, food, and saturation
                        player.setHealth(player.getMaxHealth());
                        player.getHungerManager().setFoodLevel(20);
                        player.getHungerManager().setSaturationLevel(20.0f);
                        player.getHungerManager().setExhaustion(0.0f);
                        context.getSource().sendFeedback(
                            () -> Text.literal("Kid mode enabled"),
                            false
                        );
                    }
                    return 1;
                })
            );
        });

        // Cancel all damage for kid-mode players
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof PlayerEntity player) {
                if (kidModePlayers.contains(player.getUuid())) {
                    return false;
                }
            }
            return true;
        });

        // Safety net: restore health each tick for kid-mode players
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (kidModePlayers.contains(player.getUuid())) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                }
            }
        });
    }
}
