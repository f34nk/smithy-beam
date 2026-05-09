package io.smithy.beam.erlang;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy-Build plugin entry point for Erlang server generation.
 *
 * Plugin name in smithy-build.json: "erlang-server-codegen"
 */
public final class ErlangServerPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-server-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        new ErlangServerGeneration().generate(context);
    }
}
