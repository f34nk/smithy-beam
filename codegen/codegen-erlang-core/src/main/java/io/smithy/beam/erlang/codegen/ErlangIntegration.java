package io.smithy.beam.erlang.codegen;

import software.amazon.smithy.codegen.core.SmithyIntegration;

/**
 * SPI boundary for all Erlang codegen integrations.
 *
 * <p>This is a marker interface that extends
 * {@link SmithyIntegration} typed on the Erlang-specific writer and context
 * types. All default implementations from {@code SmithyIntegration} are
 * inherited, so implementors only need to override the hooks they care about.
 *
 * <p>Implementations are discovered via the standard Java {@link java.util.ServiceLoader}
 * mechanism. Register concrete classes in:
 * <pre>
 * META-INF/services/io.smithy.beam.erlang.codegen.ErlangIntegration
 * </pre>
 */
public interface ErlangIntegration
        extends SmithyIntegration<ErlangSettings, ErlangWriter, ErlangContext> {
}
