package com.ishland.earlyloadingscreen.patch;

import com.ishland.earlyloadingscreen.SharedConstants;
import com.ishland.earlyloadingscreen.util.RemapUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.instrument.Instrumentation;
import java.util.Iterator;
import java.util.ListIterator;

public class SodiumOSDetectionPatch implements BytecodeTransformer {

    private static final SodiumOSDetectionPatch INSTANCE = new SodiumOSDetectionPatch();

    private SodiumOSDetectionPatch() {
    }

    static {
        initTransformer();
    }

    public static void init() {
        // intentionally empty, just to trigger static initialization
    }

    private static void initTransformer() {
        Instrumentation inst = PatchUtil.instrumentation;
        if (inst == null) {
            SharedConstants.LOGGER.warn("Instrumentation unavailable, entrypoint information will not be available");
            return;
        }
        PatchUtil.transformers.add(INSTANCE);
    }

    @Override
    public boolean transform(String className, ClassNode node) {
        if (className.equals("me/jellysquid/mods/sodium/client/util/workarounds/Workarounds") ||
            className.equals("me/jellysquid/mods/sodium/client/util/workarounds/driver/nvidia/NvidiaWorkarounds") ||
            className.equals("me/jellysquid/mods/sodium/client/util/workarounds/probe/GraphicsAdapterProbe")) {

            SharedConstants.LOGGER.info("Patching %s to allow early usage".formatted(className));

            final MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            final String intermediary = "intermediary";

            for (MethodNode method : node.methods) {

                {
                    final Iterator<LocalVariableNode> iterator = method.localVariables.iterator();

                    while (iterator.hasNext()) {
                        final LocalVariableNode localVariableNode = iterator.next();

                        if (localVariableNode.desc.equals(String.format("L%s;", resolver.mapClassName(intermediary, "net.minecraft.class_156$class_158").replace('.', '/')))) {
                            localVariableNode.desc = "Lcom/ishland/earlyloadingscreen/util/OSDetectionUtil$OperatingSystem;";
                        }
                    }
                }

                {
                    final ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();

                    while (iterator.hasNext()) {
                        final AbstractInsnNode insn = iterator.next();

                        if (insn instanceof MethodInsnNode methodInsnNode) {
                            // replace Util.getOperatingSystem() with OSDetectionUtil.getOperatingSystem()
                            if (methodInsnNode.getOpcode() == Opcodes.INVOKESTATIC &&
                                methodInsnNode.owner.equals(resolver.mapClassName(intermediary, "net.minecraft.class_156").replace('.', '/')) &&
                                methodInsnNode.name.equals(resolver.mapMethodName(intermediary, "net.minecraft.class_156", "method_668", "()Lnet/minecraft/class_156$class_158;")) &&
                                methodInsnNode.desc.equals(RemapUtil.remapMethodDescriptor("()Lnet/minecraft/class_156$class_158;"))) {
                                methodInsnNode.owner = "com/ishland/earlyloadingscreen/util/OSDetectionUtil";
                                methodInsnNode.name = "getOperatingSystem";
                                methodInsnNode.desc = "()Lcom/ishland/earlyloadingscreen/util/OSDetectionUtil$OperatingSystem;";
                            }

                            // replace Util$OperatingSystem.ordinal() with OSDetectionUtil$OperatingSystem.ordinal()
                            if (methodInsnNode.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                                methodInsnNode.owner.equals(resolver.mapClassName(intermediary, "net.minecraft.class_156$class_158").replace('.', '/')) &&
                                methodInsnNode.name.equals(resolver.mapMethodName(intermediary, "net.minecraft.class_156$class_158", "ordinal", "()I")) &&
                                methodInsnNode.desc.equals(RemapUtil.remapMethodDescriptor("()I"))) {
                                methodInsnNode.owner = "com/ishland/earlyloadingscreen/util/OSDetectionUtil$OperatingSystem";
                            }
                        }

                        if (insn instanceof FieldInsnNode fieldInsnNode) {
                            // replace Util$OperatingSystem with OSDetectionUtil$OperatingSystem
                            if (fieldInsnNode.getOpcode() == Opcodes.GETSTATIC &&
                                fieldInsnNode.owner.equals(resolver.mapClassName(intermediary, "net.minecraft.class_156$class_158").replace('.', '/')) &&
                                fieldInsnNode.desc.equals(RemapUtil.remapFieldDescriptor("Lnet/minecraft/class_156$class_158;"))) {
                                fieldInsnNode.owner = "com/ishland/earlyloadingscreen/util/OSDetectionUtil$OperatingSystem";
                                fieldInsnNode.desc = "Lcom/ishland/earlyloadingscreen/util/OSDetectionUtil$OperatingSystem;";
                                if (fieldInsnNode.name.equals(resolver.mapFieldName(intermediary, "net.minecraft.class_156$class_158", "field_1135", "Lnet/minecraft/class_156$class_158;"))) {
                                    fieldInsnNode.name = "LINUX";
                                } else if (fieldInsnNode.name.equals(resolver.mapFieldName(intermediary, "net.minecraft.class_156$class_158", "field_1134", "Lnet/minecraft/class_156$class_158;"))) {
                                    fieldInsnNode.name = "SOLARIS";
                                } else if (fieldInsnNode.name.equals(resolver.mapFieldName(intermediary, "net.minecraft.class_156$class_158", "field_1133", "Lnet/minecraft/class_156$class_158;"))) {
                                    fieldInsnNode.name = "WINDOWS";
                                } else if (fieldInsnNode.name.equals(resolver.mapFieldName(intermediary, "net.minecraft.class_156$class_158", "field_1132", "Lnet/minecraft/class_156$class_158;"))) {
                                    fieldInsnNode.name = "OSX";
                                } else if (fieldInsnNode.name.equals(resolver.mapFieldName(intermediary, "net.minecraft.class_156$class_158", "field_1131", "Lnet/minecraft/class_156$class_158;"))) {
                                    fieldInsnNode.name = "UNKNOWN";
                                } else {
                                    throw new RuntimeException("Unknown field name: " + fieldInsnNode.name);
                                }
                            }
                        }
                    }
                }

            }


            return true;
        }


        return false;
    }
}
