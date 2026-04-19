package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Erlang auth integration for AWS Signature Version 4.
 *
 * <p>Wraps the HTTP call body in {@code smithy_sigv4:sign(...)} via an
 * {@link OperationSendSection} interceptor. Adds the {@code SMITHY_SIGV4}
 * runtime dependency.
 */
public final class ErlangSigV4Integration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return SigV4Trait.ID;
    }

    @Override
    protected List<ErlangDependency> authDependencies() {
        return List.of(ErlangDependency.SMITHY_SIGV4);
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) -> {
                    writer.addDependency(ErlangDependency.SMITHY_SIGV4);
                    writer.write(
                            "%% TODO: smithy_sigv4:sign(Request, Credentials, Region, Service)");
                }));
    }
}
