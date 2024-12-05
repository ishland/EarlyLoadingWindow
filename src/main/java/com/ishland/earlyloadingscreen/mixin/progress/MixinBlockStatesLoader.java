package com.ishland.earlyloadingscreen.mixin.progress;

import com.ishland.earlyloadingscreen.util.ProgressUtil;
import net.minecraft.client.render.model.BlockStatesLoader;
import net.minecraft.client.render.model.UnbakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(BlockStatesLoader.class)
public class MixinBlockStatesLoader {

    @Inject(method = "method_65721", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private static void captureLoadBlockStates(Function function, UnbakedModel unbakedModel, Executor executor, Map resources, CallbackInfoReturnable<CompletionStage> cir, List list) {
        ProgressUtil.createProgress(list, cir.getReturnValue(), "block states");
    }

}
