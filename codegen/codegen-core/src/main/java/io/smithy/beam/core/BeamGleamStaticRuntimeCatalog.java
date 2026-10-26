package io.smithy.beam.core;

import java.util.List;

/** All Gleam runtime files copied from the plugin JAR. */
public final class BeamGleamStaticRuntimeCatalog {

  public static final String RESOURCE_PREFIX = "runtime/gleam/";

  private static final List<BeamGleamStaticRuntimeModule> ALL =
      List.of(
          BeamGleamStaticRuntimeModule.TYPES,
          BeamGleamStaticRuntimeModule.UTILS,
          BeamGleamStaticRuntimeModule.HTTP,
          BeamGleamStaticRuntimeModule.HTTPC);

  private BeamGleamStaticRuntimeCatalog() {}

  public static List<BeamGleamStaticRuntimeModule> allModules() {
    return ALL;
  }
}
