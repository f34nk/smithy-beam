package io.smithy.beam.erlang.codegen;

import software.amazon.smithy.codegen.core.SmithyIntegration;

/**
 * SPI boundary for all Erlang codegen integrations.
 *
 * <p>Both the client plugin and the server plugin use {@code ErlangIntegration.class}
 * as the {@code integrationClass} argument to {@code CodegenDirector}. An integration
 * written by a third party implements this interface once and is automatically
 * discovered by both plugins via {@link java.util.ServiceLoader}.
 */
public interface ErlangIntegration
        extends SmithyIntegration<ErlangSettings, ErlangWriter, ErlangContext> {
}
