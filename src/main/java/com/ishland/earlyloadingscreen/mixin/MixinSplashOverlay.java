package com.ishland.earlyloadingscreen.mixin;

import com.ishland.earlyloadingscreen.LoadingProgressManager;
import com.ishland.earlyloadingscreen.LoadingScreenManager;
import com.ishland.earlyloadingscreen.mixin.access.IGlStateManager;
import com.ishland.earlyloadingscreen.mixin.access.ISimpleResourceReload;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourceReload;
import net.minecraft.resource.SimpleResourceReload;
import org.lwjgl.opengl.GL32;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

import static com.ishland.earlyloadingscreen.render.GLText.gltSetText;

@Mixin(value = SplashOverlay.class, priority = 1010)
public class MixinSplashOverlay {

    @Shadow @Final private ResourceReload reload;

    private LoadingProgressManager.ProgressHolder progressHolder;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (this.reload instanceof SimpleResourceReload<?>) {
            LoadingProgressManager.ProgressHolder progressHolder = LoadingProgressManager.tryCreateProgressHolder();
            if (progressHolder != null) {
                this.progressHolder = progressHolder;
                this.reload.whenComplete().thenRun(progressHolder::close);
            }
        }
    }

    @Inject(method = "render", at = @At(value = "RETURN"))
    private void postRender(CallbackInfo ci) {
        final LoadingScreenManager.RenderLoop renderLoop = LoadingScreenManager.windowEventLoop.renderLoop;
        if (renderLoop != null) {
            if (this.progressHolder != null && this.reload instanceof SimpleResourceReload<?> simpleResourceReload) {
                this.progressHolder.update(() -> "Pending reloads: " + Arrays.toString(((ISimpleResourceReload) simpleResourceReload).getWaitingReloaders().toArray()));
            }
            final MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                gltSetText(renderLoop.fpsText, "%d fps".formatted(client.getCurrentFps()));
            } else {
                gltSetText(renderLoop.fpsText, "");
            }
            final Window window = MinecraftClient.getInstance().getWindow();
            renderLoop.render(window.getFramebufferWidth(), window.getFramebufferHeight(), (float) window.getScaleFactor() / 2.0f);
            // restore state
            int activeTexture = GlStateManager._getActiveTexture();
            GL32.glActiveTexture(activeTexture);
            GL32.glBindTexture(GL32.GL_TEXTURE_2D, IGlStateManager.getTEXTURES()[activeTexture - GL32.GL_TEXTURE0].boundTexture);
        }
    }

}
