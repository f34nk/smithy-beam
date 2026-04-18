package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.HttpSpec;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.QueryBinding;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.List;

/**
 * Template-method base for HTTP-binding protocol analyzers (restJson1, restXml, …).
 *
 * <p>Both {@link #analyzeClientOperation} and {@link #analyzeServerOperation} share the same
 * skeleton: extract HTTP bindings via {@link HttpBindingExtractor}, delegate the protocol-specific
 * body construction to the subclass, then assemble the {@link OperationSpec}. Subclasses only
 * need to supply:
 * <ul>
 *   <li>{@link #buildRequestBody} — protocol-specific request body (JSON vs XML)</li>
 *   <li>{@link #responseBodyEncoding} — {@link BodyEncoding} for decoding the HTTP response</li>
 *   <li>{@link ProtocolAnalyzer#contentType} — wire Content-Type value</li>
 *   <li>{@link ProtocolAnalyzer#errorStrategy} — how error codes are dispatched</li>
 * </ul>
 */
abstract class AbstractHttpProtocolAnalyzer implements ProtocolAnalyzer {

    // ── Response-binding hooks (identical across all HTTP-binding protocols) ──

    @Override
    public final String responsePayloadMember(StructureShape output) {
        return HttpBindingExtractor.buildResponsePayloadMember(output);
    }

    @Override
    public final String responseCodeMember(StructureShape output) {
        return HttpBindingExtractor.buildResponseCodeMember(output);
    }

    @Override
    public final List<HeaderBinding> responseHeaders(StructureShape output) {
        return HttpBindingExtractor.buildResponseHeaders(output);
    }

    // ── Template methods ──────────────────────────────────────────────────────

    @Override
    public final OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service) {
        HttpSpec http = HttpBindingExtractor.buildHttpSpec(op);
        StructureShape input = HttpBindingExtractor.inputShape(op, model);
        StructureShape output = HttpBindingExtractor.outputShape(op, model);

        List<LabelBinding> labels = HttpBindingExtractor.buildLabels(input, model);
        List<QueryBinding> queries = HttpBindingExtractor.buildQueries(input);
        List<HeaderBinding> headers = HttpBindingExtractor.buildHeaders(input);
        BodySpec body = buildRequestBody(input, labels, queries, headers, model);

        ErrorSpec errors = HttpBindingExtractor.buildErrors(op, model, errorStrategy(service));

        return new OperationSpec(
                op.getId().getName(),
                service.getId().getName(),
                Role.CLIENT,
                http, labels, queries, headers, body,
                errors,
                HttpBindingExtractor.buildAuth(service),
                RetrySpec.defaultRetry(),
                HttpBindingExtractor.buildPagination(op),
                HttpBindingExtractor.outputTypeName(op, model),
                HttpBindingExtractor.inputTypeName(op, model),
                responseBodyEncoding(),
                contentType(service),
                errorStrategy(service),
                null,
                HttpBindingExtractor.buildResponsePayloadMember(output),
                HttpBindingExtractor.buildResponseCodeMember(output),
                HttpBindingExtractor.buildResponseHeaders(output));
    }

    @Override
    public OperationSpec analyzeServerOperation(OperationShape op, Model model, ServiceShape service) {
        HttpSpec http = HttpBindingExtractor.buildHttpSpec(op);
        StructureShape input = HttpBindingExtractor.inputShape(op, model);

        List<LabelBinding> labels = HttpBindingExtractor.buildLabels(input, model);
        List<QueryBinding> queries = HttpBindingExtractor.buildQueries(input);
        List<HeaderBinding> headers = HttpBindingExtractor.buildHeaders(input);
        BodySpec body = buildRequestBody(input, labels, queries, headers, model);

        ErrorSpec errors = HttpBindingExtractor.buildErrors(op, model, errorStrategy(service));

        return new OperationSpec(
                op.getId().getName(),
                service.getId().getName(),
                Role.SERVER,
                http, labels, queries, headers, body,
                errors,
                AuthSpec.none(),
                RetrySpec.disabled(),
                null,
                HttpBindingExtractor.outputTypeName(op, model),
                HttpBindingExtractor.inputTypeName(op, model),
                responseBodyEncoding(),
                contentType(service),
                errorStrategy(service),
                null,
                null, null, List.of());
    }

    // ── Abstract hooks ────────────────────────────────────────────────────────

    /**
     * Builds the protocol-specific request body from the input shape members that are not already
     * bound to URI labels, query parameters, or request headers.
     */
    protected abstract BodySpec buildRequestBody(
            StructureShape input,
            List<LabelBinding> labels,
            List<QueryBinding> queries,
            List<HeaderBinding> headers,
            Model model);

    /**
     * Returns the {@link BodyEncoding} used to decode the HTTP response body for this protocol
     * (e.g. {@link BodyEncoding#JSON} for restJson1, {@link BodyEncoding#XML} for restXml).
     */
    protected abstract BodyEncoding responseBodyEncoding();
}
