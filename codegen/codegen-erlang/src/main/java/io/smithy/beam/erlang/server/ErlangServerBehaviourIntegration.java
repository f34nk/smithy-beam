package io.smithy.beam.erlang.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.ModuleAttributesSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Injects the {@code -behaviour(smithy_handler).} attribute into server modules.
 *
 * <p>Guards on {@link Mode#SERVER} so it is safe to register in the shared
 * {@code ErlangIntegration} SPI file — when the client plugin runs the mode will
 * be {@link Mode#CLIENT} and this integration becomes a no-op.
 *
 * <p>Uses the {@link #interceptors} hook so the behaviour attribute is written
 * into the {@link ModuleAttributesSection} injection point created by
 * {@link ErlangServerCodegen#generateService}, before any other content in the
 * generated server module.
 */
public final class ErlangServerBehaviourIntegration implements ErlangIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (ctx.settings().mode() != Mode.SERVER) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(ModuleAttributesSection.class, (writer, section) ->
                        writer.write("-behaviour(smithy_handler).")));
    }
}
