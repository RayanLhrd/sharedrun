package dev.sharedrun.stats;

import dev.sharedrun.state.SharedRunState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracker de consommation d'aliments par joueur pendant la run.
 *
 * Bonus  : nombre total d'aliments mangés (regen pour toute la team via hunger sync)
 * Malus  : nombre de chairs putréfiées mangées (debuff hunger pour tout le monde)
 *
 * Affiché dans l'écran de fin (RunSummary) comme "Top chef" / "Pire bouffeur".
 */
public final class FoodEatTracker {

    private static final Map<UUID, Integer> rottenFlesh = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> normalFood = new ConcurrentHashMap<>();
    private static final Map<UUID, String> names = new ConcurrentHashMap<>();

    private FoodEatTracker() {}

    /**
     * Appelé depuis le mixin ConsumableComponentMixin quand un joueur finit de manger un item.
     * Compte +1 pour chair putréfiée (malus), +1 pour autre aliment (bonus).
     */
    public static void recordEat(ServerPlayerEntity player, ItemStack stack) {
        if (!SharedRunState.timerRunning) return; // pas pendant le lobby / post-run
        if (stack == null || stack.isEmpty()) return;
        UUID uuid = player.getUuid();
        names.put(uuid, player.getName().getString());
        if (stack.isOf(Items.ROTTEN_FLESH)) {
            rottenFlesh.merge(uuid, 1, Integer::sum);
        } else {
            // Tout autre item consommable (pain, viande, pomme dorée, potion qui se mange...)
            normalFood.merge(uuid, 1, Integer::sum);
        }
    }

    /** @return l'entrée [name, count] avec le + de chairs mangées, ou null si personne n'en a mangé. */
    public static Entry topRottenFleshEater() {
        return topOf(rottenFlesh);
    }

    /** @return l'entrée [name, count] avec le + d'aliments normaux mangés, ou null si vide. */
    public static Entry topNormalFoodEater() {
        return topOf(normalFood);
    }

    private static Entry topOf(Map<UUID, Integer> map) {
        UUID best = null;
        int bestCount = 0;
        for (var e : map.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null) return null;
        return new Entry(names.getOrDefault(best, "?"), bestCount);
    }

    public static void reset() {
        rottenFlesh.clear();
        normalFood.clear();
        names.clear();
    }

    public record Entry(String name, int count) {}

    public static void writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (UUID uuid : names.keySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("uuid", uuid.toString());
            c.putString("name", names.get(uuid));
            c.putInt("rotten", rottenFlesh.getOrDefault(uuid, 0));
            c.putInt("normal", normalFood.getOrDefault(uuid, 0));
            list.add(c);
        }
        nbt.put("foodEat", list);
    }

    public static void readNbt(NbtCompound nbt) {
        reset();
        nbt.getList("foodEat").ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
                NbtElement el = list.get(i);
                if (!(el instanceof NbtCompound c)) continue;
                String uuidStr = c.getString("uuid").orElse(null);
                String name = c.getString("name").orElse("?");
                int rotten = c.getInt("rotten").orElse(0);
                int normal = c.getInt("normal").orElse(0);
                if (uuidStr == null) continue;
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    names.put(uuid, name);
                    if (rotten > 0) rottenFlesh.put(uuid, rotten);
                    if (normal > 0) normalFood.put(uuid, normal);
                } catch (IllegalArgumentException ignored) {}
            }
        });
    }
}
