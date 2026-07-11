package io.smithy.beam.core;

/** One Erlang runtime source file packaged under {@code runtime/erlang/} in the plugin JAR. */
public record BeamStaticRuntimeModule(
    String resourcePath, String outputPath, String moduleName, RuntimeFeature feature) {

  public enum RuntimeFeature {
    HTTP_CLIENT,
    SIGV4,
    HTTP_CHECKSUM,
    EVENT_STREAM
  }

  public static final BeamStaticRuntimeModule HTTP_TYPES =
      module(
          "src/runtime_types.hrl",
          "runtime_types.hrl",
          "runtime_types",
          RuntimeFeature.HTTP_CLIENT);

  public static final BeamStaticRuntimeModule HTTP_RUNTIME =
      module(
          "src/runtime_http.erl", "runtime_http.erl", "runtime_http", RuntimeFeature.HTTP_CLIENT);

  public static final BeamStaticRuntimeModule UTILS =
      module("src/utils.erl", "utils.erl", "utils", RuntimeFeature.HTTP_CLIENT);

  public static final BeamStaticRuntimeModule AWS_SIGV4 =
      module("src/aws_sigv4.erl", "aws_sigv4.erl", "aws_sigv4", RuntimeFeature.SIGV4);

  public static final BeamStaticRuntimeModule HTTP_CHECKSUM =
      module(
          "src/http_checksum.erl",
          "http_checksum.erl",
          "http_checksum",
          RuntimeFeature.HTTP_CHECKSUM);

  public static final BeamStaticRuntimeModule AWS_EVENT_STREAM =
      module(
          "src/aws_event_stream.erl",
          "aws_event_stream.erl",
          "aws_event_stream",
          RuntimeFeature.EVENT_STREAM);

  private static BeamStaticRuntimeModule module(
      String resourcePath, String outputPath, String moduleName, RuntimeFeature feature) {
    return new BeamStaticRuntimeModule(resourcePath, outputPath, moduleName, feature);
  }
}
