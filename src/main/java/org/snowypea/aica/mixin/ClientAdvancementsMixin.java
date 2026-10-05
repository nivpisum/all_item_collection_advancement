package org.snowypea.aica.mixin;

import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import org.snowypea.aica.client.AicaAdvancementSupport;
import org.snowypea.aica.client.AicaAdvancementViewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = ClientAdvancements.class, remap = false)
public abstract class ClientAdvancementsMixin {
    @Unique private Set<ResourceLocation> aica$previousNodes;

    // Explicit official/SRG names support both development and production without
    // an annotation-processor refmap. This integration targets MC 1.20.1 only.
    @Inject(method = {"setListener", "m_104397_"}, at = @At("HEAD"), remap = false)
    private void aica$orderRoots(ClientAdvancements.Listener listener, CallbackInfo ci) {
        if (listener != null) {
            ClientAdvancements client = (ClientAdvancements) (Object) this;
            AicaAdvancementSupport.orderRoots(client);
            AicaAdvancementSupport.prepareLayouts(client);
        }
    }

    @Inject(method = {"update", "m_104399_"}, at = @At("HEAD"), remap = false)
    private void aica$beforeUpdate(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        AicaAdvancementViewport.beforeUpdate((ClientAdvancements) (Object) this);
        aica$previousNodes = AicaAdvancementSupport.knownUiIds((ClientAdvancements) (Object) this);
    }

    @Inject(method = {"update", "m_104399_"}, at = @At("RETURN"), remap = false)
    private void aica$afterUpdate(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        ClientAdvancements client = (ClientAdvancements) (Object) this;
        if (packet.shouldReset() || !AicaAdvancementSupport.knownUiIds(client).equals(aica$previousNodes)) {
            AicaAdvancementSupport.orderRoots(client);
            AicaAdvancementSupport.prepareLayouts(client);
            AicaAdvancementSupport.refreshOpenScreen(client);
        } else {
            AicaAdvancementViewport.unchangedUpdate();
        }
        aica$previousNodes = null;
    }
}
