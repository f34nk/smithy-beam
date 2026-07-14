package io.smithy.beam.core;

import java.util.List;

/** All Elixir runtime files copied from the plugin JAR. */
public final class BeamElixirStaticRuntimeCatalog {

  public static final String RESOURCE_PREFIX = "runtime/elixir/";

  private static final List<BeamElixirStaticRuntimeModule> ALL =
      List.of(
          BeamElixirStaticRuntimeModule.HTTP_TYPES,
          BeamElixirStaticRuntimeModule.HTTP_RUNTIME,
          BeamElixirStaticRuntimeModule.UTILS,
          BeamElixirStaticRuntimeModule.AWS_SIGV4,
          BeamElixirStaticRuntimeModule.HTTP_CHECKSUM,
          BeamElixirStaticRuntimeModule.AWS_EVENT_STREAM);

  private BeamElixirStaticRuntimeCatalog() {}

  public static List<BeamElixirStaticRuntimeModule> allModules() {
    return ALL;
  }
}
