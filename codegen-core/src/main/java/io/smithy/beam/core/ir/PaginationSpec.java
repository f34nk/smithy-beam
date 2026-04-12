package io.smithy.beam.core.ir;

public record PaginationSpec(
        String inputTokenMember,
        String outputTokenMember,
        String itemsMember,
        String pageSizeMember // nullable
) {}
