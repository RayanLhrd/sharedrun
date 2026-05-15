package dev.sharedrun.endrun;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO parsé depuis la réponse JSON de l'API GET /run.
 * Diffère de RunSummary : eventTimestamp est une String ISO 8601 (MongoDB sérialise Date → string),
 * pas un long.
 */
public class LeaderboardEntry {
    public int endReason;
    public int elapsedSeconds;
    public int totalSeconds;
    public int deathTimeSec = -1;
    public String seed;
    public boolean solo;
    public String eventTimestamp;
    public String mvp;
    public int mvpContributions;
    public int playerCount;
    public float hpLost;
    public int swapCount;
    public String dragonKilledBy;
    public String deadPlayerName;
    public String deathReason;
    public int totalAchievements;
    public String topChef;
    public int topChefCount;
    public String topRotten;
    public int topRottenCount;
    public List<String> participants;
    public List<Milestone> milestones;
    public List<Achievement> achievements;
    public List<LeaderEntry> leaderboard;

    public static class Milestone {
        public String key;
        public boolean reached;
        public String by;
        public int timestampSec = -1;
    }

    public static class Achievement {
        public String key;
        public String by;
    }

    public static class LeaderEntry {
        public String name;
        public float hearts;
    }

    /** Convertit cet entry en RunSummary pour affichage dans RunSummaryScreen. */
    public RunSummary toRunSummary() {
        RunSummary s = new RunSummary();
        s.endReason        = endReason;
        s.elapsedSeconds   = elapsedSeconds;
        s.totalSeconds     = totalSeconds;
        s.deathTimeSec     = deathTimeSec;
        s.seed             = seed;
        s.solo             = solo;
        s.eventTimestamp   = 0L; // ISO string → long non critique pour l'affichage
        s.mvp              = mvp;
        s.mvpContributions = mvpContributions;
        s.playerCount      = playerCount;
        s.hpLost           = hpLost;
        s.swapCount        = swapCount;
        s.dragonKilledBy   = dragonKilledBy;
        s.deadPlayerName   = deadPlayerName;
        s.deathReason      = deathReason;
        s.totalAchievements = totalAchievements;
        s.topChef          = topChef;
        s.topChefCount     = topChefCount;
        s.topRotten        = topRotten;
        s.topRottenCount   = topRottenCount;
        s.participants     = participants != null ? new ArrayList<>(participants) : new ArrayList<>();

        if (milestones != null) {
            for (Milestone m : milestones) {
                RunSummary.Milestone sm = new RunSummary.Milestone();
                sm.key         = m.key;
                sm.reached     = m.reached;
                sm.by          = m.by;
                sm.timestampSec = m.timestampSec;
                sm.iconItem    = iconForKey(m.key);
                s.milestones.add(sm);
            }
        }

        if (achievements != null) {
            for (Achievement a : achievements) {
                RunSummary.Achievement sa = new RunSummary.Achievement();
                sa.key = a.key;
                sa.by  = a.by;
                s.achievements.add(sa);
            }
        }

        if (leaderboard != null) {
            for (LeaderEntry e : leaderboard) {
                RunSummary.LeaderEntry se = new RunSummary.LeaderEntry();
                se.name   = e.name;
                se.hearts = e.hearts;
                s.leaderboard.add(se);
            }
        }

        return s;
    }

    private static String iconForKey(String key) {
        return switch (key != null ? key : "") {
            case "food"    -> "minecraft:bread";
            case "iron"    -> "minecraft:iron_ingot";
            case "diamond" -> "minecraft:diamond";
            case "nether"  -> "minecraft:netherrack";
            case "blaze"   -> "minecraft:blaze_rod";
            case "pearl"   -> "minecraft:ender_pearl";
            case "eye"     -> "minecraft:ender_eye";
            case "end"     -> "minecraft:end_portal_frame";
            case "dragon"  -> "minecraft:dragon_head";
            default        -> "minecraft:air";
        };
    }
}
