package dev.sharedrun.endrun;

import com.google.gson.Gson;
import dev.sharedrun.SharedRun;
import dev.sharedrun.achievement.AchievementTracker;
import dev.sharedrun.mixin.LivingEntityAccessor;
import dev.sharedrun.network.RunSummaryPayload;
import dev.sharedrun.progress.ProgressTracker;
import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.stats.HeartsCausedTracker;
import dev.sharedrun.sync.EffectsSyncManager;
import dev.sharedrun.sync.HpSyncManager;
import dev.sharedrun.sync.HungerSyncManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.rule.GameRules;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EndRunHandler {

    public enum EndReason { VICTORY, TIME_OUT, ALL_DEAD }

    /** Durée totale de la cinématique de mort côté client (en secondes). */
    private static final int DEATH_CINEMATIC_SECONDS = 12;
    /** Durée totale de la cinématique de victoire côté client (en secondes). */
    private static final int VICTORY_CINEMATIC_SECONDS = 14;

    public static volatile String lastSummaryJson = null;

    private static volatile String pendingDeadPlayerName = null;
    private static volatile String pendingDeathReason = null;
    private static volatile String pendingDragonKilledBy = null;

    /** Set par le hook AFTER_DEATH du dragon : pseudo du joueur qui a porté le coup fatal. */
    public static void setPendingDragonKiller(String name) {
        pendingDragonKilledBy = name;
    }

    /** Si > 0, à ce tick on respawn + bascule en SPECTATOR (post-cinématique). */
    private static volatile long pendingPostCinematicTick = -1;

    private EndRunHandler() {}

    public static void endRun(MinecraftServer server, EndReason reason) {
        endRun(server, reason, null, null);
    }

    public static void endRun(MinecraftServer server, EndReason reason, Text deathMessage) {
        endRun(server, reason, null, deathMessage);
    }

    public static void endRun(MinecraftServer server, EndReason reason, String deadPlayerName, Text deathMessage) {
        if (SharedRunState.runEnded) return;
        SharedRunState.runEnded = true;

        if (reason == EndReason.ALL_DEAD) {
            pendingDeadPlayerName = deadPlayerName;
            pendingDeathReason = deathMessage != null ? deathMessage.getString() : null;
        } else {
            pendingDeadPlayerName = null;
            pendingDeathReason = null;
        }

        SharedRunState.timerRunning = false;
        SharedRunState.victoryTriggered = (reason == EndReason.VICTORY);

        SharedRunState.hpSyncEnabled = false;
        HpSyncManager.clearTracking();
        HungerSyncManager.clearTracking();
        EffectsSyncManager.clearTracking();
        SharedRunState.sharedHealthInitialized = false;
        SharedRunState.hungerInitialized = false;

        // === IMPORTANT : pour ALL_DEAD on envoie le packet AVANT de respawn ===
        // Sinon le respawn TP le joueur au spawn AVANT que la cinématique ouvre,
        // et on a l'impression d'avoir été TP avant de comprendre qu'on est mort.
        //
        // Le client reçoit le packet → ouvre DeathCinematicScreen → override
        // l'écran "Vous êtes mort" vanilla. Le joueur reste "mort" pendant 12s,
        // puis on le respawn + spectator après.

        sendSummaryPacket(server, reason);

        if (reason == EndReason.ALL_DEAD) {
            // Les joueurs viennent de mourir : on les ressuscite SUR PLACE (pas de TP au spawn),
            // invulnérables pendant la cinématique pour ne pas re-mourir (lave, void, etc.).
            // Ils restent à leur position de mort dans leur dimension (End/Nether/Overworld).
            // Le gamemode reste tel quel pendant la cinématique pour que le skin rende
            // correctement (en spectator le modèle est transparent / tête seule).
            doImmediateRevive(server);
            pendingPostCinematicTick = server.getOverworld().getTime() + DEATH_CINEMATIC_SECONDS * 20L;
        } else if (reason == EndReason.VICTORY) {
            // Joueurs déjà vivants (ils viennent de tuer le dragon).
            // On NE bascule PAS en spectator immédiatement : sinon le rendu des skins
            // dans VictoryCinematicScreen serait cassé (modèle transparent / tête seule).
            // À la place on les rend invulnérables (pour pas mourir dans le void de l'End),
            // garde leur gamemode actuel, et on bascule en spectator après la cinematic.
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.setInvulnerable(true);
                p.clearStatusEffects();
                p.setHealth(p.getMaxHealth());
                p.getHungerManager().setFoodLevel(20);
                p.getHungerManager().setSaturationLevel(5f);
            }
            pendingPostCinematicTick = server.getOverworld().getTime() + VICTORY_CINEMATIC_SECONDS * 20L;
        } else {
            // TIME_OUT : alive déjà → spectator immédiat, sur place
            switchAllToSpectator(server);
            pendingPostCinematicTick = -1;
        }

        // Restaure le border vanilla
        net.minecraft.world.border.WorldBorder border = server.getOverworld().getWorldBorder();
        if (Math.abs(border.getSize() - 5.9999968E7) > 0.01) {
            border.setSize(5.9999968E7);
        }
        server.getOverworld().getGameRules().setValue(GameRules.SHOW_DEATH_MESSAGES, true, server);
        server.getOverworld().getGameRules().setValue(GameRules.ADVANCE_TIME, true, server);

        broadcastDebriefMenu(server);
    }

    /**
     * "Ressuscite" les joueurs SUR PLACE sans appeler respawnPlayer (qui TP au spawn).
     * - health = max, deathTime = 0 → plus dead
     * - invulnerable = true → ne peuvent pas re-mourir pendant la cinématique
     *   (lave, void, mobs, etc. ignorés)
     * - gamemode inchangé → la cinématique rend le skin correctement (en spectator
     *   le modèle apparaît transparent / tête seule)
     */
    private static void doImmediateRevive(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isDead() || p.getHealth() <= 0) {
                p.setHealth(p.getMaxHealth());
                try {
                    ((LivingEntityAccessor) p).sharedrun$setDeathTime(0);
                } catch (Throwable ignored) {}
            }
            p.clearStatusEffects();
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(5f);
            p.setInvulnerable(true); // anti re-death pendant les 12s de cinématique
        }
    }

    /**
     * Bascule tout le monde en SPECTATOR (sauf creative) + retire l'invulnérabilité.
     * Appelé après la cinématique (ALL_DEAD) OU immédiatement (VICTORY/TIME_OUT).
     */
    private static void switchAllToSpectator(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.setInvulnerable(false);
            if (p.interactionManager.getGameMode() != GameMode.CREATIVE
                    && p.interactionManager.getGameMode() != GameMode.SPECTATOR) {
                p.changeGameMode(GameMode.SPECTATOR);
            }
        }
    }

    /** Tick handler : bascule en spectator quand la cinématique est terminée. */
    public static void tick(MinecraftServer server) {
        if (pendingPostCinematicTick <= 0) return;
        if (server.getOverworld().getTime() < pendingPostCinematicTick) return;
        switchAllToSpectator(server);
        pendingPostCinematicTick = -1;
    }

    private static void sendSummaryPacket(MinecraftServer server, EndReason reason) {
        RunSummary data = buildSummary(server, reason);
        String json = new Gson().toJson(data);
        lastSummaryJson = json;
        RunSummaryPayload payload = new RunSummaryPayload(json);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static boolean resendSummaryTo(ServerPlayerEntity player) {
        if (lastSummaryJson == null) return false;
        ServerPlayNetworking.send(player, new RunSummaryPayload(lastSummaryJson));
        return true;
    }

    /**
     * Broadcast le menu cliquable de debrief (TP Overworld/Nether/Stronghold/End + rouvrir récap).
     * Utilise ClickEvent.Custom → aucune popup de confirmation, géré par CustomClickActionMixin.
     */
    public static void broadcastDebriefMenu(MinecraftServer server) {
        Text divider = Text.literal("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Text header = Text.literal("§6§l🏁 Debrief §7— §fclique pour TP (spectator)");

        MutableText buttons = Text.literal("  ")
                .append(buildCustomLink("§a[🌳 Overworld]", "tp_overworld", "TP au spawn Overworld"))
                .append(Text.literal("  "))
                .append(buildCustomLink("§c[🔥 Nether]", "tp_nether", "TP à l'entrée Nether"))
                .append(Text.literal("  "))
                .append(buildCustomLink("§4[🏰 Forteresse]", "tp_fortress", "TP à la forteresse Nether"))
                .append(Text.literal("  "))
                .append(buildCustomLink("§6[🏛 Stronghold]", "tp_stronghold", "TP au stronghold"))
                .append(Text.literal("  "))
                .append(buildCustomLink("§5[🐲 End]", "tp_end", "TP au point d'arrivée End"));

        MutableText reopen = Text.literal("  ")
                .append(buildCustomLink("§e§l[📋 Rouvrir le récap]", "debrief", "Réafficher l'écran de fin de run"));

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal(""), false);
            p.sendMessage(divider, false);
            p.sendMessage(header, false);
            p.sendMessage(buttons, false);
            p.sendMessage(reopen, false);
            p.sendMessage(divider, false);
        }
    }

    private static MutableText buildCustomLink(String label, String customAction, String tooltip) {
        Identifier id = SharedRun.id(customAction);
        return Text.literal(label).styled(style -> style
                .withClickEvent(new ClickEvent.Custom(id, Optional.empty()))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(tooltip).formatted(Formatting.GRAY)))
        );
    }

    private static RunSummary buildSummary(MinecraftServer server, EndReason reason) {
        RunSummary s = new RunSummary();
        s.endReason = reason.ordinal();
        int elapsed = SharedRunState.totalSeconds - (SharedRunState.remainingTicks / 20);
        if (elapsed < 0) elapsed = 0;
        s.elapsedSeconds = elapsed;
        s.totalSeconds = SharedRunState.totalSeconds;
        s.swapCount = SharedRunState.swapCount;
        s.playerCount = server.getPlayerManager().getPlayerList().size();
        s.totalAchievements = AchievementTracker.getTotalCount();

        addMilestone(s, "food", "minecraft:bread", ProgressTracker.foodReached, ProgressTracker.byFood);
        addMilestone(s, "iron", "minecraft:iron_ingot", ProgressTracker.ironReached, ProgressTracker.byIron);
        addMilestone(s, "diamond", "minecraft:diamond", ProgressTracker.diamondReached, ProgressTracker.byDiamond);
        addMilestone(s, "nether", "minecraft:netherrack", ProgressTracker.netherReached, ProgressTracker.byNether);
        addMilestone(s, "blaze", "minecraft:blaze_rod", ProgressTracker.blazeReached, ProgressTracker.byBlaze);
        addMilestone(s, "pearl", "minecraft:ender_pearl", ProgressTracker.pearlReached, ProgressTracker.byPearl);
        addMilestone(s, "eye", "minecraft:ender_eye", ProgressTracker.eyeReached, ProgressTracker.byEye);
        addMilestone(s, "end", "minecraft:end_portal_frame", ProgressTracker.endReached, ProgressTracker.byEnd);
        addMilestone(s, "dragon", "minecraft:dragon_head", ProgressTracker.dragonKilled, ProgressTracker.byDragon);

        for (var entry : AchievementTracker.getAchievedBy().entrySet()) {
            RunSummary.Achievement a = new RunSummary.Achievement();
            a.key = entry.getKey();
            a.by = entry.getValue();
            s.achievements.add(a);
        }

        var board = HeartsCausedTracker.sortedLeaderboard();
        int rank = 0;
        for (var e : board) {
            if (rank >= 5) break;
            RunSummary.LeaderEntry le = new RunSummary.LeaderEntry();
            le.name = e.name();
            le.hearts = e.hearts();
            s.leaderboard.add(le);
            rank++;
        }

        java.util.Map<String, Integer> contrib = new java.util.HashMap<>();
        countContribution(contrib, ProgressTracker.byFood);
        countContribution(contrib, ProgressTracker.byIron);
        countContribution(contrib, ProgressTracker.byDiamond);
        countContribution(contrib, ProgressTracker.byNether);
        countContribution(contrib, ProgressTracker.byBlaze);
        countContribution(contrib, ProgressTracker.byPearl);
        countContribution(contrib, ProgressTracker.byEye);
        countContribution(contrib, ProgressTracker.byEnd);
        countContribution(contrib, ProgressTracker.byDragon);
        for (String by : AchievementTracker.getAchievedBy().values()) {
            countContribution(contrib, by);
        }
        String mvp = null;
        int mvpCount = 0;
        for (var entry : contrib.entrySet()) {
            if (entry.getValue() > mvpCount) {
                mvpCount = entry.getValue();
                mvp = entry.getKey();
            }
        }
        s.mvp = mvp;
        s.mvpContributions = mvpCount;

        s.deadPlayerName = pendingDeadPlayerName;
        s.deathReason = pendingDeathReason;
        s.dragonKilledBy = pendingDragonKilledBy;

        // Bonus / Malus food
        var topChef = dev.sharedrun.stats.FoodEatTracker.topNormalFoodEater();
        if (topChef != null) {
            s.topChef = topChef.name();
            s.topChefCount = topChef.count();
        }
        var topRotten = dev.sharedrun.stats.FoodEatTracker.topRottenFleshEater();
        if (topRotten != null) {
            s.topRotten = topRotten.name();
            s.topRottenCount = topRotten.count();
        }

        // Liste des joueurs participants (non-spec) pour le lineup de VictoryCinematicScreen
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            s.participants.add(p.getName().getString());
        }

        return s;
    }

    /**
     * TP debrief. Utilise la méthode Entity.teleport() (la même que vanilla /tp),
     * qui gère correctement le chunk loading cross-dim sans freezer.
     */
    public static void handleDebriefTp(MinecraftServer server, ServerPlayerEntity player, int dest) {
        if (!SharedRunState.runEnded) {
            player.sendMessage(Text.literal("§cTP debrief impossible — la run n'est pas terminée."), false);
            return;
        }

        ServerWorld targetWorld;
        Vec3d targetPos;

        switch (dest) {
            case 1 -> {
                targetWorld = server.getOverworld();
                BlockPos sp = targetWorld.getSpawnPoint().getPos();
                targetPos = new Vec3d(sp.getX() + 0.5, sp.getY() + 1, sp.getZ() + 0.5);
            }
            case 2 -> {
                targetWorld = server.getWorld(World.NETHER);
                if (targetWorld == null) {
                    player.sendMessage(Text.literal("§cLe Nether n'est pas disponible."), false);
                    return;
                }
                if (SharedRunState.netherEntryPos != Long.MIN_VALUE) {
                    BlockPos bp = BlockPos.fromLong(SharedRunState.netherEntryPos);
                    targetPos = new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                } else {
                    BlockPos sp = server.getOverworld().getSpawnPoint().getPos();
                    targetPos = new Vec3d(sp.getX() / 8.0, 100, sp.getZ() / 8.0);
                }
            }
            case 3 -> {
                targetWorld = server.getOverworld();
                BlockPos found;
                boolean wasKnown = SharedRunState.strongholdPos != Long.MIN_VALUE;
                if (wasKnown) {
                    found = BlockPos.fromLong(SharedRunState.strongholdPos);
                } else {
                    // Le stronghold n'a pas été trouvé pendant la run : on le locate maintenant
                    // (équivalent /locate structure) pour pouvoir montrer aux joueurs où il était.
                    player.sendMessage(Text.literal("§7§o🔍 Recherche du stronghold..."), false);
                    found = locateNearestStronghold(targetWorld, player);
                }
                if (found != null) {
                    targetPos = new Vec3d(found.getX() + 0.5, found.getY() + 0.5, found.getZ() + 0.5);
                    if (!wasKnown) {
                        player.sendMessage(Text.literal(String.format(
                                "§a✓ Stronghold trouvé à §f%d, %d, %d",
                                found.getX(), found.getY(), found.getZ())), false);
                    }
                } else {
                    player.sendMessage(Text.literal(
                            "§c✗ Aucun stronghold trouvé dans un rayon de 3200 blocs."), false);
                    return;
                }
            }
            case 4 -> {
                targetWorld = server.getWorld(World.END);
                if (targetWorld == null) {
                    player.sendMessage(Text.literal("§cL'End n'est pas disponible."), false);
                    return;
                }
                if (SharedRunState.endEntryPos != Long.MIN_VALUE) {
                    BlockPos bp = BlockPos.fromLong(SharedRunState.endEntryPos);
                    targetPos = new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                } else {
                    targetPos = new Vec3d(100.5, 60, 0.5);
                }
            }
            case 5 -> {
                // Forteresse du Nether
                targetWorld = server.getWorld(World.NETHER);
                if (targetWorld == null) {
                    player.sendMessage(Text.literal("§cLe Nether n'est pas disponible."), false);
                    return;
                }
                BlockPos found;
                boolean wasKnown = SharedRunState.fortressPos != Long.MIN_VALUE;
                if (wasKnown) {
                    found = BlockPos.fromLong(SharedRunState.fortressPos);
                } else {
                    player.sendMessage(Text.literal("§7§o🔍 Recherche de la forteresse..."), false);
                    found = locateNearestFortress(targetWorld, player);
                }
                if (found != null) {
                    // Si le Y est aberrant (locate retourne souvent chunk-origin Y=0),
                    // on scanne verticalement pour trouver la vraie altitude de la forteresse.
                    BlockPos safe = findSafeNetherY(targetWorld, found);
                    targetPos = new Vec3d(safe.getX() + 0.5, safe.getY() + 0.5, safe.getZ() + 0.5);
                    if (!wasKnown) {
                        player.sendMessage(Text.literal(String.format(
                                "§a✓ Forteresse trouvée à §f%d, %d, %d",
                                safe.getX(), safe.getY(), safe.getZ())), false);
                    }
                } else {
                    player.sendMessage(Text.literal(
                            "§c✗ Aucune forteresse trouvée dans un rayon de 1600 blocs."), false);
                    return;
                }
            }
            default -> { return; }
        }

        // Force spectator (sauf creative)
        GameMode gm = player.interactionManager.getGameMode();
        if (gm != GameMode.CREATIVE && gm != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.SPECTATOR);
        }

        // === Pré-charge les chunks de destination AVANT le TP ===
        // Fix le bug "same-dim TP = chunks pas chargés / instance bugée" :
        // vanilla MC pour un same-dim teleport envoie juste un packet position au client,
        // sans recharger les chunks à destination. Si la destination est loin de la position
        // courante du joueur (ex: Overworld spawn -> Stronghold à 2000 blocs), les chunks
        // n'existent pas encore côté client → rendu chaotique.
        //
        // Solution : on charge synchroniquement les chunks de destination à FULL côté serveur,
        // puis on ajoute un ticket PORTAL pour les garder chargés ~10s après le TP.
        // Quand le client reçoit le packet TP, le serveur a déjà les chunks prêts à streamer.
        ChunkPos targetChunk = new ChunkPos(BlockPos.ofFloored(targetPos));
        try {
            // 3x3 chunks autour de la destination, à FULL status synchrone
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    targetWorld.getChunkManager().getChunk(
                            targetChunk.x + dx,
                            targetChunk.z + dz,
                            ChunkStatus.FULL,
                            true // create/load si nécessaire
                    );
                }
            }
            // Ticket pour garder chargé après le TP (auto-expire)
            targetWorld.getChunkManager().addTicket(ChunkTicketType.PORTAL, targetChunk, 3);
        } catch (Throwable t) {
            System.err.println("[SharedRun] chunk preload failed: " + t.getMessage());
            // pas fatal, on tente le TP quand même
        }

        // TP via la méthode vanilla (gère chunk loading + cross-dim correctement, pas de freeze)
        boolean ok = false;
        try {
            ok = player.teleport(
                    targetWorld,
                    targetPos.x, targetPos.y, targetPos.z,
                    EnumSet.noneOf(PositionFlag.class), // coords absolues
                    player.getYaw(), player.getPitch(),
                    true // resetCamera
            );
        } catch (Throwable t) {
            System.err.println("[SharedRun] teleport failed: " + t.getMessage());
            t.printStackTrace();
            player.sendMessage(Text.literal("§cTP échoué : " + t.getMessage()), false);
            return;
        }

        if (!ok) {
            player.sendMessage(Text.literal("§cTP refusé par le serveur."), false);
            return;
        }

        String destLabel = switch (dest) {
            case 1 -> "🌳 Overworld";
            case 2 -> "🔥 Nether";
            case 3 -> "🏛 Stronghold";
            case 4 -> "🐲 End";
            case 5 -> "🏰 Forteresse";
            default -> "?";
        };
        // Confirmation discrète en action bar + ré-affichage du menu en chat (pour switch facile)
        player.sendMessage(Text.literal("§7→ §f" + destLabel), true);
        broadcastDebriefMenuTo(player);
    }

    /** Ré-envoie le menu cliquable à un seul joueur (après un TP par ex.). */
    private static void broadcastDebriefMenuTo(ServerPlayerEntity p) {
        Text divider = Text.literal("§8§m──────────────");
        MutableText buttons = Text.literal("  ")
                .append(buildCustomLink("§a[🌳]", "tp_overworld", "Overworld"))
                .append(Text.literal(" "))
                .append(buildCustomLink("§c[🔥]", "tp_nether", "Nether"))
                .append(Text.literal(" "))
                .append(buildCustomLink("§4[🏰]", "tp_fortress", "Forteresse Nether"))
                .append(Text.literal(" "))
                .append(buildCustomLink("§6[🏛]", "tp_stronghold", "Stronghold"))
                .append(Text.literal(" "))
                .append(buildCustomLink("§5[🐲]", "tp_end", "End"))
                .append(Text.literal("   "))
                .append(buildCustomLink("§e[📋]", "debrief", "Rouvrir le récap"));
        p.sendMessage(divider, false);
        p.sendMessage(buttons, false);
    }

    /**
     * Locate le stronghold le plus proche via la mécanique vanilla (le même tag que
     * les yeux d'ender utilisent pour pointer vers le stronghold).
     * Cache le résultat dans SharedRunState pour les TP suivants.
     */
    private static BlockPos locateNearestStronghold(ServerWorld overworld, ServerPlayerEntity feedbackTarget) {
        try {
            BlockPos origin = overworld.getSpawnPoint().getPos();
            // 200 chunks = 3200 blocs. Les strongholds vanilla sont dans un rayon ~2800.
            BlockPos found = overworld.locateStructure(StructureTags.EYE_OF_ENDER_LOCATED, origin, 200, false);
            if (found != null) {
                SharedRunState.strongholdPos = found.asLong();
            }
            return found;
        } catch (Exception e) {
            System.err.println("[SharedRun] locateStructure failed: " + e.getMessage());
            if (feedbackTarget != null) {
                feedbackTarget.sendMessage(Text.literal(
                        "§cErreur de locate : " + e.getMessage()), false);
            }
            return null;
        }
    }

    /**
     * Trouve un Y safe dans le Nether en partant des coords XZ données.
     * Vanilla locateStructure retourne souvent un BlockPos avec Y=0 (chunk origin),
     * ce qui TP le joueur tout en bas du Nether. On scanne de Y=95 vers le bas
     * pour trouver le premier bloc solide non-bedrock (probablement un nether brick
     * de la forteresse ou la surface du netherrack), et on retourne Y+1 (au-dessus).
     * Fallback : Y=70 (niveau typique des ponts de forteresse).
     */
    private static BlockPos findSafeNetherY(ServerWorld netherWorld, BlockPos rawPos) {
        // Si le Y est déjà raisonnable (capturé via blaze rod pickup), on le garde
        if (rawPos.getY() >= 25 && rawPos.getY() <= 110) {
            return rawPos;
        }
        int x = rawPos.getX();
        int z = rawPos.getZ();
        BlockPos.Mutable check = new BlockPos.Mutable();
        // Scan top-down : 95 (sous le plafond bedrock) → 25 (au-dessus du sol bedrock)
        for (int y = 95; y >= 25; y--) {
            check.set(x, y, z);
            BlockState state = netherWorld.getBlockState(check);
            if (!state.isAir() && !state.isOf(Blocks.BEDROCK) && !state.isOf(Blocks.LAVA)) {
                return new BlockPos(x, y + 1, z); // un bloc au-dessus du solide
            }
        }
        return new BlockPos(x, 70, z); // fallback : altitude typique fortress
    }

    /**
     * Locate la forteresse du Nether la plus proche via le chunk generator
     * (pas de StructureTag pour fortress en vanilla, donc on passe par StructureKeys.FORTRESS).
     */
    private static BlockPos locateNearestFortress(ServerWorld netherWorld, ServerPlayerEntity feedbackTarget) {
        try {
            BlockPos origin = feedbackTarget != null ? feedbackTarget.getBlockPos() : BlockPos.ORIGIN;
            // Si le joueur est en overworld, scaled vers le nether
            if (feedbackTarget != null
                    && feedbackTarget.getEntityWorld().getRegistryKey() != World.NETHER) {
                origin = new BlockPos(origin.getX() / 8, 64, origin.getZ() / 8);
            }

            var structureRegistry = netherWorld.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            RegistryEntry.Reference<Structure> entry;
            try {
                entry = structureRegistry.getOrThrow(StructureKeys.FORTRESS);
            } catch (Throwable t) {
                System.err.println("[SharedRun] fortress structure not in registry: " + t.getMessage());
                return null;
            }

            RegistryEntryList<Structure> list = RegistryEntryList.of(entry);

            var result = netherWorld.getChunkManager().getChunkGenerator()
                    .locateStructure(netherWorld, list, origin, 100, false);

            if (result == null) return null;
            BlockPos found = result.getFirst();
            if (found != null) {
                SharedRunState.fortressPos = found.asLong();
            }
            return found;
        } catch (Exception e) {
            System.err.println("[SharedRun] locateFortress failed: " + e.getMessage());
            if (feedbackTarget != null) {
                feedbackTarget.sendMessage(Text.literal(
                        "§cErreur de locate forteresse : " + e.getMessage()), false);
            }
            return null;
        }
    }

    private static void addMilestone(RunSummary s, String key, String iconItem, boolean reached, String by) {
        RunSummary.Milestone m = new RunSummary.Milestone();
        m.key = key;
        m.iconItem = iconItem;
        m.reached = reached;
        m.by = by;
        s.milestones.add(m);
    }

    private static void countContribution(Map<String, Integer> map, String name) {
        if (name == null || name.isEmpty()) return;
        map.merge(name, 1, Integer::sum);
    }
}
