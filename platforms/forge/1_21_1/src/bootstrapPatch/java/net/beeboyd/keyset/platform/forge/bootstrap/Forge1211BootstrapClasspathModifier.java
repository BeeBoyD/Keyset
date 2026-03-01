package net.beeboyd.keyset.platform.forge.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.bootstrap.api.BootstrapClasspathModifier;

public final class Forge1211BootstrapClasspathModifier implements BootstrapClasspathModifier {
  private static final String PATCH_PATH_PROPERTY = "keyset.fmlloaderPatchPath";
  private static final String EVENTBUS_PATCH_PATH_PROPERTY = "keyset.eventbusPatchPath";
  private static final String FMLLOADER_MARKER = "fmlloader-1.21.1-";
  private static final String EVENTBUS_MARKER = "eventbus-6.2.27.jar";

  @Override
  public String name() {
    return "keyset-fmlloader-1.21.1-patch";
  }

  @Override
  public boolean process(List<Path[]> classpath) {
    boolean changed = false;
    changed |= patchEntry(classpath, FMLLOADER_MARKER, PATCH_PATH_PROPERTY, "fmlloader");
    changed |= patchEntry(classpath, EVENTBUS_MARKER, EVENTBUS_PATCH_PATH_PROPERTY, "eventbus");
    return changed;
  }

  private static boolean patchEntry(
      List<Path[]> classpath, String marker, String propertyName, String label) {
    List<Path> patchPaths = findPatchPaths(propertyName);
    if (patchPaths.isEmpty()) {
      return false;
    }

    for (int index = 0; index < classpath.size(); index++) {
      Path[] entry = classpath.get(index);
      if (entry == null || entry.length != 1) {
        continue;
      }

      Path originalPath = entry[0];
      if (!originalPath.getFileName().toString().contains(marker)) {
        continue;
      }

      Path[] replacement = new Path[patchPaths.size() + 1];
      for (int patchIndex = 0; patchIndex < patchPaths.size(); patchIndex++) {
        replacement[patchIndex] = patchPaths.get(patchIndex);
      }
      replacement[patchPaths.size()] = originalPath;

      classpath.set(index, replacement);
      System.out.println(
          "[Keyset] Patched Forge 1.21.1 "
              + label
              + " with "
              + patchPaths.size()
              + " launcher path(s)");
      return true;
    }

    return false;
  }

  private static List<Path> findPatchPaths(String propertyName) {
    String rawPatchPath = System.getProperty(propertyName, "");
    if (rawPatchPath.isBlank()) {
      return List.of();
    }

    List<Path> patchPaths = new ArrayList<>();
    for (String candidate : rawPatchPath.split(java.io.File.pathSeparator)) {
      if (candidate.isBlank()) {
        continue;
      }

      Path path = Path.of(candidate);
      if (Files.exists(path)) {
        patchPaths.add(path);
      }
    }

    return patchPaths;
  }
}
