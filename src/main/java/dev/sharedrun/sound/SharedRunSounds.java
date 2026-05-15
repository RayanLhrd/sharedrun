package dev.sharedrun.sound;

import dev.sharedrun.SharedRun;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Registre des sons custom du mod.
 */
public final class SharedRunSounds {

    /** Son "Game Over" Super Mario joué au début de la cinématique de mort. */
    public static final SoundEvent DEATH_GAMEOVER = register("death_gameover");

    /** Thème de victoire joué pendant VictoryCinematicScreen (fourni par l'utilisateur). */
    public static final SoundEvent VICTORY_THEME = register("victory_theme");

    private SharedRunSounds() {}

    private static SoundEvent register(String name) {
        Identifier id = SharedRun.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Force l'initialisation de la classe (registration des sons) au démarrage. */
    public static void init() {}
}
