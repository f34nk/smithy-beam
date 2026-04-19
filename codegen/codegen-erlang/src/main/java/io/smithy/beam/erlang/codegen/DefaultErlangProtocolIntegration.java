package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.ProtocolResolver;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Base class for all Erlang protocol integrations.
 *
 * <p>Subclasses declare a {@link #protocolId()} and guard their customisations
 * with {@link #isApplicable(ErlangContext)}.
 *
 */
public abstract class DefaultErlangProtocolIntegration implements ErlangIntegration {

    /** Returns the Smithy protocol shape ID this integration handles. */
    public abstract ShapeId protocolId();

    /**
     * Returns {@code true} if the service being generated uses this integration's
     * protocol.
     */
    protected boolean isApplicable(ErlangContext ctx) {
        return ProtocolResolver.resolve(protocolId())
                .map(traitClass -> ctx.service().hasTrait(traitClass))
                .orElse(false);
    }
}
