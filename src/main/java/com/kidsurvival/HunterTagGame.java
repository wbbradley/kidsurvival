package com.kidsurvival;

import java.util.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class HunterTagGame {
    private boolean gameActive = false;
    private boolean gamePaused = false;
    private final Set<UUID> hunters = new HashSet<>();
    private final Set<UUID> runners = new HashSet<>();
    private final Map<UUID, Long> frozenUntilTick = new HashMap<>();
    private final Map<UUID, Vec3> frozenPositions = new HashMap<>();
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

    public void startRound(MinecraftServer server, ServerPlayer hunterPlayer, Set<UUID> benchedPlayers) {
        hunters.clear();
        runners.clear();
        frozenUntilTick.clear();
        frozenPositions.clear();
        gamePaused = false;
        this.benchedPlayers = benchedPlayers;

        UUID hunterUuid = hunterPlayer.getUUID();
        hunters.add(hunterUuid);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!uuid.equals(hunterUuid) && !benchedPlayers.contains(uuid)) {
                runners.add(uuid);
            }
        }

        gameActive = true;

        // Teleport all players near (0, 0) with random spread
        ServerLevel world = server.overworld();
        Random rand = new Random();
        Map<UUID, Vec3> teleportTargets = new HashMap<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (hunters.contains(uuid) || runners.contains(uuid)) {
                int x = rand.nextInt(21) - 10;
                int z = rand.nextInt(21) - 10;
                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                teleportTargets.put(uuid, new Vec3(x + 0.5, y, z + 0.5));
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Vec3 target = teleportTargets.get(player.getUUID());
            if (target != null) {
                player.teleportTo(world, target.x, target.y, target.z, Set.of(), 0, 0, true);
            }
        }

        // Equip loadout for all participants
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hunters.contains(player.getUUID()) || runners.contains(player.getUUID())) {
                equipLoadout(player);
            }
        }

        // Freeze hunter for 600 ticks (30 seconds)
        long unfreezeAt = tickCounter + 600;
        frozenUntilTick.put(hunterUuid, unfreezeAt);
        Vec3 hunterTarget = teleportTargets.get(hunterUuid);
        if (hunterTarget != null) {
            frozenPositions.put(hunterUuid, hunterTarget);
        }

        setupTeams(server);
        createScoreboard(server);
        applyGlowing(server);

        broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Round started! ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(hunterPlayer.getName().getString()).withStyle(ChatFormatting.RED))
                .append(Component.literal(" is the hunter (frozen for 30s)!").withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Returns true if the attack was a tag (caller should cancel the attack).
     */
    public boolean handleAttack(Player attacker, Entity target) {
        if (!gameActive || gamePaused) return false;
        if (!(target instanceof ServerPlayer targetPlayer)) return false;
        if (!(attacker instanceof ServerPlayer attackerPlayer)) return false;

        UUID attackerUuid = attacker.getUUID();
        UUID targetUuid = target.getUUID();

        if (!hunters.contains(attackerUuid)) return false;
        if (!runners.contains(targetUuid)) return false;

        // Frozen hunter can't tag
        if (frozenUntilTick.containsKey(attackerUuid) && frozenUntilTick.get(attackerUuid) > tickCounter) {
            return true; // Cancel attack but don't tag
        }

        // Tag: convert runner to hunter
        runners.remove(targetUuid);
        hunters.add(targetUuid);

        MinecraftServer server = attackerPlayer.level().getServer();
        if (server == null) return true;

        assignTeam(server, targetPlayer, HUNTER_TEAM);
        applyGlowingToPlayer(targetPlayer, true);

        broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(targetPlayer.getName().getString()).withStyle(ChatFormatting.RED))
                .append(Component.literal(" was tagged by ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(attackerPlayer.getName().getString()).withStyle(ChatFormatting.RED))
                .append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));

        // Red flash for the captured player
        targetPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
        targetPlayer.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("CAPTURED!").withStyle(ChatFormatting.DARK_RED)));

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

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            if (tickCounter >= unfreezeAt) {
                freezeIt.remove();
                frozenPositions.remove(uuid);
                player.sendSystemMessage(Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("You are unfrozen! Go hunt!").withStyle(ChatFormatting.RED)), true);
            } else {
                Vec3 pos = frozenPositions.get(uuid);
                if (pos != null) {
                    player.teleportTo(pos.x, pos.y, pos.z);
                    player.setDeltaMovement(Vec3.ZERO);
                }
            }
        }

        // Scoring: increment for online runners
        for (UUID runnerUuid : runners) {
            ServerPlayer player = server.getPlayerList().getPlayer(runnerUuid);
            if (player != null) {
                runnerTickScores.merge(runnerUuid, 1L, Long::sum);
            }
        }

        // Invisibility for sneaking players (hides name tag, keeps glowing outline)
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!hunters.contains(uuid) && !runners.contains(uuid)) continue;
            if (player.isShiftKeyDown()) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.INVISIBILITY, 5, 0, false, false, false));
            }
        }

        // Action bar update (every 20 ticks = 1 second)
        if (tickCounter % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                Component message;
                if (hunters.contains(uuid)) {
                    Long unfreezeAt = frozenUntilTick.get(uuid);
                    if (unfreezeAt != null && unfreezeAt > tickCounter) {
                        long secondsLeft = (unfreezeAt - tickCounter) / 20;
                        message = Component.literal("HUNTER (frozen: " + secondsLeft + "s)").withStyle(ChatFormatting.RED);
                    } else {
                        message = Component.literal("HUNTER").withStyle(ChatFormatting.RED);
                    }
                } else if (runners.contains(uuid)) {
                    message = Component.literal("RUNNER").withStyle(ChatFormatting.GREEN);
                } else {
                    continue;
                }
                player.sendSystemMessage(message, true);
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
        broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Round over! Scores:").withStyle(ChatFormatting.YELLOW)));

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(runnerTickScores.entrySet());
        sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());

        for (Map.Entry<UUID, Long> entry : sorted) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            String name = player != null ? player.getName().getString() : entry.getKey().toString();
            long seconds = entry.getValue() / 20;
            broadcast(server, Component.literal("  " + name + ": " + seconds + "s").withStyle(ChatFormatting.AQUA));
        }

        clearEffectsAndTeams(server);

        // Restart with last-tagged runner as new hunter
        ServerPlayer newHunter = server.getPlayerList().getPlayer(lastRunnerUuid);
        long eligible = server.getPlayerList().getPlayers().stream()
                .filter(p -> !benchedPlayers.contains(p.getUUID())).count();
        if (newHunter != null && eligible >= 2) {
            startRound(server, newHunter, benchedPlayers);
        } else {
            gameActive = false;
            broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Game ended — not enough players for next round.").withStyle(ChatFormatting.YELLOW)));
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

        broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Game stopped!").withStyle(ChatFormatting.YELLOW)));
    }

    public void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        if (!gameActive) return;
        UUID uuid = player.getUUID();

        ensureTeamsExist(server);

        if (hunters.contains(uuid)) {
            assignTeam(server, player, HUNTER_TEAM);
            applyGlowingToPlayer(player, true);

            if (gamePaused) {
                gamePaused = false;
                broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Game resumed — hunter is back!").withStyle(ChatFormatting.YELLOW)));
            }
        } else if (runners.contains(uuid)) {
            assignTeam(server, player, RUNNER_TEAM);
        } else if (!benchedPlayers.contains(uuid)) {
            // New player joins mid-game as runner
            runners.add(uuid);
            assignTeam(server, player, RUNNER_TEAM);
            player.sendSystemMessage(Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("You joined as a runner!").withStyle(ChatFormatting.GREEN)), false);
        }
        updateScoreboard(server);
    }

    public void removePlayer(UUID uuid, MinecraftServer server) {
        if (!gameActive) return;
        hunters.remove(uuid);
        runners.remove(uuid);
        frozenUntilTick.remove(uuid);
        frozenPositions.remove(uuid);
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.removeEffect(MobEffects.GLOWING);
        }
        if (runners.isEmpty() && gameActive) {
            stopGame(server);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player, MinecraftServer server) {
        if (!gameActive) return;
        UUID uuid = player.getUUID();

        if (hunters.contains(uuid)) {
            // Check if any other hunter is still online
            boolean otherHunterOnline = false;
            for (UUID hunterUuid : hunters) {
                if (!hunterUuid.equals(uuid) && server.getPlayerList().getPlayer(hunterUuid) != null) {
                    otherHunterOnline = true;
                    break;
                }
            }

            if (!otherHunterOnline) {
                gamePaused = true;
                broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Game paused — all hunters disconnected.").withStyle(ChatFormatting.YELLOW)));
            }
        } else if (runners.contains(uuid)) {
            runners.remove(uuid);
            broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(player.getName().getString() + " (runner) disconnected.").withStyle(ChatFormatting.YELLOW)));

            if (runners.isEmpty()) {
                broadcast(server, Component.literal("[Hunter Tag] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("No runners left!").withStyle(ChatFormatting.YELLOW)));
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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (hunters.contains(uuid)) {
                assignTeam(server, player, HUNTER_TEAM);
            } else if (runners.contains(uuid)) {
                assignTeam(server, player, RUNNER_TEAM);
            }
        }
    }

    private void ensureTeamsExist(MinecraftServer server) {
        var scoreboard = server.getScoreboard();

        PlayerTeam hunterTeam = scoreboard.getPlayerTeam(HUNTER_TEAM);
        if (hunterTeam == null) {
            hunterTeam = scoreboard.addPlayerTeam(HUNTER_TEAM);
            hunterTeam.setColor(ChatFormatting.RED);
            hunterTeam.setPlayerPrefix(Component.literal("[Hunter] ").withStyle(ChatFormatting.RED));
        }

        PlayerTeam runnerTeam = scoreboard.getPlayerTeam(RUNNER_TEAM);
        if (runnerTeam == null) {
            runnerTeam = scoreboard.addPlayerTeam(RUNNER_TEAM);
            runnerTeam.setColor(ChatFormatting.GREEN);
            runnerTeam.setPlayerPrefix(Component.literal("[Runner] ").withStyle(ChatFormatting.GREEN));
        }
    }

    private void assignTeam(MinecraftServer server, ServerPlayer player, String teamName) {
        var scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team != null) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }

    private void applyGlowing(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (hunters.contains(uuid)) {
                applyGlowingToPlayer(player, true);
            } else {
                player.removeEffect(MobEffects.GLOWING);
            }
        }
    }

    private void applyGlowingToPlayer(ServerPlayer player, boolean apply) {
        if (apply) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, 80, 0, false, false, false));
        } else {
            player.removeEffect(MobEffects.GLOWING);
        }
    }

    private void clearEffectsAndTeams(MinecraftServer server) {
        var scoreboard = server.getScoreboard();

        removeScoreboard(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeEffect(MobEffects.GLOWING);
        }

        PlayerTeam hunterTeam = scoreboard.getPlayerTeam(HUNTER_TEAM);
        if (hunterTeam != null) scoreboard.removePlayerTeam(hunterTeam);

        PlayerTeam runnerTeam = scoreboard.getPlayerTeam(RUNNER_TEAM);
        if (runnerTeam != null) scoreboard.removePlayerTeam(runnerTeam);
    }

    private void createScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        Objective existing = scoreboard.getObjective(SCORE_OBJECTIVE);
        if (existing != null) {
            scoreboard.removeObjective(existing);
        }
        Objective objective = scoreboard.addObjective(
                SCORE_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("Hunter Tag").withStyle(ChatFormatting.GOLD),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null);
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        updateScoreboard(server);
    }

    private void updateScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(SCORE_OBJECTIVE);
        if (objective == null) return;

        for (Map.Entry<UUID, Long> entry : runnerTickScores.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            int seconds = (int) (entry.getValue() / 20);
            scoreboard.getOrCreatePlayerScore(player, objective).set(seconds);
        }
    }

    private void removeScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(SCORE_OBJECTIVE);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }

    private void equipLoadout(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();

        // LATER: Boat in hotbar slot 0
        // inventory.setItem(0, new ItemStack(Items.OAK_BOAT));
    }

    private void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message, false);
        }
    }
}
