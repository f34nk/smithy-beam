package io.smithy.beam.gleam;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Gleam server generation.
 *
 * <p>Plugin name in smithy-build.json: "gleam-server-codegen"
 */
public final class GleamServerPlugin implements SmithyBuildPlugin {

  @Override
  public String getName() {
    return "gleam-server-codegen";
  }

  @Override
  public void execute(PluginContext context) {
    new GleamServerGeneration().generate(context);
  }
}
