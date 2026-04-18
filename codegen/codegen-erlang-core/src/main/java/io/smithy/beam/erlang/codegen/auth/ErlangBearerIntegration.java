package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Auth integration for {@code smithy.api#httpBearerAuth}.
 *
 * <p>Bearer token authentication adds an {@code Authorization: Bearer <token>}
 * HTTP header to every request. The token is retrieved from the
 * {@code Config} map via {@code maps:get(bearer_token, Config)}.
 *
 * <p>No additional runtime module dependency is needed — the header is set
 * inline in the generated code. Phase 2 may introduce a shared helper if
 * token refresh logic is added.
 */
public final class ErlangBearerIntegration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return ShapeId.from("smithy.api#httpBearerAuth");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext context) {
        if (!isApplicable(context.model(), context.settings())) {
            return List.of();
        }
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) -> {
                    String opName = section.operation().getId().getName().toLowerCase();
                    writer.write("    %% Bearer auth: attach Authorization header for $L", opName);
                    writer.write("    _Token = maps:get(bearer_token, Config),");
                    writer.write(
                            "    _AuthHeader = {<<\"Authorization\">>, <<\"Bearer \", _Token/binary>>},");
                }));
    }
}
