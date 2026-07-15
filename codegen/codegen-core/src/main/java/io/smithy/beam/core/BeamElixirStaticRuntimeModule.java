package io.smithy.beam.core;

/** One Elixir runtime source file packaged under {@code runtime/elixir/} in the plugin JAR. */
public record BeamElixirStaticRuntimeModule(
    String resourcePath, String outputPath, String moduleName, RuntimeFeature feature)
    implements BeamPackagedRuntimeModule {

  public enum RuntimeFeature {
    HTTP_CLIENT,
    SIGV4,
    HTTP_CHECKSUM,
    EVENT_STREAM
  }

  public static final BeamElixirStaticRuntimeModule HTTP_TYPES =
      module(
          "lib/runtime_types.ex", "runtime_types.ex", "RuntimeTypes", RuntimeFeature.HTTP_CLIENT);

  public static final BeamElixirStaticRuntimeModule HTTP_RUNTIME =
      module("lib/runtime_http.ex", "runtime_http.ex", "RuntimeHttp", RuntimeFeature.HTTP_CLIENT);

  public static final BeamElixirStaticRuntimeModule UTILS =
      module(
          "lib/runtime_utils.ex", "runtime_utils.ex", "RuntimeUtils", RuntimeFeature.HTTP_CLIENT);

  public static final BeamElixirStaticRuntimeModule AWS_SIGV4 =
      module("lib/aws_sigv4.ex", "aws_sigv4.ex", "AwsSigv4", RuntimeFeature.SIGV4);

  public static final BeamElixirStaticRuntimeModule HTTP_CHECKSUM =
      module(
          "lib/http_checksum.ex", "http_checksum.ex", "HttpChecksum", RuntimeFeature.HTTP_CHECKSUM);

  public static final BeamElixirStaticRuntimeModule AWS_EVENT_STREAM =
      module(
          "lib/aws_event_stream.ex",
          "aws_event_stream.ex",
          "AwsEventStream",
          RuntimeFeature.EVENT_STREAM);

  private static BeamElixirStaticRuntimeModule module(
      String resourcePath, String outputPath, String moduleName, RuntimeFeature feature) {
    return new BeamElixirStaticRuntimeModule(resourcePath, outputPath, moduleName, feature);
  }
}
