package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.ProtocolResolver;
import io.smithy.beam.erlang.codegen.sections.OperationReceiveSection;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Base class for all Erlang protocol integrations.
 *
 * <p>Subclasses declare a {@link #protocolId()} and {@link #protocolDependencies()}.
 * The base class guards all customisations with {@link #isApplicable(ErlangContext)}
 * and wires up MVP stub interceptors for {@link OperationSendSection} and
 * {@link OperationReceiveSection}.
 */
public abstract class DefaultErlangProtocolIntegration implements ErlangIntegration {

    /** Returns the Smithy protocol shape ID this integration handles. */
    public abstract ShapeId protocolId();

    /**
     * Returns the runtime {@link ErlangDependency} values this protocol requires
     * (e.g. {@code SMITHY_JSON} and {@code SMITHY_HTTP_CLIENT} for JSON protocols).
     */
    protected abstract List<ErlangDependency> protocolDependencies();

    /**
     * Returns {@code true} if the service being generated uses this integration's
     * protocol.
     */
    protected boolean isApplicable(ErlangContext ctx) {
        return ProtocolResolver.resolve(protocolId())
                .map(traitClass -> ctx.service().hasTrait(traitClass))
                .orElse(false);
    }

    /**
     * Registers {@link OperationSendSection} and {@link OperationReceiveSection}
     * interceptors that inject the protocol runtime dependencies and emit
     * syntactically correct stub comments for the serialisation/deserialisation
     * hook points.
     */
    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        List<ErlangDependency> deps = protocolDependencies();
        ShapeId pid = protocolId();
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) -> {
                    deps.forEach(writer::addDependency);
                    writer.write("%% TODO: serialize request [$L]", pid);
                }),
                CodeInterceptor.appender(OperationReceiveSection.class, (writer, section) -> {
                    deps.forEach(writer::addDependency);
                    writer.write("%% TODO: deserialize response [$L]", pid);
                }));
    }
}
