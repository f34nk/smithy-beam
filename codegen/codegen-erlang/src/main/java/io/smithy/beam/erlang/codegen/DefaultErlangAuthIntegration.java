package io.smithy.beam.erlang.codegen;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Base class for all Erlang auth-scheme integrations.
 *
 * <p>Identical structure to {@link DefaultErlangProtocolIntegration} but gated
 * on an auth-trait {@link ShapeId}.
 *
 */
public abstract class DefaultErlangAuthIntegration implements ErlangIntegration {

    /** Returns the Smithy auth-trait shape ID this integration handles. */
    public abstract ShapeId authTraitId();

    /**
     * Returns {@code true} if the service being generated has the auth trait
     * this integration targets.
     */
    protected boolean isApplicable(ErlangContext ctx) {
        return ctx.service().hasTrait(authTraitId());
    }
}
