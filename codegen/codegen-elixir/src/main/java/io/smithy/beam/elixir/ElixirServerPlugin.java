package io.smithy.beam.elixir;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Elixir server generation.
 *
 * Plugin name in smithy-build.json: "elixir-server-codegen"
 *
 * Minimal smithy-build.json configuration:
 * {
 * "plugins": {
 * "elixir-server-codegen": {
 * "service": "smithy.beam.demo.basic#BasicService",
 * "edition": "2026",
 * "protocol": "aws.protocols#restJson1"
 * }
 * }
 * }
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
