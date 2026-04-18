package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Auth integration for {@code aws.auth#sigv4}.
 *
 * <p>Activates only when the target service carries the {@code @sigv4} auth
 * trait. When active, it registers an {@code OperationSendSection}
 * interceptor on every operation that wraps the outgoing HTTP request in a
 * {@code smithy_sigv4:sign/2} call before it is dispatched by
 * {@code smithy_http_client}.
 *
 * <p>The signing call is a stub for MVP — the actual SigV4 credential
 * derivation and canonical-request construction live in the runtime module
 * {@code smithy_sigv4.erl} and are filled in as part of Phase 2.
 */
public final class ErlangSigV4Integration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return ShapeId.from("aws.auth#sigv4");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext context) {
        if (!isApplicable(context.model(), context.settings())) {
            return List.of();
        }
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) -> {
                    writer.addDependency(ErlangDependency.SMITHY_SIGV4);
                    String opName = section.operation().getId().getName().toLowerCase();
                    writer.write("    %% SigV4: sign request before dispatch for $L", opName);
                    writer.write("    _SignedRequest = smithy_sigv4:sign(Config, _Request),");
                }));
    }
}
