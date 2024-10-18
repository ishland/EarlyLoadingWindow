package com.ishland.earlyloadingscreen.mixin.progress;

import com.ishland.earlyloadingscreen.LoadingProgressManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(ModelBaker.class)
public abstract class MixinModelLoader {

    private LoadingProgressManager.ProgressHolder modelLoadProgressHolder;
    private LoadingProgressManager.ProgressHolder modelAdditionalLoadProgressHolder;
    private int modelLoadProgress = 0;
    private int modelLoadTotalEstimate;
    private int modelDependencyResolveProgress = 0;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/Object;<init>()V", shift = At.Shift.AFTER))
    private void earlyInit(CallbackInfo ci) {
        modelLoadProgressHolder = LoadingProgressManager.tryCreateProgressHolder();
        modelAdditionalLoadProgressHolder = LoadingProgressManager.tryCreateProgressHolder();
        if (modelLoadProgressHolder != null) {
            modelLoadProgressHolder.update(() -> "Preparing models...");
        }
        for(Block block : Registries.BLOCK) {
            modelLoadTotalEstimate += block.getStateManager().getStates().size();
        }
        modelLoadTotalEstimate += Registries.ITEM.getIds().size();
        modelLoadTotalEstimate += 6;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void postInit(CallbackInfo ci) {
        if (modelLoadProgressHolder != null) {
            modelLoadProgressHolder.close();
            modelLoadProgressHolder = null;
        }
        if (modelAdditionalLoadProgressHolder != null) {
            modelAdditionalLoadProgressHolder.close();
            modelAdditionalLoadProgressHolder = null;
        }
    }

    @Redirect(method = "bake", at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"))
    private void redirectIteration(Map<ModelIdentifier, UnbakedModel> instance, BiConsumer<ModelIdentifier, UnbakedModel> consumer) {
        try (LoadingProgressManager.ProgressHolder progressHolder = LoadingProgressManager.tryCreateProgressHolder()) {
            int index = 0;
            int size = instance.size();
            for (Map.Entry<ModelIdentifier, UnbakedModel> entry : instance.entrySet()) {
                final ModelIdentifier identifier = entry.getKey();
                final UnbakedModel model = entry.getValue();
                if (progressHolder != null) {
                    int finalIndex = index;
                    progressHolder.update(() -> String.format("Baking model (%d/%d): %s", finalIndex, size, identifier));
                    progressHolder.updateProgress(() -> (float) finalIndex / (float) size);
                }
                index++;
                consumer.accept(identifier, model);
            }
        }

    }

}
