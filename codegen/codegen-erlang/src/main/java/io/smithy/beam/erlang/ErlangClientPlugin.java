package io.smithy.beam.erlang;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Erlang client generation.
 *
 * Plugin name in smithy-build.json: "erlang-client-codegen"
 *
 * Minimal smithy-build.json configuration:
 * {
 * "plugins": {
 * "erlang-client-codegen": {
 * "service": "smithy.beam.demo.basic#BasicService",
 * "edition": "2026",
 * "protocol": "aws.protocols#restJson1"
 * }
 * }
 * }
 */
public final class ErlangClientPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-client-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        new ErlangClientGeneration().generate(context);
    }
}
