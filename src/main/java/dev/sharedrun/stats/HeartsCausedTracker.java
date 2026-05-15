package dev.sharedrun.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HeartsCausedTracker {
    private static final ConcurrentHashMap<UUID, Float> hearts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> names = new ConcurrentHashMap<>();

    private HeartsCausedTracker() {}

    public static void addHpDamage(UUID uuid, String name, float hpAmount) {
        if (hpAmount <= 0f) return;
        hearts.merge(uuid, hpAmount / 2f, Float::sum);
        names.put(uuid, name);
    }

    public static float getHearts(UUID uuid) {
        Float v = hearts.get(uuid);
        return v == null ? 0f : v;
    }

    public static void resetAll() {
        hearts.clear();
        names.clear();
    }

    public record Entry(UUID uuid, String name, float hearts) {}

    public static List<Entry> sortedLeaderboard() {
        List<Entry> list = new ArrayList<>();
        for (var e : hearts.entrySet()) {
            String n = names.getOrDefault(e.getKey(), e.getKey().toString().substring(0, 8));
            list.add(new Entry(e.getKey(), n, e.getValue()));
        }
        list.sort(Comparator.comparingDouble((Entry x) -> -x.hearts));
        return list;
    }
}
