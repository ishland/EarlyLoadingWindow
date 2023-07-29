package com.ishland.earlyloadingscreen;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.ishland.earlyloadingscreen.platform_cl.AppLoaderAccessSupport;
import com.ishland.earlyloadingscreen.platform_cl.Config;
import com.ishland.earlyloadingscreen.platform_cl.PlatformUtil;
import com.ishland.earlyloadingscreen.render.GLText;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.Arguments;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static com.ishland.earlyloadingscreen.SharedConstants.LOGGER;
import static com.ishland.earlyloadingscreen.render.GLText.GLT_BOTTOM;
import static com.ishland.earlyloadingscreen.render.GLText.GLT_LEFT;
import static com.ishland.earlyloadingscreen.render.GLText.GLT_RIGHT;
import static com.ishland.earlyloadingscreen.render.GLText.GLT_TOP;
import static com.ishland.earlyloadingscreen.render.GLText.gltCreateText;
import static com.ishland.earlyloadingscreen.render.GLText.gltSetText;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL32.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL32.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL32.glClear;
import static org.lwjgl.opengl.GL32.glClearColor;

public class LoadingScreenManager {

    public static final Cleaner CLEANER = Cleaner.create();

    private static long handle;
    public static final WindowEventLoop windowEventLoop;
    public static boolean eventLoopStarted = false;
    private static final Object windowEventLoopSync = new Object();

    static {
        LOGGER.info("Initializing LoadingScreenManager...");
        windowEventLoop = new WindowEventLoop(PlatformUtil.IS_WINDOWS);
        AppLoaderAccessSupport.setAccess(LoadingScreenManager::tryCreateProgressHolder);
    }

    public static void init() {
        // intentionally empty
    }

    public static void createWindow() {
        synchronized (windowEventLoopSync) {
            if (handle != 0L || eventLoopStarted) {
//                LOGGER.warn("createWindow() called twice", new Throwable());
                return;
            }
            LOGGER.info("Creating early window...");
            try {
                initGLFW();
                if (!PlatformUtil.IS_WINDOWS) {
                    handle = initWindow();
                }
            } catch (Throwable t) {
                LOGGER.error("Failed to create early window", t);
                return;
            }
            eventLoopStarted = true;
            windowEventLoop.start();
            glfwPollEvents();
        }
    }

    public static long takeContext() {
        synchronized (windowEventLoopSync) {
            if (handle == 0L) {
                return 0L;
            }
            LOGGER.info("Handing early window to Minecraft...");
            windowEventLoop.running.set(false);
            eventLoopStarted = true;
            while (windowEventLoop.isAlive()) {
                LockSupport.parkNanos("Waiting for window event loop to exit", 100_000L);
            }
            final long handle1 = handle;
            handle = 0L;
//            if (SharedConstants.REUSE_WINDOW) {
//                return handle1;
//            } else {
//                LOGGER.info("Destroying early window...");
//                windowEventLoop.renderLoop = null;
//                glfwDestroyWindow(handle1);
//                return -1;
//            }
            if (!Config.REUSE_EARLY_WINDOW) {
                windowEventLoop.renderLoop = null;
            }
            return handle1;
        }
    }

    public static void reInitLoop() {
        synchronized (windowEventLoopSync) {
            if (windowEventLoop.renderLoop != null) {
                LOGGER.info("Reinitializing screen rendering...");
                windowEventLoop.renderLoop = new RenderLoop();
            }
        }
    }

    public record WindowSettings(
            int framebufferWidth,
            int framebufferHeight,
            int windowWidth,
            int windowHeight
    ) {
    }

    public static WindowSettings getWindowSettings() {
        return new WindowSettings(
                windowEventLoop.framebufferWidth,
                windowEventLoop.framebufferHeight,
                windowEventLoop.windowWidth,
                windowEventLoop.windowHeight
        );
    }

