package org.snowypea.aica.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.snowypea.aica.client.AicaAdvancementViewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** BetterAdvancements inherits this no-op; direct screen replacement still needs a snapshot. */
@Mixin(value = Screen.class, remap = false)
public abstract class AdvancementScreenRemovalMixin {
    @Inject(method = {"removed", "m_7861_"}, at = @At("HEAD"), remap = false)
    private void aica$rememberAdvancementView(CallbackInfo ci) {
        AicaAdvancementViewport.rememberScreen(this);
    }
}
