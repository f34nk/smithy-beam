package io.smithy.beam.core;

/** One runtime source file packaged under a plugin JAR resource prefix. */
public interface BeamPackagedRuntimeModule {

  String resourcePath();

  String outputPath();
}
