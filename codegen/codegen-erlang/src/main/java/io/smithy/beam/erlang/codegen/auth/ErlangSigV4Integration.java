package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang auth integration for AWS Signature Version 4.
 *
 * <p>Wraps the HTTP call body in {@code smithy_sigv4:sign(...)} via an
 * {@code OperationSendSection} interceptor. Adds {@code SMITHY_SIGV4} dependency.
 */
public final class ErlangSigV4Integration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return SigV4Trait.ID;
    }
}
