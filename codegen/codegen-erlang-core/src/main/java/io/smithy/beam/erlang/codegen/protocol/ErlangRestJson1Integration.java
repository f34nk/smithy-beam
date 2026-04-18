package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangSettings;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.OperationReceiveSection;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Protocol integration for {@code aws.protocols#restJson1}.
 *
 * <p>Activates only when the target service carries the {@code @restJson1}
 * protocol trait. When active, it registers {@code OperationSendSection}
 * and {@code OperationReceiveSection} interceptors that emit stub
 * serialisation/deserialisation function bodies using {@code smithy_json}
 * and {@code smithy_http_client} as the codec and transport modules.
 *
 * <p>Full codec logic (HTTP bindings, {@code @http} trait extraction,
 * label/query/header/payload routing) is a Phase 2 follow-on task.
 * The stubs produced here are syntactically valid Erlang and compile
 * cleanly so the end-to-end pipeline can be proved before codec logic
 * is filled in.
 */
public final class ErlangRestJson1Integration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restJson1");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext context) {
        if (!isApplicable(context.model(), context.settings())) {
            return List.of();
        }
        return List.of(
                sendInterceptor("restJson1", "smithy_json",
                        ErlangDependency.SMITHY_JSON, ErlangDependency.SMITHY_HTTP_CLIENT),
                receiveInterceptor("smithy_json"));
    }

    static CodeInterceptor<OperationSendSection, ErlangWriter> sendInterceptor(
            String protocol, String codecModule,
            ErlangDependency... deps) {
        return CodeInterceptor.appender(OperationSendSection.class, (writer, section) -> {
            for (ErlangDependency dep : deps) {
                writer.addDependency(dep);
            }
            String opName = section.operation().getId().getName().toLowerCase();
            writer.write("    %% TODO($L): serialise $L input and send HTTP request via smithy_http_client",
                    protocol, opName);
            writer.write("    _Encoded = $L:encode(Input),", codecModule);
            writer.write("    {ok, _Response} = smithy_http_client:request(Config, _Encoded).");
        });
    }

    static CodeInterceptor<OperationReceiveSection, ErlangWriter> receiveInterceptor(
            String codecModule) {
        return CodeInterceptor.appender(OperationReceiveSection.class, (writer, section) -> {
            String opName = section.operation().getId().getName().toLowerCase();
            writer.write("    %% TODO: decode $L response via $L", opName, codecModule);
            writer.write("    {ok, $L:decode(_Response)}.", codecModule);
        });
    }
}
