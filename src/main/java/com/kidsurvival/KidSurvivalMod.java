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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import static net.minecraft.commands.Commands.literal;

public class KidSurvivalMod implements ModInitializer {
    private static final Gson GSON = new Gson();
    public static final Set<UUID> kidModePlayers = new HashSet<>();
    public static final Set<UUID> hungerModePlayers = new HashSet<>();
    public static final Set<UUID> benchedPlayers = new HashSet<>();
    public static final HunterTagGame hunterTagGame = new HunterTagGame();
    private static long tickCounter = 0;

    private static Path getStateFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("kidsurvival.json");
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
                if (obj.has("benchedPlayers")) {
                    JsonArray benchArr = obj.getAsJsonArray("benchedPlayers");
                    for (JsonElement el : benchArr) {
                        benchedPlayers.add(UUID.fromString(el.getAsString()));
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
        JsonArray benchArr = new JsonArray();
        for (UUID uuid : benchedPlayers) {
            benchArr.add(uuid.toString());
        }
        JsonObject obj = new JsonObject();
        obj.add("players", arr);
        obj.add("hungerPlayers", hungerArr);
        obj.add("benchedPlayers", benchArr);
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
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    UUID uuid = player.getUUID();

                    if (kidModePlayers.contains(uuid)) {
                        kidModePlayers.remove(uuid);
                        context.getSource().sendSuccess(
                            () -> Component.literal("Kid mode disabled"),
                            false
                        );
                    } else {
                        kidModePlayers.add(uuid);
                        // Immediately restore health
                        player.setHealth(player.getMaxHealth());

                        context.getSource().sendSuccess(
                            () -> Component.literal("Kid mode enabled"),
                            false
                        );
                    }
                    return 1;
                })
            );

            dispatcher.register(literal("hunter")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MinecraftServer server = context.getSource().getServer();

                    long eligible = server.getPlayerList().getPlayers().stream()
                            .filter(p -> !benchedPlayers.contains(p.getUUID())).count();
                    if (eligible < 2) {
                        context.getSource().sendSuccess(
                            () -> Component.literal("Need at least 2 non-benched players to start Hunter Tag!")
                                    .withStyle(ChatFormatting.RED),
                            false
                        );
                        return 0;
                    }

                    if (hunterTagGame.isGameActive()) {
                        hunterTagGame.stopGame(server);
                    }

                    hunterTagGame.startRound(server, player, benchedPlayers);
                    return 1;
                })
            );

            dispatcher.register(literal("hunger")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    UUID uuid = player.getUUID();

                    if (hungerModePlayers.contains(uuid)) {
                        hungerModePlayers.remove(uuid);
                        context.getSource().sendSuccess(
                            () -> Component.literal("Hunger mode disabled"),
                            false
                        );
                    } else {
                        hungerModePlayers.add(uuid);
                        player.getFoodData().setFoodLevel(20);
                        player.getFoodData().setSaturation(20.0f);

                        context.getSource().sendSuccess(
                            () -> Component.literal("Hunger mode enabled (no hunger)"),
                            false
                        );
                    }
                    return 1;
                })
            );

            dispatcher.register(literal("bench")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    UUID uuid = player.getUUID();

                    if (benchedPlayers.contains(uuid)) {
                        benchedPlayers.remove(uuid);
                        context.getSource().sendSuccess(
                            () -> Component.literal("You are no longer benched"),
                            false
                        );
                    } else {
                        benchedPlayers.add(uuid);
                        MinecraftServer server = context.getSource().getServer();
                        hunterTagGame.removePlayer(uuid, server);
                        context.getSource().sendSuccess(
                            () -> Component.literal("You are now benched (opted out of Hunter Tag)"),
                            false
                        );
                    }
                    return 1;
                })
            );
        });

        // Intercept attacks for hunter tag
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (hunterTagGame.handleAttack(player, entity)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Cancel all damage for kid-mode players
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof Player player) {
                if (kidModePlayers.contains(player.getUUID())) {
                    return false;
                }
            }
            return true;
        });

        // Prevent death for kid-mode players (catches /kill, void, etc.)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer player) {
                if (kidModePlayers.contains(player.getUUID())) {
                    player.setHealth(player.getMaxHealth());
                    return false;
                }
            }
            return true;
        });

        // Tick handler for kid mode and hunter tag
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (kidModePlayers.contains(player.getUUID())) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                }
                if (hungerModePlayers.contains(player.getUUID())) {
                    player.getFoodData().setFoodLevel(20);
                    player.getFoodData().setSaturation(20.0f);
                }
            }
            // Action bar indicator every 20 ticks
            if (tickCounter % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (hunterTagGame.isPlayerInGame(player.getUUID())) continue;
                    boolean kid = kidModePlayers.contains(player.getUUID());
                    boolean hunger = hungerModePlayers.contains(player.getUUID());
                    if (kid && hunger) {
                        player.sendSystemMessage(
                            Component.literal("Kid Mode + No Hunger").withStyle(ChatFormatting.GREEN),
                            true);
                    } else if (kid) {
                        player.sendSystemMessage(
                            Component.literal("Kid Mode Active").withStyle(ChatFormatting.GREEN),
                            true);
                    } else if (hunger) {
                        player.sendSystemMessage(
                            Component.literal("No Hunger").withStyle(ChatFormatting.GREEN),
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
