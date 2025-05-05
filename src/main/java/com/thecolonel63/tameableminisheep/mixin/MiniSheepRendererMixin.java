package com.thecolonel63.tameableminisheep.mixin;

import com.thecolonel63.tameableminisheep.layer.MinisheepSaddleLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.satisfy.wildernature.client.model.entity.MiniSheepModel;
import net.satisfy.wildernature.client.render.entity.MiniSheepRenderer;
import net.satisfy.wildernature.entity.MiniSheepEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MiniSheepRenderer.class)
public abstract class MiniSheepRendererMixin extends MobRenderer<MiniSheepEntity, MiniSheepModel<MiniSheepEntity>> {
    public MiniSheepRendererMixin(EntityRendererProvider.Context context, MiniSheepModel<MiniSheepEntity> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(EntityRendererProvider.Context context, CallbackInfo ci) {
        this.addLayer(new MinisheepSaddleLayer<>(((MobRenderer) this), new MiniSheepModel<>(context.bakeLayer(MiniSheepModel.LAYER_LOCATION)), new ResourceLocation("wildernature", "textures/entity/minisheep_saddle.png"), new ResourceLocation("wildernature", "textures/entity/minisheep_sheared_saddle.png")));
    }
}
