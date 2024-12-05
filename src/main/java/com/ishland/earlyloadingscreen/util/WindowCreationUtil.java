package com.ishland.earlyloadingscreen.util;

import com.ishland.earlyloadingscreen.LoadingProgressManager;
import com.ishland.earlyloadingscreen.SharedConstants;
import com.ishland.earlyloadingscreen.patch.SodiumOSDetectionPatch;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class WindowCreationUtil {

    public static long warpGlfwCreateWindow(int width, int height, CharSequence title, long monitor, long share) {
        FabricLoader.getInstance().invokeEntrypoints("els_before_window_creation", Runnable.class, Runnable::run);
        sodiumHookInit();
        sodiumHook(false);
        try {
            return GLFW.glfwCreateWindow(width, height, title, monitor, share);
        } finally {
            sodiumHook(true);
            FabricLoader.getInstance().invokeEntrypoints("els_after_window_creation", Runnable.class, Runnable::run);
        }
    }

    private static AtomicBoolean ranSodiumHookInit = new AtomicBoolean(false);
    private static boolean foundSodium = true;

    private static void sodiumHookInit() {
        if (Boolean.getBoolean("earlyloadingscreen.duringEarlyLaunch") && !SodiumOSDetectionPatch.INITIALIZED) {
            final String msg = "SodiumOSDetectionPatch unavailable, sodium workarounds may not work properly";
            SharedConstants.LOGGER.warn(msg);
            LoadingProgressManager.showMessageAsProgress(msg);
            return;
        }
        if (ranSodiumHookInit.compareAndSet(false, true)) {
            if (!FabricLoader.getInstance().isModLoaded("sodium")) {
                foundSodium = false;
                SharedConstants.LOGGER.info("Sodium not found, skipping sodium hook init");
                return;
            }
            final Class<?> graphicsAdapterProbeClazz;
            final Class<?> workaroundsClazz;
            try {
                graphicsAdapterProbeClazz = locateClass("me.jellysquid.mods.sodium.client.util.workarounds.probe.GraphicsAdapterProbe", "me.jellysquid.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe", "net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe");
                workaroundsClazz = locateClass("me.jellysquid.mods.sodium.client.util.workarounds.Workarounds", "me.jellysquid.mods.sodium.client.compatibility.workarounds.Workarounds", "net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds");
            } catch (Throwable t) {
                final String msg = "Failed to find Sodium workarounds, skipping sodium hook init";
                if (FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("els.debug")) {
                    SharedConstants.LOGGER.warn(msg, t);
                } else {
                    SharedConstants.LOGGER.warn(msg);
                }
                LoadingProgressManager.showMessageAsProgress(msg);
                foundSodium = false;
                return;
            }
            try {
                graphicsAdapterProbeClazz.getMethod("findAdapters").invoke(null);
                workaroundsClazz.getMethod("init").invoke(null);
//                final Collection<?> adapters = (Collection<?>) graphicsAdapterProbeClazz.getMethod("getAdapters").invoke(null);
//                boolean foundNvidia = false;
//                for (Object adapter : adapters) {
//                    final Enum<?> vendor = (Enum<?>) graphicsAdapterInfoClazz.getMethod("vendor").invoke(adapter);
//                    if (vendor == Enum.valueOf(graphicsAdapterVendorClazz, "NVIDIA")) {
//                        foundNvidia = true;
//                        break;
//                    }
//                }
//                if (foundNvidia && (PlatformDependent.isWindows() || PlatformDependent.normalizedOs().equals("linux"))) {
//                    final Field activeWorkarounds = workaroundsClazz.getDeclaredField("ACTIVE_WORKAROUNDS");
//                    activeWorkarounds.setAccessible(true);
//                    final Enum<?> NVIDIA_THREADED_OPTIMIZATIONS = Enum.valueOf(workaroundsReferenceClazz, "NVIDIA_THREADED_OPTIMIZATIONS");
//                    ((AtomicReference<Collection<?>>) activeWorkarounds.get(null)).set(Set.of(NVIDIA_THREADED_OPTIMIZATIONS));
//                }
            } catch (Throwable t) {
                final String msg = "Failed to init Sodium workarounds, skipping sodium hook";
                if (FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("els.debug")) {
                    SharedConstants.LOGGER.warn(msg, t);
                } else {
                    SharedConstants.LOGGER.warn(msg);
                }
                LoadingProgressManager.showMessageAsProgress(msg);
                foundSodium = false;
                return;
            }
        }
    }

    private static void sodiumHook(boolean after) {
        if (!foundSodium) return;
        final Class<?> workaroundsClazz;
        final Class<? extends Enum> workaroundsReferenceClazz;
        final Class<?> nvidiaWorkaroundsClazz;
        try {
            workaroundsClazz = locateClass("me.jellysquid.mods.sodium.client.util.workarounds.Workarounds", "me.jellysquid.mods.sodium.client.compatibility.workarounds.Workarounds", "net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds");
            workaroundsReferenceClazz = (Class<? extends Enum<?>>) locateClass("me.jellysquid.mods.sodium.client.util.workarounds.Workarounds$Reference", "me.jellysquid.mods.sodium.client.compatibility.workarounds.Workarounds$Reference", "net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds$Reference");
            nvidiaWorkaroundsClazz = locateClass("me.jellysquid.mods.sodium.client.util.workarounds.driver.nvidia.NvidiaWorkarounds", "me.jellysquid.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds", "net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds");
        } catch (Throwable e) {
            final String msg = "Failed to find Sodium workarounds, skipping sodium hook";
            if (FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("els.debug")) {
                SharedConstants.LOGGER.warn(msg, e);
            } else {
                SharedConstants.LOGGER.warn(msg);
            }
            LoadingProgressManager.showMessageAsProgress(msg);
            foundSodium = false;
            return;
        }
        try {
            final Enum<?> NVIDIA_THREADED_OPTIMIZATIONS = Arrays.stream(workaroundsReferenceClazz.getEnumConstants())
                    .filter(anEnum -> anEnum.name().equals("NVIDIA_THREADED_OPTIMIZATIONS") || anEnum.name().equals("NVIDIA_THREADED_OPTIMIZATIONS_BROKEN"))
                    .findFirst().get();
            if ((boolean) workaroundsClazz.getMethod("isWorkaroundEnabled", workaroundsReferenceClazz).invoke(null, NVIDIA_THREADED_OPTIMIZATIONS)) {
                if (!after) {
                    try {
                        nvidiaWorkaroundsClazz.getMethod("install").invoke(null);
                    } catch (NoSuchMethodException e) {
                        nvidiaWorkaroundsClazz.getMethod("applyEnvironmentChanges").invoke(null);
                    }
                    LoadingProgressManager.showMessageAsProgress("Installed Nvidia workarounds from sodium", 5000L);
                } else {
                    try {
                        nvidiaWorkaroundsClazz.getMethod("uninstall").invoke(null);
                    } catch (NoSuchMethodException e) {
                        nvidiaWorkaroundsClazz.getMethod("undoEnvironmentChanges").invoke(null);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static Class<?> locateClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new RuntimeException("Failed to locate any of " + String.join(", ", names));
    }

}
