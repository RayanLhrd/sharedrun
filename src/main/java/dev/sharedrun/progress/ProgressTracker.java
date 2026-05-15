package dev.sharedrun.progress;

import dev.sharedrun.network.ProgressSyncPayload;
import dev.sharedrun.state.SharedRunState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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

public final class ProgressTracker {
    public static volatile boolean foodReached = false;
    public static volatile boolean ironReached = false;
    public static volatile boolean diamondReached = false;
    public static volatile boolean netherReached = false;
    public static volatile boolean blazeReached = false;
    public static volatile boolean pearlReached = false;
    public static volatile boolean eyeReached = false;
    public static volatile boolean endReached = false;
    public static volatile boolean dragonKilled = false;

    public static volatile int foodCount = 0;
    public static volatile int ironIngotCount = 0;
    public static volatile int diamondCount = 0;
    public static volatile int blazeRodCount = 0;
    public static volatile int enderPearlCount = 0;
    public static volatile int eyeOfEnderCount = 0;

    public static volatile String byFood = null;
    public static volatile String byIron = null;
    public static volatile String byDiamond = null;
    public static volatile String byNether = null;
    public static volatile String byBlaze = null;
    public static volatile String byPearl = null;
    public static volatile String byEye = null;
    public static volatile String byEnd = null;
    public static volatile String byDragon = null;

    private static boolean prevFood = false;
    private static boolean prevIron = false;
    private static boolean prevDiamond = false;
    private static boolean prevNether = false;
    private static boolean prevBlaze = false;
    private static boolean prevPearl = false;
    private static boolean prevEye = false;
    private static boolean prevEnd = false;
    private static boolean prevDragon = false;

