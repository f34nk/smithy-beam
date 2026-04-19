package io.smithy.beam.elixir.codegen.auth;

import io.smithy.beam.elixir.codegen.DefaultElixirAuthIntegration;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import java.util.List;
import software.amazon.smithy.aws.traits.auth.SigV4ATrait;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir auth integration for AWS Signature Version 4A (asymmetric).
 *
 * <p>Adds the {@code SMITHY_SIGV4} runtime dependency.
 */
public final class ElixirSigV4AIntegration extends DefaultElixirAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return SigV4ATrait.ID;
    }

    @Override
    protected List<ElixirDependency> authDependencies() {
        return List.of(ElixirDependency.SMITHY_SIGV4);
    }
}
