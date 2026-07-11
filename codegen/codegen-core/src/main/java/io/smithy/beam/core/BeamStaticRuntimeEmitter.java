package io.smithy.beam.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenException;

/** Copies packaged Erlang runtime sources into a Smithy build file manifest. */
public final class BeamStaticRuntimeEmitter {

  private BeamStaticRuntimeEmitter() {}

  public static void emit(
      FileManifest manifest,
      ClassLoader classLoader,
      BeamStaticRuntimeIndex.Requirements requirements) {
    if (requirements.modules().isEmpty()) {
      return;
    }
    for (BeamStaticRuntimeModule module : requirements.modules()) {
      String resourcePath = BeamStaticRuntimeCatalog.RESOURCE_PREFIX + module.resourcePath();
      String content = readResource(classLoader, resourcePath);
      manifest.writeFile(module.outputPath(), content);
    }
  }

  private static String readResource(ClassLoader classLoader, String resourcePath) {
    try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new CodegenException(
            "Missing Erlang runtime resource: "
                + resourcePath
                + ". Rebuild codegen-erlang so runtime/erlang is packaged.");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read runtime resource " + resourcePath, e);
    }
  }
}
