package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamProtocolIntegration;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Extension point for the Elixir code generator.
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
public interface ElixirIntegration
        extends SmithyIntegration<BeamSettings, ElixirWriter, ElixirContext>,
                BeamProtocolIntegration {

    /**
     * Runs after the selected {@link io.smithy.beam.core.BeamProtocolCodegen} built-ins.
     */
    default void customizeProtocolSerialize(
            ElixirContext context,
            OperationShape operation,
            ElixirWriter writer) {
        // opt-in: integrations adjust generated serializers
    }

    /**
     * Runs after the selected {@link io.smithy.beam.core.BeamProtocolCodegen} built-ins.
     */
    default void customizeProtocolDeserialize(
            ElixirContext context,
            OperationShape operation,
            ElixirWriter writer) {
        // opt-in: integrations adjust generated deserializers
    }
}
