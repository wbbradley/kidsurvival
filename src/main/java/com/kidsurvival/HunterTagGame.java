package com.kidsurvival;

import java.util.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public class HunterTagGame {
    private boolean gameActive = false;
    private boolean gamePaused = false;
    private final Set<UUID> hunters = new HashSet<>();
    private final Set<UUID> runners = new HashSet<>();
    private final Map<UUID, Long> frozenUntilTick = new HashMap<>();
    private final Map<UUID, Vec3d> frozenPositions = new HashMap<>();
    private final Map<UUID, Long> runnerTickScores = new HashMap<>();
    private Set<UUID> benchedPlayers = Set.of();
    private long tickCounter = 0;

    private static final String HUNTER_TEAM = "ks_hunter";
    private static final String RUNNER_TEAM = "ks_runner";
    private static final String SCORE_OBJECTIVE = "ks_score";

    public boolean isGameActive() {
        return gameActive;
    }

    public boolean isPlayerInGame(UUID uuid) {
        return gameActive && (hunters.contains(uuid) || runners.contains(uuid));
    }

    public void startRound(MinecraftServer server, ServerPlayerEntity hunterPlayer, Set<UUID> benchedPlayers) {
        hunters.clear();
        runners.clear();
        frozenUntilTick.clear();
        frozenPositions.clear();
        gamePaused = false;
        this.benchedPlayers = benchedPlayers;

        UUID hunterUuid = hunterPlayer.getUuid();
        hunters.add(hunterUuid);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (!uuid.equals(hunterUuid) && !benchedPlayers.contains(uuid)) {
                runners.add(uuid);
            }
        }

        gameActive = true;

        // Teleport all players near (0, 0) with random spread
        ServerWorld world = server.getOverworld();
        Random rand = new Random();
        Map<UUID, Vec3d> teleportTargets = new HashMap<>();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (hunters.contains(uuid) || runners.contains(uuid)) {
                int x = rand.nextInt(21) - 10;
                int z = rand.nextInt(21) - 10;
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                teleportTargets.put(uuid, new Vec3d(x + 0.5, y, z + 0.5));
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Vec3d target = teleportTargets.get(player.getUuid());
            if (target != null) {
                player.teleport(world, target.x, target.y, target.z, Set.of(), 0, 0, true);
            }
        }

        // Equip loadout for all participants
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (hunters.contains(player.getUuid()) || runners.contains(player.getUuid())) {
                equipLoadout(player);
            }
        }

        // Freeze hunter for 600 ticks (30 seconds)
        long unfreezeAt = tickCounter + 600;
        frozenUntilTick.put(hunterUuid, unfreezeAt);
        Vec3d hunterTarget = teleportTargets.get(hunterUuid);
        if (hunterTarget != null) {
            frozenPositions.put(hunterUuid, hunterTarget);
        }

        setupTeams(server);
        createScoreboard(server);
        applyGlowing(server);

        broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                .append(Text.literal("Round started! ").formatted(Formatting.YELLOW))
                .append(Text.literal(hunterPlayer.getName().getString()).formatted(Formatting.RED))
                .append(Text.literal(" is the hunter (frozen for 30s)!").formatted(Formatting.YELLOW)));
    }

    /**
     * Returns true if the attack was a tag (caller should cancel the attack).
     */
    public boolean handleAttack(PlayerEntity attacker, Entity target) {
        if (!gameActive || gamePaused) return false;
        if (!(target instanceof ServerPlayerEntity targetPlayer)) return false;
        if (!(attacker instanceof ServerPlayerEntity attackerPlayer)) return false;

        UUID attackerUuid = attacker.getUuid();
        UUID targetUuid = target.getUuid();

        if (!hunters.contains(attackerUuid)) return false;
        if (!runners.contains(targetUuid)) return false;

        // Frozen hunter can't tag
        if (frozenUntilTick.containsKey(attackerUuid) && frozenUntilTick.get(attackerUuid) > tickCounter) {
            return true; // Cancel attack but don't tag
        }

        // Tag: convert runner to hunter
        runners.remove(targetUuid);
        hunters.add(targetUuid);

        MinecraftServer server = attackerPlayer.getEntityWorld().getServer();
        if (server == null) return true;

        assignTeam(server, targetPlayer, HUNTER_TEAM);
        applyGlowingToPlayer(targetPlayer, true);

        broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                .append(Text.literal(targetPlayer.getName().getString()).formatted(Formatting.RED))
                .append(Text.literal(" was tagged by ").formatted(Formatting.YELLOW))
                .append(Text.literal(attackerPlayer.getName().getString()).formatted(Formatting.RED))
                .append(Text.literal("!").formatted(Formatting.YELLOW)));

        if (runners.isEmpty()) {
            endRound(server, targetUuid);
        }

        return true;
    }

    public void onTick(MinecraftServer server) {
        tickCounter++;
        if (!gameActive || gamePaused) return;

        // Freeze enforcement
        Iterator<Map.Entry<UUID, Long>> freezeIt = frozenUntilTick.entrySet().iterator();
        while (freezeIt.hasNext()) {
            Map.Entry<UUID, Long> entry = freezeIt.next();
            UUID uuid = entry.getKey();
            long unfreezeAt = entry.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;

            if (tickCounter >= unfreezeAt) {
                freezeIt.remove();
                frozenPositions.remove(uuid);
                player.sendMessage(Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                        .append(Text.literal("You are unfrozen! Go hunt!").formatted(Formatting.RED)), true);
            } else {
                Vec3d pos = frozenPositions.get(uuid);
                if (pos != null) {
                    player.requestTeleport(pos.x, pos.y, pos.z);
                    player.setVelocity(Vec3d.ZERO);
                }
            }
        }

        // Scoring: increment for online runners
        for (UUID runnerUuid : runners) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(runnerUuid);
            if (player != null) {
                runnerTickScores.merge(runnerUuid, 1L, Long::sum);
            }
        }

        // Action bar update (every 20 ticks = 1 second)
        if (tickCounter % 20 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                Text message;
                if (hunters.contains(uuid)) {
                    Long unfreezeAt = frozenUntilTick.get(uuid);
                    if (unfreezeAt != null && unfreezeAt > tickCounter) {
                        long secondsLeft = (unfreezeAt - tickCounter) / 20;
                        message = Text.literal("HUNTER (frozen: " + secondsLeft + "s)").formatted(Formatting.RED);
                    } else {
                        message = Text.literal("HUNTER").formatted(Formatting.RED);
                    }
                } else if (runners.contains(uuid)) {
                    message = Text.literal("RUNNER").formatted(Formatting.GREEN);
                } else {
                    continue;
                }
                player.sendMessage(message, true);
            }
        }

        // Scoreboard update (every 20 ticks = 1 second)
        if (tickCounter % 20 == 0) {
            updateScoreboard(server);
        }

        // Glowing refresh (every 60 ticks = 3 seconds)
        if (tickCounter % 60 == 0) {
            applyGlowing(server);
        }
    }

    private void endRound(MinecraftServer server, UUID lastRunnerUuid) {
        broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                .append(Text.literal("Round over! Scores:").formatted(Formatting.YELLOW)));

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(runnerTickScores.entrySet());
        sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());

        for (Map.Entry<UUID, Long> entry : sorted) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            String name = player != null ? player.getName().getString() : entry.getKey().toString();
            long seconds = entry.getValue() / 20;
            broadcast(server, Text.literal("  " + name + ": " + seconds + "s").formatted(Formatting.AQUA));
        }

        clearEffectsAndTeams(server);

        // Restart with last-tagged runner as new hunter
        ServerPlayerEntity newHunter = server.getPlayerManager().getPlayer(lastRunnerUuid);
        long eligible = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> !benchedPlayers.contains(p.getUuid())).count();
        if (newHunter != null && eligible >= 2) {
            startRound(server, newHunter, benchedPlayers);
        } else {
            gameActive = false;
            broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                    .append(Text.literal("Game ended — not enough players for next round.").formatted(Formatting.YELLOW)));
        }
    }

    public void stopGame(MinecraftServer server) {
        if (!gameActive) return;
        gameActive = false;
        gamePaused = false;
        hunters.clear();
        runners.clear();
        frozenUntilTick.clear();
        frozenPositions.clear();
        // Keep runnerTickScores across games

        clearEffectsAndTeams(server);

        broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                .append(Text.literal("Game stopped!").formatted(Formatting.YELLOW)));
    }

    public void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        if (!gameActive) return;
        UUID uuid = player.getUuid();

        ensureTeamsExist(server);

        if (hunters.contains(uuid)) {
            assignTeam(server, player, HUNTER_TEAM);
            applyGlowingToPlayer(player, true);

            if (gamePaused) {
                gamePaused = false;
                broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                        .append(Text.literal("Game resumed — hunter is back!").formatted(Formatting.YELLOW)));
            }
        } else if (runners.contains(uuid)) {
            assignTeam(server, player, RUNNER_TEAM);
        } else if (!benchedPlayers.contains(uuid)) {
            // New player joins mid-game as runner
            runners.add(uuid);
            assignTeam(server, player, RUNNER_TEAM);
            player.sendMessage(Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                    .append(Text.literal("You joined as a runner!").formatted(Formatting.GREEN)), false);
        }
        updateScoreboard(server);
    }

    public void removePlayer(UUID uuid, MinecraftServer server) {
        if (!gameActive) return;
        hunters.remove(uuid);
        runners.remove(uuid);
        frozenUntilTick.remove(uuid);
        frozenPositions.remove(uuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            player.removeStatusEffect(StatusEffects.GLOWING);
        }
        if (runners.isEmpty() && gameActive) {
            stopGame(server);
        }
    }

    public void onPlayerDisconnect(ServerPlayerEntity player, MinecraftServer server) {
        if (!gameActive) return;
        UUID uuid = player.getUuid();

        if (hunters.contains(uuid)) {
            // Check if any other hunter is still online
            boolean otherHunterOnline = false;
            for (UUID hunterUuid : hunters) {
                if (!hunterUuid.equals(uuid) && server.getPlayerManager().getPlayer(hunterUuid) != null) {
                    otherHunterOnline = true;
                    break;
                }
            }

            if (!otherHunterOnline) {
                gamePaused = true;
                broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                        .append(Text.literal("Game paused — all hunters disconnected.").formatted(Formatting.YELLOW)));
            }
        } else if (runners.contains(uuid)) {
            runners.remove(uuid);
            broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                    .append(Text.literal(player.getName().getString() + " (runner) disconnected.").formatted(Formatting.YELLOW)));

            if (runners.isEmpty()) {
                broadcast(server, Text.literal("[Hunter Tag] ").formatted(Formatting.GOLD)
                        .append(Text.literal("No runners left!").formatted(Formatting.YELLOW)));
                stopGame(server);
            }
        }
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("gameActive", gameActive);

        JsonArray hunterArr = new JsonArray();
        for (UUID uuid : hunters) hunterArr.add(uuid.toString());
        obj.add("hunters", hunterArr);

        JsonArray runnerArr = new JsonArray();
        for (UUID uuid : runners) runnerArr.add(uuid.toString());
        obj.add("runners", runnerArr);

        JsonObject scores = new JsonObject();
        for (Map.Entry<UUID, Long> entry : runnerTickScores.entrySet()) {
            scores.addProperty(entry.getKey().toString(), entry.getValue());
        }
        obj.add("runnerTickScores", scores);

        return obj;
    }

    public void loadFrom(JsonObject obj) {
        if (obj == null) return;

        gameActive = obj.has("gameActive") && obj.get("gameActive").getAsBoolean();

        hunters.clear();
        if (obj.has("hunters")) {
            for (JsonElement el : obj.getAsJsonArray("hunters")) {
                hunters.add(UUID.fromString(el.getAsString()));
            }
        }

        runners.clear();
        if (obj.has("runners")) {
            for (JsonElement el : obj.getAsJsonArray("runners")) {
                runners.add(UUID.fromString(el.getAsString()));
            }
        }

        runnerTickScores.clear();
        if (obj.has("runnerTickScores")) {
            JsonObject scores = obj.getAsJsonObject("runnerTickScores");
            for (Map.Entry<String, JsonElement> entry : scores.entrySet()) {
                runnerTickScores.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
            }
        }

        // Don't restore freeze state — stale after restart
        frozenUntilTick.clear();
        frozenPositions.clear();
        gamePaused = false;
    }

    // --- Private helpers ---

    private void setupTeams(MinecraftServer server) {
        ensureTeamsExist(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (hunters.contains(uuid)) {
                assignTeam(server, player, HUNTER_TEAM);
            } else if (runners.contains(uuid)) {
                assignTeam(server, player, RUNNER_TEAM);
            }
        }
    }

    private void ensureTeamsExist(MinecraftServer server) {
        var scoreboard = server.getScoreboard();

        Team hunterTeam = scoreboard.getTeam(HUNTER_TEAM);
        if (hunterTeam == null) {
            hunterTeam = scoreboard.addTeam(HUNTER_TEAM);
            hunterTeam.setColor(Formatting.RED);
            hunterTeam.setPrefix(Text.literal("[Hunter] ").formatted(Formatting.RED));
        }

        Team runnerTeam = scoreboard.getTeam(RUNNER_TEAM);
        if (runnerTeam == null) {
            runnerTeam = scoreboard.addTeam(RUNNER_TEAM);
            runnerTeam.setColor(Formatting.GREEN);
            runnerTeam.setPrefix(Text.literal("[Runner] ").formatted(Formatting.GREEN));
        }
    }

    private void assignTeam(MinecraftServer server, ServerPlayerEntity player, String teamName) {
        var scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);
        }
    }

    private void applyGlowing(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (hunters.contains(uuid)) {
                applyGlowingToPlayer(player, true);
            } else {
                player.removeStatusEffect(StatusEffects.GLOWING);
            }
        }
    }

    private void applyGlowingToPlayer(ServerPlayerEntity player, boolean apply) {
        if (apply) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.GLOWING, 80, 0, false, false, false));
        } else {
            player.removeStatusEffect(StatusEffects.GLOWING);
        }
    }

    private void clearEffectsAndTeams(MinecraftServer server) {
        var scoreboard = server.getScoreboard();

        removeScoreboard(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.removeStatusEffect(StatusEffects.GLOWING);
        }

        Team hunterTeam = scoreboard.getTeam(HUNTER_TEAM);
        if (hunterTeam != null) scoreboard.removeTeam(hunterTeam);

        Team runnerTeam = scoreboard.getTeam(RUNNER_TEAM);
        if (runnerTeam != null) scoreboard.removeTeam(runnerTeam);
    }

    private void createScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        ScoreboardObjective existing = scoreboard.getNullableObjective(SCORE_OBJECTIVE);
        if (existing != null) {
            scoreboard.removeObjective(existing);
        }
        ScoreboardObjective objective = scoreboard.addObjective(
                SCORE_OBJECTIVE,
                ScoreboardCriterion.DUMMY,
                Text.literal("Hunter Tag").formatted(Formatting.GOLD),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                null);
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        updateScoreboard(server);
    }

    private void updateScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(SCORE_OBJECTIVE);
        if (objective == null) return;

        for (Map.Entry<UUID, Long> entry : runnerTickScores.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) continue;
            int seconds = (int) (entry.getValue() / 20);
            scoreboard.getOrCreateScore(player, objective).setScore(seconds);
        }
    }

    private void removeScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(SCORE_OBJECTIVE);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }

    private void equipLoadout(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        inventory.clear();

        // Elytra in chest slot
        player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));

        // 64 firework rockets (flight duration 3) in hotbar slot 0
        ItemStack fireworks = new ItemStack(Items.FIREWORK_ROCKET, 64);
        fireworks.set(DataComponentTypes.FIREWORKS, new FireworksComponent(3, List.of()));
        inventory.setStack(0, fireworks);

        // Boat in hotbar slot 1
        inventory.setStack(1, new ItemStack(Items.OAK_BOAT));
    }

    private void broadcast(MinecraftServer server, Text message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message, false);
        }
    }
}
