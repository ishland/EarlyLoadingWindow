package com.ishland.earlyloadingscreen.mixin.progress;

import com.ishland.earlyloadingscreen.util.ProgressUtil;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.BlockStatesLoader;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(BakedModelManager.class)
public class MixinBakedModelManager {

    @Inject(method = "method_45899", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private static void captureLoadModels(Executor unused, Map unused1, CallbackInfoReturnable<CompletionStage<?>> cir, List<? extends CompletionStage<?>> list) {
        ProgressUtil.createProgress(list, cir.getReturnValue(), "models");
    }

    @Inject(method = "method_62662", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private static void captureLoadBlockStates(Function function, BlockStatesLoader blockStatesLoader, Executor executor, Map blockStates, CallbackInfoReturnable<CompletionStage> cir, List<? extends CompletionStage<?>> list1) {
        ProgressUtil.createProgress(list1, cir.getReturnValue(), "block states");
    }

}
