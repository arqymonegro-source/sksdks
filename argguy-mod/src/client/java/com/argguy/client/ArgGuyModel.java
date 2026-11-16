package com.argguy.client;

import com.argguy.ArgGuyMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The cubes below were converted 1:1 from argguy.bbmodel (box-UV, 128x128 texture).
 * Animation is procedural: the Blockbench walk/run clips are not used.
 */
public class ArgGuyModel extends EntityModel<ArgGuyRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ArgGuyMod.id("argguy"), "main");

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ArgGuyModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(32, 64).addBox(3F, -8F, -4F, 1F, 8F, 8F)
                .texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(0.5F))
                .texOffs(80, 39).addBox(-3.5F, -7.5F, 0.5F, 7F, 7F, 3F)
                .texOffs(50, 69).addBox(-4F, -8F, -4F, 1F, 8F, 8F)
                .texOffs(68, 21).addBox(-3F, -8F, -4F, 6F, 1F, 8F)
                .texOffs(68, 30).addBox(-3F, -1F, -4F, 6F, 1F, 8F)
                .texOffs(32, 0).addBox(-3F, -7F, 3F, 6F, 6F, 1F)
                .texOffs(32, 7).addBox(-3F, -7F, -4F, 6F, 6F, 0F),
                PartPose.offset(0F, -13.25F, 0F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-4F, 0F, -2F, 8F, 20F, 4F)
                .texOffs(24, 16).addBox(-4F, 0F, -2F, 8F, 20F, 4F, new CubeDeformation(0.25F))
                .texOffs(0, 40).addBox(-3.5F, 0.5F, -1.5F, 7F, 19F, 3F),
                PartPose.offset(0F, -13F, 0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(20, 40).addBox(-2F, -2F, -2F, 4F, 20F, 4F)
                .texOffs(36, 40).addBox(-2F, -2F, -2F, 4F, 20F, 4F, new CubeDeformation(0.25F))
                .texOffs(68, 39).addBox(-1.5F, -1.5F, -1.5F, 3F, 19F, 3F),
                PartPose.offset(6F, -11F, 0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-2F, -2F, -2F, 4F, 20F, 4F)
                .texOffs(52, 24).addBox(-2F, -2F, -2F, 4F, 20F, 4F, new CubeDeformation(0.25F))
                .texOffs(68, 61).addBox(-1.5F, -1.5F, -1.5F, 3F, 19F, 3F),
                PartPose.offset(-6F, -11F, 0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(52, 48).addBox(-2F, 0F, -2F, 4F, 17F, 4F)
                .texOffs(0, 62).addBox(-2F, 0F, -2F, 4F, 17F, 4F, new CubeDeformation(0.25F))
                .texOffs(32, 80).addBox(-1.5F, 0.5F, -1.5F, 3F, 16F, 3F),
                PartPose.offset(2F, 7F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2F, 0F, -2F, 4F, 17F, 4F)
                .texOffs(16, 64).addBox(-2F, 0F, -2F, 4F, 17F, 4F, new CubeDeformation(0.25F))
                .texOffs(80, 0).addBox(-1.5F, 0.5F, -1.5F, 3F, 16F, 3F),
                PartPose.offset(-2F, 7F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(ArgGuyRenderState s) {
        super.setupAnim(s);
        this.head.yRot = s.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = s.xRot * Mth.DEG_TO_RAD;

        float swing = s.walkAnimationPos * 0.6662F;
        float amt = Math.min(1.0F, s.walkAnimationSpeed * 1.4F);
        this.rightLeg.xRot = Mth.cos(swing) * 1.1F * amt;
        this.leftLeg.xRot = Mth.cos(swing + Mth.PI) * 1.1F * amt;

        // long arms hang heavy and drag a little behind the stride, slow "breathing" sway
        float breathe = Mth.sin(s.ageInTicks * 0.09F) * 0.04F;
        this.rightArm.xRot = Mth.cos(swing + Mth.PI) * 0.45F * amt + breathe;
        this.leftArm.xRot = Mth.cos(swing) * 0.45F * amt - breathe;
        this.rightArm.zRot = 0.09F + Math.abs(breathe);
        this.leftArm.zRot = -0.09F - Math.abs(breathe);

        // "model mutilation" (Interloper): while hunting the limbs stretch and warp.
        // Deliberately slow (periods of several seconds): nothing here flickers or strobes.
        for (ModelPart part : new ModelPart[]{head, rightArm, leftArm, rightLeg, leftLeg}) {
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        }
        if (s.mutilated) {
            float t = s.ageInTicks * 0.045F;
            this.head.xScale = 1.0F + 0.10F * Mth.sin(t * 1.3F);
            this.head.yScale = 1.0F + 0.20F * Mth.sin(t * 0.9F + 1.0F);
            this.head.zRot += 0.28F * Mth.sin(t * 0.6F);
            this.rightArm.yScale = 1.35F + 0.25F * Mth.sin(t * 0.7F);
            this.leftArm.yScale = 1.35F + 0.25F * Mth.sin(t * 0.8F + 2.0F);
            this.rightLeg.xScale = 1.0F + 0.15F * Mth.sin(t * 1.1F);
            this.leftLeg.yScale = 1.0F + 0.12F * Mth.sin(t * 0.9F + 0.5F);
        }
    }
}
