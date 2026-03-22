package com.kidsurvival;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import static net.minecraft.server.command.CommandManager.literal;

public class KidSurvivalMod implements ModInitializer {
    private static final Gson GSON = new Gson();
    public static final Set<UUID> kidModePlayers = new HashSet<>();
    public static final Set<UUID> hungerModePlayers = new HashSet<>();
    public static final HunterTagGame hunterTagGame = new HunterTagGame();
    private static long tickCounter = 0;

    private static Path getStateFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("kidsurvival.json");
    }

    private static void load(MinecraftServer server) {
        Path file = getStateFile(server);
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj != null) {
                if (obj.has("players")) {
                    JsonArray arr = obj.getAsJsonArray("players");
                    for (JsonElement el : arr) {
                        kidModePlayers.add(UUID.fromString(el.getAsString()));
                    }
                }
                if (obj.has("hungerPlayers")) {
                    JsonArray hungerArr = obj.getAsJsonArray("hungerPlayers");
                    for (JsonElement el : hungerArr) {
                        hungerModePlayers.add(UUID.fromString(el.getAsString()));
                    }
                }
                if (obj.has("hunterTag")) {
                    hunterTagGame.loadFrom(obj.getAsJsonObject("hunterTag"));
                }
            }
        } catch (IOException e) {
            System.err.println("[kidsurvival] Failed to load state: " + e.getMessage());
        }
    }

    private static void save(MinecraftServer server) {
        Path file = getStateFile(server);
        JsonArray arr = new JsonArray();
        for (UUID uuid : kidModePlayers) {
            arr.add(uuid.toString());
        }
        JsonArray hungerArr = new JsonArray();
        for (UUID uuid : hungerModePlayers) {
            hungerArr.add(uuid.toString());
        }
        JsonObject obj = new JsonObject();
        obj.add("players", arr);
        obj.add("hungerPlayers", hungerArr);
        obj.add("hunterTag", hunterTagGame.toJson());
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(obj, writer);
        } catch (IOException e) {
            System.err.println("[kidsurvival] Failed to save state: " + e.getMessage());
        }
    }

    @Override
    public void onInitialize() {
        // Register /kid toggle command and /hunter command
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
                        // Immediately restore health
                        player.setHealth(player.getMaxHealth());

                        context.getSource().sendFeedback(
                            () -> Text.literal("Kid mode enabled"),
                            false
                        );
                    }
                    return 1;
                })
            );

            dispatcher.register(literal("hunter")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    MinecraftServer server = context.getSource().getServer();

                    if (server.getPlayerManager().getPlayerList().size() < 2) {
                        context.getSource().sendFeedback(
                            () -> Text.literal("Need at least 2 players to start Hunter Tag!")
                                    .formatted(Formatting.RED),
                            false
                        );
                        return 0;
                    }

                    if (hunterTagGame.isGameActive()) {
                        hunterTagGame.stopGame(server);
                    }

                    hunterTagGame.startRound(server, player);
                    return 1;
                })
            );

            dispatcher.register(literal("hunger")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    UUID uuid = player.getUuid();

                    if (hungerModePlayers.contains(uuid)) {
                        hungerModePlayers.remove(uuid);
                        context.getSource().sendFeedback(
                            () -> Text.literal("Hunger mode disabled"),
                            false
                        );
                    } else {
                        hungerModePlayers.add(uuid);
                        player.getHungerManager().setFoodLevel(20);
                        player.getHungerManager().setSaturationLevel(20.0f);

                        context.getSource().sendFeedback(
                            () -> Text.literal("Hunger mode enabled (no hunger)"),
                            false
                        );
                    }
                    return 1;
                })
            );
        });

        // Intercept attacks for hunter tag
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hunterTagGame.handleAttack(player, entity)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
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

        // Prevent death for kid-mode players (catches /kill, void, etc.)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (kidModePlayers.contains(player.getUuid())) {
                    player.setHealth(player.getMaxHealth());
                    return false;
                }
            }
            return true;
        });

        // Tick handler for kid mode and hunter tag
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (kidModePlayers.contains(player.getUuid())) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                }
                if (hungerModePlayers.contains(player.getUuid())) {
                    player.getHungerManager().setFoodLevel(20);
                    player.getHungerManager().setSaturationLevel(20.0f);
                }
            }
            // Action bar indicator every 20 ticks
            if (tickCounter % 20 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (hunterTagGame.isPlayerInGame(player.getUuid())) continue;
                    boolean kid = kidModePlayers.contains(player.getUuid());
                    boolean hunger = hungerModePlayers.contains(player.getUuid());
                    if (kid && hunger) {
                        player.sendMessage(
                            Text.literal("Kid Mode + No Hunger").formatted(Formatting.GREEN),
                            true);
                    } else if (kid) {
                        player.sendMessage(
                            Text.literal("Kid Mode Active").formatted(Formatting.GREEN),
                            true);
                    } else if (hunger) {
                        player.sendMessage(
                            Text.literal("No Hunger").formatted(Formatting.GREEN),
                            true);
                    }
                }
            }
            hunterTagGame.onTick(server);
        });

        // Player join/disconnect for hunter tag
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            hunterTagGame.onPlayerJoin(handler.player, server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            hunterTagGame.onPlayerDisconnect(handler.player, server);
        });

        // Load state when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server));

        // Save state when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save(server));
    }
}
