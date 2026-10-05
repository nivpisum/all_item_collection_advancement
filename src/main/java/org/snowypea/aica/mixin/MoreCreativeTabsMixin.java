package org.snowypea.aica.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.snowypea.aica.client.AicaCreativeSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(targets = "forge.me.hypherionmc.morecreativetabs.client.tabs.CustomCreativeTabRegistry",
        remap = false)
public abstract class MoreCreativeTabsMixin {
    @Inject(method = "processEntries", at = @At("RETURN"), remap = false)
    private static void aica$enforceTabs(Map<ResourceLocation, Resource> entries, CallbackInfo ci) {
        AicaCreativeSupport.enforceTabs();
    }
}
