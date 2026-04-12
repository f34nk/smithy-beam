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
        PaginationSpec pagination // null if not @paginated
        ) {}
