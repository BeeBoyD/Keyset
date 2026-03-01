package net.minecraftforge.eventbus.service;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.EnumSet;
import java.util.Objects;
import net.minecraftforge.eventbus.IEventBusEngine;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class ModLauncherService implements ILaunchPluginService {
  private static final EnumSet<Phase> BEFORE = EnumSet.of(Phase.BEFORE);
  private static final EnumSet<Phase> AFTER = EnumSet.of(Phase.AFTER);
  private static final EnumSet<Phase> NONE = EnumSet.noneOf(Phase.class);

  private static final String TARGET_CLASS = "net.minecraft.client.MinecraftClient";
  private static final String MOD_LOADER = "net/minecraftforge/fml/ModLoader";
  private static final String POST_EVENT_DESC = "(Lnet/minecraftforge/eventbus/api/Event;)V";
  private static final String EVENT_TYPE = "net/minecraftforge/eventbus/api/Event";

  private IEventBusEngine eventBusEngine;

  @Override
  public String name() {
    return "eventbus";
  }

  public IEventBusEngine getEventBusEngine() {
    if (this.eventBusEngine == null) {
      var layer =
          Launcher.INSTANCE
              .findLayerManager()
              .flatMap(
                  layerManager ->
                      layerManager.getLayer(
                          cpw.mods.modlauncher.api.IModuleLayerManager.Layer.GAME))
              .orElseThrow();
      this.eventBusEngine =
          java.util.ServiceLoader.load(layer, IEventBusEngine.class).findFirst().orElseThrow();
    }

    return this.eventBusEngine;
  }

  @Override
  public int processClassWithFlags(
      Phase phase, ClassNode classNode, Type classType, String reason) {
    int flags = 0;
    if (Objects.equals(reason, "classloading")) {
      flags |= getEventBusEngine().processClass(classNode, classType);
    }
    if (phase == Phase.AFTER
        && TARGET_CLASS.equals(classType.getClassName())
        && patchMinecraftClient(classNode)) {
      System.out.println("[Keyset] Patched Forge 1.21.1 MinecraftClient event bridge");
      flags |= ComputeFlags.COMPUTE_FRAMES;
    }
    return flags;
  }

  @Override
  public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
    EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
    if (isEmpty) {
      if (getEventBusEngine().findASMEventDispatcher(classType)) {
        phases.addAll(BEFORE);
      }
    } else if (getEventBusEngine().handlesClass(classType)) {
      phases.addAll(AFTER);
    }

    if (TARGET_CLASS.equals(classType.getClassName())) {
      phases.add(Phase.AFTER);
    }

    return phases.isEmpty() ? NONE : phases;
  }

  private static boolean patchMinecraftClient(ClassNode classNode) {
    for (MethodNode method : classNode.methods) {
      if (!"<init>".equals(method.name)) {
        continue;
      }

      for (AbstractInsnNode instruction = method.instructions.getFirst();
          instruction != null;
          instruction = instruction.getNext()) {
        if (!(instruction instanceof MethodInsnNode methodInstruction)) {
          continue;
        }

        if (methodInstruction.getOpcode() != Opcodes.INVOKEVIRTUAL
            || !MOD_LOADER.equals(methodInstruction.owner)
            || !"postEvent".equals(methodInstruction.name)
            || !POST_EVENT_DESC.equals(methodInstruction.desc)) {
          continue;
        }

        AbstractInsnNode previous = methodInstruction.getPrevious();
        if (previous instanceof TypeInsnNode typeInstruction
            && typeInstruction.getOpcode() == Opcodes.CHECKCAST
            && EVENT_TYPE.equals(typeInstruction.desc)) {
          return false;
        }

        method.instructions.insertBefore(
            methodInstruction, new TypeInsnNode(Opcodes.CHECKCAST, EVENT_TYPE));
        return true;
      }
    }

    return false;
  }
}
