package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpBearerAuthTrait;

/**
 * Erlang auth integration for HTTP Bearer token authentication.
 *
 * <p>Emits an inline {@code Authorization: Bearer ...} header (no extra runtime
 * dependency).
 */
public final class ErlangBearerIntegration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return HttpBearerAuthTrait.ID;
    }
}
