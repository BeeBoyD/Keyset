/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.targets;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.ServiceRunner;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
abstract class CommonDevLaunchHandler extends CommonLaunchHandler {
  protected CommonDevLaunchHandler(LaunchType type, String prefix) {
    super(type, prefix);
  }

  @Override
  public String getNaming() {
    return "mcp";
  }

  @Override
  public boolean isProduction() {
    return false;
  }

  @Override
  protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
    super.preLaunch(arguments, layer);

    if (getDist().isDedicatedServer()) {
      return arguments;
    }

    if (isData()) {
      return arguments;
    }

    var args = ArgumentList.from(arguments);

    String username = args.get("username");
    if (username != null) {
      Matcher matcher = Pattern.compile("#+").matcher(username);
      StringBuilder replaced = new StringBuilder();
      while (matcher.find()) {
        matcher.appendReplacement(replaced, getRandomNumbers(matcher.group().length()));
      }
      matcher.appendTail(replaced);
      args.put("username", replaced.toString());
    } else {
      args.putLazy("username", "Dev");
    }

    if (!args.hasValue("accessToken")) {
      args.put("accessToken", "0");
    }

    return args.getArguments();
  }

  private static String getRandomNumbers(int length) {
    return Long.toString(System.nanoTime() % (int) Math.pow(10, length));
  }

  @Override
  protected ServiceRunner makeService(final String[] arguments, final ModuleLayer gameLayer) {
    return super.makeService(preLaunch(arguments, gameLayer), gameLayer);
  }

  protected static String[] findClassPath() {
    var classpath = System.getProperty("legacyClassPath");
    if (classpath == null) {
      classpath = System.getProperty("java.class.path");
    }
    return classpath.split(File.pathSeparator);
  }

  protected static Path findJarOnClasspath(String[] classpath, String match) {
    String ret = null;

    for (var entry : classpath) {
      int idx = entry.lastIndexOf(File.separatorChar);
      if (idx == -1) {
        continue;
      }

      var name = entry.substring(idx + 1);
      if (name.startsWith(match)) {
        ret = entry;
        break;
      }
    }

    if (ret == null) {
      throw new IllegalStateException("Could not find " + match + " in classpath");
    }

    return Paths.get(ret);
  }

  // TODO: [Forge][UFS][DEV] Make Forge and MC separate sources at dev time so we don't have to
  // filter.
  protected static Path getMinecraftOnly(Path extra, Path forge) {
    var packages = getPackages();
    var extraPath = extra.toString().replace('\\', '/');

    BiPredicate<String, String> mcFilter =
        (path, base) -> {
          if (base.equals(extraPath) || path.endsWith("/")) {
            return true;
          }
          for (var pkg : packages) {
            if (path.startsWith(pkg)) {
              return false;
            }
          }
          return true;
        };

    // Use client-extra as the union base so the synthetic jar keeps Minecraft metadata instead of
    // inheriting Forge's Automatic-Module-Name from the merged Loom jar.
    var fs = UnionHelper.newFileSystem(mcFilter, new Path[] {extra, forge});
    return fs.getRootDirectories().iterator().next();
  }

  // TODO: [Forge][UFS][DEV] Make Forge and MC separate sources at dev time so we don't have to
  // filter.
  protected static Path getForgeOnly(Path forge) {
    var packages = getPackages();
    var modJar =
        SecureJar.from(
            (path, base) -> {
              if (!path.endsWith(".class")) {
                return true;
              }
              for (var pkg : packages) {
                if (path.startsWith(pkg)) {
                  return true;
                }
              }
              return false;
            },
            new Path[] {forge});

    return modJar.getRootPath();
  }

  private static String[] getPackages() {
    return new String[] {
      "net/minecraftforge/", "META-INF/services/", "META-INF/coremods.json", "META-INF/mods.toml"
    };
  }
}
