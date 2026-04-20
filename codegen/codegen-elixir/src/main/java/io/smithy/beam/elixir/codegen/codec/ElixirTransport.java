package io.smithy.beam.elixir.codegen.codec;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Strategy interface for Elixir protocol transport implementations.
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
 * <p>They are returned by {@code DefaultElixirProtocolIntegration#transport()}
 * and consumed by the section interceptors that populate
 * {@link io.smithy.beam.elixir.codegen.sections.OperationRequestSection} and
 * {@link io.smithy.beam.elixir.codegen.sections.OperationResponseSection}.
 */
public interface ElixirTransport {

    /**
     * Emits the Elixir code that constructs the outgoing HTTP request
     * (URL, method, headers, and query string) for the given operation.
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose request is being constructed
     */
    void writeRequest(ElixirWriter w, ElixirContext ctx, OperationShape op);

    /**
     * Emits the Elixir code that signs the request, dispatches the HTTP call,
     * and branches on the response status code.
     *
     * <p>{@code decodeSuccessBody} is invoked at the point where the success
     * branch should decode the response body into the operation's output
     * struct. This lets the integration weave the codec-specific decode output
     * into the transport-specific dispatch envelope without either side needing
     * to know about the other's syntax.
     *
     * @param w                 the writer to append to
     * @param ctx               the current codegen context
     * @param op                the operation whose response is being dispatched
     * @param decodeSuccessBody hook invoked at the success-branch decode point
     */
    void writeResponse(ElixirWriter w, ElixirContext ctx, OperationShape op, Runnable decodeSuccessBody);
}