    private ProgressTracker() {}

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 == 0) {
            scanInventories(server);
            scanForFilledPortalFrames(server);
            checkAndAnnounce(server);
            broadcast(server);
        }
    }

    /**
     * Scanne autour des joueurs dans l'Overworld pour détecter au moins un End Portal Frame
     * dont l'œil a été placé. Déclenche l'objectif "End" (avant : entrer dans la dim End).
     */
    private static void scanForFilledPortalFrames(MinecraftServer server) {
        if (endReached) return; // déjà trigger, plus la peine de scanner
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            ServerWorld world = (ServerWorld) p.getEntityWorld();
            if (world.getRegistryKey() != World.OVERWORLD) continue;

            BlockPos center = p.getBlockPos();
            BlockPos.Mutable check = new BlockPos.Mutable();
            int r = 8;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        check.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        BlockState state = world.getBlockState(check);
                        if (state.isOf(Blocks.END_PORTAL_FRAME) && state.get(EndPortalFrameBlock.EYE)) {
                            endReached = true;
                            byEnd = p.getName().getString();
                            return; // sort dès qu'un seul œil est trouvé
                        }
                    }
                }
            }
        }
    }

    private static void scanInventories(MinecraftServer server) {
        int food = 0, iron = 0, diamonds = 0, blaze = 0, pearls = 0, eyes = 0;
        String firstFood = null, firstIron = null, firstDiamond = null,
               firstBlaze = null, firstPearl = null, firstEye = null;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            PlayerInventory inv = p.getInventory();
            int size = inv.size();

            int pFood = 0, pIron = 0, pDiamond = 0, pBlaze = 0, pPearl = 0, pEye = 0;

            for (int i = 0; i < size; i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty()) continue;

                // Food : tout item avec un FoodComponent, sauf rotten flesh
                if (!stack.isOf(Items.ROTTEN_FLESH) && stack.contains(DataComponentTypes.FOOD)) {
                    pFood += stack.getCount();
                }

                if (stack.isOf(Items.IRON_INGOT)) pIron += stack.getCount();
                else if (stack.isOf(Items.DIAMOND)) pDiamond += stack.getCount();
                else if (stack.isOf(Items.BLAZE_ROD)) pBlaze += stack.getCount();
                else if (stack.isOf(Items.ENDER_PEARL)) pPearl += stack.getCount();
                else if (stack.isOf(Items.ENDER_EYE)) pEye += stack.getCount();
            }

            String name = p.getName().getString();
            if (pFood > 0 && firstFood == null) firstFood = name;
            if (pIron > 0 && firstIron == null) firstIron = name;
            if (pDiamond > 0 && firstDiamond == null) firstDiamond = name;
            if (pBlaze > 0 && firstBlaze == null) {
                firstBlaze = name;
                // Capture la position du joueur si il est dans le Nether → c'est probablement
                // au cœur d'une forteresse (où sont les blaze spawners). Utilisé pour le TP debrief.
                if (SharedRunState.fortressPos == Long.MIN_VALUE
                        && p.getEntityWorld().getRegistryKey() == World.NETHER) {
                    SharedRunState.fortressPos = p.getBlockPos().asLong();
                }
            }
            if (pPearl > 0 && firstPearl == null) firstPearl = name;
            if (pEye > 0 && firstEye == null) firstEye = name;

            food += pFood;
            iron += pIron;
            diamonds += pDiamond;
            blaze += pBlaze;
            pearls += pPearl;
            eyes += pEye;
        }

        if (firstFood != null && !foodReached)       { foodReached = true; byFood = firstFood; }
        if (firstIron != null && !ironReached)       { ironReached = true; byIron = firstIron; }
        if (firstDiamond != null && !diamondReached) { diamondReached = true; byDiamond = firstDiamond; }
        if (firstBlaze != null && !blazeReached)     { blazeReached = true; byBlaze = firstBlaze; }
        if (firstPearl != null && !pearlReached)     { pearlReached = true; byPearl = firstPearl; }
        if (firstEye != null && !eyeReached)         { eyeReached = true; byEye = firstEye; }

        foodCount = food;
        ironIngotCount = iron;
        diamondCount = diamonds;
        blazeRodCount = blaze;
        enderPearlCount = pearls;
        eyeOfEnderCount = eyes;
    }

    private static void checkAndAnnounce(MinecraftServer server) {
        if (!SharedRunState.timerRunning) {
            prevFood = foodReached;
            prevIron = ironReached;
            prevDiamond = diamondReached;
            prevNether = netherReached;
            prevBlaze = blazeReached;
            prevPearl = pearlReached;
            prevEye = eyeReached;
            prevEnd = endReached;
            prevDragon = dragonKilled;
            return;
        }

        if (foodReached && !prevFood) {
            announce(server, "sharedrun.progress.food", byFood, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f);
            prevFood = true;
        }
        if (ironReached && !prevIron) {
            announce(server, "sharedrun.progress.iron", byIron, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
            prevIron = true;
        }
        if (diamondReached && !prevDiamond) {
            announce(server, "sharedrun.progress.diamond", byDiamond, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.15f);
            prevDiamond = true;
        }
        if (netherReached && !prevNether) {
            announce(server, "sharedrun.progress.nether", byNether, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.05f);
            prevNether = true;
        }
        if (blazeReached && !prevBlaze) {
            announce(server, "sharedrun.progress.blaze", byBlaze, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f);
            prevBlaze = true;
        }
        if (pearlReached && !prevPearl) {
            announce(server, "sharedrun.progress.pearl", byPearl, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.25f);
            prevPearl = true;
        }
        if (eyeReached && !prevEye) {
            announce(server, "sharedrun.progress.eye", byEye, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.35f);
            prevEye = true;
        }
        if (endReached && !prevEnd) {
            announce(server, "sharedrun.progress.end", byEnd, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.5f);
            prevEnd = true;
        }
        if (dragonKilled && !prevDragon) {
            prevDragon = true;
        }
    }

    private static void announce(MinecraftServer server, String key, String byName, SoundEvent sound, float pitch) {
        String name = (byName != null && !byName.isEmpty()) ? byName : "?";
        Text msg = Text.translatable(key, name).formatted(Formatting.AQUA, Formatting.BOLD);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    sound, SoundCategory.MASTER, 0.6f, pitch);
        }
    }

    public static void onDimensionChanged(ServerPlayerEntity player, ServerWorld destination) {
        String name = player.getName().getString();
        if (destination.getRegistryKey() == World.NETHER) {
            if (!netherReached) {
                netherReached = true;
                byNether = name;
                SharedRunState.netherEntryPos = player.getBlockPos().asLong();
            }
        } else if (destination.getRegistryKey() == World.END) {
            // L'objectif "End" est désormais déclenché par scanForFilledPortalFrames
            // (placement d'au moins un œil dans un frame), pas par l'entrée dans la dim.
            // On garde néanmoins la capture de la position d'entrée pour le TP debrief.
            if (SharedRunState.endEntryPos == Long.MIN_VALUE) {
                SharedRunState.endEntryPos = player.getBlockPos().asLong();
            }
        }
    }

    public static void onDragonKilled(ServerPlayerEntity killer) {
        dragonKilled = true;
        if (killer != null && byDragon == null) {
            byDragon = killer.getName().getString();
        }
    }

    public static void reset() {
        foodReached = false;
        ironReached = false;
        diamondReached = false;
        netherReached = false;
        blazeReached = false;
        pearlReached = false;
        eyeReached = false;
        endReached = false;
        dragonKilled = false;

        foodCount = 0;
        ironIngotCount = 0;
        diamondCount = 0;
        blazeRodCount = 0;
        enderPearlCount = 0;
        eyeOfEnderCount = 0;

        byFood = null;
        byIron = null;
        byDiamond = null;
        byNether = null;
        byBlaze = null;
        byPearl = null;
        byEye = null;
        byEnd = null;
        byDragon = null;

        prevFood = false;
        prevIron = false;
        prevDiamond = false;
        prevNether = false;
        prevBlaze = false;
        prevPearl = false;
        prevEye = false;
        prevEnd = false;
        prevDragon = false;
    }

    public static void broadcast(MinecraftServer server) {
        ProgressSyncPayload payload = currentPayload();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void broadcastTo(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, currentPayload());
    }

    private static ProgressSyncPayload currentPayload() {
        int flags = 0;
        if (foodReached)    flags |= 1;
        if (ironReached)    flags |= 2;
        if (diamondReached) flags |= 4;
        if (netherReached)  flags |= 8;
        if (blazeReached)   flags |= 16;
        if (pearlReached)   flags |= 32;
        if (eyeReached)     flags |= 64;
        if (endReached)     flags |= 128;
        if (dragonKilled)   flags |= 256;
        return new ProgressSyncPayload(flags, foodCount, ironIngotCount, diamondCount,
                blazeRodCount, enderPearlCount, eyeOfEnderCount);
    }

    public static void writeNbt(NbtCompound nbt) {
        nbt.putBoolean("progressFood", foodReached);
        nbt.putInt("progressFoodCount", foodCount);
        nbt.putBoolean("progressIron", ironReached);
        nbt.putInt("progressIronIngot", ironIngotCount);
        nbt.putBoolean("progressDiamondLatch", diamondReached);
        nbt.putInt("progressDiamond", diamondCount);
        nbt.putBoolean("progressNether", netherReached);
        nbt.putBoolean("progressBlazeLatch", blazeReached);
        nbt.putInt("progressBlazeRod", blazeRodCount);
        nbt.putBoolean("progressPearlLatch", pearlReached);
        nbt.putInt("progressEnderPearl", enderPearlCount);
        nbt.putBoolean("progressEyeLatch", eyeReached);
        nbt.putInt("progressEnderEye", eyeOfEnderCount);
        nbt.putBoolean("progressEnd", endReached);
        nbt.putBoolean("progressDragon", dragonKilled);

        if (byFood != null)    nbt.putString("progressByFood", byFood);
        if (byIron != null)    nbt.putString("progressByIron", byIron);
        if (byDiamond != null) nbt.putString("progressByDiamond", byDiamond);
        if (byNether != null)  nbt.putString("progressByNether", byNether);
        if (byBlaze != null)   nbt.putString("progressByBlaze", byBlaze);
        if (byPearl != null)   nbt.putString("progressByPearl", byPearl);
        if (byEye != null)     nbt.putString("progressByEye", byEye);
        if (byEnd != null)     nbt.putString("progressByEnd", byEnd);
        if (byDragon != null)  nbt.putString("progressByDragon", byDragon);
    }

    public static void readNbt(NbtCompound nbt) {
        foodReached      = nbt.getBoolean("progressFood").orElse(false);
        foodCount        = nbt.getInt("progressFoodCount").orElse(0);
        ironReached      = nbt.getBoolean("progressIron").orElse(false);
        ironIngotCount   = nbt.getInt("progressIronIngot").orElse(0);
        diamondReached   = nbt.getBoolean("progressDiamondLatch").orElse(false);
        diamondCount     = nbt.getInt("progressDiamond").orElse(0);
        netherReached    = nbt.getBoolean("progressNether").orElse(false);
        blazeReached     = nbt.getBoolean("progressBlazeLatch").orElse(false);
        blazeRodCount    = nbt.getInt("progressBlazeRod").orElse(
                                nbt.getInt("progressBlazePowder").orElse(0));
        pearlReached     = nbt.getBoolean("progressPearlLatch").orElse(false);
        enderPearlCount  = nbt.getInt("progressEnderPearl").orElse(0);
        eyeReached       = nbt.getBoolean("progressEyeLatch").orElse(false);
        eyeOfEnderCount  = nbt.getInt("progressEnderEye").orElse(0);
        endReached       = nbt.getBoolean("progressEnd").orElse(
                                nbt.getBoolean("progressStronghold").orElse(false));
        dragonKilled     = nbt.getBoolean("progressDragon").orElse(false);

        byFood    = nbt.getString("progressByFood").orElse(null);
        byIron    = nbt.getString("progressByIron").orElse(null);
        byDiamond = nbt.getString("progressByDiamond").orElse(null);
        byNether  = nbt.getString("progressByNether").orElse(null);
        byBlaze   = nbt.getString("progressByBlaze").orElse(null);
        byPearl   = nbt.getString("progressByPearl").orElse(null);
        byEye     = nbt.getString("progressByEye").orElse(null);
        byEnd     = nbt.getString("progressByEnd").orElse(null);
        byDragon  = nbt.getString("progressByDragon").orElse(null);

        prevFood = foodReached;
        prevIron = ironReached;
        prevDiamond = diamondReached;
        prevNether = netherReached;
        prevBlaze = blazeReached;
        prevPearl = pearlReached;
        prevEye = eyeReached;
        prevEnd = endReached;
        prevDragon = dragonKilled;
    }
}
