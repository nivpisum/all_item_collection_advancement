package org.snowypea.aica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementTabType", remap = false)
public interface BetterAdvancementTabTypeAccessor {
    @Invoker(value = "getMaxTabs", remap = false)
    static int aica$getMaxTabs(int width, int height) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
