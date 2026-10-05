package org.snowypea.aica.mixin;

import net.minecraft.advancements.Advancement;
import org.snowypea.aica.client.AicaAdvancementViewport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementsScreen", remap = false)
public abstract class BetterAdvancementsScreenMixin {
    @Shadow(remap = false) @Final private Map<Advancement, ?> tabs;
    @Shadow(remap = false) protected int internalWidth;
    @Shadow(remap = false) protected int internalHeight;
    @Shadow(remap = false) private static int tabPage;

    @Inject(method = {"onClose", "m_7379_"}, at = @At("HEAD"), remap = false)
    private void aica$rememberView(CallbackInfo ci) {
        AicaAdvancementViewport.rememberScreen(this);
    }

    @Inject(method = {"onSelectedTabChanged", "m_6896_"}, at = @At("RETURN"), remap = false)
    private void aica$showSelectedPage(Advancement advancement, CallbackInfo ci) {
        if (!tabs.containsKey(advancement)) {
            return;
        }
        int pageSize = BetterAdvancementTabTypeAccessor.aica$getMaxTabs(
                internalWidth - 60, internalHeight - 70);
        int index = 0;
        for (Advancement root : tabs.keySet()) {
            if (root.equals(advancement)) {
                tabPage = index / Math.max(1, pageSize);
                return;
            }
            index++;
        }
    }
}
