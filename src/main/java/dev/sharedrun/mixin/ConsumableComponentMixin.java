package dev.sharedrun.mixin;

import dev.sharedrun.stats.FoodEatTracker;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World; // unused but kept for method signature match
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook sur la fin de consommation d'un item consommable (nourriture, potion qui se mange).
 * On track le joueur + l'item pour les stats bonus/malus de RunSummaryScreen.
 *
 * `finishConsumption` est appelé après que l'item ait fini d'être consommé (animation terminée).
 */
@Mixin(ConsumableComponent.class)
public abstract class ConsumableComponentMixin {

    @Inject(method = "finishConsumption", at = @At("HEAD"))
    private void sharedrun$trackEat(World world, LivingEntity user, ItemStack stack,
                                     CallbackInfoReturnable<ItemStack> cir) {
        // ServerPlayerEntity check garantit qu'on est côté serveur (les ClientPlayerEntity ne match pas)
        if (user instanceof ServerPlayerEntity player) {
            FoodEatTracker.recordEat(player, stack);
        }
    }
}
