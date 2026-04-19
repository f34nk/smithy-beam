package io.smithy.beam.elixir.codegen.auth;

import io.smithy.beam.elixir.codegen.DefaultElixirAuthIntegration;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.sections.OperationSendSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpBearerAuthTrait;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Elixir auth integration for HTTP Bearer token authentication.
 *
 * <p>Emits an inline {@code Authorization: Bearer ...} header stub (no extra
 * runtime dependency; the header is constructed inline by the generated code).
 */
public final class ElixirBearerIntegration extends DefaultElixirAuthIntegration {

    @Override
    public ShapeId authTraitId() {
        return HttpBearerAuthTrait.ID;
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) ->
                        writer.write(
                                "# TODO: add {\"authorization\", \"Bearer \" <> token} header")));
    }
}
