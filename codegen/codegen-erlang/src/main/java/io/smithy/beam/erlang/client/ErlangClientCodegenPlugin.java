package io.smithy.beam.erlang.client;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;

/**
 * Smithy build plugin that drives Erlang client code generation.
 *
 * <p>Plugin name: {@code "erlang-client-codegen"}.
 */
public final class ErlangClientCodegenPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-client-codegen";
    }

    @Override
    public void execute(PluginContext ctx) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
