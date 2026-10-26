package io.smithy.beam.gleam;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Gleam type generation.
 *
 * <p>Plugin name in smithy-build.json: "gleam-types-codegen"
 */
public final class GleamTypesPlugin implements SmithyBuildPlugin {

  @Override
  public String getName() {
    return "gleam-types-codegen";
  }

  @Override
  public void execute(PluginContext context) {
    new GleamTypeGeneration().generate(context);
  }
}