    private static void initGLFW() {
        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            PointerBuffer pointerBuffer = memoryStack.mallocPointer(1);
            int i = GLFW.glfwGetError(pointerBuffer);
            if (i != 0) {
                long l = pointerBuffer.get();
                String string1 = l == 0L ? "" : MemoryUtil.memUTF8(l);
                throw new IllegalStateException(String.format(Locale.ROOT, "GLFW error before init: [0x%X]%s", i, string1));
            }
        }
        List<String> list = Lists.newArrayList();
        GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback(
                (code, pointer) -> list.add(String.format(Locale.ROOT, "GLFW error during init: [0x%X]%s", code, pointer))
        );
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW, errors: " + Joiner.on(",").join(list));
        } else {
            for(String string : list) {
                LOGGER.error("GLFW error collected during initialization: {}", string);
            }

            _glfwSetErrorCallback(gLFWErrorCallback);
        }
    }

    private static void _glfwSetErrorCallback(GLFWErrorCallback gLFWErrorCallback) {
        GLFWErrorCallback old = GLFW.glfwSetErrorCallback(gLFWErrorCallback);
        if (old != null) {
            old.free();
        }
    }

    private static void throwGlError(int error, long description) {
        String string = "GLFW error " + error + ": " + MemoryUtil.memUTF8(description);
        TinyFileDialogs.tinyfd_messageBox(
                "Minecraft", string + ".\n\nPlease make sure you have up-to-date drivers (see aka.ms/mcdriver for instructions).", "ok", "error", false
        );
        throw new RuntimeException(string);
    }

    private static long initWindow() {
        GLFW.glfwSetErrorCallback(LoadingScreenManager::throwGlError);
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, 1);
        int configuredWidth = 854;
        int configuredHeight = 480;
        try {
            final Arguments arguments = FabricLoaderImpl.INSTANCE.getGameProvider().getArguments();
            configuredWidth = Integer.parseInt(arguments.getOrDefault("width", "854"));
            configuredHeight = Integer.parseInt(arguments.getOrDefault("height", "480"));
        } catch (Throwable t) {
            LOGGER.error("Failed to load window configuration", t);
        }
        final long handle = GLFW.glfwCreateWindow(configuredWidth, configuredHeight, "Minecraft - initializing mods...", 0L, 0L);
        // Center window
