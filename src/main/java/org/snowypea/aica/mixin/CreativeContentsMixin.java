package org.snowypea.aica.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.snowypea.aica.client.AicaCreativeSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read native variant stacks after their generators run, before screen selection. */
@Mixin(value = CreativeModeTabs.class, remap = false)
public abstract class CreativeContentsMixin {
    // Official and SRG names are pinned by the local 1.20.1 mapping.
    @Inject(method = {"buildAllTabContents", "m_269421_"}, at = @At("RETURN"), remap = false)
    private static void aica$nativeContents(CreativeModeTab.ItemDisplayParameters parameters,
            CallbackInfo ci) {
        AicaCreativeSupport.onNativeContentsBuilt();
    }
}
