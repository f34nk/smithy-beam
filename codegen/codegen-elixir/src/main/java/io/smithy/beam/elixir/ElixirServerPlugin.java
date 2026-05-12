package io.smithy.beam.elixir;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Elixir server generation.
 *
 * Plugin name in smithy-build.json: "elixir-server-codegen"
 */
public final class ElixirServerPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "elixir-server-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        new ElixirServerGeneration().generate(context);
    }
}
