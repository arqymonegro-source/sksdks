
package com.argguy.client;

import com.argguy.ArgGuyMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ArgGuyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(
                ArgGuyModel.LAYER,
                ArgGuyModel::createBodyLayer
        );

        EntityRendererRegistry.register(
                ArgGuyMod.ARGGUY,
                ArgGuyRenderer::new
        );
    }
}
