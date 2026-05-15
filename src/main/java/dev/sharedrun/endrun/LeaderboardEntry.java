package dev.sharedrun.endrun;

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
    public List<String> participants;
    public List<Milestone> milestones;

    public static class Milestone {
        public String key;
        public boolean reached;
        public String by;
        public int timestampSec = -1;
    }
}
