package io.smithy.beam.core;

import software.amazon.smithy.build.FileManifest;

/** Copies packaged Elixir runtime sources into a Smithy build file manifest. */
public final class BeamElixirStaticRuntimeEmitter {

  private BeamElixirStaticRuntimeEmitter() {}

  public static void emit(
      FileManifest manifest,
      ClassLoader classLoader,
      BeamElixirStaticRuntimeIndex.Requirements requirements) {
    BeamStaticRuntimeEmitter.emit(
        manifest,
        classLoader,
        BeamElixirStaticRuntimeCatalog.RESOURCE_PREFIX,
        requirements.modules(),
        "Elixir");
  }
}
