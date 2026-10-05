package org.snowypea.aica.mixin;

import net.minecraft.advancements.Advancement;
import org.snowypea.aica.client.AicaAdvancementSupport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementWidget", remap = false)
public abstract class BetterAdvancementWidgetMixin {
    @Shadow(remap = false) @Final private Advancement advancement;
    @Shadow(remap = false) protected int x;
    @Shadow(remap = false) protected int y;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void aica$compactPosition(CallbackInfo ci) {
        AicaAdvancementSupport.PresentationPosition point = AicaAdvancementSupport.position(advancement);
        if (point != null) {
            x = point.pixelX();
            y = point.pixelY();
        }
    }
}
