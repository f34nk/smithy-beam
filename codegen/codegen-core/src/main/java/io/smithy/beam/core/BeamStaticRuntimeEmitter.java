package io.smithy.beam.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenException;

/** Copies packaged runtime sources into a Smithy build file manifest. */
public final class BeamStaticRuntimeEmitter {

  private BeamStaticRuntimeEmitter() {}

  public static void emit(
      FileManifest manifest,
      ClassLoader classLoader,
      BeamStaticRuntimeIndex.Requirements requirements) {
    emit(
        manifest,
        classLoader,
        BeamStaticRuntimeCatalog.RESOURCE_PREFIX,
        requirements.modules(),
        "Erlang");
  }

  static void emit(
      FileManifest manifest,
      ClassLoader classLoader,
      String resourcePrefix,
      List<? extends BeamPackagedRuntimeModule> modules,
      String runtimeName) {
    if (modules.isEmpty()) {
      return;
    }
    for (BeamPackagedRuntimeModule module : modules) {
      String resourcePath = resourcePrefix + module.resourcePath();
      String content = readResource(classLoader, resourcePath, runtimeName);
      manifest.writeFile(module.outputPath(), content);
    }
  }

  private static String readResource(
      ClassLoader classLoader, String resourcePath, String runtimeName) {
    try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new CodegenException(
            "Missing "
                + runtimeName
                + " runtime resource: "
                + resourcePath
                + ". Rebuild the codegen plugin so runtime/"
                + runtimeName.toLowerCase()
                + " is packaged.");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read runtime resource " + resourcePath, e);
    }
  }
}
