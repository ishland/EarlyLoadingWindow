package com.ishland.earlyloadingscreen.mixin.access;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlStateManager.class)
public interface IGlStateManager {

    @Accessor
    public static GlStateManager.Texture2DState[] getTEXTURES() {
        throw new AbstractMethodError();
    }

}
