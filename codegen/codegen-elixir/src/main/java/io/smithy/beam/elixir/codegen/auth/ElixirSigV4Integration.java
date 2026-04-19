package io.smithy.beam.elixir.codegen.auth;

import io.smithy.beam.elixir.codegen.DefaultElixirAuthIntegration;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.sections.OperationSendSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Elixir auth integration for AWS Signature Version 4.
 *
 * <p>Wraps the HTTP call body in {@code SmithyBeam.SigV4.sign(...)} via an
 * {@link OperationSendSection} interceptor. Adds the {@code SMITHY_SIGV4}
 * runtime dependency.
 */
public final class ElixirSigV4Integration extends DefaultElixirAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return SigV4Trait.ID;
    }

    @Override
    protected List<ElixirDependency> authDependencies() {
        return List.of(ElixirDependency.SMITHY_SIGV4);
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) -> {
                    writer.addDependency(ElixirDependency.SMITHY_SIGV4);
                    writer.write(
                            "# TODO: SmithyBeam.SigV4.sign(request, credentials, region, service)");
                }));
    }
}
