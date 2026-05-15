package dev.sharedrun.endrun;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO sérialisé en JSON pour le packet réseau RunSummaryPayload.
 * Construit côté serveur dans EndRunHandler, parsé côté client dans RunSummaryScreen.
 */
public class RunSummary {
    public int endReason;          // 0=VICTORY, 1=TIME_OUT, 2=ALL_DEAD
    public int elapsedSeconds;
    public int totalSeconds;
    public int deathTimeSec = -1;  // secondes écoulées au moment du décès (-1 si pas ALL_DEAD)
    public float hpLost;           // HP perdus sur la pool partagée (0–20)
    public String seed;            // seed de la map (String pour éviter la perte de précision JS sur long 64-bit)
    public boolean solo;           // true si partie solo
    public long eventTimestamp;    // Unix ms au moment de la fin de run
    public List<Milestone> milestones = new ArrayList<>();
    public List<Achievement> achievements = new ArrayList<>();
    public List<LeaderEntry> leaderboard = new ArrayList<>();
    public int swapCount;
    public String mvp;
    public int mvpContributions;
    public int playerCount;
    public int totalAchievements;
    public String deadPlayerName;   // pseudo du joueur qui a déclenché la cascade (ALL_DEAD only)
    public String deathReason;      // message de mort vanilla ("X est tombé de haut", etc.)
    public List<String> participants = new ArrayList<>();
    public String dragonKilledBy;   // pseudo du joueur qui a porté le coup fatal au dragon (VICTORY only)

    // Stats bonus / malus (du FoodEatTracker)
    public String topChef;
    public int topChefCount;
    public String topRotten;
    public int topRottenCount;

    public static class Milestone {
        public String key;
        public String iconItem;
        public boolean reached;
        public String by;
        public int timestampSec = -1; // secondes écoulées quand ce milestone a été atteint (-1 si non atteint)
    }

    public static class Achievement {
        public String key;
        public String by;
    }

    public static class LeaderEntry {
        public String name;
        public float hearts;
    }
}