//        final long monitor = glfwGetPrimaryMonitor();
//        if (monitor != 0L) {
//            final GLFWVidMode vidmode = glfwGetVideoMode(monitor);
//            if (vidmode != null) {
//                glfwSetWindowPos(
//                        handle,
//                        (vidmode.width() - configuredWidth) / 2,
//                        (vidmode.height() - configuredHeight) / 2
//                );
//            }
//        }

        int[] width0 = new int[1];
        int[] height0 = new int[1];
        glfwGetFramebufferSize(handle, width0, height0);
        windowEventLoop.framebufferWidth = width0[0];
        windowEventLoop.framebufferHeight = height0[0];
        glfwGetWindowSize(handle, width0, height0);
        windowEventLoop.windowWidth = width0[0];
        windowEventLoop.windowHeight = height0[0];
        GLFW.glfwSetFramebufferSizeCallback(handle, (window, width, height) -> {
            windowEventLoop.framebufferWidth = width;
            windowEventLoop.framebufferHeight = height;
        });
        GLFW.glfwSetWindowPosCallback(handle, (window, xpos, ypos) -> {});
        GLFW.glfwSetWindowSizeCallback(handle, (window, width, height) -> {
            windowEventLoop.windowWidth = width;
            windowEventLoop.windowHeight = height;
        });
        GLFW.glfwSetWindowFocusCallback(handle, (window, focused) -> {});
        GLFW.glfwSetCursorEnterCallback(handle, (window, entered) -> {});

        return handle;
    }

    public static RenderLoop.ProgressHolder tryCreateProgressHolder() {
        spinWaitInit();
        synchronized (windowEventLoopSync) {
            final RenderLoop renderLoop = LoadingScreenManager.windowEventLoop.renderLoop;
            return renderLoop != null ? renderLoop.new ProgressHolder() : null;
        }
    }

    public static void spinWaitInit() {
        if (!LoadingScreenManager.windowEventLoop.initialized) {
            while (!LoadingScreenManager.windowEventLoop.initialized && LoadingScreenManager.windowEventLoop.isAlive()) {
                // spin wait, should come very soon
                Thread.onSpinWait();
            }
        }
    }

    public static class RenderLoop {

        public GLText glt = new GLText();
        public final GLText.GLTtext memoryUsage = gltCreateText();
        public final GLText.GLTtext fpsText = gltCreateText();
        private final GLText.GLTtext progressText = gltCreateText();

        private final Object progressSync = new Object();
        private final Set<Progress> activeProgress = new LinkedHashSet<>();

        static {
            ProgressHolder.class.getName(); // load class
            Progress.class.getName(); // load class
        }

        public void render(int width, int height) {
//            glfwGetFramebufferSize(glfwGetCurrentContext(), width, height);
//            glViewport(0, 0, width[0], height[0]);
            glt.gltViewport(width, height);

            try (final Closeable ignored = glt.gltBeginDraw()) {
                glt.gltColor(1.0f, 1.0f, 1.0f, 1.0f);

                gltSetText(this.memoryUsage, "Memory: %d/%d MiB".formatted(
                        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024L / 1024L,
                        Runtime.getRuntime().maxMemory() / 1024L / 1024L
                ));
                glt.gltDrawText2DAligned(
                        this.memoryUsage,
                        width,
                        0,
                        1.0f,
                        GLT_RIGHT, GLT_TOP
                );
                glt.gltDrawText2DAligned(
                        this.fpsText,
                        0,
                        0,
                        1.0f,
                        GLT_LEFT, GLT_TOP
                );

                StringBuilder sb = new StringBuilder();
                synchronized (progressSync) {
                    for (Progress progress : activeProgress) {
                        final String str = progress.get();
                        if (str.isBlank()) continue;
                        sb.append(str).append('\n');
                    }
                }
                gltSetText(this.progressText, sb.toString().trim());
                glt.gltDrawText2DAligned(
                        this.progressText,
                        0,
                        height,
                        1.0f,
                        GLT_LEFT, GLT_BOTTOM
                );

            } catch (IOException e) {
                throw new RuntimeException(e); // shouldn't happen
            }
        }

        private void terminate() {
            glt.gltTerminate();
        }

        private static class Progress {
            private volatile Supplier<String> supplier;
            private int lastSupplierHash = 0;
            private String text = "";

            public void update(Supplier<String> text) {
                this.supplier = text;
            }

            public String get() {
                final Supplier<String> supplier = this.supplier;
                if (supplier == null) return "";
                final int hash = System.identityHashCode(supplier);
                if (hash != lastSupplierHash) {
                    lastSupplierHash = hash;
                    text = get0();
                }
                return text;
            }

            private String get0() {
                try {
                    return supplier.get();
                } catch (Throwable t) {
                    return "Error: " + t.getMessage();
                }
            }

        }

        public class ProgressHolder implements AppLoaderAccessSupport.ProgressHolderAccessor {

            private final Progress impl;

            public ProgressHolder() {
                final Set<Progress> activeProgress1 = activeProgress;
                final Object progressSync1 = progressSync;
                Progress progress = this.impl = new Progress();
                CLEANER.register(this, () -> {
                    synchronized (progressSync1) {
                        activeProgress1.remove(progress);
                    }
                });
                synchronized (progressSync) {
                    activeProgress.add(impl);
                }
            }

            public void update(Supplier<String> text) {
                impl.update(text);
            }

            @Override
            public void close() {
                synchronized (progressSync) {
                    activeProgress.remove(impl);
                }
            }
        }

    }

    public static class WindowEventLoop extends Thread implements Executor {

        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

        private volatile boolean needsCreateWindow;
        private volatile boolean initialized = false;
        public volatile RenderLoop renderLoop = null;
        private volatile int framebufferWidth = 0;
        private volatile int framebufferHeight = 0;
        private volatile int windowWidth = 0;
        private volatile int windowHeight = 0;

        private WindowEventLoop(boolean needsCreateWindow) {
            super("EarlyLoadingScreen - Render Thread");
            this.needsCreateWindow = needsCreateWindow;
        }

        @Override
        public void run() {
            try {
                long handle;
                if (needsCreateWindow) {
                    LoadingScreenManager.handle = handle = initWindow();
                } else {
                    handle = LoadingScreenManager.handle;
                }
                GLFW.glfwMakeContextCurrent(handle);
                GL.createCapabilities();
                glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                GLFW.glfwSwapInterval(1);

                RenderLoop renderLoop = this.renderLoop = new RenderLoop();

                initialized = true;

                long lastFpsTime = System.nanoTime();
                int fpsCounter = 0;
                int fps = 0;

                long lastFrameTime = System.nanoTime();

                final GLText glt = renderLoop.glt;
                while (running.get()) {
                    {
                        Runnable runnable;
                        while ((runnable = queue.poll()) != null) {
                            try {
                                runnable.run();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    }
                    if (GLFW.glfwWindowShouldClose(handle)) {
                        GLFW.glfwSetWindowShouldClose(handle, false);
                    }
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                    glViewport(0, 0, framebufferWidth, framebufferHeight);
                    renderLoop.render(framebufferWidth, framebufferHeight);

                    if (!PlatformUtil.IS_OSX) {
                        GLFW.glfwPollEvents();
                    }
                    GLFW.glfwSwapBuffers(handle);
                    fpsCounter ++;
                    final long currentTime = System.nanoTime();
                    if (currentTime - lastFpsTime >= 1_000_000_000L) {
                        fps = (int) (fpsCounter * 1000_000_000L / (currentTime - lastFpsTime));
                        fpsCounter = 0;
                        lastFpsTime = currentTime;
                        gltSetText(renderLoop.fpsText, "%d fps".formatted(fps));
                    }
                    while ((System.nanoTime() - lastFrameTime) + 100_000L < 1_000_000_000L / 60L) {
                        LockSupport.parkNanos(100_000L);
                    }
                    lastFrameTime = System.nanoTime();
                }
            } catch (Throwable t) {
                LOGGER.error("Failed to render early window, exiting", t);
                this.renderLoop = null;
            } finally {
                Callbacks.glfwFreeCallbacks(handle);
                GLFW.glfwMakeContextCurrent(0L);
                needsCreateWindow = false;
                initialized = true;
            }
        }

        @Override
        public void execute(@NotNull Runnable command) {
            queue.add(command);
        }

        public void setWindowTitle(CharSequence title) {
            if (handle != 0L) {
                if (needsCreateWindow) {
                    this.execute(() -> GLFW.glfwSetWindowTitle(handle, title));
                } else {
                    GLFW.glfwSetWindowTitle(handle, title);
                }
            }
        }
    }

}
