package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import java.util.List;
import software.amazon.smithy.aws.traits.auth.SigV4ATrait;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang auth integration for AWS Signature Version 4A (asymmetric).
 *
 * <p>Adds the {@code SMITHY_SIGV4} runtime dependency.
 */
public final class ErlangSigV4AIntegration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return SigV4ATrait.ID;
    }

    @Override
    protected List<ErlangDependency> authDependencies() {
        return List.of(ErlangDependency.SMITHY_SIGV4);
    }
}
