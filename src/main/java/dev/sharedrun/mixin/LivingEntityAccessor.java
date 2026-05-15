package dev.sharedrun.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accesseur pour exposer le champ private/protected {@code deathTime} de LivingEntity.
 * Utilisé par DeathCinematicScreen pour forcer le rendu en pose normale du joueur mort.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("deathTime")
    int sharedrun$getDeathTime();

    @Accessor("deathTime")
    void sharedrun$setDeathTime(int value);
}
