package io.smithy.beam.erlang;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Erlang type generation.
 *
 * Plugin name in smithy-build.json: "erlang-types-codegen"
 *
 * Minimal smithy-build.json configuration:
 * {
 * "plugins": {
 * "erlang-types-codegen": {
 * "service": "smithy.beam.demo.basic#BasicService",
 * "edition": "2026"
 * }
 * }
 * }
 */
public final class ErlangTypesPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-types-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        new ErlangTypeGeneration().generate(context);
    }
}
