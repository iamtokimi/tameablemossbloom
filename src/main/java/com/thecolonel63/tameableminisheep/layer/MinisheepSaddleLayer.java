package com.thecolonel63.tameableminisheep.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Saddleable;
import net.satisfy.wildernature.client.model.entity.MiniSheepModel;
import net.satisfy.wildernature.entity.MiniSheepEntity;

public class MinisheepSaddleLayer<T extends MiniSheepEntity & Saddleable, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final MiniSheepModel<T> model;
    private final ResourceLocation shearedLocation;
    private final ResourceLocation normalLocation;

    public MinisheepSaddleLayer(RenderLayerParent<T, M> renderer, MiniSheepModel<T> model, ResourceLocation normalLocation, ResourceLocation shearedLocation) {
        super(renderer);
        this.model = model;
        this.normalLocation = normalLocation;
        this.shearedLocation = shearedLocation;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (livingEntity.isSaddled()) {
            poseStack.translate(0.0f, -0.05f, 0.0f);
            poseStack.scale(1.05f, 1.05f, 1.05f);
            this.getParentModel().copyPropertiesTo(this.model);
            this.model.prepareMobModel(livingEntity, limbSwing, limbSwingAmount, partialTick);
            this.model.setupAnim(livingEntity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(livingEntity.isSheared() ? shearedLocation : normalLocation));
            this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
