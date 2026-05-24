package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Extension point for the Erlang code generator.
 *
 * <p>Implementations are discovered through {@link java.util.ServiceLoader} when registered
 * under {@code META-INF/services} for this interface. Ordering uses {@link #name},
 * {@link #priority}, {@link #runBefore}, and {@link #runAfter}. Multiple integrations must not
 * share the same {@link #name}.</p>
 *
 * <p>Keep AWS-specific protocol traits and signing details out of unconditional generator paths.
 * Gate protocol extensions behind traits on the service or explicit integration settings via
 * {@link #configure}.</p>
 */
public interface ErlangIntegration
        extends SmithyIntegration<BeamSettings, ErlangWriter, ErlangContext> {

    /**
     * Runs after the selected {@link io.smithy.beam.core.BeamProtocolCodegen} built-ins.
     */
    default void customizeProtocolSerialize(
            ErlangContext context,
            OperationShape operation,
            ErlangWriter writer) {
        // opt-in: integrations adjust generated serializers
    }

    /**
     * Runs after the selected {@link io.smithy.beam.core.BeamProtocolCodegen} built-ins.
     */
    default void customizeProtocolDeserialize(
            ErlangContext context,
            OperationShape operation,
            ErlangWriter writer) {
        // opt-in: integrations adjust generated deserializers
    }
}
