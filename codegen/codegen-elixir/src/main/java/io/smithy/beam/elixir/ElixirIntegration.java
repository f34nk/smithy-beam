package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.SmithyIntegration;

/**
 * Extension point for the Elixir code generator.
 *
 * Implementations are discovered via Java SPI. Simply being on the classpath
 * must NOT activate an integration -- opt-in via traits or smithy-build.json
 * flags.
 */
public interface ElixirIntegration
        extends SmithyIntegration<BeamSettings, ElixirWriter, ElixirContext> {
    // No additional methods for the types-only baseline.
    // Future: add protocol hooks, custom serialization, etc.
}
