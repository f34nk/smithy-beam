package io.smithy.beam.core;

import software.amazon.smithy.build.FileManifest;

/** Copies packaged Gleam runtime sources into a Smithy build file manifest. */
public final class BeamGleamStaticRuntimeEmitter {

  private BeamGleamStaticRuntimeEmitter() {}

  public static void emit(
      FileManifest manifest,
      ClassLoader classLoader,
      BeamGleamStaticRuntimeIndex.Requirements requirements) {
    BeamStaticRuntimeEmitter.emit(
        manifest,
        classLoader,
        BeamGleamStaticRuntimeCatalog.RESOURCE_PREFIX,
        requirements.modules(),
        "Gleam");
  }
}
