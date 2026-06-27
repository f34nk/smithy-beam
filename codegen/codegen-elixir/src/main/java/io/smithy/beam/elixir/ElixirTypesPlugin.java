package io.smithy.beam.elixir;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Elixir type generation.
 *
 * <p>Plugin name in smithy-build.json: "elixir-types-codegen"
 *
 * <p>Minimal smithy-build.json configuration: { "plugins": { "elixir-types-codegen": { "service":
 * "smithy.beam.demo.basic#BasicService", "edition": "2026" } } }
 */
public final class ElixirTypesPlugin implements SmithyBuildPlugin {

  @Override
  public String getName() {
    return "elixir-types-codegen";
  }

  @Override
  public void execute(PluginContext context) {
    new ElixirTypeGeneration().generate(context);
  }
}
