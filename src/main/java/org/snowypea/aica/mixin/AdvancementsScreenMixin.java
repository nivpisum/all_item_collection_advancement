package org.snowypea.aica.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.snowypea.aica.client.AicaAdvancementSupport;
import org.snowypea.aica.client.AicaAdvancementViewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = AdvancementsScreen.class, remap = false)
public abstract class AdvancementsScreenMixin {
    // ClickableAdvancements widens this field to public: Mixin then forbids aliases.
    @Unique private static final Field aica$selectedTab = AicaAdvancementSupport.vanillaField(
            AdvancementsScreen.class, "f_97336_", "selectedTab");
    @Shadow(remap = false) private static int tabPage;

    @Inject(method = {"removed", "m_7861_"}, at = @At("HEAD"), remap = false)
    private void aica$rememberView(CallbackInfo ci) {
        AicaAdvancementViewport.rememberScreen(this);
    }

    @Inject(method = {"onSelectedTabChanged", "m_6896_"}, at = @At("RETURN"), remap = false)
    private void aica$showSelectedPage(Advancement advancement, CallbackInfo ci) {
        try {
            AdvancementTab selectedTab = (AdvancementTab) aica$selectedTab.get(this);
            if (selectedTab != null) {
                tabPage = selectedTab.getPage();
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read selected advancement tab", exception);
        }
    }
}
