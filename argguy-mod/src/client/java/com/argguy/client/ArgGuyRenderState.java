package com.argguy.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class ArgGuyRenderState extends LivingEntityRenderState {
    /** True while the entity is hunting: its model is "mutilated" (slowly stretched and warped). */
    public boolean mutilated;
}
