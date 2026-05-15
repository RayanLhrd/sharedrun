package dev.sharedrun.achievement;

import dev.sharedrun.state.SharedRunState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;

import java.util.HashMap;
import java.util.Map;

public final class AchievementTracker {
    private static final Map<String, String> achievedBy = new HashMap<>();

    private AchievementTracker() {}

    public static void tick(MinecraftServer server) {
        if (!SharedRunState.timerRunning) return;
        long t = server.getTicks();
        if (t % 10 == 0) scanInventories(server);
        if (t % 20 == 0) scanWorldFeatures(server);
    }

    private static void scanInventories(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;

            boolean ironHelm = false, ironChest = false, ironLegs = false, ironBoots = false;

            PlayerInventory inv = p.getInventory();
            int size = inv.size();
            for (int i = 0; i < size; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isEmpty()) continue;

                if (s.isOf(Items.OBSIDIAN))                    tryUnlock("first_obsidian", p, SoundEvents.BLOCK_PORTAL_TRIGGER, 0.8f);
                else if (s.isOf(Items.FLINT_AND_STEEL))        tryUnlock("first_flint_steel", p, SoundEvents.ITEM_FLINTANDSTEEL_USE, 1.0f);
                else if (s.isOf(Items.IRON_PICKAXE))           tryUnlock("first_iron_pickaxe", p, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 1.0f);
                else if (s.isOf(Items.DIAMOND_PICKAXE))        tryUnlock("first_diamond_pickaxe", p, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.5f);
                else if (s.isOf(Items.DIAMOND_SWORD))          tryUnlock("first_diamond_sword", p, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.7f);
                else if (s.isOf(Items.GOLD_INGOT))             tryUnlock("first_gold_ingot", p, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f);
                else if (s.isOf(Items.GOLDEN_APPLE))           tryUnlock("first_golden_apple", p, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.5f);
                else if (s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) tryUnlock("first_golden_apple", p, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.5f);
                else if (s.isOf(Items.WITHER_SKELETON_SKULL))  tryUnlock("first_wither_skull", p, SoundEvents.ENTITY_WITHER_SPAWN, 0.8f);
                else if (s.isIn(ItemTags.BEDS))                tryUnlock("first_bed", p, SoundEvents.BLOCK_WOOL_PLACE, 1.0f);

                if (s.isOf(Items.IRON_HELMET)) ironHelm = true;
                else if (s.isOf(Items.IRON_CHESTPLATE)) ironChest = true;
                else if (s.isOf(Items.IRON_LEGGINGS)) ironLegs = true;
                else if (s.isOf(Items.IRON_BOOTS)) ironBoots = true;
            }

            if (ironHelm && ironChest && ironLegs && ironBoots) {
                tryUnlock("first_iron_armor", p, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 1.3f);
            }
        }
    }

    private static void scanWorldFeatures(MinecraftServer server) {
        boolean needPortal = !achievedBy.containsKey("first_nether_portal_built");
        boolean needStronghold = !achievedBy.containsKey("first_stronghold");
        boolean needWarped = !achievedBy.containsKey("first_warped_forest");

        if (!needPortal && !needStronghold && !needWarped) return;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            ServerWorld world = (ServerWorld) p.getEntityWorld();
            var dim = world.getRegistryKey();

            if (dim == World.OVERWORLD && (needPortal || needStronghold)) {
                BlockPos center = p.getBlockPos();
                BlockPos.Mutable check = new BlockPos.Mutable();
                int r = 8;
                boolean foundPortal = !needPortal;
                boolean foundStronghold = !needStronghold;
                for (int dx = -r; dx <= r && !(foundPortal && foundStronghold); dx++) {
                    for (int dy = -r; dy <= r && !(foundPortal && foundStronghold); dy++) {
                        for (int dz = -r; dz <= r && !(foundPortal && foundStronghold); dz++) {
                            check.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                            BlockState state = world.getBlockState(check);
                            if (!foundPortal && state.isOf(Blocks.NETHER_PORTAL)) {
                                tryUnlock("first_nether_portal_built", p, SoundEvents.BLOCK_PORTAL_TRIGGER, 1.0f);
                                foundPortal = true;
                            }
                            if (!foundStronghold && state.isOf(Blocks.END_PORTAL_FRAME)) {
                                tryUnlock("first_stronghold", p, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, 1.0f);
                                if (SharedRunState.strongholdPos == Long.MIN_VALUE) {
                                    SharedRunState.strongholdPos = check.toImmutable().asLong();
                                }
                                foundStronghold = true;
                            }
                        }
                    }
                }
            } else if (dim == World.NETHER && needWarped) {
                BlockPos pos = p.getBlockPos();
                var biomeEntry = world.getBiome(pos);
                if (biomeEntry.matchesKey(BiomeKeys.WARPED_FOREST)) {
                    tryUnlock("first_warped_forest", p, SoundEvents.ENTITY_ENDERMAN_AMBIENT, 1.0f);
                }
            }
        }
    }

    public static void onPlayerDeath(ServerPlayerEntity player) {
        if (!SharedRunState.timerRunning) return;
        tryUnlock("first_death", player, SoundEvents.ENTITY_WITHER_DEATH, 0.7f);
    }

    private static void tryUnlock(String key, ServerPlayerEntity p, SoundEvent sound, float pitch) {
        if (achievedBy.containsKey(key)) return;
        achievedBy.put(key, p.getName().getString());
        announce(p.getEntityWorld().getServer(), key, p.getName().getString(), sound, pitch);
    }

    private static void announce(MinecraftServer server, String key, String byName, SoundEvent sound, float pitch) {
        if (server == null) return;
        Text msg = Text.translatable("sharedrun.achievement." + key, byName)
                .formatted(Formatting.GOLD, Formatting.BOLD);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    sound, SoundCategory.MASTER, 0.5f, pitch);
        }
    }

    public static void reset() {
        achievedBy.clear();
    }

    public static int getAchievedCount() {
        return achievedBy.size();
    }

    public static int getTotalCount() {
        return 14;
    }

    public static Map<String, String> getAchievedBy() {
        return java.util.Collections.unmodifiableMap(achievedBy);
    }

    public static void writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var entry : achievedBy.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("k", entry.getKey());
            c.putString("v", entry.getValue());
            list.add(c);
        }
        nbt.put("achievements", list);
    }

    public static void readNbt(NbtCompound nbt) {
        achievedBy.clear();
        nbt.getList("achievements").ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
                NbtElement el = list.get(i);
                if (!(el instanceof NbtCompound c)) continue;
                String k = c.getString("k").orElse(null);
                String v = c.getString("v").orElse(null);
                if (k != null && v != null) achievedBy.put(k, v);
            }
        });
    }
}
