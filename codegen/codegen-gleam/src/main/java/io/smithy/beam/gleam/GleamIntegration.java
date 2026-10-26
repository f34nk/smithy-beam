package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamProtocolIntegration;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Extension point for the Gleam code generator.
 *
 * <p>Implementations are discovered through {@link java.util.ServiceLoader} when registered under
 * {@code META-INF/services} for this interface. Ordering uses {@link #name}, {@link #priority},
 * {@link #runBefore}, and {@link #runAfter}. Multiple integrations must not share the same {@link
 * #name}.
 */
public interface GleamIntegration
    extends SmithyIntegration<BeamSettings, GleamWriter, GleamContext>, BeamProtocolIntegration {

  /** Runs after the selected {@link io.smithy.beam.core.BeamProtocolCodegen} built-ins. */
  default void customizeProtocolSerialize(
      GleamContext context, OperationShape operation, GleamWriter writer) {
    // opt-in: integrations adjust generated serializers
  }

  /** Runs after the selected {@link io.smithy.beam.core.BeamProtocolCodegen} built-ins. */
  default void customizeProtocolDeserialize(
      GleamContext context, OperationShape operation, GleamWriter writer) {
    // opt-in: integrations adjust generated deserializers
  }
}
