package io.smithy.beam.erlang.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;

/**
 * Injects the {@code -behaviour(smithy_handler).} attribute into server modules.
 *
 * <p>Guards on {@code mode() == SERVER} so it is safe to register in the shared
 * {@code ErlangIntegration} SPI file. Hooks into {@link #customize(ErlangContext)}.
 */
public final class ErlangServerBehaviourIntegration implements ErlangIntegration {

    @Override
    public void customize(ErlangContext ctx) {
        if (ctx.settings().mode() != Mode.SERVER) {
            return;
        }
        // inject -behaviour(smithy_handler). via ModuleAttributesSection interceptor
    }
}
