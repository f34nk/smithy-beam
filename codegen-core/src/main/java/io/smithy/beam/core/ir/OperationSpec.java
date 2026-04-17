package io.smithy.beam.core.ir;

import java.util.List;

/**
 * Full specification of one operation (client or server direction).
 *
 * <p>Response-binding fields ({@code responsePayloadMember}, {@code responseCodeMember},
 * {@code responseHeaders}) describe how the HTTP response is assembled into the output map:
 * <ul>
 *   <li>{@code responsePayloadMember} — name of the {@code @httpPayload} output member whose
 *       value is the raw response body (blob). When non-null the response body is NOT
 *       JSON-decoded; it is placed as-is under this key in the result map.</li>
 *   <li>{@code responseCodeMember} — name of the {@code @httpResponseCode} output member.
 *       When non-null the HTTP status integer is placed under this key in the result map.</li>
 *   <li>{@code responseHeaders} — {@code @httpHeader}-bound output members. Each binding maps
 *       a response header name to its Smithy member name so the writer can extract them and
 *       populate the result map.</li>
 * </ul>
 * All three default to {@code null} / {@code List.of()} for protocols that do not use
 * per-member response bindings (awsQuery, awsJson, restXml without output headers, etc.).
 */
public record OperationSpec(
        String operationName,
        String serviceName,
        Role role,
        HttpSpec http,
        List<LabelBinding> labels,
        List<QueryBinding> queries,
        List<HeaderBinding> headers,
        BodySpec body,
        ErrorSpec errors,
        AuthSpec auth,
        RetrySpec retry,
        PaginationSpec pagination,                    // null if not @paginated
        String outputTypeName,                        // actual Smithy output shape name
        String inputTypeName,                         // actual Smithy input shape name
        BodyEncoding responseEncoding,                // how to decode the HTTP response body
        String protocolContentType,                   // wire Content-Type
        ErrorCodeStrategy protocolErrorStrategy,      // how to dispatch errors for this protocol
        String apiVersion,                            // service API version; null for non-query protocols
        String responsePayloadMember,                 // @httpPayload output member name; null if none
        String responseCodeMember,                    // @httpResponseCode output member name; null if none
        List<HeaderBinding> responseHeaders           // @httpHeader output member bindings; empty if none
        ) {

}
