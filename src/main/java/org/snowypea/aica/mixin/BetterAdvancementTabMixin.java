package org.snowypea.aica.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.GuiGraphics;
import org.snowypea.aica.client.AicaAdvancementSupport;
import org.snowypea.aica.client.AicaAdvancementViewport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/** Optional exact interface for BetterAdvancements Forge 0.6.0.73. */
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementTab", remap = false)
public abstract class BetterAdvancementTabMixin {
    @Shadow(remap = false) @Final private Advancement advancement;
    @Shadow(remap = false) protected int scrollX;
    @Shadow(remap = false) protected int scrollY;
    @Shadow(remap = false) private int minX;
    @Shadow(remap = false) private int minY;
    @Shadow(remap = false) private int maxX;
    @Shadow(remap = false) private int maxY;
    @Shadow(remap = false) private boolean centered;
    @Inject(method = "loadScroll", at = @At("RETURN"), remap = false)
    private void aica$retainView(int width, int height, CallbackInfo ci) {
        AicaAdvancementViewport.restoreTab(this);
    }

    @Inject(method = "storeScroll", at = @At("RETURN"), remap = false)
    private void aica$rememberView(CallbackInfo ci) {
        AicaAdvancementViewport.rememberTab(this);
    }

    @Inject(method = "drawContents", at = @At("HEAD"), remap = false)
    private void aica$initialFrame(GuiGraphics graphics, int x, int y, int width, int height,
                                   CallbackInfo ci) {
        // Respect BetterAdvancements' saved user pan; only replace first-open framing.
        if (!centered && AicaAdvancementSupport.isCatalogRoot(advancement)) {
            scrollX = (int) Math.round(AicaAdvancementSupport.initialHorizontal(minX, maxX, width));
            scrollY = (int) Math.round(AicaAdvancementSupport.initialVertical(minY, maxY, height,
                    AicaAdvancementSupport.rootPixelY(advancement)));
            centered = true;
        }
    }
}
