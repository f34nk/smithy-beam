package io.smithy.beam.core.ir;

import java.util.List;

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
        PaginationSpec pagination,         // null if not @paginated
        String outputTypeName,             // actual Smithy output shape name
        String inputTypeName,              // actual Smithy input shape name
        BodyEncoding responseEncoding,     // how to decode the HTTP response body
        String protocolContentType,        // wire Content-Type (e.g. "application/x-amz-json-1.0")
        ErrorCodeStrategy protocolErrorStrategy  // how to dispatch errors for this protocol
        ) {}
