package io.smithy.beam.gleam;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Gleam client generation.
 *
 * <p>Plugin name in smithy-build.json: "gleam-client-codegen"
 */
public final class GleamClientPlugin implements SmithyBuildPlugin {

  @Override
  public String getName() {
    return "gleam-client-codegen";
  }

  @Override
  public void execute(PluginContext context) {
    new GleamClientGeneration().generate(context);
  }
}
