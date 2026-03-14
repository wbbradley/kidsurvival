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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import static net.minecraft.server.command.CommandManager.literal;

public class KidSurvivalMod implements ModInitializer {
    private static final Gson GSON = new Gson();
    public static final Set<UUID> kidModePlayers = new HashSet<>();

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
            if (obj != null && obj.has("players")) {
                JsonArray arr = obj.getAsJsonArray("players");
                for (JsonElement el : arr) {
                    kidModePlayers.add(UUID.fromString(el.getAsString()));
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
        JsonObject obj = new JsonObject();
        obj.add("players", arr);
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(obj, writer);
        } catch (IOException e) {
            System.err.println("[kidsurvival] Failed to save state: " + e.getMessage());
        }
    }

    @Override
    public void onInitialize() {
        // Register /kid toggle command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("kid")
                .requires(source -> source.hasPermissionLevel(2))
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

        // Restore health, food, and saturation each tick for kid-mode players
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (kidModePlayers.contains(player.getUuid())) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                    player.getHungerManager().setFoodLevel(20);
                    player.getHungerManager().setSaturationLevel(20.0f);
                    player.getHungerManager().setExhaustion(0.0f);
                }
            }
        });

        // Load kid-mode state when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server));

        // Save kid-mode state when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save(server));
    }
}
