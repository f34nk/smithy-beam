package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.OperationReceiveSection;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Protocol integration for {@code aws.protocols#awsJson1_1}.
 *
 * <p>Identical transport semantics to {@link ErlangAwsJson10Integration}
 * (POST to {@code /}, {@code X-Amz-Target} header) but uses Content-Type
 * {@code application/x-amz-json-1.1} instead of {@code 1.0}.
 *
 * <p>Full codec logic is a Phase 2 follow-on. Stubs compile cleanly.
 */
public final class ErlangAwsJson11Integration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsJson1_1");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext context) {
        if (!isApplicable(context.model(), context.settings())) {
            return List.of();
        }
        return List.of(
                ErlangRestJson1Integration.sendInterceptor("awsJson1_1", "smithy_json",
                        ErlangDependency.SMITHY_JSON, ErlangDependency.SMITHY_HTTP_CLIENT),
                ErlangRestJson1Integration.receiveInterceptor("smithy_json"));
    }
}
