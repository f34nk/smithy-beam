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
 * Auth integration for {@code aws.auth#sigv4a}.
 *
 * <p>SigV4A (asymmetric SigV4) is an extension of SigV4 that supports
 * multi-region signing. The signing call delegates to
 * {@code smithy_sigv4:sign_asymmetric/2} in the runtime module.
 *
 * <p>The signing call is a stub for MVP. Phase 2 fills in full asymmetric
 * credential derivation via the runtime module {@code smithy_sigv4.erl}.
 */
public final class ErlangSigV4AIntegration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return ShapeId.from("aws.auth#sigv4a");
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
                    writer.write("    %% SigV4A: asymmetric-sign request before dispatch for $L",
                            opName);
                    writer.write(
                            "    _SignedRequest = smithy_sigv4:sign_asymmetric(Config, _Request),");
                }));
    }
}
