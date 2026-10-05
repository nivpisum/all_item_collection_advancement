package org.snowypea.aica.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.snowypea.aica.client.AicaAdvancementSupport;
import org.snowypea.aica.client.AicaAdvancementViewport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = AdvancementTab.class, remap = false)
public abstract class AdvancementTabMixin {
    @Shadow(aliases = "f_97130_", remap = false) @Final private Advancement advancement;
    @Shadow(aliases = "f_97136_", remap = false) private double scrollX;
    @Shadow(aliases = "f_97137_", remap = false) private double scrollY;
    // ClickableAdvancements exposes all four bounds with an access transformer.
    @Unique private static final Field aica$minX = AicaAdvancementSupport.vanillaField(
            AdvancementTab.class, "f_97138_", "minX");
    @Unique private static final Field aica$minY = AicaAdvancementSupport.vanillaField(
            AdvancementTab.class, "f_97139_", "minY");
    @Unique private static final Field aica$maxX = AicaAdvancementSupport.vanillaField(
            AdvancementTab.class, "f_97140_", "maxX");
    @Unique private static final Field aica$maxY = AicaAdvancementSupport.vanillaField(
            AdvancementTab.class, "f_97141_", "maxY");
    @Shadow(aliases = "f_97143_", remap = false) private boolean centered;

    @Inject(method = {"drawContents", "m_280047_"}, at = @At("HEAD"), remap = false)
    private void aica$initialFrame(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (!centered) {
            AicaAdvancementViewport.restoreTab(this);
        }
        if (!centered && AicaAdvancementSupport.isCatalogRoot(advancement)) {
            try {
                scrollX = AicaAdvancementSupport.initialHorizontal(
                        aica$minX.getInt(this), aica$maxX.getInt(this), 234);
                scrollY = AicaAdvancementSupport.initialVertical(
                        aica$minY.getInt(this), aica$maxY.getInt(this), 113,
                        AicaAdvancementSupport.rootPixelY(advancement));
                centered = true;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot read advancement tab bounds", exception);
            }
        }
    }
}
