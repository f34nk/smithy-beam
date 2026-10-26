package io.smithy.beam.core;

/** One Gleam runtime source file packaged under {@code runtime/gleam/} in the plugin JAR. */
public record BeamGleamStaticRuntimeModule(
    String resourcePath, String outputPath, String moduleName, RuntimeFeature feature)
    implements BeamPackagedRuntimeModule {

  public enum RuntimeFeature {
    HTTP_CLIENT,
    SIGV4,
    HTTP_CHECKSUM,
    EVENT_STREAM
  }

  public static final BeamGleamStaticRuntimeModule TYPES =
      module(
          "src/smithy_beam/types.gleam",
          "smithy_beam/types.gleam",
          "smithy_beam/types",
          RuntimeFeature.HTTP_CLIENT);

  public static final BeamGleamStaticRuntimeModule UTILS =
      module(
          "src/smithy_beam/utils.gleam",
          "smithy_beam/utils.gleam",
          "smithy_beam/utils",
          RuntimeFeature.HTTP_CLIENT);

  public static final BeamGleamStaticRuntimeModule HTTP =
      module(
          "src/smithy_beam/http.gleam",
          "smithy_beam/http.gleam",
          "smithy_beam/http",
          RuntimeFeature.HTTP_CLIENT);

  public static final BeamGleamStaticRuntimeModule HTTPC =
      module(
          "src/smithy_beam/httpc.gleam",
          "smithy_beam/httpc.gleam",
          "smithy_beam/httpc",
          RuntimeFeature.HTTP_CLIENT);

  private static BeamGleamStaticRuntimeModule module(
      String resourcePath, String outputPath, String moduleName, RuntimeFeature feature) {
    return new BeamGleamStaticRuntimeModule(resourcePath, outputPath, moduleName, feature);
  }
}
