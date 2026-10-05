package org.snowypea.aica.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.snowypea.aica.client.AicaCreativeSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** IIG only rebuilds groups for CATEGORY tabs; utility selections still need cleanup. */
@Mixin(value = CreativeModeInventoryScreen.class, remap = false)
public abstract class CreativeScreenMixin {
    // Both names are pinned by the local official→SRG 1.20.1 mapping.
    @Inject(method = {"selectTab", "m_98560_"}, at = @At("HEAD"), remap = false)
    private void aica$selectTab(CreativeModeTab tab, CallbackInfo ci) {
        AicaCreativeSupport.onTabSelected(tab);
    }
}
