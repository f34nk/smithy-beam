package io.smithy.beam.elixir.codegen;

import software.amazon.smithy.codegen.core.SmithyIntegration;

/**
 * SPI boundary for all Elixir codegen integrations.
 *
 * <p>Both the client plugin and the server plugin use {@code ElixirIntegration.class}
 * as the {@code integrationClass} argument to {@code CodegenDirector}. An integration
 * written by a third party implements this interface once and is automatically
 * discovered by both plugins via {@link java.util.ServiceLoader}.
 */
public interface ElixirIntegration
        extends SmithyIntegration<ElixirSettings, ElixirWriter, ElixirContext> {
}
