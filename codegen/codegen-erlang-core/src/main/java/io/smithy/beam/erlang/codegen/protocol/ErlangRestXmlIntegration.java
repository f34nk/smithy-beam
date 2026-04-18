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
 * Protocol integration for {@code aws.protocols#restXml}.
 *
 * <p>REST-XML uses the {@code @http} trait to determine the HTTP method and
 * URI pattern for each operation. The body is XML-encoded via
 * {@code smithy_xml}.
 *
 * <p>Full codec logic (HTTP binding extraction from {@code HttpBindingIndex},
 * {@code @httpLabel}, {@code @httpQuery}, {@code @httpPayload}) is a Phase 2
 * follow-on. Stubs compile cleanly.
 */
public final class ErlangRestXmlIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restXml");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext context) {
        if (!isApplicable(context.model(), context.settings())) {
            return List.of();
        }
        return List.of(
                ErlangRestJson1Integration.sendInterceptor("restXml", "smithy_xml",
                        ErlangDependency.SMITHY_XML, ErlangDependency.SMITHY_HTTP_CLIENT),
                ErlangRestJson1Integration.receiveInterceptor("smithy_xml"));
    }
}
