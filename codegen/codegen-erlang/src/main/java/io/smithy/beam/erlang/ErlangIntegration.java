package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.SmithyIntegration;

/**
 * Extension point for the Erlang code generator.
 *
 * Implementations are discovered via Java SPI. Registering a class on the
 * classpath does NOT activate it -- integrations must be opted in via a trait
 * in the model or a feature flag in smithy-build.json.
 */
public interface ErlangIntegration
        extends SmithyIntegration<BeamSettings, ErlangWriter, ErlangContext> {
    // No additional methods for the types-only baseline.
    // Future: add methods for protocol support, custom serialization, etc.
}
