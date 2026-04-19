package io.smithy.beam.erlang.codegen.auth;

import io.smithy.beam.erlang.codegen.DefaultErlangAuthIntegration;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpBearerAuthTrait;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Erlang auth integration for HTTP Bearer token authentication.
 *
 * <p>Emits an inline {@code Authorization: Bearer ...} header stub (no extra
 * runtime dependency; the header is constructed inline by the generated code).
 */
public final class ErlangBearerIntegration extends DefaultErlangAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return HttpBearerAuthTrait.ID;
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) ->
                        writer.write(
                                "%% TODO: add {<<\"authorization\">>, <<\"Bearer \", Token/binary>>} header")));
    }
}
