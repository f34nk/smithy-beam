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
 * Protocol integration for {@code aws.protocols#ec2Query}.
 *
 * <p>EC2 Query uses the same form-encoded POST-to-{@code /} transport as
 * {@link ErlangAwsQueryIntegration}, but member serialisation follows EC2
 * naming conventions (capitalised query parameter names, no list prefixes).
 * The body is encoded via {@code smithy_query} (same module, different
 * serialisation mode configurable at runtime).
 *
 * <p>Full codec logic is a Phase 2 follow-on. Stubs compile cleanly.
 */
public final class ErlangEc2QueryIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#ec2Query");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext context) {
        if (!isApplicable(context.model(), context.settings())) {
            return List.of();
        }
        return List.of(
                ErlangRestJson1Integration.sendInterceptor("ec2Query", "smithy_query",
                        ErlangDependency.SMITHY_QUERY, ErlangDependency.SMITHY_HTTP_CLIENT),
                ErlangRestJson1Integration.receiveInterceptor("smithy_query"));
    }
}
