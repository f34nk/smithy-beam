package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import software.amazon.smithy.aws.traits.auth.SigV4ATrait;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang auth integration for AWS Signature Version 4A (asymmetric).
 *
 * <p>Adds {@code SMITHY_SIGV4} dependency.
 */
public final class ErlangSigV4AIntegration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return SigV4ATrait.ID;
    }
}
