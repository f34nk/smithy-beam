package io.smithy.beam.erlang.codegen.codec;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Strategy interface for Erlang protocol transport implementations.
 *
 * <p>A transport is responsible for emitting the HTTP request construction
 * (URI, method, headers, query string) and the HTTP response dispatch logic
 * for a specific transport style. Stateless; one instance is reused across
 * all operations of a service.
 *
 * <p>Concrete implementations live in the {@code http} package:
 * <ul>
 *   <li>{@code RestTransport} — derives the URI, method, headers, and query
 *       string from {@code @http}, {@code @httpLabel}, {@code @httpQuery}, and
 *       {@code @httpHeader} traits via
 *       {@link io.smithy.beam.core.binding.BindingHelper}.</li>
 *   <li>{@code RpcTransport} — always POSTs to {@code "/"} and adds the
 *       {@code X-Amz-Target} header (used by AWS-JSON, Query, and EC2-Query
 *       protocols).</li>
 * </ul>
 *
 * <p>They are returned by {@code DefaultErlangProtocolIntegration#transport()}
 * and consumed by the section interceptors that populate
 * {@link io.smithy.beam.erlang.codegen.sections.OperationRequestSection} and
 * {@link io.smithy.beam.erlang.codegen.sections.OperationResponseSection}.
 */
public interface ErlangTransport {

    /**
     * Emits the Erlang code that constructs the outgoing HTTP request
     * (URL, method, headers, and query string) for the given operation.
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose request is being constructed
     */
    void writeRequest(ErlangWriter w, ErlangContext ctx, OperationShape op);

    /**
     * Emits the Erlang code that dispatches on the HTTP response status code
     * and passes the body to the appropriate codec decode call.
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose response is being dispatched
     */
    void writeResponse(ErlangWriter w, ErlangContext ctx, OperationShape op);
}
