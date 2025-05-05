package com.thecolonel63.tameablemossbloom.mixin;

import net.emilsg.clutter.entity.client.layer.ModModelLayers;
import net.emilsg.clutter.entity.client.model.MossbloomModel;
import net.emilsg.clutter.entity.client.render.MossbloomRenderer;
import net.emilsg.clutter.entity.client.render.feature.EmissiveRenderer;
import net.emilsg.clutter.entity.custom.MossbloomEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.SaddleFeatureRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(MossbloomRenderer.class)
public abstract class MossbloomRendererMixin extends MobEntityRenderer<MossbloomEntity, MossbloomModel<MossbloomEntity>> {
    public MossbloomRendererMixin(EntityRendererFactory.Context context, MossbloomModel<MossbloomEntity> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(EntityRendererFactory.Context ctx, CallbackInfo ci) {
        this.addFeature(new SaddleFeatureRenderer<>(((MobEntityRenderer) this), new MossbloomModel<>(ctx.getPart(ModModelLayers.MOSSBLOOM)), new Identifier("clutter", "textures/entity/mossbloom_saddle.png")));
    }
}
