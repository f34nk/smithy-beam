package io.smithy.beam.erlang.server;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy build plugin that drives Erlang server code generation.
 *
 * <p>Plugin name: {@code "erlang-server-codegen"}.
 */
public final class ErlangServerCodegenPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-server-codegen";
    }

    @Override
    public void execute(PluginContext ctx) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
