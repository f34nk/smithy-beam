package io.smithy.beam.core.ir;

public record FieldSpec(
        String name,
        TypeRef type,
        boolean required,
        boolean isLabel,
        boolean isQuery,
        boolean isHeader,
        boolean isPayload) {}
