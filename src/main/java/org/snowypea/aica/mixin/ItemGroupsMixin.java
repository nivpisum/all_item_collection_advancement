package org.snowypea.aica.mixin;

import org.snowypea.aica.client.AicaCreativeSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.github.bizcub.inventoryItemGroups.Main", remap = false)
public abstract class ItemGroupsMixin {
    @Inject(method = "createGroups", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aica$createGroups(CallbackInfo ci) {
        if (AicaCreativeSupport.createAicaGroups()) {
            ci.cancel();
        }
    }
}
