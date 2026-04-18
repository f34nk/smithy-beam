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
 * Protocol integration for {@code aws.protocols#awsQuery}.
 *
 * <p>AWS Query protocol always sends HTTP POST to {@code /} with an
 * {@code application/x-www-form-urlencoded} body. The {@code Action}
 * and {@code Version} query parameters are added to every request.
 * The body is encoded via {@code smithy_query}.
 *
 * <p>Full codec logic is a Phase 2 follow-on. Stubs compile cleanly.
 */
public final class ErlangAwsQueryIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsQuery");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext context) {
        if (!isApplicable(context.model(), context.settings())) {
            return List.of();
        }
        return List.of(
                ErlangRestJson1Integration.sendInterceptor("awsQuery", "smithy_query",
                        ErlangDependency.SMITHY_QUERY, ErlangDependency.SMITHY_HTTP_CLIENT),
                ErlangRestJson1Integration.receiveInterceptor("smithy_query"));
    }
}
