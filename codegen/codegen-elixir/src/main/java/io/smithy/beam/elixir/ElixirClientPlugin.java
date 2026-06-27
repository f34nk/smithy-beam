package io.smithy.beam.elixir;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Elixir client generation.
 *
 * <p>Plugin name in smithy-build.json: "elixir-client-codegen"
 *
 * <p>Minimal smithy-build.json configuration: { "plugins": { "elixir-client-codegen": { "service":
 * "smithy.beam.demo.basic#BasicService", "edition": "2026", "protocol": "aws.protocols#restJson1" }
 * } }
 */
public final class ElixirClientPlugin implements SmithyBuildPlugin {

  @Override
  public String getName() {
    return "elixir-client-codegen";
  }

  @Override
  public void execute(PluginContext context) {
    new ElixirClientGeneration().generate(context);
  }
}
