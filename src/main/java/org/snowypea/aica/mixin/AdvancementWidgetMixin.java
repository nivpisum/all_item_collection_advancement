package org.snowypea.aica.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.snowypea.aica.client.AicaAdvancementSupport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvancementWidget.class, remap = false)
public abstract class AdvancementWidgetMixin {
    @Shadow(aliases = "f_97251_", remap = false) @Final @Mutable private int x;
    @Shadow(aliases = "f_97252_", remap = false) @Final @Mutable private int y;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void aica$compactPosition(AdvancementTab tab, Minecraft minecraft, Advancement advancement,
                                      DisplayInfo display, CallbackInfo ci) {
        AicaAdvancementSupport.PresentationPosition point = AicaAdvancementSupport.position(advancement);
        if (point != null) {
            x = point.pixelX();
            y = point.pixelY();
        }
    }
}
