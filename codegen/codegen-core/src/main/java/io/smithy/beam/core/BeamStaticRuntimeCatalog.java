package io.smithy.beam.core;

import java.util.List;

/** All Erlang runtime files copied from the plugin JAR. */
public final class BeamStaticRuntimeCatalog {

  public static final String RESOURCE_PREFIX = "runtime/erlang/";

  private static final List<BeamStaticRuntimeModule> ALL =
      List.of(
          BeamStaticRuntimeModule.HTTP_TYPES,
          BeamStaticRuntimeModule.HTTP_RUNTIME,
          BeamStaticRuntimeModule.UTILS,
          BeamStaticRuntimeModule.AWS_SIGV4,
          BeamStaticRuntimeModule.HTTP_CHECKSUM,
          BeamStaticRuntimeModule.AWS_EVENT_STREAM);

  private BeamStaticRuntimeCatalog() {}

  public static List<BeamStaticRuntimeModule> allModules() {
    return ALL;
  }
}
