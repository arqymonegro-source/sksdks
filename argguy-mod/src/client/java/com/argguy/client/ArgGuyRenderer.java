package com.argguy.client;

import com.argguy.ArgGuyEntity;
import com.argguy.ArgGuyMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class ArgGuyRenderer extends MobRenderer<ArgGuyEntity, ArgGuyRenderState, ArgGuyModel> {
    private static final Identifier TEXTURE = ArgGuyMod.id("textures/entity/argguy.png");

    public ArgGuyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ArgGuyModel(ctx.bakeLayer(ArgGuyModel.LAYER)), 0.6F);
    }

    @Override
    public ArgGuyRenderState createRenderState() {
        return new ArgGuyRenderState();
    }

    @Override
    public void extractRenderState(ArgGuyEntity entity, ArgGuyRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.mutilated = entity.isAggressive();
    }

    @Override
    public Identifier getTextureLocation(ArgGuyRenderState state) {
        return TEXTURE;
    }
}
